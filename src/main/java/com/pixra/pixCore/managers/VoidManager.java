package com.pixra.pixCore.managers;

import com.pixra.pixCore.PixCore;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class VoidManager implements Listener {

    private static final long COOLDOWN_MS = 5000;

    private final PixCore plugin;
    private File configFile;
    private FileConfiguration config;

    private boolean enabled;
    private int defaultVoidLevel;
    private List<String> respawnKits;
    private Map<String, Integer> kitVoidLevels = new HashMap<>();

    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public VoidManager(PixCore plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "void.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            plugin.saveResource("void.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);

        enabled = config.getBoolean("enabled", true);
        defaultVoidLevel = config.getInt("default-void-y", -64);
        respawnKits = config.getStringList("respawn-kits");

        kitVoidLevels.clear();
        if (config.isConfigurationSection("kit-void-y")) {
            for (String kitName : config.getConfigurationSection("kit-void-y").getKeys(false)) {
                kitVoidLevels.put(kitName.toLowerCase(), config.getInt("kit-void-y." + kitName));
            }
        }

        plugin.getLogger().info("void.yml loaded.");
    }

    public void reload() {
        loadConfig();
    }

    private int getVoidLevelForKit(String kitName) {
        if (kitName == null) return defaultVoidLevel;
        return kitVoidLevels.getOrDefault(kitName.toLowerCase(), defaultVoidLevel);
    }

    private boolean isOnCooldown(UUID uuid) {
        Long last = cooldowns.get(uuid);
        return last != null && (System.currentTimeMillis() - last) < COOLDOWN_MS;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!enabled) return;
        if (event.getFrom().getBlockY() == event.getTo().getBlockY()) return;

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;
        if (player.isDead()) return;

        if (plugin.isHooked() && !plugin.isInFight(player)) return;

        String kitName = plugin.isHooked() ? plugin.getKitName(player) : null;
        int voidLevel = getVoidLevelForKit(kitName);

        if (player.getLocation().getY() > voidLevel) return;

        UUID uuid = player.getUniqueId();
        if (isOnCooldown(uuid)) return;

        cooldowns.put(uuid, System.currentTimeMillis());

        if (plugin.isHooked() && kitName != null && respawnKits.contains(kitName)) {
            plugin.respawnInFight(player);
            return;
        }

        player.setHealth(0);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        EntityDamageEvent.DamageCause cause = event.getCause();

        if (cause != EntityDamageEvent.DamageCause.VOID && cause != EntityDamageEvent.DamageCause.FALL) return;

        if (isOnCooldown(player.getUniqueId())) {
            event.setCancelled(true);
            player.setFallDistance(0);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (isOnCooldown(event.getEntity().getUniqueId())) {
            event.setDeathMessage(null);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cooldowns.remove(event.getPlayer().getUniqueId());
    }
}
