package net.blueva.menu.managers.java.customitems;

import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.items.ItemBuilder;
import org.bukkit.inventory.ItemStack;

public class OraxenItemProvider implements CustomItemProvider {
    @Override
    public ItemStack createItem(String itemId) {
        ItemBuilder itemBuilder = OraxenItems.getItemById(itemId);
        return itemBuilder == null ? null : itemBuilder.build().clone();
    }
}
