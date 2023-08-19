package net.blueva.menu.listeners;

import net.blueva.menu.Main;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
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
        for (FileConfiguration menuConfig : main.javaMenuManager.menuConfigs.values()) {
            String openCommand = menuConfig.getString("openCommand");
            if(e.getMessage().equalsIgnoreCase(openCommand)) {
                e.setCancelled(true);
                String menu = getKeyByValue(main.javaMenuManager.menuConfigs, menuConfig);
                main.javaMenuManager.openMenu(e.getPlayer(), menu);
            }

        }
    }

    private static void checkBedrockMenu(Main main, PlayerCommandPreprocessEvent e) {
        for (FileConfiguration menuConfig : main.bedrockMenuManager.menuConfigs.values()) {
            String openCommand = menuConfig.getString("openCommand");
            if(e.getMessage().equalsIgnoreCase(openCommand)) {
                e.setCancelled(true);
                String menu = getKeyByValue(main.bedrockMenuManager.menuConfigs, menuConfig);
                main.bedrockMenuManager.openMenu(e.getPlayer(), menu);
            }

        }
    }

    private static <K, V> K getKeyByValue(Map<K, V> map, V value) {
        for (Map.Entry<K, V> entry : map.entrySet()) {
            if (entry.getValue().equals(value)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
