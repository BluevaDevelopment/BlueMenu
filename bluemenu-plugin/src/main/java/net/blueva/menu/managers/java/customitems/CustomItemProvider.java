package net.blueva.menu.managers.java.customitems;

import org.bukkit.inventory.ItemStack;

public interface CustomItemProvider {
    ItemStack createItem(String itemId);
}
