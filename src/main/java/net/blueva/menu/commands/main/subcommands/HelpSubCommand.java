package net.blueva.menu.commands.main.subcommands;

import net.blueva.menu.Main;
import net.blueva.menu.commands.CommandInterface;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
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
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message.replace("{plugin_version}", main.pluginversion)));
            }
        } else {
            sender.sendMessage("You do not have permission to execute this command.");
        }

        return true;
    }



}