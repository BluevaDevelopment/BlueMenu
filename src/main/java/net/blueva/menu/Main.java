package net.blueva.menu;

import net.blueva.menu.commands.main.CommandHandler;
import net.blueva.menu.commands.main.command.BlueMenuCommand;
import net.blueva.menu.commands.main.subcommands.OpenSubCommand;
import net.blueva.menu.commands.main.tabcomplete.BlueMenuTabComplete;
import net.blueva.menu.listeners.InventoryClickListener;
import net.blueva.menu.managers.java.MenuManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.Objects;

public class Main extends JavaPlugin implements Listener {

    public MenuManager javaMenuManager;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        javaMenuManager = new MenuManager(this);

        getServer().getPluginManager().registerEvents(new InventoryClickListener(this), this);

        loadConfig();
        registerCommands();

        Objects.requireNonNull(getCommand("bluemenu")).setExecutor(this);
    }

    public void registerCommands() {
        CommandHandler handler = new CommandHandler(this);

        //main command
        handler.register("bluemenu", new BlueMenuCommand(this));

        // subcommands
        handler.register("open", new OpenSubCommand(this));

        Objects.requireNonNull(getCommand("bluemenu")).setExecutor(handler);
        Objects.requireNonNull(getCommand("bluemenu")).setTabCompleter(new BlueMenuTabComplete());
    }

    private void loadConfig() {
        saveDefaultConfig();

        List<String> menuList = getConfig().getStringList("java_menus");
        for (String menuEntry : menuList) {
            String[] menuData = menuEntry.split(":");
            if (menuData.length == 2) {
                String menuName = menuData[0].trim();
                String menuFileName = menuData[1].trim();
                File menuConfigFile = new File(getDataFolder(), menuFileName);
                if (menuConfigFile.exists()) {
                    FileConfiguration menuConfig = YamlConfiguration.loadConfiguration(menuConfigFile);
                    javaMenuManager.menuConfigs.put(menuName, menuConfig);
                } else {
                    getLogger().warning("Menu file '" + menuFileName + "' specified in config.yml not found!");
                }
            } else {
                getLogger().warning("Invalid menu configuration entry: " + menuEntry);
            }
        }
    }
}
