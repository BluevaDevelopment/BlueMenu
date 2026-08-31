package net.blueva.menu.webeditor;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import net.blueva.menu.Main;
import net.blueva.menu.common.dto.MenuMetadataDTO;
import net.blueva.menu.sync.MenuType;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Reads, writes and reloads the menus the editor operates on.
 *
 * This is the half of the old WebEditorClient that had nothing to do with the
 * transport, so it survived the move to Reverb unchanged.
 */
public class WebEditorMenus {
    private static final String SETTINGS_FILE_NAME = "settings.yml";

    private final Main plugin;
    private final Logger logger;

    public WebEditorMenus(Main plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * Outcome of a save, with the note the editor shows when the change did not
     * land where the user would assume.
     *
     * @param saved   whether the change was stored
     * @param warning explanation worth surfacing, or null
     */
    public record SaveOutcome(boolean saved, String warning) {
        static SaveOutcome ok() {
            return new SaveOutcome(true, null);
        }

        static SaveOutcome ok(String warning) {
            return new SaveOutcome(true, warning);
        }

        static SaveOutcome failed() {
            return new SaveOutcome(false, null);
        }
    }

    public List<MenuMetadataDTO> list() {
        return getMenuList();
    }

    public String read(String fileName, String platform) {
        return getMenuContent(resolveFileName(fileName, platform), platform);
    }

    /**
     * Writes a menu wherever it actually lives.
     *
     * A menu synced from MySQL is owned by the database, so writing it to disk
     * would leave a file the plugin never reads and an edit nobody sees.
     */
    public SaveOutcome save(String fileName, String platform, String content) {
        if (!platform.equalsIgnoreCase("CONFIG")) {
            String folderName = platform.equalsIgnoreCase("JAVA") ? "java" : "bedrock";
            MenuType type = platform.equalsIgnoreCase("JAVA") ? MenuType.JAVA : MenuType.BEDROCK;
            String menuKey = resolveMenuKey(fileName, folderName);

            if (menuKey != null && plugin.getMenuSyncService() != null
                    && plugin.getMenuSyncService().isReceiverMenu(type, menuKey)) {
                if (!plugin.getMenuSyncService().saveReceiverMenuYaml(type, menuKey, fileName, content)) {
                    return SaveOutcome.failed();
                }

                logger.info("Receiver menu saved to MySQL: " + fileName + " (" + menuKey + ")");

                return SaveOutcome.ok("This menu is synced from MySQL. The change was written to the database "
                    + "and will propagate to every server on the next sync poll.");
            }
        }

        String targetFileName = resolveFileName(fileName, platform);

        if (!saveMenuToDisk(targetFileName, platform, content)) {
            return SaveOutcome.failed();
        }

        if (platform.equalsIgnoreCase("CONFIG")) {
            plugin.getServer().getScheduler().runTask(plugin, plugin::reloadAll);
            logger.info("Settings saved, plugin reloaded");

            return SaveOutcome.ok("Settings reloaded. Note: changes to webeditor.* and metrics only take effect "
                + "after a full server restart.");
        }

        // Register and reload on the main thread so settings.yml is edited safely.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            // A new menu must land in java_menus/bedrock_menus or the plugin never loads it.
            registerMenuInSettings(platform, fileName);

            if (plugin.getConfigManager().getSettings().getBoolean("webeditor.auto-reload", true)) {
                reloadMenusInternal(platform, fileName);
            }
        });

        logger.info("Menu saved: " + targetFileName);

        return SaveOutcome.ok();
    }

    public boolean delete(String fileName, String platform) {
        if (platform.equalsIgnoreCase("CONFIG")) {
            logger.warning("Attempt to delete settings.yml blocked");

            return false;
        }

        if (!deleteMenuFromDisk(fileName, platform)) {
            return false;
        }

        unregisterMenu(platform, fileName);
        logger.info("Menu deleted: " + fileName);

        return true;
    }

    public String resolveFileName(String fileName, String platform) {
        return platform.equalsIgnoreCase("CONFIG") ? SETTINGS_FILE_NAME : fileName;
    }

    private List<MenuMetadataDTO> getMenuList() {
        List<MenuMetadataDTO> menus = new ArrayList<>();

        List<String> javaKeys = plugin.javaMenuManager != null
            ? plugin.javaMenuManager.menuNames : new ArrayList<String>();
        Map<String, YamlDocument> javaConfigs = plugin.javaMenuManager != null
            ? plugin.javaMenuManager.menuConfigs : new LinkedHashMap<String, YamlDocument>();
        collectMenusForPlatform(menus, "JAVA", "java", MenuType.JAVA, "CHEST", javaKeys, javaConfigs, "menus/java");

        List<String> bedrockKeys = plugin.bedrockMenuManager != null
            ? plugin.bedrockMenuManager.menuNames : new ArrayList<String>();
        Map<String, YamlDocument> bedrockConfigs = plugin.bedrockMenuManager != null
            ? plugin.bedrockMenuManager.menuConfigs : new LinkedHashMap<String, YamlDocument>();
        collectMenusForPlatform(menus, "BEDROCK", "bedrock", MenuType.BEDROCK, "FORM", bedrockKeys, bedrockConfigs,
            "menus/bedrock");

        logger.fine("Reporting " + menus.size() + " menus to the web editor");
        return menus;
    }

    private void collectMenusForPlatform(List<MenuMetadataDTO> out, String platformLabel, String platformKey,
                                         MenuType type, String defaultType, List<String> loadedKeys,
                                         Map<String, YamlDocument> loadedConfigs, String folder) {
        java.util.Set<String> seenFiles = new java.util.HashSet<>();

        // 1) Menus the plugin actually has loaded
        for (String menuKey : new ArrayList<>(loadedKeys)) {
            YamlDocument config = loadedConfigs.get(menuKey);
            if (config == null) {
                continue;
            }
            String fileName = getFileNameForMenu(menuKey, platformKey);
            seenFiles.add(fileName.toLowerCase());
            boolean receiver = plugin.getMenuSyncService() != null
                && plugin.getMenuSyncService().isReceiverMenu(type, menuKey);
            out.add(buildMenuDto(config, fileName, platformLabel, defaultType, true, receiver ? "mysql" : "disk"));
        }

        // 2) Orphan .yml files on disk that are not registered / not loaded
        File dir = new File(plugin.getDataFolder(), folder);
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                if (seenFiles.contains(file.getName().toLowerCase())) {
                    continue;
                }
                try {
                    YamlDocument config = YamlDocument.create(file);
                    out.add(buildMenuDto(config, file.getName(), platformLabel, defaultType, false, "disk"));
                } catch (Exception e) {
                    logger.warning("Error reading menu file: " + file.getName() + " - " + e.getMessage());
                }
            }
        }
    }

    private MenuMetadataDTO buildMenuDto(YamlDocument config, String fileName, String platformLabel,
                                         String defaultType, boolean registered, String source) {
        String displayName = config.getString("menuName", fileName.replace(".yml", ""));
        String menuType = config.getString("type", defaultType);
        String openCommand = config.getString("openCommand", "");

        int itemCount = 0;
        Section items = config.getSection("items");
        Section buttons = config.getSection("buttons");
        Section components = config.getSection("components");
        if (items != null) {
            itemCount = items.getKeys().size();
        } else if (buttons != null) {
            itemCount = buttons.getKeys().size();
        } else if (components != null) {
            itemCount = components.getKeys().size();
        }

        return new MenuMetadataDTO(fileName, displayName, platformLabel, menuType, openCommand, itemCount,
            registered, source);
    }

    private YamlDocument loadedConfigFor(MenuType type, String menuKey) {
        if (type == MenuType.JAVA) {
            return plugin.javaMenuManager != null ? plugin.javaMenuManager.menuConfigs.get(menuKey) : null;
        }
        return plugin.bedrockMenuManager != null ? plugin.bedrockMenuManager.menuConfigs.get(menuKey) : null;
    }

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

    private String getMenuContent(String fileName, String platform) {
        try {
            // Special handling for settings.yml
            if (platform.equalsIgnoreCase("CONFIG")) {
                File settings = new File(plugin.getDataFolder(), SETTINGS_FILE_NAME);
                if (!settings.exists()) {
                    logger.warning("Settings file not found: " + settings.getPath());
                    return null;
                }
                return Files.readString(settings.toPath());
            }

            String folderName = platform.equalsIgnoreCase("JAVA") ? "java" : "bedrock";
            MenuType type = platform.equalsIgnoreCase("JAVA") ? MenuType.JAVA : MenuType.BEDROCK;
            String menuKey = resolveMenuKey(fileName, folderName);

            // Receiver menus live in MySQL, not on disk - serve the database copy
            if (menuKey != null && plugin.getMenuSyncService() != null
                    && plugin.getMenuSyncService().isReceiverMenu(type, menuKey)) {
                Optional<String> dbYaml = plugin.getMenuSyncService().fetchMenuYaml(type, menuKey);
                if (dbYaml.isPresent()) {
                    return dbYaml.get();
                }
                YamlDocument loaded = loadedConfigFor(type, menuKey);
                if (loaded != null) {
                    return loaded.dump();
                }
            }

            File menuFile = new File(plugin.getDataFolder() + "/menus/" + folderName, fileName);
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

    private String resolveMenuKey(String fileName, String folderName) {
        String fromSettings = getMenuNameFromFileName(fileName, folderName);
        if (fromSettings != null) {
            return fromSettings;
        }

        Map<String, YamlDocument> configs = folderName.equals("java")
            ? (plugin.javaMenuManager != null ? plugin.javaMenuManager.menuConfigs : null)
            : (plugin.bedrockMenuManager != null ? plugin.bedrockMenuManager.menuConfigs : null);
        if (configs == null) {
            return null;
        }

        String base = fileName.toLowerCase().endsWith(".yml")
            ? fileName.substring(0, fileName.length() - 4) : fileName;
        for (String key : configs.keySet()) {
            if (key.equalsIgnoreCase(base) || getFileNameForMenu(key, folderName).equalsIgnoreCase(fileName)) {
                return key;
            }
        }
        return null;
    }

    private void refreshOpenMenus(String menuName) {
        // Only players who actually have THIS menu open - not everyone with any menu open.
        List<org.bukkit.entity.Player> playersToRefresh = new ArrayList<>();

        for (var entry : plugin.javaMenuManager.activeMenus.entrySet()) {
            org.bukkit.entity.Player player = entry.getKey();
            if (entry.getValue() == null) {
                continue;
            }
            if (menuName.equals(net.blueva.menu.managers.java.PlayerManager.getMenuName(player))) {
                playersToRefresh.add(player);
            }
        }

        if (playersToRefresh.isEmpty()) {
            return;
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (org.bukkit.entity.Player player : playersToRefresh) {
                if (!player.isOnline()
                    || !menuName.equals(net.blueva.menu.managers.java.PlayerManager.getMenuName(player))) {
                    continue;
                }
                player.closeInventory();
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        plugin.javaMenuManager.openMenu(player, menuName);
                        logger.fine("Refreshed menu for player: " + player.getName());
                    }
                }, 2L);
            }
        }, 5L);
    }
}
