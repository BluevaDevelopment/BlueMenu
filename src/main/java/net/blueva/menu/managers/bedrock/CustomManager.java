package net.blueva.menu.managers.bedrock;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import net.blueva.menu.managers.ConditionManager;
import net.blueva.menu.utils.MessagesUtil;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.response.CustomFormResponse;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.util.*;

public class CustomManager {
    public static void openMenu(Player player, YamlDocument menuConfig) {
        FloodgatePlayer playerB = FloodgateApi.getInstance().getPlayer(player.getUniqueId());
        if (playerB == null) {
            return;
        }

        CustomForm.Builder formBuilder = CustomForm.builder()
                .title(MessagesUtil.format(player, menuConfig.getString("menuName", "")));

        // Storage to preserve component order and their actions
        List<ComponentData> componentsOrder = new ArrayList<>();

        // Process components
        Section componentsConfig = menuConfig.getSection("components");
        if (componentsConfig != null) {
            for (Object componentKeyObj : componentsConfig.getKeys()) {
                String componentKey = componentKeyObj.toString();
                Section component = componentsConfig.getSection(componentKey);
                if (component != null) {
                    String type = component.getString("type");
                    if (type != null) {
                        addComponent(formBuilder, component, type, player, componentKey, componentsOrder);
                    }
                }
            }
        }

        formBuilder.closedOrInvalidResultHandler(() ->
            ActionManager.executeActions(player, menuConfig.getStringList("close_actions")));

        CustomForm form = formBuilder.validResultHandler(response -> {
            handleResponse(player, response, componentsOrder);
        }).build();

        playerB.sendForm(form);
    }

    private static void addComponent(CustomForm.Builder formBuilder, Section component,
                                     String type, Player player, String componentKey,
                                     List<ComponentData> componentsOrder) {
        // Check display conditions before adding the component
        boolean shouldDisplay = true;

        if (component.contains("display_conditions")) {
            Object conditionsObj = component.get("display_conditions");
            if (conditionsObj instanceof Map) {
                // Map format with all/any/none
                @SuppressWarnings("unchecked")
                Map<String, Object> conditionsMap = (Map<String, Object>) conditionsObj;
                shouldDisplay = ConditionManager.evaluateConditionsMap(player, conditionsMap);
            } else if (conditionsObj instanceof List) {
                // Simple list format
                @SuppressWarnings("unchecked")
                List<String> conditionsList = (List<String>) conditionsObj;
                shouldDisplay = ConditionManager.evaluateConditions(player, conditionsList);
            }
        }

        // Only add the component if conditions pass
        if (!shouldDisplay) {
            return;
        }

        String text = MessagesUtil.format(player, component.getString("text", ""));

        switch (type.toUpperCase()) {
            case "DROPDOWN":
                List<String> dropdownOptions = component.getStringList("options");
                if (!dropdownOptions.isEmpty()) {
                    List<String> formattedOptions = MessagesUtil.format(player, dropdownOptions);
                    int defaultOption = component.getInt("default", 0);
                    formBuilder.dropdown(text, formattedOptions, defaultOption);
                    componentsOrder.add(new ComponentData(componentKey, "DROPDOWN", component.getStringList("actions"), dropdownOptions));
                }
                break;

            case "INPUT":
                String placeholder = MessagesUtil.format(player, component.getString("placeholder", ""));
                String defaultText = MessagesUtil.format(player, component.getString("default", ""));
                formBuilder.input(text, placeholder, defaultText);
                componentsOrder.add(new ComponentData(componentKey, "INPUT", component.getStringList("actions"), null));
                break;

            case "TOGGLE":
                boolean defaultValue = component.getBoolean("default", false);
                formBuilder.toggle(text, defaultValue);
                componentsOrder.add(new ComponentData(componentKey, "TOGGLE", component.getStringList("actions"), null));
                break;

            case "SLIDER":
                float min = component.getDouble("min", 0.0).floatValue();
                float max = component.getDouble("max", 10.0).floatValue();
                float step = component.getDouble("step", 1.0).floatValue();
                float defaultSlider = component.getDouble("default", (double)min).floatValue();
                formBuilder.slider(text, min, max, step, defaultSlider);
                componentsOrder.add(new ComponentData(componentKey, "SLIDER", component.getStringList("actions"), null));
                break;

            case "STEPSLIDER":
                List<String> steps = component.getStringList("steps");
                if (!steps.isEmpty()) {
                    List<String> formattedSteps = MessagesUtil.format(player, steps);
                    int defaultStep = component.getInt("default", 0);
                    formBuilder.stepSlider(text, formattedSteps, defaultStep);
                    componentsOrder.add(new ComponentData(componentKey, "STEPSLIDER", component.getStringList("actions"), steps));
                }
                break;

            case "LABEL":
                formBuilder.label(text);
                componentsOrder.add(new ComponentData(componentKey, "LABEL", new ArrayList<>(), null));
                break;

            default:
                break;
        }
    }

    private static void handleResponse(Player player, CustomFormResponse response, List<ComponentData> componentsOrder) {
        response.reset();

        // Map to store all component values
        Map<String, String> componentValues = new HashMap<>();

        // First pass: collect all values
        for (ComponentData componentData : componentsOrder) {
            if (!response.hasNext()) {
                break;
            }

            String value = null;

            switch (componentData.getType()) {
                case "DROPDOWN":
                    int dropdownIndex = response.asDropdown();
                    value = String.valueOf(dropdownIndex);
                    // Also store the selected option text if available
                    if (componentData.getOptions() != null && dropdownIndex < componentData.getOptions().size()) {
                        componentValues.put(componentData.getKey() + "_text", componentData.getOptions().get(dropdownIndex));
                    }
                    break;

                case "INPUT":
                    value = response.asInput();
                    if (value == null) value = "";
                    break;

                case "TOGGLE":
                    boolean toggleValue = response.asToggle();
                    value = String.valueOf(toggleValue);
                    break;

                case "SLIDER":
                    float sliderValue = response.asSlider();
                    // If the value is an integer, use int formatting to avoid ".0"
                    value = (sliderValue == (int) sliderValue)
                        ? String.valueOf((int) sliderValue)
                        : String.valueOf(sliderValue);
                    break;

                case "STEPSLIDER":
                    int stepSliderIndex = response.asStepSlider();
                    value = String.valueOf(stepSliderIndex);
                    // Also store the selected step text if available
                    if (componentData.getOptions() != null && stepSliderIndex < componentData.getOptions().size()) {
                        componentValues.put(componentData.getKey() + "_text", componentData.getOptions().get(stepSliderIndex));
                    }
                    break;

                case "LABEL":
                    response.skip(); // Labels have no value
                    continue;

                default:
                    response.skip();
                    continue;
            }

            if (value != null) {
                componentValues.put(componentData.getKey(), value);
            }
        }

        // Second pass: run actions with all available values
        for (ComponentData componentData : componentsOrder) {
            List<String> actions = componentData.getActions();
            if (actions != null && !actions.isEmpty()) {
                executeActionsWithValues(player, actions, componentValues);
            }
        }
    }

    private static void executeActionsWithValues(Player player, List<String> actions, Map<String, String> componentValues) {
        if (actions == null || actions.isEmpty()) {
            return;
        }

        // Process each action by replacing ONLY component placeholders
        // Let ActionManager process the rest (formatting, standard placeholders, etc.)
        List<String> processedActions = new ArrayList<>();
        for (String action : actions) {
            String processedAction = action;

            // Replace only {component_key} placeholders with their values
            for (Map.Entry<String, String> entry : componentValues.entrySet()) {
                processedAction = processedAction.replace("{" + entry.getKey() + "}", entry.getValue());
            }

            processedActions.add(processedAction);
        }

        ActionManager.executeActions(player, processedActions);
    }

    // Inner class for storing component data
    private static class ComponentData {
        private final String key;
        private final String type;
        private final List<String> actions;
        private final List<String> options; // For dropdown and stepslider

        public ComponentData(String key, String type, List<String> actions, List<String> options) {
            this.key = key;
            this.type = type;
            this.actions = actions;
            this.options = options;
        }

        public String getKey() {
            return key;
        }

        public String getType() {
            return type;
        }

        public List<String> getActions() {
            return actions;
        }

        public List<String> getOptions() {
            return options;
        }
    }
}
