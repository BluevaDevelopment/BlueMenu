package net.blueva.menu.webeditor;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.blueva.menu.common.protocol.MessageType;
import net.blueva.menu.common.protocol.WebSocketMessage;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * WebSocket client for connecting to the web editor server
 */
public class WebEditorClient extends WebSocketClient {
    private static final Logger logger = Logger.getLogger(WebEditorClient.class.getName());
    private final Gson gson = new Gson();
    private CompletableFuture<String> sessionCreationFuture;

    public WebEditorClient(URI serverUri) {
        super(serverUri);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        logger.info("Connected to web editor server");
    }

    @Override
    public void onMessage(String message) {
        try {
            WebSocketMessage msg = gson.fromJson(message, WebSocketMessage.class);
            logger.info("Received message type: " + msg.getType());

            switch (msg.getType()) {
                case SESSION_VALID -> handleSessionValid(msg);
                case PONG -> logger.fine("Pong received");
                case ERROR -> handleError(msg);
                default -> logger.warning("Unhandled message type: " + msg.getType());
            }
        } catch (Exception e) {
            logger.severe("Error processing message: " + e.getMessage());
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.info("Disconnected from web editor server: " + reason + " (code: " + code + ")");
    }

    @Override
    public void onError(Exception ex) {
        logger.severe("WebSocket error: " + ex.getMessage());
    }

    /**
     * Request a new session from the server
     * @param sessionId Optional custom session ID, or null for auto-generated
     * @return CompletableFuture that completes with the session ID
     */
    public CompletableFuture<String> requestSession(String sessionId) {
        sessionCreationFuture = new CompletableFuture<>();

        WebSocketMessage msg = new WebSocketMessage(MessageType.SESSION_CREATE);
        JsonObject data = new JsonObject();
        if (sessionId != null && !sessionId.isEmpty()) {
            data.addProperty("sessionId", sessionId);
        }
        msg.setData(data);

        send(gson.toJson(msg));
        logger.info("Session creation requested");

        return sessionCreationFuture;
    }

    /**
     * Send a ping to the server
     */
    public void sendPing() {
        WebSocketMessage ping = new WebSocketMessage(MessageType.PING);
        send(gson.toJson(ping));
    }

    private void handleSessionValid(WebSocketMessage msg) {
        JsonObject data = msg.getData();
        String sessionId = data.get("sessionId").getAsString();

        logger.info("Session created: " + sessionId);

        if (sessionCreationFuture != null && !sessionCreationFuture.isDone()) {
            sessionCreationFuture.complete(sessionId);
        }
    }

    private void handleError(WebSocketMessage msg) {
        JsonObject data = msg.getData();
        String errorMessage = data.get("message").getAsString();
        logger.severe("Server error: " + errorMessage);

        if (sessionCreationFuture != null && !sessionCreationFuture.isDone()) {
            sessionCreationFuture.completeExceptionally(new RuntimeException(errorMessage));
        }
    }
}
