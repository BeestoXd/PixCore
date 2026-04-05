package com.pixra.pixcore.support;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public final class SpawnSafetyUtil {

    private static final int MAX_HORIZONTAL_RADIUS = 3;
    private static final int[] VERTICAL_OFFSETS = new int[] { 0, 1, -1, 2, -2, 3, 4 };
    private static final int MAX_DOWNWARD_SCAN = 96;

    private SpawnSafetyUtil() {
    }

    public static Location findNearestSafeSpawn(Location source) {
        if (source == null || source.getWorld() == null) {
            return source;
        }

        if (!source.getChunk().isLoaded()) {
            source.getChunk().load();
        }

        Location base = centered(source);
        if (isSafeStandLocation(base)) {
            return base;
        }

        for (int radius = 1; radius <= MAX_HORIZONTAL_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }

                    for (int dy : VERTICAL_OFFSETS) {
                        Location candidate = offset(base, dx, dy, dz);
                        if (isSafeStandLocation(candidate)) {
                            return candidate;
                        }
                    }
                }
            }
        }

        for (int dy = 5; dy <= 8; dy++) {
            Location candidate = offset(base, 0, dy, 0);
            if (isSafeStandLocation(candidate)) {
                return candidate;
            }
        }

        int minY = getMinY(source);
        int maxDownwardOffset = Math.min(MAX_DOWNWARD_SCAN, Math.max(0, base.getBlockY() - minY));
        for (int dy = 1; dy <= maxDownwardOffset; dy++) {
            for (int radius = 0; radius <= MAX_HORIZONTAL_RADIUS; radius++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (radius > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                            continue;
                        }

                        Location candidate = offset(base, dx, -dy, dz);
                        if (candidate.getBlockY() < minY) {
                            continue;
                        }
                        if (isSafeStandLocation(candidate)) {
                            return candidate;
                        }
                    }
                }
            }
        }

        return base;
    }

    private static Location centered(Location source) {
        return new Location(
                source.getWorld(),
                source.getBlockX() + 0.5D,
                source.getBlockY(),
                source.getBlockZ() + 0.5D,
                source.getYaw(),
                source.getPitch());
    }

    private static Location offset(Location base, int dx, int dy, int dz) {
        return new Location(
                base.getWorld(),
                base.getBlockX() + dx + 0.5D,
                base.getBlockY() + dy,
                base.getBlockZ() + dz + 0.5D,
                base.getYaw(),
                base.getPitch());
    }

    private static boolean isSafeStandLocation(Location location) {
        Block feet = location.getBlock();
        Block head = feet.getRelative(BlockFace.UP);
        Block below = feet.getRelative(BlockFace.DOWN);

        return isOpenSpace(feet) && isOpenSpace(head) && isSafeSupport(below);
    }

    private static int getMinY(Location source) {
        try {
            return source.getWorld().getMinHeight();
        } catch (NoSuchMethodError ignored) {
            return 0;
        }
    }

    private static boolean isOpenSpace(Block block) {
        Material type = block.getType();
        String name = type.name();

        if (type == Material.AIR) {
            return true;
        }
        if (block.isLiquid() || isHazardous(name) || isBlockingObstacle(name)) {
            return false;
        }
        return !type.isSolid();
    }

    private static boolean isSafeSupport(Block block) {
        Material type = block.getType();
        String name = type.name();

        if (type == Material.AIR || block.isLiquid() || isHazardous(name)) {
            return false;
        }
        if (type.isSolid()) {
            return true;
        }
        return name.contains("STEP")
                || name.contains("SLAB")
                || name.endsWith("STAIRS")
                || name.contains("FENCE")
                || name.endsWith("WALL");
    }

    private static boolean isHazardous(String name) {
        return name.contains("LAVA")
                || name.contains("FIRE")
                || name.contains("CACTUS")
                || name.contains("MAGMA")
                || name.contains("CAMPFIRE")
                || name.contains("SOUL_FIRE")
                || name.contains("WEB")
                || name.contains("PORTAL")
                || name.contains("BERRY_BUSH")
                || name.contains("WITHER_ROSE");
    }

    private static boolean isBlockingObstacle(String name) {
        return name.contains("DOOR")
                || name.contains("TRAP_DOOR")
                || name.contains("TRAPDOOR")
                || name.contains("FENCE_GATE")
                || name.contains("PANE")
                || name.contains("BARS")
                || name.contains("CHEST")
                || name.contains("ANVIL")
                || name.endsWith("WALL")
                || name.contains("FENCE")
                || name.contains("BED");
    }
}
