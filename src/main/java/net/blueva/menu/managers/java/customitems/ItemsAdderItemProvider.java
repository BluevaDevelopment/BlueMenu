package net.blueva.menu.managers.java.customitems;

import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.inventory.ItemStack;

public class ItemsAdderItemProvider implements CustomItemProvider {
    @Override
    public ItemStack createItem(String itemId) {
        CustomStack stack = CustomStack.getInstance(itemId);
        return stack == null ? null : stack.getItemStack().clone();
    }
}
