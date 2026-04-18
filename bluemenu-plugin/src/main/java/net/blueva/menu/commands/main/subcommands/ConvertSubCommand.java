package net.blueva.menu.commands.main.subcommands;

import net.blueva.menu.Main;
import net.blueva.menu.commands.CommandInterface;
import net.blueva.menu.utils.MessagesUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ConvertSubCommand implements CommandInterface {

    private final Main main;

    public ConvertSubCommand(Main main) {
        this.main = main;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) throws IOException {
        if (!sender.hasPermission("bluemenu.convert")
            && !sender.hasPermission("bluemenu.admin")
            && !sender.isOp()) {
            MessagesUtil.sendMessage(sender, main.configManager.getLang().getString("commands.bluemenu.convert.insufficient_permissions"));
            return true;
        }

        File deluxeMenusFolder = new File(main.getDataFolder().getParentFile(), "DeluxeMenus/gui_menus");
        if (!deluxeMenusFolder.isDirectory()) {
            MessagesUtil.sendMessage(sender, main.configManager.getLang().getString("commands.bluemenu.convert.source_not_found"));
            return true;
        }

        File[] sourceFiles = deluxeMenusFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (sourceFiles == null || sourceFiles.length == 0) {
            MessagesUtil.sendMessage(sender, main.configManager.getLang().getString("commands.bluemenu.convert.no_menus_found"));
            return true;
        }

        MessagesUtil.sendMessage(sender, main.configManager.getLang().getString("commands.bluemenu.convert.started"));

        File javaMenusFolder = new File(main.getDataFolder(), "menus/java");
        if (!javaMenusFolder.exists()) {
            javaMenusFolder.mkdirs();
        }

        int convertedCount = 0;
        int skippedCount = 0;
        List<String> newRegistryEntries = new ArrayList<>();
        List<String> existingRegistryEntries = new ArrayList<>(main.getConfigManager().getSettings().getStringList("java_menus"));

        for (File sourceFile : sourceFiles) {
            try {
                ConversionResult result = convertFile(sourceFile, javaMenusFolder);
                if (!result.converted()) {
                    skippedCount++;
                    continue;
                }

                convertedCount++;
                if (!existingRegistryEntries.contains(result.registryEntry())) {
                    existingRegistryEntries.add(result.registryEntry());
                    newRegistryEntries.add(result.registryEntry());
                }
            } catch (Exception ex) {
                skippedCount++;
                main.getLogger().warning("Failed to convert DeluxeMenus file " + sourceFile.getName() + ": " + ex.getMessage());
            }
        }

        if (convertedCount == 0) {
            MessagesUtil.sendMessage(sender, main.configManager.getLang().getString("commands.bluemenu.convert.no_valid_menus"));
            return true;
        }

        if (!newRegistryEntries.isEmpty()) {
            main.getConfigManager().getSettings().set("java_menus", existingRegistryEntries);
            main.getConfigManager().saveSettings();
        }

        main.javaMenuManager.loadJavaMenus();

        String summary = main.configManager.getLang().getString("commands.bluemenu.convert.success");
        if (summary != null) {
            summary = summary
                .replace("{converted}", String.valueOf(convertedCount))
                .replace("{skipped}", String.valueOf(skippedCount))
                .replace("{registered}", String.valueOf(newRegistryEntries.size()));
            MessagesUtil.sendMessage(sender, summary);
        }

        return true;
    }

    private ConversionResult convertFile(File sourceFile, File javaMenusFolder) throws IOException {
        YamlConfiguration deluxe = YamlConfiguration.loadConfiguration(sourceFile);
        ConfigurationSection itemsSection = deluxe.getConfigurationSection("items");
        if (itemsSection == null || itemsSection.getKeys(false).isEmpty()) {
            return new ConversionResult(false, "");
        }

        YamlConfiguration blue = new YamlConfiguration();
        blue.set("file_version", 1);
        blue.set("menuName", deluxe.getString("menu_title", "&8Converted Menu"));
        blue.set("menuSize", normalizeMenuSize(deluxe.getInt("size", 54)));
        blue.set("type", "CHEST");
        blue.set("openCommand", toBlueOpenCommand(deluxe.get("open_command"), sourceFile.getName()));

        int itemIndex = 1;
        for (String originalItemKey : itemsSection.getKeys(false)) {
            ConfigurationSection itemSection = itemsSection.getConfigurationSection(originalItemKey);
            if (itemSection == null) {
                continue;
            }

            List<Integer> slots = resolveSlots(itemSection);
            if (slots.isEmpty()) {
                continue;
            }

            for (int slot : slots) {
                String outputItemKey = "item" + itemIndex++;
                String basePath = "items." + outputItemKey;

                blue.set(basePath + ".name", itemSection.getString("display_name", "&fItem"));
                blue.set(basePath + ".slot", slot);
                blue.set(basePath + ".itemStack.material", normalizeMaterial(itemSection.getString("material", "STONE")));
                blue.set(basePath + ".itemStack.amount", Math.max(1, itemSection.getInt("amount", 1)));

                List<String> lore = itemSection.getStringList("lore");
                if (!lore.isEmpty()) {
                    blue.set(basePath + ".lore", lore);
                }

                String skullValue = itemSection.getString("head");
                if (skullValue == null || skullValue.isBlank()) {
                    skullValue = itemSection.getString("skull_texture");
                }
                if (skullValue != null && !skullValue.isBlank()) {
                    blue.set(basePath + ".itemStack.value", skullValue.trim());
                }

                List<String> actions = new ArrayList<>();
                actions.addAll(convertCommands(itemSection.getStringList("click_commands"), "BOTH"));
                actions.addAll(convertCommands(itemSection.getStringList("left_click_commands"), "LEFT_CLICK"));
                actions.addAll(convertCommands(itemSection.getStringList("right_click_commands"), "RIGHT_CLICK"));
                if (!actions.isEmpty()) {
                    blue.set(basePath + ".actions", actions);
                }
            }
        }

        if (!blue.contains("items") || blue.getConfigurationSection("items") == null ||
            blue.getConfigurationSection("items").getKeys(false).isEmpty()) {
            return new ConversionResult(false, "");
        }

        String baseName = sourceFile.getName().substring(0, sourceFile.getName().length() - 4);
        String initialFileName = sanitizeFileName(baseName) + ".yml";
        File targetFile = resolveUniqueTargetFile(javaMenusFolder, initialFileName);
        blue.save(targetFile);

        String menuKey = sanitizeMenuKey(targetFile.getName().substring(0, targetFile.getName().length() - 4));
        return new ConversionResult(true, menuKey + ";" + targetFile.getName());
    }

    private int normalizeMenuSize(int size) {
        int normalized = Math.max(9, Math.min(54, size));
        int remainder = normalized % 9;
        if (remainder != 0) {
            normalized += (9 - remainder);
        }
        return Math.min(54, normalized);
    }

    private String toBlueOpenCommand(Object openCommand, String fallbackFileName) {
        if (openCommand instanceof List<?> list && !list.isEmpty()) {
            String first = String.valueOf(list.get(0)).trim();
            if (!first.isEmpty()) {
                return ensureSlash(first);
            }
        }

        if (openCommand instanceof String command && !command.isBlank()) {
            String trimmed = command.trim();
            if (!trimmed.isEmpty()) {
                return ensureSlash(trimmed);
            }
        }

        String fallback = fallbackFileName.toLowerCase(Locale.ROOT).replace(".yml", "");
        return "/" + sanitizeMenuKey(fallback);
    }

    private String ensureSlash(String command) {
        return command.startsWith("/") ? command : "/" + command;
    }

    private List<Integer> resolveSlots(ConfigurationSection itemSection) {
        List<Integer> slots = new ArrayList<>();
        if (itemSection.contains("slot")) {
            slots.add(itemSection.getInt("slot"));
        }

        List<Integer> listedSlots = itemSection.getIntegerList("slots");
        if (!listedSlots.isEmpty()) {
            slots.clear();
            slots.addAll(listedSlots);
        }

        slots.removeIf(slot -> slot < 0 || slot > 53);
        return slots;
    }

    private String normalizeMaterial(String material) {
        if (material == null || material.isBlank()) {
            return "STONE";
        }

        String normalized = material.trim().toUpperCase(Locale.ROOT)
            .replace('-', '_')
            .replace(' ', '_');

        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("SKULL_ITEM", "PLAYER_HEAD");
        aliases.put("SKULL", "PLAYER_HEAD");
        aliases.put("STAINED_GLASS_PANE", "GRAY_STAINED_GLASS_PANE");

        return aliases.getOrDefault(normalized, normalized);
    }

    private List<String> convertCommands(List<String> deluxeCommands, String clickType) {
        List<String> converted = new ArrayList<>();
        for (String deluxeCommand : deluxeCommands) {
            String convertedAction = convertSingleCommand(deluxeCommand);
            if (convertedAction != null && !convertedAction.isBlank()) {
                converted.add("[" + clickType + "] " + convertedAction);
            }
        }
        return converted;
    }

    private String convertSingleCommand(String deluxeCommand) {
        if (deluxeCommand == null || deluxeCommand.isBlank()) {
            return null;
        }

        String trimmed = deluxeCommand.trim();

        if (trimmed.startsWith("[") && trimmed.contains("]")) {
            int endIndex = trimmed.indexOf(']');
            String rawTarget = trimmed.substring(1, endIndex).trim().toLowerCase(Locale.ROOT);
            String rawValue = trimmed.substring(endIndex + 1).trim();

            return switch (rawTarget) {
                case "console" -> rawValue.isBlank() ? null : "CONSOLE;" + stripLeadingSlash(rawValue);
                case "player" -> rawValue.isBlank() ? null : "PLAYER;" + stripLeadingSlash(rawValue);
                case "message" -> rawValue.isBlank() ? null : "MESSAGE;" + rawValue;
                case "broadcast" -> rawValue.isBlank() ? null : "BROADCAST;" + rawValue;
                case "close" -> "CLOSE";
                case "refresh" -> "REFRESH_MENU";
                case "openguimenu", "openmenu", "open_menu" -> rawValue.isBlank() ? null : "OPEN_MENU;" + rawValue;
                case "sound" -> rawValue.isBlank() ? null : toSoundAction(rawValue);
                default -> rawValue.isBlank() ? null : "PLAYER;" + stripLeadingSlash(rawValue);
            };
        }

        return "PLAYER;" + stripLeadingSlash(trimmed);
    }

    private String stripLeadingSlash(String value) {
        if (value.startsWith("/")) {
            return value.substring(1);
        }
        return value;
    }

    private String toSoundAction(String rawValue) {
        String[] parts = rawValue.split("\\s+");
        StringBuilder builder = new StringBuilder("SOUND");
        for (String part : parts) {
            if (!part.isBlank()) {
                builder.append(';').append(part);
            }
        }
        return builder.toString();
    }

    private String sanitizeFileName(String value) {
        String sanitized = value.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9._-]", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_+|_+$", "");
        return sanitized.isBlank() ? "converted_menu" : sanitized;
    }

    private String sanitizeMenuKey(String value) {
        String sanitized = value.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9_-]", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_+|_+$", "");
        return sanitized.isBlank() ? "converted_menu" : sanitized;
    }

    private File resolveUniqueTargetFile(File folder, String initialFileName) {
        File target = new File(folder, initialFileName);
        if (!target.exists()) {
            return target;
        }

        String baseName = initialFileName.substring(0, initialFileName.length() - 4);
        int counter = 1;
        while (target.exists()) {
            target = new File(folder, baseName + "_converted" + counter + ".yml");
            counter++;
        }
        return target;
    }

    private record ConversionResult(boolean converted, String registryEntry) {
    }
}
