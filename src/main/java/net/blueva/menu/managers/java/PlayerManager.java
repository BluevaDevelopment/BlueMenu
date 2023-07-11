package net.blueva.menu.managers.java;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class PlayerManager {
    public final static Map<Player, Boolean> playerInMenu = new HashMap<>();

    public static boolean isPlayerInMenu(Player player) {
        if(!playerInMenu.isEmpty() && playerInMenu.containsKey(player)) {
            return playerInMenu.get(player).equals(true);
        }
        return false;
    }

    public static void openMenu(Player player) {
        if(!playerInMenu.isEmpty() && playerInMenu.containsKey(player) && playerInMenu.get(player).equals(false)) {
            playerInMenu.replace(player, true);
        }
    }

    public static void closeMenu(Player player) {
        if(!playerInMenu.isEmpty() && playerInMenu.containsKey(player) && playerInMenu.get(player).equals(true)) {
            playerInMenu.replace(player, false);
        }
    }
}
