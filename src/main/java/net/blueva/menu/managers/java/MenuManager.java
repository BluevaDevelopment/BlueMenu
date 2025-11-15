package net.blueva.menu.managers.java;

import fr.mrmicky.fastinv.FastInv;
import net.blueva.menu.Main;
import net.blueva.menu.managers.ConditionManager;
import net.blueva.menu.utils.MessagesUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;

import static org.bukkit.Bukkit.getLogger;

public class MenuManager {
    public final Map<String, FileConfiguration> menuConfigs = new HashMap<>();
    public final List<String> menuNames = new ArrayList<>();
    public final Map<Player, FastInv> activeMenus = new HashMap<>();

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
                    getLogger().warning(Objects.requireNonNull(Main.getPlugin().configManager.getLang().getString("global.error.invalid_menu_file")).replace("{name}", menuFileName));
                }
            } else {
                getLogger().warning(Objects.requireNonNull(Main.getPlugin().configManager.getLang().getString("global.error.invalid_menu_entry")).replace("{entry}", menuEntry));
            }
        }
    }

    public void openMenu(Player player, String menuName) {
        FileConfiguration menuConfig = menuConfigs.get(menuName);
        if (menuConfig != null) {
            int menuSize = menuConfig.getInt("menuSize");
            String menuTitle = MessagesUtil.format(player, menuConfig.getString("menuName"));

            // Create FastInv menu
            FastInv menu = new FastInv(menuSize, menuTitle);

            ConfigurationSection itemsSection = menuConfig.getConfigurationSection("items");
            if (itemsSection != null) {
                for (String itemName : itemsSection.getKeys(false)) {
                    ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemName);
                    if (itemSection != null) {
                        // Check display conditions before adding the item
                        boolean shouldDisplay = true;

                        // Check for display_conditions list
                        if (itemSection.contains("display_conditions")) {
                            List<String> displayConditions = itemSection.getStringList("display_conditions");
                            shouldDisplay = ConditionManager.evaluateConditions(player, displayConditions);
                        }

                        // Check for conditions map with all/any/none
                        if (shouldDisplay && itemSection.contains("conditions")) {
                            Object conditionsObj = itemSection.get("conditions");
                            if (conditionsObj instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> conditionsMap = (Map<String, Object>) conditionsObj;
                                shouldDisplay = ConditionManager.evaluateConditionsMap(player, conditionsMap);
                            } else if (conditionsObj instanceof List) {
                                // Simple list format
                                @SuppressWarnings("unchecked")
                                List<String> conditionsList = (List<String>) conditionsObj;
                                shouldDisplay = ConditionManager.evaluateConditions(player, conditionsList);
                            }
                        }

                        // Only add the item if conditions pass
                        if (shouldDisplay) {
                            ItemStack itemStack = ItemManager.createItemStackFromConfig(itemSection, player);
                            int slot = itemSection.getInt("slot");
                            List<String> actions = itemSection.getStringList("actions");

                            // Add item with click handler
                            menu.setItem(slot, itemStack, e -> {
                                e.setCancelled(true);
                                ActionManager.executeActions(player, actions, e.getClick());
                            });
                        }
                    }
                }
            }

            // Add close handler
            menu.addCloseHandler(e -> {
                activeMenus.remove(player);
                PlayerManager.closeMenu(player);
            });

            // Store active menu for animations
            activeMenus.put(player, menu);

            // Open the menu
            menu.open(player);

            PlayerManager.openMenu(player, menuConfig.getString("menuName"));

            // Start animations if configured
            if (menuConfig.contains("animations")) {
                ConfigurationSection animationsConfig = menuConfig.getConfigurationSection("animations");
                if(animationsConfig != null) {
                    for (String animationName : animationsConfig.getKeys(false)) {
                        ConfigurationSection animationConfig = animationsConfig.getConfigurationSection(animationName);
                        if(animationConfig != null) {
                            AnimationManager.startAnimation(main, player, animationConfig, menuSize);
                        }
                    }
                }
            }
        } else {
            MessagesUtil.sendMessage(player, Main.getPlugin().configManager.getLang().getString("global.error.invalid_menu"));
        }
    }

    public FastInv getActiveMenu(Player player) {
        return activeMenus.get(player);
    }

    static boolean isMenuOpen(Player player) {
        return PlayerManager.isPlayerInMenu(player);
    }
}
