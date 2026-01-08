package net.blueva.menu.managers.bedrock;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.blueva.menu.Main;
import net.blueva.menu.utils.MessagesUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.bukkit.Bukkit.getLogger;

public class ActionManager {
    public static void executeActions(Player player, List<String> actions) {
        for (String action : actions) {
            executeAction(player, action);
        }
    }

    private static void executeAction(Player player, String action) {
        String[] actionParts = action.split(";");
        String actionTarget = actionParts[0].toUpperCase();
        String[] params = actionParts.length > 1 ? Arrays.copyOfRange(actionParts, 1, actionParts.length) : new String[0];

        if (params.length >= 1) {
            String actionCommand = String.join(";", params);
            switch (actionTarget) {
                case "CONSOLE" -> {
                    String consoleCommand = MessagesUtil.formatPlaceholders(player, actionCommand);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), consoleCommand);
                }
                case "PLAYER" -> {
                    String playerCommand = MessagesUtil.formatPlaceholders(player, actionCommand);
                    player.chat("/" + playerCommand);
                }
                case "MESSAGE" -> MessagesUtil.sendMessage(player, actionCommand);
                case "BROADCAST" -> broadcastMessage(actionCommand);
                case "SOUND" -> playSound(player, params);
                case "OPEN_MENU" -> openBedrockMenu(player, actionCommand);
                case "CONNECT_BUNGEE" -> connectToServer(player, actionCommand, "BungeeCord");
                case "CONNECT_VELOCITY" -> connectToServer(player, actionCommand, "velocity:player_info");
                default -> getLogger().warning("Invalid action target: " + actionTarget);
            }
        } else if (actionParts.length == 1) {
            switch (actionTarget) {
                case "CLOSE" -> player.closeInventory();
                case "REFRESH_MENU" -> refreshBedrockMenu(player);
                default -> getLogger().warning("Invalid action target: " + actionTarget);
            }
        }
    }

    private static void broadcastMessage(String message) {
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            MessagesUtil.sendMessage(onlinePlayer, message);
        }
        MessagesUtil.sendMessage(Bukkit.getConsoleSender(), message);
    }

    private static void playSound(Player player, String[] params) {
        if (params.length == 0 || params[0].isBlank()) {
            getLogger().warning("Sound action missing sound name.");
            return;
        }

        String soundName = params[0].trim()
            .replace("minecraft:", "")
            .replace("MINECRAFT:", "")
            .replace(':', '_')
            .toUpperCase(Locale.ROOT);

        Sound sound;
        try {
            sound = Sound.valueOf(soundName);
        } catch (IllegalArgumentException ex) {
            getLogger().warning("Invalid sound: " + params[0]);
            return;
        }

        float volume = parseFloatOrDefault(params, 1, 1.0f);
        float pitch = parseFloatOrDefault(params, 2, 1.0f);

        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    private static float parseFloatOrDefault(String[] params, int index, float fallback) {
        if (params.length <= index || params[index] == null || params[index].isBlank()) {
            return fallback;
        }

        try {
            return Float.parseFloat(params[index].trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static void openBedrockMenu(Player player, String menuName) {
        if (menuName == null || menuName.isBlank()) {
            getLogger().warning("Open menu action missing menu name.");
            return;
        }
        Main.getPlugin().bedrockMenuManager.openMenu(player, menuName.trim());
    }

    private static void refreshBedrockMenu(Player player) {
        String menuName = Main.getPlugin().bedrockMenuManager.getLastOpenedMenu(player);
        if (menuName == null || menuName.isBlank()) {
            getLogger().warning("Refresh menu action failed: no menu is open.");
            return;
        }
        Main.getPlugin().bedrockMenuManager.openMenu(player, menuName);
    }

    private static void connectToServer(Player player, String serverName, String channel) {
        if (serverName == null || serverName.isBlank()) {
            getLogger().warning("Connect action missing server name.");
            return;
        }

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(serverName.trim());
        player.sendPluginMessage(Main.getPlugin(), channel, out.toByteArray());
    }
}
