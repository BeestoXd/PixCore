package com.pixra.pixCore.managers;

import com.pixra.pixCore.PixCore;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.List;

public class NoFallDamageManager implements Listener {

    private final PixCore plugin;

    public NoFallDamageManager(PixCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }

        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();

        if (!plugin.isHooked()) {
            return;
        }

        if (plugin.isInFight(player)) {
            String currentKit = plugin.getKitName(player);

            if (currentKit != null) {
                FileConfiguration config = plugin.getNoFallDamageConfig();

                if (config == null) {
                    return;
                }

                List<String> noFallKits = config.getStringList("kits");

                if (noFallKits == null || noFallKits.isEmpty()) {
                    return;
                }

                for (String kit : noFallKits) {
                    if (kit.equalsIgnoreCase(currentKit)) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }
}