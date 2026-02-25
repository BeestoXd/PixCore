package com.pixra.pixCore.managers;

import com.pixra.pixCore.PixCore;
import org.bukkit.configuration.ConfigurationSection;
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

public class CustomKnockbackManager implements Listener {

    private final PixCore plugin;
    private File kbFile;
    private FileConfiguration kbConfig;

    public CustomKnockbackManager(PixCore plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        kbFile = new File(plugin.getDataFolder(), "knockback.yml");
        if (!kbFile.exists()) {
            kbFile.getParentFile().mkdirs();
            plugin.saveResource("knockback.yml", false);
        }
        kbConfig = YamlConfiguration.loadConfiguration(kbFile);
        plugin.getLogger().info("knockback.yml loaded.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {

        if (!kbConfig.getBoolean("enabled", true)) return;

        if (!(event.getEntity() instanceof Player)) return;
        if (!(event.getDamager() instanceof Player)) return;

        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;

        Player victim = (Player) event.getEntity();
        Player attacker = (Player) event.getDamager();

        String kitName = plugin.getKitName(attacker);

        ConfigurationSection section;

        if (kitName != null && kbConfig.contains("kits." + kitName)) {
            section = kbConfig.getConfigurationSection("kits." + kitName);
        } else if (kbConfig.getBoolean("use-default-if-missing", true)) {
            section = kbConfig.getConfigurationSection("default");
        } else {
            return;
        }

        if (section == null) return;

        double friction = section.getDouble("friction", 2.0);
        double horizontal = section.getDouble("horizontal", 0.39);
        double vertical = section.getDouble("vertical", 0.34);
        double verticalLimit = section.getDouble("vertical-limit", 0.38);
        double sprintMultiplier = section.getDouble("sprint-multiplier", 1.15);
        double horizontalCap = section.getDouble("horizontal-cap", 0.62);

        applyKnockback(victim, attacker,
                friction,
                horizontal,
                vertical,
                verticalLimit,
                sprintMultiplier,
                horizontalCap);
    }

    private void applyKnockback(Player victim,
                                Player attacker,
                                double friction,
                                double horizontal,
                                double vertical,
                                double verticalLimit,
                                double sprintMultiplier,
                                double horizontalCap) {

        victim.setFallDistance(0);

        Vector velocity = victim.getVelocity();

        velocity.setX(velocity.getX() * (1.0 / friction));
        velocity.setZ(velocity.getZ() * (1.0 / friction));

        float yaw = attacker.getLocation().getYaw();
        double radians = Math.toRadians(yaw);

        double directionX = -Math.sin(radians);
        double directionZ = Math.cos(radians);

        double finalHorizontal = horizontal;

        if (attacker.isSprinting()) {
            finalHorizontal *= sprintMultiplier;
            attacker.setSprinting(false);
        }

        velocity.setX(velocity.getX() + (directionX * finalHorizontal));
        velocity.setZ(velocity.getZ() + (directionZ * finalHorizontal));

        double currentHorizontal = Math.sqrt(
                velocity.getX() * velocity.getX() +
                        velocity.getZ() * velocity.getZ()
        );

        if (currentHorizontal > horizontalCap) {
            double scale = horizontalCap / currentHorizontal;
            velocity.setX(velocity.getX() * scale);
            velocity.setZ(velocity.getZ() * scale);
        }

        if (victim.isOnGround()) {
            velocity.setY(vertical);
        } else {
            double airVertical = vertical * 0.7;
            velocity.setY(Math.min(velocity.getY() + airVertical, verticalLimit));
        }

        if (velocity.getY() > verticalLimit) {
            velocity.setY(verticalLimit);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                victim.setVelocity(velocity);
            }
        }.runTask(plugin);
    }
}