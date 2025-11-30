package net.blueva.menu.commands.main.subcommands;

import net.blueva.menu.Main;
import net.blueva.menu.commands.CommandInterface;
import net.blueva.menu.utils.MessagesUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;

public class ConfirmSubCommand implements CommandInterface {

    private final Main main;

    public ConfirmSubCommand(Main main) {
        this.main = main;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) throws IOException {
        if (!(sender instanceof Player player)) {
            MessagesUtil.sendMessage(sender, "&cThis command can only be used by players.");
            return true;
        }

        if (!player.hasPermission("bluemenu.editor")) {
            MessagesUtil.sendMessage(player,
                main.configManager.getLang().getString("global.error.insufficient_permissions")
            );
            return true;
        }

        if (args.length < 2) {
            MessagesUtil.sendMessage(player, "&cUsage: /bluemenu confirm <sessionId>");
            return true;
        }

        if (!main.getWebEditorManager().isEnabled()) {
            MessagesUtil.sendMessage(player, "&cWeb editor is disabled in config.yml");
            return true;
        }

        String sessionId = args[1];
        MessagesUtil.sendMessage(player, "&eConfirming editor session...");

        main.getWebEditorManager().confirmSession(sessionId, player).thenAccept(confirmed ->
            main.getServer().getScheduler().runTask(main, () -> {
                if (confirmed) {
                    MessagesUtil.sendMessage(player, "&aSession confirmed! You can open the web editor now.");
                    main.getLogger().info("Session " + sessionId + " confirmed by " + player.getName());
                } else {
                    MessagesUtil.sendMessage(player, "&cFailed to confirm session. Please try again.");
                }
            })
        ).exceptionally(ex -> {
            main.getServer().getScheduler().runTask(main, () -> {
                MessagesUtil.sendMessage(player, "&cFailed to confirm session: " + ex.getMessage());
                main.getLogger().warning("Failed to confirm session " + sessionId + " for " + player.getName() + ": " + ex.getMessage());
            });
            return null;
        });

        return true;
    }
}
