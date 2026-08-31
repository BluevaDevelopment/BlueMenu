package net.blueva.menu.managers.bedrock;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import net.blueva.menu.managers.ConditionManager;
import net.blueva.menu.utils.MessagesUtil;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.util.List;
import java.util.Map;

public class ModalManager {
    public static void openMenu(Player player, YamlDocument menuConfig) {
        FloodgatePlayer playerB = FloodgateApi.getInstance().getPlayer(player.getUniqueId());
        if (playerB == null) {
            return;
        }

        List<String> content = MessagesUtil.format(player, menuConfig.getStringList("content"));
        StringBuilder keyResult = new StringBuilder();
        for (String element : content) {
            keyResult.append(element).append("\n");
        }

        // ModalForm always shows exactly two buttons; conditions gate whether their
        // actions run, not whether they appear.
        boolean button1Visible = buttonVisible(player, menuConfig.getSection("buttons.button1"));
        boolean button2Visible = buttonVisible(player, menuConfig.getSection("buttons.button2"));

        ModalForm form = ModalForm.builder()
                .title(MessagesUtil.format(player, menuConfig.getString("menuName", "")))
                .content(keyResult.toString())
                .button1(MessagesUtil.format(player, menuConfig.getString("buttons.button1.text", "")))
                .button2(MessagesUtil.format(player, menuConfig.getString("buttons.button2.text", "")))
                .closedOrInvalidResultHandler(() ->
                    ActionManager.executeActions(player, menuConfig.getStringList("close_actions")))
                .validResultHandler(result -> {
                    if (result.clickedFirst()) {
                        if (button1Visible) {
                            ActionManager.executeActions(player, menuConfig.getStringList("buttons.button1.actions"));
                        }
                    } else {
                        if (button2Visible) {
                            ActionManager.executeActions(player, menuConfig.getStringList("buttons.button2.actions"));
                        }
                    }
                })
                .build();

        playerB.sendForm(form);
    }

    private static boolean buttonVisible(Player player, Section buttonConfig) {
        if (buttonConfig == null || !buttonConfig.contains("display_conditions")) {
            return true;
        }
        Object conditionsObj = buttonConfig.get("display_conditions");
        if (conditionsObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> conditionsMap = (Map<String, Object>) conditionsObj;
            return ConditionManager.evaluateConditionsMap(player, conditionsMap);
        }
        if (conditionsObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> conditionsList = (List<String>) conditionsObj;
            return ConditionManager.evaluateConditions(player, conditionsList);
        }
        return true;
    }
}
