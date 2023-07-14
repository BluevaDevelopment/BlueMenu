package net.blueva.menu.listeners;

import net.blueva.menu.managers.java.PlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {
    @EventHandler
    public void PJL(PlayerJoinEvent e) {
        if(!PlayerManager.playerInMenu.containsKey(e.getPlayer())) {
            PlayerManager.playerInMenu.put(e.getPlayer(), false);
            PlayerManager.playerMenuTitle.put(e.getPlayer(), "None");
        }
    }
}
