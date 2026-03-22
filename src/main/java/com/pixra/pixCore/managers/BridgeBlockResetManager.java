package com.pixra.pixCore.managers;

import com.pixra.pixCore.PixCore;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class BridgeBlockResetManager {

    private final PixCore plugin;

    public BridgeBlockResetManager(PixCore plugin) {
        this.plugin = plugin;
    }

    public void captureKitBlocks(Player player) {}

    public void restoreBlocks(Player player) {
        plugin.forceRestoreKitBlocks(player, null);
    }

    public void scheduleRestore(final Player player, final long... delays) {
        plugin.forceRestoreKitBlocks(player, null);
        for (long delay : delays) {
            new BukkitRunnable() {
                @Override public void run() {
                    if (player.isOnline()) plugin.forceRestoreKitBlocks(player, null);
                }
            }.runTaskLater(plugin, delay);
        }
    }

    public void clearPlayerData(Player player) {}
}
