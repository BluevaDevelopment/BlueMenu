package net.blueva.menu.commands.main;

import net.blueva.menu.Main;
import net.blueva.menu.commands.CommandInterface;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashMap;

public class CommandHandler implements CommandExecutor
{

    private Main main;

    public CommandHandler(Main main) {
        this.main = main;
    }

    private static HashMap<String, CommandInterface> commands = new HashMap<String, CommandInterface>();


    public void register(String name, CommandInterface cmd) {
        commands.put(name, cmd);
    }

    public boolean exists(String name) {
        return commands.containsKey(name);
    }

    public CommandInterface getExecutor(String name) {
        return commands.get(name);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String commandLabel, String[] args) {
        if(args.length == 0) {
            try {
                getExecutor("bluemenu").onCommand(sender, cmd, commandLabel, args);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return true;
        }

        if (exists(args[0])) {
            try {
                getExecutor(args[0]).onCommand(sender, cmd, commandLabel, args);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            sender.sendMessage("Unknown command");
        }
        return true;
    }

}