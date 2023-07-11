package net.blueva.menu.commands.main.tabcomplete;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class BlueMenuTabComplete implements TabCompleter {
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String @NotNull [] args) {
        List<String> subCommands = new ArrayList<>();

        if (args.length == 1) {
            if(sender.hasPermission("bluemenu.admin") || sender.isOp()){
                subCommands.add("open");
                subCommands.add("reload");
            }
            return subCommands;
        }
        return null;
    }
}
