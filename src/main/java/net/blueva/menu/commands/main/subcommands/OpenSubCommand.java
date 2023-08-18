package net.blueva.menu.commands.main.subcommands;

import net.blueva.menu.Main;
import net.blueva.menu.commands.CommandInterface;
import net.blueva.menu.utils.MessagesUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class OpenSubCommand implements CommandInterface {

    private final Main main;

    public OpenSubCommand(Main main) {
        this.main = main;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command cmd,
                             String commandLabel, String @NotNull [] args) {
        if (args.length >= 3) {
            String platform = args[1];
            String menuName = args[2];
            Player targetPlayer = null;

            if (platform.equalsIgnoreCase("java")) {
                if (sender instanceof Player player) {
                    if (player.hasPermission("bluemenu.open")) {
                        targetPlayer = player;
                    } else {
                        sender.sendMessage(MessagesUtil.format((Player) sender, main.configManager.getLang().getString("global.error.insufficient_permissions")));
                        return true;
                    }
                }
            } else if (platform.equalsIgnoreCase("bedrock")) {
                sender.sendMessage("The Bedrock platform support is coming soon.");
                return true;
            } else {
                sender.sendMessage(MessagesUtil.format((Player) sender, main.configManager.getLang().getString("global.error.invalid_platform")));
                return true;
            }

            if (args.length >= 4 && sender.hasPermission("bluemenu.open.others")) {
                String targetPlayerName = args[3];
                targetPlayer = Bukkit.getPlayer(targetPlayerName);
                if (targetPlayer == null) {
                    sender.sendMessage(MessagesUtil.format((Player) sender, main.configManager.getLang().getString("global.error.player_offline")));
                    return true;
                }
            } else {
                sender.sendMessage(MessagesUtil.format((Player) sender, main.configManager.getLang().getString("global.error.insufficient_permissions")));
                return true;
            }

            main.javaMenuManager.openMenu(targetPlayer, menuName);
        } else {
            sender.sendMessage(MessagesUtil.format((Player) sender, main.configManager.getLang().getString("global.info.use_open_subcommand")));
        }

        return true;
    }

}
