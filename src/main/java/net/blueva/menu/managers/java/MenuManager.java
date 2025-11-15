package net.blueva.menu.managers.java;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import fr.mrmicky.fastinv.FastInv;
import net.blueva.menu.Main;
import net.blueva.menu.managers.ConditionManager;
import net.blueva.menu.utils.MessagesUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.bukkit.Bukkit.getLogger;

public class MenuManager {
    public final Map<String, YamlDocument> menuConfigs = new HashMap<>();
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
                    try {
                        // Menus are user-created content, so no auto-update or versioning
                        YamlDocument menuConfig = YamlDocument.create(
                            menuConfigFile,
                            GeneralSettings.DEFAULT,
                            LoaderSettings.DEFAULT,
                            DumperSettings.DEFAULT,
                            UpdaterSettings.DEFAULT
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
        if (menuConfig != null) {
            int menuSize = menuConfig.getInt("menuSize");
            String menuTitle = MessagesUtil.format(player, menuConfig.getString("menuName"));

            // Create FastInv menu
            FastInv menu = new FastInv(menuSize, menuTitle);

            Section itemsSection = menuConfig.getSection("items");
            if (itemsSection != null) {
                for (Object itemNameObj : itemsSection.getKeys()) {
                    String itemName = itemNameObj.toString();
                    Section itemSection = itemsSection.getSection(itemName);
                    if (itemSection != null) {
                        // Check display conditions before adding the item
                        boolean shouldDisplay = true;

                        if (itemSection.contains("display_conditions")) {
                            Object conditionsObj = itemSection.get("display_conditions");
                            if (conditionsObj instanceof Map) {
                                // Map format with all/any/none
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
                Section animationsConfig = menuConfig.getSection("animations");
                if(animationsConfig != null) {
                    for (Object animationNameObj : animationsConfig.getKeys()) {
                        String animationName = animationNameObj.toString();
                        Section animationConfig = animationsConfig.getSection(animationName);
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
