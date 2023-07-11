package net.blueva.menu.managers.java;

import net.blueva.menu.Main;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;

public class MenuManager {
    public final Map<String, FileConfiguration> menuConfigs;

    public MenuManager(Main main) {
        this.menuConfigs = new HashMap<>();
    }

    public void openMenu(Player player, String menuName) {
        FileConfiguration menuConfig = menuConfigs.get(menuName);
        if (menuConfig != null) {
            int menuSize = menuConfig.getInt("menuSize");
            String menuTitle = menuConfig.getString("menuName");
            Inventory menuInventory = Bukkit.createInventory(null, menuSize, menuTitle);

            ConfigurationSection itemsSection = menuConfig.getConfigurationSection("items");
            if (itemsSection != null) {
                for (String itemName : itemsSection.getKeys(false)) {
                    ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemName);
                    if (itemSection != null) {
                        ItemStack itemStack = ItemManager.createItemStackFromConfig(itemSection);
                        int slot = itemSection.getInt("slot");
                        menuInventory.setItem(slot, itemStack);
                    }
                }
            }

            player.openInventory(menuInventory);

            if (menuConfig.contains("animations")) {
                ConfigurationSection animationsConfig = menuConfig.getConfigurationSection("animations");
                for (String animationName : animationsConfig.getKeys(false)) {
                    ConfigurationSection animationConfig = animationsConfig.getConfigurationSection(animationName);
                    AnimationManager.startAnimation(player, animationConfig, menuInventory.getSize()); // Pass the menu size
                }
            }
        } else {
            player.sendMessage("Menu not found.");
        }
    }

    public FileConfiguration getMenuConfigByInventory(Inventory inventory) {
        for (FileConfiguration menuConfig : menuConfigs.values()) {
            int menuSize = menuConfig.getInt("menuSize");
            if (inventory.getType() == InventoryType.CHEST && inventory.getSize() == menuSize) {
                return menuConfig;
            }
        }
        return null;
    }

    public boolean isCustomMenu(Inventory inventory) {
        for (FileConfiguration menuConfig : menuConfigs.values()) {
            int menuSize = menuConfig.getInt("menuSize");
            if (inventory.getType() == InventoryType.CHEST && inventory.getSize() == menuSize) {
                ItemStack[] menuItems = inventory.getContents();
                ConfigurationSection itemsSection = menuConfig.getConfigurationSection("items");
                if (itemsSection != null) {
                    for (String itemName : itemsSection.getKeys(false)) {
                        ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemName);
                        if (itemSection != null) {
                            ItemStack itemStack = ItemManager.createItemStackFromConfig(itemSection);
                            int slot = itemSection.getInt("slot");
                            ItemStack inventoryItem = menuItems[slot];
                            if (!isSimilar(itemStack, inventoryItem)) {
                                return false;
                            }
                        }
                    }
                }
                return true;
            }
        }
        return false;
    }

    private boolean isSimilar(ItemStack itemStack1, ItemStack itemStack2) {
        if (itemStack1 == null || itemStack2 == null) {
            return itemStack1 == itemStack2;
        }

        Material material1 = itemStack1.getType();
        Material material2 = itemStack2.getType();

        if (material1 != material2) {
            return false;
        }

        ItemMeta meta1 = itemStack1.getItemMeta();
        ItemMeta meta2 = itemStack2.getItemMeta();

        if (meta1 == null && meta2 == null) {
            return true;
        }

        if (meta1 != null && meta2 != null) {
            if (!meta1.hasDisplayName() && !meta2.hasDisplayName()) {
                return true;
            }

            if (meta1.hasDisplayName() && meta2.hasDisplayName()) {
                return meta1.getDisplayName().equals(meta2.getDisplayName());
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    static boolean isMenuOpen(Player player) {
        InventoryView openInventory = player.getOpenInventory();
        InventoryType openInventoryType = openInventory.getType();
        return openInventoryType == InventoryType.CHEST;
    }
}
