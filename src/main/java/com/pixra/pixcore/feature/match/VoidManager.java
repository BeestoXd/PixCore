package com.pixra.pixcore.feature.match;

import com.pixra.pixcore.PixCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class VoidManager implements Listener {

    private static final long COOLDOWN_MS = 5000;
    private static final long RESPAWN_KIT_COOLDOWN_MS = 1000;

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

    private boolean isOnRespawnKitCooldown(UUID uuid) {
        Long last = cooldowns.get(uuid);
        return last != null && (System.currentTimeMillis() - last) < RESPAWN_KIT_COOLDOWN_MS;
    }

    public boolean isRespawnKit(String kitName) {
        return kitName != null && respawnKits.contains(kitName);
    }

    public void clearCooldown(UUID uuid) {
        if (uuid != null) cooldowns.remove(uuid);
    }

    private boolean isTransitionProtected(UUID uuid) {
        return uuid != null && (plugin.frozenPlayers.contains(uuid)
                || plugin.roundTransitionPlayers.contains(uuid)
                || plugin.activeStartCountdownPlayers.contains(uuid));
    }

    private boolean rescueTransitionPlayer(Player player) {
        if (player == null) {
            return false;
        }

        UUID uuid = player.getUniqueId();
        Location arenaSpawn = plugin.arenaSpawnLocations.get(uuid);

        if (arenaSpawn != null) {
            player.teleport(arenaSpawn.clone());
            player.setFallDistance(0f);
            player.setFireTicks(0);
            // Only reapply kit during mid-match round transitions; calling respawnInFight
            // during the initial countdown gives the default/lobby kit instead of the fight kit.
            if (plugin.roundTransitionPlayers.contains(uuid)) {
                plugin.respawnInFight(player);
            }
            plugin.applySafeArenaTeleportProtection(player);
            cooldowns.put(uuid, System.currentTimeMillis());
            return true;
        }

        return false;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!enabled) return;

        Player player = event.getPlayer();
        Location destination = event.getTo();
        if (player.getGameMode() == GameMode.CREATIVE) return;
        if (player.isDead()) return;
        if (destination == null) return;

        if (plugin.respawnManager != null) {
            plugin.respawnManager.releasePostRespawnProtectionIfMoved(player, destination);
        }
        if (event.getFrom().getBlockY() == destination.getBlockY()) return;

        if (plugin.isHooked() && !plugin.isInFight(player)) return;

        String kitName = plugin.isHooked() ? plugin.getKitName(player) : null;
        int voidLevel = getVoidLevelForKit(kitName);

        if (destination.getY() > voidLevel) return;

        UUID uuid = player.getUniqueId();
        if (plugin.activeCountdowns.containsKey(uuid)) return;
        if (plugin.respawnManager != null && plugin.respawnManager.isRespawning(uuid)) return;
        if (isTransitionProtected(uuid) && rescueTransitionPlayer(player)) return;
        if (plugin.frozenPlayers.contains(uuid)) return;

        Player voidAttacker = null;
        {
            UUID killerUUID = plugin.lastDamager.get(uuid);
            Long dmgTime = plugin.lastDamageTime.get(uuid);
            if (killerUUID != null && dmgTime != null && (System.currentTimeMillis() - dmgTime) < 10000) {
                Player kp = Bukkit.getPlayer(killerUUID);
                if (kp != null && !kp.getUniqueId().equals(uuid)) voidAttacker = kp;
            }
        }

        if (plugin.isHooked() && kitName != null && respawnKits.contains(kitName)) {
            // Jika bed sudah hancur, jangan respawn — biarkan match berakhir via normal death
            if (plugin.bedFightBedBrokenPlayers.contains(uuid)) {
                plugin.bedFightBedBrokenPlayers.remove(uuid);
                // Fall through ke normal void death di bawah
            } else {
                if (!isOnRespawnKitCooldown(uuid)) {
                    cooldowns.put(uuid, System.currentTimeMillis());
                    respawnPlayerSafely(player, voidAttacker);
                }
                return;
            }
        }

        if (isOnCooldown(uuid)) return;

        cooldowns.put(uuid, System.currentTimeMillis());

        if (plugin.pearlFightManager != null && plugin.pearlFightManager.handleVoidFall(player)) {
            return;
        }

        if ((plugin.partySplitManager != null && plugin.partySplitManager.handleStickfightRoundLoss(player))
                || (plugin.partyVsPartyManager != null && plugin.partyVsPartyManager.handleStickfightRoundLoss(player))) {
            return;
        }

        broadcastVoidDeathMessages(player, voidAttacker);
        player.setLastDamageCause(new EntityDamageEvent(player, EntityDamageEvent.DamageCause.VOID, player.getHealth()));
        player.setHealth(0);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        EntityDamageEvent.DamageCause cause = event.getCause();

        if (cause != EntityDamageEvent.DamageCause.VOID && cause != EntityDamageEvent.DamageCause.FALL) return;
        UUID uuid = player.getUniqueId();

        if (isTransitionProtected(uuid)) {
            rescueTransitionPlayer(player);
            event.setCancelled(true);
            player.setFallDistance(0);
            return;
        }

        if (isOnCooldown(uuid)) {
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

    private void broadcastVoidDeathMessages(Player victim, Player attacker) {
        if (plugin.getMGetFight() == null) return;
        try {
            Object fight = plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), victim);
            if (fight == null) return;
            String vColor = plugin.getTeamColorCode(victim, fight);
            if (!vColor.equals("§c") && !vColor.equals("§9")) return;
            String victimName = vColor + plugin.getEffectivePlayerName(victim);

            String msg;
            if (attacker != null && attacker.isOnline()) {
                String aColor = plugin.getTeamColorCode(attacker, fight);
                String attackerName = aColor + plugin.getEffectivePlayerName(attacker);
                String rawKill = plugin.getMsg("death-void-kill");
                if (rawKill == null || rawKill.isEmpty()) return;
                msg = rawKill.replace("<attacker>", attackerName).replace("<killer>", attackerName).replace("<victim>", victimName);
                org.bukkit.Sound orbSound = plugin.getSoundByName("ENTITY_EXPERIENCE_ORB_PICKUP");
                if (orbSound == null) orbSound = plugin.getSoundByName("ORB_PICKUP");
                if (orbSound != null) {
                    try { attacker.playSound(attacker.getLocation(), orbSound, 1.0f, 1.0f); } catch (Exception ignored) {}
                }
            } else {
                String rawSelf = plugin.getMsg("death-void-self");
                if (rawSelf == null || rawSelf.isEmpty()) return;
                msg = rawSelf.replace("<victim>", victimName);
            }

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!plugin.isInFight(p)) continue;
                try {
                    Object pFight = plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), p);
                    if (pFight != null && pFight.equals(fight)) p.sendMessage(msg);
                } catch (Exception ignored) {}
            }
            plugin.lastBroadcastMessage.put(victim.getUniqueId(), msg);
            plugin.lastBroadcastTime.put(victim.getUniqueId(), System.currentTimeMillis());
        } catch (Exception ignored) {}
    }

    private void recordVoidKill(Player victim, Player killer) {
        if (victim == null) {
            return;
        }

        UUID victimUuid = victim.getUniqueId();
        if (plugin.killCountCooldown.containsKey(victimUuid)
                && System.currentTimeMillis() - plugin.killCountCooldown.get(victimUuid) < 2000L) {
            plugin.lastDamager.remove(victimUuid);
            plugin.lastDamageTime.remove(victimUuid);
            return;
        }
        plugin.killCountCooldown.put(victimUuid, System.currentTimeMillis());

        if (killer != null && !killer.getUniqueId().equals(victimUuid)) {
            plugin.playerMatchKills.put(killer.getUniqueId(),
                    plugin.playerMatchKills.getOrDefault(killer.getUniqueId(), 0) + 1);

            if (plugin.leaderboardManager != null) {
                String kitName = plugin.getKitName(killer);
                if (kitName != null) {
                    plugin.leaderboardManager.addKill(killer.getUniqueId(), plugin.getRealPlayerName(killer), kitName);
                }
            }
        }

        plugin.lastDamager.remove(victimUuid);
        plugin.lastDamageTime.remove(victimUuid);
    }

    private void respawnPlayerSafely(Player player, Player attacker) {
        if (player == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        recordVoidKill(player, attacker);
        broadcastVoidDeathMessages(player, attacker);
        Location arenaSpawn = plugin.arenaSpawnLocations.get(uuid);
        String kitName = plugin.isHooked() ? plugin.getKitName(player) : null;

        boolean isInstant = plugin.instantRespawnKits != null && kitName != null
                && plugin.instantRespawnKits.stream().anyMatch(k -> k.equalsIgnoreCase(kitName));
        int respawnTime = isInstant ? 0 : plugin.getConfig().getInt("settings.default-respawn-time", 3);

        if (respawnTime <= 0 || plugin.respawnManager == null || plugin.activeCountdowns.containsKey(uuid)) {
            if (plugin.partyFFAManager != null) {
                plugin.partyFFAManager.startRespawnRedirect(player);
            }
            plugin.respawnInFight(player);
            plugin.syncLayoutInstant(player, 4);
            if (arenaSpawn != null) {
                Location spawn = arenaSpawn.clone();
                player.teleport(spawn);
                player.setFallDistance(0f);
                player.setFireTicks(0);
                plugin.applySafeArenaTeleportProtection(player, false);
            } else {
                Location safeRespawn = null;
                if (plugin.respawnManager != null) {
                    safeRespawn = plugin.respawnManager.getSafeRespawnLocation(player, null);
                }
                if (safeRespawn == null && player.getWorld() != null) {
                    safeRespawn = plugin.resolveSafeArenaLocation(player.getWorld().getSpawnLocation());
                }
                if (safeRespawn != null) {
                    if (plugin.respawnManager != null) {
                        plugin.respawnManager.protectPlayerAfterRespawn(player, safeRespawn);
                    } else {
                        plugin.teleportToSafeArenaLocation(player, safeRespawn);
                        plugin.applySafeArenaTeleportProtection(player, false);
                    }
                }
            }
            return;
        }

        Location spawnLocation = arenaSpawn != null ? arenaSpawn.clone() : player.getLocation().clone();
        plugin.respawnManager.startRespawn(player, spawnLocation);
        player.setHealth(player.getMaxHealth());
        player.setFallDistance(0);
        player.setFoodLevel(20);
        player.setFireTicks(0);

        BukkitTask task = new BukkitRunnable() {
            int timeLeft = respawnTime;

            @Override
            public void run() {
                if (!player.isOnline() || !plugin.isInFight(player)
                        || plugin.leavingMatchPlayers.contains(uuid)) {
                    plugin.activeCountdowns.remove(uuid);
                    plugin.sendTitle(player, "", "", 0, 0, 0);
                    plugin.respawnManager.forceStop(player);
                    cancel();
                    return;
                }
                if (timeLeft <= 0) {
                    plugin.sendTitle(player, "", "", 0, 0, 0);
                    player.setHealth(player.getMaxHealth());
                    player.setFallDistance(0);
                    player.setFoodLevel(20);
                    plugin.activeCountdowns.remove(uuid);
                    plugin.respawnManager.finishRespawn(player);
                    cancel();
                    return;
                }
                String subtitle = plugin.getMsg("death.subtitle-countdown")
                        .replace("<seconds>", String.valueOf(timeLeft));
                plugin.sendTitle(player, plugin.getMsg("death.title"), subtitle, 0, 25, 10);
                timeLeft--;
            }
        }.runTaskTimer(plugin, 0L, plugin.respawnCountdownInterval);

        plugin.activeCountdowns.put(uuid, task);
    }
}
