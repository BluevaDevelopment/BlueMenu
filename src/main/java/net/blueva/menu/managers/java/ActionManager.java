package net.blueva.menu.managers.java;

import net.blueva.menu.utils.MessagesUtil;
import org.bukkit.Bukkit;
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
        String[] actionParts = action.split(";");
        if (actionParts.length >= 2) {
            String actionTarget = actionParts[0].toUpperCase();
            String actionCommand = actionParts[1];

            switch (actionTarget) {
                case "CONSOLE" -> {
                    String consoleCommand = MessagesUtil.format(player, actionCommand);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), consoleCommand);
                }
                case "PLAYER" -> {
                    String playerCommand = MessagesUtil.format(player, actionCommand);
                    player.performCommand(playerCommand);
                }
                case "MESSAGE" -> {
                    String message = MessagesUtil.format(player, actionCommand);
                    player.sendMessage(message);
                }
                default -> getLogger().warning("Invalid action target: " + actionTarget);
            }
        }
    }
}
