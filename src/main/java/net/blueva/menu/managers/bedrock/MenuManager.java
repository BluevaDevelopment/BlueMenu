package net.blueva.menu.managers.bedrock;

import dev.dejvokep.boostedyaml.YamlDocument;
import net.blueva.menu.Main;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MenuManager {
    public final Map<String, YamlDocument> menuConfigs = new HashMap<>();
    public final List<String> menuNames = new ArrayList<>();
    private final Map<UUID, String> lastOpenedMenus = new HashMap<>();

    private final Main main;

    public MenuManager(Main main) {
        this.main = main;
    }
    public void loadBedrockMenus() {
        List<String> menuList = main.getConfigManager().getSettings().getStringList("bedrock_menus");
        main.getMenuSyncService().loadMenus(
            net.blueva.menu.sync.MenuType.BEDROCK,
            menuList,
            new File(main.getDataFolder(), "menus/bedrock"),
            menuConfigs,
            menuNames
        );
    }

    public void updateMenuConfig(String menuName, YamlDocument menuConfig) {
        menuConfigs.put(menuName, menuConfig);
        if (!menuNames.contains(menuName)) {
            menuNames.add(menuName);
        }
    }

    public void openMenu(Player player, String menuName) {
        YamlDocument menuConfig = menuConfigs.get(menuName);
        if(menuConfig != null) {
            lastOpenedMenus.put(player.getUniqueId(), menuName);
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

    public String getLastOpenedMenu(Player player) {
        return lastOpenedMenus.get(player.getUniqueId());
    }
}
