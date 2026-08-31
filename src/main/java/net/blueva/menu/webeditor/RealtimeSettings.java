package net.blueva.menu.webeditor;

import com.google.gson.JsonObject;

/**
 * Reverb connection details, handed to the plugin when it registers and
 * refreshed on every heartbeat.
 *
 * @param key          public application key of the Pusher protocol
 * @param host         host serving the WebSocket endpoint
 * @param port         port serving the WebSocket endpoint
 * @param useTls       whether the endpoint is served over TLS
 * @param authEndpoint URL that authorises subscriptions to private channels
 */
public record RealtimeSettings(String key, String host, int port, boolean useTls, String authEndpoint) {

    public static RealtimeSettings fromJson(JsonObject json) {
        if (json == null || !json.has("key") || json.get("key").isJsonNull()) {
            return null;
        }

        String scheme = json.has("scheme") && !json.get("scheme").isJsonNull()
            ? json.get("scheme").getAsString()
            : "https";

        return new RealtimeSettings(
            json.get("key").getAsString(),
            json.has("host") && !json.get("host").isJsonNull() ? json.get("host").getAsString() : null,
            json.has("port") && !json.get("port").isJsonNull() ? json.get("port").getAsInt() : 443,
            "https".equalsIgnoreCase(scheme),
            json.has("authEndpoint") && !json.get("authEndpoint").isJsonNull()
                ? json.get("authEndpoint").getAsString()
                : null
        );
    }

    public boolean isUsable() {
        return key != null && !key.isBlank() && host != null && !host.isBlank() && authEndpoint != null;
    }
}
