package net.blueva.menu.webeditor;

import org.bukkit.plugin.Plugin;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Manages the web editor connection
 */
public class WebEditorManager {
    private final Plugin plugin;
    private final Logger logger;
    private final String serverUrl;
    private final boolean enabled;
    private WebEditorClient client;

    public WebEditorManager(Plugin plugin, String serverUrl, boolean enabled) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.serverUrl = serverUrl;
        this.enabled = enabled;
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
            URI serverUri = new URI(serverUrl);
            client = new WebEditorClient(serverUri);
            client.connect();
            logger.info("Connecting to web editor server at " + serverUrl);
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
        try {
            URI uri = new URI(serverUrl);
            String host = uri.getHost();
            int port = uri.getPort();
            String scheme;

            // If port is -1, assume Caddy with HTTPS on default port 443
            if (port == -1) {
                scheme = "https";
                return String.format("%s://%s/editor/%s", scheme, host, sessionId);
            }

            // Old behavior: websocket was on 8081, HTTP on 8080
            if (port == 8081) {
                port = 8080;
            }

            scheme = "http";

            return String.format("%s://%s:%d/editor/%s", scheme, host, port, sessionId);

        } catch (Exception e) {
            logger.severe("Error generating editor URL: " + e.getMessage());
            return "Error generating URL";
        }
    }

}
