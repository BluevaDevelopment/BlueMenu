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

    @Override
    public boolean onCommand(CommandSender sender, Command cmd,
                             String commandLabel, String[] args) {

        if(sender.hasPermission("bluemenu.info") || sender.hasPermission("bluemenu.*")) {
            sender.sendMessage("test");
        }
        return false;
    }

}