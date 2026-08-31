package net.blueva.menu.commands.main.subcommands;

import net.blueva.menu.Main;
import net.blueva.menu.commands.CommandInterface;
import net.blueva.menu.utils.MessagesUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class ReloadSubCommand implements CommandInterface
{

    private final Main main;

    public ReloadSubCommand(Main main) {
        this.main = main;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command cmd,
                             String commandLabel, String @NotNull [] args) {
        if(sender.hasPermission("bluemenu.reload")) {
            main.reloadAll();
            MessagesUtil.sendMessage(sender, main.configManager.getLang().getString("commands.bluemenu.reload.success"));
        } else {
            MessagesUtil.sendMessage(sender, main.configManager.getLang().getString("commands.bluemenu.reload.insufficient_permissions"));
        }

        return true;
    }
}
