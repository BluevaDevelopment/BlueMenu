package net.blueva.menu.listeners;

import net.blueva.menu.managers.java.PlayerManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

public class InventoryCloseListener implements Listener {
    @EventHandler
    public void ICL(InventoryCloseEvent e) {
        Player player = (Player) e.getPlayer();
        if(PlayerManager.isPlayerInMenu(player)) {
            PlayerManager.closeMenu(player);
        }
    }
}
