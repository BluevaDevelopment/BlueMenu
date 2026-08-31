package net.blueva.menu.listeners;

import dev.dejvokep.boostedyaml.YamlDocument;
import net.blueva.menu.Main;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.geysermc.floodgate.api.FloodgateApi;

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
        String label = firstToken(e.getMessage());
        for (Map.Entry<String, YamlDocument> entry : main.javaMenuManager.menuConfigs.entrySet()) {
            if (!matchesOpenCommand(entry.getValue(), label)) {
                continue;
            }
            String permission = entry.getValue().getString("openPermission", "");
            if (!permission.isBlank() && !e.getPlayer().hasPermission(permission)) {
                return; // command matched a menu, but the player may not open it
            }
            e.setCancelled(true);
            main.javaMenuManager.openMenu(e.getPlayer(), entry.getKey());
            return; // first match wins, avoid opening several menus on a duplicate command
        }
    }

    private static void checkBedrockMenu(Main main, PlayerCommandPreprocessEvent e) {
        String label = firstToken(e.getMessage());
        for (Map.Entry<String, YamlDocument> entry : main.bedrockMenuManager.menuConfigs.entrySet()) {
            if (!matchesOpenCommand(entry.getValue(), label)) {
                continue;
            }
            String permission = entry.getValue().getString("openPermission", "");
            if (!permission.isBlank() && !e.getPlayer().hasPermission(permission)) {
                return;
            }
            e.setCancelled(true);
            main.bedrockMenuManager.openMenu(e.getPlayer(), entry.getKey());
            return; // first match wins
        }
    }

    /** The command word without any trailing arguments, e.g. "/shop buy 3" -> "/shop". */
    private static String firstToken(String message) {
        String trimmed = message.trim();
        int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) : trimmed;
    }

    private static boolean matchesOpenCommand(YamlDocument menuConfig, String label) {
        String openCommand = menuConfig.getString("openCommand");
        if (openCommand == null || openCommand.trim().isEmpty()) {
            return false;
        }
        return label.equalsIgnoreCase(openCommand.trim());
    }
}
