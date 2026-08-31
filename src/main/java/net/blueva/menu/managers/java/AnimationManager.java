package net.blueva.menu.managers.java;

import dev.dejvokep.boostedyaml.block.implementation.Section;
import fr.mrmicky.fastinv.FastInv;
import net.blueva.menu.Main;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

public class AnimationManager {

    static void startAnimation(Main main, Player player, Section animationConfig, int menuSize) {
        int interval = Math.max(1, animationConfig.getInt("interval", 20));

        Section framesSection = animationConfig.getSection("frames");
        if (framesSection == null) {
            return;
        }

        // Build the frame list in declared key order and carry the slot with each frame,
        // instead of assuming the keys are literally "frame1".."frameN".
        List<AnimationFrame> frames = new ArrayList<>();
        for (Object frameKeyObj : framesSection.getKeys()) {
            Section frameSection = framesSection.getSection(frameKeyObj.toString());
            if (frameSection == null) {
                continue;
            }

            int slot = frameSection.getInt("slot", -1);
            if (slot < 0 || slot >= menuSize) {
                main.getLogger().warning("Animation frame '" + frameKeyObj + "' has slot " + slot
                    + " outside the menu (size " + menuSize + ") - skipping.");
                continue;
            }

            ItemStack item;
            try {
                item = ItemManager.createItemStackFromConfig(frameSection, player);
                item = ItemManager.applyAttributes(item, frameSection.getStringList("attributes"));
            } catch (Exception e) {
                main.getLogger().warning("Skipping animation frame '" + frameKeyObj + "': " + e.getMessage());
                continue;
            }

            frames.add(new AnimationFrame(slot, item));
        }

        if (frames.isEmpty()) {
            return;
        }

        BukkitTask task = new BukkitRunnable() {
            int current = 0;
            int previousSlot = -1;

            @Override
            public void run() {
                FastInv menu = main.javaMenuManager.getActiveMenu(player);
                if (!player.isOnline() || !MenuManager.isMenuOpen(player) || menu == null) {
                    cancel();
                    return;
                }

                Inventory inventory = menu.getInventory();

                if (previousSlot >= 0 && previousSlot < inventory.getSize()) {
                    inventory.setItem(previousSlot, new ItemStack(Material.AIR));
                }

                if (current >= frames.size()) {
                    current = 0;
                }

                AnimationFrame frame = frames.get(current);
                if (frame.slot() < inventory.getSize()) {
                    inventory.setItem(frame.slot(), frame.item());
                    previousSlot = frame.slot();
                }

                current++;
            }
        }.runTaskTimer(main, 0L, interval);

        main.javaMenuManager.registerAnimationTask(player, task);
    }

    private record AnimationFrame(int slot, ItemStack item) {
    }
}
