package net.blueva.menu.webeditor;

import org.bukkit.plugin.Plugin;
import org.bukkit.entity.Player;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Manages the web editor connection
 */
public class WebEditorManager {
    // Official BlueMenu Web Editor URLs - hardcoded for all users
    private static final String WEBSOCKET_URL = "wss://menu.blueva.net/ws";
    private static final String EDITOR_BASE_URL = "https://menu.blueva.net";

    private final Plugin plugin;
    private final Logger logger;
    private final boolean enabled;
    private final boolean requireSessionConfirmation;
    private WebEditorClient client;

    public WebEditorManager(Plugin plugin, boolean enabled, boolean requireSessionConfirmation) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.enabled = enabled;
        this.requireSessionConfirmation = requireSessionConfirmation;
    }

    /**
     * Connect to the web editor server
     */
    public void connect() {
        if (!enabled) {
            logger.info("Web editor is disabled in config");
            return;
        }

        try {
            URI serverUri = new URI(WEBSOCKET_URL);
            client = new WebEditorClient(serverUri, (net.blueva.menu.Main) plugin, requireSessionConfirmation);
            client.connect();
            logger.info("Connecting to official BlueMenu web editor at " + WEBSOCKET_URL);
        } catch (Exception e) {
            logger.severe("Failed to connect to web editor server: " + e.getMessage());
        }
    }

    /**
     * Disconnect from the web editor server
     */
    public void disconnect() {
        if (client != null && client.isOpen()) {
            client.close();
            logger.info("Disconnected from web editor server");
        }
    }

    /**
     * Create a new editor session
     * @return CompletableFuture with the session ID
     */
    public CompletableFuture<String> createSession() {
        if (!enabled) {
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(new RuntimeException("Web editor is disabled"));
            return future;
        }

        if (client == null || !client.isOpen()) {
            logger.warning("WebSocket not connected, attempting to reconnect...");
            connect();

            // Wait a bit for connection
            CompletableFuture<String> future = new CompletableFuture<>();
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (client != null && client.isOpen()) {
                    client.requestSession(null).thenAccept(future::complete)
                        .exceptionally(ex -> {
                            future.completeExceptionally(ex);
                            return null;
                        });
                } else {
                    future.completeExceptionally(new RuntimeException("Could not connect to web editor server"));
                }
            }, 20L); // Wait 1 second

            return future;
        }

        return client.requestSession(null);
    }

    /**
     * Check if the web editor is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Check if connected to the server
     */
    public boolean isConnected() {
        return client != null && client.isOpen();
    }

    /**
     * Get the editor URL for a session
     */
    public String getEditorUrl(String sessionId) {
        return String.format("%s/editor/%s", EDITOR_BASE_URL, sessionId);
    }

    /**
     * Confirm a session id for a specific player
     */
    public CompletableFuture<Boolean> confirmSession(String sessionId, Player player) {
        if (!enabled) {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            future.completeExceptionally(new RuntimeException("Web editor is disabled"));
            return future;
        }

        if (client == null || !client.isOpen()) {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            future.completeExceptionally(new RuntimeException("Web editor connection is not open"));
            return future;
        }

        return client.confirmSession(sessionId, player.getUniqueId());
    }

}
