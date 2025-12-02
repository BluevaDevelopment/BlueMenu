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
            MessagesUtil.sendMessage(sender, main.configManager.getLang().getString("commands.bluemenu.confirm.players_only"));
            return true;
        }

        if (!player.hasPermission("bluemenu.editor")) {
            MessagesUtil.sendMessage(player,
                main.configManager.getLang().getString("commands.bluemenu.confirm.insufficient_permissions")
            );
            return true;
        }

        if (args.length < 2) {
            MessagesUtil.sendMessage(player, main.configManager.getLang().getString("commands.bluemenu.confirm.usage"));
            return true;
        }

        if (!main.getWebEditorManager().isEnabled()) {
            MessagesUtil.sendMessage(player, main.configManager.getLang().getString("commands.bluemenu.confirm.disabled"));
            return true;
        }

        String verificationId = args[1];
        MessagesUtil.sendMessage(player, main.configManager.getLang().getString("commands.bluemenu.confirm.starting"));

        main.getWebEditorManager().confirmSession(verificationId, player).thenAccept(confirmed ->
            main.getServer().getScheduler().runTask(main, () -> {
                if (confirmed) {
                    MessagesUtil.sendMessage(player, main.configManager.getLang().getString("commands.bluemenu.confirm.success"));
                    main.getLogger().info("Verification " + verificationId + " confirmed by " + player.getName());
                } else {
                    MessagesUtil.sendMessage(player, main.configManager.getLang().getString("commands.bluemenu.confirm.failed"));
                }
            })
        ).exceptionally(ex -> {
            main.getServer().getScheduler().runTask(main, () -> {
                String errorMessage = main.configManager.getLang().getString("commands.bluemenu.confirm.failed_with_reason");
                MessagesUtil.sendMessage(player, errorMessage != null ? errorMessage.replace("{error}", ex.getMessage()) : "");
                main.getLogger().warning("Failed to confirm verification " + verificationId + " for " + player.getName() + ": " + ex.getMessage());
            });
            return null;
        });

        return true;
    }
}
