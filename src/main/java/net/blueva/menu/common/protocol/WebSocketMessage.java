package net.blueva.menu.common.protocol;

import com.google.gson.JsonObject;

/**
 * Base WebSocket message structure
 */
public class WebSocketMessage {
    private MessageType type;
    private JsonObject data;
    private long timestamp;

    public WebSocketMessage() {
        this.timestamp = System.currentTimeMillis();
    }

    public WebSocketMessage(MessageType type) {
        this.type = type;
        this.timestamp = System.currentTimeMillis();
        this.data = new JsonObject();
    }

    public WebSocketMessage(MessageType type, JsonObject data) {
        this.type = type;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public JsonObject getData() {
        return data;
    }

    public void setData(JsonObject data) {
        this.data = data;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
