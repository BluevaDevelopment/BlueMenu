package net.blueva.menu.managers.java;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import net.blueva.menu.Main;
import net.blueva.menu.utils.MessagesUtil;
import net.blueva.menu.managers.java.customitems.CustomItemProviderResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.bukkit.Bukkit.getLogger;

public class ItemManager {
    public static ItemStack createItemStackFromConfig(Section itemSection, Player player) {
        String itemName = MessagesUtil.format(player, itemSection.getString("name"));
        String materialName = itemSection.getString("itemStack.material");
        ItemStack itemStack = createCustomPluginItem(itemSection, player, materialName);
        Material material;

        if (itemStack != null) {
            material = itemStack.getType();
        } else {
            material = Material.valueOf(materialName);
            itemStack = new ItemStack(material);
        }

        int amount = itemSection.getInt("itemStack.amount");
        itemStack.setAmount(Math.max(1, amount));
        ItemMeta itemMeta = itemStack.getItemMeta();
        assert itemMeta != null;
        itemMeta.setDisplayName(itemName);

        List<String> lore = MessagesUtil.format(player, itemSection.getStringList("lore"));
        itemMeta.setLore(lore);

        itemStack.setItemMeta(itemMeta);

        // Set custom head value
        if (material == Material.PLAYER_HEAD) {
            String value = itemSection.getString("itemStack.value");
            // Process placeholders in value (important for {player_name} or %player_name%)
            if (value != null && !value.isEmpty()) {
                value = MessagesUtil.format(player, value);
            }
            itemStack = setCustomHeadValue(itemStack, value);
        }

        List<String> attributes = itemSection.getStringList("attributes");
        if (!attributes.isEmpty()) {
            itemStack = applyAttributes(itemStack, attributes);
        }

        return itemStack;
    }

    private static ItemStack createCustomPluginItem(Section itemSection, Player player, String materialName) {
        String provider = MessagesUtil.format(player, itemSection.getString("itemStack.provider", ""));
        String id = MessagesUtil.format(player, itemSection.getString("itemStack.id", ""));

        if (!provider.isBlank() && !id.isBlank()) {
            ItemStack providerItem = createCustomPluginItemByProvider(provider, id);
            if (providerItem != null) {
                return providerItem;
            }
        }

        if (materialName == null || materialName.isBlank()) {
            return null;
        }

        return createCustomPluginItemByPrefixedMaterial(MessagesUtil.format(player, materialName));
    }

    private static ItemStack createCustomPluginItemByPrefixedMaterial(String materialName) {
        if (materialName == null || !materialName.contains(":")) {
            return null;
        }

        String[] materialParts = materialName.split(":", 2);
        if (materialParts.length != 2) {
            return null;
        }

        return createCustomPluginItemByProvider(materialParts[0], materialParts[1]);
    }

    private static ItemStack createCustomPluginItemByProvider(String provider, String itemId) {
        return CustomItemProviderResolver.createItem(provider, itemId);
    }

    static ItemStack applyAttributes(ItemStack itemStack, List<String> attributes) {
        ItemMeta itemMeta = itemStack.getItemMeta();

        for (String attribute : attributes) {
            String attributeType = attribute.substring(1, attribute.indexOf("]"));
            String attributeValue = attribute.substring(attribute.indexOf("]") + 1).trim();

            switch (attributeType.toUpperCase()) {
                case "ENCHANTMENT" -> applyEnchantmentAttribute(itemMeta, attributeValue);
                case "BOOLEAN" -> applyBooleanAttribute(itemMeta, attributeValue);

                // Add more attribute types here as needed
                default -> getLogger().warning(Objects.requireNonNull(Main.getPlugin().configManager.getLang().getString("commands.bluemenu.manager.invalid_attribute")).replace("{type}", attributeType));
            }
        }

        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    private static void applyEnchantmentAttribute(ItemMeta itemMeta, String attributeValue) {
        String[] enchantmentData = attributeValue.split(";");
        if (enchantmentData.length == 2) {
            Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(enchantmentData[0].trim().toLowerCase()));
            if (enchantment != null) {
                int level = Integer.parseInt(enchantmentData[1].trim());
                itemMeta.addEnchant(enchantment, level, true);
            }
        }
    }

    private static void applyBooleanAttribute(ItemMeta itemMeta, String attributeValue) {
        String[] booleanData = attributeValue.split(";");
        if (booleanData.length == 2) {
            String flagName = booleanData[0].trim().toUpperCase();
            boolean flagValue = Boolean.parseBoolean(booleanData[1].trim());

            ItemFlag itemFlag = ItemFlag.valueOf(flagName);
            if (flagValue) {
                itemMeta.addItemFlags(itemFlag);
            } else {
                itemMeta.removeItemFlags(itemFlag);
            }
        }
    }

    private static ItemStack setCustomHeadValue(ItemStack itemStack, String value) {
        if (value == null || value.isEmpty()) {
            return itemStack;
        }

        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return itemStack;
        }

        try {
            // First, try parsing as customModelData (integer)
            int customModelData = Integer.parseInt(value);
            itemMeta.setCustomModelData(customModelData);
            itemStack.setItemMeta(itemMeta);
            return itemStack;
        } catch (NumberFormatException ignored) {
            // Not a number, continue with head handling
        }

        if (!(itemMeta instanceof SkullMeta skullMeta)) {
            return itemStack;
        }

        // Is this a player name?
        if (value.length() >= 3 && value.length() <= 16 && value.matches("^[a-zA-Z0-9_]+$")) {
            // New API: create a PlayerProfile by name
            PlayerProfile profile = Bukkit.createPlayerProfile(value);
            skullMeta.setOwnerProfile(profile);
        } else {
            // Handle textures (base64, direct URL, or texture hash)
            try {
                PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
                PlayerTextures textures = profile.getTextures();

                String textureUrl;

                if (value.startsWith("http://") || value.startsWith("https://")) {
                    // Direct URL
                    textureUrl = value;
                } else {
                    // Try decoding base64
                    try {
                        String decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
                        JsonObject jsonObject = JsonParser.parseString(decoded).getAsJsonObject();
                        textureUrl = jsonObject.getAsJsonObject("textures")
                                .getAsJsonObject("SKIN")
                                .get("url")
                                .getAsString();
                    } catch (Exception decodeEx) {
                        // If it fails, assume it's a textures.minecraft.net hash
                        textureUrl = "http://textures.minecraft.net/texture/" + value;
                    }
                }

                textures.setSkin(new URL(textureUrl));
                profile.setTextures(textures);
                skullMeta.setOwnerProfile(profile);
            } catch (MalformedURLException ex) {
                ex.printStackTrace();
            }
        }

        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }






}
