package net.blueva.menu.managers.bedrock;

import net.blueva.menu.managers.ConditionManager;
import net.blueva.menu.utils.MessagesUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SimpleManager {
    public static void openMenu(Player player, FileConfiguration menuConfig) {
        FloodgatePlayer playerB = FloodgateApi.getInstance().getPlayer(player.getUniqueId());

        List<String> content = MessagesUtil.format(player, menuConfig.getStringList("content"));
        StringBuilder keyResult = new StringBuilder();
        for (String element : content) {
            keyResult.append(element).append("\n");
        }

        SimpleForm.Builder formBuilder = SimpleForm.builder()
                .title(MessagesUtil.format(player, Objects.requireNonNull(menuConfig.getString("menuName"))))
                .content(keyResult.toString());

        // Store visible buttons for action handling
        List<String> visibleButtons = new ArrayList<>();

        ConfigurationSection buttonsConfig = menuConfig.getConfigurationSection("buttons");
        if (buttonsConfig != null) {
            for (String buttonKey : buttonsConfig.getKeys(false)) {
                ConfigurationSection buttonSection = buttonsConfig.getConfigurationSection(buttonKey);
                if (buttonSection != null) {
                    // Check display conditions before adding the button
                    boolean shouldDisplay = true;

                    if (buttonSection.contains("display_conditions")) {
                        Object conditionsObj = buttonSection.get("display_conditions");
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

                    // Only add the button if conditions pass
                    if (shouldDisplay) {
                        String buttonText = MessagesUtil.format(player, Objects.requireNonNull(buttonSection.getString("text")));
                        if (buttonSection.isSet("image")) {
                            formBuilder.button(buttonText, FormImage.Type.URL, Objects.requireNonNull(buttonSection.getString("image")));
                        } else {
                            formBuilder.button(buttonText);
                        }
                        visibleButtons.add(buttonKey);
                    }
                }
            }
        }

        SimpleForm form = formBuilder.validResultHandler(result -> {
            ConfigurationSection buttonsConfigSection = menuConfig.getConfigurationSection("buttons");
            if (buttonsConfigSection != null) {
                // Only check visible buttons for action execution
                for (String buttonKey : visibleButtons) {
                    ConfigurationSection buttonSection = buttonsConfigSection.getConfigurationSection(buttonKey);
                    if (buttonSection != null) {
                        String buttonText = MessagesUtil.format(player, Objects.requireNonNull(buttonSection.getString("text")));
                        if (result.clickedButton().text().equals(buttonText)) {
                            ActionManager.executeActions(player, buttonSection.getStringList("actions"));
                        }
                    }
                }
            }
        }).build();

        playerB.sendForm(form);
    }
}
