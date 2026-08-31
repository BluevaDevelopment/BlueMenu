package net.blueva.menu.webeditor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pusher.client.Pusher;
import com.pusher.client.PusherOptions;
import com.pusher.client.channel.PrivateChannelEventListener;
import com.pusher.client.channel.PusherEvent;
import com.pusher.client.connection.ConnectionEventListener;
import com.pusher.client.connection.ConnectionState;
import com.pusher.client.connection.ConnectionStateChange;
import com.pusher.client.util.HttpChannelAuthorizer;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Listens on this server's private channel for the operations the browser starts.
 *
 * Replaces the raw WebSocket client: the plugin is now a Reverb subscriber and
 * answers over HTTP, so there is no bespoke protocol left to keep in sync.
 */
public class WebEditorRealtime {
    private static final String RPC_EVENT = "rpc.request";

    private final RealtimeSettings settings;
    private final WebEditorCredentials credentials;
    private final Logger logger;
    private final RpcHandler handler;
    private Pusher pusher;

    public WebEditorRealtime(RealtimeSettings settings, WebEditorCredentials credentials, Logger logger,
                             RpcHandler handler) {
        this.settings = settings;
        this.credentials = credentials;
        this.logger = logger;
        this.handler = handler;
    }

    public void connect() {
        if (pusher != null) {
            return;
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("X-Server-Uuid", credentials.uuid());
        headers.put("X-Server-Token", credentials.token());
        headers.put("Accept", "application/json");

        HttpChannelAuthorizer authorizer = new HttpChannelAuthorizer(settings.authEndpoint());
        authorizer.setHeaders(headers);

        PusherOptions options = new PusherOptions()
            .setChannelAuthorizer(authorizer)
            .setHost(settings.host())
            .setWsPort(settings.port())
            .setWssPort(settings.port())
            .setUseTLS(settings.useTls());

        pusher = new Pusher(settings.key(), options);
        pusher.connect(new ConnectionEventListener() {
            @Override
            public void onConnectionStateChange(ConnectionStateChange change) {
                logger.info("Web editor realtime connection is " + change.getCurrentState());
            }

            @Override
            public void onError(String message, String code, Exception e) {
                logger.warning("Web editor realtime error: " + message);
            }
        }, ConnectionState.ALL);

        pusher.subscribePrivate("private-server." + credentials.uuid(), new PrivateChannelEventListener() {
            @Override
            public void onEvent(PusherEvent event) {
                dispatch(event);
            }

            @Override
            public void onSubscriptionSucceeded(String channelName) {
                logger.info("Listening for web editor requests on " + channelName);
            }

            @Override
            public void onAuthenticationFailure(String message, Exception e) {
                logger.severe("Web editor rejected this server's credentials: " + message);
            }
        }, RPC_EVENT);
    }

    public void disconnect() {
        if (pusher == null) {
            return;
        }

        pusher.disconnect();
        pusher = null;
    }

    public boolean isConnected() {
        return pusher != null && pusher.getConnection().getState() == ConnectionState.CONNECTED;
    }

    private void dispatch(PusherEvent event) {
        try {
            JsonObject data = JsonParser.parseString(event.getData()).getAsJsonObject();
            String requestId = data.get("id").getAsString();
            String action = data.get("action").getAsString();
            JsonObject payload = data.has("payload") && data.get("payload").isJsonObject()
                ? data.getAsJsonObject("payload")
                : new JsonObject();

            handler.handle(requestId, action, payload);
        } catch (Exception e) {
            logger.severe("Could not read a web editor request: " + e.getMessage());
        }
    }

    /**
     * Runs one operation and answers the browser waiting for it.
     */
    @FunctionalInterface
    public interface RpcHandler {
        void handle(String requestId, String action, JsonObject payload);
    }
}
