package com.pixra.pixCore.respawn;

import com.pixra.pixCore.PixCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RespawnManager {

    private final PixCore plugin;
    private final Map<UUID, Location> respawnLocations = new HashMap<>();
    private final Map<UUID, BukkitTask> pendingTasks = new HashMap<>();

    public RespawnManager(PixCore plugin) {
        this.plugin = plugin;
    }

    public void startRespawn(Player player, Location arenaSpawn) {
        UUID uuid = player.getUniqueId();
        if (respawnLocations.containsKey(uuid))
            return;
        respawnLocations.put(uuid, arenaSpawn.clone());

        if (pendingTasks.containsKey(uuid)) {
            pendingTasks.get(uuid).cancel();
            pendingTasks.remove(uuid);
        }

        plugin.suppressBlockDisappearReturn(uuid);

        clearInventory(player);

        plugin.addSpectator(player);

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

        Location currentLocation = player.getLocation();
        if (currentLocation.getY() <= 0 || currentLocation.getY() <= arenaSpawn.getY() - 10) {

            Location safePos = currentLocation.clone();
            safePos.setY(arenaSpawn.getY() + 5);
            player.teleport(safePos);
        }

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
        UUID uuid = player.getUniqueId();
        Location spawn = respawnLocations.get(uuid);
        if (spawn == null)
            spawn = player.getLocation();
        final Location finalSpawn = spawn;
        Bukkit.getScheduler().runTask(plugin, () -> {

            plugin.suppressBlockDisappearReturn(uuid);
            plugin.removeSpectator(player, false);

            player.teleport(finalSpawn);

            if (plugin.partyFFAManager != null)
                plugin.partyFFAManager.startRespawnRedirect(player);
            plugin.respawnInFight(player);
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.showPlayer(player);
            }
            if (player.isOnline()) plugin.syncLayoutInstant(player, 6);

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

    private void cleanup(UUID uuid) {
        respawnLocations.remove(uuid);
        if (pendingTasks.containsKey(uuid)) {
            pendingTasks.get(uuid).cancel();
            pendingTasks.remove(uuid);
        }
    }

    public void forceStop(Player player) {
        if (player != null && respawnLocations.containsKey(player.getUniqueId())) {
            plugin.cancelBlockDisappear(player.getUniqueId());
            plugin.removeSpectator(player, true);

            for (Player online : Bukkit.getOnlinePlayers()) {
                online.showPlayer(player);
            }
            cleanup(player.getUniqueId());
            plugin.unsuppressBlockDisappear(player.getUniqueId());
        }
    }
}
