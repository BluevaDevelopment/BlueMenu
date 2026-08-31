package net.blueva.menu.managers.bedrock;

import dev.dejvokep.boostedyaml.YamlDocument;
import net.blueva.menu.Main;
import net.blueva.menu.managers.ConditionManager;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;

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
        if (menuConfig == null) {
            return;
        }

        // A Bedrock form can only be sent to a Floodgate (Bedrock) player.
        if (FloodgateApi.getInstance().getPlayer(player.getUniqueId()) == null) {
            main.getLogger().warning("Cannot open Bedrock menu '" + menuName + "' for " + player.getName()
                + ": they are not a Floodgate (Bedrock) player.");
            return;
        }

        if (!canOpenMenu(player, menuConfig)) {
            return;
        }

        List<String> openActions = menuConfig.getStringList("open_actions");
        if (!openActions.isEmpty()) {
            ActionManager.executeActions(player, openActions);
        }

        lastOpenedMenus.put(player.getUniqueId(), menuName);
        String menuType = menuConfig.getString("type", "");

        try {
            if (menuType.equalsIgnoreCase("SIMPLE")) {
                SimpleManager.openMenu(player, menuConfig);
            } else if (menuType.equalsIgnoreCase("MODAL")) {
                ModalManager.openMenu(player, menuConfig);
            } else if (menuType.equalsIgnoreCase("CUSTOM")) {
                CustomManager.openMenu(player, menuConfig);
            } else {
                main.getLogger().warning("Bedrock menu '" + menuName + "' has an unknown type: '" + menuType + "'");
            }
        } catch (Exception e) {
            main.getLogger().warning("Failed to open Bedrock menu '" + menuName + "' for " + player.getName()
                + ": " + e.getMessage());
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

    public String getLastOpenedMenu(Player player) {
        return lastOpenedMenus.get(player.getUniqueId());
    }
}
