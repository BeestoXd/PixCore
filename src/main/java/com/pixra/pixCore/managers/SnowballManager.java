package com.pixra.pixCore.managers;

import com.pixra.pixCore.PixCore;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class SnowballManager implements Listener {

    private final PixCore plugin;

    public SnowballManager(PixCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSnowBreak(BlockBreakEvent event) {
        if (!plugin.getConfig().getBoolean("settings.snow-break-mechanic.enabled", false)) {
            return;
        }

        if (event.getBlock().getType() != Material.SNOW_BLOCK) {
            return;
        }

        Player player = event.getPlayer();

        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        String kitName = plugin.getKitName(player);
        if (kitName == null) return;

        List<String> allowedKits = plugin.getConfig().getStringList("settings.snow-break-mechanic.kits");
        boolean isAllowed = false;

        if (allowedKits != null) {
            for (String kit : allowedKits) {
                if (kit.equalsIgnoreCase(kitName)) {
                    isAllowed = true;
                    break;
                }
            }
        }

        if (isAllowed) {
            event.setCancelled(true);
            event.getBlock().setType(Material.AIR);

            Material snowballMat = Material.getMaterial("SNOW_BALL");
            if (snowballMat == null) {
                snowballMat = Material.getMaterial("SNOWBALL");
            }

            if (snowballMat != null) {
                ItemStack snowball = new ItemStack(snowballMat, 1);
                player.getInventory().addItem(snowball);
            }
        }
    }
}