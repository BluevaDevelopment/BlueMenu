package net.blueva.menu.managers.java;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;

import static org.bukkit.Bukkit.getLogger;

public class ActionManager {
    public static void executeActions(Player player, List<String> actions, ClickType clickType) {
        String clickTypeString = "";
        if (clickType == ClickType.LEFT) {
            clickTypeString = "LEFT_CLICK";
        } else if (clickType == ClickType.RIGHT) {
            clickTypeString = "RIGHT_CLICK";
        }

        for (String action : actions) {
            String[] actionParts = action.split(" ", 2);

            if (actionParts.length == 2) {
                String actionType = actionParts[0].toUpperCase();
                String actionData = actionParts[1];

                if (actionType.equals("[" + clickTypeString + "]")) {
                    executeClickAction(player, actionData);
                }
            }
        }
    }

    private static void executeClickAction(Player player, String action) {
        String[] actionParts = action.split(":");
        if (actionParts.length == 2) {
            String actionTarget = actionParts[0].toUpperCase();
            String actionCommand = actionParts[1];

            switch (actionTarget) {
                case "CONSOLE":
                    String consoleCommand = actionCommand.replace("%player%", player.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), consoleCommand);
                    break;
                case "PLAYER":
                    String playerCommand = actionCommand.replace("%player%", player.getName());
                    player.performCommand(playerCommand);
                    break;
                case "MESSAGE":
                    String message = ChatColor.translateAlternateColorCodes('&', actionCommand);
                    player.sendMessage(message);
                    break;
                // Agrega más objetivos de acción según tus necesidades
                default:
                    getLogger().warning("Invalid action target: " + actionTarget);
                    break;
            }
        }
    }
}
