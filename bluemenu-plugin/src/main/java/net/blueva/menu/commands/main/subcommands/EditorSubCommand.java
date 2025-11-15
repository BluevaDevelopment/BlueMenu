package net.blueva.menu.commands.main.subcommands;

import net.blueva.menu.Main;
import net.blueva.menu.commands.CommandInterface;
import net.blueva.menu.utils.MessagesUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;

public class EditorSubCommand implements CommandInterface {

    private Main main;

    public EditorSubCommand(Main main) {
        this.main = main;
    }

    @Override
    public void onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) throws IOException {
        if (!(sender instanceof Player)) {
            sender.sendMessage(MessagesUtil.getColoredMessage("&cThis command can only be used by players."));
            return;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("bluemenu.editor")) {
            player.sendMessage(MessagesUtil.getColoredMessage(
                main.getConfigManager().getLanguage().getString("commands.bluemenu.no_permission")
            ));
            return;
        }

        // Check if web editor is enabled
        if (!main.getWebEditorManager().isEnabled()) {
            player.sendMessage(MessagesUtil.getColoredMessage("&cWeb editor is disabled in config.yml"));
            return;
        }

        // Show loading message
        player.sendMessage(MessagesUtil.getColoredMessage("&eCreating editor session..."));

        // Create session asynchronously
        main.getWebEditorManager().createSession().thenAccept(sessionId -> {
            // Run on main thread to send message
            main.getServer().getScheduler().runTask(main, () -> {
                String editorUrl = main.getWebEditorManager().getEditorUrl(sessionId);

                // Send clickable link
                Component message = Component.text()
                    .append(Component.text("✓ ", NamedTextColor.GREEN))
                    .append(Component.text("Editor session created!", NamedTextColor.YELLOW))
                    .append(Component.newline())
                    .append(Component.text("Click here to open: ", NamedTextColor.GRAY))
                    .append(Component.text(editorUrl, NamedTextColor.AQUA)
                        .decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl(editorUrl)))
                    .append(Component.newline())
                    .append(Component.text("Session expires in 1 hour", NamedTextColor.DARK_GRAY, TextDecoration.ITALIC))
                    .build();

                player.sendMessage(message);

                main.getLogger().info("Editor session created for " + player.getName() + ": " + sessionId);
            });
        }).exceptionally(ex -> {
            // Run on main thread to send error
            main.getServer().getScheduler().runTask(main, () -> {
                player.sendMessage(MessagesUtil.getColoredMessage("&cFailed to create editor session: " + ex.getMessage()));
                main.getLogger().warning("Failed to create editor session for " + player.getName() + ": " + ex.getMessage());
            });
            return null;
        });
    }
}
