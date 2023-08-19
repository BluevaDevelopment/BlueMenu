package net.blueva.menu.managers.bedrock;

import net.blueva.menu.utils.MessagesUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

import static org.bukkit.Bukkit.getLogger;

public class ActionManager {
    public static void executeActions(Player player, List<String> actions) {
        for (String action : actions) {
            executeAction(player, action);
        }
    }

    private static void executeAction(Player player, String action) {
        String[] actionParts = action.split(";", 2);

        if (actionParts.length >= 2) {
            String actionTarget = actionParts[0].toUpperCase();
            String actionCommand = actionParts[1];

            switch (actionTarget) {
                case "CONSOLE" -> {
                    String consoleCommand = MessagesUtil.formatPlaceholders(player, actionCommand);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), consoleCommand);
                }
                case "PLAYER" -> {
                    String playerCommand = MessagesUtil.formatPlaceholders(player, actionCommand);
                    player.chat("/" + playerCommand);
                }
                case "MESSAGE" -> {
                    String message = MessagesUtil.format(player, actionCommand);
                    player.sendMessage(message);
                }
                default -> getLogger().warning("Invalid action target: " + actionTarget);
            }
        } else if (actionParts.length == 1) {
            String actionTarget = actionParts[0].toUpperCase();
            switch (actionTarget) {
                case "CLOSE" -> player.closeInventory();
                default -> getLogger().warning("Invalid action target: " + actionTarget);
            }
        }
    }
}