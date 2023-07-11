package net.blueva.menu.commands.main.subcommands;

import net.blueva.menu.Main;
import net.blueva.menu.commands.CommandInterface;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class OpenSubCommand implements CommandInterface
{

    private final Main main;

    public OpenSubCommand(Main main) {
        this.main = main;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command cmd,
                             String commandLabel, String @NotNull [] args) throws IOException {
        if (args.length >= 2) {
            String menuName = args[1];
            Player targetPlayer = null;

            if (sender instanceof Player player) {
                if (player.hasPermission("bluemenu.open")) {
                    targetPlayer = player;
                } else {
                    sender.sendMessage("You do not have permission to execute this command.");
                    return true;
                }
            } else if (args.length >= 3 && sender.hasPermission("bluemenu.open.others")) {
                String targetPlayerName = args[2];
                targetPlayer = Bukkit.getPlayer(targetPlayerName);
                if (targetPlayer == null) {
                    sender.sendMessage("The specified player is not online.");
                    return true;
                }
            } else {
                sender.sendMessage("You do not have permission to execute this command.");
                return true;
            }

            main.javaMenuManager.openMenu(targetPlayer, menuName);
        } else {
            sender.sendMessage("Incorrect use of the command. Use: /bluemenu open <menu> [player]");
        }

        return true;
    }



}