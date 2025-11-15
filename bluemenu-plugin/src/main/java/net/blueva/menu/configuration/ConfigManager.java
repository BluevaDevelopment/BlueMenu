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
import java.util.Objects;

public class ConfigManager {
    private final Main main;

    public ConfigManager(Main main) {
        this.main = main;
    }

    public void generateFolders() {
        if(!main.getDataFolder().exists()) {
            main.getDataFolder().mkdirs();
        }

        // Language folder
        File languagef = new File(main.getDataFolder()+ "/language");
        if(!languagef.exists()) {
            languagef.mkdirs();

            try {
                generateFile("en_UK", "/language");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
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
        YamlDocument.create(
            cfgFile,
            Objects.requireNonNull(main.getResource("net/blueva/menu/configuration/files"+folder+"/" + file + ".yml")),
            GeneralSettings.DEFAULT,
            LoaderSettings.builder().setAutoUpdate(true).build(),
            DumperSettings.DEFAULT,
            UpdaterSettings.builder().setVersioning(new BasicVersioning("file_version")).build()
        );
    }

    //Config Files
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
                main.languageFile = new File(main.getDataFolder()+"/language/",main.actualLang+".yml");
            }
            main.language = YamlDocument.create(
                main.languageFile,
                Objects.requireNonNull(main.getResource("net/blueva/menu/configuration/files/language/" + main.actualLang + ".yml")),
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
        main.languageFile = new File(main.getDataFolder()+"/language/",main.actualLang+".yml");
        if(!main.languageFile.exists()){
            reloadLang();
            saveLang();
        }
    }
}
