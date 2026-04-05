package com.pixra.pixcore.feature.gameplay;

import com.pixra.pixcore.PixCore;
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
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class BlockDisappearManager implements Listener {

    private final PixCore plugin;
    private boolean isLegacyVersion;

    private final Map<UUID, List<BukkitTask>> playerTasks = new ConcurrentHashMap<>();

    private final Map<UUID, List<Block>> playerBlocks = new ConcurrentHashMap<>();

    private final Set<UUID> suppressedPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> playerReturnGenerations = new ConcurrentHashMap<>();

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

    private void registerTask(UUID uuid, BukkitTask task) {
        playerTasks.computeIfAbsent(uuid, k -> new CopyOnWriteArrayList<>()).add(task);
    }

    private void registerBlock(UUID uuid, Block block) {
        playerBlocks.computeIfAbsent(uuid, k -> new CopyOnWriteArrayList<>()).add(block);
    }

    private void removeTask(UUID uuid, BukkitTask task) {
        List<BukkitTask> tasks = playerTasks.get(uuid);
        if (tasks != null) tasks.remove(task);
    }

    private void removeBlock(UUID uuid, Block block) {
        List<Block> blocks = playerBlocks.get(uuid);
        if (blocks != null) blocks.remove(block);
    }

    public void suppressItemReturn(UUID uuid) {
        playerReturnGenerations.merge(uuid, 1, Integer::sum);
        suppressedPlayers.add(uuid);
    }

    public void unsuppressPlayer(UUID uuid) {
        suppressedPlayers.remove(uuid);
    }

    public void cancelPlayerTasks(UUID uuid) {
        suppressedPlayers.add(uuid);

        List<BukkitTask> tasks = playerTasks.remove(uuid);
        if (tasks != null) {
            for (BukkitTask task : tasks) {
                try { task.cancel(); } catch (Exception ignored) {}
            }
        }

        List<Block> blocks = playerBlocks.remove(uuid);
        if (blocks != null) {
            for (Block block : blocks) {
                if (block != null && block.getType() != Material.AIR) {
                    block.setType(Material.AIR);
                    sendBlockBreakPacket(block.getLocation(), 10);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        String kitName = plugin.getKitName(player);
        UUID uuid = player.getUniqueId();

        if (plugin.getConfig().getBoolean("settings.spawn-protection-break.enabled", false)) {
            List<String> spawnBreakKits = plugin.getConfig().getStringList("settings.spawn-protection-break.kits");
            if (isSpawnProtectionKit(kitName, spawnBreakKits)
                    && intersectsProtectedSpawnColumn(player, block.getLocation())) {
                event.setCancelled(true);
                player.updateInventory();
                plugin.sendCooldownMessage(player, "block-place-denied-spawn");
                return;
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
        int returnGeneration = playerReturnGenerations.getOrDefault(uuid, 0);

        BukkitTask[] taskRef = new BukkitTask[1];
        taskRef[0] = new BukkitRunnable() {
            @Override
            public void run() {
                removeTask(uuid, taskRef[0]);
                if (!player.isOnline() || !plugin.isInFight(player)) return;

                if (block.getType() == Material.AIR) return;

                startAnimationTask(uuid, player, block, animationSpeed, playSound, returnItem, kitName, returnGeneration);
            }
        }.runTaskLater(plugin, delaySeconds * 20L);
        registerTask(uuid, taskRef[0]);
    }

    private boolean isSpawnProtectionKit(String kitName, List<String> spawnBreakKits) {
        if (kitName == null || spawnBreakKits == null) {
            return false;
        }
        String normalizedKit = kitName.toLowerCase();
        for (String allowedKit : spawnBreakKits) {
            if (allowedKit == null) {
                continue;
            }
            String normalizedAllowedKit = allowedKit.toLowerCase();
            if (normalizedAllowedKit.equals(normalizedKit) || normalizedKit.contains(normalizedAllowedKit)) {
                return true;
            }
        }
        return false;
    }

    private boolean intersectsProtectedSpawnColumn(Player sourcePlayer, Location blockLocation) {
        if (sourcePlayer == null || blockLocation == null || blockLocation.getWorld() == null) {
            return false;
        }
        if (!plugin.isHooked() || !plugin.isInFight(sourcePlayer)
                || plugin.getMGetFight() == null || plugin.getStrikePracticeAPI() == null) {
            return false;
        }

        Object sourceFight;
        try {
            sourceFight = plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), sourcePlayer);
        } catch (Exception ignored) {
            return false;
        }
        if (sourceFight == null) {
            return false;
        }

        for (Location protectedSpawn : collectProtectedSpawnLocations(sourceFight)) {
            if (isProtectedSpawnBlock(blockLocation, protectedSpawn)) {
                return true;
            }
        }

        return false;
    }

    private List<Location> collectProtectedSpawnLocations(Object fight) {
        List<Location> protectedSpawns = new ArrayList<>();
        if (fight == null) {
            return protectedSpawns;
        }

        for (Map.Entry<UUID, Location> entry : plugin.getArenaSpawnLocations().entrySet()) {
            Player spawnOwner = Bukkit.getPlayer(entry.getKey());
            if (!isPlayerInFight(spawnOwner, fight)) {
                continue;
            }

            addProtectedSpawnLocation(protectedSpawns, entry.getValue());
        }

        try {
            if (plugin.getMGetArena() != null) {
                Object arena = plugin.getMGetArena().invoke(fight);
                if (arena != null) {
                    if (plugin.getHook().getMArenaGetSpawn1() != null) {
                        Location spawn1 = (Location) plugin.getHook().getMArenaGetSpawn1().invoke(arena);
                        addProtectedSpawnLocation(protectedSpawns, spawn1);
                    }
                    if (plugin.getHook().getMArenaGetSpawn2() != null) {
                        Location spawn2 = (Location) plugin.getHook().getMArenaGetSpawn2().invoke(arena);
                        addProtectedSpawnLocation(protectedSpawns, spawn2);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return protectedSpawns;
    }

    private boolean isPlayerInFight(Player player, Object expectedFight) {
        if (player == null || !player.isOnline() || !plugin.isInFight(player) || expectedFight == null) {
            return false;
        }

        try {
            Object playerFight = plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), player);
            return expectedFight.equals(playerFight);
        } catch (Exception ignored) {
            return false;
        }
    }

    private void addProtectedSpawnLocation(List<Location> protectedSpawns, Location spawnLocation) {
        if (protectedSpawns == null || spawnLocation == null || spawnLocation.getWorld() == null) {
            return;
        }

        protectedSpawns.add(spawnLocation.clone());

        Location safeSpawn = plugin.resolveSafeArenaLocation(spawnLocation.clone());
        if (safeSpawn != null && safeSpawn.getWorld() != null) {
            protectedSpawns.add(safeSpawn);
        }
    }

    private boolean isProtectedSpawnBlock(Location blockLocation, Location spawnLocation) {
        if (blockLocation == null || spawnLocation == null
                || blockLocation.getWorld() == null || spawnLocation.getWorld() == null
                || !blockLocation.getWorld().equals(spawnLocation.getWorld())) {
            return false;
        }

        int dx = blockLocation.getBlockX() - spawnLocation.getBlockX();
        int dy = blockLocation.getBlockY() - spawnLocation.getBlockY();
        int dz = blockLocation.getBlockZ() - spawnLocation.getBlockZ();

        return dx == 0 && dz == 0 && (dy == 0 || dy == 1);
    }

    private void startAnimationTask(UUID uuid, Player player, Block block, int interval, boolean playSound,
                                    boolean returnItem, String kitName, int returnGeneration) {
        registerBlock(uuid, block);

        BukkitTask[] taskRef = new BukkitTask[1];
        taskRef[0] = new BukkitRunnable() {
            int stage = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !plugin.isInFight(player)) {
                    sendBlockBreakPacket(block.getLocation(), 10);
                    removeTask(uuid, taskRef[0]);
                    removeBlock(uuid, block);
                    this.cancel();
                    return;
                }

                if (block.getType() == Material.AIR) {
                    sendBlockBreakPacket(block.getLocation(), 10);
                    removeTask(uuid, taskRef[0]);
                    removeBlock(uuid, block);
                    this.cancel();
                    return;
                }

                if (stage > 9) {

                    boolean isCurrentGeneration = playerReturnGenerations.getOrDefault(uuid, 0) == returnGeneration;
                    if (!suppressedPlayers.contains(uuid) && isCurrentGeneration && returnItem) {
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
                    removeTask(uuid, taskRef[0]);
                    removeBlock(uuid, block);
                    this.cancel();
                    return;
                }

                sendBlockBreakPacket(block.getLocation(), stage);
                stage++;
            }
        }.runTaskTimer(plugin, 0L, interval);
        registerTask(uuid, taskRef[0]);
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
        Sound sound = plugin.getSoundByName("BLOCK_WOOL_BREAK");
        if (sound == null) sound = plugin.getSoundByName("DIG_STONE");

        if (sound != null) {
            try {
                block.getWorld().playSound(block.getLocation(), sound, 1f, 1f);
            } catch (Exception ignored) {}
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
