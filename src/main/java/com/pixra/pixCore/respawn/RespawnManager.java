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
        if (respawnLocations.containsKey(uuid)) return;
        respawnLocations.put(uuid, arenaSpawn.clone());

        if (pendingTasks.containsKey(uuid)) {
            pendingTasks.get(uuid).cancel();
            pendingTasks.remove(uuid);
        }

        clearInventory(player);

        plugin.addSpectator(player);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!respawnLocations.containsKey(uuid)) return;

                clearInventory(player);
                pendingTasks.remove(uuid);
            }
        }.runTaskLater(plugin, 5L);
        pendingTasks.put(uuid, task);

        Location currentLocation = player.getLocation();
        if (currentLocation.getY() <= 0 || currentLocation.getY() <= arenaSpawn.getY() - 10) {
            Location floatingLoc = currentLocation.clone();
            floatingLoc.setY(arenaSpawn.getY());
            player.teleport(floatingLoc);
        } else {
            player.teleport(currentLocation.add(0, 0.5, 0));
        }
    }

    private void clearInventory(Player player) {
        if (player != null && player.isOnline()) {
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            try {
                player.getInventory().setItemInOffHand(null);
            } catch (NoSuchMethodError ignored) {}
            player.updateInventory();
        }
    }

    public void finishRespawn(Player player) {
        UUID uuid = player.getUniqueId();
        Location spawn = respawnLocations.get(uuid);
        if (spawn == null) spawn = player.getLocation();
        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.removeSpectator(player, false);
            plugin.respawnInFight(player);
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.showPlayer(player);
            }

            // Terapkan custom layout ganda (Double-apply) untuk benar-benar menimpa sistem default StrikePractice
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    plugin.applyStartKit(player);
                }
            }, 5L);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    plugin.applyStartKit(player);
                }
            }, 15L);

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
            plugin.removeSpectator(player, true);

            for (Player online : Bukkit.getOnlinePlayers()) {
                online.showPlayer(player);
            }
            cleanup(player.getUniqueId());
        }
    }
}