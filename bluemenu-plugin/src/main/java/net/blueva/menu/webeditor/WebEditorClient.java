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
                case PONG -> {} // Silent
                case ERROR -> handleError(msg);
                // Messages we send (ignore when broadcast back to us)
                case MENU_LIST, MENU_DATA, MENU_SAVED -> {} // Silent (we sent these)
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
        String sessionId = data.has("sessionId") ? data.get("sessionId").getAsString() : null;

        if (fileName == null || platform == null || content == null) {
            logger.warning("Missing fileName, platform or content in MENU_SAVE request");
            sendError("Missing required fields", sessionId);
            return;
        }

        // Save menu to disk
        boolean success = saveMenuToDisk(fileName, platform, content);

        if (success) {
            // Auto-reload if enabled (but not for config.yml)
            boolean autoReload = plugin.getConfig().getBoolean("webeditor.auto-reload", true);
            if (autoReload && !platform.equalsIgnoreCase("CONFIG")) {
                reloadMenus(platform, fileName);
            }

            // Send success confirmation
            sendMenuSaved(fileName, platform, sessionId);
            logger.info("Menu saved: " + fileName);
        } else {
            sendError("Failed to save menu to disk", sessionId);
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
     * Save menu content to disk
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
