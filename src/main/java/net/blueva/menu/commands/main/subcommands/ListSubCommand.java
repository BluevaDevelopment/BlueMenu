package net.blueva.menu.commands.main.subcommands;

import net.blueva.menu.Main;
import net.blueva.menu.commands.CommandInterface;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ListSubCommand implements CommandInterface
{

    private final Main main;

    public ListSubCommand(Main main) {
        this.main = main;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command cmd, String commandLabel, String @NotNull [] args) {
        if (sender.hasPermission("bluemenu.list")) {
            if (args.length == 2) {
                String arg = args[1].toLowerCase();

                if (arg.equalsIgnoreCase("bedrock")) {
                    sender.sendMessage(ChatColor.RED + "Bedrock menus are not yet available.");
                } else if (arg.equalsIgnoreCase("java")) {
                    String menuList = String.join(", ", main.javaMenuManager.menuNames);

                    List<String> list = main.configManager.getLang().getStringList("commands.bluemenu.list");
                    for (String message : list) {
                        message = message.replace("{type}", "JAVA");
                        message = message.replace("{number_menus}", String.valueOf(main.javaMenuManager.menuNames.size()));
                        message = message.replace("{list_menus}", menuList);

                        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                    }
                } else {
                    sender.sendMessage("Invalid argument. Usage: /bm list (java/bedrock)");
                }
            } else {
                sender.sendMessage("Invalid usage. Usage: /bm list (java/bedrock)");
            }
        } else {
            sender.sendMessage("You do not have permission to execute this command.");
        }
        return true;
    }




}