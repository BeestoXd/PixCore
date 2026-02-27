package com.pixra.pixCore.managers;

import com.pixra.pixCore.PixCore;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

public class LeaderboardManager {

    private final PixCore plugin;
    private File dataFile;
    private FileConfiguration dataConfig;

    private String currentDailyDate;
    private String currentWeeklyDate;
    private String currentMonthlyDate;

    private boolean globalEnabled = true;
    private List<String> disabledKits = new ArrayList<>();

    public LeaderboardManager(PixCore plugin) {
        this.plugin = plugin;
        loadData();
        checkResets();
    }

    public void reload() {
        loadData();
        checkResets();
    }

    private void loadData() {
        File folder = new File(plugin.getDataFolder(), "playerdata");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        dataFile = new File(folder, "leaderboard.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        LocalDate today = LocalDate.now();
        LocalDate startOfWeek = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));

        currentDailyDate = dataConfig.getString("reset-tracking.daily", today.toString());
        currentWeeklyDate = dataConfig.getString("reset-tracking.weekly", startOfWeek.toString());
        currentMonthlyDate = dataConfig.getString("reset-tracking.monthly", YearMonth.now().toString());

        globalEnabled = dataConfig.getBoolean("settings.global-enabled", true);
        disabledKits = dataConfig.getStringList("settings.disabled-kits");
        if (disabledKits == null) {
            disabledKits = new ArrayList<>();
        }
    }

    public void saveData() {
        try {
            dataConfig.set("reset-tracking.daily", currentDailyDate);
            dataConfig.set("reset-tracking.weekly", currentWeeklyDate);
            dataConfig.set("reset-tracking.monthly", currentMonthlyDate);
            dataConfig.set("settings.global-enabled", globalEnabled);
            dataConfig.set("settings.disabled-kits", disabledKits);
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setGlobalEnabled(boolean enabled) {
        this.globalEnabled = enabled;
        saveData();
    }

    public void setKitEnabled(String kitName, boolean enabled) {
        kitName = ChatColor.stripColor(kitName).toLowerCase();
        if (enabled) {
            disabledKits.remove(kitName);
        } else {
            if (!disabledKits.contains(kitName)) {
                disabledKits.add(kitName);
            }
        }
        saveData();
    }

    public boolean isKitEnabled(String kitName) {
        if (!globalEnabled) return false;
        if (kitName == null || kitName.isEmpty()) return false;
        kitName = ChatColor.stripColor(kitName).toLowerCase();
        return !disabledKits.contains(kitName);
    }

    private void checkResets() {
        LocalDate today = LocalDate.now();
        YearMonth thisMonth = YearMonth.now();
        LocalDate startOfWeek = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));

        boolean changed = false;

        if (!today.toString().equals(currentDailyDate)) {
            dataConfig.set("stats.daily", null);
            currentDailyDate = today.toString();
            plugin.getLogger().info("Daily Winstreak Leaderboard has been reset.");
            changed = true;
        }

        if (!startOfWeek.toString().equals(currentWeeklyDate)) {
            dataConfig.set("stats.weekly", null);
            currentWeeklyDate = startOfWeek.toString();
            plugin.getLogger().info("Weekly Winstreak Leaderboard has been reset.");
            changed = true;
        }

        if (!thisMonth.toString().equals(currentMonthlyDate)) {
            dataConfig.set("stats.monthly", null);
            currentMonthlyDate = thisMonth.toString();
            plugin.getLogger().info("Monthly Winstreak Leaderboard has been reset.");
            changed = true;
        }

        if (changed) saveData();
    }

    public void addWin(UUID playerUUID, String playerName, String kitName) {
        if (!isKitEnabled(kitName)) return;

        checkResets();

        if (kitName == null || kitName.isEmpty()) return;
        kitName = ChatColor.stripColor(kitName).toLowerCase();

        dataConfig.set("names." + playerUUID.toString(), playerName);

        String dailyPath = "stats.daily." + kitName + "." + playerUUID.toString();
        dataConfig.set(dailyPath, dataConfig.getInt(dailyPath, 0) + 1);

        String weeklyPath = "stats.weekly." + kitName + "." + playerUUID.toString();
        dataConfig.set(weeklyPath, dataConfig.getInt(weeklyPath, 0) + 1);

        String monthlyPath = "stats.monthly." + kitName + "." + playerUUID.toString();
        dataConfig.set(monthlyPath, dataConfig.getInt(monthlyPath, 0) + 1);

        saveData();
    }

    public void resetStreak(UUID playerUUID, String kitName) {
        if (!isKitEnabled(kitName)) return;

        checkResets();

        if (kitName == null || kitName.isEmpty()) return;
        kitName = ChatColor.stripColor(kitName).toLowerCase();

        dataConfig.set("stats.daily." + kitName + "." + playerUUID.toString(), 0);
        dataConfig.set("stats.weekly." + kitName + "." + playerUUID.toString(), 0);
        dataConfig.set("stats.monthly." + kitName + "." + playerUUID.toString(), 0);

        saveData();
    }

    public String getHighestWinstreakString(UUID playerUUID) {
        checkResets();

        ConfigurationSection dailySection = dataConfig.getConfigurationSection("stats.daily");
        if (dailySection == null) return "0";

        int maxScore = 0;
        String bestKit = "";

        for (String kit : dailySection.getKeys(false)) {
            if (!isKitEnabled(kit)) continue;

            int score = dataConfig.getInt("stats.daily." + kit + "." + playerUUID.toString(), 0);
            if (score > maxScore) {
                maxScore = score;
                bestKit = kit;
            }
        }

        if (maxScore > 0) {
            String formattedKit = bestKit.substring(0, 1).toUpperCase() + bestKit.substring(1).toLowerCase();
            return maxScore + " (" + formattedKit + ")";
        }

        return "0";
    }

    public List<Map.Entry<String, Integer>> getTop5(String period, String kitName) {
        return getTop(period, kitName, 5);
    }

    public List<Map.Entry<String, Integer>> getTop(String period, String kitName, int limit) {
        checkResets();

        kitName = ChatColor.stripColor(kitName).toLowerCase();
        ConfigurationSection section = dataConfig.getConfigurationSection("stats." + period + "." + kitName);
        if (section == null) return new ArrayList<>();

        Map<String, Integer> scores = new HashMap<>();
        for (String uuidStr : section.getKeys(false)) {
            int score = section.getInt(uuidStr);
            if (score > 0) {
                String name = dataConfig.getString("names." + uuidStr, "Unknown");
                scores.put(name, score);
            }
        }

        return scores.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public String getDailyCountdown() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endOfDay = now.toLocalDate().atTime(23, 59, 59);
        long hours = ChronoUnit.HOURS.between(now, endOfDay);
        long mins = ChronoUnit.MINUTES.between(now, endOfDay) % 60;
        long secs = ChronoUnit.SECONDS.between(now, endOfDay) % 60;
        return hours + "h " + mins + "m " + secs + "s";
    }

    public String getWeeklyCountdown() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endOfWeek = now.toLocalDate().with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY)).atStartOfDay().minusSeconds(1);
        long days = ChronoUnit.DAYS.between(now, endOfWeek);
        long hours = ChronoUnit.HOURS.between(now, endOfWeek) % 24;
        long mins = ChronoUnit.MINUTES.between(now, endOfWeek) % 60;
        long secs = ChronoUnit.SECONDS.between(now, endOfWeek) % 60;
        return days + "d " + hours + "h " + mins + "m " + secs + "s";
    }

    public String getMonthlyCountdown() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endOfMonth = now.toLocalDate().withDayOfMonth(now.toLocalDate().lengthOfMonth()).atTime(23, 59, 59);
        long days = ChronoUnit.DAYS.between(now, endOfMonth);
        long hours = ChronoUnit.HOURS.between(now, endOfMonth) % 24;
        long mins = ChronoUnit.MINUTES.between(now, endOfMonth) % 60;
        long secs = ChronoUnit.SECONDS.between(now, endOfMonth) % 60;
        return days + "d " + hours + "h " + mins + "m " + secs + "s";
    }
}