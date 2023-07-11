package net.blueva.menu;

import net.blueva.menu.commands.main.CommandHandler;
import net.blueva.menu.commands.main.command.BlueMenuCommand;
import net.blueva.menu.commands.main.subcommands.OpenSubCommand;
import net.blueva.menu.commands.main.subcommands.ReloadSubCommand;
import net.blueva.menu.commands.main.tabcomplete.BlueMenuTabComplete;
import net.blueva.menu.configuration.ConfigManager;
import net.blueva.menu.libraries.bstats.Metrics;
import net.blueva.menu.listeners.InventoryClickListener;
import net.blueva.menu.managers.java.MenuManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.Objects;

public class Main extends JavaPlugin implements Listener {

    // Managers
    public MenuManager javaMenuManager;
    public ConfigManager configManager;


    // Lang File
    public FileConfiguration language = null;
    public File languageFile = null;
    public String actualLang;
    public String langPath;

    // Other Things
    public String pluginversion = getDescription().getVersion();


    @Override
    public void onEnable() {
        javaMenuManager = new MenuManager(this);
        configManager = new ConfigManager(this);

        getServer().getPluginManager().registerEvents(new InventoryClickListener(this), this);

        Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "  ____  _            __  __");
        Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + " | __ )| |_   _  ___|  \\/  | ___ _ __  _   _");
        Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + " |  _ \\| | | | |/ _ | |\\/| |/ _ | '_ \\| | | |");
        Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + " | |_) | | |_| |  __| |  | |  __| | | | |_| |");
        Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + " |____/|_|\\__,_|\\___|_|  |_|\\___|_| |_|\\__,_|");
        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "V. 1.0.0 | Plugin enabled successfully | blueva.net");

        configManager.generateFolders();
        saveDefaultConfig();

        actualLang = getConfig().getString("language");

        configManager.registerLang();
        javaMenuManager.loadJavaMenus();
        registerCommands();

        if(getConfig().getBoolean("metrics")) {
            int pluginId = 19060;
            Metrics metrics = new Metrics(this, pluginId);
        }
    }

    @Override
    public void onDisable() {
        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "  ____  _            __  __");
        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + " | __ )| |_   _  ___|  \\/  | ___ _ __  _   _");
        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + " |  _ \\| | | | |/ _ | |\\/| |/ _ | '_ \\| | | |");
        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + " | |_) | | |_| |  __| |  | |  __| | | | |_| |");
        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + " |____/|_|\\__,_|\\___|_|  |_|\\___|_| |_|\\__,_|");
        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "V. 1.0.0 | Plugin disabled successfully | blueva.net");
    }

    public void registerCommands() {
        CommandHandler handler = new CommandHandler(this);

        //main command
        handler.register("bluemenu", new BlueMenuCommand(this));

        // subcommands
        handler.register("open", new OpenSubCommand(this));
        handler.register("reload", new ReloadSubCommand(this));

        Objects.requireNonNull(getCommand("bluemenu")).setExecutor(handler);
        Objects.requireNonNull(getCommand("bluemenu")).setTabCompleter(new BlueMenuTabComplete());
    }
}
