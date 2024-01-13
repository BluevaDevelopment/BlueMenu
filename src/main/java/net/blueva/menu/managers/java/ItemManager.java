package net.blueva.menu.managers.java;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.blueva.menu.Main;
import net.blueva.menu.utils.MessagesUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.bukkit.Bukkit.getLogger;

public class ItemManager {
    public static ItemStack createItemStackFromConfig(ConfigurationSection itemSection, Player player) {
        String itemName = MessagesUtil.format(player, itemSection.getString("name"));
        Material material = Material.valueOf(itemSection.getString("itemStack.material"));
        int amount = itemSection.getInt("itemStack.amount");

        ItemStack itemStack = new ItemStack(material, amount);
        ItemMeta itemMeta = itemStack.getItemMeta();
        assert itemMeta != null;
        itemMeta.setDisplayName(itemName);

        List<String> lore = MessagesUtil.format(player, itemSection.getStringList("lore"));
        itemMeta.setLore(lore);

        itemStack.setItemMeta(itemMeta);

        // Set custom head value
        if (material == Material.PLAYER_HEAD) {
            String value = itemSection.getString("itemStack.value");
            itemStack = setCustomHeadValue(itemStack, value);
        }

        List<String> attributes = itemSection.getStringList("attributes");
        if (!attributes.isEmpty()) {
            itemStack = applyAttributes(itemStack, attributes);
        }

        return itemStack;
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
                default -> getLogger().warning(Objects.requireNonNull(Main.getPlugin().configManager.getLang().getString("global.error.invalid_attribute")).replace("{type}", attributeType));
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

        try {
            int customModelData = Integer.parseInt(value);
            assert itemMeta != null;
            itemMeta.setCustomModelData(customModelData);
        } catch (NumberFormatException e) {
            SkullMeta skullMeta = (SkullMeta) itemMeta;
            GameProfile profile = new GameProfile(UUID.randomUUID(), "");
            profile.getProperties().put("textures", new Property("textures", value));
            Field profileField;

            try {
                assert skullMeta != null;
                profileField = skullMeta.getClass().getDeclaredField("profile");
                profileField.setAccessible(true);
                profileField.set(skullMeta, profile);
            } catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException ex) {
                ex.printStackTrace();
            }
        }

        itemStack.setItemMeta(itemMeta);

        return itemStack;
    }





}
