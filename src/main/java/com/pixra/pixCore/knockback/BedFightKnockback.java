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

public class BedFightKnockback implements Listener {

    private final PixCore plugin;
    private File              configFile;
    private FileConfiguration config;

    private boolean      enabled;
    private List<String> kits;

    private double baseHorizontal;
    private double baseVertical;
    private double verticalCap;
    private double horizontalCap;

    private double  sprintBonus;
    private boolean cancelSprint;

    private boolean wtapEnabled;
    private double  wtapMinSpeed;
    private double  wtapBonus;

    private boolean comboEnabled;
    private long    comboWindowMs;
    private double  comboPerHit;
    private int     comboMaxStack;

    private double airHDelta;
    private double airVDelta;

    private double enchantHPerLevel;
    private double enchantVPerLevel;

    private double minHorizontal;

    private final ConcurrentHashMap<UUID, Long>    lastHitTime = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Integer> comboStack  = new ConcurrentHashMap<>();

    public BedFightKnockback(PixCore plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "knockback/bedfight.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            plugin.saveResource("knockback/bedfight.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);

        enabled  = config.getBoolean("enabled", true);
        kits     = config.getStringList("kits");

        baseHorizontal = config.getDouble("base.horizontal",      0.38);
        baseVertical   = config.getDouble("base.vertical",        0.30);
        verticalCap    = config.getDouble("base.vertical-cap",    0.42);
        horizontalCap  = config.getDouble("base.horizontal-cap",  0.65);

        sprintBonus    = config.getDouble("sprint.bonus",         0.07);
        cancelSprint   = config.getBoolean("sprint.cancel",       true);

        wtapEnabled    = config.getBoolean("wtap.enabled",        true);
        wtapMinSpeed   = config.getDouble("wtap.min-speed",       0.15);
        wtapBonus      = config.getDouble("wtap.bonus",           0.09);

        comboEnabled   = config.getBoolean("combo.enabled",       true);
        comboWindowMs  = config.getLong("combo.window-ms",        680L);
        comboPerHit    = config.getDouble("combo.per-hit-bonus",  0.035);
        comboMaxStack  = config.getInt("combo.max-stack",         5);

        airHDelta      = config.getDouble("air.h-delta",          0.04);
        airVDelta      = config.getDouble("air.v-delta",         -0.05);

        enchantHPerLevel = config.getDouble("enchant.h-per-level", 0.38);
        enchantVPerLevel = config.getDouble("enchant.v-per-level", 0.03);

        minHorizontal  = config.getDouble("min-horizontal",       0.30);
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

        final boolean victimOnGround = victim.isOnGround();
        final float   attackerYaw    = attacker.getLocation().getYaw();
        final boolean wasSprinting   = attacker.isSprinting();
        final boolean isWtap         = wtapEnabled && detectWtap(attacker);
        final int     kbLevel        = getKBLevel(attacker);

        UUID pairKey = pairKey(attacker.getUniqueId(), victim.getUniqueId());
        long now     = System.currentTimeMillis();
        Long prevHit = lastHitTime.get(pairKey);
        final int stack;
        if (comboEnabled && prevHit != null && (now - prevHit) < comboWindowMs) {
            stack = Math.min(comboStack.getOrDefault(pairKey, 0) + 1, comboMaxStack);
        } else {
            stack = 0;
        }
        lastHitTime.put(pairKey, now);
        comboStack.put(pairKey, stack);

        if (cancelSprint && wasSprinting) attacker.setSprinting(false);

        victim.setVelocity(new Vector(0, 0, 0));

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!victim.isOnline() || victim.isDead()) return;
                applyKB(victim, attackerYaw, victimOnGround,
                        wasSprinting, isWtap, kbLevel, stack);
            }
        }.runTaskLater(plugin, 1L);
    }

    private void applyKB(Player victim,
                         float   attackerYaw,
                         boolean victimWasOnGround,
                         boolean wasSprinting,
                         boolean isWtap,
                         int     kbLevel,
                         int     stack) {

        double rad  = Math.toRadians(attackerYaw);
        double dirX = -Math.sin(rad);
        double dirZ =  Math.cos(rad);

        double h = baseHorizontal;
        double v = baseVertical;

        if (wasSprinting)  h += sprintBonus;
        if (isWtap)        h += wtapBonus;
        if (stack > 0)     h += comboPerHit * stack;
        if (kbLevel > 0) {
            h += enchantHPerLevel * kbLevel;
            v += enchantVPerLevel * kbLevel;
        }

        if (!victimWasOnGround) {
            h += airHDelta;
            v += airVDelta;
        }

        if (h < minHorizontal) h = minHorizontal;
        if (h > horizontalCap) h = horizontalCap;

        double newX = dirX * h;
        double newZ = dirZ * h;
        double newY;

        if (victimWasOnGround) {
            newY = v;
        } else {
            double currentY = victim.getVelocity().getY();
            newY = Math.min(currentY + v, verticalCap);
        }

        if (newY > verticalCap) newY = verticalCap;

        victim.setFallDistance(0);
        victim.setVelocity(new Vector(newX, newY, newZ));
    }

    private boolean detectWtap(Player attacker) {
        Vector v = attacker.getVelocity();
        double speed = Math.sqrt(v.getX() * v.getX() + v.getZ() * v.getZ());
        return speed >= wtapMinSpeed;
    }

    private int getKBLevel(Player attacker) {
        ItemStack weapon = null;
        try {
            weapon = attacker.getInventory().getItemInMainHand();
        } catch (NoSuchMethodError e) {
            try {
                weapon = attacker.getItemInHand();
            } catch (Exception ignored) {}
        }
        if (weapon != null && weapon.containsEnchantment(Enchantment.KNOCKBACK)) {
            return weapon.getEnchantmentLevel(Enchantment.KNOCKBACK);
        }
        return 0;
    }

    private boolean isApplicableKit(String kitName) {
        if (kitName == null) return false;
        if (kits == null || kits.isEmpty()) {
            String l = kitName.toLowerCase();
            return l.contains("bed") || l.contains("bedfight");
        }
        for (String k : kits) {
            if (k.equalsIgnoreCase(kitName)) return true;
        }
        return false;
    }

    private UUID pairKey(UUID a, UUID b) {
        long msb = a.getMostSignificantBits()  * 1610612741L ^ b.getMostSignificantBits();
        long lsb = a.getLeastSignificantBits() * 1610612741L ^ b.getLeastSignificantBits();
        return new UUID(msb, lsb);
    }
}