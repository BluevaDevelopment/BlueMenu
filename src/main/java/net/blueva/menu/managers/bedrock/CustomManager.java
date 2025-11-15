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
                    componentsOrder.add(new ComponentData(componentKey, "DROPDOWN", component.getStringList("actions")));
                }
                break;

            case "INPUT":
                String placeholder = MessagesUtil.format(player, component.getString("placeholder", ""));
                String defaultText = MessagesUtil.format(player, component.getString("default", ""));
                formBuilder.input(text, placeholder, defaultText);
                componentsOrder.add(new ComponentData(componentKey, "INPUT", component.getStringList("actions")));
                break;

            case "TOGGLE":
                boolean defaultValue = component.getBoolean("default", false);
                formBuilder.toggle(text, defaultValue);
                componentsOrder.add(new ComponentData(componentKey, "TOGGLE", component.getStringList("actions")));
                break;

            case "SLIDER":
                float min = (float) component.getDouble("min", 0);
                float max = (float) component.getDouble("max", 10);
                float step = (float) component.getDouble("step", 1);
                float defaultSlider = (float) component.getDouble("default", min);
                formBuilder.slider(text, min, max, step, defaultSlider);
                componentsOrder.add(new ComponentData(componentKey, "SLIDER", component.getStringList("actions")));
                break;

            case "STEPSLIDER":
                List<String> steps = component.getStringList("steps");
                if (!steps.isEmpty()) {
                    List<String> formattedSteps = MessagesUtil.format(player, steps);
                    int defaultStep = component.getInt("default", 0);
                    formBuilder.stepSlider(text, formattedSteps, defaultStep);
                    componentsOrder.add(new ComponentData(componentKey, "STEPSLIDER", component.getStringList("actions")));
                }
                break;

            case "LABEL":
                formBuilder.label(text);
                componentsOrder.add(new ComponentData(componentKey, "LABEL", new ArrayList<>()));
                break;

            default:
                break;
        }
    }

    private static void handleResponse(Player player, CustomFormResponse response, List<ComponentData> componentsOrder) {
        response.reset();

        for (ComponentData componentData : componentsOrder) {
            if (!response.hasNext()) {
                break;
            }

            List<String> actions = componentData.getActions();

            switch (componentData.getType()) {
                case "DROPDOWN":
                    int dropdownIndex = response.asDropdown();
                    // Ejecutar acciones reemplazando {value} con el índice seleccionado
                    executeActionsWithValue(player, actions, String.valueOf(dropdownIndex));
                    break;

                case "INPUT":
                    String inputValue = response.asInput();
                    if (inputValue != null) {
                        // Ejecutar acciones reemplazando {value} con el texto ingresado
                        executeActionsWithValue(player, actions, inputValue);
                    }
                    break;

                case "TOGGLE":
                    boolean toggleValue = response.asToggle();
                    // Ejecutar acciones reemplazando {value} con true/false
                    executeActionsWithValue(player, actions, String.valueOf(toggleValue));
                    break;

                case "SLIDER":
                    float sliderValue = response.asSlider();
                    // Ejecutar acciones reemplazando {value} con el valor del slider
                    executeActionsWithValue(player, actions, String.valueOf(sliderValue));
                    break;

                case "STEPSLIDER":
                    int stepSliderIndex = response.asStepSlider();
                    // Ejecutar acciones reemplazando {value} con el índice del step seleccionado
                    executeActionsWithValue(player, actions, String.valueOf(stepSliderIndex));
                    break;

                case "LABEL":
                    response.skip(); // Los labels no tienen valor
                    break;

                default:
                    response.skip();
                    break;
            }
        }
    }

    private static void executeActionsWithValue(Player player, List<String> actions, String value) {
        if (actions != null && !actions.isEmpty()) {
            // Reemplazar {value} en las acciones con el valor recibido
            List<String> processedActions = new ArrayList<>();
            for (String action : actions) {
                processedActions.add(action.replace("{value}", value));
            }
            ActionManager.executeActions(player, processedActions);
        }
    }

    // Clase interna para almacenar datos de componentes
    private static class ComponentData {
        private final String key;
        private final String type;
        private final List<String> actions;

        public ComponentData(String key, String type, List<String> actions) {
            this.key = key;
            this.type = type;
            this.actions = actions;
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
    }
}
