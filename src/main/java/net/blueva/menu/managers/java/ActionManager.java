package net.blueva.menu.managers.java;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.blueva.menu.Main;
import net.blueva.menu.utils.MessagesUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.bukkit.Bukkit.getLogger;

public class ActionManager {
    private static final String BUNGEE_CHANNEL = "BungeeCord";
    private static final String VELOCITY_CHANNEL = "velocity:player_info";

    public static void executeActions(Player player, List<String> actions, ClickType clickType) {
        String clickTypeString = resolveClickType(clickType);

        for (String action : actions) {
            String[] actionParts = action.split(" ", 2);

            if (actionParts.length == 2) {
                String actionType = actionParts[0].toUpperCase();
                String actionData = actionParts[1];

                boolean matches = actionType.equals("[" + clickTypeString + "]")
                    || actionType.equals("[ALL]")
                    || (actionType.equals("[BOTH]") && (clickType == ClickType.LEFT || clickType == ClickType.RIGHT))
                    || (actionType.equals("[BOTH_SHIFT]") && (clickType == ClickType.SHIFT_LEFT || clickType == ClickType.SHIFT_RIGHT));
                if (matches) {
                    executeClickAction(player, actionData);
                }
            }
        }
    }

    public static void executeActions(Player player, List<String> actions) {
        if (actions == null || actions.isEmpty()) {
            return;
        }

        for (String action : actions) {
            if (action == null || action.isBlank()) {
                continue;
            }
            executeClickAction(player, action.trim());
        }
    }

    private static String resolveClickType(ClickType clickType) {
        if (clickType == null) {
            return "";
        }
        if (clickType == ClickType.LEFT) {
            return "LEFT_CLICK";
        }
        if (clickType == ClickType.RIGHT) {
            return "RIGHT_CLICK";
        }
        if (clickType == ClickType.SHIFT_LEFT) {
            return "SHIFT_LEFT_CLICK";
        }
        if (clickType == ClickType.SHIFT_RIGHT) {
            return "SHIFT_RIGHT_CLICK";
        }
        if (clickType == ClickType.MIDDLE) {
            return "MIDDLE_CLICK";
        }
        return "";
    }

    private static void executeClickAction(Player player, String action) {
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
                    player.chat("/"+playerCommand);
                }
                case "MESSAGE" -> MessagesUtil.sendMessage(player, actionCommand);
                case "BROADCAST" -> broadcastMessage(actionCommand);
                case "SOUND" -> playSound(player, params);
                case "OPEN_MENU" -> openJavaMenu(player, actionCommand);
                case "CONNECT" -> connectToServer(player, actionCommand);
                case "CONNECT_BUNGEE" -> connectToServer(player, actionCommand, BUNGEE_CHANNEL);
                case "CONNECT_VELOCITY" -> connectToServer(player, actionCommand, VELOCITY_CHANNEL);
                default -> getLogger().warning("Invalid action target: " + actionTarget);
            }
        } else if (actionParts.length == 1) {
            switch (actionTarget) {
                case "CLOSE" -> player.closeInventory();
                case "REFRESH_MENU" -> refreshJavaMenu(player);
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

    private static void openJavaMenu(Player player, String menuName) {
        if (menuName == null || menuName.isBlank()) {
            getLogger().warning("Open menu action missing menu name.");
            return;
        }
        Main.getPlugin().javaMenuManager.openMenu(player, menuName.trim());
    }

    private static void refreshJavaMenu(Player player) {
        String menuName = PlayerManager.getMenuName(player);
        if (menuName == null || menuName.isBlank()) {
            getLogger().warning("Refresh menu action failed: no menu is open.");
            return;
        }
        Main.getPlugin().javaMenuManager.openMenu(player, menuName);
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

    private static void connectToServer(Player player, String serverName) {
        String channel = resolveProxyChannel();
        if (channel == null) {
            getLogger().warning("Connect action failed: proxy support is not enabled (BungeeCord or Velocity).");
            return;
        }
        connectToServer(player, serverName, channel);
    }

    private static String resolveProxyChannel() {
        if (isBungeeEnabled()) {
            return BUNGEE_CHANNEL;
        }
        if (isVelocityEnabled()) {
            return VELOCITY_CHANNEL;
        }
        return null;
    }

    private static boolean isBungeeEnabled() {
        try {
            return Bukkit.spigot().getConfig().getBoolean("settings.bungeecord");
        } catch (Exception ex) {
            return false;
        }
    }

    private static boolean isVelocityEnabled() {
        File paperGlobal = new File("paper-global.yml");
        if (paperGlobal.isFile()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(paperGlobal);
            if (config.getBoolean("proxies.velocity.enabled", false)) {
                return true;
            }
        }

        File paperConfig = new File("paper.yml");
        if (paperConfig.isFile()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(paperConfig);
            if (config.getBoolean("settings.velocity-support.enabled", false)) {
                return true;
            }
        }

        return false;
    }
}
