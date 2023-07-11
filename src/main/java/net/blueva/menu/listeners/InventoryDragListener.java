package net.blueva.menu.listeners;

import net.blueva.menu.managers.java.PlayerManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryDragEvent;

public class InventoryDragListener implements Listener {
    @EventHandler
    public void IDL(InventoryDragEvent e) {
        Player player = (Player) e.getView().getPlayer();
        if (PlayerManager.isPlayerInMenu(player)) {
            e.setCancelled(true);
        }
    }
}
