package net.blueva.menu.managers.bedrock;

import net.blueva.menu.utils.MessagesUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.response.CustomFormResponse;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.util.*;

public class CustomManager {
    public static void openMenu(Player player, FileConfiguration menuConfig) {
        FloodgatePlayer playerB = FloodgateApi.getInstance().getPlayer(player.getUniqueId());

        CustomForm.Builder formBuilder = CustomForm.builder()
                .title(MessagesUtil.format(player, Objects.requireNonNull(menuConfig.getString("menuName"))));

        // Storage para mantener el orden de los componentes y sus acciones
        List<ComponentData> componentsOrder = new ArrayList<>();

        // Procesar componentes
        ConfigurationSection componentsConfig = menuConfig.getConfigurationSection("components");
        if (componentsConfig != null) {
            for (String componentKey : componentsConfig.getKeys(false)) {
                ConfigurationSection component = componentsConfig.getConfigurationSection(componentKey);
                if (component != null) {
                    String type = component.getString("type");
                    if (type != null) {
                        addComponent(formBuilder, component, type, player, componentKey, componentsOrder);
                    }
                }
            }
        }

        CustomForm form = formBuilder.validResultHandler(response -> {
            handleResponse(player, response, componentsOrder);
        }).build();

        playerB.sendForm(form);
    }

    private static void addComponent(CustomForm.Builder formBuilder, ConfigurationSection component,
                                     String type, Player player, String componentKey,
                                     List<ComponentData> componentsOrder) {
        String text = MessagesUtil.format(player, Objects.requireNonNull(component.getString("text", "")));

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
                float min = (float) component.getDouble("min", 0);
                float max = (float) component.getDouble("max", 10);
                float step = (float) component.getDouble("step", 1);
                float defaultSlider = (float) component.getDouble("default", min);
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

        // Map para almacenar todos los valores de los componentes
        Map<String, String> componentValues = new HashMap<>();

        // Primera pasada: recolectar todos los valores
        for (ComponentData componentData : componentsOrder) {
            if (!response.hasNext()) {
                break;
            }

            String value = null;

            switch (componentData.getType()) {
                case "DROPDOWN":
                    int dropdownIndex = response.asDropdown();
                    value = String.valueOf(dropdownIndex);
                    // También guardar el texto de la opción seleccionada si está disponible
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
                    // Si el valor es un número entero, usar formato int para evitar ".0"
                    value = (sliderValue == (int) sliderValue)
                        ? String.valueOf((int) sliderValue)
                        : String.valueOf(sliderValue);
                    break;

                case "STEPSLIDER":
                    int stepSliderIndex = response.asStepSlider();
                    value = String.valueOf(stepSliderIndex);
                    // También guardar el texto del step seleccionado si está disponible
                    if (componentData.getOptions() != null && stepSliderIndex < componentData.getOptions().size()) {
                        componentValues.put(componentData.getKey() + "_text", componentData.getOptions().get(stepSliderIndex));
                    }
                    break;

                case "LABEL":
                    response.skip(); // Los labels no tienen valor
                    continue;

                default:
                    response.skip();
                    continue;
            }

            if (value != null) {
                componentValues.put(componentData.getKey(), value);
            }
        }

        // Segunda pasada: ejecutar las acciones con todos los valores disponibles
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

        // Procesar cada acción reemplazando SOLO los placeholders de componentes
        // Dejar que ActionManager procese el resto (formato, placeholders estándar, etc.)
        List<String> processedActions = new ArrayList<>();
        for (String action : actions) {
            String processedAction = action;

            // Reemplazar solo los placeholders {component_key} con sus valores
            for (Map.Entry<String, String> entry : componentValues.entrySet()) {
                processedAction = processedAction.replace("{" + entry.getKey() + "}", entry.getValue());
            }

            processedActions.add(processedAction);
        }

        ActionManager.executeActions(player, processedActions);
    }

    // Clase interna para almacenar datos de componentes
    private static class ComponentData {
        private final String key;
        private final String type;
        private final List<String> actions;
        private final List<String> options; // Para dropdown y stepslider

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
