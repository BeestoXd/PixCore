package com.pixra.pixCore.duels;

import com.pixra.pixCore.PixCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DuelScoreManager implements Listener {

    private final PixCore plugin;
    private Method getDuelRequests;
    private Method getFightFromRequest;

    private final Set<Object> knownRequestFights = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<Object> activeRequestFights = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final Map<String, Map<UUID, Integer>> duelScores = new ConcurrentHashMap<>();

    private final Map<String, Long> lastActivity = new ConcurrentHashMap<>();

    private final Map<UUID, UUID> lastOpponentMap = new ConcurrentHashMap<>();
    private final Set<UUID> recentDuelParticipants = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public DuelScoreManager(PixCore plugin) {
        this.plugin = plugin;
        setupReflection();
        startScannerTask();
        startCleanupTask();
    }

    private void setupReflection() {
        try {
            Class<?> fightReqClass = Class.forName("ga.strikepractice.fights.requests.FightRequest");
            getDuelRequests = fightReqClass.getMethod("getDuelRequestsForPlayer", Player.class);
            getFightFromRequest = fightReqClass.getMethod("getFight");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to hook into FightRequest for DuelScoreManager!");
        }
    }

    private void startScannerTask() {
        if (getDuelRequests == null || getFightFromRequest == null) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    try {
                        Collection<?> requests = (Collection<?>) getDuelRequests.invoke(null, p);
                        if (requests != null && !requests.isEmpty()) {
                            for (Object req : requests) {
                                Object fight = getFightFromRequest.invoke(req);
                                if (fight != null) {
                                    knownRequestFights.add(fight);
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        }.runTaskTimerAsynchronously(plugin, 10L, 10L);
    }

    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                long expirationTime = 10 * 60 * 1000L;

                Iterator<Map.Entry<String, Long>> iterator = lastActivity.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, Long> entry = iterator.next();
                    if (now - entry.getValue() > expirationTime) {
                        String key = entry.getKey();
                        duelScores.remove(key);
                        iterator.remove();
                    }
                }
            }
        }.runTaskTimerAsynchronously(plugin, 1200L, 1200L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandPreProcess(PlayerCommandPreprocessEvent event) {
        String cmd = event.getMessage().toLowerCase();
        if (cmd.startsWith("/duel accept") || cmd.startsWith("/accept") || cmd.startsWith("/fight accept")) {
            if (getDuelRequests == null || getFightFromRequest == null) return;
            try {
                Collection<?> requests = (Collection<?>) getDuelRequests.invoke(null, event.getPlayer());
                if (requests != null && !requests.isEmpty()) {
                    for (Object req : requests) {
                        Object fight = getFightFromRequest.invoke(req);
                        if (fight != null) {
                            knownRequestFights.add(fight);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    public void onFightStart(Object fight) {
        if (knownRequestFights.contains(fight)) {
            activeRequestFights.add(fight);
            knownRequestFights.remove(fight);
        }
    }

    public boolean isDuelRequestFight(Object fight) {
        return activeRequestFights.contains(fight);
    }

    public void onFightEnd(Object fight, Player winner, Player loser) {
        if (!activeRequestFights.contains(fight)) return;

        if (winner != null && loser != null) {
            String key = getPairKey(winner.getUniqueId(), loser.getUniqueId());

            checkExpiration(key);

            duelScores.putIfAbsent(key, new HashMap<>());
            Map<UUID, Integer> scores = duelScores.get(key);
            scores.put(winner.getUniqueId(), scores.getOrDefault(winner.getUniqueId(), 0) + 1);

            lastActivity.put(key, System.currentTimeMillis());

            recentDuelParticipants.add(winner.getUniqueId());
            recentDuelParticipants.add(loser.getUniqueId());

            lastOpponentMap.put(winner.getUniqueId(), loser.getUniqueId());
            lastOpponentMap.put(loser.getUniqueId(), winner.getUniqueId());
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            activeRequestFights.remove(fight);

            if (winner != null) recentDuelParticipants.remove(winner.getUniqueId());
            if (loser != null) recentDuelParticipants.remove(loser.getUniqueId());
        }, 160L);
    }

    public boolean hasRecentDuel(UUID uuid) {
        return recentDuelParticipants.contains(uuid);
    }

    public UUID getLastOpponent(UUID uuid) {
        return lastOpponentMap.get(uuid);
    }

    private void checkExpiration(String key) {
        if (lastActivity.containsKey(key)) {
            long now = System.currentTimeMillis();
            long expirationTime = 10 * 60 * 1000L;
            if (now - lastActivity.get(key) > expirationTime) {
                duelScores.remove(key);
                lastActivity.remove(key);
            }
        }
    }

    public String getPairKey(UUID u1, UUID u2) {
        if (u1.compareTo(u2) < 0) {
            return u1.toString() + "_" + u2.toString();
        } else {
            return u2.toString() + "_" + u1.toString();
        }
    }

    public int getScore(UUID target, UUID opponent) {
        String key = getPairKey(target, opponent);

        checkExpiration(key);

        if (!duelScores.containsKey(key)) return 0;
        return duelScores.get(key).getOrDefault(target, 0);
    }
}