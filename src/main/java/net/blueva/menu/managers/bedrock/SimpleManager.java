package net.blueva.menu.managers.bedrock;

import net.blueva.menu.utils.MessagesUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.util.List;
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

        ConfigurationSection buttonsConfig = menuConfig.getConfigurationSection("buttons");
        if (buttonsConfig != null) {
            for (String buttonKey : buttonsConfig.getKeys(false)) {
                String buttonText = MessagesUtil.format(player, Objects.requireNonNull(buttonsConfig.getString(buttonKey + ".text")));
                if(buttonsConfig.isSet(buttonKey+".image")) {
                    formBuilder.button(buttonText, FormImage.Type.URL, Objects.requireNonNull(buttonsConfig.getString(buttonKey + ".image")));
                } else {
                    formBuilder.button(buttonText);
                }
            }
        }

        SimpleForm form = formBuilder.validResultHandler(result -> {
            ConfigurationSection buttonsConfigSection = menuConfig.getConfigurationSection("buttons");
            if (buttonsConfigSection != null) {
                for (String buttonKey : buttonsConfigSection.getKeys(false)) {
                    String buttonText = MessagesUtil.format(player, Objects.requireNonNull(buttonsConfigSection.getString(buttonKey + ".text")));
                    if (result.clickedButton().text().equals(buttonText)) {
                        ActionManager.executeActions(player, buttonsConfigSection.getStringList(buttonKey + ".actions"));
                    }
                }
            }
        }).build();

        playerB.sendForm(form);
    }
}
