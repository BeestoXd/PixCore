package com.pixra.pixCore.listeners;

import com.pixra.pixCore.PixCore;
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
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class GameplayListener implements Listener {

    private final PixCore plugin;

    private final Set<UUID> selfHitArrows = new HashSet<>();

    public GameplayListener(PixCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        boolean frozen = plugin.frozenPlayers.contains(player.getUniqueId());
        boolean inCountdown = plugin.activeCountdowns.containsKey(player.getUniqueId());
        boolean isRoundTransition = plugin.roundTransitionPlayers.contains(player.getUniqueId());

        if (frozen || inCountdown) {

            org.bukkit.inventory.ItemStack mainHand = player.getInventory().getItemInHand();
            if (event.getItem() != null && event.getItem().getType() == Material.BOW) {
                return;
            }
            if (mainHand != null && mainHand.getType() == Material.BOW) {
                return;
            }

            event.setCancelled(true);
            player.updateInventory();

            if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
                if (event.getItem() != null && event.getItem().getType().isBlock()) {
                    if (frozen) {
                        plugin.sendCooldownMessage(player, isRoundTransition ? "block-place-denied-start-round" : "block-place-denied-start");
                    } else {
                        plugin.sendCooldownMessage(player, "block-place-denied");
                    }
                }
            } else if (event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) {
                if (frozen) {
                    plugin.sendCooldownMessage(player, "block-break-denied-start");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player)) return;
        Player player = (Player) event.getEntity().getShooter();
        boolean restricted = plugin.frozenPlayers.contains(player.getUniqueId())
                || plugin.activeCountdowns.containsKey(player.getUniqueId());

        if (restricted && !(event.getEntity() instanceof Arrow)) {
            event.setCancelled(true);
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
    public void onArrowSelfHitBoost(ProjectileHitEvent event) {
        if (!plugin.bowBoostEnabled) return;
        if (!(event.getEntity() instanceof Arrow)) return;
        Arrow arrow = (Arrow) event.getEntity();
        if (!(arrow.getShooter() instanceof Player)) return;
        Player shooter = (Player) arrow.getShooter();

        boolean isSelfHit = false;
        try {

            java.lang.reflect.Method m = event.getClass().getMethod("getHitEntity");
            org.bukkit.entity.Entity hitEntity = (org.bukkit.entity.Entity) m.invoke(event);
            if (hitEntity instanceof Player) {
                isSelfHit = shooter.getUniqueId().equals(hitEntity.getUniqueId());
            }
        } catch (Exception e) {

            for (org.bukkit.entity.Entity nearby : arrow.getNearbyEntities(0.5, 1.2, 0.5)) {
                if (nearby instanceof Player && nearby.getUniqueId().equals(shooter.getUniqueId())) {
                    isSelfHit = true;
                    break;
                }
            }
        }
        if (!isSelfHit) return;

        if (event instanceof org.bukkit.event.Cancellable) {
            ((org.bukkit.event.Cancellable) event).setCancelled(true);
        }
        arrow.remove();

        UUID arrowId = arrow.getUniqueId();
        selfHitArrows.add(arrowId);
        new BukkitRunnable() {
            @Override public void run() { selfHitArrows.remove(arrowId); }
        }.runTaskLater(plugin, 2L);

        final Vector boost = shooter.getLocation().getDirection().normalize().multiply(plugin.bowBoostHorizontal);
        boost.setY(plugin.bowBoostVertical);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (shooter.isOnline()) shooter.setVelocity(boost);
            }
        }.runTaskLater(plugin, 1L);

        if (plugin.bowCooldownEnabled && !plugin.bowCooldowns.containsKey(shooter.getUniqueId())) {
            boolean frozen = plugin.frozenPlayers.contains(shooter.getUniqueId())
                    || plugin.activeCountdowns.containsKey(shooter.getUniqueId());
            if (frozen && isAutoGiveArrowKit(shooter)) {
                startBowCooldownTimer(shooter);
            }
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
            if (selfHitArrows.contains(arrow.getUniqueId())) return;
            final Vector boost = victim.getLocation().getDirection().normalize().multiply(plugin.bowBoostHorizontal);
            boost.setY(plugin.bowBoostVertical);
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (victim.isOnline()) victim.setVelocity(boost);
                }
            }.runTaskLater(plugin, 1L);

            if (plugin.bowCooldownEnabled && !plugin.bowCooldowns.containsKey(victim.getUniqueId())) {
                UUID uuid = victim.getUniqueId();
                boolean frozen = plugin.frozenPlayers.contains(uuid) || plugin.activeCountdowns.containsKey(uuid);
                if (frozen && isAutoGiveArrowKit(victim)) {
                    startBowCooldownTimer(victim);
                }
            }
            return;
        }

        if (event.isCancelled()) return;
        Vector kb = arrow.getVelocity().normalize().multiply(0.6);
        kb.setY(0.35);
        victim.setVelocity(kb);
    }

    private boolean isAutoGiveArrowKit(Player player) {
        if (!plugin.bowAutoGiveArrowEnabled || plugin.bowAutoGiveArrowKits == null) return false;
        String kit = plugin.getKitName(player);

        if (kit == null) {

            return true;
        }
        for (String k : plugin.bowAutoGiveArrowKits) {
            if (k.equalsIgnoreCase(kit)) return true;
        }
        return false;
    }

    private void startBowCooldownTimer(Player player) {
        UUID uuid = player.getUniqueId();
        if (plugin.bowCooldowns.containsKey(uuid)) return;
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

                    if (player.isOnline() && isAutoGiveArrowKit(player)
                            && !player.getInventory().contains(Material.ARROW)) {
                        player.getInventory().addItem(new ItemStack(Material.ARROW, 1));
                        player.updateInventory();
                    }
                    cancel();
                    return;
                }
                player.setExp(Math.max(0, Math.min(1, (float) remaining / duration)));
                player.setLevel((int) Math.ceil(remaining / 1000.0));
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (!plugin.bowCooldownEnabled || !(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();

        String kitName = plugin.getKitName(player);
        UUID uuid = player.getUniqueId();
        boolean frozen = plugin.frozenPlayers.contains(uuid) || plugin.activeCountdowns.containsKey(uuid);

        boolean allowed = false;
        if (kitName != null && plugin.bowCooldownKits != null) {
            for (String k : plugin.bowCooldownKits) if (k.equalsIgnoreCase(kitName)) { allowed = true; break; }
        }

        if (!allowed && frozen && isAutoGiveArrowKit(player)) {
            allowed = true;
        }
        if (!allowed) return;

        if (plugin.bowCooldowns.containsKey(uuid)) {
            if (plugin.bowCooldowns.get(uuid) - System.currentTimeMillis() > 0) {
                event.setCancelled(true);
                String msg = plugin.getMsg("bow-cooldown");
                if (!msg.isEmpty()) player.sendMessage(msg);

                if (isAutoGiveArrowKit(player)) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (player.isOnline() && !player.getInventory().contains(Material.ARROW)) {
                                player.getInventory().addItem(new ItemStack(Material.ARROW, 1));
                                player.updateInventory();
                            }
                        }
                    }.runTaskLater(plugin, 1L);
                }
                return;
            }
        }

        startBowCooldownTimer(player);
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

        if (plugin.frozenPlayers.contains(player.getUniqueId())) {
            boolean isRoundTransition = plugin.roundTransitionPlayers.contains(player.getUniqueId());
            event.setCancelled(true);
            plugin.sendCooldownMessage(player, isRoundTransition ? "block-place-denied-start-round" : "block-place-denied-start");
            return;
        }
        if (plugin.activeCountdowns.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            plugin.sendCooldownMessage(player, "block-place-denied");
            return;
        }

        if (!plugin.isHooked() || !plugin.buildRestrictionsEnabled || !plugin.isInFight(player)) return;

        if (plugin.buildRestrictionsEnabled && plugin.arenaBoundaryManager != null) {
            if (plugin.arenaBoundaryManager.checkBuildHeight(player, event.getBlock())) {
                event.setCancelled(true);
                plugin.sendCooldownMessage(player, "build-limit-reached");
                return;
            }
            if (plugin.arenaBoundaryManager.checkArenaBorder(player, event.getBlock())) {
                event.setCancelled(true);
                plugin.sendCooldownMessage(player, "build-outside-arena");
            }
        }
    }

}
