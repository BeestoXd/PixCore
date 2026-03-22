package com.pixra.pixCore.managers;

import com.pixra.pixCore.PixCore;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.List;

public class FireballKnockbackManager implements Listener {

    private final PixCore plugin;
    private File configFile;
    private FileConfiguration config;

    public FireballKnockbackManager(PixCore plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "knockback.yml");
        if (!configFile.exists()) {
            plugin.saveResource("knockback.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        plugin.getLogger().info("knockback.yml loaded for FireballKnockbackManager.");
    }

    public void reload() {
        loadConfig();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFireballExplosion(EntityDamageByEntityEvent event) {
        if (!config.getBoolean("fireball-knockback.enabled", false)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) return;
        if (!(event.getEntity() instanceof Player)) return;
        if (!(event.getDamager() instanceof Fireball)) return;

        Player victim = (Player) event.getEntity();

        String kitName = plugin.getKitName(victim);
        if (kitName == null) return;

        List<String> kits = config.getStringList("fireball-knockback.kits");
        boolean applicable = false;
        for (String k : kits) {
            if (k.equalsIgnoreCase(kitName)) {
                applicable = true;
                break;
            }
        }
        if (!applicable) return;

        double horizontal    = config.getDouble("fireball-knockback.horizontal", 1.1);
        double vertical      = config.getDouble("fireball-knockback.vertical", 0.45);
        double verticalLimit = config.getDouble("fireball-knockback.vertical-limit", 0.70);
        double minHorizontal = config.getDouble("fireball-knockback.min-horizontal", 0.6);

        Location fbLoc     = event.getDamager().getLocation().clone();
        Location playerLoc = victim.getLocation().clone();

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!victim.isOnline()) return;

                Vector dir = playerLoc.toVector().subtract(fbLoc.toVector());
                dir.setY(0);

                double len = dir.length();
                if (len < 0.001) {

                    dir = new Vector(Math.random() - 0.5, 0, Math.random() - 0.5);
                    len = dir.length();
                    if (len < 0.001) dir = new Vector(1, 0, 0);
                }

                dir.normalize();

                double h = Math.max(horizontal, minHorizontal);
                dir.multiply(h);

                double currentY = victim.getVelocity().getY();
                @SuppressWarnings("deprecation")
                boolean onGround = victim.isOnGround();
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
