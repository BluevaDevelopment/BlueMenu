package net.blueva.menu.listeners;

import net.blueva.menu.managers.java.PlayerManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Cleans up per-player menu state when a player leaves so nothing is kept in memory.
 * <p>
 * No join handler is needed any more: {@link PlayerManager} creates entries lazily and
 * unconditionally the first time a menu is opened, which also fixes players that were
 * already online when the plugin (re)loaded.
 */
public class PlayerJoinListener implements Listener {
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        PlayerManager.forget(e.getPlayer());
    }
}
