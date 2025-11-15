package net.blueva.menu.managers.bedrock;

import net.blueva.menu.managers.ConditionManager;
import net.blueva.menu.utils.MessagesUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ModalManager {
    public static void openMenu(Player player, FileConfiguration menuConfig) {
        FloodgatePlayer playerB = FloodgateApi.getInstance().getPlayer(player.getUniqueId());

        List<String> content = MessagesUtil.format(player, menuConfig.getStringList("content"));
        StringBuilder keyResult = new StringBuilder();
        for (String element : content) {
            keyResult.append(element).append("\n");
        }

        // Check conditions for buttons (note: ModalForm requires both buttons, so we keep default behavior)
        // You could add condition checking here if you want to skip the entire form based on button visibility

        // Get button configurations
        ConfigurationSection button1Config = menuConfig.getConfigurationSection("buttons.button1");
        ConfigurationSection button2Config = menuConfig.getConfigurationSection("buttons.button2");

        // Check button1 conditions
        boolean button1Visible = true;
        if (button1Config != null) {
            if (button1Config.contains("display_conditions")) {
                button1Visible = ConditionManager.evaluateConditions(player, button1Config.getStringList("display_conditions"));
            }
            if (button1Visible && button1Config.contains("conditions")) {
                Object conditionsObj = button1Config.get("conditions");
                if (conditionsObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> conditionsMap = (Map<String, Object>) conditionsObj;
                    button1Visible = ConditionManager.evaluateConditionsMap(player, conditionsMap);
                } else if (conditionsObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> conditionsList = (List<String>) conditionsObj;
                    button1Visible = ConditionManager.evaluateConditions(player, conditionsList);
                }
            }
        }

        // Check button2 conditions
        boolean button2Visible = true;
        if (button2Config != null) {
            if (button2Config.contains("display_conditions")) {
                button2Visible = ConditionManager.evaluateConditions(player, button2Config.getStringList("display_conditions"));
            }
            if (button2Visible && button2Config.contains("conditions")) {
                Object conditionsObj = button2Config.get("conditions");
                if (conditionsObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> conditionsMap = (Map<String, Object>) conditionsObj;
                    button2Visible = ConditionManager.evaluateConditionsMap(player, conditionsMap);
                } else if (conditionsObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> conditionsList = (List<String>) conditionsObj;
                    button2Visible = ConditionManager.evaluateConditions(player, conditionsList);
                }
            }
        }

        final boolean finalButton1Visible = button1Visible;
        final boolean finalButton2Visible = button2Visible;

        ModalForm form = ModalForm.builder()
                .title(MessagesUtil.format(player, Objects.requireNonNull(menuConfig.getString("menuName"))))
                .content(keyResult.toString())
                .button1(MessagesUtil.format(player, Objects.requireNonNull(menuConfig.getString("buttons.button1.text"))))
                .button2(MessagesUtil.format(player, Objects.requireNonNull(menuConfig.getString("buttons.button2.text"))))
                .validResultHandler(result -> {
                    if(result.clickedButtonText().equals(MessagesUtil.format(player, Objects.requireNonNull(menuConfig.getString("buttons.button1.text"))))) {
                        // Only execute actions if button1 conditions pass
                        if (finalButton1Visible) {
                            ActionManager.executeActions(player, menuConfig.getStringList("buttons.button1.actions"));
                        }
                    }
                    if(result.clickedButtonText().equals(MessagesUtil.format(player, Objects.requireNonNull(menuConfig.getString("buttons.button2.text"))))) {
                        // Only execute actions if button2 conditions pass
                        if (finalButton2Visible) {
                            ActionManager.executeActions(player, menuConfig.getStringList("buttons.button2.actions"));
                        }
                    }
                })
                .build();

        playerB.sendForm(form);
    }
}
