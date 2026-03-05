package com.pixra.pixCore.knockback;

import com.pixra.pixCore.PixCore;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MLGRushKnockback implements Listener {

    private final PixCore plugin;
    private File configFile;
    private FileConfiguration config;

    private boolean enabled;
    private List<String> kits;

    private double horizontal;
    private double vertical;
    private double verticalLimit;
    private double horizontalCap;
    private double friction;

    private double sprintMultiplier;
    private boolean resetSprint;

    private double airHorizontalMult;
    private double airVerticalMult;

    private boolean comboEnabled;
    private long    comboWindowMs;
    private double  comboHorizontalBonus;

    private double enchantKbHorizontal;
    private double enchantKbVertical;

    private final ConcurrentHashMap<UUID, Long> lastHitTime = new ConcurrentHashMap<>();

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

        enabled          = config.getBoolean("enabled", true);
        kits             = config.getStringList("kits");

        horizontal       = config.getDouble("horizontal",        0.36);
        vertical         = config.getDouble("vertical",          0.33);
        verticalLimit    = config.getDouble("vertical-limit",    0.40);
        horizontalCap    = config.getDouble("horizontal-cap",    0.55);
        friction         = config.getDouble("friction",          2.2);

        sprintMultiplier = config.getDouble("sprint-multiplier", 1.18);
        resetSprint      = config.getBoolean("reset-sprint",     true);

        airHorizontalMult = config.getDouble("air.horizontal-multiplier", 0.60);
        airVerticalMult   = config.getDouble("air.vertical-multiplier",   0.75);

        comboEnabled          = config.getBoolean("combo.enabled",           true);
        comboWindowMs         = config.getLong("combo.window-ms",            650L);
        comboHorizontalBonus  = config.getDouble("combo.horizontal-bonus",   0.06);

        enchantKbHorizontal = config.getDouble("enchantment.kb-horizontal-per-level", 0.42);
        enchantKbVertical   = config.getDouble("enchantment.kb-vertical-per-level",   0.04);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!enabled) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (!(event.getEntity()  instanceof Player)) return;
        if (!(event.getDamager() instanceof Player)) return;

        Player victim   = (Player) event.getEntity();
        Player attacker = (Player) event.getDamager();

        if (!isApplicableKit(plugin.getKitName(victim))) return;

        UUID   pairKey = pairKey(attacker.getUniqueId(), victim.getUniqueId());
        long   now     = System.currentTimeMillis();
        Long   lastHit = lastHitTime.get(pairKey);
        boolean isCombo = comboEnabled && lastHit != null && (now - lastHit) < comboWindowMs;
        lastHitTime.put(pairKey, now);

        if (resetSprint && attacker.isSprinting()) {
            attacker.setSprinting(false);
        }

        final boolean wasSprinting  = attacker.isSprinting();
        final int     kbLevel       = getKnockbackLevel(attacker);
        final float   attackerYaw   = attacker.getLocation().getYaw();

        new BukkitRunnable() {
            @Override
            public void run() {
                if (victim.isDead() || !victim.isOnline()) return;

                applyKnockback(victim, attacker, attackerYaw, wasSprinting, kbLevel, isCombo);
            }
        }.runTaskLater(plugin, 1L);
    }

    private void applyKnockback(Player victim, Player attacker,
                                float attackerYaw, boolean wasSprinting,
                                int kbLevel, boolean isCombo) {

        double radians = Math.toRadians(attackerYaw);
        double dirX    = -Math.sin(radians);
        double dirZ    =  Math.cos(radians);

        Vector current = victim.getVelocity();
        double baseX   = current.getX() / friction;
        double baseZ   = current.getZ() / friction;

        double h = horizontal;
        double v = vertical;

        if (wasSprinting)    h *= sprintMultiplier;
        if (kbLevel > 0) {
            h += enchantKbHorizontal * kbLevel;
            v += enchantKbVertical   * kbLevel;
        }
        if (isCombo)         h += comboHorizontalBonus;

        boolean onGround = victim.isOnGround();
        if (!onGround) {
            h *= airHorizontalMult;
            v *= airVerticalMult;
        }

        double newX = baseX + dirX * h;
        double newZ = baseZ + dirZ * h;
        double newY = onGround ? v : Math.min(current.getY() + v, verticalLimit);

        double resultH = Math.sqrt(newX * newX + newZ * newZ);
        if (resultH > horizontalCap) {
            double scale = horizontalCap / resultH;
            newX *= scale;
            newZ *= scale;
        }

        if (newY > verticalLimit) newY = verticalLimit;

        victim.setFallDistance(0);
        victim.setVelocity(new Vector(newX, newY, newZ));
    }

    private boolean isApplicableKit(String kitName) {
        if (kitName == null) return false;
        if (kits == null || kits.isEmpty()) {
            return kitName.equalsIgnoreCase("mlgrush")
                    || kitName.equalsIgnoreCase("mlgrushelo");
        }
        for (String k : kits) {
            if (k.equalsIgnoreCase(kitName)) return true;
        }
        return false;
    }

    private int getKnockbackLevel(Player attacker) {
        try {
            ItemStack weapon = attacker.getInventory().getItemInMainHand();
            if (weapon != null && weapon.containsEnchantment(Enchantment.KNOCKBACK)) {
                return weapon.getEnchantmentLevel(Enchantment.KNOCKBACK);
            }
        } catch (NoSuchMethodError e) {
            try {
                @SuppressWarnings("deprecation")
                ItemStack weapon = attacker.getItemInHand();
                if (weapon != null && weapon.containsEnchantment(Enchantment.KNOCKBACK)) {
                    return weapon.getEnchantmentLevel(Enchantment.KNOCKBACK);
                }
            } catch (Exception ignored) {}
        }
        return 0;
    }

    private UUID pairKey(UUID attacker, UUID victim) {
        long msb = attacker.getMostSignificantBits()  ^ (victim.getMostSignificantBits()  << 1);
        long lsb = attacker.getLeastSignificantBits() ^ (victim.getLeastSignificantBits() << 1);
        return new UUID(msb, lsb);
    }
}