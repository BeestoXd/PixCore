package com.pixra.pixcore.feature.match;

import com.pixra.pixcore.PixCore;
import com.pixra.pixcore.support.BlockCompatibility;
import com.pixra.pixcore.support.TitleUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MatchListener implements Listener {

    private static final double COUNTDOWN_ARENA_DISTANCE_SQUARED = 225.0D;
    private static final double COUNTDOWN_TELEPORT_DISTANCE_SQUARED = 25.0D;
    private static final double COUNTDOWN_TELEPORT_MATCH_DISTANCE_SQUARED = 9.0D;
    private static final long POST_MATCH_ARENA_STAY_TICKS = 60L;

    private static class BedPair {
        public final Location footLoc;
        public final Location headLoc;
        public final byte footData;
        public final byte headData;
        public final Material material;

        public BedPair(Location footLoc, Location headLoc, byte footData, byte headData, Material material) {
            this.footLoc = footLoc;
            this.headLoc = headLoc;
            this.footData = footData;
            this.headData = headData;
            this.material = material;
        }
    }

    private static final class ArenaSnapshotBlock {
        private final String worldName;
        private final int x;
        private final int y;
        private final int z;
        private final String materialName;
        private final String blockData;

        private ArenaSnapshotBlock(String worldName, int x, int y, int z, String materialName, String blockData) {
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.materialName = materialName;
            this.blockData = blockData;
        }
    }

    private final PixCore plugin;
    private final Map<String, List<BedPair>> cachedArenaBeds = new HashMap<>();
    private final Map<String, List<ArenaSnapshotBlock>> cachedArenaSnapshots = new HashMap<>();
    private final Map<String, int[]> cachedArenaSnapshotOrigins = new HashMap<>();

    private File customBedsFile;
    private org.bukkit.configuration.file.FileConfiguration customBedsConfig;
    private final List<BedPair> persistentBeds = new ArrayList<>();
    private File arenaSnapshotsFile;
    private org.bukkit.configuration.file.FileConfiguration arenaSnapshotsConfig;

    private final Set<Object> startedFights = new HashSet<>();
    private final Set<Object> partyCountdownInitializedFights = new HashSet<>();
    private final Map<Object, Long> fightStartTimes = new HashMap<>();
    private final Map<Object, Long> fightCountdownCooldown = new HashMap<>();
    private final Set<Object> partyCountdownFights = new HashSet<>();

    private final Set<UUID> matchEndedPlayers = new HashSet<>();
    private final Map<UUID, Location> pendingHubTeleports = new HashMap<>();
    private final Map<UUID, Location> pendingCountdownArenaTeleports = new HashMap<>();
    private final Map<UUID, Set<UUID>> matchPlayerGroups = new HashMap<>();
    private final Map<UUID, Long> matchEndTimes = new HashMap<>();
    private final Set<UUID> matchEndSpectators = new HashSet<>();
    private final Map<UUID, BukkitTask> matchEndFlightTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> matchEndStateTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> matchEndPearlBoardTasks = new HashMap<>();
    private final Map<UUID, Scoreboard> matchEndPearlBoards = new HashMap<>();
    private final Map<UUID, Float> matchEndOriginalFlySpeeds = new HashMap<>();
    private final Set<UUID> matchEndVanishedPlayers = new HashSet<>();

    private final Map<Object, int[]> bridge1v1Scores         = new HashMap<>();
    private final Map<UUID, Long>    bridge1v1PortalCooldown = new HashMap<>();
    private final Set<Object>        bridge1v1EndedFights    = new HashSet<>();

    private static final long[] ARENA_RESTORE_DELAYS = new long[]{0L, 20L, 80L, 120L};

    private final Map<Object, Map<String, BlockState>> arenaBlockChanges = new IdentityHashMap<>();
    private final Map<String, Object> arenaTrackedFights = new HashMap<>();

    private ItemStack createLobbyItem() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "Return to Lobby");
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isLobbyItem(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        return meta != null
                && (ChatColor.GREEN + "" + ChatColor.BOLD + "Return to Lobby").equals(meta.getDisplayName());
    }

    private Location resolveHubLocation(Player player) {
        try {
            Object spApi = plugin.getStrikePracticeAPI();
            if (spApi != null) {
                Location hub = (Location) spApi.getClass().getMethod("getSpawnLocation").invoke(spApi);
                if (hub != null) {
                    return hub.clone();
                }
            }
        } catch (Exception ignored) {}

        if (player != null && player.getWorld() != null) {
            return player.getWorld().getSpawnLocation();
        }
        return null;
    }

    private Location resolvePostMatchHub(Player player, Location preferredHub) {
        if (preferredHub != null) {
            return preferredHub.clone();
        }
        return resolveHubLocation(player);
    }

    private void forceHubTeleport(Player player, Location preferredHub) {
        if (player == null || !player.isOnline()) {
            return;
        }

        Location hub = resolvePostMatchHub(player, preferredHub);
        if (hub == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        final Location target = hub.clone();
        plugin.hubOnJoinSpawn.put(uuid, target.clone());
        attemptHubTeleport(player, target, 0);

        new BukkitRunnable() {
            @Override
            public void run() {
                Location lockedHub = plugin.hubOnJoinSpawn.get(uuid);
                if (lockedHub != null && isNearLocation(lockedHub, target, 1.0D)) {
                    plugin.hubOnJoinSpawn.remove(uuid);
                }
            }
        }.runTaskLater(plugin, 20L);
    }

    private void attemptHubTeleport(Player player, Location target, int attempt) {
        if (player == null || target == null || !player.isOnline()) {
            return;
        }

        if (!isNearLocation(player.getLocation(), target, 4.0D)) {
            try {
                player.teleport(target.clone());
            } catch (Exception ignored) {}
        }

        if (attempt >= 2) {
            return;
        }

        final int nextAttempt = attempt + 1;
        long delay = attempt == 0 ? 2L : 8L;
        new BukkitRunnable() {
            @Override
            public void run() {
                attemptHubTeleport(player, target, nextAttempt);
            }
        }.runTaskLater(plugin, delay);
    }

    private void normalizeMatchEndSpectatorSpeed(Player player) {
        if (player == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        matchEndOriginalFlySpeeds.putIfAbsent(uuid, player.getFlySpeed());

        float targetFlySpeed = Math.max(player.getFlySpeed(), player.getWalkSpeed());
        if (targetFlySpeed > player.getFlySpeed()) {
            try { player.setFlySpeed(Math.min(1.0f, targetFlySpeed)); } catch (IllegalArgumentException ignored) {}
        }
    }

    private void restoreMatchEndSpectatorSpeed(UUID uuid) {
        if (uuid == null) {
            return;
        }

        Float originalSpeed = matchEndOriginalFlySpeeds.remove(uuid);
        if (originalSpeed == null) {
            return;
        }

        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            try { player.setFlySpeed(originalSpeed); } catch (IllegalArgumentException ignored) {}
        }
    }

    private void startPearlFightEndScoreboard(Player player) {
        if (player == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        stopPearlFightEndScoreboard(uuid);
        plugin.lockedCustomSidebarPlayers.add(uuid);
        applyPearlFightEndScoreboard(player);

        BukkitTask task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline()
                        || !plugin.playerMatchResults.containsKey(uuid)
                        || !matchEndedPlayers.contains(uuid)
                        || ticks > 80) {
                    stopPearlFightEndScoreboard(uuid);
                    cancel();
                    return;
                }

                applyPearlFightEndScoreboard(player);
                ticks++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
        matchEndPearlBoardTasks.put(uuid, task);
    }

    private void stopPearlFightEndScoreboard(UUID uuid) {
        if (uuid == null) {
            return;
        }

        BukkitTask task = matchEndPearlBoardTasks.remove(uuid);
        boolean hadTask = task != null;
        if (hadTask) {
            task.cancel();
        }

        plugin.lockedCustomSidebarPlayers.remove(uuid);
        Scoreboard scoreboard = matchEndPearlBoards.remove(uuid);
        if (!hadTask && scoreboard == null) {
            return;
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline() && Bukkit.getScoreboardManager() != null) {
            try {
                if (scoreboard != null && player.getScoreboard() == scoreboard) {
                    player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                }
            } catch (Exception ignored) {}
        }
    }

    private void applyPearlFightEndScoreboard(Player player) {
        if (player == null || !player.isOnline() || Bukkit.getScoreboardManager() == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        Scoreboard scoreboard = matchEndPearlBoards.get(uuid);
        Objective objective = scoreboard != null ? scoreboard.getObjective("pixend") : null;
        if (scoreboard == null || objective == null) {
            scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            objective = scoreboard.registerNewObjective("pixend", "dummy");
            matchEndPearlBoards.put(uuid, scoreboard);
        }

        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&a&lPRACTICE"));

        String result = plugin.playerMatchResults.getOrDefault(uuid, "");
        String duration = plugin.playerMatchDurations.getOrDefault(uuid, "00:00");

        java.util.Set<String> expectedEntries = new java.util.HashSet<>();
        for (int score = 1; score <= 7; score++) {
            expectedEntries.add(ChatColor.values()[score].toString() + ChatColor.RESET);
        }
        for (String entry : new java.util.HashSet<>(scoreboard.getEntries())) {
            if (!expectedEntries.contains(entry)) {
                scoreboard.resetScores(entry);
            }
        }

        setPearlFightEndSidebarLine(scoreboard, objective, 7, "&7---------------------");
        setPearlFightEndSidebarLine(scoreboard, objective, 6, result);
        setPearlFightEndSidebarLine(scoreboard, objective, 5, "");
        setPearlFightEndSidebarLine(scoreboard, objective, 4, "&fDuration: &7" + duration);
        setPearlFightEndSidebarLine(scoreboard, objective, 3, "");
        setPearlFightEndSidebarLine(scoreboard, objective, 2, "&apixranetwork.asia");
        setPearlFightEndSidebarLine(scoreboard, objective, 1, "&7---------------------");
        if (player.getScoreboard() != scoreboard) {
            player.setScoreboard(scoreboard);
        }
    }

    private void setPearlFightEndSidebarLine(Scoreboard scoreboard, Objective objective, int score, String rawLine) {
        String entry = ChatColor.values()[score].toString() + ChatColor.RESET;
        Team team = scoreboard.getTeam("pfend" + score);
        if (team == null) {
            team = scoreboard.registerNewTeam("pfend" + score);
        }
        if (!team.hasEntry(entry)) {
            team.addEntry(entry);
        }

        String line = ChatColor.translateAlternateColorCodes('&', rawLine != null ? rawLine : "");
        String[] split = splitSidebarLine(line);
        team.setPrefix(split[0]);
        team.setSuffix(split[1]);
        objective.getScore(entry).setScore(score);
    }

    private String[] splitSidebarLine(String line) {
        if (line == null || line.isEmpty()) {
            return new String[] { "", "" };
        }
        if (line.length() <= 16) {
            return new String[] { line, "" };
        }

        int splitIndex = 16;
        if (line.charAt(splitIndex - 1) == ChatColor.COLOR_CHAR) {
            splitIndex--;
        }

        String prefix = line.substring(0, splitIndex);
        String suffix = ChatColor.getLastColors(prefix) + line.substring(splitIndex);
        if (suffix.length() > 16) {
            suffix = suffix.substring(0, 16);
            if (!suffix.isEmpty() && suffix.charAt(suffix.length() - 1) == ChatColor.COLOR_CHAR) {
                suffix = suffix.substring(0, suffix.length() - 1);
            }
        }
        return new String[] { prefix, suffix };
    }

    private void enableMatchEndSpectatorFlight(Player player) {
        if (player == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        cancelMatchEndSpectatorFlight(uuid);

        java.util.function.Consumer<Boolean> applyFlight = resetVelocity -> {
            if (!player.isOnline() || !matchEndSpectators.contains(uuid)) {
                return;
            }
            normalizeMatchEndSpectatorSpeed(player);
            player.setAllowFlight(true);
            try { player.setFlying(true); } catch (Exception ignored) {}
            player.setFallDistance(0f);
            if (Boolean.TRUE.equals(resetVelocity)) {
                // Only clear leftover knockback once so spectator movement stays responsive.
                player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            }
        };

        applyFlight.accept(true);
        BukkitTask task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !matchEndSpectators.contains(uuid) || ticks > 220) {
                    cancelMatchEndSpectatorFlight(uuid);
                    cancel();
                    return;
                }

                applyFlight.accept(false);
                ticks += 2;
            }
        }.runTaskTimer(plugin, 1L, 2L);
        matchEndFlightTasks.put(uuid, task);
    }

    private void cancelMatchEndSpectatorFlight(UUID uuid) {
        if (uuid == null) {
            return;
        }

        BukkitTask task = matchEndFlightTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    private void cancelMatchEndSpectatorState(UUID uuid) {
        if (uuid == null) {
            return;
        }

        BukkitTask task = matchEndStateTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    private void detachStrikePracticeSpectator(Player player) {
        if (player == null || !player.isOnline() || !plugin.isHooked()) {
            return;
        }

        try {
            plugin.removeSpectator(player, false);
        } catch (Exception ignored) {}
    }

    private boolean hasOnlyMatchEndLobbyItem(Player player) {
        if (player == null) {
            return false;
        }

        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (slot == 8) {
                if (!isLobbyItem(item)) {
                    return false;
                }
                continue;
            }

            if (item != null && item.getType() != Material.AIR) {
                return false;
            }
        }

        ItemStack[] armor = player.getInventory().getArmorContents();
        if (armor != null) {
            for (ItemStack item : armor) {
                if (item != null && item.getType() != Material.AIR) {
                    return false;
                }
            }
        }

        try {
            ItemStack offHand = player.getInventory().getItemInOffHand();
            if (offHand != null && offHand.getType() != Material.AIR) {
                return false;
            }
        } catch (NoSuchMethodError ignored) {}

        return true;
    }

    private void equipMatchEndLobbyItem(Player player, boolean forceReset) {
        if (player == null || !player.isOnline()) {
            return;
        }

        boolean changed = false;
        if (forceReset || !hasOnlyMatchEndLobbyItem(player)) {
            try { player.closeInventory(); } catch (Exception ignored) {}
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            try { player.getInventory().setItemInOffHand(null); } catch (NoSuchMethodError ignored) {}
            try { player.setItemOnCursor(null); } catch (Exception ignored) {}
            player.getInventory().setItem(8, createLobbyItem());
            changed = true;
        } else if (!isLobbyItem(player.getInventory().getItem(8))) {
            player.getInventory().setItem(8, createLobbyItem());
            changed = true;
        }

        try {
            if (player.getInventory().getHeldItemSlot() != 8) {
                player.getInventory().setHeldItemSlot(8);
                changed = true;
            }
        } catch (Exception ignored) {}

        if (changed) {
            player.updateInventory();
        }
    }

    private void enforceMatchEndSpectatorState(Player player, boolean resetVelocity, boolean detachSpectator) {
        if (player == null || !player.isOnline()) {
            return;
        }

        UUID uuid = player.getUniqueId();
        if (!matchEndSpectators.contains(uuid)) {
            return;
        }

        if (detachSpectator) {
            detachStrikePracticeSpectator(player);
        }
        plugin.frozenPlayers.remove(uuid);
        plugin.activeStartCountdownPlayers.remove(uuid);
        pendingCountdownArenaTeleports.remove(uuid);
        matchEndVanishedPlayers.add(uuid);
        normalizeMatchEndSpectatorSpeed(player);

        try { player.setGameMode(org.bukkit.GameMode.ADVENTURE); } catch (Exception ignored) {}
        player.setAllowFlight(true);
        try { player.setFlying(true); } catch (Exception ignored) {}
        player.setFallDistance(0f);
        player.setFireTicks(0);
        if (resetVelocity) {
            player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        }
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.INVISIBILITY,
                Integer.MAX_VALUE, 1, false, false));

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online != null && !online.equals(player)) {
                online.hidePlayer(player);
            }
        }

        equipMatchEndLobbyItem(player, resetVelocity);
    }

    private void startMatchEndSpectatorStateTask(Player player) {
        if (player == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        cancelMatchEndSpectatorState(uuid);
        enforceMatchEndSpectatorState(player, true, true);

        BukkitTask task = new BukkitRunnable() {
            long ticks = 0L;

            @Override
            public void run() {
                if (!player.isOnline()
                        || !matchEndSpectators.contains(uuid)
                        || ticks >= POST_MATCH_ARENA_STAY_TICKS) {
                    cancelMatchEndSpectatorState(uuid);
                    cancel();
                    return;
                }

                enforceMatchEndSpectatorState(player, false, ticks < 5L);
                ticks++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
        matchEndStateTasks.put(uuid, task);
    }

    private void applyMatchEndVanish(Player player) {
        startMatchEndSpectatorStateTask(player);
    }

    private boolean cleanupMatchEndVanish(Player player) {
        if (player == null) {
            return false;
        }

        UUID uuid = player.getUniqueId();
        cancelMatchEndSpectatorState(uuid);
        boolean wasVanished = matchEndVanishedPlayers.remove(uuid);
        if (!wasVanished) {
            return false;
        }

        player.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
        player.setAllowFlight(false);
        try { player.setFlying(false); } catch (Exception ignored) {}
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online != null) {
                online.showPlayer(player);
            }
        }
        return true;
    }

    public MatchListener(PixCore plugin) {
        this.plugin = plugin;
        loadCustomBeds();
        loadArenaSnapshots();
        registerStrikePracticeEvents();
    }

    private void markFightStarted(Object fight) {
        if (fight != null && !fightStartTimes.containsKey(fight)) {
            fightStartTimes.put(fight, System.currentTimeMillis());
        }
    }

    private String getEndedDuration(Object fight) {
        if (fight == null) {
            return "00:00";
        }

        Long startedAt = fightStartTimes.get(fight);
        if (startedAt == null) {
            return "00:00";
        }

        long totalSeconds = Math.max(0L, (System.currentTimeMillis() - startedAt) / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void storeFightDuration(Object fight, Iterable<Player> players) {
        String duration = getEndedDuration(fight);
        for (Player player : players) {
            if (player != null) {
                plugin.playerMatchDurations.put(player.getUniqueId(), duration);
            }
        }
    }

    private void storeFightDuration(Object fight, Player... players) {
        String duration = getEndedDuration(fight);
        for (Player player : players) {
            if (player != null) {
                plugin.playerMatchDurations.put(player.getUniqueId(), duration);
            }
        }
    }

    private void clearPlayerMatchEndState(UUID uid) {
        cancelMatchEndSpectatorFlight(uid);
        cancelMatchEndSpectatorState(uid);
        stopPearlFightEndScoreboard(uid);
        matchEndVanishedPlayers.remove(uid);
        matchEndSpectators.remove(uid);
        matchEndedPlayers.remove(uid);
        pendingHubTeleports.remove(uid);
        pendingCountdownArenaTeleports.remove(uid);
        matchEndTimes.remove(uid);
        matchPlayerGroups.remove(uid);
        matchPlayerGroups.values().forEach(group -> {
            if (group != null) group.remove(uid);
        });
        matchPlayerGroups.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isEmpty());
        plugin.playerMatchResults.remove(uid);
        plugin.playerMatchDurations.remove(uid);
        restoreMatchEndSpectatorSpeed(uid);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        clearPlayerMatchEndState(event.getPlayer().getUniqueId());
        if (plugin.hologramManager != null) {
            plugin.hologramManager.scheduleRehideForPlayer(event.getPlayer(), 2L, 20L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearPlayerMatchEndState(event.getPlayer().getUniqueId());
    }

    private void loadCustomBeds() {
        customBedsFile = new File(plugin.getDataFolder(), "custombeds.yml");
        if (!customBedsFile.exists()) {
            try {
                customBedsFile.createNewFile();
            } catch (Exception e) {
            }
        }
        customBedsConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(customBedsFile);

        persistentBeds.clear();
        if (customBedsConfig.contains("beds")) {
            for (String key : customBedsConfig.getConfigurationSection("beds").getKeys(false)) {
                String path = "beds." + key;
                Location footLoc = (Location) customBedsConfig.get(path + ".footLoc");
                Location headLoc = (Location) customBedsConfig.get(path + ".headLoc");
                byte footData = (byte) customBedsConfig.getInt(path + ".footData");
                byte headData = (byte) customBedsConfig.getInt(path + ".headData");
                if (footLoc != null && headLoc != null) {
                    persistentBeds.add(new BedPair(footLoc, headLoc, footData, headData, null));
                }
            }
        }
    }

    private String normalizeArenaName(String arenaName) {
        if (arenaName == null) return null;
        String normalized = arenaName.trim();
        if (normalized.isEmpty()) return null;
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String getArenaSnapshotName(Object arena) {
        if (arena == null || plugin.getHook() == null || plugin.getHook().getMArenaGetOriginalName() == null) {
            return getArenaName(arena);
        }

        try {
            Object originalName = plugin.getHook().getMArenaGetOriginalName().invoke(arena);
            if (originalName instanceof String && !((String) originalName).trim().isEmpty()) {
                return ((String) originalName).trim();
            }
        } catch (Exception ignored) {}

        return getArenaName(arena);
    }

    private String resolveBedCacheKey(String arenaName, Object arena) {
        String cacheName = arena != null ? getArenaSnapshotName(arena) : arenaName;
        if (cacheName == null || cacheName.trim().isEmpty()) {
            cacheName = arenaName;
        }
        return normalizeArenaName(cacheName);
    }

    private boolean isBedMaterialName(String materialName) {
        return materialName != null && (materialName.contains("BED_BLOCK") || materialName.endsWith("_BED"));
    }

    private boolean isBedBlock(Block block) {
        return block != null && isBedMaterialName(block.getType().name());
    }

    private Block findBedHalf(Block bedBlock) {
        if (!isBedBlock(bedBlock)) return null;

        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        for (BlockFace face : faces) {
            Block relative = bedBlock.getRelative(face);
            if (isBedBlock(relative)) {
                return relative;
            }
        }
        return null;
    }

    private List<Block> findConnectedBedCluster(Block origin) {
        List<Block> cluster = new ArrayList<>();
        if (!isBedBlock(origin)) return cluster;

        Deque<Block> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        queue.add(origin);

        while (!queue.isEmpty() && cluster.size() < 8) {
            Block block = queue.poll();
            String key = block != null ? getBlockKey(block.getLocation()) : null;
            if (key == null || !visited.add(key)) continue;
            if (!isBedBlock(block)) continue;

            cluster.add(block);
            for (BlockFace face : faces) {
                queue.add(block.getRelative(face));
            }
        }

        return cluster;
    }

    private Block findAdjacentBedInCluster(Block source, List<Block> cluster) {
        if (source == null || cluster == null) return null;

        String sourceKey = getBlockKey(source.getLocation());
        for (Block candidate : cluster) {
            if (candidate == null) continue;
            String candidateKey = getBlockKey(candidate.getLocation());
            if (sourceKey != null && sourceKey.equals(candidateKey)) continue;

            if (candidate.getWorld() != null
                    && source.getWorld() != null
                    && candidate.getWorld().getName().equals(source.getWorld().getName())
                    && candidate.getY() == source.getY()
                    && Math.abs(candidate.getX() - source.getX()) + Math.abs(candidate.getZ() - source.getZ()) == 1) {
                return candidate;
            }
        }
        return null;
    }

    private BedPair createBedPairFromCluster(List<Block> cluster) {
        if (cluster == null || cluster.isEmpty()) return null;

        for (Block block : cluster) {
            BedPair bedPair = createBedPair(block, findAdjacentBedInCluster(block, cluster));
            if (bedPair != null) {
                return bedPair;
            }
        }
        return null;
    }

    private void clearNearbyBedArtifacts(Location... anchors) {
        if (anchors == null || anchors.length == 0) return;

        Deque<Block> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

        for (Location anchor : anchors) {
            if (anchor == null || anchor.getWorld() == null) continue;
            Block base = anchor.getBlock();
            queue.add(base);
            for (BlockFace face : faces) {
                queue.add(base.getRelative(face));
            }
        }

        while (!queue.isEmpty() && visited.size() < 16) {
            Block block = queue.poll();
            String key = block != null ? getBlockKey(block.getLocation()) : null;
            if (key == null || !visited.add(key)) continue;
            if (!isBedBlock(block)) continue;

            BlockCompatibility.setBlockType(block, Material.AIR, false);
            for (BlockFace face : faces) {
                queue.add(block.getRelative(face));
            }
        }
    }

    private int[] getBedOffset(int dir) {
        int dx = 0;
        int dz = 0;
        if (dir == 0) dz = 1;
        else if (dir == 1) dx = -1;
        else if (dir == 2) dz = -1;
        else if (dir == 3) dx = 1;
        return new int[]{dx, dz};
    }

    private BedPair createBedPair(Block bedBlock, Block otherHalf) {
        if (!isBedBlock(bedBlock)) return null;

        Block counterpart = isBedBlock(otherHalf) ? otherHalf : findBedHalf(bedBlock);
        byte bedData = getBlockDataSafe(bedBlock);
        if (counterpart != null) {
            byte counterpartData = getBlockDataSafe(counterpart);
            if (bedData == 0 && counterpartData != 0) {
                bedData = counterpartData;
            }
        }

        int dir = bedData & 3;
        int[] offset = getBedOffset(dir);
        boolean isHead = (bedData & 8) == 8;

        Location blockLocation = bedBlock.getLocation();
        Location footLoc = isHead
                ? blockLocation.clone().add(-offset[0], 0, -offset[1])
                : blockLocation.clone();
        Location headLoc = isHead
                ? blockLocation.clone()
                : blockLocation.clone().add(offset[0], 0, offset[1]);

        Material material = bedBlock.getType();
        if (counterpart != null && isBedBlock(counterpart)) {
            material = counterpart.getType();
        }

        return new BedPair(footLoc, headLoc, (byte) dir, (byte) (dir | 8), material);
    }

    private boolean populateCachedBedsFromSnapshot(String arenaName, Object arena) {
        if (arena == null || plugin.arenaBoundaryManager == null) return false;

        String bedCacheKey = resolveBedCacheKey(arenaName, arena);
        String snapshotKey = getArenaSnapshotName(arena);
        String normalizedSnapshotKey = normalizeArenaName(snapshotKey);
        if (bedCacheKey == null || normalizedSnapshotKey == null) return false;

        List<ArenaSnapshotBlock> snapshotBlocks = cachedArenaSnapshots.get(normalizedSnapshotKey);
        int[] origin = cachedArenaSnapshotOrigins.get(normalizedSnapshotKey);
        if (snapshotBlocks == null || snapshotBlocks.isEmpty() || origin == null || origin.length < 6) return false;

        Location min = plugin.arenaBoundaryManager.getCorner1(arena);
        Location max = plugin.arenaBoundaryManager.getCorner2(arena);
        if (min == null || max == null || min.getWorld() == null) return false;

        int minX = Math.min(min.getBlockX(), max.getBlockX());
        int maxX = Math.max(min.getBlockX(), max.getBlockX());
        int minY = Math.min(min.getBlockY(), max.getBlockY());
        int maxY = Math.max(min.getBlockY(), max.getBlockY());
        int minZ = Math.min(min.getBlockZ(), max.getBlockZ());
        int maxZ = Math.max(min.getBlockZ(), max.getBlockZ());

        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;
        if ((origin[3] > 0 && origin[3] != sizeX)
                || (origin[4] > 0 && origin[4] != sizeY)
                || (origin[5] > 0 && origin[5] != sizeZ)) {
            return false;
        }

        Map<String, BedPair> bedMap = new HashMap<>();
        org.bukkit.World world = min.getWorld();
        for (ArenaSnapshotBlock snapshotBlock : snapshotBlocks) {
            if (snapshotBlock == null || !isBedMaterialName(snapshotBlock.materialName)) continue;

            Material material = Material.matchMaterial(snapshotBlock.materialName);
            if (material == null) continue;

            byte bedData = 0;
            if (snapshotBlock.blockData != null && !snapshotBlock.blockData.isEmpty()) {
                try {
                    bedData = BlockCompatibility.getBedDataFromSerialized(snapshotBlock.blockData);
                } catch (Exception ignored) {}
            }

            int dir = bedData & 3;
            int[] offset = getBedOffset(dir);
            boolean isHead = (bedData & 8) == 8;

            int dx = snapshotBlock.x - origin[0];
            int dy = snapshotBlock.y - origin[1];
            int dz = snapshotBlock.z - origin[2];
            Location currentLoc = new Location(world, minX + dx, minY + dy, minZ + dz);

            Location footLoc = isHead
                    ? currentLoc.clone().add(-offset[0], 0, -offset[1])
                    : currentLoc.clone();
            Location headLoc = isHead
                    ? currentLoc.clone()
                    : currentLoc.clone().add(offset[0], 0, offset[1]);

            BedPair bedPair = new BedPair(footLoc, headLoc, (byte) dir, (byte) (dir | 8), material);
            String blockKey = getBlockKey(bedPair.footLoc);
            if (blockKey != null && !bedMap.containsKey(blockKey)) {
                bedMap.put(blockKey, bedPair);
            }
        }

        if (bedMap.isEmpty()) return false;
        cachedArenaBeds.put(bedCacheKey, new ArrayList<>(bedMap.values()));
        return true;
    }

    private String getArenaSnapshotConfigKey(String arenaName) {
        String normalized = normalizeArenaName(arenaName);
        if (normalized == null) return null;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(normalized.getBytes(StandardCharsets.UTF_8));
    }

    private String encodeSnapshotValue(String value) {
        if (value == null || value.isEmpty()) return "";
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decodeSnapshotValue(String value) {
        if (value == null || value.isEmpty()) return "";
        try {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private String serializeArenaSnapshotBlock(ArenaSnapshotBlock block) {
        if (block == null) return null;
        return block.worldName + ";" + block.x + ";" + block.y + ";" + block.z + ";"
                + block.materialName + ";" + encodeSnapshotValue(block.blockData);
    }

    private ArenaSnapshotBlock deserializeArenaSnapshotBlock(String value) {
        if (value == null || value.isEmpty()) return null;

        String[] parts = value.split(";", 6);
        if (parts.length < 6) return null;

        try {
            return new ArenaSnapshotBlock(
                    parts[0],
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]),
                    parts[4],
                    decodeSnapshotValue(parts[5]));
        } catch (Exception ignored) {
            return null;
        }
    }

    private void loadArenaSnapshots() {
        arenaSnapshotsFile = new File(plugin.getDataFolder(), "arenasnapshots.yml");
        if (!arenaSnapshotsFile.exists()) {
            try {
                arenaSnapshotsFile.createNewFile();
            } catch (Exception ignored) {}
        }

        arenaSnapshotsConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(arenaSnapshotsFile);
        cachedArenaSnapshots.clear();
        cachedArenaSnapshotOrigins.clear();

        if (!arenaSnapshotsConfig.contains("arenas")) return;

        org.bukkit.configuration.ConfigurationSection section = arenaSnapshotsConfig.getConfigurationSection("arenas");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            String path = "arenas." + key;
            String arenaName = arenaSnapshotsConfig.getString(path + ".name");
            String normalizedArenaName = normalizeArenaName(arenaName);
            if (normalizedArenaName == null) continue;

            int minX = arenaSnapshotsConfig.getInt(path + ".origin.minX", Integer.MIN_VALUE);
            int minY = arenaSnapshotsConfig.getInt(path + ".origin.minY", Integer.MIN_VALUE);
            int minZ = arenaSnapshotsConfig.getInt(path + ".origin.minZ", Integer.MIN_VALUE);
            int sizeX = arenaSnapshotsConfig.getInt(path + ".origin.sizeX", -1);
            int sizeY = arenaSnapshotsConfig.getInt(path + ".origin.sizeY", -1);
            int sizeZ = arenaSnapshotsConfig.getInt(path + ".origin.sizeZ", -1);
            if (minX == Integer.MIN_VALUE || minY == Integer.MIN_VALUE || minZ == Integer.MIN_VALUE) continue;

            List<String> serializedBlocks = arenaSnapshotsConfig.getStringList(path + ".blocks");
            if (serializedBlocks == null || serializedBlocks.isEmpty()) continue;

            List<ArenaSnapshotBlock> blocks = new ArrayList<>();
            for (String serializedBlock : serializedBlocks) {
                ArenaSnapshotBlock block = deserializeArenaSnapshotBlock(serializedBlock);
                if (block != null) blocks.add(block);
            }

            if (!blocks.isEmpty()) {
                cachedArenaSnapshots.put(normalizedArenaName, blocks);
                cachedArenaSnapshotOrigins.put(normalizedArenaName, new int[]{minX, minY, minZ, sizeX, sizeY, sizeZ});
            }
        }
    }

    private void saveArenaSnapshotToDisk(String arenaName, List<ArenaSnapshotBlock> blocks, int[] origin) throws Exception {
        if (arenaSnapshotsConfig == null || arenaSnapshotsFile == null) {
            loadArenaSnapshots();
        }

        String configKey = getArenaSnapshotConfigKey(arenaName);
        if (configKey == null) throw new IllegalArgumentException("Arena name is empty");

        String path = "arenas." + configKey;
        List<String> serializedBlocks = new ArrayList<>();
        for (ArenaSnapshotBlock block : blocks) {
            String serialized = serializeArenaSnapshotBlock(block);
            if (serialized != null) serializedBlocks.add(serialized);
        }

        arenaSnapshotsConfig.set(path + ".name", arenaName);
        arenaSnapshotsConfig.set(path + ".origin.minX", origin[0]);
        arenaSnapshotsConfig.set(path + ".origin.minY", origin[1]);
        arenaSnapshotsConfig.set(path + ".origin.minZ", origin[2]);
        arenaSnapshotsConfig.set(path + ".origin.sizeX", origin[3]);
        arenaSnapshotsConfig.set(path + ".origin.sizeY", origin[4]);
        arenaSnapshotsConfig.set(path + ".origin.sizeZ", origin[5]);
        arenaSnapshotsConfig.set(path + ".blocks", serializedBlocks);
        arenaSnapshotsConfig.save(arenaSnapshotsFile);
    }

    private Object resolveArenaByName(String arenaName) {
        String normalized = normalizeArenaName(arenaName);
        if (normalized == null || plugin.getHook() == null) return null;

        try {
            Method apiGetArena = plugin.getHook().getMApiGetArenaByName();
            if (apiGetArena != null) {
                Object directArena = apiGetArena.invoke(plugin.getStrikePracticeAPI(), arenaName);
                if (directArena != null) return directArena;

                Object normalizedArena = apiGetArena.invoke(plugin.getStrikePracticeAPI(), normalized);
                if (normalizedArena != null) return normalizedArena;
            }
        } catch (Exception ignored) {}

        try {
            Method apiGetArenas = plugin.getHook().getMApiGetArenas();
            if (apiGetArenas == null) return null;

            Object result = apiGetArenas.invoke(plugin.getStrikePracticeAPI());
            if (!(result instanceof Iterable<?> arenas)) return null;

            for (Object arena : arenas) {
                String currentName = getArenaName(arena);
                if (normalized.equals(normalizeArenaName(currentName))) {
                    return arena;
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    private List<ArenaSnapshotBlock> captureArenaSnapshot(Object arena) {
        List<ArenaSnapshotBlock> snapshot = new ArrayList<>();
        if (arena == null || plugin.arenaBoundaryManager == null) return snapshot;

        Location min = plugin.arenaBoundaryManager.getCorner1(arena);
        Location max = plugin.arenaBoundaryManager.getCorner2(arena);
        if (min == null || max == null || min.getWorld() == null) return snapshot;

        int minX = Math.min(min.getBlockX(), max.getBlockX());
        int maxX = Math.max(min.getBlockX(), max.getBlockX());
        int minY = Math.min(min.getBlockY(), max.getBlockY());
        int maxY = Math.max(min.getBlockY(), max.getBlockY());
        int minZ = Math.min(min.getBlockZ(), max.getBlockZ());
        int maxZ = Math.max(min.getBlockZ(), max.getBlockZ());

        org.bukkit.World world = min.getWorld();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    String blockData = BlockCompatibility.serializeBlockData(block);
                    snapshot.add(new ArenaSnapshotBlock(
                            world.getName(), x, y, z, block.getType().name(), blockData));
                }
            }
        }

        return snapshot;
    }

    private boolean applyArenaSnapshot(Object arena, String snapshotKey) {
        String normalizedSnapshotKey = normalizeArenaName(snapshotKey);
        if (arena == null || normalizedSnapshotKey == null || plugin.arenaBoundaryManager == null) return false;

        List<ArenaSnapshotBlock> snapshotBlocks = cachedArenaSnapshots.get(normalizedSnapshotKey);
        int[] origin = cachedArenaSnapshotOrigins.get(normalizedSnapshotKey);
        if (snapshotBlocks == null || snapshotBlocks.isEmpty() || origin == null || origin.length < 6) return false;

        Location min = plugin.arenaBoundaryManager.getCorner1(arena);
        Location max = plugin.arenaBoundaryManager.getCorner2(arena);
        if (min == null || max == null || min.getWorld() == null) return false;

        int minX = Math.min(min.getBlockX(), max.getBlockX());
        int maxX = Math.max(min.getBlockX(), max.getBlockX());
        int minY = Math.min(min.getBlockY(), max.getBlockY());
        int maxY = Math.max(min.getBlockY(), max.getBlockY());
        int minZ = Math.min(min.getBlockZ(), max.getBlockZ());
        int maxZ = Math.max(min.getBlockZ(), max.getBlockZ());

        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;
        if ((origin[3] > 0 && origin[3] != sizeX)
                || (origin[4] > 0 && origin[4] != sizeY)
                || (origin[5] > 0 && origin[5] != sizeZ)) {
            return false;
        }

        org.bukkit.World world = min.getWorld();
        boolean applied = false;
        for (ArenaSnapshotBlock snapshotBlock : snapshotBlocks) {
            if (snapshotBlock == null) continue;

            Material material = Material.matchMaterial(snapshotBlock.materialName);
            if (material == null) continue;

            int dx = snapshotBlock.x - origin[0];
            int dy = snapshotBlock.y - origin[1];
            int dz = snapshotBlock.z - origin[2];

            Block block = world.getBlockAt(minX + dx, minY + dy, minZ + dz);
            try {
                if (material == Material.AIR) {
                    BlockCompatibility.setBlockType(block, Material.AIR, false);
                } else {
                    BlockCompatibility.setBlockType(block, material, false);
                    if (snapshotBlock.blockData != null && !snapshotBlock.blockData.isEmpty()) {
                        BlockCompatibility.applySerializedBlockData(block, snapshotBlock.blockData);
                    }
                }
                applied = true;
            } catch (Exception ignored) {}
        }

        return applied;
    }

    private boolean applyStoredArenaSnapshot(String arenaName, Object fight) {
        Object arena = null;
        try {
            if (fight != null && plugin.getMGetArena() != null) {
                arena = plugin.getMGetArena().invoke(fight);
            }
        } catch (Exception ignored) {}

        if (arena == null) {
            arena = resolveArenaByName(arenaName);
        }
        if (arena == null) return false;

        String snapshotKey = getArenaSnapshotName(arena);
        return applyArenaSnapshot(arena, snapshotKey);
    }

    private int[] getArenaSnapshotOrigin(Object arena) {
        if (arena == null || plugin.arenaBoundaryManager == null) return null;

        Location min = plugin.arenaBoundaryManager.getCorner1(arena);
        Location max = plugin.arenaBoundaryManager.getCorner2(arena);
        if (min == null || max == null) return null;

        int minX = Math.min(min.getBlockX(), max.getBlockX());
        int maxX = Math.max(min.getBlockX(), max.getBlockX());
        int minY = Math.min(min.getBlockY(), max.getBlockY());
        int maxY = Math.max(min.getBlockY(), max.getBlockY());
        int minZ = Math.min(min.getBlockZ(), max.getBlockZ());
        int maxZ = Math.max(min.getBlockZ(), max.getBlockZ());
        return new int[]{minX, minY, minZ, maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1};
    }

    private Object resolveArenaSnapshotSource(Object arena) {
        if (arena == null) return null;

        String snapshotName = getArenaSnapshotName(arena);
        if (snapshotName == null) return arena;

        Object sourceArena = resolveArenaByName(snapshotName);
        return sourceArena != null ? sourceArena : arena;
    }

    private boolean ensureArenaSnapshot(Object arena) {
        if (arena == null) return false;

        String snapshotName = getArenaSnapshotName(arena);
        String normalized = normalizeArenaName(snapshotName);
        if (normalized == null) return false;
        if (cachedArenaSnapshots.containsKey(normalized) && cachedArenaSnapshotOrigins.containsKey(normalized)) {
            return true;
        }

        Object sourceArena = resolveArenaSnapshotSource(arena);
        List<ArenaSnapshotBlock> snapshotBlocks = captureArenaSnapshot(sourceArena);
        int[] origin = getArenaSnapshotOrigin(sourceArena);
        if (snapshotBlocks.isEmpty() || origin == null) return false;

        cachedArenaSnapshots.put(normalized, snapshotBlocks);
        cachedArenaSnapshotOrigins.put(normalized, origin);
        try {
            saveArenaSnapshotToDisk(snapshotName, snapshotBlocks, origin);
            return true;
        } catch (Exception ex) {
            cachedArenaSnapshots.remove(normalized);
            cachedArenaSnapshotOrigins.remove(normalized);
            return false;
        }
    }

    private void clearTrackedArenaState(String arenaName) {
        String normalized = normalizeArenaName(arenaName);
        if (normalized == null) return;

        List<String> keysToRemove = new ArrayList<>();
        for (Map.Entry<String, Object> entry : arenaTrackedFights.entrySet()) {
            if (!normalized.equals(normalizeArenaName(entry.getKey()))) continue;
            if (entry.getValue() != null) {
                arenaBlockChanges.remove(entry.getValue());
            }
            keysToRemove.add(entry.getKey());
        }

        for (String key : keysToRemove) {
            arenaTrackedFights.remove(key);
        }
    }

    public boolean saveArenaSnapshot(String arenaName) {
        Object arena = resolveArenaByName(arenaName);
        if (arena == null) return false;

        Object sourceArena = resolveArenaSnapshotSource(arena);
        String resolvedArenaName = getArenaSnapshotName(sourceArena);
        List<ArenaSnapshotBlock> snapshotBlocks = captureArenaSnapshot(sourceArena);
        int[] origin = getArenaSnapshotOrigin(sourceArena);
        if (snapshotBlocks.isEmpty() || origin == null) return false;

        String normalized = normalizeArenaName(resolvedArenaName);
        cachedArenaSnapshots.put(normalized, snapshotBlocks);
        cachedArenaSnapshotOrigins.put(normalized, origin);
        try {
            saveArenaSnapshotToDisk(resolvedArenaName, snapshotBlocks, origin);
            return true;
        } catch (Exception ex) {
            cachedArenaSnapshots.remove(normalized);
            cachedArenaSnapshotOrigins.remove(normalized);
            return false;
        }
    }

    public boolean restoreArenaSnapshot(String arenaName) {
        Object arena = resolveArenaByName(arenaName);
        String snapshotKey = arena != null ? getArenaSnapshotName(arena) : arenaName;
        String normalized = normalizeArenaName(snapshotKey);
        if (normalized == null || !cachedArenaSnapshots.containsKey(normalized)) return false;

        boolean restored = arena != null ? applyArenaSnapshot(arena, snapshotKey) : applyStoredArenaSnapshot(arenaName, null);
        if (restored) {
            clearTrackedArenaState(snapshotKey);
        }
        return restored;
    }

    public int getArenaSnapshotBlockCount(String arenaName) {
        Object arena = resolveArenaByName(arenaName);
        String snapshotKey = arena != null ? getArenaSnapshotName(arena) : arenaName;
        List<ArenaSnapshotBlock> snapshotBlocks = cachedArenaSnapshots.get(normalizeArenaName(snapshotKey));
        return snapshotBlocks != null ? snapshotBlocks.size() : 0;
    }

    private void saveCustomBed(BedPair bed) {
        persistentBeds.add(bed);
        String key = java.util.UUID.randomUUID().toString();
        String path = "beds." + key;
        customBedsConfig.set(path + ".footLoc", bed.footLoc);
        customBedsConfig.set(path + ".headLoc", bed.headLoc);
        customBedsConfig.set(path + ".footData", bed.footData);
        customBedsConfig.set(path + ".headData", bed.headData);
        try {
            customBedsConfig.save(customBedsFile);
        } catch (Exception e) {
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpecialBedPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        Player player = event.getPlayer();
        org.bukkit.inventory.ItemStack item = event.getItemInHand();

        if (item != null && item.getType().name().contains("BED") && item.hasItemMeta()
                && item.getItemMeta().hasDisplayName()) {
            if (item.getItemMeta().getDisplayName().contains("Arena Bed Fixer")) {
                Block foot = event.getBlockPlaced();

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (foot.getType().name().contains("BED")) {
                            byte footData = getBlockDataSafe(foot);
                            int dir = footData & 3;
                            int dx = 0, dz = 0;
                            if (dir == 0)
                                dz = 1;
                            else if (dir == 1)
                                dx = -1;
                            else if (dir == 2)
                                dz = -1;
                            else if (dir == 3)
                                dx = 1;

                            Block head = foot.getRelative(dx, 0, dz);
                            if (head.getType().name().contains("BED")) {
                                byte headData = getBlockDataSafe(head);
                                BedPair newBed = new BedPair(foot.getLocation(), head.getLocation(), footData,
                                        headData, foot.getType());
                                saveCustomBed(newBed);
                                player.sendMessage(ChatColor.GREEN
                                        + "[PixCore] Custom Bed saved successfully! It will perfectly restore in this arena.");
                            } else {
                                player.sendMessage(ChatColor.RED
                                        + "[PixCore] Failed to save bed. Make sure it is placed completely.");
                            }
                        }
                    }
                }.runTaskLater(plugin, 2L);
            }
        }
    }

    private void registerStrikePracticeEvents() {
        try {
            Class<? extends Event> duelStartClass = (Class<? extends Event>) Class
                    .forName("ga.strikepractice.events.DuelStartEvent");
            Bukkit.getPluginManager().registerEvent(duelStartClass, this, EventPriority.MONITOR,
                    (listener, event) -> handleDuelStart(event), plugin);

            Class<? extends Event> duelEndClass = (Class<? extends Event>) Class
                    .forName("ga.strikepractice.events.DuelEndEvent");
            Bukkit.getPluginManager().registerEvent(duelEndClass, this, EventPriority.LOWEST,
                    (listener, event) -> markDuelEndPlayers(event), plugin);
            Bukkit.getPluginManager().registerEvent(duelEndClass, this, EventPriority.MONITOR,
                    (listener, event) -> handleDuelEnd(event), plugin);

            Class<? extends Event> roundEndClass = (Class<? extends Event>) Class
                    .forName("ga.strikepractice.events.RoundEndEvent");
            Bukkit.getPluginManager().registerEvent(roundEndClass, this, EventPriority.MONITOR,
                    (listener, event) -> handleRoundEnd(event), plugin);

            try {

                Class<? extends Event> roundStartClass = (Class<? extends Event>) Class
                        .forName("ga.strikepractice.events.RoundStartEvent");
                Bukkit.getPluginManager().registerEvent(roundStartClass, this, EventPriority.HIGHEST,
                        (listener, event) -> handleRoundStart(event), plugin);
            } catch (Exception ignored) {
                plugin.getLogger().warning("[MatchListener] Could not register RoundStartEvent — leavematch cleanup may be incomplete.");
            }

            try {
                Class<? extends Event> kitSelectClass = (Class<? extends Event>) Class
                        .forName("ga.strikepractice.events.KitSelectEvent");
                Bukkit.getPluginManager().registerEvent(kitSelectClass, this, EventPriority.MONITOR,
                        (listener, event) -> handlePartyKitSelect(event), plugin);
            } catch (Exception ignored) {
                plugin.getLogger()
                        .warning("[MatchListener] Could not register KitSelectEvent — party countdown may not work.");
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Could not register StrikePractice custom events.");
        }
    }

    @SuppressWarnings("unchecked")
    private void handlePartyKitSelect(Event event) {
        if (!plugin.startCountdownEnabled)
            return;
        try {
            Player player = (Player) event.getClass().getMethod("getPlayer").invoke(event);
            if (player == null || !player.isOnline())
                return;
            if (!plugin.isHooked() || !plugin.isInFight(player))
                return;

            Object fight = plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), player);
            if (fight == null)
                return;

            boolean isParty = (plugin.partySplitManager != null && plugin.partySplitManager.isPartySplit(fight))
                    || (plugin.partyVsPartyManager != null && plugin.partyVsPartyManager.isPartyVsParty(fight));
            if (!isParty)
                return;

            if (partyCountdownFights.contains(fight))
                return;
            partyCountdownFights.add(fight);
            fightCountdownCooldown.put(fight, System.currentTimeMillis());

            if (partyCountdownInitializedFights.add(fight)) {
                for (Player pp : resolveAllFightPlayers(fight, new ArrayList<>())) {
                    if (pp != null) {
                        plugin.playerMatchKills.put(pp.getUniqueId(), 0);
                        plugin.playerMatchResults.remove(pp.getUniqueId());
                        plugin.playerMatchDurations.remove(pp.getUniqueId());
                    }
                }
            }

            final Object finalFight = fight;
            final List<Player> allPlayers = resolveAllFightPlayers(fight, new ArrayList<>());

            String partyKit = null;
            if (!allPlayers.isEmpty() && allPlayers.get(0) != null) {
                partyKit = plugin.getKitName(allPlayers.get(0));
                if (partyKit == null) {
                    try {
                        Object k = fight.getClass().getMethod("getKit").invoke(fight);
                        if (k != null)
                            partyKit = (String) k.getClass().getMethod("getName").invoke(k);
                    } catch (Exception ignored) {
                    }
                }
            }
            startMatchCountdown(allPlayers, finalFight, true, partyKit);

            new BukkitRunnable() {
                @Override
                public void run() {
                    partyCountdownFights.remove(finalFight);
                }
            }.runTaskLater(plugin, 20L * 60 * 10);

        } catch (Exception ignored) {
        }
    }

    private byte getBlockDataSafe(Block block) {
        return BlockCompatibility.getBlockDataSafe(block);
    }

    private void placeBedRobust(Location footLoc, Location headLoc, byte footData, byte headData, Material bedMaterial) {
        try {
            if (!footLoc.getChunk().isLoaded())
                footLoc.getChunk().load();
            if (!headLoc.getChunk().isLoaded())
                headLoc.getChunk().load();

            Block foot = footLoc.getBlock();
            Block head = headLoc.getBlock();
            clearNearbyBedArtifacts(footLoc, headLoc);

            org.bukkit.Material legacyBedMat = null;
            try {
                legacyBedMat = org.bukkit.Material.valueOf("BED_BLOCK");
            } catch (Exception ignored) {}

            if (legacyBedMat != null) {
                try {
                    Method setTypeIdAndData = Block.class.getMethod("setTypeIdAndData", int.class, byte.class, boolean.class);
                    Method getId = org.bukkit.Material.class.getMethod("getId");
                    int bedId = (Integer) getId.invoke(legacyBedMat);
                    setTypeIdAndData.invoke(foot, 0, (byte) 0, false);
                    setTypeIdAndData.invoke(head, 0, (byte) 0, false);
                    setTypeIdAndData.invoke(foot, bedId, footData, false);
                    setTypeIdAndData.invoke(head, bedId, headData, false);
                    return;
                } catch (Exception ignored) {}
            }

            org.bukkit.Material mat = bedMaterial;
            if (mat == null || !mat.name().endsWith("_BED")) {
                try { mat = org.bukkit.Material.valueOf("WHITE_BED"); } catch (Exception ignored) {}
            }
            if (mat == null) return;

            BlockCompatibility.setBlockType(foot, org.bukkit.Material.AIR, false);
            BlockCompatibility.setBlockType(head, org.bukkit.Material.AIR, false);
            BlockCompatibility.setBlockType(foot, mat, false);
            BlockCompatibility.setBlockType(head, mat, false);

            BlockCompatibility.configureModernBedState(foot, head, footData, headData);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMoveDuringCountdown(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!plugin.frozenPlayers.contains(player.getUniqueId()))
            return;

        if (event instanceof org.bukkit.event.player.PlayerTeleportEvent) {

            if (plugin.hologramManager != null
                    && plugin.activeStartCountdownPlayers.contains(player.getUniqueId()))
                plugin.hologramManager.clearPlayerHologram(player);
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null)
            return;
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ())
            return;

        Location locked = to.clone();
        locked.setX(from.getX());
        locked.setZ(from.getZ());
        event.setTo(locked);
        player.setFallDistance(0f);
        Vector velocity = player.getVelocity();
        if (velocity.getX() != 0.0D || velocity.getZ() != 0.0D) {
            player.setVelocity(new Vector(0, velocity.getY(), 0));
        }
    }

    @SuppressWarnings("unchecked")
    private String getPartyLeaderName(Object fight, Player referencePlayer) {
        if (referencePlayer == null)
            return null;
        try {
            if (plugin.partyVsPartyManager != null && plugin.partyVsPartyManager.isPartyVsParty(fight)) {
                boolean inParty1 = plugin.partyVsPartyManager.isInParty1(referencePlayer, fight);
                Method getPartyMethod = fight.getClass().getMethod(inParty1 ? "getParty1" : "getParty2");
                Object party = getPartyMethod.invoke(fight);
                if (party != null) {
                    for (String m : new String[] { "getLeader", "getLeaderName", "getLeaderUUID" }) {
                        try {
                            Object leader = party.getClass().getMethod(m).invoke(party);
                            if (leader instanceof Player)
                                return plugin.getEffectivePlayerName((Player) leader);
                            if (leader instanceof String) {
                                Player namedLeader = Bukkit.getPlayerExact((String) leader);
                                return namedLeader != null ? plugin.getEffectivePlayerName(namedLeader) : (String) leader;
                            }
                            if (leader instanceof java.util.UUID) {
                                Player lp = Bukkit.getPlayer((java.util.UUID) leader);
                                if (lp != null)
                                    return plugin.getEffectivePlayerName(lp);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    List<Player> members = plugin.partyVsPartyManager.getParty1Players(fight);
                    if (!inParty1)
                        members = plugin.partyVsPartyManager.getParty2Players(fight);
                    if (!members.isEmpty())
                        return plugin.getEffectivePlayerName(members.get(0));
                }
            }

            if (plugin.partySplitManager != null && plugin.partySplitManager.isPartySplit(fight)) {
                boolean inTeam1 = plugin.partySplitManager.isInTeam1(referencePlayer, fight);
                List<Player> team = inTeam1
                        ? plugin.partySplitManager.getTeam1Players(fight)
                        : plugin.partySplitManager.getTeam2Players(fight);
                if (!team.isEmpty())
                    return plugin.getEffectivePlayerName(team.get(0));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Location tryGetArenaSpawnForPlayer(Player p, Object fight) {
        try {
            if (plugin.getMGetArena() == null) return null;
            Object arena = plugin.getMGetArena().invoke(fight);
            if (arena == null) return null;

            java.lang.reflect.Method mS1 = plugin.getHook().getMArenaGetSpawn1();
            java.lang.reflect.Method mS2 = plugin.getHook().getMArenaGetSpawn2();
            if (mS1 == null || mS2 == null) return null;

            Location spawn1 = (Location) mS1.invoke(arena);
            Location spawn2 = (Location) mS2.invoke(arena);

            if (plugin.getMGetFirstPlayer() != null) {
                try {
                    Player first = (Player) plugin.getMGetFirstPlayer().invoke(fight);
                    if (p.equals(first) && spawn1 != null) return spawn1.clone();
                } catch (Exception ignored) {}
            }
            if (plugin.getMGetSecondPlayer() != null) {
                try {
                    Player second = (Player) plugin.getMGetSecondPlayer().invoke(fight);
                    if (p.equals(second) && spawn2 != null) return spawn2.clone();
                } catch (Exception ignored) {}
            }

            if (spawn1 != null && spawn2 != null
                    && p.getWorld() != null && p.getWorld().equals(spawn1.getWorld())) {
                double d1 = p.getLocation().distanceSquared(spawn1);
                double d2 = p.getLocation().distanceSquared(spawn2);
                return (d1 <= d2 ? spawn1 : spawn2).clone();
            }
            if (spawn1 != null) return spawn1.clone();
            if (spawn2 != null) return spawn2.clone();

        } catch (Exception ignored) {}
        return null;
    }

    private boolean isLocationInsideArena(Location location, Object arena) {
        if (location == null || arena == null) {
            return false;
        }

        try {
            Location corner1 = plugin.arenaBoundaryManager != null ? plugin.arenaBoundaryManager.getCorner1(arena) : null;
            Location corner2 = plugin.arenaBoundaryManager != null ? plugin.arenaBoundaryManager.getCorner2(arena) : null;
            if (corner1 != null && corner2 != null
                    && corner1.getWorld() != null
                    && location.getWorld() != null
                    && corner1.getWorld().equals(location.getWorld())) {
                int minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
                int maxX = Math.max(corner1.getBlockX(), corner2.getBlockX());
                int minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
                int maxZ = Math.max(corner1.getBlockZ(), corner2.getBlockZ());
                int x = location.getBlockX();
                int z = location.getBlockZ();
                return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
            }
        } catch (Exception ignored) {}

        return false;
    }

    private boolean isArenaSpawnCompatible(Player player, Object fight, Location storedSpawn) {
        if (player == null || fight == null || storedSpawn == null) {
            return false;
        }

        Location liveArenaSpawn = tryGetArenaSpawnForPlayer(player, fight);
        if (liveArenaSpawn != null) {
            return isNearLocation(storedSpawn, liveArenaSpawn, COUNTDOWN_ARENA_DISTANCE_SQUARED);
        }

        if (plugin.getMGetArena() == null) {
            return false;
        }

        try {
            Object arena = plugin.getMGetArena().invoke(fight);
            return isLocationInsideArena(storedSpawn, arena);
        } catch (Exception ignored) {
            return false;
        }
    }

    private Location getCompatibleArenaSpawn(Player player, Object fight, Location storedSpawn) {
        if (storedSpawn == null) {
            return null;
        }
        return isArenaSpawnCompatible(player, fight, storedSpawn) ? storedSpawn : null;
    }

    private void clearStaleArenaSpawnCache(Player player, Object fight) {
        if (player == null || fight == null) {
            return;
        }

        UUID uid = player.getUniqueId();
        Location cachedArenaSpawn = plugin.arenaSpawnLocations.get(uid);
        if (cachedArenaSpawn != null && !isArenaSpawnCompatible(player, fight, cachedArenaSpawn)) {
            plugin.arenaSpawnLocations.remove(uid);
        }

        Location pendingArenaSpawn = pendingCountdownArenaTeleports.get(uid);
        if (pendingArenaSpawn != null && !isArenaSpawnCompatible(player, fight, pendingArenaSpawn)) {
            pendingCountdownArenaTeleports.remove(uid);
        }
    }

    private boolean isNearLocation(Location current, Location target, double maxDistanceSquared) {
        return current != null
                && target != null
                && current.getWorld() != null
                && current.getWorld().equals(target.getWorld())
                && current.distanceSquared(target) <= maxDistanceSquared;
    }

    private boolean isPlayerNearArenaSpawn(Player player, Location arenaSpawn, double maxDistanceSquared) {
        return player != null && isNearLocation(player.getLocation(), arenaSpawn, maxDistanceSquared);
    }

    private Location getCountdownArenaSpawn(Player player, Object fight, boolean isPartyFight) {
        if (player == null) {
            return null;
        }

        UUID uid = player.getUniqueId();
        Location target = getCompatibleArenaSpawn(player, fight, pendingCountdownArenaTeleports.get(uid));
        if (target == null && !isPartyFight) {
            target = tryGetArenaSpawnForPlayer(player, fight);
        }
        if (target == null) {
            target = getCompatibleArenaSpawn(player, fight, plugin.arenaSpawnLocations.get(uid));
        }
        if (target == null) {
            return null;
        }

        Location safeTarget = plugin.resolveSafeArenaLocation(target.clone());
        return safeTarget != null ? safeTarget : target.clone();
    }

    private Location rememberCountdownArenaSpawn(Player player, Object fight, boolean isPartyFight) {
        Location target = getCountdownArenaSpawn(player, fight, isPartyFight);
        if (target == null) {
            return null;
        }

        Location stored = target.clone();
        pendingCountdownArenaTeleports.put(player.getUniqueId(), stored.clone());
        plugin.arenaSpawnLocations.put(player.getUniqueId(), stored.clone());
        return stored;
    }

    private void enforceCountdownArenaSpawn(Player player, Location arenaSpawn) {
        if (player == null || !player.isOnline() || arenaSpawn == null) {
            return;
        }

        UUID uid = player.getUniqueId();
        if (!plugin.activeStartCountdownPlayers.contains(uid)
                || plugin.leavingMatchPlayers.contains(uid)
                || matchEndedPlayers.contains(uid)) {
            pendingCountdownArenaTeleports.remove(uid);
            return;
        }

        if (isPlayerNearArenaSpawn(player, arenaSpawn, COUNTDOWN_TELEPORT_DISTANCE_SQUARED)) {
            return;
        }

        if (plugin.teleportToSafeArenaLocation(player, arenaSpawn)) {
            plugin.applySafeArenaTeleportProtection(player);
        }
    }

    @SuppressWarnings("unchecked")
    public void handleDuelStart(Event event) {
        try {
            Object fight = event.getClass().getMethod("getFight").invoke(event);
            if (fight == null)
                return;

            if (plugin.duelScoreManager != null) {
                plugin.duelScoreManager.onFightStart(fight);
            }

            Object arena = (plugin.getMGetArena() != null) ? plugin.getMGetArena().invoke(fight) : null;
            List<Player> players = new ArrayList<>();

            if (plugin.getMGetPlayersInFight() != null) {
                try {
                    List<Player> found = (List<Player>) plugin.getMGetPlayersInFight().invoke(fight);
                    if (found != null)
                        players.addAll(found);
                } catch (Exception e) {
                }
            }

            if (players.isEmpty() && plugin.getMGetPlayers() != null) {
                try {
                    List<Player> found = (List<Player>) plugin.getMGetPlayers().invoke(fight);
                    if (found != null)
                        players.addAll(found);
                } catch (Exception e) {
                }
            }

            if (players.isEmpty()) {
                try {
                    List<Player> found = (List<Player>) fight.getClass().getMethod("getPlayers").invoke(fight);
                    if (found != null)
                        players.addAll(found);
                } catch (NoSuchMethodException e) {
                    if (plugin.getMGetFirstPlayer() != null)
                        players.add((Player) plugin.getMGetFirstPlayer().invoke(fight));
                    if (plugin.getMGetSecondPlayer() != null)
                        players.add((Player) plugin.getMGetSecondPlayer().invoke(fight));
                }
            }
            players.removeIf(p -> p == null);

            if (!players.isEmpty()) {
                String kitName = plugin.getKitName(players.get(0));
                if (kitName == null) {
                    try {
                        Object kit = fight.getClass().getMethod("getKit").invoke(fight);
                        if (kit != null)
                            kitName = (String) kit.getClass().getMethod("getName").invoke(kit);
                    } catch (Exception e) {
                    }
                }

                boolean isBridge = kitName != null && plugin.isRoundScoredKit(kitName);

                for (Player p : players) {
                    pendingCountdownArenaTeleports.remove(p.getUniqueId());
                    // Bridge uses the same arena for every round, so the cached spawn is
                    // still valid. Clearing it here (before teleportBridge1v1PlayersToSpawns
                    // runs) removes the only reliable spawn reference, forcing the code to
                    // fall back to a distance-based guess that picks the wrong spawn when
                    // the scoring player is near the opponent's portal.
                    if (!isBridge) {
                        clearStaleArenaSpawnCache(p, fight);
                    }
                }

                if (kitName != null
                        && plugin.bestofConfig != null
                        && (plugin.isRoundScoredKit(kitName) || kitName.toLowerCase().contains("stickfight"))) {
                    try {
                        Object bestOf = fight.getClass().getMethod("getBestOf").invoke(fight);
                        if (bestOf != null) {
                            int scoreLimit = plugin.getBestOfScoreLimit(kitName, -1);
                            if (scoreLimit > 0) {
                                int newRounds = 2 * scoreLimit - 1;
                                java.lang.reflect.Field roundsField = null;
                                Class<?> c = bestOf.getClass();
                                while (c != null && c != Object.class) {
                                    try {
                                        roundsField = c.getDeclaredField("rounds");
                                        break;
                                    } catch (NoSuchFieldException ignored) {}
                                    c = c.getSuperclass();
                                }
                                if (roundsField != null) {
                                    roundsField.setAccessible(true);
                                    roundsField.set(bestOf, newRounds);
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }

                boolean isFirstRound = !startedFights.contains(fight);
                if (isFirstRound) {
                    startedFights.add(fight);
                    markFightStarted(fight);
                    if (plugin.boxingTrackerManager != null) {
                        plugin.boxingTrackerManager.onFightStart(fight, players, kitName);
                    }
                    if (plugin.pearlFightManager != null) {
                        plugin.pearlFightManager.onFightStart(fight, players, kitName);
                    }
                    for (Player p : players) {
                        plugin.playerMatchKills.put(p.getUniqueId(), 0);
                        plugin.playerMatchResults.remove(p.getUniqueId());
                        plugin.playerMatchDurations.remove(p.getUniqueId());
                    }

                    if (plugin.matchDurationManager != null
                            && kitName != null
                            && (plugin.pearlFightManager == null || !plugin.pearlFightManager.isPearlFightKit(kitName))) {
                        plugin.matchDurationManager.startTimer(fight, kitName);
                    }
                }

                final boolean isPartyFight = (plugin.partySplitManager != null
                        && plugin.partySplitManager.isPartySplit(fight))
                        || (plugin.partyVsPartyManager != null && plugin.partyVsPartyManager.isPartyVsParty(fight))
                        || (plugin.partyFFAManager != null && plugin.partyFFAManager.isPartyFFA(fight));

                if (isPartyFight) {
                    final Object fightRef = fight;
                    final List<Player> seedPlayers = new ArrayList<>(players);
                    Runnable storeSpawns = () -> {
                        for (Player pp : resolveAllFightPlayers(fightRef, seedPlayers)) {
                            if (pp != null && pp.isOnline()
                                    && !plugin.arenaSpawnLocations.containsKey(pp.getUniqueId())) {
                                plugin.arenaSpawnLocations.put(pp.getUniqueId(), pp.getLocation().clone());
                            }
                        }
                    };
                    storeSpawns.run();
                    for (long d : new long[] { 3L, 8L, 20L }) {
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                storeSpawns.run();
                            }
                        }.runTaskLater(plugin, d);
                    }
                } else {

                    final Object fightRef2 = fight;
                    final List<Player> seedPlayers2 = new ArrayList<>(players);

                    for (Player pp : players) {
                        if (pp == null || !pp.isOnline()) continue;
                        Location arenaSpawn = tryGetArenaSpawnForPlayer(pp, fightRef2);
                        if (arenaSpawn != null) {
                            Location safeArenaSpawn = plugin.resolveSafeArenaLocation(arenaSpawn);
                            plugin.arenaSpawnLocations.put(pp.getUniqueId(),
                                    safeArenaSpawn != null ? safeArenaSpawn : arenaSpawn.clone());
                        }
                    }

                    for (long d : new long[] { 20L, 40L }) {
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                for (Player pp : resolveAllFightPlayers(fightRef2, seedPlayers2)) {
                                    if (pp != null && pp.isOnline()
                                            && !plugin.arenaSpawnLocations.containsKey(pp.getUniqueId())) {
                                        Location sp = tryGetArenaSpawnForPlayer(pp, fightRef2);
                                        if (sp != null) {
                                            Location safeArenaSpawn = plugin.resolveSafeArenaLocation(sp);
                                            plugin.arenaSpawnLocations.put(pp.getUniqueId(),
                                                    safeArenaSpawn != null ? safeArenaSpawn : sp.clone());
                                        }
                                    }
                                }
                            }
                        }.runTaskLater(plugin, d);
                    }
                }

                long now = System.currentTimeMillis();
                long lastTime = fightCountdownCooldown.getOrDefault(fight, 0L);

                if (now - lastTime > 4000L) {
                    if (isFirstRound || !isBridge) {
                        if (!isPartyFight) {
                            fightCountdownCooldown.put(fight, now);
                            startMatchCountdown(players, fight, false, kitName, !isFirstRound);
                        }
                    }
                }

                if (plugin.getMSetBedwars() != null && kitName != null) {
                    try {
                        Player p = players.get(0);
                        boolean isBed = false;

                        List<String> bedRestoreKits = plugin.getConfig().getStringList("settings.bed-restore-kits");
                        if (bedRestoreKits != null && !bedRestoreKits.isEmpty()) {
                            for (String kit : bedRestoreKits) {
                                if (kit.equalsIgnoreCase(kitName)) {
                                    isBed = true;
                                    break;
                                }
                            }
                        } else {
                            String kitLower = kitName.toLowerCase();
                            isBed = kitLower.contains("bed")
                                    || kitLower.contains("fireball")
                                    || kitLower.contains("mlgrush");
                        }

                        if (!isBed && plugin.respawnChatCountdownKits != null) {
                            for (String k : plugin.respawnChatCountdownKits)
                                if (k.equalsIgnoreCase(kitName))
                                    isBed = true;
                        }

                        if (isBed) {
                            Object kit = plugin.getStrikePracticeAPI().getClass().getMethod("getKit", Player.class)
                                    .invoke(plugin.getStrikePracticeAPI(), p);
                            plugin.getMSetBedwars().invoke(kit, true);

                            if (arena != null) {
                                try {
                                    ensureArenaSnapshot(arena);
                                    String arenaName = getArenaName(arena);
                                    rememberArenaFight(arenaName, fight);
                                    ensureFightBedsCached(fight);

                                    scheduleArenaSnapshotRefresh(arenaName, fight, 2L, 20L);
                                    forceFixBeds(arenaName, fight, 10L);

                                } catch (Exception ex) {
                                }
                            }
                        }
                    } catch (Exception ex) {
                    }
                }
            }
        } catch (Exception e) {
        }
    }

    private void saveArenaBeds(String arenaName, Object arena) {
        if (plugin.arenaBoundaryManager == null)
            return;
        String cacheKey = resolveBedCacheKey(arenaName, arena);
        if (cacheKey == null)
            return;
        Location min = plugin.arenaBoundaryManager.getCorner1(arena);
        Location max = plugin.arenaBoundaryManager.getCorner2(arena);
        if (min == null || max == null)
            return;

        int minX = Math.min(min.getBlockX(), max.getBlockX());
        int maxX = Math.max(min.getBlockX(), max.getBlockX());
        int minY = Math.min(min.getBlockY(), max.getBlockY());
        int maxY = Math.max(min.getBlockY(), max.getBlockY());
        int minZ = Math.min(min.getBlockZ(), max.getBlockZ());
        int maxZ = Math.max(min.getBlockZ(), max.getBlockZ());

        Map<String, BedPair> bedMap = new HashMap<>();
        Set<String> visitedBedBlocks = new HashSet<>();
        org.bukkit.World world = min.getWorld();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block b = world.getBlockAt(x, y, z);
                    if (!isBedBlock(b))
                        continue;

                    String blockKey = getBlockKey(b.getLocation());
                    if (blockKey != null && visitedBedBlocks.contains(blockKey)) {
                        continue;
                    }

                    List<Block> cluster = findConnectedBedCluster(b);
                    for (Block clusterBlock : cluster) {
                        String clusterKey = getBlockKey(clusterBlock.getLocation());
                        if (clusterKey != null) {
                            visitedBedBlocks.add(clusterKey);
                        }
                    }

                    BedPair bedPair = createBedPairFromCluster(cluster);
                    String key = bedPair != null ? getBlockKey(bedPair.footLoc) : null;
                    if (bedPair != null && key != null && !bedMap.containsKey(key)) {
                        bedMap.put(key, bedPair);
                    }
                }
            }
        }

        if (!bedMap.isEmpty()) {
            cachedArenaBeds.put(cacheKey, new ArrayList<>(bedMap.values()));
        }
    }

    private String getArenaName(Object arena) {
        if (arena == null) return null;
        try {
            return (String) arena.getClass().getMethod("getName").invoke(arena);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String getBlockKey(Location location) {
        if (location == null || location.getWorld() == null) return null;
        return location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() + ","
                + location.getWorld().getName();
    }

    private void rememberArenaFight(String arenaName, Object fight) {
        if (arenaName == null || arenaName.isEmpty() || fight == null) return;
        arenaTrackedFights.put(arenaName, fight);
    }

    private boolean isInsideArenaBounds(Object arena, Location location) {
        if (arena == null || location == null || location.getWorld() == null || plugin.arenaBoundaryManager == null) {
            return false;
        }

        Location min = plugin.arenaBoundaryManager.getCorner1(arena);
        Location max = plugin.arenaBoundaryManager.getCorner2(arena);
        if (min == null || max == null || min.getWorld() == null) return false;
        if (!min.getWorld().getName().equalsIgnoreCase(location.getWorld().getName())) return false;

        int minX = Math.min(min.getBlockX(), max.getBlockX());
        int maxX = Math.max(min.getBlockX(), max.getBlockX());
        int minY = Math.min(min.getBlockY(), max.getBlockY());
        int maxY = Math.max(min.getBlockY(), max.getBlockY());
        int minZ = Math.min(min.getBlockZ(), max.getBlockZ());
        int maxZ = Math.max(min.getBlockZ(), max.getBlockZ());

        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    private Object findArenaByLocation(Location location) {
        if (location == null || plugin.getHook() == null || plugin.getHook().getMApiGetArenas() == null) return null;
        try {
            Object result = plugin.getHook().getMApiGetArenas().invoke(plugin.getStrikePracticeAPI());
            if (!(result instanceof Iterable<?> arenas)) return null;

            for (Object arena : arenas) {
                if (isInsideArenaBounds(arena, location)) {
                    return arena;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Object resolveTrackedFight(String arenaName, Object preferredFight) {
        if (preferredFight != null) {
            rememberArenaFight(arenaName, preferredFight);
            return preferredFight;
        }
        if (arenaName == null || arenaName.isEmpty()) return null;

        Object trackedFight = arenaTrackedFights.get(arenaName);
        if (trackedFight != null) return trackedFight;

        if (!plugin.isHooked() || plugin.getMGetFight() == null || plugin.getMGetArena() == null) return null;

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!plugin.isInFight(online)) continue;
            try {
                Object fight = plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), online);
                if (fight == null) continue;

                Object arena = plugin.getMGetArena().invoke(fight);
                String currentArenaName = getArenaName(arena);
                if (arenaName.equals(currentArenaName)) {
                    rememberArenaFight(arenaName, fight);
                    return fight;
                }
            } catch (Exception ignored) {}
        }

        return null;
    }

    private void trackArenaBlockState(Object fight, String arenaName, BlockState originalState) {
        if (fight == null || arenaName == null || arenaName.isEmpty() || originalState == null) return;

        String key = getBlockKey(originalState.getLocation());
        if (key == null) return;

        rememberArenaFight(arenaName, fight);
        arenaBlockChanges.computeIfAbsent(fight, ignored -> new HashMap<>()).putIfAbsent(key, originalState);
    }

    private void trackArenaBlockStates(Object fight, String arenaName, Iterable<Block> blocks) {
        if (blocks == null) return;
        for (Block block : blocks) {
            if (block == null) continue;
            trackArenaBlockState(fight, arenaName, block.getState());
        }
    }

    private void restoreArenaBlocks(String arenaName, Object fight, boolean clearAfterRestore) {
        Object trackedFight = resolveTrackedFight(arenaName, fight);
        if (trackedFight == null) {
            if (clearAfterRestore && arenaName != null) arenaTrackedFights.remove(arenaName);
            return;
        }

        Map<String, BlockState> changes = arenaBlockChanges.get(trackedFight);
        if (changes == null || changes.isEmpty()) {
            if (clearAfterRestore) {
                arenaBlockChanges.remove(trackedFight);
                if (arenaName != null && arenaTrackedFights.get(arenaName) == trackedFight) {
                    arenaTrackedFights.remove(arenaName);
                }
            }
            return;
        }

        List<BlockState> snapshot = new ArrayList<>(changes.values());
        for (BlockState state : snapshot) {
            try {
                state.update(true, false);
            } catch (Exception ignored) {}
        }

        if (clearAfterRestore) {
            arenaBlockChanges.remove(trackedFight);
            if (arenaName != null && arenaTrackedFights.get(arenaName) == trackedFight) {
                arenaTrackedFights.remove(arenaName);
            }
        }
    }

    private void scheduleArenaRestore(String arenaName, Object fight) {
        if (arenaName == null || arenaName.isEmpty()) return;
        rememberArenaFight(arenaName, fight);

        for (int i = 0; i < ARENA_RESTORE_DELAYS.length; i++) {
            final boolean clearAfterRestore = i == ARENA_RESTORE_DELAYS.length - 1;
            long delay = ARENA_RESTORE_DELAYS[i];

            new BukkitRunnable() {
                @Override
                public void run() {
                    resetCachedBlockChanges(fight);
                    restoreArenaBlocks(arenaName, fight, clearAfterRestore);
                    if (clearAfterRestore) {
                        applyStoredArenaSnapshot(arenaName, fight);
                    }
                }
            }.runTaskLater(plugin, delay);
        }
    }

    private void scheduleArenaSnapshotRefresh(String arenaName, Object fight, long... delays) {
        if (arenaName == null || arenaName.isEmpty() || delays == null || delays.length == 0) return;

        for (long delay : delays) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    applyStoredArenaSnapshot(arenaName, fight);
                }
            }.runTaskLater(plugin, delay);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlaceTrackForReset(BlockPlaceEvent event) {
        if (!plugin.isHooked()) return;
        Player player = event.getPlayer();
        if (!plugin.isInFight(player)) return;
        if (plugin.getMGetArena() == null || plugin.getMGetFight() == null) return;
        try {
            Object fight = plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), player);
            if (fight == null) return;
            Object arena = plugin.getMGetArena().invoke(fight);
            if (arena == null) return;
            String arenaName = getArenaName(arena);
            if (arenaName == null) return;

            BlockState original = event.getBlockReplacedState();
            trackArenaBlockState(fight, arenaName, original);
        } catch (Exception ignored) {}
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreakTrackForReset(org.bukkit.event.block.BlockBreakEvent event) {
        if (!plugin.isHooked()) return;
        Player player = event.getPlayer();
        if (!plugin.isInFight(player)) return;
        if (plugin.getMGetArena() == null || plugin.getMGetFight() == null) return;
        try {
            Object fight = plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), player);
            if (fight == null) return;
            Object arena = plugin.getMGetArena().invoke(fight);
            if (arena == null) return;
            String arenaName = getArenaName(arena);
            if (arenaName == null) return;

            Block block = event.getBlock();
            trackArenaBlockState(fight, arenaName, block.getState());
        } catch (Exception ignored) {}
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplodeTrackForReset(EntityExplodeEvent event) {
        if (!plugin.isHooked()) return;
        List<Block> explodedBlocks = event.blockList();
        if (explodedBlocks == null || explodedBlocks.isEmpty()) return;

        Object arena = findArenaByLocation(explodedBlocks.get(0).getLocation());
        if (arena == null) arena = findArenaByLocation(event.getLocation());

        String arenaName = getArenaName(arena);
        if (arenaName == null) return;

        Object fight = resolveTrackedFight(arenaName, null);
        if (fight == null) return;

        trackArenaBlockStates(fight, arenaName, explodedBlocks);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplodeTrackForReset(BlockExplodeEvent event) {
        if (!plugin.isHooked()) return;
        List<Block> explodedBlocks = event.blockList();
        if (explodedBlocks == null || explodedBlocks.isEmpty()) return;

        Object arena = findArenaByLocation(event.getBlock().getLocation());
        if (arena == null) arena = findArenaByLocation(explodedBlocks.get(0).getLocation());

        String arenaName = getArenaName(arena);
        if (arenaName == null) return;

        Object fight = resolveTrackedFight(arenaName, null);
        if (fight == null) return;

        trackArenaBlockStates(fight, arenaName, explodedBlocks);
    }

    private void forceFixBeds(String arenaName, Object fight, long delayTicks) {
        new BukkitRunnable() {
            @Override
            public void run() {
                resetCachedBlockChanges(fight);
                executeBedRestore(arenaName, fight);
            }
        }.runTaskLater(plugin, delayTicks);

        new BukkitRunnable() {
            @Override
            public void run() {
                resetCachedBlockChanges(fight);
                executeBedRestore(arenaName, fight);
            }
        }.runTaskLater(plugin, delayTicks + 40L);
    }

    public void ensureFightBedsCached(Object fight) {
        if (fight == null || plugin.getMGetArena() == null) {
            return;
        }

        try {
            Object arena = plugin.getMGetArena().invoke(fight);
            if (arena == null) {
                return;
            }

            String arenaName = getArenaName(arena);
            if (arenaName == null || arenaName.isEmpty()) {
                return;
            }

            rememberArenaFight(arenaName, fight);
            String cacheKey = resolveBedCacheKey(arenaName, arena);
            if (cacheKey == null) {
                return;
            }

            cachedArenaBeds.remove(cacheKey);
            ensureArenaSnapshot(arena);
            if (!populateCachedBedsFromSnapshot(arenaName, arena)) {
                saveArenaBeds(arenaName, arena);
            }
        } catch (Exception ignored) {}
    }

    public void rememberBrokenBedForFight(Object fight, Block bedBlock, Block otherHalf) {
        if (fight == null || bedBlock == null || plugin.getMGetArena() == null) {
            return;
        }

        try {
            Object arena = plugin.getMGetArena().invoke(fight);
            if (arena == null) {
                return;
            }

            String arenaName = getArenaName(arena);
            if (arenaName == null || arenaName.isEmpty()) {
                return;
            }

            rememberArenaFight(arenaName, fight);
            String cacheKey = resolveBedCacheKey(arenaName, arena);
            if (cacheKey == null) {
                return;
            }

            ensureFightBedsCached(fight);
        } catch (Exception ignored) {}
    }

    public void restoreFightBedsForRound(Object fight, long delayTicks) {
        if (fight == null) {
            return;
        }

        try {
            ensureFightBedsCached(fight);
            if (plugin.getMGetArena() == null) {
                return;
            }
            Object arena = plugin.getMGetArena().invoke(fight);
            if (arena == null) {
                return;
            }
            String arenaName = getArenaName(arena);
            if (arenaName == null || arenaName.isEmpty()) {
                return;
            }
            forceFixBeds(arenaName, fight, Math.max(0L, delayTicks));
        } catch (Exception ignored) {}
    }

    private void resetCachedBlockChanges(Object fight) {
        if (fight == null) return;
        java.lang.reflect.Method mGetBlockChanges = plugin.getHook().getMGetBlockChanges();
        if (mGetBlockChanges == null) return;
        try {
            Object result = mGetBlockChanges.invoke(fight);
            if (!(result instanceof Iterable)) return;
            for (Object change : (Iterable<?>) result) {
                if (change == null) continue;
                try {
                    boolean supported = (boolean) change.getClass().getMethod("isResetSupported").invoke(change);
                    if (!supported) continue;
                    try {
                        change.getClass().getMethod("reset", boolean.class).invoke(change, true);
                    } catch (Exception ignored) {
                        change.getClass().getMethod("reset").invoke(change);
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    private void executeBedRestore(String arenaName, Object fight) {
        List<BedPair> bedsToRestore = new ArrayList<>();
        boolean usingPersistent = false;
        Object arena = null;

        try {
            arena = (plugin.getMGetArena() != null && fight != null) ? plugin.getMGetArena().invoke(fight)
                    : null;
            if (arena != null && plugin.arenaBoundaryManager != null) {
                Location min = plugin.arenaBoundaryManager.getCorner1(arena);
                Location max = plugin.arenaBoundaryManager.getCorner2(arena);
                if (min != null && max != null) {
                    int minX = Math.min(min.getBlockX(), max.getBlockX());
                    int maxX = Math.max(min.getBlockX(), max.getBlockX());
                    int minZ = Math.min(min.getBlockZ(), max.getBlockZ());
                    int maxZ = Math.max(min.getBlockZ(), max.getBlockZ());

                    for (BedPair b : persistentBeds) {
                        Location loc = b.footLoc;
                        if (loc.getWorld().getName().equals(min.getWorld().getName()) &&
                                loc.getBlockX() >= minX && loc.getBlockX() <= maxX &&
                                loc.getBlockZ() >= minZ && loc.getBlockZ() <= maxZ) {

                            bedsToRestore.add(b);
                            usingPersistent = true;
                        }
                    }
                }
            }
        } catch (Exception e) {
        }

        String cacheKey = resolveBedCacheKey(arenaName, arena);
        if (!usingPersistent && cacheKey != null && !cachedArenaBeds.containsKey(cacheKey) && arena != null) {
            populateCachedBedsFromSnapshot(arenaName, arena);
        }

        if (!usingPersistent && cacheKey != null && cachedArenaBeds.containsKey(cacheKey)) {
            bedsToRestore.addAll(cachedArenaBeds.get(cacheKey));
        }

        for (BedPair bed : bedsToRestore) {
            placeBedRobust(bed.footLoc, bed.headLoc, bed.footData, bed.headData, bed.material);
        }

        if (fight != null && plugin.getClsAbstractFight() != null && plugin.getClsAbstractFight().isInstance(fight)) {
            try {
                if (plugin.getFBed1Broken() != null)
                    plugin.getFBed1Broken().set(fight, false);
                if (plugin.getFBed2Broken() != null)
                    plugin.getFBed2Broken().set(fight, false);
            } catch (Exception ex) {
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Player> getPartyAllPlayers(Object fight) {
        if (plugin.partySplitManager != null && plugin.partySplitManager.isPartySplit(fight)) {
            return plugin.partySplitManager.getAllPlayers(fight);
        }
        if (plugin.partyVsPartyManager != null && plugin.partyVsPartyManager.isPartyVsParty(fight)) {
            return plugin.partyVsPartyManager.getAllPlayers(fight);
        }
        return new java.util.ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private List<Player> resolveAllFightPlayers(Object fight, List<Player> seed) {
        List<Player> result = new ArrayList<>();

        for (Player p : seed) {
            if (p != null && p.isOnline() && !result.contains(p))
                result.add(p);
        }

        for (Player p : getPartyAllPlayers(fight)) {
            if (p != null && p.isOnline() && !result.contains(p))
                result.add(p);
        }

        if (plugin.isHooked() && plugin.getMGetFight() != null && plugin.getStrikePracticeAPI() != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (result.contains(p))
                    continue;
                try {
                    if (!plugin.isInFight(p))
                        continue;
                    Object pFight = plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), p);
                    if (fight.equals(pFight))
                        result.add(p);
                } catch (Exception ignored) {
                }
            }
        }

        return result;
    }

    private void startMatchCountdown(List<Player> players, Object fight, boolean isPartyFight) {
        startMatchCountdown(players, fight, isPartyFight, null, false);
    }

    private void startMatchCountdown(List<Player> players, Object fight, boolean isPartyFight, String kitName) {
        startMatchCountdown(players, fight, isPartyFight, kitName, false);
    }

    private void startMatchCountdown(List<Player> players, Object fight, boolean isPartyFight, String kitName, boolean isRoundTransition) {
        final Object finalFight = fight;
        final boolean finalIsParty = isPartyFight;
        final String finalKit = kitName;
        final boolean finalIsRoundTransition = isRoundTransition;

        if (!isPartyFight) {
            for (Player p : players) {
                if (p == null || !p.isOnline()) continue;
                if (!plugin.leavingMatchPlayers.contains(p.getUniqueId())) continue;
                Player opp = null;
                for (Player other : players) {
                    if (other != null && other.isOnline() && !other.getUniqueId().equals(p.getUniqueId())) {
                        opp = other; break;
                    }
                }
                handleBestOfLeave(fight, p, opp, kitName);
                return;
            }
        }

        final List<Player> countdownPlayers = finalIsParty
                ? resolveAllFightPlayers(fight, players)
                : new ArrayList<>(players);

        final Set<UUID> countdownActive = new HashSet<>();
        final Map<UUID, Location> lobbyPositions = new HashMap<>();
        for (Player p : countdownPlayers) {
            if (p != null && p.isOnline()) {
                plugin.frozenPlayers.add(p.getUniqueId());
                if (finalIsRoundTransition) plugin.roundTransitionPlayers.add(p.getUniqueId());
                countdownActive.add(p.getUniqueId());
                plugin.activeStartCountdownPlayers.add(p.getUniqueId());
                lobbyPositions.put(p.getUniqueId(), p.getLocation().clone());
                Location countdownArenaSpawn = rememberCountdownArenaSpawn(p, finalFight, finalIsParty);
                if (countdownArenaSpawn != null) {
                    final Player countdownPlayer = p;
                    final Location targetSpawn = countdownArenaSpawn.clone();
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            enforceCountdownArenaSpawn(countdownPlayer, targetSpawn);
                        }
                    }.runTaskLater(plugin, 1L);
                }
                plugin.syncLayoutInstant(p, 2);
            }
        }

        final int maxSeconds = finalIsRoundTransition ? 3 : plugin.startCountdownDuration;
        final int countdownRepairMaxTicks = Math.max(60, (maxSeconds + 2) * 20);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks += 2;
                if (ticks > countdownRepairMaxTicks) {
                    cancel();
                    return;
                }
                boolean hasActiveCountdownPlayer = false;
                for (Player p : new ArrayList<>(countdownPlayers)) {
                    if (p == null || !p.isOnline())
                        continue;
                    if (!countdownActive.contains(p.getUniqueId()))
                        continue;
                    if (!plugin.activeStartCountdownPlayers.contains(p.getUniqueId()))
                        continue;
                    hasActiveCountdownPlayer = true;
                    if (plugin.hologramManager == null)
                        continue;
                    if (plugin.hologramManager.hasPlayerHologram(p))
                        continue;
                    Location lobby = lobbyPositions.get(p.getUniqueId());
                    Location arenaSpawn = rememberCountdownArenaSpawn(p, finalFight, finalIsParty);

                    if (arenaSpawn != null && isPlayerNearArenaSpawn(p, arenaSpawn, COUNTDOWN_ARENA_DISTANCE_SQUARED)) {
                        if (!finalIsRoundTransition) {
                            plugin.hologramManager.showForPlayer(p, finalKit);
                        }
                        continue;
                    }

                    boolean lobbyIsArena = lobby != null && arenaSpawn != null
                            && lobby.getWorld() != null
                            && lobby.getWorld().equals(arenaSpawn.getWorld())
                            && lobby.distanceSquared(arenaSpawn) < 9.0;
                    if (!lobbyIsArena && lobby != null && p.getWorld().equals(lobby.getWorld())
                            && p.getLocation().distanceSquared(lobby) < 9.0) {
                        continue;
                    }

                    if (arenaSpawn != null && !isPlayerNearArenaSpawn(p, arenaSpawn, COUNTDOWN_ARENA_DISTANCE_SQUARED)) {
                        continue;
                    }

                    if (!finalIsRoundTransition) {
                        plugin.hologramManager.showForPlayer(p, finalKit);
                    }
                }
                if (!hasActiveCountdownPlayer)
                    cancel();
            }
        }.runTaskTimer(plugin, 2L, 2L);

        for (long delay : new long[] { 1L, 3L, 8L, 20L, 40L, 60L }) {
            final long thisDelay = delay;
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (finalIsParty) {
                        for (Player lp : resolveAllFightPlayers(finalFight, countdownPlayers)) {
                            if (lp != null && lp.isOnline() && !countdownPlayers.contains(lp)) {
                                countdownPlayers.add(lp);
                                countdownActive.add(lp.getUniqueId());
                                plugin.activeStartCountdownPlayers.add(lp.getUniqueId());
                                plugin.frozenPlayers.add(lp.getUniqueId());
                                lobbyPositions.put(lp.getUniqueId(), lp.getLocation().clone());
                                rememberCountdownArenaSpawn(lp, finalFight, true);
                            }
                        }
                    }
                    for (Player fp : new ArrayList<>(countdownPlayers)) {
                        if (fp != null && fp.isOnline()
                                && countdownActive.contains(fp.getUniqueId())
                                && plugin.activeStartCountdownPlayers.contains(fp.getUniqueId())) {
                            plugin.frozenPlayers.add(fp.getUniqueId());
                            Location countdownArenaSpawn = rememberCountdownArenaSpawn(fp, finalFight, finalIsParty);
                            enforceCountdownArenaSpawn(fp, countdownArenaSpawn);
                            if (plugin.hologramManager != null) {
                                Location lobby = lobbyPositions.get(fp.getUniqueId());
                                Location arenaSpawnDel = countdownArenaSpawn;

                                boolean lobbyIsArena2 = lobby != null && arenaSpawnDel != null
                                        && lobby.getWorld() != null
                                        && lobby.getWorld().equals(arenaSpawnDel.getWorld())
                                        && lobby.distanceSquared(arenaSpawnDel) < 9.0;

                                boolean playerNearArena = arenaSpawnDel != null
                                        && isPlayerNearArenaSpawn(fp, arenaSpawnDel, COUNTDOWN_ARENA_DISTANCE_SQUARED);
                                boolean notAtLobby = playerNearArena
                                        || lobbyIsArena2
                                        || lobby == null
                                        || !fp.getWorld().equals(lobby.getWorld())
                                        || fp.getLocation().distanceSquared(lobby) >= 9.0;
                                if (notAtLobby) {

                                    boolean nearArena = arenaSpawnDel == null
                                            || isPlayerNearArenaSpawn(fp, arenaSpawnDel, COUNTDOWN_ARENA_DISTANCE_SQUARED);
                                    if (nearArena) {

                                        if (thisDelay >= 20L || !plugin.hologramManager.hasPlayerHologram(fp)) {

                                            if (!finalIsRoundTransition) {
                                                plugin.hologramManager.showForPlayer(fp, finalKit);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }.runTaskLater(plugin, delay);
        }

        new BukkitRunnable() {
            int current = maxSeconds;

            @Override
            public void run() {
                if (plugin.endedFightWinners.containsKey(finalFight)) {
                    for (Player p : new ArrayList<>(countdownPlayers)) {
                        if (p == null) continue;
                        countdownActive.remove(p.getUniqueId());
                        plugin.activeStartCountdownPlayers.remove(p.getUniqueId());
                        pendingCountdownArenaTeleports.remove(p.getUniqueId());
                        plugin.frozenPlayers.remove(p.getUniqueId());
                        plugin.roundTransitionPlayers.remove(p.getUniqueId());
                        if (plugin.hologramManager != null)
                            plugin.hologramManager.clearPlayerHologram(p);
                    }
                    cancel();
                    return;
                }
                if (finalIsParty) {
                    for (Player lp : resolveAllFightPlayers(finalFight, countdownPlayers)) {
                        if (lp != null && lp.isOnline() && !countdownPlayers.contains(lp)) {
                            countdownPlayers.add(lp);
                            countdownActive.add(lp.getUniqueId());
                            plugin.activeStartCountdownPlayers.add(lp.getUniqueId());
                            plugin.frozenPlayers.add(lp.getUniqueId());
                            rememberCountdownArenaSpawn(lp, finalFight, true);
                        }
                    }
                }

                if (current <= 0) {
                    if (finalIsParty && !finalIsRoundTransition) {
                        if (!fightStartTimes.containsKey(finalFight)) {
                            markFightStarted(finalFight);
                        }

                        String timerKit = finalKit;
                        if (timerKit == null || timerKit.isEmpty()) {
                            for (Player countdownPlayer : new ArrayList<>(countdownPlayers)) {
                                if (countdownPlayer == null) continue;
                                timerKit = plugin.getKitName(countdownPlayer);
                                if (timerKit != null && !timerKit.isEmpty()) {
                                    break;
                                }
                            }
                        }
                        if ((timerKit == null || timerKit.isEmpty()) && finalFight != null) {
                            try {
                                Object kit = finalFight.getClass().getMethod("getKit").invoke(finalFight);
                                if (kit != null) {
                                    timerKit = (String) kit.getClass().getMethod("getName").invoke(kit);
                                }
                            } catch (Exception ignored) {}
                        }

                        if (plugin.matchDurationManager != null
                                && timerKit != null
                                && !timerKit.isEmpty()
                                && !plugin.matchDurationManager.hasTimer(finalFight)
                                && (plugin.pearlFightManager == null || !plugin.pearlFightManager.isPearlFightKit(timerKit))) {
                            plugin.matchDurationManager.startTimer(finalFight, timerKit);
                        }
                    }

                    for (Player p : new ArrayList<>(countdownPlayers)) {
                        if (p == null || !p.isOnline())
                            continue;
                        if (!plugin.activeStartCountdownPlayers.contains(p.getUniqueId())) {
                            countdownActive.remove(p.getUniqueId());
                            pendingCountdownArenaTeleports.remove(p.getUniqueId());
                            if (plugin.hologramManager != null)
                                plugin.hologramManager.clearPlayerHologram(p);
                            continue;
                        }

                        if (finalIsRoundTransition) {
                            plugin.clearRoundTransitionRespawnState(p);
                        }

                        Location arenaSpawn = rememberCountdownArenaSpawn(p, finalFight, finalIsParty);
                        if (arenaSpawn != null) {
                            enforceCountdownArenaSpawn(p, arenaSpawn);
                            plugin.arenaSpawnLocations.put(p.getUniqueId(), arenaSpawn.clone());
                        } else {
                            plugin.arenaSpawnLocations.put(p.getUniqueId(), p.getLocation().clone());
                        }
                        if (plugin.startMatchMessage != null && !plugin.startMatchMessage.isEmpty())
                            p.sendMessage(plugin.startMatchMessage);
                        if (!finalIsRoundTransition && plugin.startCountdownTitles != null && plugin.startCountdownTitles.containsKey(0))
                            plugin.sendTitle(p, plugin.startCountdownTitles.get(0), "", 0, 20, 10);
                        if (plugin.startCountdownSoundEnabled && plugin.startMatchSound != null)
                            p.playSound(p.getLocation(), plugin.startMatchSound, plugin.startCountdownVolume,
                                    plugin.startCountdownPitch);
                        plugin.frozenPlayers.remove(p.getUniqueId());
                        plugin.roundTransitionPlayers.remove(p.getUniqueId());
                        countdownActive.remove(p.getUniqueId());
                        plugin.activeStartCountdownPlayers.remove(p.getUniqueId());
                        pendingCountdownArenaTeleports.remove(p.getUniqueId());
                        if (plugin.hologramManager != null)
                            plugin.hologramManager.clearPlayerHologram(p);
                        if (plugin.blockReplenishManager != null)
                            plugin.blockReplenishManager.scanPlayerInventory(p);
                        plugin.syncLayoutInstant(p, 2);
                    }
                    cancel();
                    return;
                }

                if (plugin.startCountdownEnabled) {
                    for (Player p : new ArrayList<>(countdownPlayers)) {
                        if (p == null || !p.isOnline())
                            continue;
                        if (plugin.startCountdownMessages != null)
                            p.sendMessage(plugin.startCountdownMessages.getOrDefault(current,
                                    ChatColor.RED + String.valueOf(current)));
                        if (!finalIsRoundTransition && plugin.startCountdownTitles != null && plugin.startCountdownTitles.containsKey(current))
                            plugin.sendTitle(p, plugin.startCountdownTitles.get(current), "", 0, 20, 0);
                        if (plugin.startCountdownSoundEnabled && plugin.startCountdownSounds != null) {
                            Sound s = plugin.startCountdownSounds.get(current);
                            if (s != null)
                                p.playSound(p.getLocation(), s, plugin.startCountdownVolume,
                                        plugin.startCountdownPitch);
                        }
                    }
                }
                current--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        if (plugin.hologramManager != null) {
            plugin.hologramManager.scheduleRehideForPlayer(event.getPlayer(), 2L, 10L, 30L);
        }

        if (plugin.partyFFAManager != null
                && plugin.partyFFAManager.isInCustomSpectator(uuid)) {
            event.setCancelled(true);
            return;
        }
        Location dest = event.getTo();
        if (dest == null)
            return;

        if (!matchEndedPlayers.contains(uuid)) {
            boolean isPearlFight = plugin.pearlFightManager != null && plugin.pearlFightManager.isPearlFight(event.getPlayer());
            if (!isPearlFight && plugin.respawnManager != null) {
                Location protectedRespawnTarget = plugin.respawnManager.getProtectedRespawnTeleportTarget(
                        event.getPlayer(), dest);
                if (protectedRespawnTarget != null) {
                    event.setTo(protectedRespawnTarget);
                    plugin.applySafeArenaTeleportProtection(event.getPlayer(), false);
                    return;
                }
            }

            Location countdownArenaSpawn = pendingCountdownArenaTeleports.get(uuid);
            if (countdownArenaSpawn != null
                    && plugin.activeStartCountdownPlayers.contains(uuid)
                    && !plugin.leavingMatchPlayers.contains(uuid)) {
                Location safeArenaSpawn = plugin.resolveSafeArenaLocation(countdownArenaSpawn);
                if (safeArenaSpawn != null) {
                    pendingCountdownArenaTeleports.put(uuid, safeArenaSpawn.clone());
                    plugin.arenaSpawnLocations.put(uuid, safeArenaSpawn.clone());
                    if (!isNearLocation(dest, safeArenaSpawn, COUNTDOWN_TELEPORT_MATCH_DISTANCE_SQUARED)) {
                        event.setTo(safeArenaSpawn.clone());
                        plugin.applySafeArenaTeleportProtection(event.getPlayer());
                    }
                }
            }
            return;
        }

        if (pendingHubTeleports.containsKey(uuid)) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        long now = System.currentTimeMillis();
        Set<UUID> group = matchPlayerGroups.getOrDefault(uuid, new HashSet<>());
        if (group.isEmpty())
            group.add(uuid);

        for (UUID memberUUID : group) {
            if (!matchEndedPlayers.contains(memberUUID))
                continue;
            if (pendingHubTeleports.containsKey(memberUUID))
                continue;

            final Location hub = resolveHubLocation(Bukkit.getPlayer(memberUUID));
            pendingHubTeleports.put(memberUUID, hub);

            long matchEndTime = matchEndTimes.getOrDefault(memberUUID, now);
            long elapsedTicks = (now - matchEndTime) / 50L;
            long delayTicks = Math.max(0L, POST_MATCH_ARENA_STAY_TICKS - elapsedTicks);

            final UUID finalMemberUUID = memberUUID;
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (matchEndSpectators.contains(finalMemberUUID)) {

                        return;
                    }
                    Player p = Bukkit.getPlayer(finalMemberUUID);
                    matchEndedPlayers.remove(finalMemberUUID);
                    Location hubLoc = pendingHubTeleports.remove(finalMemberUUID);
                    matchPlayerGroups.remove(finalMemberUUID);
                    matchEndTimes.remove(finalMemberUUID);
                    if (p != null && p.isOnline() && hubLoc != null) {
                        forceHubTeleport(p, hubLoc);
                    }
                }
            }.runTaskLater(plugin, delayTicks);
        }
    }

    private void handleBestOfLeave(Object fight, Player leaver, Player winner, String kitName) {
        UUID leaverUid = leaver.getUniqueId();

        plugin.leavingMatchPlayers.remove(leaverUid);

        plugin.frozenPlayers.remove(leaverUid);
        plugin.activeStartCountdownPlayers.remove(leaverUid);
        pendingCountdownArenaTeleports.remove(leaverUid);

        UUID winnerUuid = winner != null ? winner.getUniqueId() : null;
        plugin.endedFightWinners.put(fight, winnerUuid);

        if (plugin.matchDurationManager != null) plugin.matchDurationManager.stopTimer(fight);
        if (plugin.boxingTrackerManager != null) {
            plugin.boxingTrackerManager.onFightEnd(fight, java.util.Arrays.asList(leaver, winner));
        }
        if (plugin.pearlFightManager != null) {
            plugin.pearlFightManager.onFightEnd(fight, java.util.Arrays.asList(leaver, winner));
        }
        storeFightDuration(fight, leaver, winner);

        plugin.playerMatchResults.put(leaverUid, ChatColor.RED + "" + ChatColor.BOLD + "DEFEAT");
        if (plugin.leaderboardManager != null && kitName != null)
            plugin.leaderboardManager.resetStreak(leaverUid, kitName);

        final UUID finalLeaverUidCleanup = leaverUid;
        new BukkitRunnable() {
            @Override public void run() {
                plugin.playerMatchResults.remove(finalLeaverUidCleanup);
                plugin.playerMatchDurations.remove(finalLeaverUidCleanup);
            }
        }.runTaskLater(plugin, 160L);

        leaver.getInventory().clear();
        leaver.getInventory().setArmorContents(null);
        try { leaver.getInventory().setItemInOffHand(null); } catch (NoSuchMethodError ignored) {}
        leaver.updateInventory();

        Location hubLoc = null;
        try {
            Object spApi = plugin.getStrikePracticeAPI();
            if (spApi != null)
                hubLoc = (Location) spApi.getClass().getMethod("getSpawnLocation").invoke(spApi);
        } catch (Exception ignored) {}
        if (leaver.isOnline()) forceHubTeleport(leaver, hubLoc);

        if (winner != null && winner.isOnline()) {
            plugin.hubOnJoinSpawn.put(winner.getUniqueId(), winner.getLocation().clone());
        }

        if (winner == null || !winner.isOnline()) return;
        final boolean isPearlFightEnd = plugin.pearlFightManager != null
                && plugin.pearlFightManager.isPearlFightKit(kitName);

        UUID wUid = winner.getUniqueId();
        plugin.frozenPlayers.remove(wUid);
        plugin.activeStartCountdownPlayers.remove(wUid);
        pendingCountdownArenaTeleports.remove(wUid);

        plugin.playerMatchResults.put(wUid, ChatColor.GREEN + "" + ChatColor.BOLD + "VICTORY");
        if (plugin.leaderboardManager != null && kitName != null)
            plugin.leaderboardManager.addWin(wUid, plugin.getRealPlayerName(winner), kitName);

        final UUID finalWUidCleanup = wUid;
        new BukkitRunnable() {
            @Override public void run() {
                plugin.playerMatchResults.remove(finalWUidCleanup);
                plugin.playerMatchDurations.remove(finalWUidCleanup);
            }
        }.runTaskLater(plugin, 160L);

        String winTitle = plugin.getMsg("win.title");
        String winSub   = plugin.getMsg("win.subtitle");
        if (winTitle == null || winTitle.isEmpty()) winTitle = "&a&lVICTORY!";
        if (winSub   == null || winSub.isEmpty())   winSub   = "&7" + plugin.getEffectivePlayerName(leaver) + " left";
        plugin.sendTitle(winner,
                ChatColor.translateAlternateColorCodes('&', winTitle.replace("<opponent>", plugin.getEffectivePlayerName(leaver))),
                ChatColor.translateAlternateColorCodes('&', winSub  .replace("<opponent>", plugin.getEffectivePlayerName(leaver))),
                10, 70, 20);
        List<Player> celebrationViewers = resolveAllFightPlayers(fight, new ArrayList<>());
        if (celebrationViewers.isEmpty()) {
            celebrationViewers.add(winner);
            if (leaver != null && leaver.isOnline()) celebrationViewers.add(leaver);
        }
        plugin.playEndMatchSounds(winner, true, celebrationViewers);

        Location hubForWinner = hubLoc;
        if (hubForWinner == null) {
            try {
                Object spApi = plugin.getStrikePracticeAPI();
                if (spApi != null) hubForWinner = (Location) spApi.getClass()
                        .getMethod("getSpawnLocation").invoke(spApi);
            } catch (Exception ignored) {}
        }
        matchEndedPlayers.add(wUid);
        matchEndSpectators.add(wUid);
        pendingHubTeleports.put(wUid, hubForWinner);
        matchEndTimes.put(wUid, System.currentTimeMillis());
        applyMatchEndVanish(winner);
        enableMatchEndSpectatorFlight(winner);

        final Player finalWinner = winner;

        if (!isPearlFightEnd) {
            new BukkitRunnable() {
                int ticks = 0;
                @Override public void run() {
                    ticks++;
                    if (!finalWinner.isOnline()
                            || !matchEndSpectators.contains(finalWinner.getUniqueId())
                            || ticks > 20) { cancel(); return; }
                    equipMatchEndLobbyItem(finalWinner, false);
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }

        new BukkitRunnable() {
            @Override public void run() {
                if (!finalWinner.isOnline()) return;
                if (matchEndSpectators.contains(finalWinner.getUniqueId())) {
                    plugin.hubOnJoinSpawn.remove(finalWinner.getUniqueId());
                    exitMatchSpectator(finalWinner);
                }
            }
        }.runTaskLater(plugin, POST_MATCH_ARENA_STAY_TICKS);

        if (plugin.getMGetArena() != null) {
            try {
                Object arena = plugin.getMGetArena().invoke(fight);
                if (arena != null) {
                    String arenaName = getArenaName(arena);
                    forceFixBeds(arenaName, fight, 80L);
                    scheduleArenaRestore(arenaName, fight);
                }
            } catch (Exception ignored) {}
        }

        if (winnerUuid != null) {
            try {
                Object bestOf = fight.getClass().getMethod("getBestOf").invoke(fight);
                if (bestOf != null) {
                    int rounds = (int) bestOf.getClass().getMethod("getRounds").invoke(bestOf);
                    int winsNeeded = rounds / 2 + 1;
                    @SuppressWarnings("unchecked")
                    Map<UUID, Integer> wonMap = (Map<UUID, Integer>) bestOf.getClass()
                            .getMethod("getRoundsWon").invoke(bestOf);
                    int currentWins = wonMap.getOrDefault(winnerUuid, 0);

                    int toAward = winsNeeded - 1 - currentWins;
                    for (int i = 0; i < toAward && i < 10; i++) {
                        bestOf.getClass().getMethod("handleWin", UUID.class)
                                .invoke(bestOf, winnerUuid);
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private void exitMatchSpectator(Player p) {
        if (plugin.partyFFAManager != null) plugin.partyFFAManager.cleanupBridgeCustomSpectator(p);
        cleanupMatchEndVanish(p);
        UUID uuid = p.getUniqueId();
        cancelMatchEndSpectatorFlight(uuid);
        stopPearlFightEndScoreboard(uuid);
        matchEndSpectators.remove(uuid);
        matchEndedPlayers.remove(uuid);
        Location hub = pendingHubTeleports.remove(uuid);
        pendingCountdownArenaTeleports.remove(uuid);
        matchPlayerGroups.remove(uuid);
        matchEndTimes.remove(uuid);
        restoreMatchEndSpectatorSpeed(uuid);
        plugin.playerMatchResults.remove(uuid);
        plugin.playerMatchDurations.remove(uuid);

        plugin.sendTitle(p, " ", " ", 0, 1, 0);

        try {
            Object api = plugin.getStrikePracticeAPI();
            if (api != null)
                api.getClass().getMethod("clear", Player.class, boolean.class, boolean.class)
                        .invoke(api, p, true, true);
        } catch (Exception ignored) {}

        forceHubTeleport(p, hub);
    }

    @SuppressWarnings("unchecked")
    private void markDuelEndPlayers(Event event) {
        try {
            final Object fight = event.getClass().getMethod("getFight").invoke(event);
            if (fight == null)
                return;

            if (plugin.endedFightWinners.containsKey(fight)) return;

            List<Player> players = new ArrayList<>();
            if (plugin.getMGetPlayersInFight() != null) {
                try {
                    List<Player> temp = (List<Player>) plugin.getMGetPlayersInFight().invoke(fight);
                    if (temp != null)
                        players.addAll(temp);
                } catch (Exception ignored) {
                }
            }
            if (players.isEmpty() && plugin.getMGetPlayers() != null) {
                try {
                    List<Player> temp = (List<Player>) plugin.getMGetPlayers().invoke(fight);
                    if (temp != null)
                        players.addAll(temp);
                } catch (Exception ignored) {
                }
            }
            if (players.isEmpty()) {
                try {
                    Player w = (Player) event.getClass().getMethod("getWinner").invoke(event);
                    if (w != null)
                        players.add(w);
                } catch (Exception ignored) {
                }
                try {
                    Player l = (Player) event.getClass().getMethod("getLoser").invoke(event);
                    if (l != null)
                        players.add(l);
                } catch (Exception ignored) {
                }
            }

            long endTime = System.currentTimeMillis();
            Set<UUID> uuids = new HashSet<>();
            Player hubReference = players.isEmpty() ? null : players.get(0);
            Location defaultHub = resolveHubLocation(hubReference);
            for (Player p : players) {
                if (p != null) {
                    uuids.add(p.getUniqueId());
                    matchEndedPlayers.add(p.getUniqueId());
                    matchEndTimes.put(p.getUniqueId(), endTime);
                    pendingHubTeleports.put(p.getUniqueId(), defaultHub != null ? defaultHub.clone() : null);
                }
            }
            for (UUID u : uuids) {
                matchPlayerGroups.put(u, new HashSet<>(uuids));
            }

            final List<Player> cleanup = new ArrayList<>(players);
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (Player p : cleanup) {
                        if (p != null) {
                            UUID u = p.getUniqueId();
                            matchEndedPlayers.remove(u);
                            pendingHubTeleports.remove(u);
                            matchPlayerGroups.remove(u);
                            matchEndTimes.remove(u);
                        }
                    }
                }
            }.runTaskLater(plugin, 200L);
        } catch (Exception ignored) {
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPostMatchSpectatorItemUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        Player p = event.getPlayer();
        if (!matchEndSpectators.contains(p.getUniqueId()))
            return;

        event.setCancelled(true);
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.COMPASS)
            return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !(ChatColor.GREEN + "" + ChatColor.BOLD + "Return to Lobby").equals(meta.getDisplayName()))
            return;
        exitMatchSpectator(p);
    }

    @SuppressWarnings("unchecked")
    public void handleDuelEnd(Event event) {
        try {
            final Object fight = event.getClass().getMethod("getFight").invoke(event);

            if (fight != null && plugin.endedFightWinners.containsKey(fight)) {

                if (plugin.matchDurationManager != null) plugin.matchDurationManager.stopTimer(fight);
                if (plugin.boxingTrackerManager != null) {
                    plugin.boxingTrackerManager.onFightEnd(fight, resolveAllFightPlayers(fight, new ArrayList<>()));
                }
                if (plugin.pearlFightManager != null) {
                    plugin.pearlFightManager.onFightEnd(fight, resolveAllFightPlayers(fight, new ArrayList<>()));
                }

                final Object fightForCleanup = fight;
                new BukkitRunnable() {
                    @Override public void run() {
                        plugin.endedFightWinners.remove(fightForCleanup);
                        startedFights.remove(fightForCleanup);
                        partyCountdownInitializedFights.remove(fightForCleanup);
                        fightStartTimes.remove(fightForCleanup);
                        List<Player> allInFight = new ArrayList<>();
                        try {
                            if (plugin.getMGetPlayersInFight() != null) {
                                @SuppressWarnings("unchecked")
                                List<Player> tmp = (List<Player>) plugin.getMGetPlayersInFight().invoke(fightForCleanup);
                                if (tmp != null) allInFight.addAll(tmp);
                            }
                        } catch (Exception ignored) {}
                        try {
                            if (allInFight.isEmpty() && plugin.getMGetPlayers() != null) {
                                @SuppressWarnings("unchecked")
                                List<Player> tmp = (List<Player>) plugin.getMGetPlayers().invoke(fightForCleanup);
                                if (tmp != null) allInFight.addAll(tmp);
                            }
                        } catch (Exception ignored) {}
                        for (Player p : resolveAllFightPlayers(fightForCleanup, allInFight)) {
                            if (p != null) {
                                plugin.playerMatchResults.remove(p.getUniqueId());
                                plugin.playerMatchDurations.remove(p.getUniqueId());
                            }
                        }
                    }
                }.runTaskLater(plugin, 160L);
                return;
            }

            final boolean isTimeLimitDraw = fight != null && plugin.drawFights.remove(fight);

            Player winner = null;
            Player loser = null;
            try {
                Object winnerObj = event.getClass().getMethod("getWinner").invoke(event);
                if (winnerObj instanceof Player) winner = (Player) winnerObj;
                else if (winnerObj instanceof UUID) winner = Bukkit.getPlayer((UUID) winnerObj);
                else if (winnerObj instanceof String) winner = Bukkit.getPlayer((String) winnerObj);
            } catch (Exception ignored) {}
            try {
                Object loserObj = event.getClass().getMethod("getLoser").invoke(event);
                if (loserObj instanceof Player) loser = (Player) loserObj;
                else if (loserObj instanceof UUID) loser = Bukkit.getPlayer((UUID) loserObj);
                else if (loserObj instanceof String) loser = Bukkit.getPlayer((String) loserObj);
            } catch (Exception ignored) {}

            List<Player> fightPlayers = new ArrayList<>();
            if (fight != null) {
                plugin.endedFightWinners.put(fight, winner != null ? winner.getUniqueId() : null);

                if (plugin.getMGetPlayersInFight() != null) {
                    try {
                        List<Player> temp = (List<Player>) plugin.getMGetPlayersInFight().invoke(fight);
                        if (temp != null)
                            fightPlayers.addAll(temp);
                    } catch (Exception ignored) {
                    }
                }
                if (fightPlayers.isEmpty() && plugin.getMGetPlayers() != null) {
                    try {
                        List<Player> temp = (List<Player>) plugin.getMGetPlayers().invoke(fight);
                        if (temp != null)
                            fightPlayers.addAll(temp);
                    } catch (Exception ignored) {
                    }
                }
                if (fightPlayers.isEmpty()) {
                    if (winner != null)
                        fightPlayers.add(winner);
                    if (loser != null)
                        fightPlayers.add(loser);
                }

                boolean isPartyFight = (plugin.partySplitManager != null
                        && plugin.partySplitManager.isPartySplit(fight))
                        || (plugin.partyVsPartyManager != null && plugin.partyVsPartyManager.isPartyVsParty(fight));
                if (isPartyFight) {
                    for (Player pp : resolveAllFightPlayers(fight, fightPlayers)) {
                        if (pp != null && !fightPlayers.contains(pp))
                            fightPlayers.add(pp);
                    }
                }
                fightPlayers.removeIf(p -> p == null);
                storeFightDuration(fight, fightPlayers);

                for (Player p : fightPlayers) {
                    matchEndedPlayers.add(p.getUniqueId());
                    plugin.activeStartCountdownPlayers.remove(p.getUniqueId());
                    pendingCountdownArenaTeleports.remove(p.getUniqueId());
                    if (plugin.hologramManager != null)
                        plugin.hologramManager.clearPlayerHologram(p);
                }
                final List<Player> finalFightPlayersForCleanup = new ArrayList<>(fightPlayers);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        for (Player p : finalFightPlayersForCleanup) {
                            if (p != null) {
                                UUID uuid = p.getUniqueId();
                                boolean shouldForceHubTeleport = matchEndedPlayers.contains(uuid)
                                        || matchEndSpectators.contains(uuid)
                                        || pendingHubTeleports.containsKey(uuid);
                                Location hub200 = resolvePostMatchHub(p, pendingHubTeleports.get(uuid));

                                matchEndSpectators.remove(uuid);
                                cancelMatchEndSpectatorFlight(uuid);
                                stopPearlFightEndScoreboard(uuid);
                                matchEndedPlayers.remove(uuid);
                                pendingHubTeleports.remove(uuid);
                                pendingCountdownArenaTeleports.remove(uuid);
                                matchPlayerGroups.remove(uuid);
                                matchEndTimes.remove(uuid);
                                restoreMatchEndSpectatorSpeed(uuid);
                                if (shouldForceHubTeleport && p.isOnline()) {
                                    if (plugin.partyFFAManager != null) {
                                        plugin.partyFFAManager.cleanupBridgeCustomSpectator(p);
                                    }
                                    cleanupMatchEndVanish(p);
                                    try {
                                        Object api = plugin.getStrikePracticeAPI();
                                        if (api != null)
                                            api.getClass().getMethod("clear", Player.class, boolean.class, boolean.class)
                                                    .invoke(api, p, true, true);
                                    } catch (Exception ignored) {}
                                    forceHubTeleport(p, hub200);
                                }
                            }
                        }
                    }
                }.runTaskLater(plugin, 200L);

                if (winner == null && fight != null && !fightPlayers.isEmpty()) {
                    boolean isPartyFight2 = (plugin.partySplitManager != null
                            && plugin.partySplitManager.isPartySplit(fight))
                            || (plugin.partyVsPartyManager != null && plugin.partyVsPartyManager.isPartyVsParty(fight));
                    if (isPartyFight2) {
                        if (winner == null && plugin.partySplitManager != null)
                            winner = plugin.partySplitManager.getBridgeWinner(fight);
                        if (winner == null && plugin.partyVsPartyManager != null)
                            winner = plugin.partyVsPartyManager.getBridgeWinner(fight);

                        if (winner == null)
                            try {
                                Object winners = event.getClass().getMethod("getWinners").invoke(event);
                                if (winners instanceof Iterable) {
                                    for (Object obj : (Iterable<?>) winners) {
                                        if (obj instanceof Player) {
                                            winner = (Player) obj;
                                            break;
                                        }
                                        if (obj instanceof String) {
                                            Player p = org.bukkit.Bukkit.getPlayer((String) obj);
                                            if (p != null) {
                                                winner = p;
                                                break;
                                            }
                                        }
                                    }
                                }
                            } catch (Exception ignored) {
                            }

                        if (winner == null) {
                            try {
                                Boolean bed1Broken = (plugin.getMIsBed1Broken() != null)
                                        ? (Boolean) plugin.getMIsBed1Broken().invoke(fight)
                                        : null;
                                Boolean bed2Broken = (plugin.getMIsBed2Broken() != null)
                                        ? (Boolean) plugin.getMIsBed2Broken().invoke(fight)
                                        : null;
                                if (bed1Broken != null && bed2Broken != null) {
                                    for (Player candidate : fightPlayers) {
                                        String cc = plugin.getTeamColorCode(candidate, fight);
                                        boolean isTeam1 = cc.contains("9") || cc.contains("b");
                                        boolean isTeam2 = cc.contains("c") || cc.contains("d");
                                        if (isTeam1 && !bed1Broken) {
                                            winner = candidate;
                                            break;
                                        }
                                        if (isTeam2 && !bed2Broken) {
                                            winner = candidate;
                                            break;
                                        }
                                    }
                                }
                                if (winner == null && (bed1Broken != null || bed2Broken != null)) {
                                    Player refBlue = null, refRed = null;
                                    for (Player candidate : fightPlayers) {
                                        String cc = plugin.getTeamColorCode(candidate, fight);
                                        if (refBlue == null && (cc.contains("9") || cc.contains("b")))
                                            refBlue = candidate;
                                        if (refRed == null && (cc.contains("c") || cc.contains("d")))
                                            refRed = candidate;
                                    }
                                    if (refBlue != null && Boolean.FALSE.equals(bed1Broken))
                                        winner = refBlue;
                                    else if (refRed != null && Boolean.FALSE.equals(bed2Broken))
                                        winner = refRed;
                                }
                            } catch (Exception ignored) {
                            }
                        }

                        if (winner == null && plugin.partySplitManager != null
                                && plugin.partySplitManager.isPartySplit(fight)) {
                            try {
                                java.util.HashSet<String> alive1 = (java.util.HashSet<String>) fight.getClass()
                                        .getMethod("getAlive1").invoke(fight);
                                java.util.HashSet<String> alive2 = (java.util.HashSet<String>) fight.getClass()
                                        .getMethod("getAlive2").invoke(fight);
                                if (alive1 != null && alive2 != null) {
                                    if (!alive1.isEmpty() && alive2.isEmpty()) {
                                        for (String name : alive1) {
                                            Player cand = Bukkit.getPlayer(name);
                                            if (cand != null && cand.isOnline()) {
                                                winner = cand;
                                                break;
                                            }
                                        }
                                    } else if (alive1.isEmpty() && !alive2.isEmpty()) {
                                        for (String name : alive2) {
                                            Player cand = Bukkit.getPlayer(name);
                                            if (cand != null && cand.isOnline()) {
                                                winner = cand;
                                                break;
                                            }
                                        }
                                    }
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }

                boolean isPartyEndFightForMark = (plugin.partySplitManager != null
                        && plugin.partySplitManager.isPartySplit(fight))
                        || (plugin.partyVsPartyManager != null && plugin.partyVsPartyManager.isPartyVsParty(fight));
                if (isPartyEndFightForMark) {
                    for (Player pMark : fightPlayers)
                        if (pMark != null)
                            plugin.recentPartyEndedPlayers.add(pMark.getUniqueId());
                }

                for (Player p : fightPlayers) {
                    if (p == null)
                        continue;

                    String result = ChatColor.YELLOW + "" + ChatColor.BOLD + "DRAW!";
                    String kitName = plugin.getKitName(p);

                    if (isTimeLimitDraw) {

                        result = ChatColor.YELLOW + "" + ChatColor.BOLD + "DRAW!";
                    } else if (winner != null) {
                        boolean isWinner = p.getUniqueId().equals(winner.getUniqueId());
                        if (!isWinner && plugin.getMPlayersAreTeammates() != null) {
                            try {
                                isWinner = (boolean) plugin.getMPlayersAreTeammates().invoke(fight, p, winner);
                            } catch (Exception ignored) {
                            }
                        }

                        if (isWinner) {
                            result = ChatColor.GREEN + "" + ChatColor.BOLD + "VICTORY";

                            if (p.getUniqueId().equals(winner.getUniqueId()) && plugin.leaderboardManager != null) {
                                plugin.leaderboardManager.addWin(winner.getUniqueId(), plugin.getRealPlayerName(winner), kitName);
                            }
                        } else {
                            result = ChatColor.RED + "" + ChatColor.BOLD + "DEFEAT";

                            if (loser != null && p.getUniqueId().equals(loser.getUniqueId())
                                    && plugin.leaderboardManager != null) {
                                plugin.leaderboardManager.resetStreak(loser.getUniqueId(), kitName);
                            }
                        }
                    } else {
                        if (plugin.leaderboardManager != null) {
                            plugin.leaderboardManager.resetStreak(p.getUniqueId(), kitName);
                        }
                    }

                    plugin.playerMatchResults.put(p.getUniqueId(), result);
                }

                boolean isBestOf = false;
                boolean isPearlFightEnd = false;
                int blueScore = 0;
                int redScore = 0;
                boolean isBlueWinner = false;
                String endKitName = null;
                boolean isRoundScoredFightEnd = false;

                try {
                    if (winner != null) {
                        endKitName = plugin.getKitName(winner);
                    }
                    if ((endKitName == null || endKitName.isEmpty()) && loser != null) {
                        endKitName = plugin.getKitName(loser);
                    }
                    if ((endKitName == null || endKitName.isEmpty()) && !fightPlayers.isEmpty()) {
                        endKitName = plugin.getKitName(fightPlayers.get(0));
                    }
                    isRoundScoredFightEnd = plugin.isRoundScoredKit(endKitName);
                    isPearlFightEnd = plugin.pearlFightManager != null
                            && plugin.pearlFightManager.isPearlFightKit(endKitName);
                } catch (Exception ignored) {
                }

                try {
                    Method getBestOfMethod = fight.getClass().getMethod("getBestOf");
                    Object bestOf = getBestOfMethod.invoke(fight);
                    if (bestOf != null) {

                        int totalRounds = 1;
                        try {
                            Method getRoundsMethod = bestOf.getClass().getMethod("getRounds");
                            totalRounds = (int) getRoundsMethod.invoke(bestOf);
                        } catch (Exception ignored) {
                        }

                        if (totalRounds > 1) {
                            isBestOf = true;
                        }

                        if (!isBestOf && fightPlayers.size() > 0) {
                            String kitName = plugin.getKitName(fightPlayers.get(0));
                            if (kitName != null) {
                                if (plugin.isRoundScoredKit(kitName)) {
                                    isBestOf = true;
                                }
                            }
                        }

                        Player bluePlayer = null;
                        Player redPlayer = null;

                        for (Player p : fightPlayers) {
                            if (p == null)
                                continue;
                            String color = plugin.getTeamColorCode(p, fight);
                            if (color.contains("9") || color.contains("b"))
                                bluePlayer = p;
                            else if (color.contains("c") || color.contains("d"))
                                redPlayer = p;
                        }

                        if (bluePlayer == null && fightPlayers.size() > 0)
                            bluePlayer = fightPlayers.get(0);
                        if (redPlayer == null && fightPlayers.size() > 1)
                            redPlayer = fightPlayers.get(fightPlayers.size() - 1);

                        Map<UUID, Integer> oldScores = plugin.matchScores.getOrDefault(fight, new HashMap<>());

                        try {
                            Method getRoundsWon = bestOf.getClass().getMethod("getRoundsWon");
                            Map<?, ?> scores = (Map<?, ?>) getRoundsWon.invoke(bestOf);
                            if (scores != null && !scores.isEmpty()) {
                                for (Map.Entry<?, ?> entry : scores.entrySet()) {
                                    Object key = entry.getKey();
                                    int score = entry.getValue() instanceof Number
                                            ? ((Number) entry.getValue()).intValue()
                                            : 0;

                                    if (bluePlayer != null && (key.equals(bluePlayer.getUniqueId())
                                            || key.equals(bluePlayer.getName()))) {
                                        blueScore = score;
                                    } else if (redPlayer != null && (key.equals(redPlayer.getUniqueId())
                                            || key.equals(redPlayer.getName()))) {
                                        redScore = score;
                                    }
                                }
                            }
                        } catch (Exception ignored) {
                        }

                        if (bluePlayer != null && blueScore == 0)
                            blueScore = oldScores.getOrDefault(bluePlayer.getUniqueId(), 0);
                        if (redPlayer != null && redScore == 0)
                            redScore = oldScores.getOrDefault(redPlayer.getUniqueId(), 0);

                        if (winner != null) {
                            String wColor = plugin.getTeamColorCode(winner, fight);
                            isBlueWinner = wColor.contains("9") || wColor.contains("b");
                        }
                    }
                } catch (Exception ignored) {
                }

                if (!isBestOf && isRoundScoredFightEnd) {
                    isBestOf = true;
                }

                if (isRoundScoredFightEnd) {
                    if (plugin.partySplitManager != null && plugin.partySplitManager.isPartySplit(fight)) {
                        int[] partyScores = plugin.partySplitManager.getBridgeScores(fight);
                        blueScore = partyScores[0];
                        redScore = partyScores[1];
                    } else if (plugin.partyVsPartyManager != null && plugin.partyVsPartyManager.isPartyVsParty(fight)) {
                        int[] partyScores = plugin.partyVsPartyManager.getBridgeScores(fight);
                        blueScore = partyScores[0];
                        redScore = partyScores[1];
                    }

                    if (winner != null) {
                        String winnerColor = plugin.getTeamColorCode(winner, fight);
                        isBlueWinner = winnerColor.contains("9") || winnerColor.contains("b");
                    }
                }

                final boolean finalIsBestOf = isBestOf;
                final boolean finalIsPearlFightEnd = isPearlFightEnd;
                final int finalBlueScore = blueScore;
                final int finalRedScore = redScore;
                final boolean finalIsBlueWinner = isBlueWinner;

                final boolean isDuelReq = plugin.duelScoreManager != null
                        && plugin.duelScoreManager.isDuelRequestFight(fight);

                if (isDuelReq && winner != null && loser != null) {
                    plugin.duelScoreManager.onFightEnd(fight, winner, loser);
                }

                final boolean partySplitBridgeHandled  = plugin.partySplitManager  != null && plugin.partySplitManager.isBridgeEndHandled(fight);
                final boolean partyVsPartyBridgeHandled = plugin.partyVsPartyManager != null && plugin.partyVsPartyManager.isBridgeEndHandled(fight);
                final boolean ffaBridgeHandled          = plugin.partyFFAManager     != null && plugin.partyFFAManager.isBridgeEndHandled(fight);
                final boolean bridge1v1WasHandled       = bridge1v1EndedFights.contains(fight);

                if (plugin.boxingTrackerManager != null) {
                    plugin.boxingTrackerManager.onFightEnd(fight, fightPlayers);
                }
                if (plugin.pearlFightManager != null) {
                    plugin.pearlFightManager.onFightEnd(fight, fightPlayers);
                }
                plugin.matchScores.remove(fight);
                startedFights.remove(fight);
                partyCountdownInitializedFights.remove(fight);
                fightStartTimes.remove(fight);
                if (plugin.matchDurationManager != null) plugin.matchDurationManager.stopTimer(fight);
                fightCountdownCooldown.remove(fight);
                partyCountdownFights.remove(fight);
                if (plugin.partySplitManager != null)
                    plugin.partySplitManager.onFightEnd(fight);
                if (plugin.partyVsPartyManager != null)
                    plugin.partyVsPartyManager.onFightEnd(fight);
                bridge1v1Scores.remove(fight);
                bridge1v1EndedFights.remove(fight);
                if (plugin.partyFFAManager != null)
                    plugin.partyFFAManager.onFightEnd(fight);
                if (plugin.bridgeBlockResetManager != null) {
                    for (Player p : fightPlayers) {
                        if (p != null)
                            plugin.bridgeBlockResetManager.clearPlayerData(p);
                    }
                }
                for (Player p : fightPlayers) {
                    if (p != null) plugin.bedFightBedBrokenPlayers.remove(p.getUniqueId());
                }

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        plugin.endedFightWinners.remove(fight);
                        for (Player p : fightPlayers) {
                            if (p != null) {
                                plugin.playerMatchResults.remove(p.getUniqueId());
                                plugin.playerMatchDurations.remove(p.getUniqueId());
                                plugin.playerMatchKills.remove(p.getUniqueId());
                                plugin.recentPartyEndedPlayers.remove(p.getUniqueId());
                            }
                        }
                    }
                }.runTaskLater(plugin, 160L);

                if (fight != null && plugin.getMGetArena() != null) {
                    try {
                        Object arena = plugin.getMGetArena().invoke(fight);
                        if (arena != null) {
                            String arenaName = getArenaName(arena);
                            forceFixBeds(arenaName, fight, 80L);
                            scheduleArenaRestore(arenaName, fight);
                        }
                    } catch (Exception ex) {
                    }
                }

                for (Player p : fightPlayers) {
                    if (p == null)
                        continue;

                    boolean isWinner = false;
                    boolean isLoser = false;

                    if (winner != null) {
                        isWinner = p.getUniqueId().equals(winner.getUniqueId());
                        if (!isWinner && plugin.getMPlayersAreTeammates() != null) {
                            try {
                                isWinner = (boolean) plugin.getMPlayersAreTeammates().invoke(fight, p, winner);
                            } catch (Exception ignored) {
                            }
                        }
                    }

                    if (winner != null && !isWinner) {
                        isLoser = true;
                    }

                    boolean isPartyEndFight = (plugin.partySplitManager != null
                            && plugin.partySplitManager.isPartySplit(fight))
                            || (plugin.partyVsPartyManager != null && plugin.partyVsPartyManager.isPartyVsParty(fight));
                    if (winner != null && !isWinner && !isLoser && !isPartyEndFight) {
                        continue;
                    }
                    if (!isWinner && !isLoser && isPartyEndFight) {
                        isLoser = true;
                    }

                    plugin.cleanupPlayer(p.getUniqueId(), false);

                    if (plugin.isHooked()) {
                        final Player finalPExit = p;
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (finalPExit.isOnline()
                                        && matchEndSpectators.contains(finalPExit.getUniqueId())) {
                                    exitMatchSpectator(finalPExit);
                                }
                            }
                        }.runTaskLater(plugin, POST_MATCH_ARENA_STAY_TICKS);
                    }

                    final boolean finalIsWinnerForSound = isWinner;
                    final Player finalWinner = winner;
                    final Player finalLoser = loser;
                    final boolean finalIsDuelReq = isDuelReq;
                    final boolean finalIsPartyFight = isPartyEndFight;

                    final String finalWinnerLeader;
                    final String finalLoserLeader;
                    if (isPartyEndFight && winner != null) {
                        finalWinnerLeader = getPartyLeaderName(fight, winner);
                        finalLoserLeader = isLoser ? getPartyLeaderName(fight, p) : null;
                    } else {
                        finalWinnerLeader = null;
                        finalLoserLeader = null;
                    }

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (p.isOnline()) {

                                if (isTimeLimitDraw) {
                                    matchEndSpectators.add(p.getUniqueId());
                                    applyMatchEndVanish(p);
                                    enableMatchEndSpectatorFlight(p);

                                    String drawTitle    = plugin.matchDurationManager != null
                                            ? ChatColor.translateAlternateColorCodes('&', plugin.matchDurationManager.getDrawTitle())
                                            : ChatColor.translateAlternateColorCodes('&', "&e&lDRAW!");
                                    String drawSubtitle = plugin.matchDurationManager != null
                                            ? ChatColor.translateAlternateColorCodes('&', plugin.matchDurationManager.getDrawSubtitle())
                                            : ChatColor.translateAlternateColorCodes('&', "&fTime's up — no winner.");
                                    plugin.sendTitle(p, drawTitle, drawSubtitle, 10, 70, 20);
                                    plugin.playEndMatchSounds(p, false, fightPlayers);

                                    final Player finalPDraw = p;
                                    new BukkitRunnable() {
                                        int ticks = 0;
                                        @Override public void run() {
                                            ticks++;
                                            if (!finalPDraw.isOnline()
                                                    || !matchEndSpectators.contains(finalPDraw.getUniqueId())
                                                    || ticks > 20) { cancel(); return; }
                                            equipMatchEndLobbyItem(finalPDraw, false);
                                        }
                                    }.runTaskTimer(plugin, 0L, 1L);
                                    return;
                                }

                                if (partySplitBridgeHandled || partyVsPartyBridgeHandled
                                        || ffaBridgeHandled || bridge1v1WasHandled) {
                                    plugin.frozenPlayers.remove(p.getUniqueId());
                                    plugin.playEndMatchSounds(p, finalIsWinnerForSound, fightPlayers);
                                    if (plugin.isHooked() && plugin.partyFFAManager != null) {
                                        matchEndSpectators.add(p.getUniqueId());
                                        plugin.partyFFAManager.applyCustomSpectator(p, fightPlayers);
                                    }
                                    return;
                                }
                                if (finalIsWinnerForSound) {
                                    if (finalIsBestOf && !finalIsPearlFightEnd) {
                                        String t = finalIsBlueWinner ? plugin.getMsg("bestof.blue-wins.title")
                                                : plugin.getMsg("bestof.red-wins.title");
                                        String s = finalIsBlueWinner ? plugin.getMsg("bestof.blue-wins.subtitle")
                                                : plugin.getMsg("bestof.red-wins.subtitle");
                                        if (t == null || t.isEmpty())
                                            t = finalIsBlueWinner ? "&9&lBLUE WINS!" : "&c&lRED WINS!";
                                        if (s == null || s.isEmpty())
                                            s = finalIsBlueWinner ? "&9<blue_score> &8- &c<red_score>"
                                                    : "&c<red_score> &8- &9<blue_score>";
                                        s = s.replace("<blue_score>", String.valueOf(finalBlueScore))
                                                .replace("<red_score>", String.valueOf(finalRedScore));
                                        TitleUtil.sendGlowingTitle(plugin, p, ChatColor.translateAlternateColorCodes('&', t),
                                                ChatColor.translateAlternateColorCodes('&', s), 3, 14, 3);
                                    } else if (finalIsPartyFight) {
                                        String t = plugin.getMsg("party.victory.title");
                                        String s = plugin.getMsg("party.victory.subtitle");
                                        if (t == null || t.isEmpty())
                                            t = "&e&lVICTORY!";
                                        if (s == null || s.isEmpty())
                                            s = "&fYour team won the Match!";
                                        String leader = finalWinnerLeader != null ? finalWinnerLeader
                                                : (finalWinner != null ? plugin.getEffectivePlayerName(finalWinner) : "Unknown");
                                        t = t.replace("<leader>", leader);
                                        s = s.replace("<leader>", leader);
                                        TitleUtil.sendGlowingTitle(plugin, p, ChatColor.translateAlternateColorCodes('&', t),
                                                ChatColor.translateAlternateColorCodes('&', s), 10, 70, 20);
                                    } else if (finalIsDuelReq && finalLoser != null
                                            && plugin.duelScoreManager != null) {
                                        Player myOpponent = finalLoser;
                                        int myScore = plugin.duelScoreManager.getScore(p.getUniqueId(),
                                                myOpponent.getUniqueId());
                                        int opScore = plugin.duelScoreManager.getScore(myOpponent.getUniqueId(),
                                                p.getUniqueId());
                                        String title = ChatColor.translateAlternateColorCodes('&', "&e&lVICTORY!");
                                        String subtitle = ChatColor.translateAlternateColorCodes('&',
                                                "&a" + myScore + " &8- &c" + opScore);
                                        TitleUtil.sendGlowingTitle(plugin, p, title, subtitle, 10, 70, 20);
                                    } else {
                                                TitleUtil.sendGlowingTitle(plugin, p,
                                                plugin.getMsg("victory.title").replace("<player>",
                                                        finalWinner != null ? plugin.getEffectivePlayerName(finalWinner) : "Unknown"),
                                                plugin.getMsg("victory.subtitle").replace("<player>",
                                                        finalWinner != null ? plugin.getEffectivePlayerName(finalWinner) : "Unknown"),
                                                10, 70, 20);
                                    }
                                    plugin.playEndMatchSounds(p, true, fightPlayers);
                                } else {
                                    if (finalIsBestOf && !finalIsPearlFightEnd) {
                                        String t = finalIsBlueWinner ? plugin.getMsg("bestof.blue-wins.title")
                                                : plugin.getMsg("bestof.red-wins.title");
                                        String s = finalIsBlueWinner ? plugin.getMsg("bestof.blue-wins.subtitle")
                                                : plugin.getMsg("bestof.red-wins.subtitle");
                                        if (t == null || t.isEmpty())
                                            t = finalIsBlueWinner ? "&9&lBLUE WINS!" : "&c&lRED WINS!";
                                        if (s == null || s.isEmpty())
                                            s = finalIsBlueWinner ? "&9<blue_score> &8- &c<red_score>"
                                                    : "&c<red_score> &8- &9<blue_score>";
                                        s = s.replace("<blue_score>", String.valueOf(finalBlueScore))
                                                .replace("<red_score>", String.valueOf(finalRedScore));
                                        TitleUtil.sendGlowingTitle(plugin, p, ChatColor.translateAlternateColorCodes('&', t),
                                                ChatColor.translateAlternateColorCodes('&', s), 3, 14, 3);
                                    } else if (finalIsPartyFight) {
                                        String t = plugin.getMsg("party.defeat.title");
                                        String s = plugin.getMsg("party.defeat.subtitle");
                                        if (t == null || t.isEmpty())
                                            t = "&c&lDEFEAT!";
                                        if (s == null || s.isEmpty())
                                            s = "&c<opponent_leader> &fwon this Match!";
                                        String oppLeader = finalWinnerLeader != null ? finalWinnerLeader
                                                : (finalWinner != null ? plugin.getEffectivePlayerName(finalWinner) : "Unknown");
                                        t = t.replace("<opponent_leader>", oppLeader);
                                        s = s.replace("<opponent_leader>", oppLeader);
                                        TitleUtil.sendGlowingTitle(plugin, p, ChatColor.translateAlternateColorCodes('&', t),
                                                ChatColor.translateAlternateColorCodes('&', s), 10, 70, 20);
                                    } else if (finalIsDuelReq && finalWinner != null
                                            && plugin.duelScoreManager != null) {
                                        Player myOpponent = finalWinner;
                                        int myScore = plugin.duelScoreManager.getScore(p.getUniqueId(),
                                                myOpponent.getUniqueId());
                                        int opScore = plugin.duelScoreManager.getScore(myOpponent.getUniqueId(),
                                                p.getUniqueId());
                                        String title = ChatColor.translateAlternateColorCodes('&', "&c&lDEFEAT!");
                                        String subtitle = ChatColor.translateAlternateColorCodes('&',
                                                "&c" + myScore + " &8- &a" + opScore);
                                        TitleUtil.sendGlowingTitle(plugin, p, title, subtitle, 10, 70, 20);
                                    } else {
                                        String op = finalWinner != null ? plugin.getEffectivePlayerName(finalWinner) : "Unknown";
                                        TitleUtil.sendGlowingTitle(plugin, p, plugin.getMsg("defeat.title").replace("<opponent>", op),
                                                plugin.getMsg("defeat.subtitle").replace("<opponent>", op), 10, 70, 20);
                                    }
                                    plugin.playEndMatchSounds(p, false, fightPlayers);
                                    org.bukkit.Sound thunderSound = plugin.getSoundByName("ENTITY_LIGHTNING_BOLT_THUNDER");
                                    if (thunderSound != null) {
                                        try { p.playSound(p.getLocation(), thunderSound, 2.0f, 1.0f); } catch (Exception ignored) {}
                                    }
                                }

                                if (!plugin.leavingMatchPlayers.contains(p.getUniqueId())) {
                                    matchEndSpectators.add(p.getUniqueId());
                                    applyMatchEndVanish(p);
                                    enableMatchEndSpectatorFlight(p);
                                }

                                if (plugin.leavingMatchPlayers.remove(p.getUniqueId())) {

                                    matchEndedPlayers.remove(p.getUniqueId());
                                    pendingHubTeleports.remove(p.getUniqueId());
                                    matchPlayerGroups.remove(p.getUniqueId());
                                    matchEndTimes.remove(p.getUniqueId());
                                    stopPearlFightEndScoreboard(p.getUniqueId());
                                    restoreMatchEndSpectatorSpeed(p.getUniqueId());
                                    plugin.hubOnJoinSpawn.remove(p.getUniqueId());
                                    if (plugin.partyFFAManager != null) {
                                        plugin.partyFFAManager.cleanupBridgeCustomSpectator(p);
                                    }
                                    cleanupMatchEndVanish(p);

                                    p.getInventory().clear();
                                    p.getInventory().setArmorContents(null);
                                    try { p.getInventory().setItemInOffHand(null); } catch (NoSuchMethodError ignored) {}
                                    p.updateInventory();
                                    Location hubNow = null;
                                    try {
                                        Object spApi = plugin.getStrikePracticeAPI();
                                        if (spApi != null)
                                            hubNow = (Location) spApi.getClass().getMethod("getSpawnLocation").invoke(spApi);
                                    } catch (Exception ignored) {}
                                    if (hubNow == null) hubNow = p.getWorld().getSpawnLocation();
                                    try {
                                        Object spApi = plugin.getStrikePracticeAPI();
                                        if (spApi != null)
                                            spApi.getClass().getMethod("clear", Player.class, boolean.class, boolean.class)
                                                    .invoke(spApi, p, true, true);
                                    } catch (Exception ignored) {}
                                    if (p.isOnline()) forceHubTeleport(p, hubNow);
                                }
                            }
                        }
                    }.runTaskLater(plugin, 5L);
                }
            }
        } catch (Exception e) {
        }
    }

    @SuppressWarnings("unchecked")
    private void handleRoundStart(Event event) {
        try {
            Object fight = event.getClass().getMethod("getFight").invoke(event);
            if (fight == null) return;

            if (!plugin.endedFightWinners.containsKey(fight) && plugin.bridgeBlockResetManager != null) {
                try {

                    @SuppressWarnings("unchecked")
                    List<Player> rPlayers = new ArrayList<>();
                    try {
                        if (plugin.getMGetPlayersInFight() != null)
                            rPlayers = (List<Player>) plugin.getMGetPlayersInFight().invoke(fight);
                    } catch (Exception ignored) {}
                    if (rPlayers == null || rPlayers.isEmpty()) {
                        try {
                            if (plugin.getMGetPlayers() != null)
                                rPlayers = (List<Player>) plugin.getMGetPlayers().invoke(fight);
                        } catch (Exception ignored) {}
                    }
                    if (rPlayers == null || rPlayers.isEmpty()) {
                        try {
                            rPlayers = (List<Player>) event.getClass().getMethod("getPlayers").invoke(event);
                        } catch (Exception ignored) {}
                    }
                    if (rPlayers != null && !rPlayers.isEmpty()) {
                        String kn = null;
                        for (Player rp : rPlayers) {
                            if (rp != null && rp.isOnline()) { kn = plugin.getKitName(rp); if (kn != null) break; }
                        }
                        if (kn != null && kn.toLowerCase().contains("bridge")) {
                            final List<Player> fp = new ArrayList<>(rPlayers);
                            final Object finalFight = fight;

                            for (long delay : new long[]{2L, 5L, 10L, 15L}) {
                                new BukkitRunnable() {
                                    @Override public void run() {
                                        for (Player rp : fp) {
                                            if (rp != null && rp.isOnline())
                                                plugin.forceRestoreKitBlocks(rp, finalFight);
                                        }
                                    }
                                }.runTaskLater(plugin, delay);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (!plugin.endedFightWinners.containsKey(fight)) return;

            UUID winnerUUID = plugin.endedFightWinners.get(fight);

            Location hub = null;
            try {
                Object spApi = plugin.getStrikePracticeAPI();
                if (spApi != null)
                    hub = (Location) spApi.getClass().getMethod("getSpawnLocation").invoke(spApi);
            } catch (Exception ignored) {}

            List<Player> players = (List<Player>) event.getClass().getMethod("getPlayers").invoke(event);
            if (players == null || players.isEmpty()) return;

            Player loser = null;
            final Location finalHub = hub;
            for (Player p : players) {
                if (p == null || !p.isOnline()) continue;
                UUID pUuid = p.getUniqueId();
                if (winnerUUID != null && pUuid.equals(winnerUUID)) {

                    Location redirect = matchEndSpectators.contains(pUuid)
                            ? p.getLocation().clone()
                            : (finalHub != null ? finalHub : p.getLocation().clone());
                    plugin.hubOnJoinSpawn.put(pUuid, redirect);
                } else {

                    loser = p;
                    if (finalHub != null) plugin.hubOnJoinSpawn.put(pUuid, finalHub);
                }
            }

            if (loser == null) loser = players.get(0);
            if (plugin.getMHandleDeath() == null) return;

            final Player finalLoser = loser;
            final Object finalFight = fight;

            new BukkitRunnable() {
                @Override public void run() {
                    try { plugin.getMHandleDeath().invoke(finalFight, finalLoser); }
                    catch (Exception ignored) {}
                }
            }.runTaskLater(plugin, 1L);

        } catch (Exception ignored) {}
    }

    public void handleRoundEnd(Event event) {

        try {
            boolean isEndingNow = (boolean) event.getClass().getMethod("isEndingNow").invoke(event);
            if (!isEndingNow) {
                final Object fightObj = event.getClass().getMethod("getFight").invoke(event);
                java.util.Collection<?> losers =
                        (java.util.Collection<?>) event.getClass().getMethod("getLosers").invoke(event);
                boolean foundLeaver = false;
                Player leaver = null;
                Player roundWinner = null;
                for (Object loserObj : losers) {
                    if (!(loserObj instanceof Player)) continue;
                    Player p = (Player) loserObj;
                    if (!p.isOnline()) continue;
                    if (!plugin.leavingMatchPlayers.contains(p.getUniqueId())) continue;
                    foundLeaver = true;
                    leaver = p;
                    break;
                }
                if (foundLeaver && fightObj != null) {

                    try {
                        java.util.Collection<?> winners =
                                (java.util.Collection<?>) event.getClass().getMethod("getWinners").invoke(event);
                        for (Object w : winners) {
                            if (w instanceof Player && ((Player) w).isOnline()) {
                                roundWinner = (Player) w; break;
                            }
                        }
                    } catch (Exception ignored) {}

                    String kitName = plugin.getKitName(roundWinner != null ? roundWinner : leaver);
                    if (kitName == null) {
                        try {
                            Object kit = fightObj.getClass().getMethod("getKit").invoke(fightObj);
                            if (kit != null) kitName = (String) kit.getClass().getMethod("getName").invoke(kit);
                        } catch (Exception ignored) {}
                    }

                    handleBestOfLeave(fightObj, leaver, roundWinner, kitName);
                    return;
                }
            }
        } catch (Exception ignored) {}

        try {
            final Object fight = event.getClass().getMethod("getFight").invoke(event);
            if (fight == null)
                return;

            boolean isPartyFfaFight = plugin.partyFFAManager != null && plugin.partyFFAManager.isPartyFFA(fight);
            boolean isPartySplitFight = plugin.partySplitManager != null && plugin.partySplitManager.isPartySplit(fight);
            boolean isPartyVsPartyFight = plugin.partyVsPartyManager != null && plugin.partyVsPartyManager.isPartyVsParty(fight);

            String roundKitName = null;
            try {
                Object kit = fight.getClass().getMethod("getKit").invoke(fight);
                if (kit != null) {
                    roundKitName = (String) kit.getClass().getMethod("getName").invoke(kit);
                }
            } catch (Exception ignored) {}
            if (roundKitName == null || roundKitName.isEmpty()) {
                for (Player player : resolveAllFightPlayers(fight, new ArrayList<>())) {
                    if (player == null) continue;
                    roundKitName = plugin.getKitName(player);
                    if (roundKitName != null && !roundKitName.isEmpty()) {
                        break;
                    }
                }
            }

            boolean isPartyStickfight = roundKitName != null
                    && roundKitName.toLowerCase().contains("stickfight")
                    && (isPartySplitFight || isPartyVsPartyFight);

            if (isPartyFfaFight || ((isPartySplitFight || isPartyVsPartyFight) && !isPartyStickfight))
                return;

            boolean isEndingNowTmp = false;
            try { isEndingNowTmp = (boolean) event.getClass().getMethod("isEndingNow").invoke(event); }
            catch (Exception ignored) {}
            final boolean finalIsEndingNow = isEndingNowTmp;

            Player tempWinner = null;
            for (Method m : event.getClass().getMethods()) {
                if ((m.getName().toLowerCase().contains("winner") || m.getName().equalsIgnoreCase("getPlayer"))
                        && m.getParameterTypes().length == 0) {
                    try {
                        Object res = m.invoke(event);
                        if (res instanceof Player) {
                            tempWinner = (Player) res;
                            break;
                        } else if (res instanceof UUID) {
                            tempWinner = Bukkit.getPlayer((UUID) res);
                            break;
                        } else if (res instanceof String) {
                            tempWinner = Bukkit.getPlayer((String) res);
                            break;
                        }
                    } catch (Exception ex) {
                    }
                }
            }

            Object tempBestOf = null;
            try {
                Method getBestOf = fight.getClass().getMethod("getBestOf");
                tempBestOf = getBestOf.invoke(fight);
            } catch (Exception ignored) {
            }

            final Player initialWinner = tempWinner;
            final Object bestOf = tempBestOf;

            if (!finalIsEndingNow) {
                fightCountdownCooldown.put(fight, System.currentTimeMillis() + 10000L);
            }

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (plugin.endedFightWinners.containsKey(fight)) {
                        return;
                    }

                    Player winner = initialWinner;

                    if (bestOf != null) {
                        try {
                            Method getRoundsWon = bestOf.getClass().getMethod("getRoundsWon");
                            Map<UUID, Integer> currentScores = (Map<UUID, Integer>) getRoundsWon.invoke(bestOf);
                            Map<UUID, Integer> oldScores = plugin.matchScores.getOrDefault(fight, new HashMap<>());

                            if (currentScores != null) {
                                for (Map.Entry<UUID, Integer> entry : currentScores.entrySet()) {
                                    UUID u = entry.getKey();
                                    int newScore = entry.getValue();
                                    int oldScore = oldScores.getOrDefault(u, 0);

                                    if (newScore > oldScore) {
                                        winner = Bukkit.getPlayer(u);
                                        break;
                                    }
                                }
                                plugin.matchScores.put(fight, new HashMap<>(currentScores));
                            }
                        } catch (Exception ex) {
                        }
                    }

                    if (winner == null)
                        return;

                    String kitName = plugin.getKitName(winner);
                    if (kitName == null) {
                        try {
                            Object kit = fight.getClass().getMethod("getKit").invoke(fight);
                            if (kit != null)
                                kitName = (String) kit.getClass().getMethod("getName").invoke(kit);
                        } catch (Exception e) {
                        }
                    }

                    if (kitName == null)
                        return;
                    String kitLower = kitName.toLowerCase();

                    if (plugin.pearlFightManager != null && plugin.pearlFightManager.isPearlFightKit(kitLower)) {
                        List<Player> pearlFightPlayers = new ArrayList<>();
                        pearlFightPlayers.add(winner);
                        for (Player p : resolveAllFightPlayers(fight, pearlFightPlayers)) {
                            if (p != null) {
                                plugin.frozenPlayers.remove(p.getUniqueId());
                                plugin.roundTransitionPlayers.remove(p.getUniqueId());
                            }
                        }
                        return;
                    }

                    boolean allowed = plugin.roundEndKits.isEmpty();
                    if (kitLower.contains("bridge") || kitLower.contains("thebridge")
                            || kitLower.contains("thebridgeelo")) {
                        allowed = true;
                    } else if (!allowed) {
                        for (String k : plugin.roundEndKits) {
                            if (kitLower.contains(k.toLowerCase())) {
                                allowed = true;
                                break;
                            }
                        }
                    }

                    if (!allowed && bestOf != null) allowed = true;

                    if (!allowed)
                        return;

                    boolean isBridgeKitReset = plugin.isRoundScoredKit(kitLower);
                    if (!isBridgeKitReset || finalIsEndingNow) {
                        if (plugin.getMGetArena() != null) {
                            try {
                                Object arena = plugin.getMGetArena().invoke(fight);
                                if (arena != null) {
                                    String arenaName = getArenaName(arena);
                                    forceFixBeds(arenaName, fight, 80L);
                                    scheduleArenaRestore(arenaName, fight);
                                }
                            } catch (Exception ex) {
                            }
                        }
                    }

                    List<Player> matchPlayers = new ArrayList<>();
                    try {
                        Method getPlayers = fight.getClass().getMethod("getPlayersInFight");
                        List<Player> fPlayers = (List<Player>) getPlayers.invoke(fight);
                        if (fPlayers != null)
                            matchPlayers.addAll(fPlayers);
                    } catch (Exception e) {
                        try {
                            Method getPlayers = fight.getClass().getMethod("getPlayers");
                            List<Player> fPlayers = (List<Player>) getPlayers.invoke(fight);
                            if (fPlayers != null)
                                matchPlayers.addAll(fPlayers);
                        } catch (Exception ignored) {
                        }
                    }
                    if (matchPlayers.isEmpty())
                        matchPlayers.add(winner);

                    boolean isPartyFight = (plugin.partySplitManager != null
                            && plugin.partySplitManager.isPartySplit(fight))
                            || (plugin.partyVsPartyManager != null && plugin.partyVsPartyManager.isPartyVsParty(fight));
                    if (isPartyFight) {
                        for (Player partyPlayer : resolveAllFightPlayers(fight, new ArrayList<>(matchPlayers))) {
                            if (partyPlayer != null && !matchPlayers.contains(partyPlayer)) {
                                matchPlayers.add(partyPlayer);
                            }
                        }
                    }

                    Map<UUID, Integer> curScores = plugin.matchScores.getOrDefault(fight, new HashMap<>());
                    String winnerColor = plugin.getTeamColorCode(winner, fight);
                    Player loserPlayer = null;
                    for (Player p : matchPlayers) {
                        if (p != null && !p.getUniqueId().equals(winner.getUniqueId())) {
                            loserPlayer = p;
                            break;
                        }
                    }
                    String loserColor = loserPlayer != null ? plugin.getTeamColorCode(loserPlayer, fight) : "§c";
                    int wScore = curScores.getOrDefault(winner.getUniqueId(), 0);
                    int lScore = loserPlayer != null ? curScores.getOrDefault(loserPlayer.getUniqueId(), 0) : 0;

                    String rawScoredTitle = plugin.getMsg("scored.title");
                    String rawScoredSub = plugin.getMsg("scored.subtitle");
                    if (rawScoredTitle == null || rawScoredTitle.isEmpty())
                        rawScoredTitle = "&e&l<player>";
                    if (rawScoredSub == null || rawScoredSub.isEmpty())
                        rawScoredSub = "&6Scored!";
                    final String scoredTitle = ChatColor.translateAlternateColorCodes('&',
                            rawScoredTitle.replace("<player>", plugin.getEffectivePlayerName(winner)));
                    final String scoredSubtitle = ChatColor.translateAlternateColorCodes('&',
                            rawScoredSub.replace("<player>", plugin.getEffectivePlayerName(winner)))
                            + "  " + winnerColor + wScore + " §8- " + loserColor + lScore;

                    if (kitLower.contains("bridge") || kitLower.contains("thebridge")) {

                        final Object roundFight = fight;
                        final List<Player> frozenList = new ArrayList<>(matchPlayers);
                        markBridge1v1RoundTransitionState(frozenList);

                        Sound goalSound = plugin.getSoundByName("LEVEL_UP");
                        if (goalSound == null)
                            goalSound = plugin.getSoundByName("ENTITY_PLAYER_LEVELUP");
                        final Sound finalGoalSound = goalSound;
                        for (Player p : frozenList) {
                            if (p != null && p.isOnline()) {
                                plugin.sendTitle(p, scoredTitle, scoredSubtitle, 5, 60, 10);
                                if (finalGoalSound != null)
                                    p.playSound(p.getLocation(), finalGoalSound, 1.0f, 1.0f);
                            }
                        }

                        for (Player p : frozenList) {
                            if (p != null && p.isOnline()) {
                                plugin.forceRestoreKitBlocks(p, roundFight);
                            }
                        }

                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (plugin.endedFightWinners.containsKey(fight)) {
                                    clearBridge1v1RoundTransitionState(frozenList);
                                    return;
                                }
                                teleportBridge1v1PlayersToSpawns(frozenList, roundFight);
                                startBridge1v1Countdown(frozenList, roundFight);
                            }
                        }.runTaskLater(plugin, 60L);

                    } else {

                        Sound levelUp = plugin.getSoundByName("LEVEL_UP");
                        if (levelUp == null)
                            levelUp = plugin.getSoundByName("ENTITY_PLAYER_LEVELUP");
                        final Sound finalLevelUp = levelUp;
                        final String finalKitName = kitName;
                        final boolean finalIsPartyFight = isPartyFight;

                        fightCountdownCooldown.put(fight, System.currentTimeMillis() + 10000L);

                        for (Player p : matchPlayers) {
                            if (p != null && p.isOnline()) {
                                plugin.frozenPlayers.add(p.getUniqueId());
                                plugin.roundTransitionPlayers.add(p.getUniqueId());
                            }
                        }

                        new BukkitRunnable() {
                            int ticks = 20;
                            boolean soundPlayed = false;

                            @Override
                            public void run() {
                                boolean isStickfight = finalKitName != null
                                        && finalKitName.toLowerCase().contains("stickfight");

                                if (ticks <= 0 || isStickfight) {
                                    cancel();

                                    if (!plugin.endedFightWinners.containsKey(fight)) {
                                        fightCountdownCooldown.put(fight, System.currentTimeMillis());
                                        startMatchCountdown(matchPlayers, fight, finalIsPartyFight, finalKitName, true);
                                    } else {

                                        for (Player p : matchPlayers) {
                                            if (p != null) {
                                                plugin.frozenPlayers.remove(p.getUniqueId());
                                                plugin.roundTransitionPlayers.remove(p.getUniqueId());
                                            }
                                        }
                                    }
                                    return;
                                }
                                for (Player p : matchPlayers) {
                                    if (p != null && p.isOnline()) {
                                        plugin.sendTitle(p, scoredTitle, scoredSubtitle, 0, 10, 0);
                                        if (!soundPlayed && finalLevelUp != null) {
                                            p.playSound(p.getLocation(), finalLevelUp, 1.0f, 1.0f);
                                        }
                                    }
                                }
                                soundPlayed = true;
                                ticks -= 5;
                            }
                        }.runTaskTimer(plugin, 0L, 5L);
                    }
                }
            }.runTaskLater(plugin, 2L);

        } catch (Exception e) {
        }
    }

    @SuppressWarnings("unchecked")
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBridge1v1PortalMove(PlayerMoveEvent event) {
        int scoreLimit = plugin.getConfig().getInt("settings.bridge-score-limit", 0);
        if (scoreLimit <= 0) return;
        if (event instanceof PlayerTeleportEvent) return;
        if (event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        Player player = event.getPlayer();
        if (!plugin.isHooked() || !plugin.isInFight(player)) return;

        Location toLoc = event.getTo();
        Block feet  = toLoc.getBlock();
        Block below = feet.getRelative(BlockFace.DOWN);
        Block portalBlock = null;
        if (feet.getType().name().contains("END_PORTAL") || feet.getType().name().contains("ENDER_PORTAL"))
            portalBlock = feet;
        else if (below.getType().name().contains("END_PORTAL") || below.getType().name().contains("ENDER_PORTAL"))
            portalBlock = below;
        if (portalBlock == null) return;

        Object fight;
        try {
            fight = plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), player);
        } catch (Exception e) { return; }
        if (fight == null) return;

    }

    private void markBridge1v1RoundTransitionState(List<Player> players) {
        for (Player p : players) {
            if (p == null || !p.isOnline()) continue;
            plugin.frozenPlayers.add(p.getUniqueId());
            plugin.roundTransitionPlayers.add(p.getUniqueId());
            plugin.activeStartCountdownPlayers.remove(p.getUniqueId());
            plugin.syncLayoutInstant(p, 2);
        }
    }

    private void clearBridge1v1RoundTransitionState(List<Player> players) {
        for (Player p : players) {
            if (p == null) continue;
            plugin.frozenPlayers.remove(p.getUniqueId());
            plugin.roundTransitionPlayers.remove(p.getUniqueId());
            plugin.activeStartCountdownPlayers.remove(p.getUniqueId());
            if (p.isOnline()) {
                plugin.syncLayoutInstant(p, 2);
            }
        }
    }

    private Location resolveBridge1v1RoundSpawn(Player player, Object fight) {
        if (player == null) {
            return null;
        }

        // Prefer the cached spawn (set correctly at round start / previous round transition)
        // over getCountdownArenaSpawn, which may invoke the distance fallback in
        // tryGetArenaSpawnForPlayer and return the *opponent's* spawn when the scoring
        // player is standing near the opponent's portal right after a score.
        Location cachedSpawn = plugin.arenaSpawnLocations.get(player.getUniqueId());
        if (cachedSpawn != null) {
            Location safe = plugin.resolveSafeArenaLocation(cachedSpawn.clone());
            Location spawn = safe != null ? safe : cachedSpawn.clone();
            plugin.arenaSpawnLocations.put(player.getUniqueId(), spawn.clone());
            return spawn;
        }

        Location spawn = getCountdownArenaSpawn(player, fight, false);
        if (spawn != null) {
            plugin.arenaSpawnLocations.put(player.getUniqueId(), spawn.clone());
        }
        return spawn;
    }

    private void teleportBridge1v1PlayersToSpawns(List<Player> players, Object fight) {
        for (Player p : players) {
            if (p == null || !p.isOnline()) continue;

            plugin.clearRoundTransitionRespawnState(p);
            if (plugin.voidManager != null) plugin.voidManager.clearCooldown(p.getUniqueId());

            Location spawn = resolveBridge1v1RoundSpawn(p, fight);
            if (spawn == null) continue;

            Location opponentSpawn = null;
            for (Player other : players) {
                if (other == null || !other.isOnline() || other.getUniqueId().equals(p.getUniqueId())) continue;
                opponentSpawn = resolveBridge1v1RoundSpawn(other, fight);
                if (opponentSpawn != null) break;
            }

            p.setFallDistance(0f);
            p.setFireTicks(0);
            p.setVelocity(new Vector(0, 0, 0));
            if (plugin.teleportToSafeArenaLocation(p, bridge1v1FaceToward(spawn, opponentSpawn))) {
                plugin.applySafeArenaTeleportProtection(p);
            }
        }
    }

    private Location bridge1v1FaceToward(Location from, Location target) {
        if (target == null) return from;
        Location loc = from.clone();
        double dx = target.getX() - from.getX();
        double dz = target.getZ() - from.getZ();
        float yaw = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
        loc.setYaw(yaw);
        loc.setPitch(0f);
        return loc;
    }

    private void startBridge1v1Countdown(List<Player> players, Object fight) {
        if (!plugin.startCountdownEnabled) {
            new BukkitRunnable() {
                @Override public void run() {
                    clearBridge1v1RoundTransitionState(players);
                }
            }.runTaskLater(plugin, 20L);
            return;
        }
        final int maxSeconds = 3;
        new BukkitRunnable() {
            int current = maxSeconds;
            @Override
            public void run() {
                if (bridge1v1EndedFights.contains(fight)) {
                    clearBridge1v1RoundTransitionState(players);
                    cancel();
                    return;
                }
                try {
                    if ((boolean) fight.getClass().getMethod("hasEnded").invoke(fight)) {
                        clearBridge1v1RoundTransitionState(players);
                        cancel();
                        return;
                    }
                } catch (Exception ignored) {}
                if (current <= 0) {
                    for (Player p : players) {
                        if (p == null || !p.isOnline()) continue;
                        if (plugin.startMatchMessage != null && !plugin.startMatchMessage.isEmpty())
                            p.sendMessage(plugin.startMatchMessage);
                        if (plugin.startCountdownSoundEnabled && plugin.startMatchSound != null)
                            p.playSound(p.getLocation(), plugin.startMatchSound,
                                    plugin.startCountdownVolume, plugin.startCountdownPitch);
                        plugin.frozenPlayers.remove(p.getUniqueId());
                        plugin.roundTransitionPlayers.remove(p.getUniqueId());
                        plugin.activeStartCountdownPlayers.remove(p.getUniqueId());
                        if (plugin.blockReplenishManager != null)
                            plugin.blockReplenishManager.scanPlayerInventory(p);
                        plugin.syncLayoutInstant(p, 2);
                        final Player fp = p;
                        plugin.forceRestoreKitBlocks(fp, fight);
                        for (long dl : new long[]{2L, 5L, 10L}) {
                            new BukkitRunnable() {
                                @Override public void run() {
                                    if (fp.isOnline()) {
                                        plugin.forceRestoreKitBlocks(fp, fight);
                                    }
                                }
                            }.runTaskLater(plugin, dl);
                        }
                    }
                    cancel();
                    return;
                }
                for (Player p : players) {
                    if (p == null || !p.isOnline()) continue;
                    if (plugin.startCountdownMessages != null)
                        p.sendMessage(plugin.startCountdownMessages.getOrDefault(current,
                                ChatColor.RED + String.valueOf(current)));
                    if (plugin.startCountdownSoundEnabled && plugin.startCountdownSounds != null) {
                        Sound s = plugin.startCountdownSounds.get(current);
                        if (s != null) p.playSound(p.getLocation(), s,
                                plugin.startCountdownVolume, plugin.startCountdownPitch);
                    }
                }
                current--;
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }
}
