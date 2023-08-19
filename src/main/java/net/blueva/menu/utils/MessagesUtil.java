package net.blueva.menu.utils;

import me.clip.placeholderapi.PlaceholderAPI;
import net.blueva.menu.libraries.iridiumcolor.IridiumColorAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import net.blueva.menu.Main;

import java.util.ArrayList;
import java.util.List;

public class MessagesUtil {
    public static @NotNull String format (Player player, String text) {
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

        return IridiumColorAPI.process(text);
    }
}