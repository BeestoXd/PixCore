package com.pixra.pixCore.managers;

import com.pixra.pixCore.PixCore;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.io.File;
import java.util.List;
import java.util.Locale;

public class BowHitMessageManager implements Listener {

    private final PixCore plugin;
    private File configFile;
    private FileConfiguration config;

    private boolean enabled;
    private String messageFormat;
    private List<String> allowedKits;

    public BowHitMessageManager(PixCore plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "bowhit.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            plugin.saveResource("bowhit.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);

        enabled = config.getBoolean("enabled", true);
        messageFormat = config.getString("message", "&d<player> &fis at &c<total> &4<icon_heart>");
        allowedKits = config.getStringList("allowed-kits");

        plugin.getLogger().info("bowhit.yml loaded.");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBowHit(EntityDamageByEntityEvent event) {
        if (!enabled) return;

        if (!(event.getDamager() instanceof Arrow)) return;
        Arrow arrow = (Arrow) event.getDamager();

        if (!(arrow.getShooter() instanceof Player)) return;
        Player shooter = (Player) arrow.getShooter();

        if (!(event.getEntity() instanceof Player)) return;
        Player victim = (Player) event.getEntity();

        if (shooter.getUniqueId().equals(victim.getUniqueId())) return;

        if (!plugin.isHooked()) return;

        try {
            if (!plugin.isInFight(shooter) || !plugin.isInFight(victim)) return;

            if (allowedKits != null && !allowedKits.isEmpty()) {
                String kitName = plugin.getKitName(shooter);
                if (kitName == null || !isKitAllowed(kitName)) {
                    return;
                }
            }

            double finalHealth = victim.getHealth() - event.getFinalDamage();
            if (finalHealth < 0) finalHealth = 0;

            double visualHealth = Math.ceil(finalHealth);

            String totalHearts = String.format(Locale.US, "%.1f", visualHealth);

            String msg = ChatColor.translateAlternateColorCodes('&', messageFormat)
                    .replace("<player>", victim.getName())
                    .replace("<total>", totalHearts)
                    .replace("<icon_heart>", "❤");

            shooter.sendMessage(msg);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isKitAllowed(String kitName) {
        for (String allowed : allowedKits) {
            if (allowed.equalsIgnoreCase(kitName)) return true;
        }
        return false;
    }
}