package net.blueva.menu.commands.main.command;

import net.blueva.menu.Main;
import net.blueva.menu.commands.CommandInterface;
import net.blueva.menu.utils.MessagesUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.List;

public class BlueMenuCommand implements CommandInterface
{

    private Main main;

    public BlueMenuCommand(Main main) {
        this.main = main;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd,
                             String commandLabel, String[] args) {

        if(sender.hasPermission("bluemenu.info")) {
            List<String> list = main.configManager.getLang().getStringList("commands.bluemenu.info");
            for (String message : list) {
                MessagesUtil.sendMessage(sender, message.replace("{plugin_version}", main.pluginversion));
            }
        }
        return false;
    }

}