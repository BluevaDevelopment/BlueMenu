package net.blueva.menu.sync;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import net.blueva.menu.Main;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MenuSyncService {
    private final Main main;
    private MenuRepository repository;
    private BukkitTask pollTask;
    private SyncConfig syncConfig;
    private final Map<MenuType, Map<String, ReceiverState>> receiverStates = new EnumMap<>(MenuType.class);
    private final Map<MenuType, Map<String, Long>> lastPublishedVersions = new EnumMap<>(MenuType.class);

    public MenuSyncService(Main main) {
        this.main = main;
        for (MenuType type : MenuType.values()) {
            receiverStates.put(type, new ConcurrentHashMap<>());
            lastPublishedVersions.put(type, new ConcurrentHashMap<>());
        }
    }

    public void reload() {
        stopPolling();
        closeRepository();
        syncConfig = SyncConfig.fromSettings(main);
        if (!syncConfig.enabled()) {
            return;
        }
        try {
            repository = new MySqlMenuRepository(syncConfig.mysqlConfig());
            repository.ensureSchema();
            startPolling();
        } catch (SQLException e) {
            main.getLogger().warning("Failed to initialize MySQL menu sync: " + e.getMessage());
            closeRepository();
        }
    }

    public boolean isEnabled() {
        return syncConfig != null && syncConfig.enabled();
    }

    private boolean isRepositoryAvailable() {
        return syncConfig != null && syncConfig.enabled() && repository != null;
    }

    public void shutdown() {
        stopPolling();
        closeRepository();
    }

    public void loadMenus(MenuType type, List<String> registryEntries, File baseFolder,
                          Map<String, YamlDocument> menuConfigs, List<String> menuNames) {
        Map<String, YamlDocument> previousConfigs = new HashMap<>(menuConfigs);
        Map<String, ReceiverState> previousReceivers = new HashMap<>(receiverStates.get(type));
        menuConfigs.clear();
        menuNames.clear();
        receiverStates.get(type).clear();
        Map<String, MenuEntry> registry = parseRegistry(type, registryEntries);
        SyncTargets targets = resolveSyncTargets(type, registry);

        for (MenuEntry entry : registry.values()) {
            if (targets.receive().contains(entry.normalizedEntry())) {
                boolean loaded = loadMenuFromDatabase(entry, menuConfigs, menuNames);
                if (!loaded) {
                    YamlDocument cachedConfig = previousConfigs.get(entry.menuKey());
                    if (cachedConfig != null) {
                        menuConfigs.put(entry.menuKey(), cachedConfig);
                        menuNames.add(entry.menuKey());
                        ReceiverState cachedState = previousReceivers.get(entry.menuKey());
                        if (cachedState != null) {
                            receiverStates.get(type).put(entry.menuKey(), cachedState);
                        }
                    }
                }
            } else {
                loadMenuFromDisk(entry, baseFolder, menuConfigs, menuNames);
                if (targets.send().contains(entry.normalizedEntry())) {
                    publishMenu(entry, menuConfigs.get(entry.menuKey()));
                }
            }
        }
    }

    private void loadMenuFromDisk(MenuEntry entry, File baseFolder,
                                  Map<String, YamlDocument> menuConfigs, List<String> menuNames) {
        File menuConfigFile = new File(baseFolder, entry.fileName());
        if (!menuConfigFile.exists()) {
            main.getLogger().warning(invalidMenuFileMessage(entry.fileName()));
            return;
        }
        try {
            YamlDocument menuConfig = YamlDocument.create(
                menuConfigFile,
                GeneralSettings.DEFAULT,
                LoaderSettings.DEFAULT,
                DumperSettings.DEFAULT,
                UpdaterSettings.DEFAULT
            );
            menuConfigs.put(entry.menuKey(), menuConfig);
            menuNames.add(entry.menuKey());
        } catch (IOException e) {
            main.getLogger().warning("Failed to load menu file: " + entry.fileName());
            e.printStackTrace();
        }
    }

    private boolean loadMenuFromDatabase(MenuEntry entry,
                                      Map<String, YamlDocument> menuConfigs, List<String> menuNames) {
        if (!isRepositoryAvailable()) {
            main.getLogger().warning("Menu sync is enabled but MySQL repository is unavailable.");
            return false;
        }
        try {
            Optional<MenuRecord> record = repository.fetchMenu(entry.type(), entry.menuKey());
            if (record.isEmpty()) {
                main.getLogger().warning("Receiver menu " + entry.normalizedEntry()
                    + " was not found in MySQL. This server will keep the last cached copy.");
                receiverStates.get(entry.type()).put(entry.menuKey(), new ReceiverState(entry, 0L));
                return false;
            }
            MenuRecord data = record.get();
            YamlDocument menuConfig = parseYaml(data.yaml());
            menuConfigs.put(entry.menuKey(), menuConfig);
            menuNames.add(entry.menuKey());
            receiverStates.get(entry.type()).put(entry.menuKey(), new ReceiverState(entry, data.version()));
            return true;
        } catch (SQLException | IOException e) {
            main.getLogger().warning("Failed to load receiver menu from MySQL: " + entry.normalizedEntry()
                + " (" + e.getMessage() + ")");
            return false;
        }
    }

    private void publishMenu(MenuEntry entry, YamlDocument menuConfig) {
        if (!isRepositoryAvailable()) {
            return;
        }
        if (menuConfig == null) {
            main.getLogger().warning("Send menu " + entry.normalizedEntry() + " was not loaded from disk.");
            return;
        }
        try {
            Optional<MenuMetadata> metadata = repository.fetchMetadata(entry.type(), entry.menuKey());
            SendConflictPolicy policy = syncConfig.sendConflictPolicy();
            Map<String, Long> publishedVersions = lastPublishedVersions.get(entry.type());
            Long lastPublished = publishedVersions.get(entry.menuKey());
            if (metadata.isPresent()) {
                long dbVersion = metadata.get().version();
                if (policy == SendConflictPolicy.SKIP && (lastPublished == null || dbVersion > lastPublished)) {
                    main.getLogger().warning("Skipping publish for menu " + entry.normalizedEntry()
                        + " because the database has a newer version (DB=" + dbVersion + ").");
                    return;
                }
                long newVersion = repository.updateMenu(entry.type(), entry.menuKey(), entry.fileName(), menuConfig.dump());
                publishedVersions.put(entry.menuKey(), newVersion);
            } else {
                long newVersion = repository.insertMenu(entry.type(), entry.menuKey(), entry.fileName(), menuConfig.dump());
                publishedVersions.put(entry.menuKey(), newVersion);
            }
        } catch (SQLException e) {
            main.getLogger().warning("Failed to publish menu to MySQL: " + entry.normalizedEntry()
                + " (" + e.getMessage() + ")");
        }
    }

    private void startPolling() {
        int intervalSeconds = syncConfig.pollIntervalSeconds();
        if (intervalSeconds <= 0) {
            return;
        }
        pollTask = Bukkit.getScheduler().runTaskTimerAsynchronously(main, this::pollReceiverMenus,
            intervalSeconds * 20L, intervalSeconds * 20L);
    }

    private void pollReceiverMenus() {
        if (!isRepositoryAvailable()) {
            return;
        }
        for (Map<String, ReceiverState> receiverMap : receiverStates.values()) {
            for (ReceiverState state : receiverMap.values()) {
                pollReceiverMenu(state);
            }
        }
    }

    private void pollReceiverMenu(ReceiverState state) {
        try {
            Optional<MenuMetadata> metadata = repository.fetchMetadata(state.entry().type(), state.entry().menuKey());
            if (metadata.isEmpty()) {
                return;
            }
            long dbVersion = metadata.get().version();
            if (dbVersion <= state.version()) {
                return;
            }
            Optional<MenuRecord> record = repository.fetchMenu(state.entry().type(), state.entry().menuKey());
            if (record.isEmpty()) {
                return;
            }
            MenuRecord data = record.get();
            Bukkit.getScheduler().runTask(main, () -> applyReceiverUpdate(state, data));
        } catch (SQLException e) {
            main.getLogger().warning("Failed to poll receiver menu " + state.entry().normalizedEntry()
                + " (" + e.getMessage() + ")");
        }
    }

    private void applyReceiverUpdate(ReceiverState state, MenuRecord record) {
        try {
            YamlDocument menuConfig = parseYaml(record.yaml());
            if (record.type() == MenuType.JAVA) {
                main.javaMenuManager.updateMenuConfig(record.menuKey(), menuConfig);
            } else if (Main.isUsingFloodgate) {
                main.bedrockMenuManager.updateMenuConfig(record.menuKey(), menuConfig);
            }
            receiverStates.get(record.type()).put(record.menuKey(), new ReceiverState(state.entry(), record.version()));
        } catch (IOException e) {
            main.getLogger().warning("Failed to reload receiver menu " + record.menuKey()
                + " from MySQL: " + e.getMessage());
        }
    }

    private YamlDocument parseYaml(String yaml) throws IOException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
        return YamlDocument.create(
            inputStream,
            GeneralSettings.DEFAULT,
            LoaderSettings.DEFAULT,
            DumperSettings.DEFAULT,
            UpdaterSettings.DEFAULT
        );
    }

    private Map<String, MenuEntry> parseRegistry(MenuType type, List<String> registryEntries) {
        Map<String, MenuEntry> registry = new LinkedHashMap<>();
        for (String menuEntry : registryEntries) {
            MenuEntry entry = parseMenuEntry(type, menuEntry);
            if (entry == null) {
                main.getLogger().warning(invalidMenuEntryMessage(menuEntry));
                continue;
            }
            registry.put(entry.normalizedEntry(), entry);
        }
        return registry;
    }

    private SyncTargets resolveSyncTargets(MenuType type, Map<String, MenuEntry> registry) {
        if (!isEnabled()) {
            return new SyncTargets(Set.of(), Set.of());
        }
        Set<String> sendList = normalizeSyncList(type, syncConfig.getSendList(type), registry, "send");
        Set<String> receiveList = normalizeSyncList(type, syncConfig.getReceiveList(type), registry, "receive");

        validateSyncEntries(type, registry, sendList, "send");
        validateSyncEntries(type, registry, receiveList, "receive");

        Set<String> conflicts = new HashSet<>(sendList);
        conflicts.retainAll(receiveList);
        for (String conflict : conflicts) {
            main.getLogger().severe("Menu " + conflict + " is configured in both send and receive lists. "
                + "It will be treated as receive.");
            sendList.remove(conflict);
        }

        return new SyncTargets(sendList, receiveList);
    }

    private Set<String> normalizeSyncList(MenuType type, List<String> entries, Map<String, MenuEntry> registry, String listName) {
        Set<String> normalized = new HashSet<>();
        
        // Check if the list contains a wildcard "*" entry
        boolean hasWildcard = entries.stream().anyMatch(entry -> "*".equals(entry.trim()));
        
        if (hasWildcard) {
            // If wildcard is present, include all menus from registry
            // Note: Any other entries in the list alongside the wildcard are ignored
            if (entries.size() > 1) {
                main.getLogger().warning("Wildcard '*' detected in " + listName + " list for " + type.getConfigKey() 
                    + " menus. All other entries in the list will be ignored.");
            }
            normalized.addAll(registry.keySet());
            main.getLogger().info("Wildcard '*' detected in " + listName + " list for " + type.getConfigKey() 
                + " menus. Including all " + registry.size() + " registered menu(s).");
            return normalized;
        }
        
        // Normal processing for specific entries
        for (String entry : entries) {
            MenuEntry menuEntry = parseMenuEntry(type, entry);
            if (menuEntry == null) {
                main.getLogger().warning("Ignoring invalid " + listName + " entry: " + entry);
                continue;
            }
            normalized.add(menuEntry.normalizedEntry());
        }
        return normalized;
    }

    private void validateSyncEntries(MenuType type, Map<String, MenuEntry> registry, Set<String> entries, String listName) {
        Iterator<String> iterator = entries.iterator();
        while (iterator.hasNext()) {
            String entry = iterator.next();
            if (!registry.containsKey(entry)) {
                iterator.remove();
                main.getLogger().warning("Ignoring " + listName + " entry " + entry
                    + " because it is not registered in " + type.getConfigKey() + "_menus.");
            }
        }
    }

    private MenuEntry parseMenuEntry(MenuType type, String menuEntry) {
        if (menuEntry == null) {
            return null;
        }
        String[] menuData = menuEntry.split(";");
        if (menuData.length != 2) {
            return null;
        }
        String menuKey = menuData[0].trim();
        String menuFileName = menuData[1].trim();
        if (menuKey.isEmpty() || menuFileName.isEmpty()) {
            return null;
        }
        return new MenuEntry(type, menuKey, menuFileName, menuEntry);
    }

    private void closeRepository() {
        if (repository != null) {
            repository.close();
            repository = null;
        }
    }

    private void stopPolling() {
        if (pollTask != null) {
            pollTask.cancel();
            pollTask = null;
        }
    }

    private String invalidMenuFileMessage(String fileName) {
        return Objects.requireNonNull(main.configManager.getLang().getString("commands.bluemenu.manager.invalid_menu_file"))
            .replace("{name}", fileName);
    }

    private String invalidMenuEntryMessage(String menuEntry) {
        return Objects.requireNonNull(main.configManager.getLang().getString("commands.bluemenu.manager.invalid_menu_entry"))
            .replace("{entry}", menuEntry);
    }

    private record ReceiverState(MenuEntry entry, long version) {
    }

    private record SyncTargets(Set<String> send, Set<String> receive) {
    }

    private enum SendConflictPolicy {
        SKIP,
        OVERWRITE
    }

    private record SyncConfig(boolean enabled, int pollIntervalSeconds, SendConflictPolicy sendConflictPolicy,
                              List<String> sendJava, List<String> sendBedrock,
                              List<String> receiveJava, List<String> receiveBedrock,
                              MySqlConfig mysqlConfig) {

        static SyncConfig fromSettings(Main main) {
            boolean enabled = main.getConfigManager().getSettings().getBoolean("sync-menus.enabled", false);
            int pollInterval = main.getConfigManager().getSettings().getInt("sync-menus.poll-interval-seconds", 3);
            String policyRaw = main.getConfigManager().getSettings().getString("sync-menus.send-conflict-policy", "SKIP");
            SendConflictPolicy policy;
            try {
                policy = SendConflictPolicy.valueOf(policyRaw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                policy = SendConflictPolicy.SKIP;
            }

            List<String> sendJava = main.getConfigManager().getSettings().getStringList("sync-menus.send.java");
            List<String> sendBedrock = main.getConfigManager().getSettings().getStringList("sync-menus.send.bedrock");
            List<String> receiveJava = main.getConfigManager().getSettings().getStringList("sync-menus.receive.java");
            List<String> receiveBedrock = main.getConfigManager().getSettings().getStringList("sync-menus.receive.bedrock");

            MySqlConfig mysqlConfig = new MySqlConfig(
                main.getConfigManager().getSettings().getString("sync-menus.mysql.host", "localhost"),
                main.getConfigManager().getSettings().getInt("sync-menus.mysql.port", 3306),
                main.getConfigManager().getSettings().getString("sync-menus.mysql.database", "bluemenu"),
                main.getConfigManager().getSettings().getString("sync-menus.mysql.username", "root"),
                main.getConfigManager().getSettings().getString("sync-menus.mysql.password", "password"),
                main.getConfigManager().getSettings().getBoolean("sync-menus.mysql.use-ssl", false),
                main.getConfigManager().getSettings().getInt("sync-menus.mysql.pool-size", 10),
                main.getConfigManager().getSettings().getLong("sync-menus.mysql.connection-timeout-ms", 10000L),
                main.getConfigManager().getSettings().getLong("sync-menus.mysql.max-lifetime-ms", 1800000L)
            );
            return new SyncConfig(enabled, pollInterval, policy, sendJava, sendBedrock, receiveJava, receiveBedrock, mysqlConfig);
        }

        List<String> getSendList(MenuType type) {
            return type == MenuType.JAVA ? sendJava : sendBedrock;
        }

        List<String> getReceiveList(MenuType type) {
            return type == MenuType.JAVA ? receiveJava : receiveBedrock;
        }
    }
}
