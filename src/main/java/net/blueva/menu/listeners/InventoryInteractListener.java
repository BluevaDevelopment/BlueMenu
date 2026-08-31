package net.blueva.menu.listeners;

import fr.mrmicky.fastinv.FastInv;
import net.blueva.menu.Main;
import net.blueva.menu.managers.java.PlayerManager;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryInteractEvent;

/**
 * Safety net: cancel any interaction (click, drag, shift-move) while a BlueMenu Java menu
 * is open, so items in the GUI can never be taken or duplicated even if FastInv misses a case.
 */
public class InventoryInteractListener implements Listener {
    @EventHandler
    public void onInteract(InventoryInteractEvent e) {
        HumanEntity viewer = e.getView().getPlayer();
        if (!(viewer instanceof Player player)) {
            return;
        }

        if (PlayerManager.isPlayerInMenu(player)) {
            e.setCancelled(true);
            return;
        }

        FastInv menu = Main.getPlugin().javaMenuManager.getActiveMenu(player);
        if (menu != null && e.getInventory().equals(menu.getInventory())) {
            e.setCancelled(true);
        }
    }
}
