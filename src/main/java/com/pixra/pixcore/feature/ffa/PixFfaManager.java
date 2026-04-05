package com.pixra.pixcore.feature.ffa;

import com.pixra.pixcore.PixCore;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PixFfaManager implements Listener {

    private final PixCore plugin;

    private final Set<UUID> arenaPlayers = new HashSet<>();
    private final Map<UUID, PlayerSnapshot> arenaSnapshots = new HashMap<>();
    private final Map<UUID, PlayerSnapshot> battlekitEditSnapshots = new HashMap<>();
    private final Map<UUID, String> battlekitEditors = new HashMap<>();
    private final Map<UUID, Location> battlekitEditorLocations = new HashMap<>();
    private final Map<UUID, Location> pendingHubTeleports = new HashMap<>();
    private final Map<UUID, FfaPair> pairByPlayer = new HashMap<>();
    private final Map<UUID, Long> localMessageCooldowns = new HashMap<>();
    private final Set<UUID> respawningPlayers = new HashSet<>();

    private File configFile;
    private FileConfiguration config;
    private BukkitTask actionBarTask;

    private boolean enabled;
    private boolean lobbyItemEnabled;
    private boolean buildEnabled;
    private boolean requireReciprocalHit;
    private int combatRequestSeconds;
    private int combatActiveSeconds;
    private int lobbyItemSlot;
    private int lobbyHubRadius;
    private long respawnDelayTicks;
    private double voidYThreshold;
    private Material lobbyItemMaterial;
    private String lobbyItemName;
    private List<String> lobbyItemLore = new ArrayList<>();
    private String activeBattlekit;
    private String combatActionBarFormat;
    private Location arenaSpawn;

    public PixFfaManager(PixCore plugin) {
        this.plugin = plugin;
        loadConfig();
        startActionBarTask();
    }

    public void reload() {
        loadConfig();
        startActionBarTask();
    }

    public void bootstrapOnlinePlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (pendingHubTeleports.containsKey(player.getUniqueId())) {
                schedulePendingHubTeleport(player);
                continue;
            }
            scheduleLobbyItemRefreshSequence(player);
        }
    }

    public void shutdown(boolean teleportPlayersToHub) {
        if (actionBarTask != null) {
            actionBarTask.cancel();
            actionBarTask = null;
        }

        for (UUID uuid : new HashSet<>(arenaPlayers)) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                leaveArenaInternal(player, teleportPlayersToHub, false);
            } else {
                clearArenaRuntime(uuid);
            }
        }

        for (Map.Entry<UUID, PlayerSnapshot> entry : new HashMap<>(battlekitEditSnapshots).entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                entry.getValue().restore(player);
                player.updateInventory();
            }
        }

        battlekitEditSnapshots.clear();
        battlekitEditors.clear();
        battlekitEditorLocations.clear();
        pendingHubTeleports.clear();
        respawningPlayers.clear();
        localMessageCooldowns.clear();
    }

    public boolean isArenaPlayer(Player player) {
        return player != null && arenaPlayers.contains(player.getUniqueId());
    }

    public boolean isEditingBattlekit(Player player) {
        return player != null && battlekitEditors.containsKey(player.getUniqueId());
    }

    public Collection<String> getBattlekitNames() {
        if (!config.isConfigurationSection("battlekits")) {
            return Collections.emptyList();
        }

        Set<String> names = new LinkedHashSet<>();
        for (String key : config.getConfigurationSection("battlekits").getKeys(false)) {
            if (key != null && !key.trim().isEmpty()) {
                names.add(key);
            }
        }
        return names;
    }

    public String getActiveBattlekitName() {
        String normalized = normalizeKey(activeBattlekit);
        if (!normalized.isEmpty() && hasBattlekit(normalized)) {
            return normalized;
        }

        for (String name : getBattlekitNames()) {
            String candidate = normalizeKey(name);
            if (!candidate.isEmpty()) {
                activeBattlekit = candidate;
                config.set("battlekit.active", candidate);
                saveConfigFile();
                return candidate;
            }
        }
        return null;
    }

    public boolean isBuildEnabled() {
        return buildEnabled;
    }

    public boolean setArenaPosition(Player player) {
        if (player == null) {
            return false;
        }

        arenaSpawn = player.getLocation().clone();
        saveLocation("arena.spawn", arenaSpawn);
        saveConfigFile();
        sendMessage(player, "&aFFA arena position saved successfully.");
        return true;
    }

    public boolean setBuildEnabled(boolean value) {
        buildEnabled = value;
        config.set("battlekit.build-enabled", value);
        saveConfigFile();
        return true;
    }

    public boolean selectBattlekit(String name) {
        String normalized = normalizeKey(name);
        if (normalized.isEmpty() || !hasBattlekit(normalized)) {
            return false;
        }

        activeBattlekit = normalized;
        config.set("battlekit.active", normalized);
        saveConfigFile();
        return true;
    }

    public boolean createBattlekit(Player player, String name) {
        String normalized = normalizeKey(name);
        if (player == null || normalized.isEmpty() || hasBattlekit(normalized)) {
            return false;
        }

        storeBattlekit(normalized, BattlekitData.capture(player));
        activeBattlekit = normalized;
        config.set("battlekit.active", normalized);
        saveConfigFile();
        return true;
    }

    public boolean saveBattlekit(Player player, String providedName) {
        if (player == null) {
            return false;
        }

        UUID uuid = player.getUniqueId();
        String normalized = normalizeKey(providedName);
        if (normalized.isEmpty()) {
            normalized = normalizeKey(battlekitEditors.get(uuid));
        }
        if (normalized.isEmpty()) {
            return false;
        }

        storeBattlekit(normalized, BattlekitData.capture(player));
        saveConfigFile();

        PlayerSnapshot snapshot = battlekitEditSnapshots.remove(uuid);
        battlekitEditors.remove(uuid);
        battlekitEditorLocations.remove(uuid);
        if (snapshot != null) {
            snapshot.restore(player);
            player.updateInventory();
        }

        return true;
    }

    public boolean startBattlekitEdit(Player player, String name) {
        String normalized = normalizeKey(name);
        if (player == null || normalized.isEmpty() || !hasBattlekit(normalized)) {
            return false;
        }
        if (isArenaPlayer(player) || plugin.isInFight(player) || respawningPlayers.contains(player.getUniqueId())) {
            return false;
        }
        if (battlekitEditors.containsKey(player.getUniqueId())) {
            return false;
        }

        BattlekitData data = loadBattlekit(normalized);
        if (data == null) {
            return false;
        }

        battlekitEditSnapshots.put(player.getUniqueId(), PlayerSnapshot.capture(player));
        battlekitEditors.put(player.getUniqueId(), normalized);
        battlekitEditorLocations.put(player.getUniqueId(), player.getLocation().clone());
        player.closeInventory();
        player.setVelocity(new Vector(0, 0, 0));
        applyBattlekit(player, data);
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setWalkSpeed(0f);
        player.setFlySpeed(0f);
        player.setFallDistance(0f);
        player.updateInventory();
        return true;
    }

    public boolean cancelBattlekitEdit(Player player) {
        if (player == null) {
            return false;
        }

        UUID uuid = player.getUniqueId();
        PlayerSnapshot snapshot = battlekitEditSnapshots.remove(uuid);
        battlekitEditors.remove(uuid);
        battlekitEditorLocations.remove(uuid);
        if (snapshot == null) {
            return false;
        }

        snapshot.restore(player);
        player.updateInventory();
        return true;
    }

    public boolean joinArena(Player player) {
        if (player == null) {
            return false;
        }

        if (!enabled) {
            sendMessage(player, getMessage("arena-closed"));
            return false;
        }
        if (isArenaPlayer(player)) {
            return true;
        }
        if (plugin.isInFight(player)) {
            sendMessage(player, getMessage("in-strikepractice-fight"));
            return false;
        }
        if (battlekitEditors.containsKey(player.getUniqueId())) {
            sendMessage(player, "&cFinish editing your FFA battlekit first.");
            return false;
        }
        if (!buildEnabled) {
            sendMessage(player, getMessage("arena-closed"));
            return false;
        }

        BattlekitData activeKit = loadBattlekit(getActiveBattlekitName());
        if (activeKit == null) {
            sendMessage(player, getMessage("no-battlekit"));
            return false;
        }

        Location spawn = getResolvedArenaSpawn();
        if (spawn == null) {
            sendMessage(player, getMessage("setup-missing"));
            return false;
        }

        arenaSnapshots.put(player.getUniqueId(), PlayerSnapshot.capture(player));
        arenaPlayers.add(player.getUniqueId());
        respawningPlayers.remove(player.getUniqueId());
        clearPair(player, false);

        player.closeInventory();
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        applyBattlekit(player, activeKit);
        player.setVelocity(new Vector(0, 0, 0));
        plugin.teleportToSafeArenaLocation(player, spawn);
        plugin.applySafeArenaTeleportProtection(player, false);
        sendMessage(player, getMessage("join"));
        return true;
    }

    public boolean leaveArena(Player player, boolean sendFeedback) {
        if (player == null || !isArenaPlayer(player)) {
            return false;
        }
        leaveArenaInternal(player, true, sendFeedback);
        return true;
    }

    private void leaveArenaInternal(Player player, boolean teleportToHub, boolean sendFeedback) {
        UUID uuid = player.getUniqueId();
        clearPair(player, true);
        respawningPlayers.remove(uuid);
        arenaPlayers.remove(uuid);

        PlayerSnapshot snapshot = arenaSnapshots.remove(uuid);
        if (snapshot != null) {
            snapshot.restore(player);
        } else {
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            setOffhandItem(player.getInventory(), null);
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
            player.setSaturation(20f);
        }

        player.setFireTicks(0);
        player.setFallDistance(0f);
        player.updateInventory();

        if (teleportToHub) {
            Location hub = plugin.resolveHubLocation(player);
            if (hub != null) {
                player.teleport(hub);
            }
            scheduleLobbyItemRefreshSequence(player);
        }

        if (sendFeedback) {
            sendMessage(player, getMessage("leave"));
        }
    }

    private void handleArenaRespawn(Player player, Player attacker) {
        if (player == null || !player.isOnline() || !isArenaPlayer(player)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        if (respawningPlayers.contains(uuid)) {
            return;
        }

        clearPair(player, true);
        respawningPlayers.add(uuid);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !isArenaPlayer(player)) {
                    respawningPlayers.remove(uuid);
                    return;
                }

                BattlekitData activeKit = loadBattlekit(getActiveBattlekitName());
                Location spawn = getResolvedArenaSpawn();
                if (activeKit == null || spawn == null) {
                    respawningPlayers.remove(uuid);
                    leaveArenaInternal(player, true, true);
                    return;
                }

                player.closeInventory();
                player.setGameMode(GameMode.SURVIVAL);
                player.setAllowFlight(false);
                player.setFlying(false);
                player.setVelocity(new Vector(0, 0, 0));
                player.setHealth(player.getMaxHealth());
                player.setFoodLevel(20);
                player.setSaturation(20f);
                player.setFireTicks(0);
                player.setFallDistance(0f);
                applyBattlekit(player, activeKit);
                plugin.teleportToSafeArenaLocation(player, spawn);
                plugin.applySafeArenaTeleportProtection(player, false);
                player.updateInventory();
                sendMessage(player, getMessage("respawn"));
                respawningPlayers.remove(uuid);
            }
        }.runTaskLater(plugin, Math.max(1L, respawnDelayTicks));

        if (attacker != null && attacker.isOnline() && isArenaPlayer(attacker)) {
            sendMessage(attacker, "&aYou defeated " + player.getName() + " in FFA.");
        }
    }

    private void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "pixffa.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            plugin.saveResource("pixffa.yml", false);
        }

        config = new YamlConfiguration();
        try {
            config.load(configFile);
        } catch (InvalidConfigurationException e) {
            recoverBrokenConfig(e);
        } catch (Exception e) {
            recoverBrokenConfig(e);
        }

        enabled = config.getBoolean("enabled", true);
        lobbyItemEnabled = config.getBoolean("lobby-item.enabled", true);
        buildEnabled = config.getBoolean("battlekit.build-enabled", false);
        requireReciprocalHit = config.getBoolean("combat.require-reciprocal-hit", true);
        combatRequestSeconds = Math.max(1, config.getInt("combat.request-seconds", 20));
        combatActiveSeconds = Math.max(1, config.getInt("combat.active-seconds", 20));
        respawnDelayTicks = Math.max(1L, config.getLong("arena.respawn-delay-ticks", 2L));
        voidYThreshold = config.getDouble("arena.void-y-threshold", -64.0D);
        lobbyItemSlot = normalizeSlot(config.getInt("lobby-item.slot", 3));
        lobbyHubRadius = Math.max(0, config.getInt("lobby-item.hub-radius", 24));
        lobbyItemMaterial = parseMaterial(config.getString("lobby-item.material", "GOLD_SWORD"),
                "GOLDEN_SWORD", "GOLD_SWORD", "STONE_SWORD");
        lobbyItemName = config.getString("lobby-item.name", "&6&lPix FFA");
        lobbyItemLore = new ArrayList<>(config.getStringList("lobby-item.lore"));
        activeBattlekit = normalizeKey(config.getString("battlekit.active", "default"));
        combatActionBarFormat = config.getString("combat.actionbar", "&6Combat: &e<seconds>s");
        arenaSpawn = loadLocation("arena.spawn");
    }

    private void startActionBarTask() {
        if (actionBarTask != null) {
            actionBarTask.cancel();
        }

        actionBarTask = new BukkitRunnable() {
            @Override
            public void run() {
                tickPairs();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void tickPairs() {
        long now = System.currentTimeMillis();
        Set<FfaPair> uniquePairs = new HashSet<>(pairByPlayer.values());
        for (FfaPair pair : uniquePairs) {
            if (pair == null) {
                continue;
            }

            Player first = plugin.getServer().getPlayer(pair.first);
            Player second = plugin.getServer().getPlayer(pair.second);
            if (first == null || second == null || !first.isOnline() || !second.isOnline()
                    || !isArenaPlayer(first) || !isArenaPlayer(second)) {
                clearPair(pair, false);
                continue;
            }

            long remainingMillis = pair.expiresAt - now;
            if (remainingMillis <= 0L) {
                if (!pair.pending) {
                    String key = "combat-ended";
                    sendMessage(first, formatMessage(getMessage(key), Map.of("<player>", second.getName())));
                    sendMessage(second, formatMessage(getMessage(key), Map.of("<player>", first.getName())));
                }
                sendActionBar(first, "");
                sendActionBar(second, "");
                clearPair(pair, false);
                continue;
            }

            int secondsLeft = (int) Math.ceil(remainingMillis / 1000.0D);
            String actionBar = color(combatActionBarFormat.replace("<seconds>", String.valueOf(secondsLeft)));
            sendActionBar(first, actionBar);
            sendActionBar(second, actionBar);
        }
    }

    private void handleCombatDamage(EntityDamageByEntityEvent event, Player attacker, Player victim) {
        if (attacker == null || victim == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        boolean attackerInArena = isArenaPlayer(attacker);
        boolean victimInArena = isArenaPlayer(victim);
        if (!attackerInArena || !victimInArena) {
            if (attackerInArena || victimInArena) {
                event.setCancelled(true);
                event.setDamage(0.0D);
                attacker.setNoDamageTicks(0);
                victim.setNoDamageTicks(0);
            }
            return;
        }

        FfaPair attackerPair = getActivePair(attacker.getUniqueId());
        FfaPair victimPair = getActivePair(victim.getUniqueId());

        if (attackerPair != null && attackerPair == victimPair) {
            if (attackerPair.pending) {
                if (!requireReciprocalHit || !attackerPair.isInitiator(attacker.getUniqueId())) {
                    attackerPair.pending = false;
                    attackerPair.refresh(combatActiveSeconds);
                    event.setCancelled(false);
                    event.setDamage(0.0D);
                    attacker.setNoDamageTicks(0);
                    victim.setNoDamageTicks(0);
                    sendMessage(attacker, formatMessage(getMessage("combat-started"), Map.of("<player>", victim.getName())));
                    sendMessage(victim, formatMessage(getMessage("combat-started"), Map.of("<player>", attacker.getName())));
                    return;
                }

                attackerPair.refresh(combatRequestSeconds);
                event.setCancelled(true);
                event.setDamage(0.0D);
                attacker.setNoDamageTicks(0);
                victim.setNoDamageTicks(0);
                return;
            }

            attackerPair.refresh(combatActiveSeconds);
            event.setCancelled(false);
            event.setDamage(0.0D);
            attacker.setNoDamageTicks(0);
            victim.setNoDamageTicks(0);
            return;
        }

        if (victimPair != null && attackerPair != victimPair) {
            event.setCancelled(true);
            event.setDamage(0.0D);
            attacker.setNoDamageTicks(0);
            victim.setNoDamageTicks(0);
            if (shouldSendLocalMessage(attacker.getUniqueId())) {
                String message = config.getString("messages.blocked-third-party", "&7<player> is already in combat with another player.");
                sendMessage(attacker, formatMessage(message, Map.of("<player>", victim.getName())));
            }
            return;
        }

        if (attackerPair != null || victimPair != null) {
            event.setCancelled(true);
            event.setDamage(0.0D);
            attacker.setNoDamageTicks(0);
            victim.setNoDamageTicks(0);
            if (shouldSendLocalMessage(attacker.getUniqueId())) {
                sendMessage(attacker, config.getString("messages.already-in-combat", "&7You are already in combat with another player."));
            }
            return;
        }

        FfaPair pair = new FfaPair(attacker.getUniqueId(), victim.getUniqueId(), attacker.getUniqueId(), true);
        pair.refresh(combatRequestSeconds);
        pairByPlayer.put(attacker.getUniqueId(), pair);
        pairByPlayer.put(victim.getUniqueId(), pair);

        event.setCancelled(false);
        event.setDamage(0.0D);
        attacker.setNoDamageTicks(0);
        victim.setNoDamageTicks(0);
    }

    private void clearPair(Player player, boolean notifyOpponent) {
        if (player == null) {
            return;
        }
        clearPair(getActivePair(player.getUniqueId()), notifyOpponent);
    }

    private void clearPair(FfaPair pair, boolean notifyPlayers) {
        if (pair == null) {
            return;
        }

        pairByPlayer.remove(pair.first);
        pairByPlayer.remove(pair.second);

        if (!notifyPlayers) {
            return;
        }

        Player first = plugin.getServer().getPlayer(pair.first);
        Player second = plugin.getServer().getPlayer(pair.second);

        if (first != null && first.isOnline()) {
            sendActionBar(first, "");
            if (second != null && second.isOnline()) {
                sendMessage(first, formatMessage(getMessage("combat-ended"), Map.of("<player>", second.getName())));
            }
        }
        if (second != null && second.isOnline()) {
            sendActionBar(second, "");
            if (first != null && first.isOnline()) {
                sendMessage(second, formatMessage(getMessage("combat-ended"), Map.of("<player>", first.getName())));
            }
        }
    }

    private FfaPair getActivePair(UUID uuid) {
        FfaPair pair = pairByPlayer.get(uuid);
        if (pair == null) {
            return null;
        }
        if (pair.expiresAt <= System.currentTimeMillis()) {
            clearPair(pair, false);
            return null;
        }
        return pair;
    }

    private void clearArenaRuntime(UUID uuid) {
        arenaPlayers.remove(uuid);
        arenaSnapshots.remove(uuid);
        respawningPlayers.remove(uuid);
        localMessageCooldowns.remove(uuid);
        FfaPair pair = pairByPlayer.remove(uuid);
        if (pair != null) {
            pairByPlayer.remove(pair.first);
            pairByPlayer.remove(pair.second);
        }
    }

    private boolean isMovementChanged(Location from, Location to) {
        if (from == null || to == null) {
            return false;
        }
        return from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ();
    }

    private Location createFrozenLocation(Location base, Location lookTarget) {
        if (base == null) {
            return lookTarget != null ? lookTarget.clone() : null;
        }

        Location frozen = base.clone();
        if (lookTarget != null) {
            frozen.setYaw(lookTarget.getYaw());
            frozen.setPitch(lookTarget.getPitch());
        }
        return frozen;
    }

    private void scheduleLobbyItemRefresh(Player player, long delayTicks) {
        if (player == null) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                refreshLobbyItem(player);
            }
        }, Math.max(1L, delayTicks));
    }

    private void scheduleLobbyItemRefreshSequence(Player player) {
        scheduleLobbyItemRefresh(player, 2L);
        scheduleLobbyItemRefresh(player, 20L);
        scheduleLobbyItemRefresh(player, 40L);
    }

    private void refreshLobbyItem(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!enabled || !lobbyItemEnabled || isArenaPlayer(player) || isEditingBattlekit(player) || plugin.isInFight(player)) {
            return;
        }

        player.getInventory().setItem(lobbyItemSlot, createLobbyItem());
        player.updateInventory();
    }

    private boolean isNearHub(Player player) {
        Location hub = plugin.resolveHubLocation(player);
        if (hub == null) {
            return true;
        }
        if (hub.getWorld() == null || player.getWorld() == null || !hub.getWorld().equals(player.getWorld())) {
            return false;
        }
        double maxDistanceSquared = (double) lobbyHubRadius * (double) lobbyHubRadius;
        return maxDistanceSquared <= 0.0D || player.getLocation().distanceSquared(hub) <= maxDistanceSquared;
    }

    private ItemStack createLobbyItem() {
        Material fallbackMaterial = parseMaterial(null, "GOLDEN_SWORD", "GOLD_SWORD", "STONE_SWORD");
        ItemStack item = new ItemStack(lobbyItemMaterial != null ? lobbyItemMaterial : fallbackMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(lobbyItemName));
            List<String> lore = new ArrayList<>();
            for (String line : lobbyItemLore) {
                lore.add(color(line));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isLobbyItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        if (item.getType() != lobbyItemMaterial) {
            return false;
        }

        ItemStack expected = createLobbyItem();
        if (item.isSimilar(expected)) {
            return true;
        }

        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() && color(lobbyItemName).equals(meta.getDisplayName());
    }

    private void applyBattlekit(Player player, BattlekitData data) {
        if (player == null || data == null) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(null);
        setOffhandItem(inventory, null);
        setStorageContents(inventory, data.storage);
        inventory.setArmorContents(cloneItemArray(data.armor));
        setOffhandItem(inventory, data.offhand != null ? data.offhand.clone() : null);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setExhaustion(0f);
        player.setExp(0f);
        player.setLevel(0);
        player.setFireTicks(0);
        player.setFallDistance(0f);
        player.updateInventory();
    }

    private BattlekitData loadBattlekit(String name) {
        String normalized = normalizeKey(name);
        if (normalized.isEmpty() || !hasBattlekit(normalized)) {
            return null;
        }

        String basePath = "battlekits." + normalized;
        ItemStack[] storage = loadIndexedItems(basePath + ".contents", 36);
        if (isEmptyItemArray(storage)) {
            storage = deserializeItems(config.getList(basePath + ".contents"), 36);
        }

        ItemStack[] armor = loadIndexedItems(basePath + ".armor", 4);
        if (isEmptyItemArray(armor)) {
            armor = deserializeItems(config.getList(basePath + ".armor"), 4);
        }

        ItemStack offhand = config.getItemStack(basePath + ".offhand");
        return new BattlekitData(storage, armor, offhand != null ? offhand.clone() : null);
    }

    private void storeBattlekit(String name, BattlekitData data) {
        String normalized = normalizeKey(name);
        if (normalized.isEmpty() || data == null) {
            return;
        }

        String basePath = "battlekits." + normalized;
        saveIndexedItems(basePath + ".contents", data.storage);
        saveIndexedItems(basePath + ".armor", data.armor);
        config.set(basePath + ".offhand", data.offhand != null ? data.offhand.clone() : null);
    }

    private boolean hasBattlekit(String name) {
        String normalized = normalizeKey(name);
        return !normalized.isEmpty() && config.isConfigurationSection("battlekits." + normalized);
    }

    private ItemStack[] deserializeItems(List<?> raw, int size) {
        ItemStack[] result = new ItemStack[size];
        if (raw == null) {
            return result;
        }

        for (int i = 0; i < Math.min(raw.size(), size); i++) {
            Object entry = raw.get(i);
            if (entry instanceof ItemStack) {
                result[i] = ((ItemStack) entry).clone();
            }
        }
        return result;
    }

    private List<ItemStack> serializeItems(ItemStack[] items) {
        List<ItemStack> serialized = new ArrayList<>();
        if (items == null) {
            return serialized;
        }

        for (ItemStack item : items) {
            serialized.add(item != null ? item.clone() : null);
        }
        return serialized;
    }

    private ItemStack[] loadIndexedItems(String path, int size) {
        ItemStack[] result = new ItemStack[size];
        for (int i = 0; i < size; i++) {
            Object raw = config.get(path + "." + i);
            if (raw instanceof ItemStack) {
                result[i] = ((ItemStack) raw).clone();
            } else if (raw instanceof Map) {
                result[i] = deserializeLegacyItemMap((Map<?, ?>) raw);
            }
        }
        return result;
    }

    private void saveIndexedItems(String path, ItemStack[] items) {
        config.set(path, null);
        if (items == null) {
            return;
        }

        for (int i = 0; i < items.length; i++) {
            ItemStack item = items[i];
            if (item != null && item.getType() != Material.AIR) {
                config.set(path + "." + i, serializeItemMap(item));
            }
        }
    }

    private boolean isEmptyItemArray(ItemStack[] items) {
        if (items == null) {
            return true;
        }

        for (ItemStack item : items) {
            if (item != null && item.getType() != Material.AIR) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Object> serializeItemMap(ItemStack item) {
        Map<String, Object> serialized = item.serialize();
        return new java.util.LinkedHashMap<>(serialized);
    }

    @SuppressWarnings("unchecked")
    private ItemStack deserializeLegacyItemMap(Map<?, ?> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }

        try {
            return ItemStack.deserialize(new java.util.LinkedHashMap<>((Map<String, Object>) raw));
        } catch (Exception ignored) {
            return null;
        }
    }

    private void recoverBrokenConfig(Exception cause) {
        File brokenFile = new File(configFile.getParentFile(),
                "pixffa.broken-" + System.currentTimeMillis() + ".yml");
        if (configFile.exists() && !configFile.renameTo(brokenFile)) {
            plugin.getLogger().warning("[PixFFA] Failed to back up the broken pixffa.yml file.");
        }

        plugin.getLogger().warning("[PixFFA] pixffa.yml is invalid. The old file was backed up to "
                + brokenFile.getName() + " and a new config will be created.");
        if (cause != null) {
            cause.printStackTrace();
        }

        plugin.saveResource("pixffa.yml", false);
        config = new YamlConfiguration();
        try {
            config.load(configFile);
        } catch (Exception reloadException) {
            plugin.getLogger().severe("[PixFFA] Failed to load the new pixffa.yml file.");
            reloadException.printStackTrace();
        }
    }

    private void saveLocation(String path, Location location) {
        if (location == null || location.getWorld() == null) {
            config.set(path, null);
            return;
        }

        config.set(path + ".world", location.getWorld().getName());
        config.set(path + ".x", location.getX());
        config.set(path + ".y", location.getY());
        config.set(path + ".z", location.getZ());
        config.set(path + ".yaw", location.getYaw());
        config.set(path + ".pitch", location.getPitch());
    }

    private Location loadLocation(String path) {
        if (!config.isConfigurationSection(path)) {
            return null;
        }

        String worldName = config.getString(path + ".world");
        if (worldName == null || worldName.trim().isEmpty()) {
            return null;
        }

        org.bukkit.World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            return null;
        }

        return new Location(
                world,
                config.getDouble(path + ".x"),
                config.getDouble(path + ".y"),
                config.getDouble(path + ".z"),
                (float) config.getDouble(path + ".yaw"),
                (float) config.getDouble(path + ".pitch"));
    }

    private Location getResolvedArenaSpawn() {
        return arenaSpawn != null ? plugin.resolveSafeArenaLocation(arenaSpawn.clone()) : null;
    }

    private void saveConfigFile() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[PixFFA] Failed to save pixffa.yml.");
            e.printStackTrace();
        }
    }

    private String getMessage(String key) {
        return config.getString("messages." + key, "&cMissing message: " + key);
    }

    private void sendMessage(Player player, String message) {
        if (player == null || message == null || message.isEmpty()) {
            return;
        }
        String prefix = config.getString("messages.prefix", "&6[PixFFA] &f");
        player.sendMessage(color(prefix + message));
    }

    private void sendActionBar(Player player, String message) {
        if (player == null || !player.isOnline()) {
            return;
        }
        String colored = color(message);
        try {
            try {
                Object spigot = player.getClass().getMethod("spigot").invoke(player);
                Class<?> chatMessageTypeClass = Class.forName("net.md_5.bungee.api.ChatMessageType");
                Class<?> textComponentClass = Class.forName("net.md_5.bungee.api.chat.TextComponent");
                Class<?> baseComponentArrayClass = Class.forName("[Lnet.md_5.bungee.api.chat.BaseComponent;");

                Object chatMessageType = Enum.valueOf((Class<Enum>) chatMessageTypeClass, "ACTION_BAR");
                Object textComponents = textComponentClass.getMethod("fromLegacyText", String.class).invoke(null, colored);

                spigot.getClass().getMethod("sendMessage", chatMessageTypeClass, baseComponentArrayClass)
                        .invoke(spigot, chatMessageType, textComponents);
                return;
            } catch (Exception ignored) {
            }

            Class<?> iChatBaseComponentClass = getNMSClass("IChatBaseComponent");
            Class<?> packetPlayOutChatClass = getNMSClass("PacketPlayOutChat");
            if (iChatBaseComponentClass == null || packetPlayOutChatClass == null) {
                return;
            }

            Class<?> chatSerializerClass = iChatBaseComponentClass.getDeclaredClasses()[0];
            String json = "{\"text\":\"" + escapeJson(colored) + "\"}";
            Object chatComponent = chatSerializerClass.getMethod("a", String.class).invoke(null, json);

            Object packet;
            try {
                packet = packetPlayOutChatClass.getConstructor(iChatBaseComponentClass, byte.class)
                        .newInstance(chatComponent, (byte) 2);
            } catch (NoSuchMethodException e) {
                packet = packetPlayOutChatClass.getConstructor(iChatBaseComponentClass).newInstance(chatComponent);
            }
            sendPacket(player, packet);
        } catch (Exception ignored) {
        }
    }

    private void sendPacket(Player player, Object packet) {
        if (player == null || packet == null) {
            return;
        }
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object playerConnection = handle.getClass().getField("playerConnection").get(handle);
            playerConnection.getClass().getMethod("sendPacket", getNMSClass("Packet")).invoke(playerConnection, packet);
        } catch (Exception ignored) {
        }
    }

    private Class<?> getNMSClass(String name) {
        try {
            String version = plugin.getServer().getClass().getPackage().getName().split("\\.")[3];
            return Class.forName("net.minecraft.server." + version + "." + name);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String escapeJson(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    private String formatMessage(String raw, Map<String, String> replacements) {
        String result = raw != null ? raw : "";
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message != null ? message : "");
    }

    private boolean shouldSendLocalMessage(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        long last = localMessageCooldowns.getOrDefault(uuid, 0L);
        if (now - last < 1000L) {
            return false;
        }
        localMessageCooldowns.put(uuid, now);
        return true;
    }

    private Material parseMaterial(String configuredName, String... fallbackNames) {
        List<String> candidates = new ArrayList<>();
        if (configuredName != null && !configuredName.trim().isEmpty()) {
            candidates.add(configuredName);
        }
        if (fallbackNames != null) {
            candidates.addAll(Arrays.asList(fallbackNames));
        }

        for (String candidate : candidates) {
            if (candidate == null || candidate.trim().isEmpty()) {
                continue;
            }

            String normalized = candidate.trim().toUpperCase(Locale.ROOT);
            try {
                Material material = Material.valueOf(normalized);
                if (material != null) {
                    return material;
                }
            } catch (IllegalArgumentException ignored) {
            }

            try {
                Material material = Material.matchMaterial(normalized);
                if (material != null) {
                    return material;
                }
            } catch (Exception ignored) {
            }
        }

        return Material.STONE_SWORD;
    }

    private int normalizeSlot(int configuredSlot) {
        int zeroBased = configuredSlot - 1;
        if (zeroBased < 0) {
            zeroBased = 0;
        }
        return Math.min(8, zeroBased);
    }

    private String normalizeKey(String value) {
        return value == null ? "" : ChatColor.stripColor(value).trim().toLowerCase(Locale.ROOT);
    }

    private ItemStack[] cloneItemArray(ItemStack[] items) {
        if (items == null) {
            return null;
        }
        ItemStack[] clone = Arrays.copyOf(items, items.length);
        for (int i = 0; i < clone.length; i++) {
            clone[i] = clone[i] != null ? clone[i].clone() : null;
        }
        return clone;
    }

    private ItemStack[] getStorageContents(PlayerInventory inventory) {
        ItemStack[] contents = new ItemStack[36];
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = inventory.getItem(i);
            contents[i] = item != null ? item.clone() : null;
        }
        return contents;
    }

    private void setStorageContents(PlayerInventory inventory, ItemStack[] items) {
        for (int i = 0; i < 36; i++) {
            ItemStack item = items != null && i < items.length ? items[i] : null;
            inventory.setItem(i, item != null ? item.clone() : null);
        }
    }

    private ItemStack getOffhandItem(PlayerInventory inventory) {
        try {
            Method method = inventory.getClass().getMethod("getItemInOffHand");
            Object raw = method.invoke(inventory);
            return raw instanceof ItemStack ? ((ItemStack) raw).clone() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void setOffhandItem(PlayerInventory inventory, ItemStack item) {
        try {
            Method method = inventory.getClass().getMethod("setItemInOffHand", ItemStack.class);
            method.invoke(inventory, item != null ? item.clone() : null);
        } catch (Exception ignored) {
        }
    }

    private void setKeepInventory(PlayerDeathEvent event, boolean keepInventory) {
        try {
            Method method = event.getClass().getMethod("setKeepInventory", boolean.class);
            method.invoke(event, keepInventory);
        } catch (Exception ignored) {
        }
    }

    private void setKeepLevel(PlayerDeathEvent event, boolean keepLevel) {
        try {
            Method method = event.getClass().getMethod("setKeepLevel", boolean.class);
            method.invoke(event, keepLevel);
        } catch (Exception ignored) {
        }
    }

    private void forceRespawn(Player player) {
        try {
            Object spigot = player.getClass().getMethod("spigot").invoke(player);
            spigot.getClass().getMethod("respawn").invoke(spigot);
        } catch (Exception ignored) {
        }
    }

    private Player resolveDamager(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            return (Player) event.getDamager();
        }
        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player) {
            return (Player) projectile.getShooter();
        }
        return null;
    }

    private void schedulePendingHubTeleport(Player player) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            Location hub = pendingHubTeleports.remove(player.getUniqueId());
            if (hub != null) {
                player.teleport(hub);
            }
            scheduleLobbyItemRefreshSequence(player);
        }, 2L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (pendingHubTeleports.containsKey(player.getUniqueId())) {
            schedulePendingHubTeleport(player);
        } else {
            scheduleLobbyItemRefreshSequence(player);
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (isArenaPlayer(event.getPlayer()) || isEditingBattlekit(event.getPlayer())) {
            return;
        }
        scheduleLobbyItemRefreshSequence(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (isArenaPlayer(player)) {
            PlayerSnapshot snapshot = arenaSnapshots.remove(uuid);
            if (snapshot != null) {
                snapshot.restore(player);
            }
            pendingHubTeleports.put(uuid, plugin.resolveHubLocation(player));
            clearPair(player, false);
            clearArenaRuntime(uuid);
        }

        PlayerSnapshot editSnapshot = battlekitEditSnapshots.remove(uuid);
        if (editSnapshot != null) {
            editSnapshot.restore(player);
        }
        battlekitEditors.remove(uuid);
        battlekitEditorLocations.remove(uuid);
        respawningPlayers.remove(uuid);
        localMessageCooldowns.remove(uuid);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (isArenaPlayer(event.getPlayer())) {
            Location spawn = getResolvedArenaSpawn();
            if (spawn != null) {
                event.setRespawnLocation(spawn);
            }
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> handleArenaRespawn(event.getPlayer(), null), 1L);
            return;
        }

        scheduleLobbyItemRefreshSequence(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (isEditingBattlekit(player)) {
            event.setCancelled(true);
            player.setFallDistance(0f);
            return;
        }

        if (!isArenaPlayer(player)) {
            return;
        }
        if (respawningPlayers.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        Player attacker = null;
        if (event instanceof EntityDamageByEntityEvent byEntityEvent) {
            attacker = resolveDamager(byEntityEvent);
            if (attacker != null) {
                if (isEditingBattlekit(attacker)) {
                    event.setCancelled(true);
                    attacker.setNoDamageTicks(0);
                    player.setNoDamageTicks(0);
                    return;
                }
                handleCombatDamage(byEntityEvent, attacker, player);
            }
        }

        if (event.isCancelled()) {
            return;
        }

        if (event.getCause() == EntityDamageEvent.DamageCause.VOID || player.getLocation().getY() <= voidYThreshold) {
            event.setCancelled(true);
            handleArenaRespawn(player, attacker);
            return;
        }

        if (player.getHealth() - event.getFinalDamage() <= 0.0D) {
            event.setCancelled(true);
            handleArenaRespawn(player, attacker);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (isEditingBattlekit(event.getPlayer())) {
            Location frozenBase = battlekitEditorLocations.get(event.getPlayer().getUniqueId());
            Location to = event.getTo();
            if (isMovementChanged(event.getFrom(), to)) {
                event.setTo(createFrozenLocation(frozenBase != null ? frozenBase : event.getFrom(), to));
                event.getPlayer().setVelocity(new Vector(0, 0, 0));
                event.getPlayer().setFallDistance(0f);
            } else if (frozenBase == null) {
                battlekitEditorLocations.put(event.getPlayer().getUniqueId(), event.getFrom().clone());
            }
            return;
        }

        if (!isArenaPlayer(event.getPlayer()) || respawningPlayers.contains(event.getPlayer().getUniqueId())) {
            return;
        }

        Location to = event.getTo();
        if (to == null) {
            return;
        }

        if (to.getY() <= voidYThreshold) {
            handleArenaRespawn(event.getPlayer(), null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        if (!isArenaPlayer(event.getEntity())) {
            return;
        }

        event.setDeathMessage(null);
        event.getDrops().clear();
        setKeepInventory(event, true);
        setKeepLevel(event, true);

        Player player = event.getEntity();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && isArenaPlayer(player)) {
                forceRespawn(player);
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (isEditingBattlekit(player)) {
            event.setCancelled(true);
            return;
        }
        if (isArenaPlayer(player)) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) {
            item = player.getItemInHand();
        }
        if (!isLobbyItem(item)) {
            return;
        }

        event.setCancelled(true);
        event.setUseInteractedBlock(Result.DENY);
        event.setUseItemInHand(Result.DENY);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && !isArenaPlayer(player)) {
                joinArena(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isEditingBattlekit(event.getPlayer())) {
            event.getItemDrop().remove();
            return;
        }
        if (isArenaPlayer(event.getPlayer())) {
            return;
        }
        if (isLobbyItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(PlayerPickupItemEvent event) {
        if (isEditingBattlekit(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (isEditingBattlekit(player)) {
            return;
        }

        if (isArenaPlayer(player)) {
            return;
        }

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        if (isLobbyItem(current) || isLobbyItem(cursor)) {
            event.setCancelled(true);
        }
    }

    private static final class PlayerSnapshot {
        private final ItemStack[] storage;
        private final ItemStack[] armor;
        private final ItemStack offhand;
        private final int heldSlot;
        private final double health;
        private final int foodLevel;
        private final float saturation;
        private final float exhaustion;
        private final float exp;
        private final int level;
        private final GameMode gameMode;
        private final boolean allowFlight;
        private final boolean flying;
        private final float walkSpeed;
        private final float flySpeed;

        private PlayerSnapshot(ItemStack[] storage, ItemStack[] armor, ItemStack offhand, int heldSlot,
                               double health, int foodLevel, float saturation, float exhaustion,
                               float exp, int level, GameMode gameMode, boolean allowFlight,
                               boolean flying, float walkSpeed, float flySpeed) {
            this.storage = storage;
            this.armor = armor;
            this.offhand = offhand;
            this.heldSlot = heldSlot;
            this.health = health;
            this.foodLevel = foodLevel;
            this.saturation = saturation;
            this.exhaustion = exhaustion;
            this.exp = exp;
            this.level = level;
            this.gameMode = gameMode;
            this.allowFlight = allowFlight;
            this.flying = flying;
            this.walkSpeed = walkSpeed;
            this.flySpeed = flySpeed;
        }

        private static PlayerSnapshot capture(Player player) {
            PlayerInventory inventory = player.getInventory();
            return new PlayerSnapshot(
                    cloneItems(getStorageContentsStatic(inventory)),
                    cloneItems(inventory.getArmorContents()),
                    getOffhandItemStatic(inventory),
                    inventory.getHeldItemSlot(),
                    player.getHealth(),
                    player.getFoodLevel(),
                    player.getSaturation(),
                    player.getExhaustion(),
                    player.getExp(),
                    player.getLevel(),
                    player.getGameMode(),
                    player.getAllowFlight(),
                    player.isFlying(),
                    player.getWalkSpeed(),
                    player.getFlySpeed());
        }

        private void restore(Player player) {
            PlayerInventory inventory = player.getInventory();
            inventory.clear();
            setStorageContentsStatic(inventory, storage);
            inventory.setArmorContents(cloneItems(armor));
            setOffhandItemStatic(inventory, offhand != null ? offhand.clone() : null);
            inventory.setHeldItemSlot(heldSlot);
            player.setGameMode(gameMode);
            player.setAllowFlight(allowFlight);
            player.setFlying(allowFlight && flying);
            player.setWalkSpeed(walkSpeed);
            player.setFlySpeed(flySpeed);
            player.setHealth(Math.min(player.getMaxHealth(), Math.max(1.0D, health)));
            player.setFoodLevel(foodLevel);
            player.setSaturation(saturation);
            player.setExhaustion(exhaustion);
            player.setExp(exp);
            player.setLevel(level);
            player.setFireTicks(0);
            player.setFallDistance(0f);
        }

        private static ItemStack[] cloneItems(ItemStack[] items) {
            if (items == null) {
                return null;
            }
            ItemStack[] clone = Arrays.copyOf(items, items.length);
            for (int i = 0; i < clone.length; i++) {
                clone[i] = clone[i] != null ? clone[i].clone() : null;
            }
            return clone;
        }

        private static ItemStack[] getStorageContentsStatic(PlayerInventory inventory) {
            ItemStack[] contents = new ItemStack[36];
            for (int i = 0; i < contents.length; i++) {
                ItemStack item = inventory.getItem(i);
                contents[i] = item != null ? item.clone() : null;
            }
            return contents;
        }

        private static void setStorageContentsStatic(PlayerInventory inventory, ItemStack[] items) {
            for (int i = 0; i < 36; i++) {
                ItemStack item = items != null && i < items.length ? items[i] : null;
                inventory.setItem(i, item != null ? item.clone() : null);
            }
        }

        private static ItemStack getOffhandItemStatic(PlayerInventory inventory) {
            try {
                Method method = inventory.getClass().getMethod("getItemInOffHand");
                Object raw = method.invoke(inventory);
                return raw instanceof ItemStack ? ((ItemStack) raw).clone() : null;
            } catch (Exception ignored) {
                return null;
            }
        }

        private static void setOffhandItemStatic(PlayerInventory inventory, ItemStack item) {
            try {
                Method method = inventory.getClass().getMethod("setItemInOffHand", ItemStack.class);
                method.invoke(inventory, item != null ? item.clone() : null);
            } catch (Exception ignored) {
            }
        }
    }

    private static final class BattlekitData {
        private final ItemStack[] storage;
        private final ItemStack[] armor;
        private final ItemStack offhand;

        private BattlekitData(ItemStack[] storage, ItemStack[] armor, ItemStack offhand) {
            this.storage = storage != null ? storage : new ItemStack[36];
            this.armor = armor != null ? armor : new ItemStack[4];
            this.offhand = offhand;
        }

        private static BattlekitData capture(Player player) {
            PlayerInventory inventory = player.getInventory();
            return new BattlekitData(
                    PlayerSnapshot.cloneItems(PlayerSnapshot.getStorageContentsStatic(inventory)),
                    PlayerSnapshot.cloneItems(inventory.getArmorContents()),
                    PlayerSnapshot.getOffhandItemStatic(inventory));
        }
    }

    private static final class FfaPair {
        private final UUID first;
        private final UUID second;
        private final UUID initiator;
        private boolean pending;
        private long expiresAt;

        private FfaPair(UUID first, UUID second, UUID initiator, boolean pending) {
            this.first = first;
            this.second = second;
            this.initiator = initiator;
            this.pending = pending;
        }

        private boolean isInitiator(UUID uuid) {
            return initiator != null && initiator.equals(uuid);
        }

        private void refresh(int seconds) {
            expiresAt = System.currentTimeMillis() + (seconds * 1000L);
        }
    }
}
