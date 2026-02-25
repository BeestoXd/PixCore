package com.pixra.pixCore.managers;

import com.pixra.pixCore.PixCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Constructor;
import java.util.List;

public class BlockDisappearManager implements Listener {

    private final PixCore plugin;
    private boolean isLegacyVersion;

    public BlockDisappearManager(PixCore plugin) {
        this.plugin = plugin;
        try {
            String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            int minor = Integer.parseInt(version.split("_")[1]);
            this.isLegacyVersion = minor < 13;
        } catch (Exception e) {
            this.isLegacyVersion = false;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        String kitName = plugin.getKitName(player);

        if (plugin.getConfig().getBoolean("settings.spawn-protection-break.enabled", false)) {
            List<String> spawnBreakKits = plugin.getConfig().getStringList("settings.spawn-protection-break.kits");
            boolean isAllowedSpawnKit = false;

            if (spawnBreakKits != null && kitName != null) {
                for (String k : spawnBreakKits) {
                    if (k.equalsIgnoreCase(kitName)) {
                        isAllowedSpawnKit = true;
                        break;
                    }
                }
            }

            if (isAllowedSpawnKit) {
                Location spawnLoc = plugin.getArenaSpawnLocations().get(player.getUniqueId());

                if (spawnLoc != null) {
                    if (block.getLocation().getBlockX() == spawnLoc.getBlockX() &&
                            block.getLocation().getBlockY() == spawnLoc.getBlockY() &&
                            block.getLocation().getBlockZ() == spawnLoc.getBlockZ()) {

                        int delay = plugin.getConfig().getInt("settings.spawn-protection-break.delay-before-break", 0);
                        int speed = plugin.getConfig().getInt("settings.spawn-protection-break.animation-tick-interval", 2);
                        boolean sound = plugin.getConfig().getBoolean("settings.spawn-protection-break.play-sound", true);

                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (player == null || !player.isOnline() || !plugin.isInFight(player)) return;

                                startAnimationTask(player, block, speed, sound, false, kitName);
                            }
                        }.runTaskLater(plugin, delay * 20L);

                        return;
                    }
                }
            }
        }

        if (!plugin.getConfig().getBoolean("settings.block-disappear.enabled", false)) return;

        if (kitName == null) return;

        List<String> allowedKits = plugin.getConfig().getStringList("settings.block-disappear.kits");
        boolean isAllowedKit = false;

        if (allowedKits != null) {
            for (String k : allowedKits) {
                if (k.equalsIgnoreCase(kitName)) {
                    isAllowedKit = true;
                    break;
                }
            }
        }

        if (!isAllowedKit) return;

        int delaySeconds = plugin.getConfig().getInt("settings.block-disappear.delay-before-break", 3);
        int animationSpeed = plugin.getConfig().getInt("settings.block-disappear.animation-tick-interval", 4);
        boolean playSound = plugin.getConfig().getBoolean("settings.block-disappear.play-sound", true);

        boolean returnItem = plugin.getConfig().getBoolean("settings.block-disappear.return-item", true);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (player == null || !player.isOnline() || !plugin.isInFight(player)) return;

                if (block.getType() == Material.AIR) {
                    return;
                }
                startAnimationTask(player, block, animationSpeed, playSound, returnItem, kitName);
            }
        }.runTaskLater(plugin, delaySeconds * 20L);
    }

    private void startAnimationTask(Player player, Block block, int interval, boolean playSound, boolean returnItem, String kitName) {
        new BukkitRunnable() {
            int stage = 0;

            @Override
            public void run() {
                if (player == null || !player.isOnline() || !plugin.isInFight(player)) {
                    sendBlockBreakPacket(block.getLocation(), 10);
                    this.cancel();
                    return;
                }

                if (block.getType() == Material.AIR) {
                    sendBlockBreakPacket(block.getLocation(), 10);
                    this.cancel();
                    return;
                }

                if (stage > 9) {
                    if (returnItem) {
                        if (kitName != null && kitName.equalsIgnoreCase("tntsumo")) {
                            giveTntSumoItems(player);
                        } else {
                            Material mat = block.getType();
                            ItemStack item = new ItemStack(mat, 1);

                            if (isLegacyVersion) {
                                try {
                                    if (block.getData() != 0) {
                                        item.setDurability(block.getData());
                                    }
                                } catch (Throwable ignored) {}
                            }
                            player.getInventory().addItem(item);
                        }
                    }

                    block.setType(Material.AIR);

                    if (playSound) {
                        playBreakSound(block);
                    }
                    sendBlockBreakPacket(block.getLocation(), 10);
                    this.cancel();
                    return;
                }

                sendBlockBreakPacket(block.getLocation(), stage);
                stage++;
            }
        }.runTaskTimer(plugin, 0L, interval);
    }

    private void giveTntSumoItems(Player player) {
        ItemStack item;
        if (isLegacyVersion) {
            try {
                item = new ItemStack(Material.valueOf("STAINED_CLAY"), 64, (short) 11);
            } catch (Exception e) {
                item = new ItemStack(Material.STONE, 64);
            }
        } else {
            Material mat = Material.getMaterial("BLUE_TERRACOTTA");
            if (mat == null) mat = Material.getMaterial("STAINED_CLAY");
            if (mat == null) mat = Material.STONE;
            item = new ItemStack(mat, 64);
        }

        player.getInventory().addItem(item);
    }

    private void playBreakSound(Block block) {
        Sound sound = getSafeSound("BLOCK_WOOL_BREAK");
        if (sound == null) sound = getSafeSound("DIG_WOOL");
        if (sound == null) sound = getSafeSound("DIG_STONE");

        if (sound != null) {
            try {
                block.getWorld().playSound(block.getLocation(), sound, 1f, 1f);
            } catch (Exception ignored) {}
        }
    }

    private Sound getSafeSound(String name) {
        try {
            java.lang.reflect.Method method = Sound.class.getMethod("valueOf", String.class);
            return (Sound) method.invoke(null, name);
        } catch (Throwable t) {
            return null;
        }
    }

    private void sendBlockBreakPacket(Location loc, int stage) {
        try {
            String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            String nmsPackage = "net.minecraft.server." + version;

            int breakId = (loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ()).hashCode();

            Class<?> blockPositionClass = Class.forName(nmsPackage + ".BlockPosition");
            Class<?> packetClass = Class.forName(nmsPackage + ".PacketPlayOutBlockBreakAnimation");

            Constructor<?> blockPosConstructor = blockPositionClass.getConstructor(int.class, int.class, int.class);
            Object blockPosition = blockPosConstructor.newInstance(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

            Constructor<?> packetConstructor = packetClass.getConstructor(int.class, blockPositionClass, int.class);
            Object packet = packetConstructor.newInstance(breakId, blockPosition, stage);

            for (Player p : loc.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(loc) < 1024) {
                    sendPacket(p, packet);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendPacket(Player player, Object packet) {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object playerConnection = handle.getClass().getField("playerConnection").get(handle);

            String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            Class<?> packetInterface = Class.forName("net.minecraft.server." + version + ".Packet");

            playerConnection.getClass().getMethod("sendPacket", packetInterface).invoke(playerConnection, packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}