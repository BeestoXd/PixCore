package com.pixra.pixcore.feature.match;

import com.pixra.pixcore.PixCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PearlFightManager implements Listener {

    private static final double PEARL_FIGHT_MAX_HEALTH = 6.0D;
    private static final double DEFAULT_MAX_HEALTH = 20.0D;

    private final PixCore plugin;
    private final Map<UUID, Integer> lives = new HashMap<>();
    private final Map<Object, Set<UUID>> fightPlayers = new HashMap<>();
    private final Map<UUID, Object> playerFights = new HashMap<>();

    public PearlFightManager(PixCore plugin) {
        this.plugin = plugin;
    }

    public boolean isPearlFightKit(String kitName) {
        if (kitName == null) {
            return false;
        }
        String normalized = kitName.toLowerCase();
        return normalized.contains("pearlfight");
    }

    public int getStartingLives() {
        return plugin.getConfig().getInt("settings.pearlfight.lives", 3);
    }

    public int getRespawnTime() {
        return plugin.getConfig().getInt("settings.pearlfight.respawn-time", 3);
    }

    public void clearAll() {
        for (UUID uuid : new HashSet<>(playerFights.keySet())) {
            restoreDefaultHealth(Bukkit.getPlayer(uuid));
        }
        lives.clear();
        fightPlayers.clear();
        playerFights.clear();
    }

    public void clearPlayer(UUID uuid) {
        if (uuid == null) {
            return;
        }
        lives.remove(uuid);
        playerFights.remove(uuid);
        fightPlayers.values().forEach(players -> {
            if (players != null) {
                players.remove(uuid);
            }
        });
        fightPlayers.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isEmpty());
        restoreDefaultHealth(Bukkit.getPlayer(uuid));
    }

    public void onFightStart(Object fight, List<Player> players, String kitName) {
        if (!isPearlFightKit(kitName) || fight == null || players == null || players.isEmpty()) {
            return;
        }

        Set<UUID> trackedPlayers = new HashSet<>();
        for (Player player : players) {
            if (player == null) {
                continue;
            }
            trackedPlayers.add(player.getUniqueId());
            lives.put(player.getUniqueId(), getStartingLives());
            playerFights.put(player.getUniqueId(), fight);
            applyPearlFightHealth(player);
            scheduleHealthRefresh(player);
        }

        if (!trackedPlayers.isEmpty()) {
            fightPlayers.put(fight, trackedPlayers);
        }
    }

    public void onFightEnd(Object fight, Iterable<Player> players) {
        Set<UUID> tracked = fight != null ? fightPlayers.remove(fight) : null;
        if (tracked != null) {
            for (UUID uuid : tracked) {
                lives.remove(uuid);
                playerFights.remove(uuid);
                restoreDefaultHealth(Bukkit.getPlayer(uuid));
            }
        }

        if (players != null) {
            for (Player player : players) {
                if (player != null) {
                    lives.remove(player.getUniqueId());
                    playerFights.remove(player.getUniqueId());
                    restoreDefaultHealth(player);
                }
            }
        }
    }

    public int getLives(UUID uuid) {
        if (uuid == null) {
            return 0;
        }
        return lives.getOrDefault(uuid, getStartingLives());
    }

    public int getLives(Player player) {
        return player != null ? getLives(player.getUniqueId()) : 0;
    }

    public int getOpponentLives(Player player, Object fight) {
        Player opponent = getOpponent(player, fight);
        return opponent != null ? getLives(opponent.getUniqueId()) : getStartingLives();
    }

    public boolean isTracked(Player player) {
        return player != null && (lives.containsKey(player.getUniqueId()) || playerFights.containsKey(player.getUniqueId()));
    }

    public Object getTrackedFight(Player player) {
        if (player == null) {
            return null;
        }

        Object liveFight = resolveCurrentFight(player);
        if (liveFight != null) {
            playerFights.put(player.getUniqueId(), liveFight);
            return liveFight;
        }

        return playerFights.get(player.getUniqueId());
    }

    public boolean isPearlFight(Player player) {
        if (player == null || !plugin.isHooked()) {
            return false;
        }
        return isPearlFightKit(plugin.getKitName(player)) || isTracked(player);
    }

    public Player getOpponent(Player player, Object fight) {
        if (player == null) {
            return null;
        }

        if (fight == null) {
            fight = getTrackedFight(player);
        }
        if (fight == null) {
            return null;
        }

        List<Player> players = resolveFightPlayers(fight);
        for (Player other : players) {
            if (other != null && !other.getUniqueId().equals(player.getUniqueId())) {
                return other;
            }
        }
        return null;
    }

    public boolean shouldDisableDamage(Player victim, Player attacker) {
        if (victim == null || attacker == null || victim.getUniqueId().equals(attacker.getUniqueId())) {
            return false;
        }
        if (!plugin.isHooked() || !plugin.isInFight(victim) || !plugin.isInFight(attacker)) {
            return false;
        }

        String victimKit = plugin.getKitName(victim);
        String attackerKit = plugin.getKitName(attacker);
        if (!isPearlFightKit(victimKit) || !isPearlFightKit(attackerKit)) {
            return false;
        }

        try {
            if (plugin.getMGetFight() == null || plugin.getStrikePracticeAPI() == null) {
                return false;
            }
            Object victimFight = plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), victim);
            Object attackerFight = plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), attacker);
            return victimFight != null && victimFight.equals(attackerFight);
        } catch (Exception ignored) {}

        return false;
    }

    public boolean handleVoidFall(Player player) {
        if (player == null || !plugin.isHooked() || !plugin.isInFight(player)) {
            return false;
        }

        if (!isPearlFight(player)) {
            return false;
        }

        Object fight = getTrackedFight(player);
        Player killer = resolveKiller(player);

        int livesLeft = Math.max(0, getLives(player) - 1);
        lives.put(player.getUniqueId(), livesLeft);
        syncFightPlayers(fight);
        playVoidKillSound(killer);
        broadcastVoidDeathMessage(player, killer, fight);
        recordVoidKill(player, killer);

        if (livesLeft <= 0) {
            Player winner = getOpponent(player, fight);
            if (winner == null) {
                winner = killer;
            }
            primeBestOfForMatchEnd(fight, winner != null ? winner.getUniqueId() : null);
            eliminatePlayer(player, fight);
            return true;
        }

        broadcastLivesLeft(fight, player, livesLeft);
        startRespawnCountdown(player, fight);
        return true;
    }

    private void broadcastLivesLeft(Object fight, Player player, int livesLeft) {
        String teamColor = plugin.getTeamColorCode(player, fight);
        if (teamColor == null || teamColor.isEmpty()) {
            teamColor = ChatColor.GREEN.toString();
        }
        String message = teamColor + String.valueOf(livesLeft) + " lives left";

        List<Player> viewers = resolveFightPlayers(fight);
        if (viewers.isEmpty()) {
            viewers.add(player);
            Player opponent = getOpponent(player, fight);
            if (opponent != null) {
                viewers.add(opponent);
            }
        }

        for (Player viewer : viewers) {
            if (viewer != null && viewer.isOnline()) {
                viewer.sendMessage(message);
            }
        }
    }

    private void startRespawnCountdown(Player player, Object fight) {
        UUID uuid = player.getUniqueId();
        if (plugin.activeCountdowns.containsKey(uuid) || plugin.respawnManager == null) {
            return;
        }

        plugin.frozenPlayers.remove(uuid);
        plugin.roundTransitionPlayers.remove(uuid);

        Location arenaSpawn = plugin.arenaSpawnLocations.get(uuid);
        if (arenaSpawn == null) {
            arenaSpawn = player.getLocation().clone();
        }

        plugin.respawnManager.startRespawn(player, arenaSpawn);

        int respawnTime = getRespawnTime();
        BukkitTask task = new BukkitRunnable() {
            int timeLeft = respawnTime;

            @Override
            public void run() {
                if (!player.isOnline() || !plugin.isInFight(player)
                        || plugin.leavingMatchPlayers.contains(uuid)
                        || !isTracked(player)) {
                    plugin.activeCountdowns.remove(uuid);
                    plugin.sendTitle(player, "", "", 0, 0, 0);
                    plugin.respawnManager.forceStop(player);
                    cancel();
                    return;
                }

                if (timeLeft <= 0) {
                    plugin.sendTitle(player, "", "", 0, 0, 0);
                    applyPearlFightHealth(player);
                    player.setHealth(player.getMaxHealth());
                    player.setFallDistance(0);
                    player.setFoodLevel(20);
                    plugin.activeCountdowns.remove(uuid);
                    plugin.respawnManager.finishRespawn(player);
                    scheduleHealthRefresh(player);
                    syncFightPlayers(fight);
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

    private List<Player> resolveFightPlayers(Object fight) {
        List<Player> players = new ArrayList<>();
        if (fight == null) {
            return players;
        }

        Set<UUID> tracked = fightPlayers.get(fight);
        if (tracked != null) {
            for (UUID uuid : tracked) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    players.add(player);
                }
            }
        }

        if (!players.isEmpty()) {
            return players;
        }

        try {
            if (plugin.getMGetPlayersInFight() != null) {
                @SuppressWarnings("unchecked")
                List<Player> fightList = (List<Player>) plugin.getMGetPlayersInFight().invoke(fight);
                if (fightList != null) {
                    players.addAll(fightList);
                }
            }
        } catch (Exception ignored) {}

        if (!players.isEmpty()) {
            return players;
        }

        try {
            if (plugin.getMGetPlayers() != null) {
                @SuppressWarnings("unchecked")
                List<Player> fightList = (List<Player>) plugin.getMGetPlayers().invoke(fight);
                if (fightList != null) {
                    players.addAll(fightList);
                }
            }
        } catch (Exception ignored) {}

        return players;
    }

    private Object resolveCurrentFight(Player player) {
        try {
            if (plugin.getMGetFight() != null && plugin.getStrikePracticeAPI() != null) {
                return plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), player);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void syncFightPlayers(Object fight) {
        for (Player player : resolveFightPlayers(fight)) {
            if (player != null && player.isOnline()) {
                if (isTracked(player)) {
                    applyPearlFightHealth(player);
                }
                plugin.syncLayoutInstant(player, 2);
            }
        }
    }

    private void scheduleHealthRefresh(Player player) {
        if (player == null) {
            return;
        }

        for (long delay : new long[] { 1L, 5L, 20L }) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline() && isTracked(player)) {
                        applyPearlFightHealth(player);
                    }
                }
            }.runTaskLater(plugin, delay);
        }
    }

    private void applyPearlFightHealth(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return;
        }

        try {
            if (player.getMaxHealth() != PEARL_FIGHT_MAX_HEALTH) {
                player.setMaxHealth(PEARL_FIGHT_MAX_HEALTH);
            }
            player.setHealth(PEARL_FIGHT_MAX_HEALTH);
        } catch (Exception ignored) {}
    }

    private void restoreDefaultHealth(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        try {
            if (player.getMaxHealth() != DEFAULT_MAX_HEALTH) {
                player.setMaxHealth(DEFAULT_MAX_HEALTH);
            }
            if (!player.isDead()) {
                player.setHealth(Math.min(player.getMaxHealth(), DEFAULT_MAX_HEALTH));
            }
        } catch (Exception ignored) {}
    }

    private void eliminatePlayer(Player player, Object fight) {
        plugin.frozenPlayers.remove(player.getUniqueId());
        plugin.roundTransitionPlayers.remove(player.getUniqueId());

        if (fight != null && plugin.getMHandleDeath() != null) {
            try {
                plugin.getMHandleDeath().invoke(fight, player);
                return;
            } catch (Exception ignored) {}
        }

        try {
            player.setHealth(0.0D);
        } catch (Exception ignored) {}
    }

    private Player resolveKiller(Player victim) {
        if (victim == null) {
            return null;
        }

        Player killer = victim.getKiller();
        if (killer == null) {
            UUID killerUUID = plugin.lastDamager.get(victim.getUniqueId());
            Long time = plugin.lastDamageTime.get(victim.getUniqueId());
            if (killerUUID != null && time != null && (System.currentTimeMillis() - time) < 10000L) {
                killer = Bukkit.getPlayer(killerUUID);
            }
        }

        if (killer != null && killer.getUniqueId().equals(victim.getUniqueId())) {
            killer = null;
        }
        return killer;
    }

    private void playVoidKillSound(Player killer) {
        if (killer == null || !plugin.soundEnabled || plugin.respawnSound == null) {
            return;
        }

        try {
            killer.playSound(killer.getLocation(), plugin.respawnSound, 1.0f, 1.0f);
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

        if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
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

    private void broadcastVoidDeathMessage(Player victim, Player killer, Object fight) {
        if (victim == null) {
            return;
        }

        String victimColor = plugin.getTeamColorCode(victim, fight);
        if (victimColor == null || victimColor.isEmpty()) {
            victimColor = ChatColor.RED.toString();
        }

        String killerColor = "";
        if (killer != null) {
            killerColor = plugin.getTeamColorCode(killer, fight);
            if (killerColor == null || killerColor.isEmpty()) {
                killerColor = ChatColor.GREEN.toString();
            }
        }

        String victimName = victimColor + victim.getName();
        String message;
        if (killer != null) {
            String killerName = killerColor + killer.getName();
            message = plugin.getMsg("death-void-kill")
                    .replace("<victim>", victimName)
                    .replace("<killer>", killerName);
        } else {
            message = plugin.getMsg("death-void-self").replace("<victim>", victimName);
        }

        if (message == null || message.isEmpty()) {
            return;
        }

        List<Player> viewers = resolveFightPlayers(fight);
        if (viewers.isEmpty()) {
            viewers.add(victim);
            Player opponent = getOpponent(victim, fight);
            if (opponent != null) {
                viewers.add(opponent);
            }
        }

        for (Player viewer : viewers) {
            if (viewer != null && viewer.isOnline()) {
                viewer.sendMessage(message);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void primeBestOfForMatchEnd(Object fight, UUID winnerUuid) {
        if (fight == null || winnerUuid == null) {
            return;
        }

        try {
            Object bestOf = fight.getClass().getMethod("getBestOf").invoke(fight);
            if (bestOf == null) {
                return;
            }

            int rounds = ((Number) bestOf.getClass().getMethod("getRounds").invoke(bestOf)).intValue();
            if (rounds <= 1) {
                return;
            }

            int winsNeeded = rounds / 2 + 1;
            Map<UUID, Integer> wonMap = (Map<UUID, Integer>) bestOf.getClass()
                    .getMethod("getRoundsWon").invoke(bestOf);
            int currentWins = wonMap.getOrDefault(winnerUuid, 0);
            int toAward = winsNeeded - 1 - currentWins;

            for (int i = 0; i < toAward && i < 20; i++) {
                bestOf.getClass().getMethod("handleWin", UUID.class).invoke(bestOf, winnerUuid);
            }
        } catch (Exception ignored) {}
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        clearPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearPlayer(event.getPlayer().getUniqueId());
    }
}
