package com.pixra.pixCore.knockback;

import com.pixra.pixCore.PixCore;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.io.File;

public class MLGRushKnockback implements Listener {

    private final PixCore plugin;
    private File configFile;
    private FileConfiguration config;

    private boolean enabled;
    private double horizontal;
    private double vertical;
    private double verticalLimit;
    private double sprintMultiplier;
    private double airHorizontalMultiplier;
    private double airVerticalMultiplier;

    private double enchantKbHorizontal;
    private double enchantKbVertical;

    public MLGRushKnockback(PixCore plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "knockback/mlgrush.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            plugin.saveResource("knockback/mlgrush.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);

        enabled = config.getBoolean("enabled", true);
        horizontal = config.getDouble("horizontal", 0.38);
        vertical = config.getDouble("vertical", 0.36);
        verticalLimit = config.getDouble("vertical-limit", 0.42);
        sprintMultiplier = config.getDouble("sprint-multiplier", 1.15);

        airHorizontalMultiplier = config.getDouble("air-horizontal-multiplier", 0.65);
        airVerticalMultiplier = config.getDouble("air-vertical-multiplier", 0.90);

        enchantKbHorizontal = config.getDouble("enchantment.kb-horizontal-per-level", 0.45);
        enchantKbVertical = config.getDouble("enchantment.kb-vertical-per-level", 0.05);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!enabled) return;

        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) return;

        Player victim = (Player) event.getEntity();
        Player attacker = (Player) event.getDamager();

        String kitName = plugin.getKitName(victim);
        if (kitName == null || (!kitName.equalsIgnoreCase("mlgrush") && !kitName.equalsIgnoreCase("mlgrushelo"))) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (victim.isDead()) return;

            double finalHorizontal = horizontal;
            double finalVertical = vertical;

            if (attacker.isSprinting()) {
                finalHorizontal *= sprintMultiplier;
            }

            ItemStack weapon = attacker.getItemInHand();
            if (weapon != null && weapon.containsEnchantment(Enchantment.KNOCKBACK)) {
                int kbLevel = weapon.getEnchantmentLevel(Enchantment.KNOCKBACK);
                finalHorizontal += (enchantKbHorizontal * kbLevel);
                finalVertical += (enchantKbVertical * kbLevel);
            }

            if (!victim.isOnGround()) {
                finalHorizontal *= airHorizontalMultiplier;
                finalVertical *= airVerticalMultiplier;
            }

            Vector trajectory = victim.getLocation().toVector().subtract(attacker.getLocation().toVector());

            if (trajectory.lengthSquared() == 0) {
                trajectory = new Vector(Math.random() - 0.5, 0, Math.random() - 0.5);
            }

            trajectory.setY(0).normalize();

            Vector newVelocity = new Vector(
                    trajectory.getX() * finalHorizontal,
                    finalVertical,
                    trajectory.getZ() * finalHorizontal
            );

            if (newVelocity.getY() > verticalLimit) {
                newVelocity.setY(verticalLimit);
            }

            victim.setVelocity(newVelocity);

        }, 1L);
    }
}