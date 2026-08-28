package net.blueva.menu.webeditor;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import net.blueva.menu.Main;
import net.blueva.menu.common.dto.MenuMetadataDTO;
import org.bukkit.Bukkit;
import net.blueva.menu.common.protocol.MessageType;
import net.blueva.menu.common.protocol.WebSocketMessage;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.io.File;
import java.io.FileWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * WebSocket client for connecting to the web editor server
 */
public class WebEditorClient extends WebSocketClient {
    private static final String SETTINGS_FILE_NAME = "settings.yml";
    private final Gson gson = new Gson();
    private final Main plugin;
    private final Logger logger;
    private final boolean requireSessionConfirmation;
    private CompletableFuture<String> sessionCreationFuture;
    private CompletableFuture<Boolean> sessionConfirmationFuture;

    public WebEditorClient(URI serverUri, Main plugin, boolean requireSessionConfirmation) {
        super(serverUri);
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.requireSessionConfirmation = requireSessionConfirmation;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        logger.info("Connected to web editor server");
    }

    @Override
    public void onMessage(String message) {
        try {
            WebSocketMessage msg = gson.fromJson(message, WebSocketMessage.class);
            // Only log important message types
            if (msg.getType() != MessageType.PONG) {
                logger.fine("Received message type: " + msg.getType());
            }

            switch (msg.getType()) {
                case SESSION_VALID -> handleSessionValid(msg);
                case SESSION_CONFIRMED -> handleSessionConfirmed(msg);
                case MENU_LIST_REQUEST -> handleMenuListRequest(msg);
                case MENU_GET -> handleMenuGet(msg);
                case MENU_SAVE -> handleMenuSave(msg);
                case MENU_DELETE -> handleMenuDelete(msg);
                case PONG -> {} // Silent
                case ERROR -> handleError(msg);
                // Messages we send (ignore when broadcast back to us)
                case MENU_LIST, MENU_DATA, MENU_SAVED, MENU_DELETED -> {} // Silent (we sent these)
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
        data.addProperty("requireConfirmation", requireSessionConfirmation);
        data.addProperty("serverVersion", Bukkit.getBukkitVersion().split("-")[0]);
        msg.setData(data);

        send(gson.toJson(msg));
        logger.info("Session creation requested");

        return sessionCreationFuture;
    }

    /**
     * Confirm a session for a specific player
     */
    public CompletableFuture<Boolean> confirmSession(String verificationId, java.util.UUID confirmedBy) {
        sessionConfirmationFuture = new CompletableFuture<>();

        WebSocketMessage msg = new WebSocketMessage(MessageType.SESSION_CONFIRM);
        JsonObject data = new JsonObject();
        data.addProperty("verificationId", verificationId);
        data.addProperty("confirmedBy", confirmedBy.toString());
        msg.setData(data);

        send(gson.toJson(msg));
        logger.info("Session confirmation requested with verification: " + verificationId);

        return sessionConfirmationFuture;
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

    private void handleSessionConfirmed(WebSocketMessage msg) {
        JsonObject data = msg.getData();
        String sessionId = data.has("sessionId") ? data.get("sessionId").getAsString() : "";
        String verificationId = data.has("verificationId") ? data.get("verificationId").getAsString() : "";
        boolean confirmed = data.has("confirmed") && data.get("confirmed").getAsBoolean();
        String message = data.has("message") ? data.get("message").getAsString() : "";

        if (confirmed) {
            logger.info("Session confirmed: " + sessionId + " with verification " + verificationId);
            if (sessionConfirmationFuture != null && !sessionConfirmationFuture.isDone()) {
                sessionConfirmationFuture.complete(true);
            }
            return;
        }

        if (sessionConfirmationFuture != null && !sessionConfirmationFuture.isDone()) {
            sessionConfirmationFuture.completeExceptionally(new RuntimeException(message.isEmpty() ? "Session confirmation failed" : message));
        }
        logger.warning("Session confirmation failed for " + sessionId + (message.isEmpty() ? "" : ": " + message));
    }

    private void handleError(WebSocketMessage msg) {
        JsonObject data = msg.getData();
        String errorMessage = data.get("message").getAsString();
        logger.severe("Server error: " + errorMessage);

        if (sessionCreationFuture != null && !sessionCreationFuture.isDone()) {
            sessionCreationFuture.completeExceptionally(new RuntimeException(errorMessage));
        }

        if (sessionConfirmationFuture != null && !sessionConfirmationFuture.isDone()) {
            sessionConfirmationFuture.completeExceptionally(new RuntimeException(errorMessage));
        }
    }

    /**
     * Handle menu list request from the server
     */
    private void handleMenuListRequest(WebSocketMessage msg) {
        JsonObject data = msg.getData();
        String sessionId = data.has("sessionId") ? data.get("sessionId").getAsString() : null;

        logger.fine("Menu list requested for session: " + sessionId);

        // Get menu list and send it back
        List<MenuMetadataDTO> menus = getMenuList();
        sendMenuList(menus, sessionId);
    }

    /**
     * Handle menu get request (request for full menu content)
     */
    private void handleMenuGet(WebSocketMessage msg) {
        JsonObject data = msg.getData();
        String fileName = data.has("fileName") ? data.get("fileName").getAsString() : null;
        String platform = data.has("platform") ? data.get("platform").getAsString() : null;
        String sessionId = data.has("sessionId") ? data.get("sessionId").getAsString() : null;

        logger.fine("Menu content requested: " + fileName + " (platform: " + platform + ")");

        if (fileName == null || platform == null) {
            logger.warning("Missing fileName or platform in MENU_GET request");
            return;
        }

        String resolvedFileName = platform.equalsIgnoreCase("CONFIG") ? SETTINGS_FILE_NAME : fileName;

        // Get menu content and send it back
        String menuContent = getMenuContent(resolvedFileName, platform);
        if (menuContent != null) {
            sendMenuData(resolvedFileName, platform, menuContent, sessionId);
        } else {
            logger.warning("Menu not found: " + resolvedFileName);
        }
    }

    /**
     * Handle menu save request (save modified menu content to disk)
     */
    private void handleMenuSave(WebSocketMessage msg) {
        JsonObject data = msg.getData();
        String fileName = data.has("fileName") ? data.get("fileName").getAsString() : null;
        String platform = data.has("platform") ? data.get("platform").getAsString() : null;
        String content = data.has("content") ? data.get("content").getAsString() : null;
        String sessionId = data.has("sessionId") ? data.get("sessionId").getAsString() : null;

        if (fileName == null || platform == null || content == null) {
            logger.warning("Missing fileName, platform or content in MENU_SAVE request");
            sendError("Missing required fields", sessionId);
            return;
        }

        // Save menu to disk
        String targetFileName = platform.equalsIgnoreCase("CONFIG") ? SETTINGS_FILE_NAME : fileName;

        boolean success = saveMenuToDisk(targetFileName, platform, content);

        if (success) {
            // Special handling for settings.yml - reload entire plugin
            if (platform.equalsIgnoreCase("CONFIG")) {
                reloadPlugin();
                logger.info("Settings saved - plugin reloaded");
            } else {
                final String menuPlatform = platform;
                final String menuFileName = fileName;
                // Register + reload on the main thread so settings.yml is edited safely
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    // Make sure new menus end up in java_menus/bedrock_menus, otherwise
                    // the plugin never loads them (not even with /bm reload).
                    registerMenuInSettings(menuPlatform, menuFileName);

                    boolean autoReload = plugin.getConfigManager().getSettings()
                        .getBoolean("webeditor.auto-reload", true);
                    if (autoReload) {
                        reloadMenusInternal(menuPlatform, menuFileName);
                    }
                });
            }

            // Send success confirmation
            sendMenuSaved(targetFileName, platform, sessionId);
            logger.info("Menu saved: " + targetFileName);
        } else {
            sendError("Failed to save menu to disk", sessionId);
        }
    }

    /**
     * Handle menu delete request (delete menu file from disk)
     */
    private void handleMenuDelete(WebSocketMessage msg) {
        JsonObject data = msg.getData();
        String fileName = data.has("fileName") ? data.get("fileName").getAsString() : null;
        String platform = data.has("platform") ? data.get("platform").getAsString() : null;
        String sessionId = data.has("sessionId") ? data.get("sessionId").getAsString() : null;

        if (fileName == null || platform == null) {
            logger.warning("Missing fileName or platform in MENU_DELETE request");
            sendError("Missing required fields", sessionId);
            return;
        }

        // Prevent deletion of settings.yml
        if (platform.equalsIgnoreCase("CONFIG")) {
            logger.warning("Attempt to delete settings.yml blocked");
            sendError("Cannot delete settings.yml", sessionId);
            return;
        }

        // Delete menu from disk
        boolean success = deleteMenuFromDisk(fileName, platform);

        if (success) {
            // Unregister menu if loaded
            unregisterMenu(platform, fileName);

            // Send success confirmation
            sendMenuDeleted(fileName, platform, sessionId);
            logger.info("Menu deleted: " + fileName);
        } else {
            sendError("Failed to delete menu from disk", sessionId);
        }
    }

    /**
     * Get list of all menus (Java + Bedrock) by scanning filesystem
     */
    private List<MenuMetadataDTO> getMenuList() {
        List<MenuMetadataDTO> menus = new ArrayList<>();

        // Scan Java menus from filesystem
        File javaMenusDir = new File(plugin.getDataFolder(), "menus/java");
        if (javaMenusDir.exists() && javaMenusDir.isDirectory()) {
            File[] javaFiles = javaMenusDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
            if (javaFiles != null) {
                for (File file : javaFiles) {
                    try {
                        YamlDocument config = YamlDocument.create(file);
                        String fileName = file.getName();
                        String displayName = config.getString("menuName", fileName.replace(".yml", ""));
                        String type = config.getString("type", "CHEST");
                        String openCommand = config.getString("openCommand", "");

                        // Count items
                        int itemCount = 0;
                        Section itemsSection = config.getSection("items");
                        if (itemsSection != null) {
                            itemCount = itemsSection.getKeys().size();
                        }

                        menus.add(new MenuMetadataDTO(fileName, displayName, "JAVA", type, openCommand, itemCount));
                    } catch (Exception e) {
                        logger.warning("Error reading Java menu file: " + file.getName() + " - " + e.getMessage());
                    }
                }
            }
        }

        // Scan Bedrock menus from filesystem
        File bedrockMenusDir = new File(plugin.getDataFolder(), "menus/bedrock");
        if (bedrockMenusDir.exists() && bedrockMenusDir.isDirectory()) {
            File[] bedrockFiles = bedrockMenusDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
            if (bedrockFiles != null) {
                for (File file : bedrockFiles) {
                    try {
                        YamlDocument config = YamlDocument.create(file);
                        String fileName = file.getName();
                        String displayName = config.getString("menuName", fileName.replace(".yml", ""));
                        String type = config.getString("type", "FORM");
                        String openCommand = config.getString("openCommand", "");

                        // Count buttons/components
                        int itemCount = 0;
                        Section buttonsSection = config.getSection("buttons");
                        Section componentsSection = config.getSection("components");
                        if (buttonsSection != null) {
                            itemCount = buttonsSection.getKeys().size();
                        } else if (componentsSection != null) {
                            itemCount = componentsSection.getKeys().size();
                        }

                        menus.add(new MenuMetadataDTO(fileName, displayName, "BEDROCK", type, openCommand, itemCount));
                    } catch (Exception e) {
                        logger.warning("Error reading Bedrock menu file: " + file.getName() + " - " + e.getMessage());
                    }
                }
            }
        }

        logger.fine("Found " + menus.size() + " menus from filesystem");
        return menus;
    }

    /**
     * Get filename for a menu from the config
     */
    private String getFileNameForMenu(String menuName, String platform) {
        String configKey = platform.equals("java") ? "java_menus" : "bedrock_menus";
        List<String> menuList = plugin.getConfigManager().getSettings().getStringList(configKey);

        for (String entry : menuList) {
            String[] parts = entry.split(";");
            if (parts.length == 2 && parts[0].trim().equals(menuName)) {
                return parts[1].trim();
            }
        }

        return menuName + ".yml"; // Fallback
    }

    /**
     * Send menu list to the server
     */
    public void sendMenuList(List<MenuMetadataDTO> menus, String sessionId) {
        WebSocketMessage msg = new WebSocketMessage(MessageType.MENU_LIST);
        JsonObject data = new JsonObject();

        if (sessionId != null) {
            data.addProperty("sessionId", sessionId);
        }
        data.add("menus", gson.toJsonTree(menus));

        msg.setData(data);
        send(gson.toJson(msg));

        logger.fine("Sent menu list: " + menus.size() + " menus");
    }

    /**
     * Get full menu content as YAML string
     */
    private String getMenuContent(String fileName, String platform) {
        try {
            File menuFile;

            // Special handling for settings.yml
            if (platform.equalsIgnoreCase("CONFIG")) {
                menuFile = new File(plugin.getDataFolder(), SETTINGS_FILE_NAME);
            } else {
                String folderName = platform.equalsIgnoreCase("JAVA") ? "java" : "bedrock";
                menuFile = new File(plugin.getDataFolder() + "/menus/" + folderName, fileName);
            }

            if (!menuFile.exists()) {
                logger.warning("Menu file not found: " + menuFile.getPath());
                return null;
            }

            // Read file content as string
            return Files.readString(menuFile.toPath());
        } catch (Exception e) {
            logger.severe("Error reading menu file: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Send menu data (full content) to the server
     */
    private void sendMenuData(String fileName, String platform, String content, String sessionId) {
        WebSocketMessage msg = new WebSocketMessage(MessageType.MENU_DATA);
        JsonObject data = new JsonObject();

        data.addProperty("fileName", fileName);
        data.addProperty("platform", platform);
        data.addProperty("content", content);
        if (sessionId != null) {
            data.addProperty("sessionId", sessionId);
        }

        msg.setData(data);
        send(gson.toJson(msg));

        logger.fine("Sent menu data: " + fileName + " (" + content.length() + " bytes)");
    }

    /**
     * Save menu content to disk
     */
    private boolean saveMenuToDisk(String fileName, String platform, String content) {
        try {
            File menuFile;

            // Special handling for settings.yml
            if (platform.equalsIgnoreCase("CONFIG")) {
                menuFile = new File(plugin.getDataFolder(), SETTINGS_FILE_NAME);
            } else {
                String folderName = platform.equalsIgnoreCase("JAVA") ? "java" : "bedrock";
                menuFile = new File(plugin.getDataFolder() + "/menus/" + folderName, fileName);
            }

            // Create parent directories if they don't exist
            menuFile.getParentFile().mkdirs();

            // Write content to file
            try (FileWriter writer = new FileWriter(menuFile, StandardCharsets.UTF_8)) {
                writer.write(content);
            }

            logger.fine("Menu file written to disk: " + menuFile.getPath());
            return true;
        } catch (Exception e) {
            logger.severe("Error writing menu file: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Delete menu file from disk
     */
    private boolean deleteMenuFromDisk(String fileName, String platform) {
        try {
            String folderName = platform.equalsIgnoreCase("JAVA") ? "java" : "bedrock";
            File menuFile = new File(plugin.getDataFolder() + "/menus/" + folderName, fileName);

            if (!menuFile.exists()) {
                logger.warning("Menu file not found for deletion: " + menuFile.getPath());
                return false;
            }

            // Delete the file
            boolean deleted = menuFile.delete();

            if (deleted) {
                logger.fine("Menu file deleted from disk: " + menuFile.getPath());
            } else {
                logger.warning("Failed to delete menu file: " + menuFile.getPath());
            }

            return deleted;
        } catch (Exception e) {
            logger.severe("Error deleting menu file: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Unregister menu from memory
     */
    private void unregisterMenu(String platform, String fileName) {
        try {
            // Run on main thread
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                // Drop the entry from settings.yml, then rebuild the in-memory registry
                // from the updated list so the menu is fully gone (key may differ from file name).
                unregisterMenuFromSettings(platform, fileName);
                reloadMenusInternal(platform, fileName);
                logger.info("Unregistered menu: " + fileName);
            });
        } catch (Exception e) {
            logger.warning("Error unregistering menu: " + e.getMessage());
        }
    }

    /**
     * Reload menus in memory after saving and refresh open menus.
     * MUST be called from the main server thread.
     */
    private void reloadMenusInternal(String platform, String fileName) {
        try {
            if (platform.equalsIgnoreCase("JAVA")) {
                // Get menu name from fileName
                String menuName = getMenuNameFromFileName(fileName, "java");

                // Close and reopen menus for players who have it open
                if (menuName != null) {
                    refreshOpenMenus(menuName);
                }

                // Reload menus in memory
                plugin.javaMenuManager.loadJavaMenus();
                logger.info("Java menu reloaded and refreshed: " + fileName);
            } else {
                // Bedrock menus don't need refresh (can't force close)
                plugin.bedrockMenuManager.loadBedrockMenus();
                logger.info("Bedrock menus reloaded: " + fileName);
            }
        } catch (Exception e) {
            logger.severe("Error reloading menus: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Ensure a menu file is registered in java_menus / bedrock_menus inside settings.yml.
     * Without an entry there the plugin never loads the menu (createNewMenu only writes the file).
     * MUST be called from the main server thread.
     */
    private void registerMenuInSettings(String platform, String fileName) {
        try {
            String configKey = platform.equalsIgnoreCase("JAVA") ? "java_menus" : "bedrock_menus";
            YamlDocument settings = plugin.getConfigManager().getSettings();
            List<String> entries = new ArrayList<>(settings.getStringList(configKey));

            String baseKey = fileName.toLowerCase().endsWith(".yml")
                ? fileName.substring(0, fileName.length() - 4)
                : fileName;

            java.util.Set<String> usedKeys = new java.util.HashSet<>();
            for (String entry : entries) {
                String[] parts = entry.split(";");
                if (parts.length == 2) {
                    if (parts[1].trim().equalsIgnoreCase(fileName)) {
                        return; // already registered, nothing to do
                    }
                    usedKeys.add(parts[0].trim().toLowerCase());
                }
            }

            String menuKey = baseKey;
            int suffix = 2;
            while (usedKeys.contains(menuKey.toLowerCase())) {
                menuKey = baseKey + "_" + suffix++;
            }

            entries.add(menuKey + ";" + fileName);
            settings.set(configKey, entries);
            plugin.getConfigManager().saveSettings();
            logger.info("Registered new menu in settings.yml: " + menuKey + ";" + fileName);
        } catch (Exception e) {
            logger.warning("Failed to register menu in settings.yml: " + e.getMessage());
        }
    }

    /**
     * Remove a menu entry from java_menus / bedrock_menus inside settings.yml.
     * MUST be called from the main server thread.
     */
    private void unregisterMenuFromSettings(String platform, String fileName) {
        try {
            String configKey = platform.equalsIgnoreCase("JAVA") ? "java_menus" : "bedrock_menus";
            YamlDocument settings = plugin.getConfigManager().getSettings();
            List<String> entries = new ArrayList<>(settings.getStringList(configKey));
            boolean removed = entries.removeIf(entry -> {
                String[] parts = entry.split(";");
                return parts.length == 2 && parts[1].trim().equalsIgnoreCase(fileName);
            });
            if (removed) {
                settings.set(configKey, entries);
                plugin.getConfigManager().saveSettings();
                logger.info("Removed menu from settings.yml: " + fileName);
            }
        } catch (Exception e) {
            logger.warning("Failed to remove menu from settings.yml: " + e.getMessage());
        }
    }

    /**
     * Reload entire plugin (used when settings.yml is saved)
     */
    private void reloadPlugin() {
        try {
            // Run on main thread
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    // Mirror what /bm reload does so settings.yml changes actually take effect
                    plugin.getConfigManager().reloadSettings();
                    plugin.getConfigManager().reloadLang();
                    plugin.getMenuSyncService().reload();

                    // Reload all menus
                    plugin.javaMenuManager.loadJavaMenus();
                    plugin.bedrockMenuManager.loadBedrockMenus();

                    logger.info("Plugin configuration and menus reloaded successfully");
                    logger.info("Note: webeditor.* and metrics changes still require a full server restart");
                } catch (Exception e) {
                    logger.severe("Error reloading plugin: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            logger.severe("Error scheduling plugin reload: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get menu name from file name
     */
    private String getMenuNameFromFileName(String fileName, String platform) {
        String configKey = platform.equals("java") ? "java_menus" : "bedrock_menus";
        List<String> menuList = plugin.getConfigManager().getSettings().getStringList(configKey);

        for (String entry : menuList) {
            String[] parts = entry.split(";");
            if (parts.length == 2 && parts[1].trim().equals(fileName)) {
                return parts[0].trim();
            }
        }

        return null;
    }

    /**
     * Refresh (close and reopen) menus for players who have them open
     * Only works for Java menus
     */
    private void refreshOpenMenus(String menuName) {
        // Get all players who have this menu open
        List<org.bukkit.entity.Player> playersToRefresh = new ArrayList<>();

        for (var entry : plugin.javaMenuManager.activeMenus.entrySet()) {
            org.bukkit.entity.Player player = entry.getKey();
            fr.mrmicky.fastinv.FastInv menu = entry.getValue();

            // Check if this player has the specific menu open
            if (menu != null) {
                playersToRefresh.add(player);
            }
        }

        // Close and reopen menus after a short delay
        if (!playersToRefresh.isEmpty()) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                for (org.bukkit.entity.Player player : playersToRefresh) {
                    if (player.isOnline() && plugin.javaMenuManager.activeMenus.containsKey(player)) {
                        // Close current menu
                        player.closeInventory();

                        // Reopen menu after a tick
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            if (player.isOnline()) {
                                plugin.javaMenuManager.openMenu(player, menuName);
                                logger.fine("Refreshed menu for player: " + player.getName());
                            }
                        }, 2L);
                    }
                }
            }, 5L);
        }
    }

    /**
     * Send menu saved confirmation to the server
     */
    private void sendMenuSaved(String fileName, String platform, String sessionId) {
        WebSocketMessage msg = new WebSocketMessage(MessageType.MENU_SAVED);
        JsonObject data = new JsonObject();

        data.addProperty("fileName", fileName);
        data.addProperty("platform", platform);
        data.addProperty("success", true);
        data.addProperty("message", "Menu saved successfully");
        if (sessionId != null) {
            data.addProperty("sessionId", sessionId);
        }

        msg.setData(data);
        send(gson.toJson(msg));

        logger.fine("Sent menu saved confirmation: " + fileName);
    }

    /**
     * Send menu deleted confirmation to the server
     */
    private void sendMenuDeleted(String fileName, String platform, String sessionId) {
        WebSocketMessage msg = new WebSocketMessage(MessageType.MENU_DELETED);
        JsonObject data = new JsonObject();

        data.addProperty("fileName", fileName);
        data.addProperty("platform", platform);
        data.addProperty("success", true);
        data.addProperty("message", "Menu deleted successfully");
        if (sessionId != null) {
            data.addProperty("sessionId", sessionId);
        }

        msg.setData(data);
        send(gson.toJson(msg));

        logger.fine("Sent menu deleted confirmation: " + fileName);
    }

    /**
     * Send error message to the server
     */
    private void sendError(String errorMessage, String sessionId) {
        WebSocketMessage msg = new WebSocketMessage(MessageType.ERROR);
        JsonObject data = new JsonObject();

        data.addProperty("message", errorMessage);
        if (sessionId != null) {
            data.addProperty("sessionId", sessionId);
        }

        msg.setData(data);
        send(gson.toJson(msg));

        logger.warning("Sent error: " + errorMessage);
    }
}
