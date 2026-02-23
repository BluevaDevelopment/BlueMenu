package net.blueva.menu.managers.java.customitems;

import com.nexomc.nexo.api.NexoItems;
import com.nexomc.nexo.items.ItemBuilder;
import org.bukkit.inventory.ItemStack;

public class NexoItemProvider implements CustomItemProvider {
    @Override
    public ItemStack createItem(String itemId) {
        ItemBuilder itemBuilder = NexoItems.itemFromId(itemId);
        return itemBuilder == null ? null : itemBuilder.build().clone();
    }
}
