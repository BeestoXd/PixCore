package com.pixra.pixCore.managers;

import com.pixra.pixCore.PixCore;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.List;

public class TntMechanicsManager implements Listener {

    private final PixCore plugin;

    public TntMechanicsManager(PixCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTntDamage(EntityDamageByEntityEvent event) {
        if (!plugin.getConfig().getBoolean("settings.tnt-mechanics.prevent-self-knockback.enabled", false)) {
            return;
        }

        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        if (!(event.getDamager() instanceof TNTPrimed)) {
            return;
        }

        Player victim = (Player) event.getEntity();
        TNTPrimed tnt = (TNTPrimed) event.getDamager();

        if (!(tnt.getSource() instanceof Player)) {
            return;
        }

        Player attacker = (Player) tnt.getSource();

        if (victim.getUniqueId().equals(attacker.getUniqueId())) {

            String kitName = plugin.getKitName(victim);
            if (kitName == null) return;

            List<String> allowedKits = plugin.getConfig().getStringList("settings.tnt-mechanics.prevent-self-knockback.kits");
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
            }
        }
    }
}