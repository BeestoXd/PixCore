package com.pixra.pixcore.feature.combat;

import com.pixra.pixcore.PixCore;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PearlCooldownManager implements Listener {

    private static final long MESSAGE_THROTTLE_MS = 600L;

    private final PixCore plugin;

    private File configFile;
    private FileConfiguration config;

    private final Map<UUID, Long> pearlCooldowns = new HashMap<>();
    private final Map<UUID, BukkitTask> xpBarTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> restoreTasks = new HashMap<>();
    private final Map<UUID, Long> lastMessageTime = new HashMap<>();
    private final Map<UUID, Integer> savedHandSlots = new HashMap<>();
    private final Map<UUID, Integer> savedPearlAmounts = new HashMap<>();
    private final Map<UUID, Integer> pendingHandSlots = new HashMap<>();
    private final Map<UUID, Integer> pendingPearlAmounts = new HashMap<>();

    private Method setCooldownMethod;
    private boolean setCooldownChecked;

    public PearlCooldownManager(PixCore plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "pearlcooldown.yml");
        if (!configFile.exists()) {
            plugin.saveResource("pearlcooldown.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        plugin.getLogger().info("pearlcooldown.yml loaded for PearlCooldownManager.");
    }

    public void reload() {
        loadConfig();
        if (!isEnabled()) {
            clearAll();
        }
    }

    public void clearAll() {
        Set<UUID> trackedPlayers = new HashSet<>();
        trackedPlayers.addAll(pearlCooldowns.keySet());
        trackedPlayers.addAll(xpBarTasks.keySet());
        trackedPlayers.addAll(restoreTasks.keySet());
        trackedPlayers.addAll(lastMessageTime.keySet());
        trackedPlayers.addAll(savedHandSlots.keySet());
        trackedPlayers.addAll(savedPearlAmounts.keySet());
        trackedPlayers.addAll(pendingHandSlots.keySet());
        trackedPlayers.addAll(pendingPearlAmounts.keySet());

        for (UUID uuid : trackedPlayers) {
            clearPlayer(uuid);
        }
    }

    public void clearPlayer(UUID uuid) {
        if (uuid == null) {
            return;
        }

        boolean hadState = pearlCooldowns.containsKey(uuid)
                || xpBarTasks.containsKey(uuid)
                || restoreTasks.containsKey(uuid)
                || lastMessageTime.containsKey(uuid)
                || savedHandSlots.containsKey(uuid)
                || savedPearlAmounts.containsKey(uuid)
                || pendingHandSlots.containsKey(uuid)
                || pendingPearlAmounts.containsKey(uuid);

        pearlCooldowns.remove(uuid);
        lastMessageTime.remove(uuid);
        savedHandSlots.remove(uuid);
        savedPearlAmounts.remove(uuid);
        pendingHandSlots.remove(uuid);
        pendingPearlAmounts.remove(uuid);

        BukkitTask task = xpBarTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }

        BukkitTask restoreTask = restoreTasks.remove(uuid);
        if (restoreTask != null) {
            restoreTask.cancel();
        }

        Player player = plugin.getServer().getPlayer(uuid);
        if (hadState && player != null && player.isOnline()) {
            clearNativeCooldown(player);
            player.setExp(0.0f);
            player.setLevel(0);
        }
    }

    private boolean isEnabled() {
        return config.getBoolean("pearl-cooldown.enabled", true);
    }

    private String normalizeKitKey(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return ChatColor.stripColor(value)
                .replaceAll("[^a-zA-Z0-9]", "")
                .toLowerCase(Locale.ROOT);
    }

    private int getCooldownSeconds(Player player) {
        if (player == null || !isEnabled()) {
            return 0;
        }

        int defaultSeconds = Math.max(0, config.getInt("pearl-cooldown.default", 15));
        String kitName = plugin.getKitName(player);
        ConfigurationSection section = config.getConfigurationSection("pearl-cooldown.kits");
        if (section == null || section.getKeys(false).isEmpty()) {
            return defaultSeconds;
        }

        Integer fallback = null;
        if (section.contains("all")) {
            fallback = Math.max(0, section.getInt("all", defaultSeconds));
        } else if (section.contains("*")) {
            fallback = Math.max(0, section.getInt("*", defaultSeconds));
        }

        if (kitName == null || kitName.isEmpty()) {
            return fallback != null ? fallback : defaultSeconds;
        }

        for (String key : section.getKeys(false)) {
            if (key.equalsIgnoreCase("all") || key.equals("*")) {
                continue;
            }

            if (key.equalsIgnoreCase(kitName)) {
                return Math.max(0, section.getInt(key, fallback != null ? fallback : defaultSeconds));
            }
        }

        String normalizedKit = normalizeKitKey(kitName);
        for (String key : section.getKeys(false)) {
            if (key.equalsIgnoreCase("all") || key.equals("*")) {
                continue;
            }

            String normalizedKey = normalizeKitKey(key);
            if (normalizedKey.isEmpty()) {
                continue;
            }

            if (normalizedKit.equals(normalizedKey)) {
                return Math.max(0, section.getInt(key, fallback != null ? fallback : defaultSeconds));
            }
        }

        return fallback != null ? fallback : defaultSeconds;
    }

    private long getRemainingMillis(UUID uuid) {
        Long expiresAt = pearlCooldowns.get(uuid);
        if (expiresAt == null) {
            return 0L;
        }

        long remaining = expiresAt - System.currentTimeMillis();
        if (remaining > 0L) {
            return remaining;
        }

        pearlCooldowns.remove(uuid);
        BukkitTask task = xpBarTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }

        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null && player.isOnline()) {
            clearNativeCooldown(player);
            player.setExp(0.0f);
            player.setLevel(0);
        }
        clearSavedPearlState(uuid);

        return 0L;
    }

    private void sendCooldownMessage(Player player, long remainingMs) {
        if (player == null || !player.isOnline()) {
            return;
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (now - lastMessageTime.getOrDefault(uuid, 0L) < MESSAGE_THROTTLE_MS) {
            return;
        }

        lastMessageTime.put(uuid, now);

        String raw = config.getString(
                "pearl-cooldown.message",
                "&cPearl is on cooldown! &e<seconds>s remaining"
        );
        String message = ChatColor.translateAlternateColorCodes(
                '&',
                raw.replace("<seconds>", String.valueOf((int) Math.ceil(remainingMs / 1000.0D)))
        );
        player.sendMessage(message);
    }

    private void storePendingLaunchState(Player player, ItemStack item) {
        if (player == null || item == null || item.getType() != Material.ENDER_PEARL) {
            return;
        }

        UUID uuid = player.getUniqueId();
        int heldSlot = player.getInventory().getHeldItemSlot();
        int expectedAmount = Math.max(0, item.getAmount() - 1);

        pendingHandSlots.put(uuid, heldSlot);
        pendingPearlAmounts.put(uuid, expectedAmount);
    }

    private void clearSavedPearlState(UUID uuid) {
        savedHandSlots.remove(uuid);
        savedPearlAmounts.remove(uuid);
    }

    private void clearPendingPearlState(UUID uuid) {
        pendingHandSlots.remove(uuid);
        pendingPearlAmounts.remove(uuid);
    }

    private void applyPendingLaunchState(UUID uuid, Player player) {
        Integer pendingSlot = pendingHandSlots.remove(uuid);
        Integer pendingAmount = pendingPearlAmounts.remove(uuid);

        if (pendingSlot == null || pendingAmount == null) {
            if (player == null || !player.isOnline()) {
                clearSavedPearlState(uuid);
                return;
            }

            int heldSlot = player.getInventory().getHeldItemSlot();
            ItemStack current = player.getInventory().getItem(heldSlot);
            savedHandSlots.put(uuid, heldSlot);
            if (current == null || current.getType() != Material.ENDER_PEARL) {
                savedPearlAmounts.put(uuid, 0);
            } else {
                savedPearlAmounts.put(uuid, Math.max(0, current.getAmount()));
            }
            return;
        }

        savedHandSlots.put(uuid, pendingSlot);
        savedPearlAmounts.put(uuid, pendingAmount);
    }

    private void restoreSavedPearl(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        UUID uuid = player.getUniqueId();
        Integer savedSlot = savedHandSlots.get(uuid);
        Integer savedAmount = savedPearlAmounts.get(uuid);
        if (savedSlot == null || savedAmount == null) {
            return;
        }

        ItemStack current = player.getInventory().getItem(savedSlot);
        if (savedAmount <= 0) {
            if (current != null && current.getType() == Material.ENDER_PEARL) {
                player.getInventory().setItem(savedSlot, null);
            }
            return;
        }

        if (current == null || current.getType() == Material.AIR) {
            player.getInventory().setItem(savedSlot, new ItemStack(Material.ENDER_PEARL, savedAmount));
            return;
        }

        if (current.getType() != Material.ENDER_PEARL) {
            player.getInventory().setItem(savedSlot, new ItemStack(Material.ENDER_PEARL, savedAmount));
            return;
        }

        if (current.getAmount() < savedAmount) {
            current.setAmount(savedAmount);
        }
    }

    private void syncCancelledUse(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        UUID uuid = player.getUniqueId();
        BukkitTask oldTask = restoreTasks.remove(uuid);
        if (oldTask != null) {
            oldTask.cancel();
        }

        player.updateInventory();
        BukkitTask task = new BukkitRunnable() {
            private int attempts;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    restoreTasks.remove(uuid);
                    cancel();
                    return;
                }

                restoreSavedPearl(player);
                player.updateInventory();
                attempts++;
                if (attempts >= 5) {
                    restoreTasks.remove(uuid);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        restoreTasks.put(uuid, task);
    }

    private void startCooldown(Player player, int seconds) {
        if (player == null || seconds <= 0) {
            return;
        }

        UUID uuid = player.getUniqueId();
        long duration = seconds * 1000L;

        BukkitTask oldTask = xpBarTasks.remove(uuid);
        if (oldTask != null) {
            oldTask.cancel();
        }

        pearlCooldowns.put(uuid, System.currentTimeMillis() + duration);
        clearNativeCooldown(player);
        player.setExp(1.0f);
        player.setLevel(seconds);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    pearlCooldowns.remove(uuid);
                    xpBarTasks.remove(uuid);
                    clearSavedPearlState(uuid);
                    clearPendingPearlState(uuid);
                    cancel();
                    return;
                }

                long remaining = pearlCooldowns.getOrDefault(uuid, 0L) - System.currentTimeMillis();
                if (remaining <= 0L) {
                    pearlCooldowns.remove(uuid);
                    xpBarTasks.remove(uuid);
                    clearNativeCooldown(player);
                    player.setExp(0.0f);
                    player.setLevel(0);
                    clearSavedPearlState(uuid);
                    clearPendingPearlState(uuid);
                    cancel();
                    return;
                }

                player.setExp(Math.max(0.0f, Math.min(1.0f, (float) remaining / (float) duration)));
                player.setLevel((int) Math.ceil(remaining / 1000.0D));
            }
        }.runTaskTimer(plugin, 0L, 2L);

        xpBarTasks.put(uuid, task);
    }

    private void clearNativeCooldown(Player player) {
        if (player == null) {
            return;
        }

        Method method = resolveSetCooldownMethod();
        if (method == null) {
            return;
        }

        try {
            method.invoke(player, Material.ENDER_PEARL, 0);
        } catch (Exception ignored) {
        }
    }

    private Method resolveSetCooldownMethod() {
        if (setCooldownChecked) {
            return setCooldownMethod;
        }

        setCooldownChecked = true;
        try {
            setCooldownMethod = Player.class.getMethod("setCooldown", Material.class, int.class);
        } catch (Exception ignored) {
            setCooldownMethod = null;
        }
        return setCooldownMethod;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPearlInteract(PlayerInteractEvent event) {
        if (!isEnabled()) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.ENDER_PEARL) {
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        int cooldownSeconds = getCooldownSeconds(player);

        if (cooldownSeconds <= 0) {
            clearPlayer(uuid);
            return;
        }

        long remaining = getRemainingMillis(uuid);
        if (remaining <= 0L) {
            storePendingLaunchState(player, item);
            clearNativeCooldown(player);
            return;
        }

        event.setCancelled(true);
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        clearNativeCooldown(player);
        sendCooldownMessage(player, remaining);
        syncCancelledUse(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPearlLaunch(ProjectileLaunchEvent event) {
        if (!isEnabled()) {
            return;
        }

        if (!(event.getEntity() instanceof EnderPearl)) {
            return;
        }
        if (!(event.getEntity().getShooter() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity().getShooter();
        UUID uuid = player.getUniqueId();

        int cooldownSeconds = getCooldownSeconds(player);
        if (cooldownSeconds <= 0) {
            clearPlayer(uuid);
            return;
        }

        long remaining = getRemainingMillis(uuid);
        if (remaining > 0L) {
            event.setCancelled(true);
            clearNativeCooldown(player);
            sendCooldownMessage(player, remaining);
            syncCancelledUse(player);
            return;
        }

        applyPendingLaunchState(uuid, player);
        BukkitTask restoreTask = restoreTasks.remove(uuid);
        if (restoreTask != null) {
            restoreTask.cancel();
        }
        startCooldown(player, cooldownSeconds);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        clearPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        clearPlayer(event.getPlayer().getUniqueId());
    }
}
