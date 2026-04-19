package net.blueva.menu.managers.java;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import fr.mrmicky.fastinv.FastInv;
import net.blueva.menu.Main;
import net.blueva.menu.managers.ConditionManager;
import net.blueva.menu.utils.MessagesUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MenuManager {
    public final Map<String, YamlDocument> menuConfigs = new HashMap<>();
    public final List<String> menuNames = new ArrayList<>();
    public final Map<Player, FastInv> activeMenus = new HashMap<>();

    private final Main main;

    public MenuManager(Main main) {
        this.main = main;
    }

    public void loadJavaMenus() {
        List<String> menuList = main.getConfigManager().getSettings().getStringList("java_menus");
        main.getMenuSyncService().loadMenus(
            net.blueva.menu.sync.MenuType.JAVA,
            menuList,
            new File(main.getDataFolder(), "menus/java"),
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
        if (menuConfig != null) {
            if (!canOpenMenu(player, menuConfig)) {
                return;
            }

            List<String> openActions = menuConfig.getStringList("open_actions");
            if (!openActions.isEmpty()) {
                ActionManager.executeActions(player, openActions);
            }

            int menuSize = menuConfig.getInt("menuSize");
            String menuTitle = MessagesUtil.format(player, menuConfig.getString("menuName"));

            // Create FastInv menu
            FastInv menu = new FastInv(menuSize, menuTitle);

            Section itemsSection = menuConfig.getSection("items");
            if (itemsSection != null) {
                Map<Integer, PrioritizedMenuItem> prioritizedItems = new HashMap<>();

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
                            int priority = itemSection.getInt("priority", 0);

                            PrioritizedMenuItem existing = prioritizedItems.get(slot);
                            if (existing == null || priority > existing.priority()) {
                                prioritizedItems.put(slot, new PrioritizedMenuItem(itemStack, actions, priority));
                            }
                        }
                    }
                }

                for (Map.Entry<Integer, PrioritizedMenuItem> entry : prioritizedItems.entrySet()) {
                    int slot = entry.getKey();
                    PrioritizedMenuItem item = entry.getValue();
                    menu.setItem(slot, item.itemStack(), e -> {
                        e.setCancelled(true);
                        ActionManager.executeActions(player, item.actions(), e.getClick());
                    });
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

            PlayerManager.openMenu(player, menuConfig.getString("menuName"), menuName);

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
            MessagesUtil.sendMessage(player, Main.getPlugin().configManager.getLang().getString("commands.bluemenu.menu.invalid_menu"));
        }
    }

    private boolean canOpenMenu(Player player, YamlDocument menuConfig) {
        if (!menuConfig.contains("open_conditions")) {
            return true;
        }

        Object openConditionsObj = menuConfig.get("open_conditions");
        if (openConditionsObj instanceof Map<?, ?> rawMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> openConditionsMap = (Map<String, Object>) rawMap;
            return ConditionManager.evaluateConditionsMap(player, openConditionsMap);
        }

        if (openConditionsObj instanceof List<?> rawList) {
            List<String> conditions = new ArrayList<>();
            for (Object condition : rawList) {
                if (condition instanceof String conditionString) {
                    conditions.add(conditionString);
                }
            }
            return ConditionManager.evaluateConditions(player, conditions);
        }

        if (openConditionsObj instanceof String singleCondition) {
            return ConditionManager.evaluateCondition(player, singleCondition);
        }

        return true;
    }

    public FastInv getActiveMenu(Player player) {
        return activeMenus.get(player);
    }

    static boolean isMenuOpen(Player player) {
        return PlayerManager.isPlayerInMenu(player);
    }

    private record PrioritizedMenuItem(ItemStack itemStack, List<String> actions, int priority) {
    }
}
