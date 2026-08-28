package net.blueva.menu.commands.main.subcommands;

import net.blueva.menu.Main;
import net.blueva.menu.commands.CommandInterface;
import net.blueva.menu.utils.MessagesUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ConvertSubCommand implements CommandInterface {

    private static final String YAML_EXTENSION = ".yml";
    private static final int YAML_EXTENSION_LENGTH = YAML_EXTENSION.length();
    private static final FilenameFilter YAML_FILE_FILTER =
        (dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(YAML_EXTENSION);

    // DeluxeMenus special material prefixes
    private static final String PREFIX_HEAD   = "head-";
    private static final String PREFIX_BASEHEAD = "basehead-";
    private static final String PREFIX_HDB    = "hdb-";

    private final Main main;

    public ConvertSubCommand(Main main) {
        this.main = main;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) throws IOException {
        if (!sender.hasPermission("bluemenu.convert")
            && !sender.hasPermission("bluemenu.admin")
            && !sender.isOp()) {
            MessagesUtil.sendMessage(sender, main.getConfigManager().getLang().getString("commands.bluemenu.convert.insufficient_permissions"));
            return true;
        }

        File deluxeMenusFolder = new File(main.getDataFolder().getParentFile(), "DeluxeMenus/gui_menus");
        if (!deluxeMenusFolder.isDirectory()) {
            MessagesUtil.sendMessage(sender, main.getConfigManager().getLang().getString("commands.bluemenu.convert.source_not_found"));
            return true;
        }

        File[] sourceFiles = deluxeMenusFolder.listFiles(YAML_FILE_FILTER);
        if (sourceFiles == null || sourceFiles.length == 0) {
            MessagesUtil.sendMessage(sender, main.getConfigManager().getLang().getString("commands.bluemenu.convert.no_menus_found"));
            return true;
        }

        MessagesUtil.sendMessage(sender, main.getConfigManager().getLang().getString("commands.bluemenu.convert.started"));

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
            MessagesUtil.sendMessage(sender, main.getConfigManager().getLang().getString("commands.bluemenu.convert.no_valid_menus"));
            return true;
        }

        if (!newRegistryEntries.isEmpty()) {
            main.getConfigManager().getSettings().set("java_menus", existingRegistryEntries);
            main.getConfigManager().saveSettings();
        }

        main.javaMenuManager.loadJavaMenus();

        String summary = main.getConfigManager().getLang().getString("commands.bluemenu.convert.success");
        if (summary != null) {
            summary = summary
                .replace("{converted}", String.valueOf(convertedCount))
                .replace("{skipped}", String.valueOf(skippedCount))
                .replace("{registered}", String.valueOf(newRegistryEntries.size()));
            MessagesUtil.sendMessage(sender, summary);
        }

        return true;
    }

    // -----------------------------------------------------------------------
    // File conversion
    // -----------------------------------------------------------------------

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
        ConfigurationSection openReq = deluxe.getConfigurationSection("open_requirement");
        if (openReq != null) {
            List<String> openConditions = convertViewRequirement(openReq);
            if (!openConditions.isEmpty()) {
                blue.set("open_conditions", openConditions);
            }
        }
        List<String> openActions = convertStandaloneCommands(deluxe.getStringList("open_commands"));
        if (!openActions.isEmpty()) {
            blue.set("open_actions", openActions);
        }

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

                // Basic fields
                blue.set(basePath + ".name", itemSection.getString("display_name", "&fItem"));
                blue.set(basePath + ".slot", slot);
                if (itemSection.contains("priority")) {
                    blue.set(basePath + ".priority", itemSection.getInt("priority", 0));
                }

                // Material (handles head-/basehead-/hdb-/regular)
                MaterialResult mat = parseMaterial(itemSection.getString("material", "STONE"));
                blue.set(basePath + ".itemStack.material", mat.material());
                if (mat.headValue() != null) {
                    blue.set(basePath + ".itemStack.value", mat.headValue());
                }
                blue.set(basePath + ".itemStack.amount", Math.max(1, itemSection.getInt("amount", 1)));

                // Lore
                List<String> lore = itemSection.getStringList("lore");
                if (!lore.isEmpty()) {
                    blue.set(basePath + ".lore", lore);
                }

                // skull_texture / head field (overrides material-derived value if present)
                String explicitSkull = itemSection.getString("skull_texture");
                if (explicitSkull == null || explicitSkull.isBlank()) {
                    explicitSkull = itemSection.getString("head");
                }
                if (explicitSkull != null && !explicitSkull.isBlank()) {
                    blue.set(basePath + ".itemStack.material", "PLAYER_HEAD");
                    blue.set(basePath + ".itemStack.value", explicitSkull.trim());
                }

                // Enchantments: 'SILK_TOUCH;1' -> '[ENCHANTMENT] SILK_TOUCH;1'
                List<String> attributes = new ArrayList<>();
                List<String> enchantments = itemSection.getStringList("enchantments");
                for (String ench : enchantments) {
                    if (!ench.isBlank()) {
                        attributes.add("[ENCHANTMENT] " + ench.trim().toUpperCase(Locale.ROOT));
                    }
                }

                // Item flags from item_flags list  e.g. HIDE_ATTRIBUTES → [BOOLEAN] HIDE_ATTRIBUTES;true
                List<String> itemFlags = itemSection.getStringList("item_flags");
                for (String flag : itemFlags) {
                    if (!flag.isBlank()) {
                        attributes.add("[BOOLEAN] " + flag.trim().toUpperCase(Locale.ROOT) + ";true");
                    }
                }
                // Legacy boolean shorthand fields
                addBooleanAttribute(attributes, "HIDE_ATTRIBUTES",
                    itemSection.getBoolean("hide_attributes", false));
                addBooleanAttribute(attributes, "HIDE_ENCHANTMENTS",
                    itemSection.getBoolean("hide_enchantments", false));
                addBooleanAttribute(attributes, "HIDE_POTION_EFFECTS",
                    itemSection.getBoolean("hide_effects", false));
                addBooleanAttribute(attributes, "HIDE_UNBREAKABLE",
                    itemSection.getBoolean("hide_unbreakable", false));

                if (!attributes.isEmpty()) {
                    blue.set(basePath + ".attributes", attributes);
                }

                // view_requirement → display_conditions
                ConfigurationSection viewReq = itemSection.getConfigurationSection("view_requirement");
                if (viewReq != null) {
                    List<String> conditions = convertViewRequirement(viewReq);
                    if (!conditions.isEmpty()) {
                        blue.set(basePath + ".display_conditions", conditions);
                    }
                }

                // Actions: preserve per-click mappings including shift/middle parity
                List<String> actions = new ArrayList<>();
                actions.addAll(convertCommands(itemSection.getStringList("click_commands"), "BOTH"));
                actions.addAll(convertCommands(itemSection.getStringList("left_click_commands"), "LEFT_CLICK"));
                actions.addAll(convertCommands(itemSection.getStringList("right_click_commands"), "RIGHT_CLICK"));
                actions.addAll(convertCommands(itemSection.getStringList("shift_left_click_commands"), "SHIFT_LEFT_CLICK"));
                actions.addAll(convertCommands(itemSection.getStringList("shift_right_click_commands"), "SHIFT_RIGHT_CLICK"));
                actions.addAll(convertCommands(itemSection.getStringList("middle_click_commands"), "MIDDLE_CLICK"));

                if (!actions.isEmpty()) {
                    blue.set(basePath + ".actions", actions);
                }
            }
        }

        if (!blue.contains("items") || blue.getConfigurationSection("items") == null
            || blue.getConfigurationSection("items").getKeys(false).isEmpty()) {
            return new ConversionResult(false, "");
        }

        String baseName = sourceFile.getName().substring(0, sourceFile.getName().length() - YAML_EXTENSION_LENGTH);
        String initialFileName = sanitizeFileName(baseName) + YAML_EXTENSION;
        File targetFile = resolveUniqueTargetFile(javaMenusFolder, initialFileName);
        blue.save(targetFile);

        String menuKey = sanitizeMenuKey(targetFile.getName().substring(0, targetFile.getName().length() - YAML_EXTENSION_LENGTH));
        return new ConversionResult(true, menuKey + ";" + targetFile.getName());
    }

    // -----------------------------------------------------------------------
    // Material parsing
    // -----------------------------------------------------------------------

    private MaterialResult parseMaterial(String raw) {
        if (raw == null || raw.isBlank()) {
            return new MaterialResult("STONE", null);
        }

        String lower = raw.trim().toLowerCase(Locale.ROOT);

        // head-<playerName>
        if (lower.startsWith(PREFIX_HEAD)) {
            String playerName = raw.trim().substring(PREFIX_HEAD.length());
            return new MaterialResult("PLAYER_HEAD", playerName);
        }

        // basehead-<base64value>
        if (lower.startsWith(PREFIX_BASEHEAD)) {
            String base64 = raw.trim().substring(PREFIX_BASEHEAD.length());
            return new MaterialResult("PLAYER_HEAD", base64);
        }

        // hdb-<id>: HeadDatabase item – no direct equivalent, use PLAYER_HEAD with id as value
        if (lower.startsWith(PREFIX_HDB)) {
            String hdbId = raw.trim().substring(PREFIX_HDB.length());
            return new MaterialResult("PLAYER_HEAD", hdbId);
        }

        return new MaterialResult(normalizeMaterial(raw), null);
    }

    private String normalizeMaterial(String material) {
        String normalized = material.trim().toUpperCase(Locale.ROOT)
            .replace('-', '_')
            .replace(' ', '_');

        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("SKULL_ITEM", "PLAYER_HEAD");
        aliases.put("SKULL", "PLAYER_HEAD");
        aliases.put("STAINED_GLASS_PANE", "GRAY_STAINED_GLASS_PANE");

        return aliases.getOrDefault(normalized, normalized);
    }

    // -----------------------------------------------------------------------
    // View requirement → display_conditions
    // -----------------------------------------------------------------------

    private List<String> convertViewRequirement(ConfigurationSection viewReq) {
        List<String> conditions = new ArrayList<>();
        ConfigurationSection requirements = viewReq.getConfigurationSection("requirements");
        if (requirements == null) {
            return conditions;
        }

        for (String reqKey : requirements.getKeys(false)) {
            ConfigurationSection req = requirements.getConfigurationSection(reqKey);
            if (req == null) {
                continue;
            }
            String type = req.getString("type", "").toLowerCase(Locale.ROOT).trim();
            String condition = convertRequirement(type, req);
            if (condition != null && !condition.isBlank()) {
                conditions.add(condition);
            }
        }
        return conditions;
    }

    private String convertRequirement(String type, ConfigurationSection req) {
        switch (type) {
            case "has permission" -> {
                String perm = req.getString("permission", "");
                if (perm.isBlank()) return null;
                return "%player_has_permission_" + perm + "% equals true";
            }
            case "!has permission" -> {
                String perm = req.getString("permission", "");
                if (perm.isBlank()) return null;
                return "%player_has_permission_" + perm + "% equals false";
            }
            case "string equals" -> {
                String input  = req.getString("input",  "");
                String output = req.getString("output", "");
                if (input.isBlank()) return null;
                return input + " equals '" + output + "'";
            }
            case "string equals ignorecase" -> {
                String input  = req.getString("input",  "");
                String output = req.getString("output", "");
                if (input.isBlank()) return null;
                return input + " equalsIgnoreCase '" + output + "'";
            }
            case "string contains" -> {
                String input  = req.getString("input",  "");
                String output = req.getString("output", "");
                if (input.isBlank()) return null;
                return input + " contains '" + output + "'";
            }
            case "regex matches" -> {
                String input = req.getString("input", "");
                String output = req.getString("output", "");
                if (input.isBlank() || output.isBlank()) return null;
                return input + " matches '" + output + "'";
            }
            case ">", ">=", "<", "<=", "==" -> {
                String input  = req.getString("input",  "");
                String output = req.getString("output", "");
                if (input.isBlank()) return null;
                return input + " " + type + " " + output;
            }
            case "has money" -> {
                String amount = req.getString("amount", "0");
                return "%vault_eco_balance% >= " + amount;
            }
            default -> {
                return null;
            }
        }
    }

    // -----------------------------------------------------------------------
    // Action conversion
    // -----------------------------------------------------------------------

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

    private List<String> convertStandaloneCommands(List<String> deluxeCommands) {
        List<String> converted = new ArrayList<>();
        for (String deluxeCommand : deluxeCommands) {
            String convertedAction = convertSingleCommand(deluxeCommand);
            if (convertedAction != null && !convertedAction.isBlank()) {
                converted.add(convertedAction);
            }
        }
        return converted;
    }

    private String convertSingleCommand(String deluxeCommand) {
        if (deluxeCommand == null || deluxeCommand.isBlank()) {
            return null;
        }

        // Strip optional <delay=X> suffix
        String trimmed = deluxeCommand.trim().replaceAll("(?i)<delay=\\d+>\\s*$", "").trim();
        if (trimmed.isBlank()) {
            return null;
        }

        if (trimmed.startsWith("[") && trimmed.contains("]")) {
            int endIndex = trimmed.indexOf(']');
            String rawTarget = trimmed.substring(1, endIndex).trim().toLowerCase(Locale.ROOT);
            String rawValue  = trimmed.substring(endIndex + 1).trim();

            return switch (rawTarget) {
                case "console"                     -> rawValue.isBlank() ? null : "CONSOLE;" + stripLeadingSlash(rawValue);
                case "player", "commandevent"      -> rawValue.isBlank() ? null : "PLAYER;" + stripLeadingSlash(rawValue);
                case "message"                     -> rawValue.isBlank() ? null : "MESSAGE;" + rawValue;
                case "broadcast"                   -> rawValue.isBlank() ? null : "BROADCAST;" + rawValue;
                case "close"                       -> "CLOSE";
                case "refresh"                     -> "REFRESH_MENU";
                case "openguimenu", "openmenu",
                     "open_menu"                  -> rawValue.isBlank() ? null : "OPEN_MENU;" + rawValue;
                case "connect"                     -> rawValue.isBlank() ? null : "CONNECT;" + rawValue;
                case "sound"                       -> rawValue.isBlank() ? null : toSoundAction(rawValue);
                // broadcastsound → play sound server-wide via console playsound (best effort)
                case "broadcastsound"              -> rawValue.isBlank() ? null : "CONSOLE;execute run playsound " + rawValue.split("\\s+")[0] + " master @a ~ ~ ~ 1 1";
                // json → strip JSON and send as plain text (lossy)
                case "json"                        -> null;
                // takemoney/givemoney: no BlueMenu equivalent, skip
                case "takemoney", "givemoney"      -> null;
                default                            -> rawValue.isBlank() ? null : "PLAYER;" + stripLeadingSlash(rawValue);
            };
        }

        // Bare command (no [type] prefix) → treat as player command
        return "PLAYER;" + stripLeadingSlash(trimmed);
    }

    private String stripLeadingSlash(String value) {
        return value.startsWith("/") ? value.substring(1) : value;
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

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void addBooleanAttribute(List<String> attributes, String flagName, boolean value) {
        if (value) {
            attributes.add("[BOOLEAN] " + flagName + ";true");
        }
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
        String fallback = fallbackFileName.toLowerCase(Locale.ROOT).replace(YAML_EXTENSION, "");
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
        String baseName = initialFileName.substring(0, initialFileName.length() - YAML_EXTENSION_LENGTH);
        int counter = 1;
        while (target.exists()) {
            target = new File(folder, baseName + "_converted" + counter + YAML_EXTENSION);
            counter++;
        }
        return target;
    }

    // -----------------------------------------------------------------------
    // Records
    // -----------------------------------------------------------------------

    private record MaterialResult(String material, String headValue) {
    }

    private record ConversionResult(boolean converted, String registryEntry) {
    }
}
