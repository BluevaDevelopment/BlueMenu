package net.blueva.menu.configuration;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import net.blueva.menu.Main;
import net.blueva.menu.utils.MessagesUtil;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

public class ConfigManager {
    private final Main main;
    private YamlDocument settings;
    private File settingsFile;

    public ConfigManager(Main main) {
        this.main = main;
    }

    public void generateFolders() {
        if(!main.getDataFolder().exists()) {
            main.getDataFolder().mkdirs();
        }

        // menusf folder
        File menusf = new File(main.getDataFolder()+ "/menus");
        if(!menusf.exists()) {
            menusf.mkdirs();
        }

        // Java folder
        File javaf = new File(main.getDataFolder()+ "/menus/java");
        if(!javaf.exists()) {
            javaf.mkdirs();

            try {
                generateFile("chest_example", "/menus/java");
                generateFile("slots_helper", "/menus/java");
                generateFile("conditions_example", "/menus/java");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        // Bedrock folder
        File bedrockf = new File(main.getDataFolder()+ "/menus/bedrock");
        if(!bedrockf.exists()) {
            bedrockf.mkdirs();

            try {
                generateFile("modal_example", "/menus/bedrock");
                generateFile("simple_example", "/menus/bedrock");
                generateFile("custom_example", "/menus/bedrock");
                generateFile("conditions_custom_example", "/menus/bedrock");
                generateFile("conditions_simple_example", "/menus/bedrock");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

    }

    public void generateFile(String file, String folder) throws IOException {
        File cfgFile = new File(main.getDataFolder() + folder + "/", file + ".yml");
        // Example menus are meant to be copied and edited freely - never auto-update them
        // against the bundled resource, or a version bump would reorder/strip the user's keys.
        if (cfgFile.exists()) {
            return;
        }
        YamlDocument.create(
            cfgFile,
            Objects.requireNonNull(main.getResource("net/blueva/menu/configuration/files"+folder+"/" + file + ".yml")),
            GeneralSettings.DEFAULT,
            LoaderSettings.DEFAULT,
            DumperSettings.DEFAULT,
            UpdaterSettings.DEFAULT
        );
    }

    //Config Files
    // Settings Manager
    public YamlDocument getSettings() {
        if (settings == null) {
            reloadSettings();
        }
        return settings;
    }

    public void reloadSettings() {
        try {
            if (settingsFile == null) {
                settingsFile = new File(main.getDataFolder(), "settings.yml");
            }

            settings = YamlDocument.create(
                settingsFile,
                Objects.requireNonNull(main.getResource("settings.yml")),
                GeneralSettings.DEFAULT,
                LoaderSettings.builder().setAutoUpdate(true).build(),
                DumperSettings.DEFAULT,
                UpdaterSettings.builder().setVersioning(new BasicVersioning("file_version")).build()
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to load settings.yml", e);
        }
    }

    public void saveSettings() {
        try {
            if (settings != null) {
                settings.save();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to save settings.yml", e);
        }
    }

    public void registerSettings() {
        settingsFile = new File(main.getDataFolder(), "settings.yml");
        reloadSettings();

        boolean migrated = migrateLegacyConfig();
        if (!settingsFile.exists() || migrated) {
            saveSettings();
        }
    }

    private boolean migrateLegacyConfig() {
        File legacyConfig = new File(main.getDataFolder(), "config.yml");
        if (!legacyConfig.exists()) {
            return false;
        }

        try {
            YamlDocument legacy = YamlDocument.create(
                legacyConfig,
                GeneralSettings.DEFAULT,
                LoaderSettings.DEFAULT,
                DumperSettings.DEFAULT,
                UpdaterSettings.builder().setVersioning(new BasicVersioning("file_version")).build()
            );

            boolean migrated = false;
            for (String key : Arrays.asList(
                "metrics",
                "java_menus",
                "bedrock_menus",
                "webeditor.enabled",
                "webeditor.require-confirmation",
                "webeditor.auto-save",
                "webeditor.auto-reload",
                "webeditor.environment"
            )) {
                if (legacy.contains(key)) {
                    settings.set(key, legacy.get(key));
                    migrated = true;
                }
            }

            if (migrated) {
                main.getLogger().info("Detected legacy config.yml, migrated values to settings.yml.");
            }

            return migrated;
        } catch (IOException e) {
            main.getLogger().warning("Failed to migrate legacy config.yml: " + e.getMessage());
            return false;
        }
    }

    // Language Manager
    public YamlDocument getLang() {
        if(main.language == null) {
            reloadLang();
        }
        return main.language;
    }

    public void reloadLang(){
        try {
            if(main.language == null){
                main.languageFile = new File(main.getDataFolder(), "language.yml");
            }
            main.language = YamlDocument.create(
                main.languageFile,
                Objects.requireNonNull(main.getResource("net/blueva/menu/configuration/files/language.yml")),
                GeneralSettings.DEFAULT,
                LoaderSettings.builder().setAutoUpdate(true).build(),
                DumperSettings.DEFAULT,
                UpdaterSettings.builder().setVersioning(new BasicVersioning("file_version")).build()
            );

            // Clear the prefix cache when language is reloaded
            MessagesUtil.clearPrefixCache();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveLang(){
        try{
            main.language.save();
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    public void registerLang(){
        main.languageFile = new File(main.getDataFolder(),"language.yml");
        if(!main.languageFile.exists()){
            reloadLang();
            saveLang();
        }
    }
}
