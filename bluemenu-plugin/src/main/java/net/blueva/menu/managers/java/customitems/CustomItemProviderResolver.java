package net.blueva.menu.managers.java.customitems;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

public final class CustomItemProviderResolver {
    private CustomItemProviderResolver() {
    }

    public static ItemStack createItem(String provider, String itemId) {
        if (provider == null || itemId == null || itemId.isBlank()) {
            return null;
        }

        return switch (provider.toLowerCase(Locale.ROOT)) {
            case "ia", "itemsadder", "items_adder" -> createItemsAdderItem(itemId);
            case "oraxen" -> createOraxenItem(itemId);
            case "nexo" -> createNexoItem(itemId);
            default -> null;
        };
    }

    private static ItemStack createItemsAdderItem(String itemId) {
        if (!Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            return null;
        }

        return new ItemsAdderItemProvider().createItem(itemId);
    }

    private static ItemStack createOraxenItem(String itemId) {
        if (!Bukkit.getPluginManager().isPluginEnabled("Oraxen")) {
            return null;
        }

        return new OraxenItemProvider().createItem(itemId);
    }

    private static ItemStack createNexoItem(String itemId) {
        if (!Bukkit.getPluginManager().isPluginEnabled("Nexo")) {
            return null;
        }

        return new NexoItemProvider().createItem(itemId);
    }
}
