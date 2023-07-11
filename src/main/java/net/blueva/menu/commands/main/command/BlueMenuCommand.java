package net.blueva.menu.commands.main.command;

import net.blueva.menu.Main;
import net.blueva.menu.commands.CommandInterface;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public class BlueMenuCommand implements CommandInterface
{

    private Main main;

    public BlueMenuCommand(Main main) {
        this.main = main;
    }

    //The command should be automatically created.
    @Override
    public boolean onCommand(CommandSender sender, Command cmd,
                             String commandLabel, String[] args) {

        if(sender.hasPermission("bluemenu.info") || sender.hasPermission("bluemenu.*")) {
            /*List<String> bminfo = "";
            for (String message : bminfo) {
                String player = sender.getName();
                sender.sendMessage();
            }*/
            sender.sendMessage("Working!");
        }
        return false;
    }

}