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
    private String currentMonthlyDate;

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
        
        currentDailyDate = dataConfig.getString("reset-tracking.daily", LocalDate.now().toString());
        currentMonthlyDate = dataConfig.getString("reset-tracking.monthly", YearMonth.now().toString());
    }

    public void saveData() {
        try {
            dataConfig.set("reset-tracking.daily", currentDailyDate);
            dataConfig.set("reset-tracking.monthly", currentMonthlyDate);
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void checkResets() {
        LocalDate today = LocalDate.now();
        YearMonth thisMonth = YearMonth.now();

        boolean changed = false;

        // Cek Reset Harian: Kosongkan semua data Winstreak hari ini
        if (!today.toString().equals(currentDailyDate)) {
            dataConfig.set("stats.daily", null);           
            currentDailyDate = today.toString();
            plugin.getLogger().info("Daily Winstreak Leaderboard has been reset.");
            changed = true;
        }

        // Cek Reset Bulanan: Kosongkan semua data Winstreak bulan ini
        if (!thisMonth.toString().equals(currentMonthlyDate)) {
            dataConfig.set("stats.monthly", null);         
            currentMonthlyDate = thisMonth.toString();
            plugin.getLogger().info("Monthly Winstreak Leaderboard has been reset.");
            changed = true;
        }

        if (changed) saveData();
    }

    public void addWin(UUID playerUUID, String playerName, String kitName) {
        checkResets(); // Pastikan data tidak tertinggal saat pergantian hari

        if (kitName == null || kitName.isEmpty()) return;
        kitName = ChatColor.stripColor(kitName).toLowerCase();

        // Update nama player di database
        dataConfig.set("names." + playerUUID.toString(), playerName);

        // --- TAMBAH DAILY STREAK BERJALAN ---
        String dailyPath = "stats.daily." + kitName + "." + playerUUID.toString();
        dataConfig.set(dailyPath, dataConfig.getInt(dailyPath, 0) + 1);

        // --- TAMBAH MONTHLY STREAK BERJALAN ---
        String monthlyPath = "stats.monthly." + kitName + "." + playerUUID.toString();
        dataConfig.set(monthlyPath, dataConfig.getInt(monthlyPath, 0) + 1);

        saveData();
    }

    public void resetStreak(UUID playerUUID, String kitName) {
        checkResets();

        if (kitName == null || kitName.isEmpty()) return;
        kitName = ChatColor.stripColor(kitName).toLowerCase();
        
        // --- PUTUS STREAK & LENYAP DARI LEADERBOARD ---
        // Karena sistem yang Anda inginkan adalah real-time "Current Streak", 
        // kita paksa skor mereka menjadi 0 saat mereka kalah.
        dataConfig.set("stats.daily." + kitName + "." + playerUUID.toString(), 0);
        dataConfig.set("stats.monthly." + kitName + "." + playerUUID.toString(), 0);

        saveData();
    }

    // Mengambil Top 5 Data
    public List<Map.Entry<String, Integer>> getTop5(String period, String kitName) {
        checkResets(); 
        
        kitName = ChatColor.stripColor(kitName).toLowerCase();
        ConfigurationSection section = dataConfig.getConfigurationSection("stats." + period + "." + kitName);
        if (section == null) return new ArrayList<>();

        Map<String, Integer> scores = new HashMap<>();
        for (String uuidStr : section.getKeys(false)) {
            int score = section.getInt(uuidStr);
            // FILTER: Hanya tampilkan pemain yang memiliki Streak berjalan (>0).
            // Jika dia kalah (skornya 0), dia akan diabaikan (lenyap dari papan).
            if (score > 0) { 
                String name = dataConfig.getString("names." + uuidStr, "Unknown");
                scores.put(name, score);
            }
        }

        return scores.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue())) // Sort descending (Terbesar ke terkecil)
                .limit(5)
                .collect(Collectors.toList());
    }

    // Format Countdown Waktu (Termasuk Detik agar tidak beku)
    public String getDailyCountdown() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endOfDay = now.toLocalDate().atTime(23, 59, 59);
        long hours = ChronoUnit.HOURS.between(now, endOfDay);
        long mins = ChronoUnit.MINUTES.between(now, endOfDay) % 60;
        long secs = ChronoUnit.SECONDS.between(now, endOfDay) % 60;
        return hours + "h " + mins + "m " + secs + "s";
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
