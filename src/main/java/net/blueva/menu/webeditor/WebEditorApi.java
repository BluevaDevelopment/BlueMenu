package net.blueva.menu.webeditor;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * HTTP side of the web editor protocol.
 *
 * Everything the plugin initiates travels over plain requests; only the
 * operations the browser starts arrive the other way, over Reverb.
 */
public class WebEditorApi {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final Gson gson = new Gson();
    private final String baseUrl;
    private final WebEditorCredentials credentials;

    public WebEditorApi(String baseUrl, WebEditorCredentials credentials) {
        this.baseUrl = baseUrl;
        this.credentials = credentials;
    }

    /**
     * Claims an identity for this server. The response is the only time the
     * token is ever sent back, so the caller must persist it.
     */
    public CompletableFuture<JsonObject> register(String name, String pluginVersion, String serverVersion) {
        JsonObject body = new JsonObject();
        body.addProperty("name", name);
        body.addProperty("pluginVersion", pluginVersion);
        body.addProperty("serverVersion", serverVersion);

        return send(request("/api/plugin/register", body, false));
    }

    public CompletableFuture<JsonObject> heartbeat(boolean polling) {
        JsonObject body = new JsonObject();
        body.addProperty("polling", polling);

        return send(request("/api/plugin/heartbeat", body, true));
    }

    /**
     * Collects the requests waiting for this server.
     *
     * Used when the realtime channel is unreachable, so the editor still works
     * on a deployment without it.
     */
    public CompletableFuture<JsonObject> pollRequests() {
        return send(request("/api/plugin/rpc-poll", new JsonObject(), true));
    }

    public CompletableFuture<JsonObject> createSession(boolean requireConfirmation) {
        JsonObject body = new JsonObject();
        body.addProperty("requireConfirmation", requireConfirmation);

        return send(request("/api/plugin/sessions", body, true));
    }

    public CompletableFuture<Boolean> confirmSession(String verificationId, UUID confirmedBy) {
        JsonObject body = new JsonObject();
        body.addProperty("verificationId", verificationId);
        body.addProperty("confirmedBy", confirmedBy.toString());

        return send(request("/api/plugin/sessions/confirm", body, true))
            .thenApply(json -> json.has("confirmed") && json.get("confirmed").getAsBoolean());
    }

    /**
     * Answers one RPC request, releasing the browser request waiting on it.
     */
    public CompletableFuture<Void> respond(String requestId, boolean ok, JsonObject payload, String error) {
        JsonObject body = new JsonObject();
        body.addProperty("id", requestId);
        body.addProperty("ok", ok);
        body.add("payload", payload == null ? new JsonObject() : payload);
        if (error != null) {
            body.addProperty("error", error);
        }

        return send(request("/api/plugin/rpc-response", body, true)).thenAccept(ignored -> {});
    }

    private HttpRequest request(String path, JsonObject body, boolean authenticated) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .timeout(TIMEOUT)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)));

        if (authenticated) {
            builder.header("X-Server-Uuid", credentials.uuid())
                .header("X-Server-Token", credentials.token());
        }

        return builder.build();
    }

    private CompletableFuture<JsonObject> send(HttpRequest request) {
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                if (response.statusCode() >= 400) {
                    throw new CompletionException(
                        new WebEditorException(describe(response)));
                }

                if (response.body() == null || response.body().isBlank()) {
                    return new JsonObject();
                }

                return JsonParser.parseString(response.body()).getAsJsonObject();
            });
    }

    private String describe(HttpResponse<String> response) {
        String body = response.body();

        if (body != null && !body.isBlank()) {
            try {
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                if (json.has("message")) {
                    return json.get("message").getAsString();
                }
            } catch (Exception ignored) {
                // Fall through to the generic description below.
            }
        }

        return "The web editor answered with status " + response.statusCode();
    }

    /**
     * Failure reported by the web editor, carried to the player who ran the command.
     */
    public static class WebEditorException extends RuntimeException {
        public WebEditorException(String message) {
            super(message);
        }
    }
}
