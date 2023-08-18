package net.blueva.menu.commands.main.subcommands;

import net.blueva.menu.Main;
import net.blueva.menu.commands.CommandInterface;
import net.blueva.menu.utils.MessagesUtil;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class HelpSubCommand implements CommandInterface
{

    private final Main main;

    public HelpSubCommand(Main main) {
        this.main = main;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command cmd,
                             String commandLabel, String @NotNull [] args) {
        if(sender.hasPermission("bluemenu.help")) {
            List<String> list = main.configManager.getLang().getStringList("commands.bluemenu.help");
            for (String message : list) {
                sender.sendMessage(MessagesUtil.format((Player) sender, message.replace("{plugin_version}", main.pluginversion)));
            }
        } else {
            sender.sendMessage(MessagesUtil.format((Player) sender, main.configManager.getLang().getString("global.error.insufficient_permissions")));
        }

        return true;
    }



}