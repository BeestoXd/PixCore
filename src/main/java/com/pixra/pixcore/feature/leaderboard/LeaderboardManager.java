package com.pixra.pixcore.feature.leaderboard;

import com.pixra.pixcore.PixCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class LeaderboardManager {

    private static final Set<String> SUPPORTED_PERIODS =
            Set.of("daily", "weekly", "monthly", "lifetime");
    private static final long PLAYTIME_CACHE_TTL_MILLIS = 30_000L;
    private static final List<String> PLAYTIME_STAT_FILE_KEYS = Arrays.asList(
            "stat.playOneMinute",
            "stat.playOneTick",
            "stat.minutesPlayed",
            "minecraft:play_one_minute",
            "minecraft:play_one_tick",
            "minecraft:play_time"
    );

    private final PixCore plugin;
    private File dataFile;
    private FileConfiguration dataConfig;

    private String currentDailyDate;
    private String currentWeeklyDate;
    private String currentMonthlyDate;

    private boolean globalEnabled = true;
    private List<String> disabledKits = new ArrayList<>();

    private List<DisplayEntry> cachedPlaytimeTop = Collections.emptyList();
    private long cachedPlaytimeTopAt = 0L;
    private Statistic cachedPlaytimeStatistic;
    private boolean playtimeStatisticResolved = false;

    public static final class DisplayEntry {
        private final String playerName;
        private final long numericValue;
        private final String displayValue;

        public DisplayEntry(String playerName, long numericValue, String displayValue) {
            this.playerName = playerName;
            this.numericValue = numericValue;
            this.displayValue = displayValue;
        }

        public String getPlayerName() {
            return playerName;
        }

        public long getNumericValue() {
            return numericValue;
        }

        public String getDisplayValue() {
            return displayValue;
        }
    }

    public LeaderboardManager(PixCore plugin) {
        this.plugin = plugin;
        loadData();
        checkResets();
    }

    public void reload() {
        loadData();
        checkResets();
        invalidateRuntimeCaches();
    }

    private void loadData() {
        File folder = new File(plugin.getDataFolder(), "playerdata");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Failed to create leaderboard data folder.");
        }

        dataFile = new File(folder, "leaderboard.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create leaderboard.yml");
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
        disabledKits = new ArrayList<>(dataConfig.getStringList("settings.disabled-kits"));

        migrateOldData();
        invalidateRuntimeCaches();
    }

    private void migrateOldData() {
        if (dataConfig.contains("stats.daily")) {
            dataConfig.set("winstreak.daily", dataConfig.getConfigurationSection("stats.daily"));
            dataConfig.set("stats", null);
            saveData();
            plugin.getLogger().info("Migrated old leaderboard stats to new format.");
        }
    }

    private void invalidateRuntimeCaches() {
        cachedPlaytimeTop = Collections.emptyList();
        cachedPlaytimeTopAt = 0L;
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
            plugin.getLogger().severe("Failed to save leaderboard.yml");
            e.printStackTrace();
        }
    }

    public void backupData(String prefix) {
        if (dataFile == null || !dataFile.exists()) {
            return;
        }

        File backupFolder = new File(plugin.getDataFolder(), "backups");
        if (!backupFolder.exists() && !backupFolder.mkdirs()) {
            plugin.getLogger().warning("Failed to create leaderboard backup folder.");
            return;
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String fileName = (prefix != null && !prefix.isEmpty() ? prefix + "_" : "")
                + "leaderboard_" + timestamp + ".yml";
        File backupFile = new File(backupFolder, fileName);

        try {
            Files.copy(dataFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Leaderboard data backed up to: backups/" + fileName);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to backup leaderboard data.");
            e.printStackTrace();
        }
    }

    public boolean restoreData(String fileName) {
        File backupFolder = new File(plugin.getDataFolder(), "backups");
        File backupFile = new File(backupFolder, fileName);
        if (!backupFile.exists()) {
            return false;
        }

        try {
            backupData("pre-restore");
            Files.copy(backupFile.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            reload();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<String> getBackupFiles() {
        File backupFolder = new File(plugin.getDataFolder(), "backups");
        if (!backupFolder.exists()) {
            return new ArrayList<>();
        }

        File[] files = backupFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return new ArrayList<>();
        }

        return Arrays.stream(files)
                .map(File::getName)
                .sorted(Collections.reverseOrder())
                .collect(Collectors.toList());
    }

    public void resetAllData() {
        backupData("pre-reset-all");
        dataConfig.set("winstreak", null);
        dataConfig.set("wins", null);
        dataConfig.set("kills", null);
        dataConfig.set("names", null);
        saveData();
    }

    public void resetCategoryData(String category, String kitName) {
        category = normalizeCategory(category);
        kitName = normalizeKitKey(kitName);
        backupData("pre-reset-" + category + "-" + kitName);

        if ("winstreak".equals(category)) {
            dataConfig.set("winstreak.daily." + kitName, null);
        } else if ("wins".equals(category) || "kills".equals(category)) {
            for (String period : SUPPORTED_PERIODS) {
                dataConfig.set(category + "." + period + "." + kitName, null);
            }
        }

        saveData();
    }

    public void setGlobalEnabled(boolean enabled) {
        this.globalEnabled = enabled;
        saveData();
    }

    public void setKitEnabled(String kitName, boolean enabled) {
        String normalizedKit = normalizeKitKey(kitName);
        if (enabled) {
            disabledKits.remove(normalizedKit);
        } else if (!disabledKits.contains(normalizedKit)) {
            disabledKits.add(normalizedKit);
        }
        saveData();
    }

    public boolean isKitEnabled(String kitName) {
        if (!globalEnabled || kitName == null || kitName.isEmpty()) {
            return false;
        }
        return !disabledKits.contains(normalizeKitKey(kitName));
    }

    private void checkResets() {
        LocalDate today = LocalDate.now();
        YearMonth thisMonth = YearMonth.now();
        LocalDate startOfWeek = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));

        boolean changed = false;

        if (!today.toString().equals(currentDailyDate)) {
            dataConfig.set("winstreak.daily", null);
            dataConfig.set("wins.daily", null);
            dataConfig.set("kills.daily", null);
            currentDailyDate = today.toString();
            plugin.getLogger().info("Daily leaderboards have been reset.");
            changed = true;
        }

        if (!startOfWeek.toString().equals(currentWeeklyDate)) {
            dataConfig.set("wins.weekly", null);
            dataConfig.set("kills.weekly", null);
            currentWeeklyDate = startOfWeek.toString();
            plugin.getLogger().info("Weekly leaderboards have been reset.");
            changed = true;
        }

        if (!thisMonth.toString().equals(currentMonthlyDate)) {
            dataConfig.set("wins.monthly", null);
            dataConfig.set("kills.monthly", null);
            currentMonthlyDate = thisMonth.toString();
            plugin.getLogger().info("Monthly leaderboards have been reset.");
            changed = true;
        }

        if (changed) {
            saveData();
        }
    }

    public void addWin(UUID playerUUID, String playerName, String kitName) {
        if (!isKitEnabled(kitName)) {
            return;
        }
        checkResets();

        String normalizedKit = normalizeKitKey(kitName);
        dataConfig.set("names." + playerUUID, playerName);

        String wsPath = "winstreak.daily." + normalizedKit + "." + playerUUID;
        dataConfig.set(wsPath, dataConfig.getInt(wsPath, 0) + 1);

        for (String period : SUPPORTED_PERIODS) {
            String path = "wins." + period + "." + normalizedKit + "." + playerUUID;
            dataConfig.set(path, dataConfig.getInt(path, 0) + 1);
        }

        saveData();
    }

    public void addKill(UUID playerUUID, String playerName, String kitName) {
        if (!isKitEnabled(kitName)) {
            return;
        }
        checkResets();

        String normalizedKit = normalizeKitKey(kitName);
        dataConfig.set("names." + playerUUID, playerName);

        for (String period : SUPPORTED_PERIODS) {
            String path = "kills." + period + "." + normalizedKit + "." + playerUUID;
            dataConfig.set(path, dataConfig.getInt(path, 0) + 1);
        }

        saveData();
    }

    public void resetStreak(UUID playerUUID, String kitName) {
        if (!isKitEnabled(kitName)) {
            return;
        }
        checkResets();

        String normalizedKit = normalizeKitKey(kitName);
        dataConfig.set("winstreak.daily." + normalizedKit + "." + playerUUID, 0);
        saveData();
    }

    public String getHighestWinstreakString(UUID playerUUID) {
        checkResets();
        ConfigurationSection dailySection = dataConfig.getConfigurationSection("winstreak.daily");
        if (dailySection == null) {
            return "0";
        }

        int maxScore = 0;
        String bestKit = "";

        for (String kit : dailySection.getKeys(false)) {
            if (!isKitEnabled(kit)) {
                continue;
            }

            int score = dataConfig.getInt("winstreak.daily." + kit + "." + playerUUID, 0);
            if (score > maxScore) {
                maxScore = score;
                bestKit = kit;
            }
        }

        if (maxScore <= 0) {
            return "0";
        }

        String formattedKit = bestKit.substring(0, 1).toUpperCase(Locale.ROOT)
                + bestKit.substring(1).toLowerCase(Locale.ROOT);
        return maxScore + " (" + formattedKit + ")";
    }

    public List<Map.Entry<String, Integer>> getTop(String category, String period, String kitName, int limit) {
        category = normalizeCategory(category);
        if ("winstreak".equals(category) || "wins".equals(category) || "kills".equals(category)) {
            return getStoredTop(category, period, kitName, limit);
        }

        return getDisplayTop(category, period, kitName, limit).stream()
                .map(entry -> new AbstractMap.SimpleEntry<>(entry.getPlayerName(), safeInt(entry.getNumericValue())))
                .collect(Collectors.toList());
    }

    public List<DisplayEntry> getDisplayTop(String category, String period, String kitName, int limit) {
        String normalizedCategory = normalizeCategory(category);
        String normalizedPeriod = normalizePeriod(period);
        if (limit <= 0 || !isSupportedCategory(normalizedCategory) || !supportsPeriod(normalizedCategory, normalizedPeriod)) {
            return new ArrayList<>();
        }

        if ("winstreak".equals(normalizedCategory) || "wins".equals(normalizedCategory) || "kills".equals(normalizedCategory)) {
            return getStoredTop(normalizedCategory, normalizedPeriod, kitName, limit).stream()
                    .map(entry -> new DisplayEntry(entry.getKey(), entry.getValue(), String.valueOf(entry.getValue())))
                    .collect(Collectors.toList());
        }

        if ("deaths".equals(normalizedCategory)) {
            return getStrikePracticeTopEntries("deaths", limit);
        }

        if ("playtime".equals(normalizedCategory)) {
            return getPlaytimeTopEntries(limit);
        }

        if ("elo".equals(normalizedCategory)) {
            return getStrikePracticeTopEntries(resolveEloColumnKey(kitName), limit);
        }

        return new ArrayList<>();
    }

    public String normalizeCategory(String category) {
        if (category == null) {
            return "";
        }

        String normalized = ChatColor.stripColor(category).trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "ws":
                return "winstreak";
            case "ranked":
            case "rankedelo":
            case "ranked_elo":
            case "elo-ranked":
            case "ranked-elo":
                return "elo";
            default:
                return normalized;
        }
    }

    public String normalizePeriod(String period) {
        return period == null ? "" : period.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isSupportedCategory(String category) {
        String normalized = normalizeCategory(category);
        return "winstreak".equals(normalized)
                || "wins".equals(normalized)
                || "kills".equals(normalized)
                || "deaths".equals(normalized)
                || "playtime".equals(normalized)
                || "elo".equals(normalized);
    }

    public boolean supportsPeriod(String category, String period) {
        String normalizedCategory = normalizeCategory(category);
        String normalizedPeriod = normalizePeriod(period);

        switch (normalizedCategory) {
            case "winstreak":
                return "daily".equals(normalizedPeriod);
            case "wins":
            case "kills":
                return SUPPORTED_PERIODS.contains(normalizedPeriod);
            case "deaths":
            case "playtime":
            case "elo":
                return "lifetime".equals(normalizedPeriod);
            default:
                return false;
        }
    }

    public boolean usesKitRotation(String category) {
        String normalized = normalizeCategory(category);
        return !"deaths".equals(normalized) && !"playtime".equals(normalized);
    }

    public boolean isGlobalCategory(String category) {
        String normalized = normalizeCategory(category);
        return "deaths".equals(normalized) || "playtime".equals(normalized);
    }

    public String getCategoryDisplayName(String category) {
        switch (normalizeCategory(category)) {
            case "winstreak":
                return "Daily Streak";
            case "wins":
                return "Top Wins";
            case "kills":
                return "Top Kills";
            case "deaths":
                return "Top Deaths";
            case "playtime":
                return "Top Playtime";
            case "elo":
                return "Ranked Elo";
            default:
                return "Leaderboard";
        }
    }

    public String formatPlaytimeTicks(long ticks) {
        long totalSeconds = Math.max(0L, ticks / 20L);
        long days = totalSeconds / 86_400L;
        long hours = (totalSeconds % 86_400L) / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;

        if (days > 0L) {
            return days + "d " + hours + "h";
        }
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }

        long seconds = totalSeconds % 60L;
        return minutes + "m " + seconds + "s";
    }

    public String resolveLeaderboardKitDisplayName(String category, String kitName) {
        if ("elo".equals(normalizeCategory(category))) {
            return resolveStrikePracticeKitName(kitName);
        }
        return stripKitName(kitName);
    }

    public String resolveLeaderboardKitIdentity(String category, String kitName) {
        String normalizedCategory = normalizeCategory(category);
        if ("elo".equals(normalizedCategory)) {
            return resolveEloColumnKey(kitName).toLowerCase(Locale.ROOT);
        }
        return normalizeKitKey(kitName);
    }

    private List<Map.Entry<String, Integer>> getStoredTop(String category, String period, String kitName, int limit) {
        checkResets();
        if (!supportsPeriod(category, period) || kitName == null || kitName.isEmpty()) {
            return new ArrayList<>();
        }

        String normalizedKit = normalizeKitKey(kitName);
        ConfigurationSection section = dataConfig.getConfigurationSection(category + "." + period + "." + normalizedKit);
        if (section == null) {
            return new ArrayList<>();
        }

        Map<String, Integer> scores = new HashMap<>();
        for (String uuidStr : section.getKeys(false)) {
            int score = section.getInt(uuidStr);
            if (score <= 0) {
                continue;
            }

            String playerName = dataConfig.getString("names." + uuidStr, "Unknown");
            scores.put(playerName, score);
        }

        return scores.entrySet().stream()
                .sorted((left, right) -> Integer.compare(right.getValue(), left.getValue()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private List<DisplayEntry> getStrikePracticeTopEntries(String statKey, int limit) {
        String resolvedStatKey = resolveTopStatsKey(statKey);
        LinkedHashMap<String, Double> topMap = queryStrikePracticeTopStats(resolvedStatKey, limit);
        if (topMap == null || topMap.isEmpty()) {
            topMap = getStrikePracticeTopStats().get(resolvedStatKey);
        }
        if (topMap == null || topMap.isEmpty()) {
            return new ArrayList<>();
        }

        List<DisplayEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Double> entry : topMap.entrySet()) {
            String playerName = entry.getKey();
            if (playerName == null || playerName.trim().isEmpty()) {
                continue;
            }

            long numericValue = Math.round(entry.getValue());
            if (numericValue <= 0L) {
                continue;
            }

            String displayValue = "playtime".equals(resolvedStatKey)
                    ? formatPlaytimeTicks(numericValue)
                    : String.valueOf(numericValue);
            entries.add(new DisplayEntry(playerName, numericValue, displayValue));
            if (entries.size() >= limit) {
                break;
            }
        }
        return entries;
    }

    @SuppressWarnings("unchecked")
    private Map<String, LinkedHashMap<String, Double>> getStrikePracticeTopStats() {
        if (!plugin.isHooked()) {
            return Collections.emptyMap();
        }

        try {
            Class<?> clazz = Class.forName("ga.strikepractice.stats.DefaultPlayerStats");
            Method method = clazz.getMethod("getTopStats");
            Object raw = method.invoke(null);
            if (raw instanceof Map) {
                return (Map<String, LinkedHashMap<String, Double>>) raw;
            }
        } catch (Exception ignored) {
        }

        return Collections.emptyMap();
    }

    private List<DisplayEntry> getPlaytimeTopEntries(int limit) {
        long now = System.currentTimeMillis();
        if (cachedPlaytimeTopAt == 0L || now - cachedPlaytimeTopAt >= PLAYTIME_CACHE_TTL_MILLIS) {
            refreshPlaytimeCache();
        }

        if (cachedPlaytimeTop.isEmpty()) {
            return new ArrayList<>();
        }

        return new ArrayList<>(cachedPlaytimeTop.subList(0, Math.min(limit, cachedPlaytimeTop.size())));
    }

    private void refreshPlaytimeCache() {
        Statistic playtimeStatistic = resolvePlaytimeStatistic();
        if (playtimeStatistic == null) {
            cachedPlaytimeTop = Collections.emptyList();
            cachedPlaytimeTopAt = System.currentTimeMillis();
            return;
        }

        List<DisplayEntry> computed = new ArrayList<>();

        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player == null) {
                continue;
            }

            String playerName = resolveOfflinePlayerName(player);
            if (playerName == null || playerName.isEmpty()) {
                continue;
            }

            long ticks = readStatistic(player, playtimeStatistic);
            if (ticks <= 0L) {
                continue;
            }

            computed.add(new DisplayEntry(playerName, ticks, formatPlaytimeTicks(ticks)));
        }

        computed.sort((left, right) -> Long.compare(right.getNumericValue(), left.getNumericValue()));
        if (computed.size() > 10) {
            computed = new ArrayList<>(computed.subList(0, 10));
        }

        cachedPlaytimeTop = computed;
        cachedPlaytimeTopAt = System.currentTimeMillis();
    }

    private Statistic resolvePlaytimeStatistic() {
        if (playtimeStatisticResolved) {
            return cachedPlaytimeStatistic;
        }

        playtimeStatisticResolved = true;
        for (String candidate : Arrays.asList("PLAY_ONE_MINUTE", "PLAY_ONE_TICK", "MINUTE_PLAYED")) {
            try {
                cachedPlaytimeStatistic = Statistic.valueOf(candidate);
                return cachedPlaytimeStatistic;
            } catch (IllegalArgumentException ignored) {
            }
        }

        plugin.getLogger().warning("Unable to resolve a compatible playtime statistic for this server version.");
        return null;
    }

    private String resolveOfflinePlayerName(OfflinePlayer player) {
        String playerName = player.getName();
        if (playerName != null && !playerName.trim().isEmpty()) {
            return playerName;
        }
        return player.getUniqueId() != null ? player.getUniqueId().toString().substring(0, 8) : "";
    }

    private long readStatistic(OfflinePlayer player, Statistic statistic) {
        Long reflectedValue = invokeStatisticGetter(player, statistic);
        if (reflectedValue != null) {
            return reflectedValue;
        }

        if (player != null && player.isOnline()) {
            Long onlineValue = invokeStatisticGetter(player.getPlayer(), statistic);
            if (onlineValue != null) {
                return onlineValue;
            }
        }

        return readStatisticFromStatsFiles(player);
    }

    private Long invokeStatisticGetter(Object target, Statistic statistic) {
        if (target == null || statistic == null) {
            return null;
        }

        try {
            Method method = target.getClass().getMethod("getStatistic", Statistic.class);
            Object value = method.invoke(target, statistic);
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private long readStatisticFromStatsFiles(OfflinePlayer player) {
        if (player == null || player.getUniqueId() == null) {
            return 0L;
        }

        long total = 0L;
        String uuidStatsFileName = player.getUniqueId().toString() + ".json";
        String playerName = player.getName();
        String nameStatsFileName = playerName != null && !playerName.trim().isEmpty()
                ? playerName + ".json"
                : null;
        for (World world : Bukkit.getWorlds()) {
            if (world == null) {
                continue;
            }

            File statsDir = new File(world.getWorldFolder(), "stats");
            long worldPlaytime = extractPlaytimeFromStatsFile(new File(statsDir, uuidStatsFileName));
            if (worldPlaytime <= 0L && nameStatsFileName != null) {
                worldPlaytime = extractPlaytimeFromStatsFile(new File(statsDir, nameStatsFileName));
            }
            total += worldPlaytime;
        }
        return total;
    }

    private long extractPlaytimeFromStatsFile(File statsFile) {
        if (statsFile == null || !statsFile.isFile()) {
            return 0L;
        }

        try {
            String json = Files.readString(statsFile.toPath());
            return findJsonStatisticValue(json, PLAYTIME_STAT_FILE_KEYS);
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private long findJsonStatisticValue(String json, List<String> keys) {
        if (json == null || json.isEmpty() || keys == null || keys.isEmpty()) {
            return 0L;
        }

        for (String key : keys) {
            String token = "\"" + key + "\"";
            int keyIndex = json.indexOf(token);
            if (keyIndex < 0) {
                continue;
            }

            int colonIndex = json.indexOf(':', keyIndex + token.length());
            if (colonIndex < 0) {
                continue;
            }

            int valueStart = colonIndex + 1;
            while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
                valueStart++;
            }

            int valueEnd = valueStart;
            while (valueEnd < json.length() && Character.isDigit(json.charAt(valueEnd))) {
                valueEnd++;
            }

            if (valueEnd <= valueStart) {
                continue;
            }

            try {
                return Long.parseLong(json.substring(valueStart, valueEnd));
            } catch (NumberFormatException ignored) {
            }
        }

        return 0L;
    }

    @SuppressWarnings("unchecked")
    private LinkedHashMap<String, Double> queryStrikePracticeTopStats(String statKey, int limit) {
        if (!plugin.isHooked() || statKey == null || statKey.trim().isEmpty() || limit <= 0) {
            return null;
        }

        try {
            Object strikePracticePlugin = resolveStrikePracticePluginInstance();
            if (strikePracticePlugin == null) {
                return null;
            }

            Object databaseManager = resolveStrikePracticeDatabaseManager(strikePracticePlugin);
            if (databaseManager == null) {
                return null;
            }

            Method queryMethod = resolveStrikePracticeTopQueryMethod(databaseManager);
            if (queryMethod == null) {
                return null;
            }

            Object raw = queryMethod.invoke(databaseManager, statKey, limit);
            if (raw instanceof LinkedHashMap) {
                return (LinkedHashMap<String, Double>) raw;
            }
            if (raw instanceof Map) {
                return new LinkedHashMap<>((Map<String, Double>) raw);
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private Object resolveStrikePracticePluginInstance() {
        try {
            if (plugin.getHook() != null
                    && plugin.getHook().getMGetStrikePracticePlugin() != null
                    && plugin.getStrikePracticeAPI() != null) {
                Object strikePracticePlugin = plugin.getHook().getMGetStrikePracticePlugin().invoke(plugin.getStrikePracticeAPI());
                if (strikePracticePlugin != null) {
                    return strikePracticePlugin;
                }
            }
        } catch (Exception ignored) {
        }

        try {
            Class<?> strikePracticeClass = Class.forName("ga.strikepractice.StrikePractice");
            return strikePracticeClass.getMethod("getInstance").invoke(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Object resolveStrikePracticeDatabaseManager(Object strikePracticePlugin) {
        if (strikePracticePlugin == null) {
            return null;
        }

        try {
            return strikePracticePlugin.getClass().getMethod("ar").invoke(strikePracticePlugin);
        } catch (Exception ignored) {
        }

        for (Method method : strikePracticePlugin.getClass().getMethods()) {
            if (method.getParameterCount() == 0 && "ga.strikepractice.b.a".equals(method.getReturnType().getName())) {
                try {
                    return method.invoke(strikePracticePlugin);
                } catch (Exception ignored) {
                    return null;
                }
            }
        }

        return null;
    }

    private Method resolveStrikePracticeTopQueryMethod(Object databaseManager) {
        if (databaseManager == null) {
            return null;
        }

        try {
            return databaseManager.getClass().getMethod("a", String.class, int.class);
        } catch (Exception ignored) {
        }

        for (Method method : databaseManager.getClass().getMethods()) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length == 2
                    && String.class.equals(parameterTypes[0])
                    && int.class.equals(parameterTypes[1])
                    && LinkedHashMap.class.isAssignableFrom(method.getReturnType())) {
                return method;
            }
        }

        return null;
    }

    private String resolveTopStatsKey(String statKey) {
        if (statKey == null || statKey.trim().isEmpty()) {
            return statKey;
        }

        Map<String, LinkedHashMap<String, Double>> topStats = getStrikePracticeTopStats();
        if (topStats.containsKey(statKey)) {
            return statKey;
        }

        for (String existingKey : topStats.keySet()) {
            if (existingKey != null && existingKey.equalsIgnoreCase(statKey)) {
                return existingKey;
            }
        }

        return statKey;
    }

    private String resolveEloColumnKey(String kitName) {
        String resolvedKitName = resolveStrikePracticeKitName(kitName);
        String sanitizedKitName = sanitizeStrikePracticeName(resolvedKitName);
        if (sanitizedKitName.isEmpty()) {
            return "global_elo";
        }
        return resolveTopStatsKey("elo_" + sanitizedKitName);
    }

    private String resolveStrikePracticeKitName(String kitName) {
        String strippedKitName = stripKitName(kitName);
        String sanitizedRequestedKitName = sanitizeStrikePracticeName(strippedKitName);
        String normalizedRequestedKitName = normalizeEloLookupName(strippedKitName);
        if (strippedKitName.isEmpty()) {
            return "";
        }

        if (plugin.isHooked() && plugin.getStrikePracticeAPI() != null) {
            try {
                Object api = plugin.getStrikePracticeAPI();
                Method getKitsMethod = api.getClass().getMethod("getKits");
                Object rawKits = getKitsMethod.invoke(api);
                if (rawKits instanceof Iterable) {
                    String exactMatch = null;
                    String normalizedMatch = null;
                    for (Object rawKit : (Iterable<?>) rawKits) {
                        String strikePracticeKitName = readStrikePracticeKitName(rawKit);
                        if (strikePracticeKitName.isEmpty()) {
                            continue;
                        }

                        boolean eloKit = isStrikePracticeEloKit(rawKit);
                        String sanitizedStrikePracticeKitName = sanitizeStrikePracticeName(strikePracticeKitName);
                        String normalizedStrikePracticeKitName = normalizeEloLookupName(strikePracticeKitName);

                        if (eloKit && (strikePracticeKitName.equalsIgnoreCase(strippedKitName)
                                || sanitizedStrikePracticeKitName.equalsIgnoreCase(sanitizedRequestedKitName))) {
                            return strikePracticeKitName;
                        }

                        if (eloKit && normalizedStrikePracticeKitName.equalsIgnoreCase(normalizedRequestedKitName)) {
                            normalizedMatch = strikePracticeKitName;
                            continue;
                        }

                        if (exactMatch == null && (strikePracticeKitName.equalsIgnoreCase(strippedKitName)
                                || sanitizedStrikePracticeKitName.equalsIgnoreCase(sanitizedRequestedKitName))) {
                            exactMatch = strikePracticeKitName;
                        }
                    }

                    if (normalizedMatch != null) {
                        return normalizedMatch;
                    }
                    if (exactMatch != null) {
                        return exactMatch;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        String eloStatKey = resolveTopStatsKey("elo_" + sanitizeStrikePracticeName(strippedKitName));
        if (eloStatKey != null && eloStatKey.regionMatches(true, 0, "elo_", 0, 4) && eloStatKey.length() > 4) {
            return eloStatKey.substring(4);
        }

        Map<String, LinkedHashMap<String, Double>> topStats = getStrikePracticeTopStats();
        for (String existingKey : topStats.keySet()) {
            if (existingKey == null || !existingKey.regionMatches(true, 0, "elo_", 0, 4) || existingKey.length() <= 4) {
                continue;
            }

            String existingKitName = existingKey.substring(4);
            if (normalizeEloLookupName(existingKitName).equalsIgnoreCase(normalizedRequestedKitName)) {
                return existingKitName;
            }
        }

        return strippedKitName;
    }

    private String readStrikePracticeKitName(Object rawKit) {
        if (rawKit == null) {
            return "";
        }

        try {
            Object rawName = rawKit.getClass().getMethod("getName").invoke(rawKit);
            return rawName == null ? "" : stripKitName(String.valueOf(rawName));
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean isStrikePracticeEloKit(Object rawKit) {
        if (rawKit == null) {
            return false;
        }

        try {
            Object rawValue = rawKit.getClass().getMethod("isElo").invoke(rawKit);
            return rawValue instanceof Boolean && (Boolean) rawValue;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String sanitizeStrikePracticeName(String name) {
        return stripKitName(name).replaceAll("[^a-zA-Z0-9_-]", "");
    }

    private String normalizeEloLookupName(String name) {
        String sanitized = sanitizeStrikePracticeName(name).toLowerCase(Locale.ROOT);
        if (sanitized.endsWith("elo") && sanitized.length() > 3) {
            return sanitized.substring(0, sanitized.length() - 3);
        }
        return sanitized;
    }

    private String stripKitName(String kitName) {
        return kitName == null ? "" : ChatColor.stripColor(kitName).trim();
    }

    private String normalizeKitKey(String kitName) {
        return kitName == null ? "" : ChatColor.stripColor(kitName).trim().toLowerCase(Locale.ROOT);
    }

    private int safeInt(long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) value;
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
        LocalDateTime endOfWeek = now.toLocalDate()
                .with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY))
                .atStartOfDay()
                .minusSeconds(1);
        long days = ChronoUnit.DAYS.between(now, endOfWeek);
        long hours = ChronoUnit.HOURS.between(now, endOfWeek) % 24;
        long mins = ChronoUnit.MINUTES.between(now, endOfWeek) % 60;
        long secs = ChronoUnit.SECONDS.between(now, endOfWeek) % 60;
        return days + "d " + hours + "h " + mins + "m " + secs + "s";
    }

    public String getMonthlyCountdown() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endOfMonth = now.toLocalDate()
                .withDayOfMonth(now.toLocalDate().lengthOfMonth())
                .atTime(23, 59, 59);
        long days = ChronoUnit.DAYS.between(now, endOfMonth);
        long hours = ChronoUnit.HOURS.between(now, endOfMonth) % 24;
        long mins = ChronoUnit.MINUTES.between(now, endOfMonth) % 60;
        long secs = ChronoUnit.SECONDS.between(now, endOfMonth) % 60;
        return days + "d " + hours + "h " + mins + "m " + secs + "s";
    }
}
