package net.blueva.menu.managers.java;

import dev.dejvokep.boostedyaml.block.implementation.Section;
import fr.mrmicky.fastinv.FastInv;
import net.blueva.menu.Main;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public class AnimationManager {
    static void startAnimation(Main main, Player player, Section animationConfig, int menuSize) {
        int interval = animationConfig.getInt("interval");
        List<ItemStack> frames = new ArrayList<>();

        Section framesSection = animationConfig.getSection("frames");
        if (framesSection != null) {
            for (Object frameObj : framesSection.getKeys()) {
                String frame = frameObj.toString();
                Section frameSection = framesSection.getSection(frame);
                if (frameSection != null) {
                    ItemStack frameItem = ItemManager.createItemStackFromConfig(frameSection, player);
                    frames.add(frameItem);
                }
            }
        }

        if (!frames.isEmpty()) {
            new BukkitRunnable() {
                int currentFrame = 0;

                @Override
                public void run() {
                    if (!player.isOnline() || !MenuManager.isMenuOpen(player)) {
                        // Player logged out, cancel the task
                        cancel();
                        return;
                    }

                    // Get the active FastInv menu
                    FastInv menu = main.javaMenuManager.getActiveMenu(player);
                    if (menu == null) {
                        cancel();
                        return;
                    }

                    Inventory inventory = menu.getInventory();

                    // Clear previous frames
                    for (int i = 0; i < frames.size(); i++) {
                        Section frameSection = framesSection.getSection("frame" + (i + 1));
                        if (frameSection != null) {
                            int slot = frameSection.getInt("slot");
                            if (slot >= 0 && slot < menuSize) {
                                ItemStack air = new ItemStack(Material.AIR);
                                inventory.setItem(slot, air);
                            }
                        }
                    }

                    // Update inventory with the current frame
                    if (currentFrame >= frames.size()) {
                        // Reached the end of frames, start over from the beginning
                        currentFrame = 0;
                    }

                    Section currentFrameSection = framesSection.getSection("frame" + (currentFrame + 1));
                    if (currentFrameSection != null) {
                        int slot = currentFrameSection.getInt("slot");
                        if (slot >= 0 && slot < menuSize) {
                            ItemStack frameItem = frames.get(currentFrame);
                            ItemStack frameItemWithAttributes = ItemManager.applyAttributes(frameItem, currentFrameSection.getStringList("attributes"));
                            inventory.setItem(slot, frameItemWithAttributes);
                        }
                    }

                    currentFrame++;
                }
            }.runTaskTimer(main, 0, interval).getTaskId();
        }
    }
}
