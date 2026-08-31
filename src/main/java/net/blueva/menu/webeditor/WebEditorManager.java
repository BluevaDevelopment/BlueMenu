package net.blueva.menu.webeditor;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.blueva.menu.Main;
import net.blueva.menu.common.dto.MenuMetadataDTO;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Owns the plugin's side of the web editor.
 *
 * The plugin registers once, keeps itself marked as reachable with a heartbeat,
 * and answers the operations that arrive on its private Reverb channel.
 */
public class WebEditorManager {
    private static final long HEARTBEAT_TICKS = 20L * 30;
    private static final long POLL_TICKS = 20L;

    private final Main plugin;
    private final Logger logger;
    private final boolean enabled;
    private final boolean requireSessionConfirmation;
    private final WebEditorEnvironment environment;
    private final WebEditorCredentials credentials;
    private final WebEditorApi api;
    private final WebEditorMenus menus;
    private final Gson gson = new Gson();

    private WebEditorRealtime realtime;
    private BukkitTask heartbeatTask;
    private BukkitTask pollTask;
    private volatile boolean polling = true;

    public WebEditorManager(Main plugin, boolean enabled, boolean requireSessionConfirmation,
                            WebEditorEnvironment environment) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.enabled = enabled;
        this.requireSessionConfirmation = requireSessionConfirmation;
        this.environment = environment;
        this.credentials = new WebEditorCredentials(plugin.getDataFolder(), logger);
        this.api = new WebEditorApi(environment.baseUrl(), credentials);
        this.menus = new WebEditorMenus(plugin);
    }

    public void connect() {
        if (!enabled) {
            logger.info("Web editor is disabled in config");
            return;
        }

        if (environment == WebEditorEnvironment.DEVELOPMENT) {
            logger.warning("Development environment is intended for plugin contributors only."
                + " Do not use on production servers.");
        }

        ensureRegistered()
            .thenCompose(ignored -> api.heartbeat(false))
            .thenAccept(this::startRealtime)
            .exceptionally(error -> {
                logger.severe("Could not reach the web editor: " + rootMessage(error));
                return null;
            });
    }

    public void disconnect() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
            heartbeatTask = null;
        }

        if (pollTask != null) {
            pollTask.cancel();
            pollTask = null;
        }

        if (realtime != null) {
            realtime.disconnect();
            realtime = null;
            logger.info("Disconnected from the web editor");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isConnected() {
        return polling ? heartbeatTask != null : realtime != null && realtime.isConnected();
    }

    public String getEditorUrl(String sessionId) {
        return environment.editorUrl(sessionId);
    }

    /**
     * Opens a session for /bm editor and resolves with its id.
     */
    public CompletableFuture<String> createSession() {
        if (!enabled) {
            return failed("Web editor is disabled");
        }

        return ensureRegistered()
            .thenCompose(ignored -> api.createSession(requireSessionConfirmation))
            .thenApply(response -> response.get("sessionId").getAsString());
    }

    public CompletableFuture<Boolean> confirmSession(String verificationId, UUID confirmedBy) {
        if (!enabled) {
            return failed("Web editor is disabled");
        }

        return ensureRegistered().thenCompose(ignored -> api.confirmSession(verificationId, confirmedBy));
    }

    public CompletableFuture<Boolean> confirmSession(String verificationId, Player player) {
        return confirmSession(verificationId, player.getUniqueId());
    }

    private CompletableFuture<Void> ensureRegistered() {
        if (credentials.exist()) {
            return CompletableFuture.completedFuture(null);
        }

        String serverVersion = plugin.getServer().getBukkitVersion().split("-")[0];

        return api.register(plugin.getServer().getName(),
                plugin.getDescription().getVersion(), serverVersion)
            .thenAccept(response -> credentials.store(
                response.get("uuid").getAsString(),
                response.get("token").getAsString()
            ));
    }

    private void startRealtime(JsonObject heartbeat) {
        RealtimeSettings settings = RealtimeSettings.fromJson(
            heartbeat.has("realtime") ? heartbeat.getAsJsonObject("realtime") : null);

        // Start out collecting requests over HTTP. The channel takes over only
        // once it confirms it is subscribed, so a rejected or dropped
        // subscription can never leave the editor waiting on nothing.
        startPolling();
        startHeartbeat();

        if (settings == null || !settings.isUsable()) {
            logger.warning("The web editor reported no realtime configuration, staying on polling");
            return;
        }

        realtime = new WebEditorRealtime(settings, credentials, logger, this::handleRpc, this::onRealtimeState);
        realtime.connect();
    }

    /**
     * Switches between the channel and polling, and tells the editor at once so
     * it stops publishing requests nobody is listening for.
     */
    private synchronized void onRealtimeState(boolean carryingTraffic) {
        if (carryingTraffic == !polling) {
            return;
        }

        polling = !carryingTraffic;

        if (polling) {
            logger.warning("The web editor channel is not delivering, falling back to polling");
            startPolling();
        } else {
            logger.info("The web editor channel is live, stopping the HTTP poll");
            stopPolling();
        }

        api.heartbeat(polling).exceptionally(error -> {
            logger.fine("Heartbeat failed: " + rootMessage(error));
            return null;
        });
    }

    private synchronized void stopPolling() {
        if (pollTask != null) {
            pollTask.cancel();
            pollTask = null;
        }
    }

    private void startHeartbeat() {
        heartbeatTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin,
            () -> api.heartbeat(polling).exceptionally(error -> {
                logger.fine("Heartbeat failed: " + rootMessage(error));
                return null;
            }),
            0L, HEARTBEAT_TICKS);
    }

    /**
     * Asks the editor once a second for anything waiting to be done.
     */
    private synchronized void startPolling() {
        if (pollTask != null) {
            return;
        }

        pollTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin,
            () -> api.pollRequests()
                .thenAccept(this::dispatchPending)
                .exceptionally(error -> {
                    logger.fine("Poll failed: " + rootMessage(error));
                    return null;
                }),
            0L, POLL_TICKS);
    }

    private void dispatchPending(JsonObject response) {
        if (response == null || !response.has("requests")) {
            return;
        }

        for (var element : response.getAsJsonArray("requests")) {
            JsonObject request = element.getAsJsonObject();
            handleRpc(
                request.get("id").getAsString(),
                request.get("action").getAsString(),
                request.has("payload") && request.get("payload").isJsonObject()
                    ? request.getAsJsonObject("payload")
                    : new JsonObject()
            );
        }
    }

    /**
     * Runs one browser operation. File access happens off the main thread, and
     * the pieces that touch the server state reschedule themselves.
     */
    private void handleRpc(String requestId, String action, JsonObject payload) {
        try {
            switch (action) {
                case "MENU_LIST_REQUEST" -> respondWithMenuList(requestId);
                case "MENU_GET" -> respondWithMenu(requestId, payload);
                case "MENU_SAVE" -> respondToSave(requestId, payload);
                case "MENU_DELETE" -> respondToDelete(requestId, payload);
                default -> api.respond(requestId, false, null, "Unknown action " + action);
            }
        } catch (Exception e) {
            logger.severe("Error handling web editor action " + action + ": " + e.getMessage());
            api.respond(requestId, false, null, e.getMessage());
        }
    }

    private void respondWithMenuList(String requestId) {
        List<MenuMetadataDTO> found = menus.list();
        JsonObject payload = new JsonObject();
        payload.add("menus", gson.toJsonTree(found));

        api.respond(requestId, true, payload, null);
    }

    private void respondWithMenu(String requestId, JsonObject request) {
        String platform = string(request, "platform");
        String fileName = menus.resolveFileName(string(request, "fileName"), platform);
        String content = menus.read(fileName, platform);

        if (content == null) {
            api.respond(requestId, false, null, "Menu file not found");
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("fileName", fileName);
        payload.addProperty("platform", platform);
        payload.addProperty("content", content);

        api.respond(requestId, true, payload, null);
    }

    private void respondToSave(String requestId, JsonObject request) {
        String platform = string(request, "platform");
        String fileName = string(request, "fileName");
        String content = string(request, "content");

        if (platform == null || fileName == null || content == null) {
            api.respond(requestId, false, null, "Missing required fields");
            return;
        }

        WebEditorMenus.SaveOutcome outcome = menus.save(fileName, platform, content);

        if (!outcome.saved()) {
            api.respond(requestId, false, null, "Failed to save the menu");
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("fileName", menus.resolveFileName(fileName, platform));
        payload.addProperty("platform", platform);
        if (outcome.warning() != null) {
            payload.addProperty("warning", outcome.warning());
        }

        api.respond(requestId, true, payload, null);
    }

    private void respondToDelete(String requestId, JsonObject request) {
        String platform = string(request, "platform");
        String fileName = string(request, "fileName");

        if (platform == null || fileName == null) {
            api.respond(requestId, false, null, "Missing required fields");
            return;
        }

        if (!menus.delete(fileName, platform)) {
            api.respond(requestId, false, null, "Failed to delete menu from disk");
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("fileName", fileName);
        payload.addProperty("platform", platform);

        api.respond(requestId, true, payload, null);
    }

    private String string(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : null;
    }

    private <T> CompletableFuture<T> failed(String message) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(new WebEditorApi.WebEditorException(message));

        return future;
    }

    private String rootMessage(Throwable error) {
        Throwable cause = error.getCause() == null ? error : error.getCause();

        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }
}
