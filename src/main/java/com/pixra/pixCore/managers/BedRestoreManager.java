package com.pixra.pixCore.managers;

import com.pixra.pixCore.PixCore;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;

public class BedRestoreManager {

    public static class BedPair {
        public final Location footLoc;
        public final Location headLoc;
        public final byte     footData;
        public final byte     headData;

        public BedPair(Location footLoc, Location headLoc, byte footData, byte headData) {
            this.footLoc  = footLoc;
            this.headLoc  = headLoc;
            this.footData = footData;
            this.headData = headData;
        }
    }

    private final PixCore plugin;

    private final Map<String, List<BedPair>> cachedArenaBeds = new HashMap<>();

    private final List<BedPair> persistentBeds = new ArrayList<>();

    private File                                              customBedsFile;
    private org.bukkit.configuration.file.FileConfiguration  customBedsConfig;

    public BedRestoreManager(PixCore plugin) {
        this.plugin = plugin;
        loadCustomBeds();
    }

    private void loadCustomBeds() {
        customBedsFile = new File(plugin.getDataFolder(), "custombeds.yml");
        if (!customBedsFile.exists()) {
            try { customBedsFile.createNewFile(); } catch (Exception ignored) {}
        }
        customBedsConfig = org.bukkit.configuration.file.YamlConfiguration
                .loadConfiguration(customBedsFile);

        persistentBeds.clear();
        if (customBedsConfig.contains("beds")) {
            for (String key : customBedsConfig.getConfigurationSection("beds").getKeys(false)) {
                String   path     = "beds." + key;
                Location footLoc  = (Location) customBedsConfig.get(path + ".footLoc");
                Location headLoc  = (Location) customBedsConfig.get(path + ".headLoc");
                byte     footData = (byte) customBedsConfig.getInt(path + ".footData");
                byte     headData = (byte) customBedsConfig.getInt(path + ".headData");
                if (footLoc != null && headLoc != null) {
                    persistentBeds.add(new BedPair(footLoc, headLoc, footData, headData));
                }
            }
        }
    }

    public void saveCustomBed(Location footLoc, Location headLoc, byte footData, byte headData) {
        BedPair bed  = new BedPair(footLoc, headLoc, footData, headData);
        persistentBeds.add(bed);

        String path  = "beds." + UUID.randomUUID();
        customBedsConfig.set(path + ".footLoc",  bed.footLoc);
        customBedsConfig.set(path + ".headLoc",  bed.headLoc);
        customBedsConfig.set(path + ".footData", bed.footData);
        customBedsConfig.set(path + ".headData", bed.headData);
        try { customBedsConfig.save(customBedsFile); } catch (Exception ignored) {}
    }

    public boolean hasCachedBeds(String arenaName) {
        return cachedArenaBeds.containsKey(arenaName);
    }

    public void saveArenaBeds(String arenaName, Object arena) {
        if (plugin.arenaBoundaryManager == null) return;
        Location min = plugin.arenaBoundaryManager.getCorner1(arena);
        Location max = plugin.arenaBoundaryManager.getCorner2(arena);
        if (min == null || max == null) return;

        int minX = Math.min(min.getBlockX(), max.getBlockX());
        int maxX = Math.max(min.getBlockX(), max.getBlockX());
        int minY = Math.min(min.getBlockY(), max.getBlockY());
        int maxY = Math.max(min.getBlockY(), max.getBlockY());
        int minZ = Math.min(min.getBlockZ(), max.getBlockZ());
        int maxZ = Math.max(min.getBlockZ(), max.getBlockZ());

        Map<String, BedPair> bedMap = new HashMap<>();
        org.bukkit.World world = min.getWorld();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block  b        = world.getBlockAt(x, y, z);
                    String typeName = b.getType().name();

                    if (!typeName.contains("BED_BLOCK") && !typeName.endsWith("_BED")) continue;

                    byte data   = getBlockDataSafe(b);
                    int  dir    = data & 3;
                    boolean isHead = (data & 8) == 8;

                    int dx = 0, dz = 0;
                    if      (dir == 0) dz =  1;
                    else if (dir == 1) dx = -1;
                    else if (dir == 2) dz = -1;
                    else if (dir == 3) dx =  1;

                    Location footLoc, headLoc;
                    if (isHead) {
                        headLoc = b.getLocation();
                        footLoc = headLoc.clone().add(-dx, 0, -dz);
                    } else {
                        footLoc = b.getLocation();
                        headLoc = footLoc.clone().add(dx, 0, dz);
                    }

                    String key = footLoc.toString();
                    if (!bedMap.containsKey(key)) {
                        bedMap.put(key, new BedPair(footLoc, headLoc, (byte) dir, (byte) (dir | 8)));
                    }
                }
            }
        }

        if (!bedMap.isEmpty()) {
            cachedArenaBeds.put(arenaName, new ArrayList<>(bedMap.values()));
        }
    }

    public void forceFixBeds(String arenaName, Object fight, long delayTicks) {
        new BukkitRunnable() {
            @Override public void run() { executeBedRestore(arenaName, fight); }
        }.runTaskLater(plugin, delayTicks);

        new BukkitRunnable() {
            @Override public void run() { executeBedRestore(arenaName, fight); }
        }.runTaskLater(plugin, delayTicks + 40L);
    }

    private void executeBedRestore(String arenaName, Object fight) {
        List<BedPair> bedsToRestore  = new ArrayList<>();
        boolean       usingPersistent = false;

        try {
            Object arena = (plugin.getMGetArena() != null && fight != null)
                    ? plugin.getMGetArena().invoke(fight) : null;
            if (arena != null && plugin.arenaBoundaryManager != null) {
                Location min = plugin.arenaBoundaryManager.getCorner1(arena);
                Location max = plugin.arenaBoundaryManager.getCorner2(arena);
                if (min != null && max != null) {
                    int minX = Math.min(min.getBlockX(), max.getBlockX());
                    int maxX = Math.max(min.getBlockX(), max.getBlockX());
                    int minZ = Math.min(min.getBlockZ(), max.getBlockZ());
                    int maxZ = Math.max(min.getBlockZ(), max.getBlockZ());
                    for (BedPair b : persistentBeds) {
                        Location loc = b.footLoc;
                        if (loc.getWorld().getName().equals(min.getWorld().getName())
                                && loc.getBlockX() >= minX && loc.getBlockX() <= maxX
                                && loc.getBlockZ() >= minZ && loc.getBlockZ() <= maxZ) {
                            bedsToRestore.add(b);
                            usingPersistent = true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        if (!usingPersistent && cachedArenaBeds.containsKey(arenaName)) {
            bedsToRestore.addAll(cachedArenaBeds.get(arenaName));
        }

        for (BedPair bed : bedsToRestore) {
            placeBedRobust(bed.footLoc, bed.headLoc, bed.footData, bed.headData);
        }

        if (fight != null && plugin.getClsAbstractFight() != null
                && plugin.getClsAbstractFight().isInstance(fight)) {
            try {
                if (plugin.getFBed1Broken() != null) plugin.getFBed1Broken().set(fight, false);
                if (plugin.getFBed2Broken() != null) plugin.getFBed2Broken().set(fight, false);
            } catch (Exception ignored) {}
        }
    }

    public byte getBlockDataSafe(Block block) {
        try {
            return (byte) block.getClass().getMethod("getData").invoke(block);
        } catch (Exception e) {
            return 0;
        }
    }

    private void placeBedRobust(Location footLoc, Location headLoc,
                                byte footData, byte headData) {
        try {
            if (!footLoc.getChunk().isLoaded()) footLoc.getChunk().load();
            if (!headLoc.getChunk().isLoaded()) headLoc.getChunk().load();

            Block foot = footLoc.getBlock();
            Block head = headLoc.getBlock();

            org.bukkit.Material bedMat;
            try {
                bedMat = org.bukkit.Material.valueOf("BED_BLOCK");
            } catch (Exception e) {
                return;
            }

            try {
                Method setTID = Block.class.getMethod("setTypeIdAndData", int.class, byte.class, boolean.class);
                Method getId  = org.bukkit.Material.class.getMethod("getId");
                int    bedId  = (Integer) getId.invoke(bedMat);

                setTID.invoke(foot, 0, (byte) 0, false);
                setTID.invoke(head, 0, (byte) 0, false);
                setTID.invoke(foot, bedId, footData, false);
                setTID.invoke(head, bedId, headData, false);
                return;
            } catch (Exception ignored) {}

            foot.setType(org.bukkit.Material.AIR);
            head.setType(org.bukkit.Material.AIR);

            org.bukkit.block.BlockState footState = foot.getState();
            org.bukkit.block.BlockState headState = head.getState();
            footState.setType(bedMat);
            headState.setType(bedMat);

            try {
                Method setRaw = org.bukkit.block.BlockState.class.getMethod("setRawData", byte.class);
                setRaw.invoke(footState, footData);
                setRaw.invoke(headState, headData);
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            footState.update(true, false);
            headState.update(true, false);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}