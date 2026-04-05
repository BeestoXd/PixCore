package com.pixra.pixcore.feature.leaderboard;

import com.pixra.pixcore.PixCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import me.clip.placeholderapi.PlaceholderAPI;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;

public class HologramManager {

    private final PixCore plugin;

    private final Map<UUID, List<ArmorStand>> playerHolograms = new HashMap<>();
    private final Map<UUID, PlayerHologramLayout> playerHologramLayouts = new HashMap<>();
    private final Set<String> registeredKits = new HashSet<>();
    private File perPlayerCfgFile;
    private FileConfiguration perPlayerCfg;

    private final Map<Integer, List<ArmorStand>> standingHolograms = new HashMap<>();
    private final Map<Integer, Integer> rotationIndex = new HashMap<>();
    private BukkitTask rotationTask = null;
    private BukkitTask countdownTask = null;
    private BukkitTask glowTask = null;
    private int glowFrame = 0;

    private File standingCfgFile;
    private FileConfiguration standingCfg;

    private static final double FORWARD = 3.0;
    private static final double SIDE = 2.5;
    private static final double LINE_GAP = -0.27;
    private static final int LINES_PLAYER = 8;
    private static final int LINES_STAND = 13;
    private static final int PLAYER_STAND_COUNT = LINES_PLAYER * 2;
    private static final double STAND_Y_OFFSET = 1.0;
    private static final double PLAYER_HOLOGRAM_POSITION_TOLERANCE_SQUARED = 2.25D;
    private static final double PLAYER_HOLOGRAM_LAYOUT_RELEVANCE_DISTANCE_SQUARED = 16.0D;
    private static final double PLAYER_HOLOGRAM_LAYOUT_MAX_VERTICAL_DELTA = 8.0D;

    private static final Set<String> VALID_CATEGORIES = new HashSet<>(Arrays.asList("ws", "wins", "kills"));
    private static final Set<String> VALID_PERIODS = new HashSet<>(
            Arrays.asList("daily", "weekly", "monthly", "lifetime"));

    private static final class PlayerHologramLayout {
        private final Location origin;
        private final Location rightTop;
        private final Location leftTop;

        private PlayerHologramLayout(Location origin, Location rightTop, Location leftTop) {
            this.origin = origin.clone();
            this.rightTop = rightTop.clone();
            this.leftTop = leftTop.clone();
        }
    }

    public HologramManager(PixCore plugin) {
        this.plugin = plugin;
        loadPerPlayerConfig();
        loadStandingConfig();
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanAllStandingGhosts();
                respawnAllStanding();
                startRotationTask();
                startCountdownUpdater();
                startGlowAnimation();
            }
        }.runTaskLater(plugin, 60L);
    }

    private void loadPerPlayerConfig() {
        File folder = new File(plugin.getDataFolder(), "playerdata");
        if (!folder.exists())
            folder.mkdirs();
        perPlayerCfgFile = new File(folder, "holograms.yml");
        if (!perPlayerCfgFile.exists()) {
            try {
                perPlayerCfgFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        perPlayerCfg = YamlConfiguration.loadConfiguration(perPlayerCfgFile);
        registeredKits.clear();
        for (String k : perPlayerCfg.getStringList("registered-kits"))
            registeredKits.add(k.toLowerCase());
    }

    private void savePerPlayerConfig() {
        perPlayerCfg.set("registered-kits", new ArrayList<>(registeredKits));
        try {
            perPlayerCfg.save(perPlayerCfgFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadStandingConfig() {
        standingCfgFile = new File(plugin.getDataFolder(), "standing-holograms.yml");
        if (!standingCfgFile.exists()) {
            plugin.saveResource("standing-holograms.yml", false);
            if (!standingCfgFile.exists()) {
                try {
                    standingCfgFile.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        standingCfg = YamlConfiguration.loadConfiguration(standingCfgFile);

        if (!standingCfg.contains("rotation-interval-seconds"))
            standingCfg.set("rotation-interval-seconds", 30);
        if (!standingCfg.contains("rotation-kits"))
            standingCfg.set("rotation-kits", Arrays.asList("bedfight", "sumo"));
        if (!standingCfg.contains("holograms"))
            standingCfg.set("holograms", new HashMap<>());
        saveStandingConfig();
    }

    private void saveStandingConfig() {
        try {
            standingCfg.save(standingCfgFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private List<String> getRotationKits() {
        List<String> kits = standingCfg.getStringList("rotation-kits");
        List<String> result = new ArrayList<>();
        for (String k : kits)
            result.add(k.toLowerCase());
        return result.isEmpty() ? Collections.singletonList("unknown") : result;
    }

    private int getRotationIntervalSeconds() {
        return Math.max(10, standingCfg.getInt("rotation-interval-seconds", 30));
    }

    public void reload() {
        hideAll();
        despawnAllStanding();
        if (rotationTask != null) {
            rotationTask.cancel();
            rotationTask = null;
        }
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        if (glowTask != null) {
            glowTask.cancel();
            glowTask = null;
        }
        loadPerPlayerConfig();
        loadStandingConfig();
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanAllStandingGhosts();
                respawnAllStanding();
                startRotationTask();
                startCountdownUpdater();
                startGlowAnimation();
            }
        }.runTaskLater(plugin, 20L);
    }

    public void registerKit(Player admin, String kitName) {
        kitName = strip(kitName);
        boolean added = registeredKits.add(kitName);
        savePerPlayerConfig();
        if (added) {
            admin.sendMessage(color("&a[PixCore] Kit &e" + kitName.toUpperCase()
                    + " &aregistered! Leaderboard hologram will appear in front of each player "
                    + "during a &e" + kitName.toUpperCase() + " &amatch countdown in every arena."));
        } else {
            admin.sendMessage(color("&e[PixCore] Kit &f" + kitName.toUpperCase() + " &ewas already registered."));
        }
    }

    public void unregisterKit(Player admin, String kitName) {
        kitName = strip(kitName);
        if (registeredKits.remove(kitName)) {
            savePerPlayerConfig();
            admin.sendMessage(color("&a[PixCore] Kit &e" + kitName.toUpperCase() + " &aunregistered."));
        } else {
            admin.sendMessage(color("&cKit &e" + kitName + " &cwas not registered."));
        }
    }

    public void registerKitConsole(String kitName) {
        registeredKits.add(strip(kitName));
        savePerPlayerConfig();
    }

    public void unregisterKitConsole(String kitName) {
        registeredKits.remove(strip(kitName));
        savePerPlayerConfig();
    }

    public Set<String> getRegisteredKits() {
        return Collections.unmodifiableSet(registeredKits);
    }

    public void showForPlayer(Player player, String kitName) {
        if (kitName == null)
            return;
        kitName = strip(kitName);
        if (!registeredKits.contains(kitName))
            return;
        if (plugin.leaderboardManager == null)
            return;
        if (!plugin.leaderboardManager.isKitEnabled(kitName))
            return;

        PlayerHologramLayout layout = resolvePlayerHologramLayout(player);
        if (layout == null)
            return;

        List<String> leftLines = buildPlayerLeft(kitName);
        List<String> rightLines = buildPlayerRight(kitName);

        if (refreshPlayerHologram(player, layout, leftLines, rightLines)) {
            scheduleRehideForOtherPlayers(player, 1L, 10L);
            return;
        }

        List<ArmorStand> stands = new ArrayList<>();
        removePlayerHologramEntities(player.getUniqueId());
        stands.addAll(spawnColumnForPlayer(layout.rightTop, leftLines, LINES_PLAYER, player));
        stands.addAll(spawnColumnForPlayer(layout.leftTop, rightLines, LINES_PLAYER, player));
        playerHolograms.put(player.getUniqueId(), stands);
        scheduleRehideForOtherPlayers(player, 0L, 1L, 10L);
    }

    public void hideForPlayer(Player player) {
        clearPlayerHologramState(player.getUniqueId());
    }

    public void clearPlayerHologram(Player player) {
        hideForPlayer(player);
    }

    public void clearPlayerHologram(UUID uuid) {
        clearPlayerHologramState(uuid);
    }

    public boolean hasPlayerHologram(Player player) {
        return hasValidPlayerHologram(player);
    }

    public void removeAllHolograms() {
        hideAll();
    }

    public void hideAll() {
        for (List<ArmorStand> s : new ArrayList<>(playerHolograms.values()))
            despawnList(s);
        playerHolograms.clear();
        playerHologramLayouts.clear();
        despawnAllStanding();
        if (glowTask != null) {
            glowTask.cancel();
            glowTask = null;
        }
    }

    public boolean placeStandingHologram(Player admin, String category, String period) {
        category = category.toLowerCase();
        period = period.toLowerCase();

        if (!VALID_CATEGORIES.contains(category)) {
            admin.sendMessage(color("&cInvalid category. Use: &ews &c| &ewins &c| &ekills"));
            return false;
        }
        if (!VALID_PERIODS.contains(period)) {
            admin.sendMessage(color("&cInvalid period. Use: &edaily &c| &eweekly &c| &emonthly &c| &elifetime"));
            return false;
        }
        if (category.equals("ws") && !period.equals("daily")) {
            admin.sendMessage(color("&cDaily Streak &e(ws)&c only supports period &edaily&c."));
            return false;
        }

        int id = nextStandingId();

        Location loc = admin.getLocation();
        double y = loc.getY() + STAND_Y_OFFSET;

        String path = "holograms." + id;
        standingCfg.set(path + ".world", loc.getWorld().getName());
        standingCfg.set(path + ".x", loc.getX());
        standingCfg.set(path + ".y", y);
        standingCfg.set(path + ".z", loc.getZ());
        standingCfg.set(path + ".category", category);
        standingCfg.set(path + ".period", period);
        saveStandingConfig();

        rotationIndex.put(id, 0);
        spawnStanding(id);

        admin.sendMessage(color("&a[PixCore] Standing hologram &e#" + id
                + " &aplaced! Category: &e" + category.toUpperCase()
                + " &a| Period: &e" + period.toUpperCase()
                + " &a| Rotates kits: &e" + String.join(", ", getRotationKits())));
        return true;
    }

    public void removeStandingHologram(Player admin, int id) {
        if (!standingCfg.contains("holograms." + id)) {
            admin.sendMessage(color("&cStanding hologram &e#" + id + " &cnot found."));
            return;
        }
        despawnStanding(id);
        standingCfg.set("holograms." + id, null);
        saveStandingConfig();
        admin.sendMessage(color("&a[PixCore] Standing hologram &e#" + id + " &aremoved."));
    }

    public void listStandingHolograms(Player admin) {
        ConfigurationSection sec = standingCfg.getConfigurationSection("holograms");
        if (sec == null || sec.getKeys(false).isEmpty()) {
            admin.sendMessage(color("&7No standing holograms placed."));
            return;
        }
        admin.sendMessage(color("&b&lStanding Holograms:"));
        for (String key : sec.getKeys(false)) {
            String path = "holograms." + key;
            String world = standingCfg.getString(path + ".world", "?");
            double x = standingCfg.getDouble(path + ".x");
            double y = standingCfg.getDouble(path + ".y");
            double z = standingCfg.getDouble(path + ".z");
            String category = standingCfg.getString(path + ".category", "?");
            String period = standingCfg.getString(path + ".period", "?");
            admin.sendMessage(color("  &e#" + key + " &7| &f" + category.toUpperCase() + " " + period
                    + " &7| " + world + " " + (int) x + "," + (int) y + "," + (int) z));
        }
    }

    private void respawnAllStanding() {
        ConfigurationSection sec = standingCfg.getConfigurationSection("holograms");
        if (sec == null)
            return;
        for (String key : sec.getKeys(false)) {
            try {
                int id = Integer.parseInt(key);
                if (!rotationIndex.containsKey(id))
                    rotationIndex.put(id, 0);
                despawnStanding(id);
                spawnStanding(id);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void spawnStanding(int id) {
        String path = "holograms." + id;
        String worldName = standingCfg.getString(path + ".world");
        if (worldName == null)
            return;
        World world = Bukkit.getWorld(worldName);
        if (world == null)
            return;

        double x = standingCfg.getDouble(path + ".x");
        double y = standingCfg.getDouble(path + ".y");
        double z = standingCfg.getDouble(path + ".z");
        String category = standingCfg.getString(path + ".category", "ws");
        String period = standingCfg.getString(path + ".period", "daily");

        List<String> kits = getRotationKits();
        int idx = rotationIndex.getOrDefault(id, 0) % kits.size();
        String kit = kits.get(idx);

        Location top = new Location(world, x, y, z);
        List<String> lines = buildStandingLines(category, period, kit);

        despawnStanding(id);
        standingHolograms.put(id, spawnColumn(top, lines, LINES_STAND));
    }

    private void despawnStanding(int id) {
        despawnList(standingHolograms.remove(id));
    }

    private void despawnAllStanding() {
        for (int id : new ArrayList<>(standingHolograms.keySet()))
            despawnStanding(id);
        standingHolograms.clear();
    }

    private void startRotationTask() {
        if (rotationTask != null)
            rotationTask.cancel();
        long intervalTicks = getRotationIntervalSeconds() * 20L;
        rotationTask = new BukkitRunnable() {
            @Override
            public void run() {
                ConfigurationSection sec = standingCfg.getConfigurationSection("holograms");
                if (sec == null)
                    return;
                List<String> kits = getRotationKits();
                for (String key : sec.getKeys(false)) {
                    try {
                        int id = Integer.parseInt(key);
                        int next = (rotationIndex.getOrDefault(id, 0) + 1) % kits.size();
                        rotationIndex.put(id, next);
                        spawnStanding(id);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }.runTaskTimer(plugin, intervalTicks, intervalTicks);
    }

    private void startCountdownUpdater() {
        if (countdownTask != null)
            countdownTask.cancel();
        countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (plugin.leaderboardManager == null)
                    return;
                ConfigurationSection sec = standingCfg.getConfigurationSection("holograms");
                if (sec == null)
                    return;
                for (String key : sec.getKeys(false)) {
                    try {
                        int id = Integer.parseInt(key);
                        List<ArmorStand> stands = standingHolograms.get(id);
                        if (stands == null || stands.size() < 3)
                            continue;
                        String period = standingCfg.getString("holograms." + id + ".period", "daily");
                        String newCountdown;
                        switch (period) {
                            case "daily":
                                newCountdown = plugin.leaderboardManager.getDailyCountdown();
                                break;
                            case "weekly":
                                newCountdown = plugin.leaderboardManager.getWeeklyCountdown();
                                break;
                            case "monthly":
                                newCountdown = plugin.leaderboardManager.getMonthlyCountdown();
                                break;
                            default:
                                newCountdown = null;
                                break;
                        }
                        if (newCountdown == null)
                            continue;
                        ArmorStand countdownStand = stands.get(2);
                        if (countdownStand != null && !countdownStand.isDead()) {
                            countdownStand.setCustomName(color("&7Resets in: &c" + newCountdown));
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private int nextStandingId() {
        ConfigurationSection sec = standingCfg.getConfigurationSection("holograms");
        if (sec == null)
            return 1;
        int max = 0;
        for (String k : sec.getKeys(false)) {
            try {
                max = Math.max(max, Integer.parseInt(k));
            } catch (NumberFormatException ignored) {
            }
        }
        return max + 1;
    }

    private List<String> buildPlayerLeft(String kit) {
        List<String> lines = new ArrayList<>();
        lines.add("&b&l✦ DAILY STREAK ✦");
        lines.add("&7Resets in: &c" + plugin.leaderboardManager.getDailyCountdown());
        lines.addAll(topN(plugin.leaderboardManager.getTop("winstreak", "daily", kit, 5), 5));
        return padTo(lines, LINES_PLAYER);
    }

    private List<String> buildPlayerRight(String kit) {
        List<String> lines = new ArrayList<>();
        lines.add("&6&l✦ MONTHLY TOP WINS ✦");
        lines.add("&7Resets in: &c" + plugin.leaderboardManager.getMonthlyCountdown());
        lines.addAll(topN(plugin.leaderboardManager.getTop("wins", "monthly", kit, 5), 5));
        return padTo(lines, LINES_PLAYER);
    }

    private List<String> buildStandingLines(String category, String period, String kit) {
        List<String> lines = new ArrayList<>();

        String titleColor, titleLabel, categoryLabel;
        switch (category) {
            case "ws":
                titleColor = "&b";
                titleLabel = "✦ DAILY STREAK ✦";
                categoryLabel = "Daily Streak";
                break;
            case "wins":
                titleColor = "&a";
                titleLabel = "✦ TOP WINS ✦";
                categoryLabel = "Wins";
                break;
            default:
                titleColor = "&c";
                titleLabel = "✦ TOP KILLS ✦";
                categoryLabel = "Kills";
                break;
        }

        String periodLabel = period.substring(0, 1).toUpperCase() + period.substring(1);
        String countdown;
        switch (period) {
            case "daily":
                countdown = plugin.leaderboardManager.getDailyCountdown();
                break;
            case "weekly":
                countdown = plugin.leaderboardManager.getWeeklyCountdown();
                break;
            case "monthly":
                countdown = plugin.leaderboardManager.getMonthlyCountdown();
                break;
            default:
                countdown = null;
                break;
        }

        String dataCategory = category.equals("ws") ? "winstreak" : category;

        lines.add(titleColor + "&l" + periodLabel + " " + titleLabel);
        lines.add("&eKit: &f" + kit.toUpperCase());
        if (countdown != null) {
            lines.add("&7Resets in: &c" + countdown);
        } else {
            lines.add("&7Lifetime stats");
        }
        lines.addAll(topN(plugin.leaderboardManager.getTop(dataCategory, period, kit, 10), 10));
        return padTo(lines, LINES_STAND);
    }

    private List<String> topN(List<Map.Entry<String, Integer>> top, int n) {
        String[] colors = { "&a", "&e", "&6", "&f", "&7", "&7", "&7", "&7", "&7", "&7" };
        String[] icons = { "⚑ ", "★ ", "★ ", "  ", "  ", "  ", "  ", "  ", "  ", "  " };
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String c = i < colors.length ? colors[i] : "&7";
            String ic = i < icons.length ? icons[i] : "  ";
            if (i < top.size()) {
                Map.Entry<String, Integer> e = top.get(i);
                String pName = e.getKey();
                lines.add(c + ic + (i + 1) + ". " + resolvePrefix(pName) + "&f" + pName + "&r " + resolveTag(pName) + "&r &8- &d" + e.getValue());
            } else {
                lines.add(c + (i + 1) + ". &8&o-");
            }
        }
        return lines;
    }

    private String resolvePrefix(String playerName) {
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            String lp = PlaceholderAPI.setPlaceholders(online, "%luckperms_prefix%");
            String result = "%luckperms_prefix%".equals(lp) ? "" : lp;
            plugin.prefixCache.put(playerName, result);
            return result;
        }
        return plugin.prefixCache.getOrDefault(playerName, "");
    }

    private String resolveTag(String playerName) {
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            String tag = PlaceholderAPI.setPlaceholders(online, "%deluxetags_tag%");
            String result = "%deluxetags_tag%".equals(tag) ? "" : tag;
            plugin.tagCache.put(playerName, result);
            return result;
        }
        return plugin.tagCache.getOrDefault(playerName, "");
    }

    private List<String> padTo(List<String> lines, int size) {
        while (lines.size() < size)
            lines.add(" ");
        return lines.subList(0, size);
    }

    private List<ArmorStand> spawnColumn(Location top, List<String> lines, int maxLines) {
        return spawnColumnForPlayer(top, lines, maxLines, null);
    }

    private List<ArmorStand> spawnColumnForPlayer(Location top, List<String> lines, int maxLines, Player owner) {
        List<ArmorStand> stands = new ArrayList<>();
        Location cur = top.clone();
        int count = Math.min(lines.size(), maxLines);
        for (int i = 0; i < count; i++) {
            ArmorStand as = (ArmorStand) cur.getWorld().spawnEntity(cur, EntityType.ARMOR_STAND);
            as.setVisible(false);
            as.setGravity(false);
            as.setCustomNameVisible(true);
            as.setBasePlate(false);
            as.setCustomName(color(lines.get(i)));
            applyOwnerVisibility(as, owner);
            try {
                as.setPersistent(false);
            } catch (NoSuchMethodError ignored) {
            }
            try {
                as.setMarker(true);
            } catch (NoSuchMethodError ignored) {
            }
            stands.add(as);
            cur = cur.clone().add(0, LINE_GAP, 0);
        }
        return stands;
    }

    private void applyOwnerVisibility(ArmorStand as, Player owner) {
        if (owner == null) {
            return;
        }

        try {
            as.setVisibleByDefault(false);
        } catch (NoSuchMethodError ignored) {
        }

        hideFromOthers(as, owner);
        try {
            owner.showEntity(plugin, as);
        } catch (NoSuchMethodError | Exception ignored) {
        }
    }

    private void hideFromOthers(ArmorStand as, Player owner) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(owner.getUniqueId())) continue;
            try {
                other.hideEntity(plugin, as);
            } catch (NoSuchMethodError | Exception ignored) {
                sendDestroyPacketToOthers(as, owner);
                return;
            }
        }
    }

    private boolean refreshPlayerHologram(Player player, PlayerHologramLayout layout,
                                          List<String> leftLines, List<String> rightLines) {
        List<ArmorStand> stands = playerHolograms.get(player.getUniqueId());
        if (stands == null || stands.size() != PLAYER_STAND_COUNT) {
            cleanupInvalidPlayerHologram(player.getUniqueId(), stands);
            return false;
        }

        if (!refreshPlayerColumn(stands, 0, layout.rightTop, leftLines, player)) {
            cleanupInvalidPlayerHologram(player.getUniqueId(), stands);
            return false;
        }
        if (!refreshPlayerColumn(stands, LINES_PLAYER, layout.leftTop, rightLines, player)) {
            cleanupInvalidPlayerHologram(player.getUniqueId(), stands);
            return false;
        }
        return true;
    }

    private boolean refreshPlayerColumn(List<ArmorStand> stands, int startIndex, Location top,
                                        List<String> lines, Player owner) {
        Location current = top.clone();
        for (int i = 0; i < LINES_PLAYER; i++) {
            ArmorStand stand = stands.get(startIndex + i);
            if (!isStandUsable(stand))
                return false;

            if (!stand.getWorld().equals(current.getWorld())
                    || stand.getLocation().distanceSquared(current) > 0.0001D) {
                stand.teleport(current);
            }

            stand.setVisible(false);
            stand.setGravity(false);
            stand.setCustomNameVisible(true);
            stand.setBasePlate(false);
            stand.setCustomName(color(lines.get(i)));
            try {
                stand.setPersistent(false);
            } catch (NoSuchMethodError ignored) {
            }
            try {
                stand.setMarker(true);
            } catch (NoSuchMethodError ignored) {
            }

            applyOwnerVisibility(stand, owner);

            current.add(0, LINE_GAP, 0);
        }
        return true;
    }

    private boolean hasValidPlayerHologram(Player owner) {
        if (owner == null) {
            return false;
        }

        UUID ownerUuid = owner.getUniqueId();
        PlayerHologramLayout layout = playerHologramLayouts.get(ownerUuid);
        if (layout == null) {
            removePlayerHologramEntities(ownerUuid);
            return false;
        }
        if (!isPlayerHologramLayoutRelevant(owner, layout)) {
            clearPlayerHologramState(ownerUuid);
            return false;
        }

        List<ArmorStand> stands = playerHolograms.get(ownerUuid);
        if (stands == null || stands.size() != PLAYER_STAND_COUNT) {
            cleanupInvalidPlayerHologram(ownerUuid, stands);
            return false;
        }

        for (ArmorStand stand : stands) {
            if (!isStandUsable(stand)) {
                cleanupInvalidPlayerHologram(ownerUuid, stands);
                return false;
            }
        }

        return isPlayerHologramCurrent(stands, layout);
    }

    private boolean isPlayerHologramCurrent(List<ArmorStand> stands, PlayerHologramLayout layout) {
        if (stands.size() < PLAYER_STAND_COUNT) {
            return false;
        }

        return isStandNear(stands.get(0), layout.rightTop)
                && isStandNear(stands.get(LINES_PLAYER), layout.leftTop);
    }

    private boolean isStandNear(ArmorStand stand, Location expected) {
        if (!isStandUsable(stand) || expected == null || expected.getWorld() == null) {
            return false;
        }
        if (!stand.getWorld().equals(expected.getWorld())) {
            return false;
        }
        return stand.getLocation().distanceSquared(expected) <= PLAYER_HOLOGRAM_POSITION_TOLERANCE_SQUARED;
    }

    private void cleanupInvalidPlayerHologram(UUID ownerUuid, List<ArmorStand> stands) {
        if (stands != null) {
            despawnList(stands);
        }
        playerHolograms.remove(ownerUuid);
    }

    private PlayerHologramLayout resolvePlayerHologramLayout(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerHologramLayout layout = playerHologramLayouts.get(uuid);
        if (layout != null && isPlayerHologramLayoutRelevant(player, layout)) {
            return layout;
        }

        playerHologramLayouts.remove(uuid);
        layout = createPlayerHologramLayout(player);
        if (layout != null) {
            playerHologramLayouts.put(uuid, layout);
        }
        return layout;
    }

    private PlayerHologramLayout createPlayerHologramLayout(Player player) {
        Location eye = player.getEyeLocation();
        if (eye == null || eye.getWorld() == null) {
            return null;
        }

        Vector dir = resolveLockedPlayerForward(player);
        if (dir.lengthSquared() < 0.001D) {
            dir = new Vector(0, 0, 1);
        }
        dir.normalize();

        Location anchor = eye.clone().add(dir.clone().multiply(FORWARD));
        anchor.setY(eye.getY() + 1.2);

        Vector left = new Vector(-dir.getZ(), 0, dir.getX()).normalize().multiply(SIDE);
        Vector right = new Vector(dir.getZ(), 0, -dir.getX()).normalize().multiply(SIDE);

        return new PlayerHologramLayout(
                player.getLocation(),
                anchor.clone().add(right),
                anchor.clone().add(left)
        );
    }

    private Vector resolveLockedPlayerForward(Player player) {
        Vector dir = resolveArenaFacingDirection(player);
        if (dir != null && dir.lengthSquared() >= 0.001D) {
            return dir;
        }

        Location eye = player.getEyeLocation();
        dir = eye.getDirection().clone().setY(0);
        if (dir.lengthSquared() < 0.001D) {
            return new Vector(0, 0, 1);
        }
        return dir.normalize();
    }

    private Vector resolveArenaFacingDirection(Player player) {
        try {
            if (!plugin.isHooked() || plugin.getStrikePracticeAPI() == null || plugin.getMGetFight() == null
                    || plugin.getMGetArena() == null) {
                return null;
            }

            Object fight = plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), player);
            if (fight == null) {
                return null;
            }

            Object arena = plugin.getMGetArena().invoke(fight);
            if (arena == null) {
                return null;
            }

            Method spawn1Method = plugin.getHook().getMArenaGetSpawn1();
            Method spawn2Method = plugin.getHook().getMArenaGetSpawn2();
            if (spawn1Method == null || spawn2Method == null) {
                return null;
            }

            Location spawn1 = (Location) spawn1Method.invoke(arena);
            Location spawn2 = (Location) spawn2Method.invoke(arena);
            if (spawn1 == null || spawn2 == null || spawn1.getWorld() == null || spawn2.getWorld() == null
                    || !spawn1.getWorld().equals(spawn2.getWorld())) {
                return null;
            }

            Location ownSpawn = null;
            Location targetSpawn = null;
            if (plugin.getMGetFirstPlayer() != null) {
                try {
                    Player first = (Player) plugin.getMGetFirstPlayer().invoke(fight);
                    if (player.equals(first)) {
                        ownSpawn = spawn1;
                        targetSpawn = spawn2;
                    }
                } catch (Exception ignored) {
                }
            }
            if (ownSpawn == null && plugin.getMGetSecondPlayer() != null) {
                try {
                    Player second = (Player) plugin.getMGetSecondPlayer().invoke(fight);
                    if (player.equals(second)) {
                        ownSpawn = spawn2;
                        targetSpawn = spawn1;
                    }
                } catch (Exception ignored) {
                }
            }

            if (ownSpawn == null && player.getWorld() != null && player.getWorld().equals(spawn1.getWorld())) {
                double d1 = player.getLocation().distanceSquared(spawn1);
                double d2 = player.getLocation().distanceSquared(spawn2);
                if (d1 <= d2) {
                    ownSpawn = spawn1;
                    targetSpawn = spawn2;
                } else {
                    ownSpawn = spawn2;
                    targetSpawn = spawn1;
                }
            }

            if (ownSpawn == null || targetSpawn == null) {
                return null;
            }

            Vector dir = targetSpawn.toVector().subtract(ownSpawn.toVector());
            dir.setY(0);
            return dir.lengthSquared() < 0.001D ? null : dir.normalize();
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isPlayerHologramLayoutRelevant(Player owner, PlayerHologramLayout layout) {
        if (owner == null || layout == null || layout.origin == null || layout.origin.getWorld() == null
                || owner.getWorld() == null || !owner.getWorld().equals(layout.origin.getWorld())) {
            return false;
        }

        Location current = owner.getLocation();
        double dx = current.getX() - layout.origin.getX();
        double dz = current.getZ() - layout.origin.getZ();
        double horizontalDistanceSquared = dx * dx + dz * dz;
        return horizontalDistanceSquared <= PLAYER_HOLOGRAM_LAYOUT_RELEVANCE_DISTANCE_SQUARED
                && Math.abs(current.getY() - layout.origin.getY()) <= PLAYER_HOLOGRAM_LAYOUT_MAX_VERTICAL_DELTA;
    }

    private void clearPlayerHologramState(UUID ownerUuid) {
        removePlayerHologramEntities(ownerUuid);
        playerHologramLayouts.remove(ownerUuid);
    }

    private void removePlayerHologramEntities(UUID ownerUuid) {
        despawnList(playerHolograms.remove(ownerUuid));
    }

    private boolean isStandUsable(ArmorStand stand) {
        return stand != null && !stand.isDead() && stand.isValid();
    }

    public void reHideAllFromPlayer(Player viewer) {
        UUID viewerUuid = viewer.getUniqueId();
        for (Map.Entry<UUID, List<ArmorStand>> entry : new HashMap<>(playerHolograms).entrySet()) {
            if (entry.getKey().equals(viewerUuid)) continue;
            List<ArmorStand> stands = entry.getValue();
            if (stands == null) continue;
            for (ArmorStand as : stands) {
                if (as == null || as.isDead()) continue;
                try {
                    viewer.hideEntity(plugin, as);
                } catch (NoSuchMethodError | Exception ignored) {
                    sendDestroyPacketToPlayer(as, viewer);
                }
            }
        }
    }

    public void scheduleRehideForPlayer(Player viewer, long... delays) {
        if (viewer == null) {
            return;
        }

        scheduleRehideForViewerUuid(viewer.getUniqueId(), delays);
    }

    public void scheduleRehideForOtherPlayers(Player owner, long... delays) {
        if (owner == null) {
            return;
        }

        UUID ownerUuid = owner.getUniqueId();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(ownerUuid)) {
                continue;
            }
            scheduleRehideForViewerUuid(viewer.getUniqueId(), delays);
        }
    }

    private void scheduleRehideForViewerUuid(UUID viewerUuid, long... delays) {
        if (viewerUuid == null) {
            return;
        }

        LinkedHashSet<Long> uniqueDelays = new LinkedHashSet<>();
        if (delays == null || delays.length == 0) {
            uniqueDelays.add(1L);
        } else {
            for (long delay : delays) {
                uniqueDelays.add(Math.max(0L, delay));
            }
        }

        for (long delay : uniqueDelays) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player viewer = Bukkit.getPlayer(viewerUuid);
                if (viewer != null && viewer.isOnline()) {
                    reHideAllFromPlayer(viewer);
                }
            }, delay);
        }
    }

    private void sendDestroyPacketToOthers(ArmorStand as, Player owner) {
        try {
            int entityId = as.getEntityId();
            String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            Object packet = buildDestroyPacket(entityId, version);
            if (packet == null) return;
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.getUniqueId().equals(owner.getUniqueId())) continue;
                sendRawPacketToPlayer(packet, other, version);
            }
        } catch (Exception ignored) {
        }
    }

    private void sendDestroyPacketToPlayer(ArmorStand as, Player target) {
        try {
            int entityId = as.getEntityId();
            String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            Object packet = buildDestroyPacket(entityId, version);
            if (packet == null) return;
            sendRawPacketToPlayer(packet, target, version);
        } catch (Exception ignored) {
        }
    }

    private Object buildDestroyPacket(int entityId, String version) {
        try {
            Class<?> packetClass;
            try {
                packetClass = Class.forName("net.minecraft.network.protocol.game.PacketPlayOutEntityDestroy");
            } catch (ClassNotFoundException ex) {
                packetClass = Class.forName("net.minecraft.server." + version + ".PacketPlayOutEntityDestroy");
            }
            try {
                return packetClass.getConstructor(int.class).newInstance(entityId);
            } catch (NoSuchMethodException e) {
                return packetClass.getConstructor(int[].class).newInstance((Object) new int[] { entityId });
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private void sendRawPacketToPlayer(Object packet, Player target, String version) {
        try {
            Object craftPlayer = target.getClass().getMethod("getHandle").invoke(target);
            Object playerConn = craftPlayer.getClass().getField("playerConnection").get(craftPlayer);
            playerConn.getClass().getMethod("sendPacket", getPacketClass(version)).invoke(playerConn, packet);
        } catch (Exception e2) {
            try {
                Object craftPlayer = target.getClass().getMethod("getHandle").invoke(target);
                Object conn = null;
                for (java.lang.reflect.Field f : craftPlayer.getClass().getFields()) {
                    String fname = f.getType().getSimpleName();
                    if (fname.equals("PlayerConnection") || fname.equals("ServerGamePacketListenerImpl")) {
                        conn = f.get(craftPlayer);
                        break;
                    }
                }
                if (conn == null) {
                    Class<?> cls = craftPlayer.getClass();
                    outer: while (cls != null) {
                        for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                            f.setAccessible(true);
                            String fname = f.getType().getSimpleName();
                            if (fname.equals("PlayerConnection")
                                    || fname.equals("ServerGamePacketListenerImpl")) {
                                conn = f.get(craftPlayer);
                                break outer;
                            }
                        }
                        cls = cls.getSuperclass();
                    }
                }
                if (conn != null) {
                    for (Method m : conn.getClass().getMethods()) {
                        if (m.getName().equals("sendPacket") || m.getName().equals("send")) {
                            if (m.getParameterCount() == 1) {
                                m.invoke(conn, packet);
                                break;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    private Class<?> getPacketClass(String version) {
        try {
            return Class.forName("net.minecraft.network.protocol.Packet");
        } catch (ClassNotFoundException e) {
            try {
                return Class.forName("net.minecraft.server." + version + ".Packet");
            } catch (ClassNotFoundException ex) {
                return Object.class;
            }
        }
    }

    private void despawnList(List<ArmorStand> stands) {
        if (stands == null)
            return;
        for (ArmorStand as : stands)
            if (as != null && !as.isDead())
                as.remove();
    }

    private void cleanAllStandingGhosts() {
        ConfigurationSection sec = standingCfg.getConfigurationSection("holograms");
        if (sec == null)
            return;
        for (String key : sec.getKeys(false)) {
            try {
                int id = Integer.parseInt(key);
                String path = "holograms." + id;
                String wn = standingCfg.getString(path + ".world");
                if (wn == null)
                    continue;
                World w = Bukkit.getWorld(wn);
                if (w == null)
                    continue;
                Location loc = new Location(w,
                        standingCfg.getDouble(path + ".x"),
                        standingCfg.getDouble(path + ".y"),
                        standingCfg.getDouble(path + ".z"));
                cleanGhostsNear(loc, 5.0);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void cleanGhostsNear(Location center, double radius) {
        if (center == null || center.getWorld() == null)
            return;
        try {
            for (org.bukkit.entity.Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
                if (!(e instanceof ArmorStand))
                    continue;
                ArmorStand as = (ArmorStand) e;
                if (as.isVisible() || as.hasBasePlate())
                    continue;
                String n = as.getCustomName();
                if (n == null || n.trim().isEmpty()
                        || n.contains("DAILY STREAK") || n.contains("WINSTREAK") || n.contains("TOP WINS") || n.contains("TOP KILLS")
                        || n.contains("Kit:") || n.contains("Resets in:") || n.contains("Lifetime")
                        || n.contains("✦") || n.contains("⚑") || n.contains("★"))
                    as.remove();
            }
        } catch (Exception ignored) {
        }
    }

    private void startGlowAnimation() {
        if (glowTask != null)
            glowTask.cancel();
        glowTask = new BukkitRunnable() {
            @Override
            public void run() {
                glowFrame++;

                for (Map.Entry<Integer, List<ArmorStand>> entry : new HashMap<>(standingHolograms).entrySet()) {
                    int id = entry.getKey();
                    List<ArmorStand> stands = entry.getValue();
                    if (stands == null || stands.isEmpty())
                        continue;
                    ArmorStand titleStand = stands.get(0);
                    if (titleStand == null || titleStand.isDead())
                        continue;

                    String category = standingCfg.getString("holograms." + id + ".category", "ws");
                    String period = standingCfg.getString("holograms." + id + ".period", "daily");
                    String periodLabel = period.substring(0, 1).toUpperCase() + period.substring(1);

                    String titleColor, titleLabel;
                    switch (category) {
                        case "ws":
                            titleColor = "&b";
                            titleLabel = "✦ DAILY STREAK ✦";
                            break;
                        case "wins":
                            titleColor = "&a";
                            titleLabel = "✦ TOP WINS ✦";
                            break;
                        default:
                            titleColor = "&c";
                            titleLabel = "✦ TOP KILLS ✦";
                            break;
                    }

                    titleStand.setCustomName(
                            color(buildGlowTitle(periodLabel + " " + titleLabel, titleColor, glowFrame)));
                }

                if (glowFrame % 20 == 0) {
                    for (Map.Entry<UUID, List<ArmorStand>> rehideEntry : new HashMap<>(playerHolograms).entrySet()) {
                        UUID ownerUUID = rehideEntry.getKey();
                        List<ArmorStand> hStands = rehideEntry.getValue();
                        if (hStands == null) continue;
                        for (ArmorStand hAs : hStands) {
                            if (hAs == null || hAs.isDead()) continue;
                            for (Player viewer : Bukkit.getOnlinePlayers()) {
                                if (viewer.getUniqueId().equals(ownerUUID)) continue;
                                try {
                                    viewer.hideEntity(plugin, hAs);
                                } catch (NoSuchMethodError | Exception ignored) {
                                    sendDestroyPacketToPlayer(hAs, viewer);
                                }
                            }
                        }
                    }
                }

                for (List<ArmorStand> stands : new ArrayList<>(playerHolograms.values())) {
                    if (stands == null || stands.size() < LINES_PLAYER + 1)
                        continue;

                    ArmorStand leftTitle = stands.get(0);
                    ArmorStand rightTitle = stands.get(LINES_PLAYER);

                    if (leftTitle != null && !leftTitle.isDead())
                        leftTitle.setCustomName(color(buildGlowTitle("✦ DAILY STREAK ✦", "&b", glowFrame)));
                    if (rightTitle != null && !rightTitle.isDead())
                        rightTitle.setCustomName(color(buildGlowTitle("✦ MONTHLY TOP WINS ✦", "&6", glowFrame)));
                }
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    private String buildGlowTitle(String text, String baseColor, int frame) {
        String dimColor = getDimColor(baseColor);
        int len = text.length();
        int glowPos = frame % (len + 6);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            int dist = Math.abs(i - glowPos);
            if (dist == 0) {
                sb.append("&f&l");
            } else if (dist == 1) {
                sb.append(baseColor).append("&l");
            } else {
                sb.append(dimColor).append("&l");
            }
            sb.append(text.charAt(i));
        }
        return sb.toString();
    }

    private String getDimColor(String baseColor) {
        switch (baseColor) {
            case "&b":
                return "&3";
            case "&a":
                return "&2";
            case "&c":
                return "&4";
            case "&6":
                return "&e";
            default:
                return "&8";
        }
    }

    private String strip(String s) {
        return ChatColor.stripColor(s).toLowerCase();
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
