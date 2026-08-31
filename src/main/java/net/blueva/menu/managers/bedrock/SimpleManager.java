package net.blueva.menu.managers.bedrock;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import net.blueva.menu.managers.ConditionManager;
import net.blueva.menu.utils.MessagesUtil;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SimpleManager {
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

        SimpleForm.Builder formBuilder = SimpleForm.builder()
                .title(MessagesUtil.format(player, menuConfig.getString("menuName", "")))
                .content(keyResult.toString());

        // Buttons that actually got added, in order - so the clicked index maps back to a key.
        List<String> visibleButtons = new ArrayList<>();

        Section buttonsConfig = menuConfig.getSection("buttons");
        if (buttonsConfig != null) {
            for (Object buttonKeyObj : buttonsConfig.getKeys()) {
                String buttonKey = buttonKeyObj.toString();
                Section buttonSection = buttonsConfig.getSection(buttonKey);
                if (buttonSection == null || !shouldDisplay(player, buttonSection)) {
                    continue;
                }

                String buttonText = MessagesUtil.format(player, buttonSection.getString("text", ""));
                String image = buttonSection.getString("image", "");
                if (!image.isBlank()) {
                    formBuilder.button(buttonText, FormImage.Type.URL, image);
                } else {
                    formBuilder.button(buttonText);
                }
                visibleButtons.add(buttonKey);
            }
        }

        formBuilder.closedOrInvalidResultHandler(() ->
            ActionManager.executeActions(player, menuConfig.getStringList("close_actions")));

        SimpleForm form = formBuilder.validResultHandler(result -> {
            int id = result.clickedButtonId();
            if (id < 0 || id >= visibleButtons.size()) {
                return;
            }
            Section buttonsSection = menuConfig.getSection("buttons");
            if (buttonsSection == null) {
                return;
            }
            Section buttonSection = buttonsSection.getSection(visibleButtons.get(id));
            if (buttonSection != null) {
                ActionManager.executeActions(player, buttonSection.getStringList("actions"));
            }
        }).build();

        playerB.sendForm(form);
    }

    private static boolean shouldDisplay(Player player, Section buttonSection) {
        if (!buttonSection.contains("display_conditions")) {
            return true;
        }
        Object conditionsObj = buttonSection.get("display_conditions");
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
