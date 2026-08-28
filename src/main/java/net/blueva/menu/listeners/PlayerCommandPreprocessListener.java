package net.blueva.menu.listeners;

import dev.dejvokep.boostedyaml.YamlDocument;
import net.blueva.menu.Main;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.io.File;
import java.util.Map;

public class PlayerCommandPreprocessListener implements Listener {
    Main main;

    public PlayerCommandPreprocessListener(Main main) {
        this.main = main;
    }


    @EventHandler
    public void PCPL(PlayerCommandPreprocessEvent e) {
        Player player = e.getPlayer();

        if(Main.isUsingFloodgate) {
            if(FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId())) {
                checkBedrockMenu(main, e);
            } else {
                checkJavaMenu(main, e);
            }
        } else {
            checkJavaMenu(main, e);
        }
    }

    private static void checkJavaMenu(Main main, PlayerCommandPreprocessEvent e) {
        String message = e.getMessage().trim();
        for (Map.Entry<String, YamlDocument> entry : main.javaMenuManager.menuConfigs.entrySet()) {
            String openCommand = entry.getValue().getString("openCommand");
            if (openCommand == null || openCommand.trim().isEmpty()) {
                continue;
            }
            if (message.equalsIgnoreCase(openCommand.trim())) {
                e.setCancelled(true);
                main.javaMenuManager.openMenu(e.getPlayer(), entry.getKey());
                return; // first match wins, avoid opening several menus on a duplicate command
            }
        }
    }

    private static void checkBedrockMenu(Main main, PlayerCommandPreprocessEvent e) {
        String message = e.getMessage().trim();
        for (Map.Entry<String, YamlDocument> entry : main.bedrockMenuManager.menuConfigs.entrySet()) {
            String openCommand = entry.getValue().getString("openCommand");
            if (openCommand == null || openCommand.trim().isEmpty()) {
                continue;
            }
            if (message.equalsIgnoreCase(openCommand.trim())) {
                e.setCancelled(true);
                main.bedrockMenuManager.openMenu(e.getPlayer(), entry.getKey());
                return; // first match wins
            }
        }
    }
}
