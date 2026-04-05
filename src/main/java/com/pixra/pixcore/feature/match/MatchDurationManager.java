package com.pixra.pixcore.feature.match;

import com.pixra.pixcore.PixCore;
import ga.strikepractice.fights.MatchDurationLimit;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MatchDurationManager {

    private final PixCore plugin;
    private FileConfiguration config;

    private final Map<Object, FightTimer> activeTimers = new HashMap<>();

    public MatchDurationManager(PixCore plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        File file = new File(plugin.getDataFolder(), "matchduration.yml");
        if (!file.exists()) {
            plugin.saveResource("matchduration.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public boolean isEnabled() {
        return config.getBoolean("enabled", true);
    }

    private int getDurationForKit(String kitName) {
        if (kitName == null) return -1;
        String lower = kitName.toLowerCase();

        if (config.contains("kits." + lower)) {
            return config.getInt("kits." + lower, -1);
        }

        for (String key : config.getConfigurationSection("kits") != null
                ? config.getConfigurationSection("kits").getKeys(false)
                : new java.util.HashSet<String>()) {
            if (lower.contains(key.toLowerCase()) || key.toLowerCase().contains(lower)) {
                return config.getInt("kits." + key, -1);
            }
        }
        return -1;
    }

    public String getConfiguredTimeLeft(String kitName) {
        int duration = getDurationForKit(kitName);
        if (duration <= 0) return "";
        return String.format("%02d:%02d", duration / 60, duration % 60);
    }

    private int getEndingSoonThreshold() {
        return config.getInt("ending-soon-threshold", 60);
    }

    private Object findTrackedFightKey(Object fight) {
        if (fight == null) return null;
        if (activeTimers.containsKey(fight)) return fight;

        Set<UUID> targetPlayers = getFightPlayerIds(fight);
        if (targetPlayers.isEmpty()) return null;

        for (Map.Entry<Object, FightTimer> entry : activeTimers.entrySet()) {
            Set<UUID> trackedPlayers = getFightPlayerIds(entry.getKey());
            if (!trackedPlayers.isEmpty() && trackedPlayers.equals(targetPlayers)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public void startTimer(Object fight, String kitName) {
        if (!isEnabled()) return;
        if (fight == null || findTrackedFightKey(fight) != null) return;

        int duration = getDurationForKit(kitName);
        if (duration <= 0) return;

        FightTimer timer = new FightTimer(fight, duration);
        activeTimers.put(fight, timer);
        timer.onFightStart();
    }

    public void stopTimer(Object fight) {
        Object trackedFight = findTrackedFightKey(fight);
        FightTimer timer = trackedFight != null ? activeTimers.remove(trackedFight) : null;
        if (timer != null) timer.cancel();
    }

    public String getTimeLeft(Object fight) {
        if (fight == null) return "";
        Object trackedFight = findTrackedFightKey(fight);
        FightTimer timer = trackedFight != null ? activeTimers.get(trackedFight) : null;
        if (timer == null) return "";
        int secs = Math.max(0, timer.timeLeftSeconds);
        return String.format("%02d:%02d", secs / 60, secs % 60);
    }

    public boolean isEndingSoon(Object fight) {
        if (fight == null) return false;
        Object trackedFight = findTrackedFightKey(fight);
        FightTimer timer = trackedFight != null ? activeTimers.get(trackedFight) : null;
        if (timer == null) return false;
        return timer.isEndingSoon();
    }

    public boolean hasTimer(Object fight) {
        return findTrackedFightKey(fight) != null;
    }

    private class FightTimer implements MatchDurationLimit {

        private final Object fight;
        private volatile int timeLeftSeconds;
        private BukkitTask task;
        private final List<Integer> warningThresholds = new ArrayList<>();
        private boolean triggered = false;

        FightTimer(Object fight, int durationSeconds) {
            this.fight = fight;
            this.timeLeftSeconds = durationSeconds;

            List<?> warnings = config.getList("warnings");
            if (warnings != null) {
                for (Object w : warnings) {
                    if (w instanceof Map<?, ?> map) {
                        Object sec = map.get("seconds");
                        if (sec instanceof Number) {
                            warningThresholds.add(((Number) sec).intValue());
                        }
                    }
                }
            }
        }

        @Override
        public void onFightStart() {
            task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (triggered) {
                        cancel();
                        return;
                    }
                    timeLeftSeconds--;
                    sendWarningIfNeeded(timeLeftSeconds);

                    if (timeLeftSeconds <= 0) {
                        triggered = true;
                        cancel();
                        triggerDraw();
                    }
                }
            }.runTaskTimer(plugin, 20L, 20L);
        }

        @Override
        public boolean isEndingSoon() {
            return timeLeftSeconds <= getEndingSoonThreshold();
        }

        void cancel() {
            if (task != null) {
                task.cancel();
                task = null;
            }
        }

        private void sendWarningIfNeeded(int remaining) {
            List<?> warnings = config.getList("warnings");
            if (warnings == null) return;
            for (Object w : warnings) {
                if (w instanceof Map<?, ?> map) {
                    Object sec = map.get("seconds");
                    Object msg = map.get("message");
                    if (sec instanceof Number && msg instanceof String) {
                        if (((Number) sec).intValue() == remaining) {
                            String formatted = ChatColor.translateAlternateColorCodes('&', (String) msg);
                            broadcastToFight(fight, formatted);
                        }
                    }
                }
            }
        }

        private void triggerDraw() {
            List<Player> players = getFightPlayers(fight);
            plugin.drawFights.add(fight);

            String rawTitle    = config.getString("draw.title",    "&e&lDRAW!");
            String rawSubtitle = config.getString("draw.subtitle", "&fTime's up — no winner.");
            String rawMessage  = config.getString("draw.message",  "&e[Timer] &fTime limit reached! Match ended as a &eDRAW &f— streaks unchanged.");
            int fadeIn  = config.getInt("draw.fade-in", 10);
            int stay    = config.getInt("draw.stay",    60);
            int fadeOut = config.getInt("draw.fade-out", 20);

            String title    = ChatColor.translateAlternateColorCodes('&', rawTitle);
            String subtitle = ChatColor.translateAlternateColorCodes('&', rawSubtitle);
            String message  = ChatColor.translateAlternateColorCodes('&', rawMessage);

            for (Player p : players) {
                if (p != null && p.isOnline()) {
                    plugin.sendTitle(p, title, subtitle, fadeIn, stay, fadeOut);
                    p.sendMessage(message);
                }
            }

            if (!players.isEmpty()) {
                Player firstPlayer = players.get(0);
                if (firstPlayer != null && firstPlayer.isOnline()) {
                    if (players.size() > 1) {
                        Player secondPlayer = players.get(1);
                        if (secondPlayer != null) {
                            preFillBestOfScoreForDraw(fight, secondPlayer.getUniqueId());
                        }
                    }
                    try {
                        Method handleDeath = plugin.getMHandleDeath();
                        if (handleDeath != null) {
                            handleDeath.invoke(fight, firstPlayer);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("[MatchDurationManager] Could not end fight via handleDeath: " + e.getMessage());
                    }
                }
            }

            activeTimers.remove(fight);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Player> getFightPlayers(Object fight) {
        List<Player> players = new ArrayList<>();
        try {
            if (plugin.getMGetPlayersInFight() != null) {
                List<Player> found = (List<Player>) plugin.getMGetPlayersInFight().invoke(fight);
                if (found != null) players.addAll(found);
            }
        } catch (Exception ignored) {}

        if (players.isEmpty()) {
            try {
                if (plugin.getMGetPlayers() != null) {
                    List<Player> found = (List<Player>) plugin.getMGetPlayers().invoke(fight);
                    if (found != null) players.addAll(found);
                }
            } catch (Exception ignored) {}
        }

        if (players.isEmpty()) {
            try {
                if (plugin.getMGetFirstPlayer() != null) {
                    Player p1 = (Player) plugin.getMGetFirstPlayer().invoke(fight);
                    if (p1 != null) players.add(p1);
                }
                if (plugin.getMGetSecondPlayer() != null) {
                    Player p2 = (Player) plugin.getMGetSecondPlayer().invoke(fight);
                    if (p2 != null) players.add(p2);
                }
            } catch (Exception ignored) {}
        }

        players.removeIf(p -> p == null);
        return players;
    }

    private Set<UUID> getFightPlayerIds(Object fight) {
        Set<UUID> playerIds = new HashSet<>();
        for (Player player : getFightPlayers(fight)) {
            if (player != null) {
                playerIds.add(player.getUniqueId());
            }
        }
        return playerIds;
    }

    private void broadcastToFight(Object fight, String message) {
        for (Player p : getFightPlayers(fight)) {
            if (p.isOnline()) p.sendMessage(message);
        }
    }

    @SuppressWarnings("unchecked")
    private void preFillBestOfScoreForDraw(Object fight, UUID potentialWinnerUUID) {
        try {
            Object bestOf = fight.getClass().getMethod("getBestOf").invoke(fight);
            if (bestOf == null) return;
            int rounds = ((Number) bestOf.getClass().getMethod("getRounds").invoke(bestOf)).intValue();
            if (rounds <= 1) return;
            int winsNeeded = rounds / 2 + 1;
            Map<UUID, Integer> wonMap = (Map<UUID, Integer>) bestOf.getClass()
                    .getMethod("getRoundsWon").invoke(bestOf);
            int currentWins = wonMap.getOrDefault(potentialWinnerUUID, 0);
            int toAward = winsNeeded - 1 - currentWins;
            for (int i = 0; i < toAward && i < 20; i++) {
                bestOf.getClass().getMethod("handleWin", UUID.class).invoke(bestOf, potentialWinnerUUID);
            }
        } catch (Exception ignored) {}
    }

    public String getDrawTitle() {
        return config.getString("draw.title", "&e&lDRAW!");
    }

    public String getDrawSubtitle() {
        return config.getString("draw.subtitle", "&fTime's up — no winner.");
    }
}
