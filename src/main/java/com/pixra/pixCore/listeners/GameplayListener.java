package com.pixra.pixCore.listeners;

import com.pixra.pixCore.PixCore;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.util.UUID;

public class GameplayListener implements Listener {

    private final PixCore plugin;

    public GameplayListener(PixCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (plugin.frozenPlayers.contains(player.getUniqueId()) || plugin.activeCountdowns.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            player.updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player) {
            Player player = (Player) event.getEntity().getShooter();
            if (plugin.frozenPlayers.contains(player.getUniqueId()) || plugin.activeCountdowns.containsKey(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (plugin.frozenPlayers.contains(player.getUniqueId()) || plugin.activeCountdowns.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBowBoost(EntityDamageByEntityEvent event) {
        if (!plugin.bowBoostEnabled || !(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Arrow)) return;
        Player victim = (Player) event.getEntity();
        Arrow arrow = (Arrow) event.getDamager();
        if (!(arrow.getShooter() instanceof Player)) return;
        Player shooter = (Player) arrow.getShooter();

        if (shooter.getUniqueId().equals(victim.getUniqueId())) {
            event.setCancelled(true);
            Vector boost = victim.getLocation().getDirection().normalize().multiply(plugin.bowBoostHorizontal);
            boost.setY(plugin.bowBoostVertical);
            victim.setVelocity(boost);
            return;
        }

        if (event.isCancelled()) return;
        Vector kb = arrow.getVelocity().normalize().multiply(0.6);
        kb.setY(0.35);
        victim.setVelocity(kb);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (!plugin.bowCooldownEnabled || !(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();

        String kitName = plugin.getKitName(player);
        boolean allowed = false;
        if(kitName != null && plugin.bowCooldownKits != null) {
            for(String k : plugin.bowCooldownKits) if(k.equalsIgnoreCase(kitName)) { allowed = true; break;}
        }
        if(!allowed) return;

        UUID uuid = player.getUniqueId();
        if (plugin.bowCooldowns.containsKey(uuid)) {
            if (plugin.bowCooldowns.get(uuid) - System.currentTimeMillis() > 0) {
                event.setCancelled(true);
                String msg = plugin.getMsg("bow-cooldown");
                if (!msg.isEmpty()) player.sendMessage(msg);
                return;
            }
        }

        long duration = plugin.bowCooldownSeconds * 1000L;
        plugin.bowCooldowns.put(uuid, System.currentTimeMillis() + duration);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !plugin.bowCooldowns.containsKey(uuid)) {
                    cancel();
                    if (player.isOnline()) { player.setExp(0); player.setLevel(0); }
                    return;
                }
                long remaining = plugin.bowCooldowns.get(uuid) - System.currentTimeMillis();
                if (remaining <= 0) {
                    plugin.bowCooldowns.remove(uuid);
                    player.setExp(0); player.setLevel(0);
                    cancel();
                    return;
                }
                player.setExp(Math.max(0, Math.min(1, (float) remaining / duration)));
                player.setLevel((int) Math.ceil(remaining / 1000.0));
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTntPlace(BlockPlaceEvent event) {
        if (!plugin.isHooked()) return;
        if (event.getBlock().getType() != Material.TNT) return;
        Player player = event.getPlayer();
        String kitName = plugin.getKitName(player);
        if (kitName != null && plugin.tntTickConfig != null && plugin.tntTickConfig.contains("kits." + kitName)) {
            int ticks = Math.max(0, plugin.tntTickConfig.getInt("kits." + kitName));
            event.getBlock().setType(Material.AIR);
            TNTPrimed tnt = (TNTPrimed) event.getBlock().getWorld().spawnEntity(event.getBlock().getLocation().add(0.5, 0, 0.5), plugin.tntEntityType);
            tnt.setFuseTicks(ticks);
            if (plugin.hasSetSource) tnt.setSource(player);
            else trySetSourceReflection(tnt, player);
        }
    }

    private void trySetSourceReflection(TNTPrimed tnt, Player source) {
        try {
            Object craftTnt = tnt.getClass().getMethod("getHandle").invoke(tnt);
            Object craftEntity = source.getClass().getMethod("getHandle").invoke(source);
            Field sourceField = craftTnt.getClass().getDeclaredField("source");
            sourceField.setAccessible(true);
            sourceField.set(craftTnt, craftEntity);
        } catch (Exception e) {}
    }

    @EventHandler
    public void onTntSpawn(EntitySpawnEvent event) {
        if (!plugin.isHooked() || !(event.getEntity() instanceof TNTPrimed)) return;
        TNTPrimed tnt = (TNTPrimed) event.getEntity();
        if (tnt.getSource() instanceof Player) {
            String kitName = plugin.getKitName((Player) tnt.getSource());
            if (kitName != null && plugin.tntTickConfig != null && plugin.tntTickConfig.contains("kits." + kitName)) {
                tnt.setFuseTicks(Math.max(0, plugin.tntTickConfig.getInt("kits." + kitName)));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onBlockPlaceBuildLimit(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (plugin.frozenPlayers.contains(player.getUniqueId()) || plugin.activeCountdowns.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            plugin.sendCooldownMessage(player, "block-place-denied");
            return;
        }

        if (!plugin.isHooked() || !plugin.buildRestrictionsEnabled || !plugin.isInFight(player)) return;

        String kitName = plugin.getKitName(player);
        boolean allowed = false;
        if(kitName != null && plugin.buildRestrictionKits != null) {
            for(String k : plugin.buildRestrictionKits) if(k.equalsIgnoreCase(kitName)) { allowed = true; break;}
        }
        if(!allowed) return;

        if (event.getBlock().getY() >= plugin.maxBuildY) {
            event.setCancelled(true);
            plugin.sendCooldownMessage(player, "build-limit-reached");
            return;
        }

        if (plugin.checkArenaBorders && plugin.arenaReflectionLoaded && plugin.arenaBoundaryManager != null) {
            if (plugin.arenaBoundaryManager.checkArenaBorder(player, event.getBlock())) {
                event.setCancelled(true);
                plugin.sendCooldownMessage(player, "build-outside-arena");
            }
        }
    }
}