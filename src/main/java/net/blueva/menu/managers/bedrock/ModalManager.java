package net.blueva.menu.managers.bedrock;

import net.blueva.menu.utils.MessagesUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.util.List;
import java.util.Objects;

public class ModalManager {
    public static void openMenu(Player player, FileConfiguration menuConfig) {
        FloodgatePlayer playerB = FloodgateApi.getInstance().getPlayer(player.getUniqueId());

        List<String> content = MessagesUtil.format(player, menuConfig.getStringList("content"));
        StringBuilder keyResult = new StringBuilder();
        for (String element : content) {
            keyResult.append(element).append("\n");
        }

        ModalForm form = ModalForm.builder()
                .title(MessagesUtil.format(player, Objects.requireNonNull(menuConfig.getString("menuName"))))
                .content(keyResult.toString())
                .button1(MessagesUtil.format(player, Objects.requireNonNull(menuConfig.getString("buttons.button1.text"))))
                .button2(MessagesUtil.format(player, Objects.requireNonNull(menuConfig.getString("buttons.button2.text"))))
                .validResultHandler(result -> {
                    if(result.clickedButtonText().equals(MessagesUtil.format(player, Objects.requireNonNull(menuConfig.getString("buttons.button1.text"))))) {
                        ActionManager.executeActions(player, menuConfig.getStringList("buttons.button1.actions"));
                    }
                    if(result.clickedButtonText().equals(MessagesUtil.format(player, Objects.requireNonNull(menuConfig.getString("buttons.button2.text"))))) {
                        ActionManager.executeActions(player, menuConfig.getStringList("buttons.button2.actions"));
                    }
                })
                .build();

        playerB.sendForm(form);
    }
}
