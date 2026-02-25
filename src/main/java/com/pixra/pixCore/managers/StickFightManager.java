package com.pixra.pixCore.managers;

import com.pixra.pixCore.PixCore;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
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

public class StickFightManager implements Listener {

    private final PixCore plugin;
    private File configFile;
    private FileConfiguration config;

    public StickFightManager(PixCore plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "stickfight.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            plugin.saveResource("stickfight.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        plugin.getLogger().info("stickfight.yml loaded.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStickFightHit(EntityDamageByEntityEvent event) {
        if (!config.getBoolean("stickfight-mechanics.straight-knockback.enabled", false)) {
            return;
        }

        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return;
        }

        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }

        Player victim = (Player) event.getEntity();
        Player attacker = (Player) event.getDamager();

        String kitName = plugin.getKitName(attacker);
        if (kitName == null) return;

        List<String> allowedKits = config.getStringList("stickfight-mechanics.straight-knockback.kits");
        boolean isAllowed = false;
        if (allowedKits != null) {
            for (String kit : allowedKits) {
                if (kit.equalsIgnoreCase(kitName)) {
                    isAllowed = true;
                    break;
                }
            }
        }

        if (!isAllowed) return;

        double horizontal = config.getDouble("stickfight-mechanics.straight-knockback.horizontal", 0.85);
        double vertical = config.getDouble("stickfight-mechanics.straight-knockback.vertical", 0.40);
        double verticalLimit = config.getDouble("stickfight-mechanics.straight-knockback.vertical-limit", 0.45);
        double sprintMultiplier = config.getDouble("stickfight-mechanics.straight-knockback.sprint-multiplier", 1.20);

        if (attacker.isSprinting()) {
            horizontal *= sprintMultiplier;
            attacker.setSprinting(false);
        }

        float yaw = attacker.getLocation().getYaw();
        double radians = Math.toRadians(yaw);

        final double finalDx = -Math.sin(radians) * horizontal;
        final double finalDz = Math.cos(radians) * horizontal;
        final double finalDy = vertical;
        final double finalLimit = verticalLimit;

        new BukkitRunnable() {
            @Override
            public void run() {
                victim.setFallDistance(0);

                Vector velocity = new Vector(finalDx, 0, finalDz);

                if (victim.isOnGround()) {
                    velocity.setY(finalDy);
                } else {
                    double currentY = victim.getVelocity().getY();
                    double airVertical = finalDy * 0.7;
                    velocity.setY(Math.min(currentY + airVertical, finalLimit));
                }

                victim.setVelocity(velocity);
            }
        }.runTask(plugin);
    }
}