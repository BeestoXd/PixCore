package com.pixra.pixcore.feature.arena;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.List;

public class ArenaBoundaryManager {

    private final Object strikePracticeAPI;
    private final Method mGetFight;
    private final Method mGetArena;
    private final Method mApiGetArenaByName;
    private final Method mApiGetArenas;
    private final Method mArenaGetCenter;
    private final Method mArenaGetMin;
    private final Method mArenaGetMax;
    private final Method mArenaGetOriginalName;

    private static final class ArenaBounds {
        private final Location corner1;
        private final Location corner2;

        private ArenaBounds(Location corner1, Location corner2) {
            this.corner1 = corner1;
            this.corner2 = corner2;
        }

        private int minX() {
            return Math.min(corner1.getBlockX(), corner2.getBlockX());
        }

        private int maxX() {
            return Math.max(corner1.getBlockX(), corner2.getBlockX());
        }

        private int minZ() {
            return Math.min(corner1.getBlockZ(), corner2.getBlockZ());
        }

        private int maxZ() {
            return Math.max(corner1.getBlockZ(), corner2.getBlockZ());
        }

        private int maxBlockY() {
            return Math.max(corner1.getBlockY(), corner2.getBlockY());
        }

        private boolean containsXZ(Location location) {
            if (location == null) return false;
            int x = location.getBlockX();
            int z = location.getBlockZ();
            return x >= minX() && x <= maxX() && z >= minZ() && z <= maxZ();
        }
    }

    public ArenaBoundaryManager(Object strikePracticeAPI, Method mGetFight, Method mGetArena,
                                Method mApiGetArenaByName, Method mApiGetArenas,
                                Method mArenaGetCenter,
                                Method mArenaGetMin, Method mArenaGetMax,
                                Method mArenaGetOriginalName) {
        this.strikePracticeAPI = strikePracticeAPI;
        this.mGetFight = mGetFight;
        this.mGetArena = mGetArena;
        this.mApiGetArenaByName = mApiGetArenaByName;
        this.mApiGetArenas = mApiGetArenas;
        this.mArenaGetCenter = mArenaGetCenter;
        this.mArenaGetMin = mArenaGetMin;
        this.mArenaGetMax = mArenaGetMax;
        this.mArenaGetOriginalName = mArenaGetOriginalName;
    }

    public Location getCorner1(Object arena) {
        try {
            return (Location) mArenaGetMin.invoke(arena);
        } catch (Exception e) {
            return null;
        }
    }

    public Location getCorner2(Object arena) {
        try {
            return (Location) mArenaGetMax.invoke(arena);
        } catch (Exception e) {
            return null;
        }
    }

    public Location getCenter(Object arena) {
        if (arena == null || mArenaGetCenter == null) return null;
        try {
            return (Location) mArenaGetCenter.invoke(arena);
        } catch (Exception e) {
            return null;
        }
    }

    private Object getArenaForPlayer(Player player) {
        try {
            Object fight = mGetFight.invoke(strikePracticeAPI, player);
            if (fight == null) return null;
            return mGetArena.invoke(fight);
        } catch (Exception e) {
            return null;
        }
    }

    private String getOriginalName(Object arena) {
        if (arena == null || mArenaGetOriginalName == null) return null;
        try {
            Object value = mArenaGetOriginalName.invoke(arena);
            if (!(value instanceof String)) return null;
            String originalName = ((String) value).trim();
            return originalName.isEmpty() ? null : originalName;
        } catch (Exception e) {
            return null;
        }
    }

    private Object getArenaByName(String arenaName) {
        if (arenaName == null || arenaName.isEmpty() || strikePracticeAPI == null || mApiGetArenaByName == null) return null;
        try {
            return mApiGetArenaByName.invoke(strikePracticeAPI, arenaName);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object> getArenas() {
        if (strikePracticeAPI == null || mApiGetArenas == null) return null;
        try {
            Object value = mApiGetArenas.invoke(strikePracticeAPI);
            if (value instanceof List) return (List<Object>) value;
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private ArenaBounds getBounds(Object arena) {
        if (arena == null) return null;

        Location corner1 = getCorner1(arena);
        Location corner2 = getCorner2(arena);
        if (corner1 == null || corner2 == null) return null;

        return new ArenaBounds(corner1, corner2);
    }

    private ArenaBounds findBoundsByOriginalName(String originalName) {
        if (originalName == null) return null;

        List<Object> arenas = getArenas();
        if (arenas == null) return null;

        for (Object arena : arenas) {
            String arenaOriginalName = getOriginalName(arena);
            if (arenaOriginalName == null || !arenaOriginalName.equalsIgnoreCase(originalName)) continue;

            ArenaBounds bounds = getBounds(arena);
            if (bounds != null) return bounds;
        }
        return null;
    }

    private ArenaBounds findBoundsByLocation(Location location) {
        if (location == null) return null;

        List<Object> arenas = getArenas();
        if (arenas == null) return null;

        ArenaBounds sameWorldMatch = null;
        for (Object arena : arenas) {
            ArenaBounds bounds = getBounds(arena);
            if (bounds == null || !bounds.containsXZ(location)) continue;

            if (bounds.corner1.getWorld() != null && location.getWorld() != null
                    && bounds.corner1.getWorld().getName().equalsIgnoreCase(location.getWorld().getName())) {
                return bounds;
            }
            if (sameWorldMatch == null) sameWorldMatch = bounds;
        }
        return sameWorldMatch;
    }

    private ArenaBounds resolveBounds(Player player) {
        Object currentArena = getArenaForPlayer(player);

        ArenaBounds bounds = getBounds(currentArena);
        if (bounds != null) return bounds;

        String originalName = getOriginalName(currentArena);

        bounds = getBounds(getArenaByName(originalName));
        if (bounds != null) return bounds;

        bounds = findBoundsByOriginalName(originalName);
        if (bounds != null) return bounds;

        return findBoundsByLocation(player != null ? player.getLocation() : null);
    }

    public boolean checkBuildHeight(Player player, Block block) {
        try {
            ArenaBounds bounds = resolveBounds(player);
            if (bounds == null) return false;

            return block.getY() > bounds.maxBlockY();

        } catch (Exception e) {
            return false;
        }
    }

    public boolean checkArenaBorder(Player player, Block block) {
        try {
            ArenaBounds bounds = resolveBounds(player);
            if (bounds == null) return false;

            int bx = block.getX();
            int bz = block.getZ();

            return bx < bounds.minX() || bx > bounds.maxX() || bz < bounds.minZ() || bz > bounds.maxZ();

        } catch (Exception e) {
            return false;
        }
    }

    public boolean isPastStrikePracticeCenterLimit(Player player, Block block, int buildLimit) {
        try {
            Object arena = getArenaForPlayer(player);
            if (arena == null) return false;

            Location center = getCenter(arena);
            if (center == null) return false;

            return block.getY() - center.getBlockY() > buildLimit;
        } catch (Exception e) {
            return false;
        }
    }
}
