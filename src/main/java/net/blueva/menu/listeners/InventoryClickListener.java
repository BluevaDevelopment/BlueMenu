package net.blueva.menu.listeners;

import net.blueva.menu.Main;
import net.blueva.menu.managers.java.ActionManager;
import net.blueva.menu.managers.java.PlayerManager;
import net.blueva.menu.utils.MessagesUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class InventoryClickListener implements Listener {

    Main main;

    public InventoryClickListener(Main main) {
        this.main = main;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();
        Inventory inventory = e.getClickedInventory();
        ItemStack clickedItem = e.getCurrentItem();

        // main.javaMenuManager.isCustomMenu(inventory, player)
        if (PlayerManager.isPlayerInMenu(player)) {
            e.setCancelled(true);

            if(inventory != null && clickedItem != null) {
                ItemMeta itemMeta = clickedItem.getItemMeta();
                if (itemMeta != null && itemMeta.hasDisplayName()) {
                    String displayName = itemMeta.getDisplayName();
                    FileConfiguration menuConfig = main.javaMenuManager.getMenuConfig(inventory, player);

                    if (menuConfig != null && menuConfig.isConfigurationSection("items")) {
                        ConfigurationSection itemsSection = menuConfig.getConfigurationSection("items");

                        assert itemsSection != null;
                        for (String itemName : itemsSection.getKeys(false)) {
                            ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemName);
                            if (itemSection != null && itemSection.isString("name")) {
                                String itemDisplayName = MessagesUtil.format(player, itemSection.getString("name"));

                                if (displayName.equals(itemDisplayName)) {
                                    List<String> actions = itemSection.getStringList("actions");
                                    ActionManager.executeActions(player, actions, e.getClick());
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
