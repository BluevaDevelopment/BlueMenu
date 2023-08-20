package net.blueva.menu.commands.main.subcommands;

import net.blueva.menu.Main;
import net.blueva.menu.commands.CommandInterface;
import net.blueva.menu.utils.MessagesUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;
import org.jetbrains.annotations.NotNull;

public class OpenSubCommand implements CommandInterface {

    private final Main main;

    public OpenSubCommand(Main main) {
        this.main = main;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command cmd,
                             String commandLabel, String @NotNull [] args) {

        if (args.length < 3) {
            sender.sendMessage(MessagesUtil.format(null, main.configManager.getLang().getString("global.other.use_open_subcommand")));
            return true;
        }

        String platform = args[1];
        String menuName = args[2];
        Player targetPlayer = null;

        if (sender instanceof Player player) {
            if (player.hasPermission("bluemenu.open")) {
                targetPlayer = player;
            } else {
                sender.sendMessage(MessagesUtil.format(player, main.configManager.getLang().getString("global.error.insufficient_permissions")));
                return true;
            }
        }

        if (args.length >= 4 && sender.hasPermission("bluemenu.open.others")) {
            String targetPlayerName = args[3];
            targetPlayer = main.getServer().getPlayer(targetPlayerName);
            if (targetPlayer == null) {
                sender.sendMessage(MessagesUtil.format(null, main.configManager.getLang().getString("global.error.player_offline")));
                return true;
            }
        } else if (targetPlayer == null) {
            sender.sendMessage(MessagesUtil.format(null, main.configManager.getLang().getString("global.error.insufficient_permissions")));
            return true;
        }

        if (platform.equalsIgnoreCase("auto")) {
            if(Main.isUsingFloodgate) {
                if(FloodgateApi.getInstance().isFloodgatePlayer(targetPlayer.getUniqueId())) {
                    main.bedrockMenuManager.openMenu(targetPlayer, menuName);
                } else {
                    main.javaMenuManager.openMenu(targetPlayer, menuName);
                }
            } else {
                main.javaMenuManager.openMenu(targetPlayer, menuName);
            }
        } else if (platform.equalsIgnoreCase("java")) {
            main.javaMenuManager.openMenu(targetPlayer, menuName);
        } else if (platform.equalsIgnoreCase("bedrock")) {
            if(Main.isUsingFloodgate) {
                main.bedrockMenuManager.openMenu(targetPlayer, menuName);
            } else {
                sender.sendMessage(MessagesUtil.format(null, main.configManager.getLang().getString("global.error.bedrock_disabled")));
            }
        } else {
            sender.sendMessage(MessagesUtil.format(null, main.configManager.getLang().getString("global.error.invalid_platform")));
            return true;
        }

        return true;
    }
}
