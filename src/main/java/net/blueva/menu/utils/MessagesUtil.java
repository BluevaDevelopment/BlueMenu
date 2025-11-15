package net.blueva.menu.utils;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import net.blueva.menu.Main;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessagesUtil {
    private static final MiniMessage miniMessage = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacyAmpersand();
    private static String cachedPrefix = null;

    public static @NotNull String format (Player player, String text) {
        if(player == null) {
            return formatColors(text);
        }
        if(text == null) {
            text = "";
        }
        String textfinal = formatPlaceholders(player, text);
        return formatColors(textfinal);
    }

    public static @NotNull List<String> format(Player player, List<String> textList) {
        List<String> formattedTextList = new ArrayList<>();

        for (String text : textList) {
            String formattedText = formatPlaceholders(player, text);
            formattedText = formatColors(formattedText);
            formattedTextList.add(formattedText);
        }

        return formattedTextList;
    }

    public static String formatPlaceholders(Player player, String text) {
        if(player != null) {
            text = text
                    .replace("{player_name}", player.getName())
                    .replace("{player_displayname}", player.getDisplayName())
                    .replace("{player_ping}", String.valueOf(player.getPing()))
                    .replace("{player_exp}", String.valueOf(player.getExp()))
                    .replace("{player_health}", String.valueOf(player.getHealth()))
                    .replace("{player_level}", String.valueOf(player.getLevel()))
                    .replace("{player_location_x}", String.valueOf(Math.round(player.getLocation().getX())))
                    .replace("{player_location_y}", String.valueOf(Math.round(player.getLocation().getY())))
                    .replace("{player_location_z}", String.valueOf(Math.round(player.getLocation().getZ())))
                    .replace("{server_name}", Bukkit.getServer().getName())
                    .replace("{server_max_players}", String.valueOf(Bukkit.getServer().getMaxPlayers()))
                    .replace("{server_version}", Bukkit.getServer().getVersion());

            if(Main.isUsingPAPI) {
                return PlaceholderAPI.setPlaceholders(player, text);
            }
        }

        return text;
    }

    public static @NotNull String formatColors(String text) {
        if(text == null) {
            return "";
        }

        // Replace {prefix} with the actual prefix from language file
        text = replacePrefixPlaceholder(text);

        // Convert legacy color codes to MiniMessage format
        text = convertLegacyToMiniMessage(text);

        // Process with MiniMessage and convert back to legacy for Bukkit compatibility
        Component component = miniMessage.deserialize(text);
        return legacySerializer.serialize(component);
    }

    private static String replacePrefixPlaceholder(String text) {
        if(!text.contains("{prefix}")) {
            return text;
        }

        if(cachedPrefix == null) {
            // Get prefix from language file
            String rawPrefix = Main.getPlugin().language.getString("prefix", "");
            if(!rawPrefix.isEmpty()) {
                cachedPrefix = formatColors(rawPrefix);
            } else {
                cachedPrefix = "";
            }
        }

        return text.replace("{prefix}", cachedPrefix);
    }

    public static void clearPrefixCache() {
        cachedPrefix = null;
    }

    private static String convertLegacyToMiniMessage(String text) {
        // Pattern to match legacy color codes
        Pattern hexPattern = Pattern.compile("&#([A-Fa-f0-9]{6})");
        Matcher hexMatcher = hexPattern.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (hexMatcher.find()) {
            hexMatcher.appendReplacement(sb, "<color:#" + hexMatcher.group(1) + ">");
        }
        hexMatcher.appendTail(sb);
        text = sb.toString();

        // Convert gradient patterns like &b&lBlue&9&lMenu to MiniMessage gradient
        text = convertGradientPatterns(text);

        // Convert standard color codes
        text = text.replace("&0", "<black>")
                .replace("&1", "<dark_blue>")
                .replace("&2", "<dark_green>")
                .replace("&3", "<dark_aqua>")
                .replace("&4", "<dark_red>")
                .replace("&5", "<dark_purple>")
                .replace("&6", "<gold>")
                .replace("&7", "<gray>")
                .replace("&8", "<dark_gray>")
                .replace("&9", "<blue>")
                .replace("&a", "<green>")
                .replace("&b", "<aqua>")
                .replace("&c", "<red>")
                .replace("&d", "<light_purple>")
                .replace("&e", "<yellow>")
                .replace("&f", "<white>");

        // Convert formatting codes
        text = text.replace("&l", "<bold>")
                .replace("&m", "<strikethrough>")
                .replace("&n", "<underlined>")
                .replace("&o", "<italic>")
                .replace("&k", "<obfuscated>")
                .replace("&r", "<reset>");

        return text;
    }

    private static String convertGradientPatterns(String text) {
        // Special handling for &b&lBlue&9&lMenu pattern -> gradient
        Pattern blueMenuPattern = Pattern.compile("&b&l(Blue)&9&l(Menu)");
        Matcher blueMenuMatcher = blueMenuPattern.matcher(text);
        if(blueMenuMatcher.find()) {
            text = blueMenuMatcher.replaceAll("<bold><gradient:#55FFFF:#5555FF>BlueMenu</gradient></bold>");
        }

        // General pattern for detecting potential gradients (color1 + text + color2)
        // This is a simple implementation - you can enhance it if needed
        return text;
    }
}