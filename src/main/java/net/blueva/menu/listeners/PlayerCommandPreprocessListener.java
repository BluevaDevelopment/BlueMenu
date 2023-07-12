package net.blueva.menu.listeners;

import net.blueva.menu.Main;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.io.File;
import java.util.Map;

public class PlayerCommandPreprocessListener implements Listener {
    Main main;

    public PlayerCommandPreprocessListener(Main main) {
        this.main = main;
    }


    @EventHandler
    public void PCPL(PlayerCommandPreprocessEvent e) {
        for (FileConfiguration menuConfig : main.javaMenuManager.menuConfigs.values()) {
            String openCommand = menuConfig.getString("openCommand");
            if(e.getMessage().equalsIgnoreCase(openCommand)) {
                e.setCancelled(true);
                String menu = getKeyByValue(main.javaMenuManager.menuConfigs, menuConfig);
                main.javaMenuManager.openMenu(e.getPlayer(), menu);
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
