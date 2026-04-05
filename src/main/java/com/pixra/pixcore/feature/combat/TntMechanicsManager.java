package com.pixra.pixcore.feature.combat;

import com.pixra.pixcore.PixCore;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.List;

public class TntMechanicsManager implements Listener {

    private final PixCore plugin;
    private File configFile;
    private FileConfiguration config;

    public TntMechanicsManager(PixCore plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "knockback.yml");
        if (!configFile.exists()) {
            plugin.saveResource("knockback.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void reload() {
        loadConfig();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTntDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (!(event.getDamager() instanceof TNTPrimed)) return;

        Player victim = (Player) event.getEntity();
        TNTPrimed tnt = (TNTPrimed) event.getDamager();

        if (!(tnt.getSource() instanceof Player)) return;

        Player attacker = (Player) tnt.getSource();

        if (!victim.getUniqueId().equals(attacker.getUniqueId())) return;

        String kitName = plugin.getKitName(victim);
        if (kitName == null) return;

        if (config.getBoolean("tnt-self-knockback.enabled", false)) {
            List<String> kbKits = config.getStringList("tnt-self-knockback.kits");
            for (String k : kbKits) {
                if (k.equalsIgnoreCase(kitName)) {
                    applyTntSelfKnockback(event, victim, tnt);
                    return;
                }
            }
        }

        if (plugin.getConfig().getBoolean("settings.tnt-mechanics.prevent-self-knockback.enabled", false)) {
            List<String> legacyKits = plugin.getConfig().getStringList("settings.tnt-mechanics.prevent-self-knockback.kits");
            for (String k : legacyKits) {
                if (k.equalsIgnoreCase(kitName)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    private void applyTntSelfKnockback(EntityDamageByEntityEvent event, Player victim, TNTPrimed tnt) {
        double horizontal    = config.getDouble("tnt-self-knockback.horizontal", 0.9);
        double vertical      = config.getDouble("tnt-self-knockback.vertical", 0.42);
        double verticalLimit = config.getDouble("tnt-self-knockback.vertical-limit", 0.65);
        double minHorizontal = config.getDouble("tnt-self-knockback.min-horizontal", 0.4);

        Location tntLoc    = tnt.getLocation().clone();
        Location playerLoc = victim.getLocation().clone();

        event.setCancelled(true);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!victim.isOnline()) return;

                Vector dir = playerLoc.toVector().subtract(tntLoc.toVector());
                dir.setY(0);

                double len = dir.length();
                if (len < 0.001) {
                    dir = new Vector(Math.random() - 0.5, 0, Math.random() - 0.5);
                    len = dir.length();
                    if (len < 0.001) dir = new Vector(1, 0, 0);
                }

                dir.normalize();
                dir.multiply(Math.max(horizontal, minHorizontal));

                @SuppressWarnings("deprecation")
                boolean onGround = victim.isOnGround();
                double currentY = victim.getVelocity().getY();
                double vy = onGround
                        ? vertical
                        : Math.min(currentY + vertical * 0.65, verticalLimit);
                dir.setY(vy);

                victim.setVelocity(dir);
                victim.setFallDistance(0);
            }
        }.runTask(plugin);
    }
}
