package com.pixra.pixcore.feature.match;

import com.pixra.pixcore.PixCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RespawnManager {

    private static final long POST_RESPAWN_PROTECTION_MS = 2500L;
    private static final double POST_RESPAWN_MAX_HORIZONTAL_DRIFT_SQUARED = 36.0D;
    private static final double POST_RESPAWN_MAX_DOWNWARD_DRIFT = 4.0D;
    private static final double POST_RESPAWN_RELEASE_DISTANCE_SQUARED = 0.25D;

    private final PixCore plugin;
    private final Map<UUID, Location> respawnLocations = new HashMap<>();
    private final Map<UUID, BukkitTask> pendingTasks = new HashMap<>();
    private final Map<UUID, Long> postRespawnProtection = new HashMap<>();
    private final Map<UUID, Location> protectedRespawnLocations = new HashMap<>();

    public RespawnManager(PixCore plugin) {
        this.plugin = plugin;
    }

    public void startRespawn(Player player, Location arenaSpawn) {
        UUID uuid = player.getUniqueId();
        if (respawnLocations.containsKey(uuid))
            return;
        Location safeArenaSpawn = resolveRespawnLocation(player, arenaSpawn);
        Location deathLocation = player.getLocation() != null ? player.getLocation().clone() : null;
        respawnLocations.put(uuid, safeArenaSpawn.clone());

        if (pendingTasks.containsKey(uuid)) {
            pendingTasks.get(uuid).cancel();
            pendingTasks.remove(uuid);
        }

        plugin.suppressBlockDisappearReturn(uuid);

        clearInventory(player);

        plugin.addSpectator(player);
        holdPlayerAtDeathLocation(player, deathLocation);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!respawnLocations.containsKey(uuid))
                    return;

                clearInventory(player);
                pendingTasks.remove(uuid);
            }
        }.runTaskLater(plugin, 5L);
        pendingTasks.put(uuid, task);
    }

    private void clearInventory(Player player) {
        if (player != null && player.isOnline()) {
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            try {
                player.getInventory().setItemInOffHand(null);
            } catch (NoSuchMethodError ignored) {
            }
            player.updateInventory();
        }
    }

    public void finishRespawn(Player player) {
        if (player == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        if (shouldAbortRespawn(player)) {
            abortRespawn(player, uuid);
            return;
        }

        Location spawn = respawnLocations.get(uuid);
        if (spawn == null) {
            abortRespawn(player, uuid);
            return;
        }

        final Location finalSpawn = resolveRespawnLocation(player, spawn);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (shouldAbortRespawn(player)) {
                abortRespawn(player, uuid);
                return;
            }

            plugin.suppressBlockDisappearReturn(uuid);

            // Bedfight: if this player's bed was broken during respawn countdown, end match properly
            if (plugin.bedFightBedBrokenPlayers.remove(uuid)) {
                plugin.cancelBlockDisappear(uuid);
                // Ambil fight object sebelum removeSpectator agar referensi masih valid
                Object bedFight = null;
                try {
                    if (plugin.getMGetFight() != null && plugin.getStrikePracticeAPI() != null) {
                        bedFight = plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), player);
                    }
                } catch (Exception ignored) {}
                // Keluarkan dari spectator tanpa teleport ke hub (false = jangan teleport)
                plugin.removeSpectator(player, false);
                for (Player online : Bukkit.getOnlinePlayers()) {
                    online.showPlayer(player);
                }
                cleanup(uuid);
                plugin.unsuppressBlockDisappear(uuid);
                // Panggil handleDeath agar StrikePractice yang mengakhiri match
                final Object finalBedFight = bedFight;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        if (finalBedFight != null && plugin.getMHandleDeath() != null) {
                            plugin.getMHandleDeath().invoke(finalBedFight, player);
                        }
                    } catch (Exception ignored) {}
                });
                return;
            }

            plugin.removeSpectator(player, false);

            if (plugin.partyFFAManager != null)
                plugin.partyFFAManager.startRespawnRedirect(player);
            plugin.respawnInFight(player);
            protectPlayerAfterRespawn(player, finalSpawn);
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.showPlayer(player);
            }
            if (player.isOnline()) plugin.syncLayoutInstant(player, 6);

            String respawnKitName = plugin.isHooked() ? plugin.getKitName(player) : null;
            if (respawnKitName != null && plugin.voidManager != null
                    && plugin.voidManager.isRespawnKit(respawnKitName)) {
                plugin.forceRestoreKitBlocks(player, null);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) plugin.forceRestoreKitBlocks(player, null);
                }, 3L);
            }

            if (plugin.partyFFAManager != null) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) plugin.partyFFAManager.restoreArmorColor(player);
                }, 3L);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) plugin.partyFFAManager.restoreArmorColor(player);
                }, 7L);
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.unsuppressBlockDisappear(uuid), 1L);

            cleanup(uuid);
        });
    }

    public Location getSafeRespawnLocation(Player player, Location preferredLocation) {
        Location safeLocation = resolveRespawnLocation(player, preferredLocation);
        return safeLocation != null ? safeLocation.clone() : null;
    }

    public void protectPlayerAfterRespawn(Player player, Location safeSpawn) {
        if (player == null || safeSpawn == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        Location protectedSpawn = resolveRespawnLocation(player, safeSpawn);
        if (protectedSpawn == null) {
            return;
        }

        armPostRespawnProtection(uuid, protectedSpawn);
        plugin.teleportToSafeArenaLocation(player, protectedSpawn);
        plugin.applySafeArenaTeleportProtection(player, false);

        scheduleRespawnStabilization(player, protectedSpawn, 1L);
        scheduleRespawnStabilization(player, protectedSpawn, 5L);
        scheduleRespawnStabilization(player, protectedSpawn, 15L);
    }

    private void cleanup(UUID uuid) {
        respawnLocations.remove(uuid);
        if (pendingTasks.containsKey(uuid)) {
            pendingTasks.get(uuid).cancel();
            pendingTasks.remove(uuid);
        }
    }

    private void armPostRespawnProtection(UUID uuid, Location safeSpawn) {
        postRespawnProtection.put(uuid, System.currentTimeMillis() + POST_RESPAWN_PROTECTION_MS);
        if (safeSpawn != null) {
            protectedRespawnLocations.put(uuid, safeSpawn.clone());
        } else {
            protectedRespawnLocations.remove(uuid);
        }
    }

    private void clearPostRespawnProtection(UUID uuid) {
        postRespawnProtection.remove(uuid);
        protectedRespawnLocations.remove(uuid);
    }

    public boolean hasPostRespawnProtection(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        Long expiresAt = postRespawnProtection.get(uuid);
        if (expiresAt == null) {
            return false;
        }
        if (System.currentTimeMillis() > expiresAt) {
            clearPostRespawnProtection(uuid);
            return false;
        }
        return true;
    }

    public void releasePostRespawnProtectionIfMoved(Player player, Location currentLocation) {
        if (player == null || currentLocation == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        if (!hasPostRespawnProtection(uuid)) {
            return;
        }

        Location safeSpawn = protectedRespawnLocations.get(uuid);
        if (safeSpawn == null) {
            clearPostRespawnProtection(uuid);
            return;
        }

        if (currentLocation.getWorld() == null || safeSpawn.getWorld() == null
                || !currentLocation.getWorld().equals(safeSpawn.getWorld())) {
            return;
        }

        if (isSuspiciousRespawnTeleport(currentLocation, safeSpawn)) {
            return;
        }

        if (horizontalDistanceSquared(currentLocation, safeSpawn) >= POST_RESPAWN_RELEASE_DISTANCE_SQUARED) {
            clearPostRespawnProtection(uuid);
        }
    }

    public boolean isRespawning(UUID uuid) {
        return uuid != null && (respawnLocations.containsKey(uuid) || pendingTasks.containsKey(uuid));
    }

    public boolean rescuePlayerToProtectedSpawn(Player player) {
        if (player == null) {
            return false;
        }

        UUID uuid = player.getUniqueId();
        if (!hasPostRespawnProtection(uuid)) {
            return false;
        }

        Location safeSpawn = protectedRespawnLocations.get(uuid);
        if (safeSpawn == null) {
            return false;
        }
        if (!isLikelyRespawnDisplacement(player.getLocation(), safeSpawn)) {
            return false;
        }

        plugin.teleportToSafeArenaLocation(player, safeSpawn);
        plugin.applySafeArenaTeleportProtection(player, false);
        return true;
    }

    public Location getProtectedRespawnTeleportTarget(Player player, Location destination) {
        if (player == null || destination == null) {
            return null;
        }

        UUID uuid = player.getUniqueId();
        if (!hasPostRespawnProtection(uuid)) {
            return null;
        }

        Location safeSpawn = protectedRespawnLocations.get(uuid);
        if (safeSpawn == null) {
            return null;
        }

        if (!isSuspiciousRespawnTeleport(destination, safeSpawn)) {
            return null;
        }

        return safeSpawn.clone();
    }

    public void forceStop(Player player) {
        if (player != null && respawnLocations.containsKey(player.getUniqueId())) {
            plugin.cancelBlockDisappear(player.getUniqueId());
            plugin.removeSpectator(player, true);

            for (Player online : Bukkit.getOnlinePlayers()) {
                online.showPlayer(player);
            }
            cleanup(player.getUniqueId());
            clearPostRespawnProtection(player.getUniqueId());
            plugin.unsuppressBlockDisappear(player.getUniqueId());
        }
    }

    public boolean releaseForRoundTransition(Player player) {
        if (player == null) {
            return false;
        }

        UUID uuid = player.getUniqueId();
        BukkitTask pendingTask = pendingTasks.remove(uuid);
        boolean hadPendingTask = pendingTask != null;
        if (pendingTask != null) {
            pendingTask.cancel();
        }

        boolean hadRespawnLocation = respawnLocations.remove(uuid) != null;
        boolean hadPostProtection = postRespawnProtection.containsKey(uuid)
                || protectedRespawnLocations.containsKey(uuid);

        if (!hadPendingTask && !hadRespawnLocation && !hadPostProtection) {
            return false;
        }

        clearPostRespawnProtection(uuid);
        plugin.cancelBlockDisappear(uuid);
        plugin.removeSpectator(player, false);

        if (plugin.partyFFAManager != null) {
            plugin.partyFFAManager.startRespawnRedirect(player);
        }

        plugin.respawnInFight(player);
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showPlayer(player);
        }
        if (player.isOnline()) {
            plugin.syncLayoutInstant(player, 4);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.unsuppressBlockDisappear(uuid), 1L);
        return true;
    }

    private boolean shouldAbortRespawn(Player player) {
        if (player == null || !player.isOnline()) {
            return true;
        }

        UUID uuid = player.getUniqueId();
        if (plugin.leavingMatchPlayers.contains(uuid) || !plugin.isInFight(player)) {
            return true;
        }

        return plugin.pearlFightManager != null
                && plugin.pearlFightManager.isPearlFightKit(plugin.getKitName(player))
                && !plugin.pearlFightManager.isTracked(player);
    }

    private void abortRespawn(Player player, UUID uuid) {
        forceStop(player);
        cleanup(uuid);
        clearPostRespawnProtection(uuid);
        plugin.unsuppressBlockDisappear(uuid);
    }

    private Location resolveRespawnLocation(Player player, Location preferredLocation) {
        Location preferredSafeLocation = getUsableRespawnLocation(preferredLocation);
        if (preferredSafeLocation != null) {
            return preferredSafeLocation;
        }

        if (player != null) {
            Location cachedArenaSpawn = getUsableRespawnLocation(plugin.arenaSpawnLocations.get(player.getUniqueId()));
            if (cachedArenaSpawn != null) {
                return cachedArenaSpawn;
            }

            if (player.getWorld() != null) {
                Location worldSpawn = getUsableRespawnLocation(player.getWorld().getSpawnLocation());
                if (worldSpawn != null) {
                    return worldSpawn;
                }
            }

            Location currentLocation = player.getLocation() != null ? player.getLocation().clone() : null;
            if (currentLocation != null) {
                currentLocation.setY(Math.max(currentLocation.getY(), plugin.voidYLimit + 5));
                Location liftedSafeLocation = getUsableRespawnLocation(currentLocation);
                if (liftedSafeLocation != null) {
                    return liftedSafeLocation;
                }
                return currentLocation;
            }
        }

        if (preferredLocation != null) {
            return preferredLocation.clone();
        }

        return player != null ? player.getLocation().clone() : null;
    }

    private Location getUsableRespawnLocation(Location location) {
        if (location == null) {
            return null;
        }

        Location safeLocation = plugin.resolveSafeArenaLocation(location.clone());
        if (safeLocation == null || safeLocation.getWorld() == null) {
            return null;
        }

        return safeLocation.getY() > plugin.voidYLimit ? safeLocation : null;
    }

    private void stabilizeRespawnPosition(Player player, Location safeSpawn) {
        if (player == null || safeSpawn == null) {
            return;
        }
        if (!isLikelyRespawnDisplacement(player.getLocation(), safeSpawn)) {
            return;
        }
        plugin.teleportToSafeArenaLocation(player, safeSpawn);
        plugin.applySafeArenaTeleportProtection(player, false);
    }

    private void scheduleRespawnStabilization(Player player, Location safeSpawn, long delayTicks) {
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || plugin.leavingMatchPlayers.contains(uuid) || !hasPostRespawnProtection(uuid)) {
                return;
            }
            stabilizeRespawnPosition(player, safeSpawn);
        }, delayTicks);
    }

    private void holdPlayerAtDeathLocation(Player player, Location deathLocation) {
        if (player == null || deathLocation == null || deathLocation.getWorld() == null) {
            return;
        }

        stabilizeDeathLocation(player, deathLocation);

        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !respawnLocations.containsKey(uuid)) {
                return;
            }
            stabilizeDeathLocation(player, deathLocation);
        }, 1L);
    }

    private void stabilizeDeathLocation(Player player, Location deathLocation) {
        if (player == null || deathLocation == null || deathLocation.getWorld() == null) {
            return;
        }

        try {
            player.teleport(deathLocation.clone());
        } catch (Exception ignored) {
        }

        player.setFallDistance(0f);
        player.setFireTicks(0);
        try {
            player.setVelocity(new Vector(0, 0, 0));
        } catch (Exception ignored) {
        }
    }

    private boolean isSuspiciousRespawnTeleport(Location destination, Location safeSpawn) {
        if (destination == null || safeSpawn == null
                || destination.getWorld() == null || safeSpawn.getWorld() == null) {
            return false;
        }
        if (!destination.getWorld().equals(safeSpawn.getWorld())) {
            return true;
        }
        if (destination.getY() <= plugin.voidYLimit) {
            return true;
        }
        if (isBelowProtectedRespawnHeight(destination, safeSpawn)) {
            return true;
        }
        return horizontalDistanceSquared(destination, safeSpawn) > POST_RESPAWN_MAX_HORIZONTAL_DRIFT_SQUARED;
    }

    private boolean isLikelyRespawnDisplacement(Location currentLocation, Location safeSpawn) {
        if (currentLocation == null || safeSpawn == null
                || currentLocation.getWorld() == null || safeSpawn.getWorld() == null) {
            return false;
        }
        if (!currentLocation.getWorld().equals(safeSpawn.getWorld())) {
            return true;
        }
        if (currentLocation.getY() <= plugin.voidYLimit) {
            return true;
        }
        if (isBelowProtectedRespawnHeight(currentLocation, safeSpawn)) {
            return true;
        }
        return horizontalDistanceSquared(currentLocation, safeSpawn) > POST_RESPAWN_MAX_HORIZONTAL_DRIFT_SQUARED;
    }

    private boolean isBelowProtectedRespawnHeight(Location location, Location safeSpawn) {
        return (safeSpawn.getY() - location.getY()) > POST_RESPAWN_MAX_DOWNWARD_DRIFT;
    }

    private double horizontalDistanceSquared(Location first, Location second) {
        double dx = first.getX() - second.getX();
        double dz = first.getZ() - second.getZ();
        return (dx * dx) + (dz * dz);
    }
}
