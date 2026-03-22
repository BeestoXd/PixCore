package com.pixra.pixCore.managers;

import com.pixra.pixCore.PixCore;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FireballCooldownManager implements Listener {

    private final PixCore plugin;
    private File configFile;
    private FileConfiguration config;

    private static final Material FIRE_CHARGE_MATERIAL;
    static {
        Material m = Material.getMaterial("FIRE_CHARGE");
        if (m == null) m = Material.getMaterial("FIREBALL");
        FIRE_CHARGE_MATERIAL = m;
    }

    private final Map<UUID, Long>       fireballCooldowns = new HashMap<>();
    private final Map<UUID, BukkitTask> xpBarTasks        = new HashMap<>();

    private final Set<UUID>             allowedThrows     = new HashSet<>();

    private final Map<UUID, Integer>    savedHandSlots    = new HashMap<>();

    private final Map<UUID, Long>       lastMsgTime       = new HashMap<>();

    public FireballCooldownManager(PixCore plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "fireballcooldown.yml");
        if (!configFile.exists()) {
            plugin.saveResource("fireballcooldown.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        plugin.getLogger().info("fireballcooldown.yml loaded for FireballCooldownManager.");
    }

    public void reload() {
        loadConfig();
    }

    public void clearPlayer(UUID uuid) {
        fireballCooldowns.remove(uuid);
        allowedThrows.remove(uuid);
        savedHandSlots.remove(uuid);
        lastMsgTime.remove(uuid);
        BukkitTask task = xpBarTasks.remove(uuid);
        if (task != null) task.cancel();
        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null && player.isOnline()) {
            player.setExp(0);
            player.setLevel(0);
        }
    }

    private boolean isApplicableKit(Player player) {
        String kitName = plugin.getKitName(player);
        if (kitName == null) return false;
        for (String k : config.getStringList("fireball-cooldown.kits")) {
            if (k.equalsIgnoreCase(kitName)) return true;
        }
        return false;
    }

    private void sendCooldownMessage(Player player, long remainingMs) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (now - lastMsgTime.getOrDefault(uuid, 0L) < 600) return;
        lastMsgTime.put(uuid, now);
        String rawMsg = config.getString("fireball-cooldown.message",
                "&cFireball is on cooldown! &e<seconds>s remaining.");
        String msg = ChatColor.translateAlternateColorCodes('&',
                rawMsg.replace("<seconds>", String.valueOf((int) Math.ceil(remainingMs / 1000.0))));
        player.sendMessage(msg);
    }

    private void startCooldown(Player player) {
        int seconds = config.getInt("fireball-cooldown.seconds", 3);
        long duration = seconds * 1000L;
        UUID uuid = player.getUniqueId();

        BukkitTask old = xpBarTasks.remove(uuid);
        if (old != null) old.cancel();

        fireballCooldowns.put(uuid, System.currentTimeMillis() + duration);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !fireballCooldowns.containsKey(uuid)) {
                    cancel();
                    xpBarTasks.remove(uuid);
                    if (player.isOnline()) { player.setExp(0); player.setLevel(0); }
                    return;
                }
                long remaining = fireballCooldowns.get(uuid) - System.currentTimeMillis();
                if (remaining <= 0) {
                    fireballCooldowns.remove(uuid);
                    xpBarTasks.remove(uuid);
                    player.setExp(0);
                    player.setLevel(0);
                    cancel();
                    return;
                }
                player.setExp(Math.max(0f, Math.min(1f, (float) remaining / duration)));
                player.setLevel((int) Math.ceil(remaining / 1000.0));
            }
        }.runTaskTimer(plugin, 0L, 2L);

        xpBarTasks.put(uuid, task);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onFireballInteract(PlayerInteractEvent event) {
        if (!config.getBoolean("fireball-cooldown.enabled", false)) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (plugin.frozenPlayers.contains(uuid)) return;
        if (plugin.activeCountdowns.containsKey(uuid)) return;

        ItemStack item = event.getItem();
        if (item == null) return;
        if (FIRE_CHARGE_MATERIAL != null && item.getType() != FIRE_CHARGE_MATERIAL) return;

        if (!isApplicableKit(player)) return;

        long now = System.currentTimeMillis();
        long remaining = fireballCooldowns.getOrDefault(uuid, 0L) - now;

        if (remaining > 0) {

            savedHandSlots.put(uuid, player.getInventory().getHeldItemSlot());
            event.setCancelled(true);
            event.setUseItemInHand(Event.Result.DENY);
            new BukkitRunnable() {
                @Override public void run() {
                    if (player.isOnline()) player.updateInventory();
                }
            }.runTask(plugin);
            sendCooldownMessage(player, remaining);
        } else {

            allowedThrows.add(uuid);
            startCooldown(player);

            new BukkitRunnable() {
                @Override public void run() { allowedThrows.remove(uuid); }
            }.runTask(plugin);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFireballLaunch(ProjectileLaunchEvent event) {
        if (!config.getBoolean("fireball-cooldown.enabled", false)) return;
        if (!(event.getEntity() instanceof Fireball)) return;
        if (!(event.getEntity().getShooter() instanceof Player)) return;

        Player player = (Player) event.getEntity().getShooter();
        UUID uuid = player.getUniqueId();

        if (allowedThrows.remove(uuid)) {
            savedHandSlots.remove(uuid);
            return;
        }

        if (!isApplicableKit(player)) return;

        boolean frozen = plugin.frozenPlayers.contains(uuid) || plugin.activeCountdowns.containsKey(uuid);
        long remaining = fireballCooldowns.getOrDefault(uuid, 0L) - System.currentTimeMillis();
        boolean onCooldown = remaining > 0;

        if (!frozen && !onCooldown) return;

        event.setCancelled(true);

        Integer savedSlot = savedHandSlots.remove(uuid);
        final int targetSlot = (savedSlot != null) ? savedSlot : player.getInventory().getHeldItemSlot();

        new BukkitRunnable() {
            @Override public void run() {
                if (!player.isOnline() || FIRE_CHARGE_MATERIAL == null) return;
                ItemStack current = player.getInventory().getItem(targetSlot);
                if (current == null || current.getType() == Material.AIR) {

                    player.getInventory().setItem(targetSlot, new ItemStack(FIRE_CHARGE_MATERIAL, 1));
                } else if (current.getType() == FIRE_CHARGE_MATERIAL) {

                    current.setAmount(current.getAmount() + 1);
                } else {

                    player.getInventory().addItem(new ItemStack(FIRE_CHARGE_MATERIAL, 1));
                }
                player.updateInventory();
            }
        }.runTask(plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        clearPlayer(event.getPlayer().getUniqueId());
    }
}
