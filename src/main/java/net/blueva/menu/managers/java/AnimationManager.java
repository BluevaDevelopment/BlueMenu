package net.blueva.menu.managers.java;

import net.blueva.menu.Main;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnimationManager {
    public static Map<Player, Integer> animationTasks = new HashMap<>();

    static void startAnimation(Main main, Player player, ConfigurationSection animationConfig, int menuSize) {
        int interval = animationConfig.getInt("interval");
        List<ItemStack> frames = new ArrayList<>();

        ConfigurationSection framesSection = animationConfig.getConfigurationSection("frames");
        if (framesSection != null) {
            for (String frame : framesSection.getKeys(false)) {
                ConfigurationSection frameSection = framesSection.getConfigurationSection(frame);
                if (frameSection != null) {
                    ItemStack frameItem = ItemManager.createItemStackFromConfig(frameSection, player);
                    frames.add(frameItem);
                }
            }
        }

        if (!frames.isEmpty()) {
            int taskId = new BukkitRunnable() {
                int currentFrame = 0;

                @Override
                public void run() {
                    if (!player.isOnline() || !MenuManager.isMenuOpen(player)) {
                        // Player logged out, cancel the task
                        cancelAnimation(player);
                        return;
                    }

                    // Clear previous frames
                    Inventory inventory = player.getOpenInventory().getTopInventory();
                    for (int i = 0; i < frames.size(); i++) {
                        ConfigurationSection frameSection = framesSection.getConfigurationSection("frame" + (i + 1));
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

                    ConfigurationSection currentFrameSection = framesSection.getConfigurationSection("frame" + (currentFrame + 1));
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

            animationTasks.put(player, taskId);
        }
    }

    private static void cancelAnimation(Player player) {
        if (animationTasks.containsKey(player)) {
            int taskId = animationTasks.get(player);
            Bukkit.getScheduler().cancelTask(taskId);
            animationTasks.remove(player);
        }
    }
}
