package net.blueva.menu.managers.bedrock;

import net.blueva.menu.Main;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;

import static org.bukkit.Bukkit.getLogger;

public class MenuManager {
    public final Map<String, FileConfiguration> menuConfigs = new HashMap<>();
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
                    FileConfiguration menuConfig = YamlConfiguration.loadConfiguration(menuConfigFile);
                    menuConfigs.put(menuName, menuConfig);
                    menuNames.add(menuName);
                } else {
                    getLogger().warning(Objects.requireNonNull(Main.getPlugin().configManager.getLang().getString("global.error.invalid_menu_file")).replace("{name}", menuFileName));
                }
            } else {
                getLogger().warning(Objects.requireNonNull(Main.getPlugin().configManager.getLang().getString("global.error.invalid_menu_entry")).replace("{entry}", menuEntry));
            }
        }
    }

    public void openMenu(Player player, String menuName) {
        FileConfiguration menuConfig = menuConfigs.get(menuName);
        if(menuConfig != null) {
            String menuType = menuConfig.getString("type");
            if(menuType != null) {
                if(menuType.equalsIgnoreCase("SIMPLE")) {
                    //SimpleManager.openMenu(Player player, FileConfiguration menuConfig);
                }
                if(menuType.equalsIgnoreCase("MODAL")) {
                    ModalManager.openMenu(player, menuConfig);
                }
            }
        }
    }
}
