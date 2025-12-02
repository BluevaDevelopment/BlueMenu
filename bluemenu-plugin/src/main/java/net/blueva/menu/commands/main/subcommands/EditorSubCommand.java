package net.blueva.menu.commands.main.subcommands;

import net.blueva.menu.Main;
import net.blueva.menu.commands.CommandInterface;
import net.blueva.menu.utils.MessagesUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.List;

public class EditorSubCommand implements CommandInterface {

    private Main main;

    public EditorSubCommand(Main main) {
        this.main = main;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) throws IOException {
        if (!(sender instanceof Player)) {
            MessagesUtil.sendMessage(sender, main.configManager.getLang().getString("commands.bluemenu.editor.players_only"));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("bluemenu.editor")) {
            MessagesUtil.sendMessage(player,
                main.configManager.getLang().getString("commands.bluemenu.editor.insufficient_permissions")
            );
            return true;
        }

        // Check if web editor is enabled
        if (!main.getWebEditorManager().isEnabled()) {
            MessagesUtil.sendMessage(player, main.configManager.getLang().getString("commands.bluemenu.editor.disabled"));
            return true;
        }

        // Show loading message
        MessagesUtil.sendMessage(player, main.configManager.getLang().getString("commands.bluemenu.editor.creating_session"));

        // Create session asynchronously
        main.getWebEditorManager().createSession().thenAccept(sessionId -> {
            // Run on main thread to send message
            main.getServer().getScheduler().runTask(main, () -> {
                String editorUrl = main.getWebEditorManager().getEditorUrl(sessionId);

                List<String> createdMessages = main.configManager.getLang().getStringList("commands.bluemenu.editor.session_created");
                for (String message : createdMessages) {
                    message = message
                        .replace("{editor_url}", editorUrl)
                        .replace("{session_id}", sessionId)
                        .replace("{expires_in}", "1 hour");

                    MessagesUtil.sendMessage(player, message);
                }

                main.getLogger().info("Editor session created for " + player.getName() + ": " + sessionId);
            });
        }).exceptionally(ex -> {
            // Run on main thread to send error
            main.getServer().getScheduler().runTask(main, () -> {
                String errorMessage = main.configManager.getLang().getString("commands.bluemenu.editor.creation_failed_with_reason");
                MessagesUtil.sendMessage(player, errorMessage != null ? errorMessage.replace("{error}", ex.getMessage()) : "");
                main.getLogger().warning("Failed to create editor session for " + player.getName() + ": " + ex.getMessage());
            });
            return null;
        });

        return true;
    }
}
