package net.blueva.menu.configuration;

import net.blueva.menu.Main;
import net.blueva.menu.configuration.updater.ConfigUpdater;
import net.blueva.menu.utils.MessagesUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
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
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(cfgFile);
        Reader defConfigStream = new InputStreamReader(Objects.requireNonNull(main.getResource("net/blueva/menu/configuration/files"+folder+"/" + file + ".yml")), StandardCharsets.UTF_8);
        YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(defConfigStream);
        cfg.setDefaults(defConfig);
        cfg.options().copyDefaults(true);
        cfg.save(cfgFile);
    }

    //Config Files
    // Language Manager
    public FileConfiguration getLang() {
        if(main.language == null) {
            reloadLang();
        }
        return main.language;
    }

    public void reloadLang(){
        if(main.language == null){
            main.languageFile = new File(main.getDataFolder()+"/language/",main.actualLang+".yml");
        }
        main.language = YamlConfiguration.loadConfiguration(main.languageFile);
        Reader defConfigStream;
        defConfigStream = new InputStreamReader(Objects.requireNonNull(main.getResource("net/blueva/menu/configuration/files/language/" + main.actualLang + ".yml")), StandardCharsets.UTF_8);
        YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(defConfigStream);
        main.language.setDefaults(defConfig);

        // Clear the prefix cache when language is reloaded
        MessagesUtil.clearPrefixCache();
    }

    public void saveLang(){
        try{
            main.language.save(main.languageFile);
            ConfigUpdater.update(main, "net/blueva/menu/configuration/files/language/"+main.actualLang+".yml", new File(main.getDataFolder()+"/language/"+main.actualLang+".yml"));
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    public void registerLang(){
        main.languageFile = new File(main.getDataFolder()+"/language/",main.actualLang+".yml");
        if(!main.languageFile.exists()){
            this.getLang().options().copyDefaults(true);
            main.langPath = getLang().getCurrentPath();
            saveLang();
        }
    }
}
