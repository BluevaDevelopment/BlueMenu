package net.blueva.menu.commands.main.subcommands;

import net.blueva.menu.Main;
import net.blueva.menu.commands.CommandInterface;
import net.blueva.menu.managers.java.AnimationManager;
import net.blueva.menu.utils.MessagesUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
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
            main.reloadConfig();
            main.configManager.reloadLang();
            main.javaMenuManager.loadJavaMenus();
            if(Main.isUsingFloodgate) {
                main.bedrockMenuManager.loadBedrockMenus();
            }
            sender.sendMessage(MessagesUtil.format((Player) sender, main.configManager.getLang().getString("global.success.configuration_reloaded")));
        } else {
            sender.sendMessage(MessagesUtil.format((Player) sender, main.configManager.getLang().getString("global.error.insufficient_permissions")));
        }

        return true;
    }
}