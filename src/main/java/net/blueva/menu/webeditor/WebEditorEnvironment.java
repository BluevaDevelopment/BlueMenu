package net.blueva.menu.webeditor;

/**
 * Where the plugin talks to the web editor.
 */
public enum WebEditorEnvironment {
    PRODUCTION("production", "Official", "https://menu.blueva.net"),
    DEVELOPMENT("development", "Development", "https://menu.blueva.net/dev");

    private final String configKey;
    private final String displayName;
    private final String baseUrl;

    WebEditorEnvironment(String configKey, String displayName, String baseUrl) {
        this.configKey = configKey;
        this.displayName = displayName;
        this.baseUrl = baseUrl;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String displayName() {
        return displayName;
    }

    public String editorUrl(String sessionId) {
        return baseUrl + "/editor/" + sessionId;
    }

    public static WebEditorEnvironment fromConfig(String value) {
        if (value == null) {
            return PRODUCTION;
        }

        String normalized = value.trim();
        for (WebEditorEnvironment environment : values()) {
            if (environment.configKey.equalsIgnoreCase(normalized)) {
                return environment;
            }
        }

        return PRODUCTION;
    }
}
