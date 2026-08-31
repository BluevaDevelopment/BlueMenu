package net.blueva.menu.webeditor;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.UUID;

/**
 * Manages the web editor connection
 */
public class WebEditorManager {
    private final Plugin plugin;
    private final Logger logger;
    private final boolean enabled;
    private final boolean requireSessionConfirmation;
    private final WebEditorEnvironment environment;
    private WebEditorClient client;
    private BukkitTask watchdogTask;

    /** How often the watchdog pings / checks the connection, in ticks (15s). */
    private static final long WATCHDOG_INTERVAL_TICKS = 20L * 15;

    public WebEditorManager(Plugin plugin, boolean enabled, boolean requireSessionConfirmation,
                            WebEditorEnvironment environment) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.enabled = enabled;
        this.requireSessionConfirmation = requireSessionConfirmation;
        this.environment = environment;
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
            URI serverUri = new URI(environment.websocketUrl());
            client = new WebEditorClient(serverUri, (net.blueva.menu.Main) plugin, requireSessionConfirmation);
            client.connect();
            logger.info("Connecting to " + environment.displayName() + " BlueMenu web editor at "
                + environment.websocketUrl());
            if (environment == WebEditorEnvironment.DEVELOPMENT) {
                logger.warning("Development environment is intended for plugin contributors only."
                    + " Do not use on production servers.");
            }
            startWatchdog();
        } catch (Exception e) {
            logger.severe("Failed to connect to web editor server: " + e.getMessage());
        }
    }

    /**
     * Periodic keepalive + auto-reconnect. Proxies in front of the official server
     * silently drop idle WebSockets; without this the editor goes dark until a restart.
     */
    private void startWatchdog() {
        stopWatchdog();
        watchdogTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!enabled) {
                return;
            }
            try {
                if (client == null || client.isClosed()) {
                    reconnect();
                } else if (client.isOpen()) {
                    client.sendPing();
                }
            } catch (Exception e) {
                logger.fine("Web editor watchdog error: " + e.getMessage());
            }
        }, WATCHDOG_INTERVAL_TICKS, WATCHDOG_INTERVAL_TICKS);
    }

    private void stopWatchdog() {
        if (watchdogTask != null) {
            watchdogTask.cancel();
            watchdogTask = null;
        }
    }

    private void reconnect() {
        if (!enabled) {
            return;
        }
        WebEditorClient old = client;
        if (old != null) {
            try {
                old.closeBlocking();
            } catch (Exception ignored) {
                // best effort
            }
        }
        try {
            URI serverUri = new URI(environment.websocketUrl());
            client = new WebEditorClient(serverUri, (net.blueva.menu.Main) plugin, requireSessionConfirmation);
            client.connect();
            logger.info("Reconnecting to BlueMenu web editor at " + environment.websocketUrl());
        } catch (Exception e) {
            logger.warning("Web editor reconnect failed: " + e.getMessage());
        }
    }

    /**
     * Disconnect from the web editor server
     */
    public void disconnect() {
        stopWatchdog();
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
        return String.format("%s/editor/%s", environment.editorBaseUrl(), sessionId);
    }

    /**
     * Confirm a verification id for a specific player
     */
    public CompletableFuture<Boolean> confirmSession(String verificationId, UUID confirmedBy) {
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

        return client.confirmSession(verificationId, confirmedBy);
    }

    public CompletableFuture<Boolean> confirmSession(String verificationId, Player player) {
        return confirmSession(verificationId, player.getUniqueId());
    }

    public enum WebEditorEnvironment {
        PRODUCTION("production", "Official", "https://menu.blueva.net", "wss://menu.blueva.net/ws"),
        DEVELOPMENT("development", "Development", "https://menu.blueva.net/dev", "wss://menu.blueva.net/dev/ws");

        private final String configKey;
        private final String displayName;
        private final String editorBaseUrl;
        private final String websocketUrl;

        WebEditorEnvironment(String configKey, String displayName, String editorBaseUrl, String websocketUrl) {
            this.configKey = configKey;
            this.displayName = displayName;
            this.editorBaseUrl = editorBaseUrl;
            this.websocketUrl = websocketUrl;
        }

        public String editorBaseUrl() {
            return editorBaseUrl;
        }

        public String websocketUrl() {
            return websocketUrl;
        }

        public String displayName() {
            return displayName;
        }

        public static WebEditorEnvironment fromConfig(String value) {
            if (value == null) {
                return PRODUCTION;
            }

            String normalized = value.trim().toLowerCase();
            for (WebEditorEnvironment environment : values()) {
                if (environment.configKey.equalsIgnoreCase(normalized)) {
                    return environment;
                }
            }

            return PRODUCTION;
        }
    }
}
