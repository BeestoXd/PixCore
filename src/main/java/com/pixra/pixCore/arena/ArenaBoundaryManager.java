package com.pixra.pixCore.arena;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

public class ArenaBoundaryManager {

    private final Object strikePracticeAPI;
    private final Method mGetFight;
    private final Method mGetArena;
    private final Method mArenaGetMin;
    private final Method mArenaGetMax;

    public ArenaBoundaryManager(Object strikePracticeAPI, Method mGetFight, Method mGetArena, Method mArenaGetMin, Method mArenaGetMax) {
        this.strikePracticeAPI = strikePracticeAPI;
        this.mGetFight = mGetFight;
        this.mGetArena = mGetArena;
        this.mArenaGetMin = mArenaGetMin;
        this.mArenaGetMax = mArenaGetMax;
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

    public boolean checkArenaBorder(Player player, Block block) {
        try {
            Object fight = mGetFight.invoke(strikePracticeAPI, player);
            if (fight == null) return false;

            Object arena = mGetArena.invoke(fight);
            if (arena == null) return false;

            Location corner1 = getCorner1(arena);
            Location corner2 = getCorner2(arena);

            if (corner1 == null || corner2 == null) return false;

            int bx = block.getX();
            int bz = block.getZ();

            int minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
            int maxX = Math.max(corner1.getBlockX(), corner2.getBlockX());
            int minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
            int maxZ = Math.max(corner1.getBlockZ(), corner2.getBlockZ());

            return bx < minX || bx > maxX || bz < minZ || bz > maxZ;

        } catch (Exception e) {
            return false;
        }
    }
}