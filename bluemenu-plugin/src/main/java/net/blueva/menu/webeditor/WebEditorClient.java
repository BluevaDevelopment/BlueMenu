package net.blueva.menu.webeditor;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import net.blueva.menu.Main;
import net.blueva.menu.common.dto.MenuMetadataDTO;
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
    private static final Logger logger = Logger.getLogger(WebEditorClient.class.getName());
    private final Gson gson = new Gson();
    private final Main plugin;
    private CompletableFuture<String> sessionCreationFuture;

    public WebEditorClient(URI serverUri, Main plugin) {
        super(serverUri);
        this.plugin = plugin;
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

        // Get menu content and send it back
        String menuContent = getMenuContent(fileName, platform);
        if (menuContent != null) {
            sendMenuData(fileName, platform, menuContent, sessionId);
        } else {
            logger.warning("Menu not found: " + fileName);
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
        JsonObject structuredData = data.has("structuredData") ? data.getAsJsonObject("structuredData") : null;
        String sessionId = data.has("sessionId") ? data.get("sessionId").getAsString() : null;

        if (fileName == null || platform == null) {
            logger.warning("Missing fileName or platform in MENU_SAVE request");
            sendError("Missing required fields", sessionId);
            return;
        }

        if (content == null && structuredData == null) {
            logger.warning("Missing both content and structuredData in MENU_SAVE request");
            sendError("Missing content or structured data", sessionId);
            return;
        }

        boolean success;

        // If we have structured data (from visual editor), use it to preserve comments
        if (structuredData != null) {
            success = saveMenuFromStructuredData(fileName, platform, structuredData);
        } else {
            // Fall back to string content (from YAML editor)
            success = saveMenuToDisk(fileName, platform, content);
        }

        if (success) {
            // Special handling for config.yml - reload entire plugin
            if (platform.equalsIgnoreCase("CONFIG")) {
                reloadPlugin();
                logger.info("Config saved - plugin reloaded");
            } else {
                // Auto-reload menus if enabled
                boolean autoReload = plugin.getConfig().getBoolean("webeditor.auto-reload", true);
                if (autoReload) {
                    reloadMenus(platform, fileName);
                }
            }

            // Send success confirmation
            sendMenuSaved(fileName, platform, sessionId);
            logger.info("Menu saved: " + fileName);
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

        // Prevent deletion of config.yml
        if (platform.equalsIgnoreCase("CONFIG")) {
            logger.warning("Attempt to delete config.yml blocked");
            sendError("Cannot delete config.yml", sessionId);
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
        List<String> menuList = plugin.getConfig().getStringList(configKey);

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

            // Special handling for config.yml
            if (platform.equalsIgnoreCase("CONFIG")) {
                menuFile = new File(plugin.getDataFolder(), fileName);
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
     * Save menu content to disk using BootedYaml to preserve comments and spacing
     */
    private boolean saveMenuToDisk(String fileName, String platform, String content) {
        try {
            File menuFile;

            // Special handling for config.yml
            if (platform.equalsIgnoreCase("CONFIG")) {
                menuFile = new File(plugin.getDataFolder(), fileName);
            } else {
                String folderName = platform.equalsIgnoreCase("JAVA") ? "java" : "bedrock";
                menuFile = new File(plugin.getDataFolder() + "/menus/" + folderName, fileName);
            }

            // Create parent directories if they don't exist
            menuFile.getParentFile().mkdirs();

            // If file doesn't exist, just write directly (no comments to preserve)
            if (!menuFile.exists()) {
                try (FileWriter writer = new FileWriter(menuFile, StandardCharsets.UTF_8)) {
                    writer.write(content);
                }
                logger.fine("New menu file created: " + menuFile.getPath());
                return true;
            }

            // File exists - use BootedYaml to preserve comments and spacing
            try {
                // Load the new content as YAML to extract values
                YamlDocument newData = YamlDocument.create(new java.io.ByteArrayInputStream(
                    content.getBytes(StandardCharsets.UTF_8)
                ));

                // Load the existing file with BootedYaml (preserves comments)
                YamlDocument existingDoc = YamlDocument.create(menuFile);

                // Update all top-level keys from new data to existing doc
                for (Object keyObj : newData.getKeys()) {
                    String key = keyObj.toString();
                    Object value = newData.get(key);
                    existingDoc.set(key, value);
                }

                // Save the updated document (preserves comments and formatting)
                existingDoc.save(menuFile);

                logger.fine("Menu file updated preserving comments: " + menuFile.getPath());
                return true;
            } catch (Exception e) {
                // Fallback: if BootedYaml parsing fails, write directly
                logger.warning("BootedYaml update failed, falling back to direct write: " + e.getMessage());
                try (FileWriter writer = new FileWriter(menuFile, StandardCharsets.UTF_8)) {
                    writer.write(content);
                }
                return true;
            }
        } catch (Exception e) {
            logger.severe("Error writing menu file: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Save menu from structured data (JSON) using BootedYaml to preserve comments
     * This is used when saving from the visual editor
     */
    private boolean saveMenuFromStructuredData(String fileName, String platform, JsonObject structuredData) {
        try {
            File menuFile;

            // Special handling for config.yml
            if (platform.equalsIgnoreCase("CONFIG")) {
                menuFile = new File(plugin.getDataFolder(), fileName);
            } else {
                String folderName = platform.equalsIgnoreCase("JAVA") ? "java" : "bedrock";
                menuFile = new File(plugin.getDataFolder() + "/menus/" + folderName, fileName);
            }

            // Create parent directories if they don't exist
            menuFile.getParentFile().mkdirs();

            // Load existing file with BootedYaml (preserves comments)
            YamlDocument doc = YamlDocument.create(menuFile);

            // Update fields from structured data
            // Preserved fields (file_version, openCommand, etc.)
            if (structuredData.has("_preservedFields")) {
                JsonObject preserved = structuredData.getAsJsonObject("_preservedFields");
                if (preserved.has("file_version")) {
                    doc.set("file_version", preserved.get("file_version").getAsInt());
                }
                if (preserved.has("openCommand")) {
                    doc.set("openCommand", preserved.get("openCommand").getAsString());
                }
            }

            // Main menu properties
            if (structuredData.has("title")) {
                if (platform.equalsIgnoreCase("JAVA")) {
                    doc.set("menuName", structuredData.get("title").getAsString());
                } else {
                    doc.set("title", structuredData.get("title").getAsString());
                }
            }

            if (structuredData.has("size")) {
                doc.set("menuSize", structuredData.get("size").getAsInt());
            }

            if (structuredData.has("type")) {
                doc.set("type", structuredData.get("type").getAsString());
            }

            // Items section (Java menus)
            if (structuredData.has("items") && platform.equalsIgnoreCase("JAVA")) {
                JsonObject items = structuredData.getAsJsonObject("items");
                updateItemsSection(doc, items);
            }

            // Animations section (Java menus)
            if (structuredData.has("animations") && platform.equalsIgnoreCase("JAVA")) {
                JsonObject animations = structuredData.getAsJsonObject("animations");
                updateAnimationsSection(doc, animations);
            }

            // Bedrock-specific fields
            if (platform.equalsIgnoreCase("BEDROCK")) {
                if (structuredData.has("content")) {
                    doc.set("content", structuredData.get("content").getAsString());
                }
                if (structuredData.has("buttons")) {
                    // Convert buttons from JSON to map
                    doc.set("buttons", gson.fromJson(structuredData.get("buttons"), Object.class));
                }
                if (structuredData.has("components")) {
                    doc.set("components", gson.fromJson(structuredData.get("components"), Object.class));
                }
            }

            // Save the updated document (preserves comments and formatting)
            doc.save(menuFile);

            logger.fine("Menu file updated from structured data preserving comments: " + menuFile.getPath());
            return true;
        } catch (Exception e) {
            logger.severe("Error saving menu from structured data: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Update items section in YAML document from JSON data
     */
    private void updateItemsSection(YamlDocument doc, JsonObject itemsJson) {
        // Clear existing items section
        doc.remove("items");

        // Create items section and populate from JSON
        int itemCounter = 1;
        for (String slotKey : itemsJson.keySet()) {
            JsonObject itemData = itemsJson.getAsJsonObject(slotKey);
            if (itemData != null && !itemData.has("_isAnimation")) {
                String itemKey = "items.item" + itemCounter;

                // Set slot first
                if (itemData.has("slot")) {
                    doc.set(itemKey + ".slot", itemData.get("slot").getAsInt());
                } else {
                    doc.set(itemKey + ".slot", Integer.parseInt(slotKey));
                }

                // Set name
                if (itemData.has("name")) {
                    doc.set(itemKey + ".name", itemData.get("name").getAsString());
                }

                // Set itemStack
                if (itemData.has("material")) {
                    doc.set(itemKey + ".itemStack.material", itemData.get("material").getAsString());
                }
                if (itemData.has("amount")) {
                    doc.set(itemKey + ".itemStack.amount", itemData.get("amount").getAsInt());
                }
                if (itemData.has("value")) {
                    doc.set(itemKey + ".itemStack.value", itemData.get("value").getAsString());
                }

                // Set lore
                if (itemData.has("lore")) {
                    doc.set(itemKey + ".lore", gson.fromJson(itemData.get("lore"), java.util.List.class));
                }

                // Set actions
                if (itemData.has("actions")) {
                    doc.set(itemKey + ".actions", gson.fromJson(itemData.get("actions"), java.util.List.class));
                }

                // Set display_conditions
                if (itemData.has("display_conditions")) {
                    doc.set(itemKey + ".display_conditions", gson.fromJson(itemData.get("display_conditions"), Object.class));
                }

                itemCounter++;
            }
        }
    }

    /**
     * Update animations section in YAML document from JSON data
     */
    private void updateAnimationsSection(YamlDocument doc, JsonObject animationsJson) {
        // Clear existing animations section
        doc.remove("animations");

        // Create animations section from JSON
        for (String animKey : animationsJson.keySet()) {
            JsonObject animData = animationsJson.getAsJsonObject(animKey);
            if (animData != null) {
                String basePath = "animations." + animKey;

                // Set interval
                if (animData.has("interval")) {
                    doc.set(basePath + ".interval", animData.get("interval").getAsInt());
                }

                // Set frames
                if (animData.has("frames")) {
                    doc.set(basePath + ".frames", gson.fromJson(animData.get("frames"), Object.class));
                }
            }
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
                String menuNameWithoutExtension = fileName.replace(".yml", "");

                if (platform.equalsIgnoreCase("JAVA")) {
                    // Unregister from Java menu manager
                    plugin.javaMenuManager.menuNames.remove(menuNameWithoutExtension);
                    plugin.javaMenuManager.menuConfigs.remove(menuNameWithoutExtension);
                    logger.info("Unregistered Java menu: " + menuNameWithoutExtension);
                } else if (platform.equalsIgnoreCase("BEDROCK")) {
                    // Unregister from Bedrock menu manager
                    plugin.bedrockMenuManager.menuNames.remove(menuNameWithoutExtension);
                    plugin.bedrockMenuManager.menuConfigs.remove(menuNameWithoutExtension);
                    logger.info("Unregistered Bedrock menu: " + menuNameWithoutExtension);
                }
            });
        } catch (Exception e) {
            logger.warning("Error unregistering menu: " + e.getMessage());
        }
    }

    /**
     * Reload menus in memory after saving and refresh open menus
     */
    private void reloadMenus(String platform, String fileName) {
        try {
            // Run on main thread
            plugin.getServer().getScheduler().runTask(plugin, () -> {
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
            });
        } catch (Exception e) {
            logger.severe("Error reloading menus: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Reload entire plugin (used when config.yml is saved)
     */
    private void reloadPlugin() {
        try {
            // Run on main thread
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    // Reload config.yml
                    plugin.reloadConfig();

                    // Reload all menus
                    plugin.javaMenuManager.loadJavaMenus();
                    plugin.bedrockMenuManager.loadBedrockMenus();

                    logger.info("Plugin configuration and menus reloaded successfully");
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
        List<String> menuList = plugin.getConfig().getStringList(configKey);

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
