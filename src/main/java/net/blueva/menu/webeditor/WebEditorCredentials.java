package net.blueva.menu.webeditor;

import dev.dejvokep.boostedyaml.YamlDocument;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * The uuid and token this server received the first time it registered.
 *
 * Kept out of settings.yml so an admin sharing that file does not leak the
 * token, and so a config reload never drops the credentials.
 */
public class WebEditorCredentials {
    private static final String FILE_NAME = "webeditor-credentials.yml";

    private final File file;
    private final Logger logger;
    private String uuid;
    private String token;

    public WebEditorCredentials(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, FILE_NAME);
        this.logger = logger;
        load();
    }

    public boolean exist() {
        return uuid != null && !uuid.isBlank() && token != null && !token.isBlank();
    }

    public String uuid() {
        return uuid;
    }

    public String token() {
        return token;
    }

    public void store(String uuid, String token) {
        this.uuid = uuid;
        this.token = token;

        try {
            YamlDocument document = YamlDocument.create(file);
            document.set("uuid", uuid);
            document.set("token", token);
            document.save();
            logger.info("Stored web editor credentials for this server");
        } catch (IOException e) {
            logger.severe("Could not store web editor credentials: " + e.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) {
            return;
        }

        try {
            YamlDocument document = YamlDocument.create(file);
            uuid = document.getString("uuid");
            token = document.getString("token");
        } catch (IOException e) {
            logger.warning("Could not read " + FILE_NAME + ": " + e.getMessage());
        }
    }
}
