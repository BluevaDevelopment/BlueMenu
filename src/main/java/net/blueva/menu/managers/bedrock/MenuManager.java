package net.blueva.menu.managers.bedrock;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import net.blueva.menu.Main;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.bukkit.Bukkit.getLogger;

public class MenuManager {
    public final Map<String, YamlDocument> menuConfigs = new HashMap<>();
    public final List<String> menuNames = new ArrayList<>();

    private final Main main;

    public MenuManager(Main main) {
        this.main = main;
    }
    public void loadBedrockMenus() {
        menuConfigs.clear();
        menuNames.clear();
        List<String> menuList = main.getConfig().getStringList("bedrock_menus");
        for (String menuEntry : menuList) {
            String[] menuData = menuEntry.split(";");
            if (menuData.length == 2) {
                String menuName = menuData[0].trim();
                String menuFileName = menuData[1].trim();
                File menuConfigFile = new File(main.getDataFolder()+"/menus/bedrock", menuFileName);
                if (menuConfigFile.exists()) {
                    try {
                        YamlDocument menuConfig = YamlDocument.create(
                            menuConfigFile,
                            GeneralSettings.DEFAULT,
                            LoaderSettings.builder().setAutoUpdate(true).build(),
                            DumperSettings.DEFAULT,
                            UpdaterSettings.builder().setVersioning(new BasicVersioning("file_version")).build()
                        );
                        menuConfigs.put(menuName, menuConfig);
                        menuNames.add(menuName);
                    } catch (IOException e) {
                        getLogger().warning("Failed to load menu file: " + menuFileName);
                        e.printStackTrace();
                    }
                } else {
                    getLogger().warning(Objects.requireNonNull(Main.getPlugin().configManager.getLang().getString("global.error.invalid_menu_file")).replace("{name}", menuFileName));
                }
            } else {
                getLogger().warning(Objects.requireNonNull(Main.getPlugin().configManager.getLang().getString("global.error.invalid_menu_entry")).replace("{entry}", menuEntry));
            }
        }
    }

    public void openMenu(Player player, String menuName) {
        YamlDocument menuConfig = menuConfigs.get(menuName);
        if(menuConfig != null) {
            String menuType = menuConfig.getString("type");
            if(menuType != null) {
                if(menuType.equalsIgnoreCase("SIMPLE")) {
                    SimpleManager.openMenu(player, menuConfig);
                }
                if(menuType.equalsIgnoreCase("MODAL")) {
                    ModalManager.openMenu(player, menuConfig);
                }
                if(menuType.equalsIgnoreCase("CUSTOM")) {
                    CustomManager.openMenu(player, menuConfig);
                }
            }
        }
    }
}
