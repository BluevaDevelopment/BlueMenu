package net.blueva.menu.managers.java;

import net.blueva.menu.Main;
import net.blueva.menu.utils.MessagesUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.bukkit.Bukkit.getLogger;

public class MenuManager {
    public final Map<String, FileConfiguration> menuConfigs = new HashMap<>();
    public final List<String> menuNames = new ArrayList<>();

    private final Main main;

    public MenuManager(Main main) {
        this.main = main;
    }

    public void loadJavaMenus() {
        menuConfigs.clear();
        menuNames.clear();
        List<String> menuList = main.getConfig().getStringList("java_menus");
        for (String menuEntry : menuList) {
            String[] menuData = menuEntry.split(";");
            if (menuData.length == 2) {
                String menuName = menuData[0].trim();
                String menuFileName = menuData[1].trim();
                File menuConfigFile = new File(main.getDataFolder()+"/menus/java", menuFileName);
                if (menuConfigFile.exists()) {
                    FileConfiguration menuConfig = YamlConfiguration.loadConfiguration(menuConfigFile);
                    menuConfigs.put(menuName, menuConfig);
                    menuNames.add(menuName);
                } else {
                    getLogger().warning("Menu file '" + menuFileName + "' specified in config.yml not found!");
                }
            } else {
                getLogger().warning("Invalid menu configuration entry: " + menuEntry);
            }
        }
    }

    public void openMenu(Player player, String menuName) {
        FileConfiguration menuConfig = menuConfigs.get(menuName);
        if (menuConfig != null) {
            int menuSize = menuConfig.getInt("menuSize");
            String menuTitle = MessagesUtil.format(player, menuConfig.getString("menuName"));
            Inventory menuInventory = Bukkit.createInventory(null, menuSize, menuTitle);

            ConfigurationSection itemsSection = menuConfig.getConfigurationSection("items");
            if (itemsSection != null) {
                for (String itemName : itemsSection.getKeys(false)) {
                    ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemName);
                    if (itemSection != null) {
                        ItemStack itemStack = ItemManager.createItemStackFromConfig(itemSection, player);
                        int slot = itemSection.getInt("slot");
                        menuInventory.setItem(slot, itemStack);
                    }
                }
            }

            player.openInventory(menuInventory);

            PlayerManager.openMenu(player, menuConfig.getString("menuName"));

            if (menuConfig.contains("animations")) {
                ConfigurationSection animationsConfig = menuConfig.getConfigurationSection("animations");
                for (String animationName : animationsConfig.getKeys(false)) {
                    ConfigurationSection animationConfig = animationsConfig.getConfigurationSection(animationName);
                    AnimationManager.startAnimation(main, player, animationConfig, menuInventory.getSize()); // Pass the menu size
                }
            }
        } else {
            player.sendMessage("Menu not found.");
        }
    }

    public FileConfiguration getMenuConfig(Inventory inventory, Player player) {
        for (FileConfiguration menuConfig : menuConfigs.values()) {
            String menuName = MessagesUtil.format(player, menuConfig.getString("menuName"));
            int menuSize = menuConfig.getInt("menuSize");
            if (inventory.getType() == InventoryType.CHEST && inventory.getSize() == menuSize && PlayerManager.playerMenuTitle.get(player).equals(menuName)) {
                return menuConfig;
            }
        }
        return null;
    }

    static boolean isMenuOpen(Player player) {
        InventoryView openInventory = player.getOpenInventory();
        InventoryType openInventoryType = openInventory.getType();
        return openInventoryType == InventoryType.CHEST;
    }
}
