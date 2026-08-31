package net.blueva.menu.managers.java;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import fr.mrmicky.fastinv.FastInv;
import net.blueva.menu.Main;
import net.blueva.menu.managers.ConditionManager;
import net.blueva.menu.utils.MessagesUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MenuManager {
    public final Map<String, YamlDocument> menuConfigs = new HashMap<>();
    public final List<String> menuNames = new ArrayList<>();
    public final Map<Player, FastInv> activeMenus = new HashMap<>();
    private final Map<UUID, List<BukkitTask>> activeAnimations = new ConcurrentHashMap<>();

    private final Main main;

    public MenuManager(Main main) {
        this.main = main;
    }

    /** Track an animation task so it can be cancelled when the menu closes or the plugin disables. */
    public void registerAnimationTask(Player player, BukkitTask task) {
        activeAnimations.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).add(task);
    }

    /** Cancel and forget every animation task running for a player. */
    public void cancelAnimations(Player player) {
        List<BukkitTask> tasks = activeAnimations.remove(player.getUniqueId());
        if (tasks != null) {
            for (BukkitTask task : tasks) {
                try {
                    task.cancel();
                } catch (Exception ignored) {
                    // task already dead
                }
            }
        }
    }

    /** Cancel every animation and drop all live menu references. Call on plugin disable. */
    public void shutdown() {
        for (List<BukkitTask> tasks : activeAnimations.values()) {
            for (BukkitTask task : tasks) {
                try {
                    task.cancel();
                } catch (Exception ignored) {
                    // task already dead
                }
            }
        }
        activeAnimations.clear();
        activeMenus.clear();
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

            // Kill any animation left over from a previously open menu before we build the new one.
            cancelAnimations(player);

            List<String> openActions = menuConfig.getStringList("open_actions");
            if (!openActions.isEmpty()) {
                ActionManager.executeActions(player, openActions);
            }

            int menuSize = normalizeMenuSize(menuConfig.getInt("menuSize", 27));
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
                            ItemStack itemStack;
                            try {
                                itemStack = ItemManager.createItemStackFromConfig(itemSection, player);
                            } catch (Exception ex) {
                                main.getLogger().warning("Skipping menu item '" + itemName + "' in menu '"
                                    + menuName + "': " + ex.getMessage());
                                continue;
                            }

                            int slot = itemSection.getInt("slot");
                            if (slot < 0 || slot >= menuSize) {
                                main.getLogger().warning("Menu item '" + itemName + "' in menu '" + menuName
                                    + "' has slot " + slot + " outside the menu (size " + menuSize + ") - skipping.");
                                continue;
                            }
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
                cancelAnimations(player);
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

    /** Clamp a configured chest size to a Bukkit-legal value (9..54, multiple of 9). */
    private int normalizeMenuSize(int configured) {
        int size = configured;
        if (size < 9) {
            size = 9;
        } else if (size > 54) {
            size = 54;
        } else if (size % 9 != 0) {
            size = Math.min(54, ((size / 9) + 1) * 9);
        }
        return size;
    }

    private record PrioritizedMenuItem(ItemStack itemStack, List<String> actions, int priority) {
    }
}
