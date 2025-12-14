package net.blueva.menu.commands.main.subcommands;

import net.blueva.menu.Main;
import net.blueva.menu.commands.CommandInterface;
import net.blueva.menu.utils.MessagesUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class ConfirmSubCommand implements CommandInterface {

    private static final UUID CONSOLE_CONFIRMATION_UUID =
        UUID.nameUUIDFromBytes("BLUEMENU_CONSOLE".getBytes(StandardCharsets.UTF_8));
    private final Main main;

    public ConfirmSubCommand(Main main) {
        this.main = main;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) throws IOException {
        if (!sender.hasPermission("bluemenu.editor")) {
            MessagesUtil.sendMessage(sender,
                main.configManager.getLang().getString("commands.bluemenu.confirm.insufficient_permissions")
            );
            return true;
        }

        if (args.length < 2) {
            MessagesUtil.sendMessage(sender, main.configManager.getLang().getString("commands.bluemenu.confirm.usage"));
            return true;
        }

        if (!main.getWebEditorManager().isEnabled()) {
            MessagesUtil.sendMessage(sender, main.configManager.getLang().getString("commands.bluemenu.confirm.disabled"));
            return true;
        }

        String verificationId = args[1];
        MessagesUtil.sendMessage(sender, main.configManager.getLang().getString("commands.bluemenu.confirm.starting"));

        UUID confirmerId = sender instanceof Player player
            ? player.getUniqueId()
            : CONSOLE_CONFIRMATION_UUID;
        String confirmerName = sender.getName();

        main.getWebEditorManager().confirmSession(verificationId, confirmerId).thenAccept(confirmed ->
            main.getServer().getScheduler().runTask(main, () -> {
                if (confirmed) {
                    MessagesUtil.sendMessage(sender, main.configManager.getLang().getString("commands.bluemenu.confirm.success"));
                    main.getLogger().info("Verification " + verificationId + " confirmed by " + confirmerName);
                } else {
                    MessagesUtil.sendMessage(sender, main.configManager.getLang().getString("commands.bluemenu.confirm.failed"));
                }
            })
        ).exceptionally(ex -> {
            main.getServer().getScheduler().runTask(main, () -> {
                String errorMessage = main.configManager.getLang().getString("commands.bluemenu.confirm.failed_with_reason");
                MessagesUtil.sendMessage(sender, errorMessage != null ? errorMessage.replace("{error}", ex.getMessage()) : "");
                main.getLogger().warning("Failed to confirm verification " + verificationId + " for " + confirmerName + ": " + ex.getMessage());
            });
            return null;
        });

        return true;
    }
}
