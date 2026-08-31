package net.blueva.menu.managers.java;

import net.blueva.menu.utils.MessagesUtil;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which players currently have a BlueMenu Java menu open.
 * <p>
 * Keyed by {@link UUID} (not the {@link Player} object) so entries don't pin a disconnected
 * player in memory, and every mutation is unconditional so state is correct even for players
 * that were already online when the plugin (re)loaded.
 */
public class PlayerManager {
    private static final Map<UUID, Boolean> playerInMenu = new ConcurrentHashMap<>();
    private static final Map<UUID, String> playerMenuTitle = new ConcurrentHashMap<>();
    private static final Map<UUID, String> playerMenuName = new ConcurrentHashMap<>();

    public static boolean isPlayerInMenu(Player player) {
        return player != null && Boolean.TRUE.equals(playerInMenu.get(player.getUniqueId()));
    }

    public static void openMenu(Player player, String title, String menuName) {
        UUID id = player.getUniqueId();
        playerInMenu.put(id, true);
        playerMenuTitle.put(id, MessagesUtil.format(player, title));
        playerMenuName.put(id, menuName);
    }

    public static void closeMenu(Player player) {
        UUID id = player.getUniqueId();
        playerInMenu.put(id, false);
        playerMenuTitle.put(id, "None");
        playerMenuName.remove(id);
    }

    /**
     * Drop every trace of a player. Call on quit and on plugin disable.
     */
    public static void forget(Player player) {
        UUID id = player.getUniqueId();
        playerInMenu.remove(id);
        playerMenuTitle.remove(id);
        playerMenuName.remove(id);
    }

    public static void clearAll() {
        playerInMenu.clear();
        playerMenuTitle.clear();
        playerMenuName.clear();
    }

    public static String getMenuName(Player player) {
        return playerMenuName.get(player.getUniqueId());
    }

    public static String getMenuTitle(Player player) {
        return playerMenuTitle.get(player.getUniqueId());
    }
}
