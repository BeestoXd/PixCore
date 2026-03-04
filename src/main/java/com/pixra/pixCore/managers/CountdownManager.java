package com.pixra.pixCore.managers;

import com.pixra.pixCore.PixCore;
import com.pixra.pixCore.util.TitleUtil;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class CountdownManager {

    private final PixCore plugin;

    public CountdownManager(PixCore plugin) {
        this.plugin = plugin;
    }

    public void startMatchCountdown(List<Player> players) {

        for (Player p : players) {
            if (p == null || !p.isOnline()) continue;
            plugin.frozenPlayers.add(p.getUniqueId());

            new BukkitRunnable() {
                @Override public void run() {
                    if (p.isOnline()) plugin.applyStartKit(p);
                }
            }.runTaskLater(plugin, 5L);

            new BukkitRunnable() {
                @Override public void run() {
                    if (p.isOnline()) plugin.applyStartKit(p);
                }
            }.runTaskLater(plugin, 15L);
        }

        final int maxSeconds = plugin.startCountdownDuration;
        new BukkitRunnable() {
            int current = maxSeconds;

            @Override
            public void run() {
                if (current <= 0) {
                    for (Player p : players) {
                        if (p == null || !p.isOnline()) continue;

                        plugin.arenaSpawnLocations.put(p.getUniqueId(), p.getLocation().clone());

                        if (plugin.startMatchMessage != null && !plugin.startMatchMessage.isEmpty()) {
                            p.sendMessage(plugin.startMatchMessage);
                        }

                        if (plugin.startCountdownTitles != null
                                && plugin.startCountdownTitles.containsKey(0)) {
                            TitleUtil.sendTitle(p, plugin.startCountdownTitles.get(0), "", 0, 20, 10);
                        }

                        if (plugin.startCountdownSoundEnabled && plugin.startMatchSound != null) {
                            p.playSound(p.getLocation(), plugin.startMatchSound,
                                    plugin.startCountdownVolume, plugin.startCountdownPitch);
                        }

                        plugin.frozenPlayers.remove(p.getUniqueId());

                        if (plugin.blockReplenishManager != null) {
                            plugin.blockReplenishManager.scanPlayerInventory(p);
                        }
                    }
                    cancel();
                    return;
                }

                if (plugin.startCountdownEnabled) {
                    for (Player p : players) {
                        if (p == null || !p.isOnline()) continue;

                        if (plugin.startCountdownMessages != null) {
                            p.sendMessage(plugin.startCountdownMessages
                                    .getOrDefault(current, ChatColor.RED + String.valueOf(current)));
                        }

                        if (plugin.startCountdownTitles != null
                                && plugin.startCountdownTitles.containsKey(current)) {
                            TitleUtil.sendTitle(p, plugin.startCountdownTitles.get(current), "", 0, 20, 0);
                        }

                        if (plugin.startCountdownSoundEnabled && plugin.startCountdownSounds != null) {
                            Sound s = plugin.startCountdownSounds.get(current);
                            if (s != null) p.playSound(p.getLocation(), s,
                                    plugin.startCountdownVolume, plugin.startCountdownPitch);
                        }
                    }
                }

                current--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }
}