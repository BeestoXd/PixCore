package com.pixra.pixCore.listeners;

import com.pixra.pixCore.PixCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

public class CombatListener implements Listener {

    private final PixCore plugin;

    public CombatListener(PixCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!plugin.isHooked()) return;
        Player player = event.getEntity();
        UUID uid = player.getUniqueId();

        if (plugin.activeCountdowns.containsKey(uid) ||
                (plugin.deathMessageCooldowns.containsKey(uid) && System.currentTimeMillis() - plugin.deathMessageCooldowns.get(uid) < 1000)) {
            event.setDeathMessage(null);
            event.getDrops().clear();
            return;
        }

        try {
            Object fight = null;
            try { fight = plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), player); } catch (Exception ignored) {}
            boolean inFight = plugin.isInFight(player);
            if (fight != null) inFight = true;

            if (inFight) {
                event.setDeathMessage(null);
                event.getDrops().clear();

                if (plugin.mlgRushBedDeaths.remove(uid)) return;

                recordKill(player);

                if (isPlayerBedBroken(player, fight)) return;

                boolean isVoid = player.getLastDamageCause() != null && player.getLastDamageCause().getCause() == EntityDamageEvent.DamageCause.VOID;

                if (isInstantRespawnKit(player)) {
                    handleDeathSequence(player, fight, isVoid);
                    return;
                }

                if (isDeathTitleDisabled(player)) return;
                handleDeathSequence(player, fight, isVoid);
            }
        } catch (Exception e) {}
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!plugin.isHooked()) return;

        if (event instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent edbe = (EntityDamageByEntityEvent) event;
            Player damager = null;
            if (edbe.getDamager() instanceof Player) damager = (Player) edbe.getDamager();
            else if (edbe.getDamager() instanceof Projectile && ((Projectile) edbe.getDamager()).getShooter() instanceof Player) {
                damager = (Player) ((Projectile) edbe.getDamager()).getShooter();
            }

            if (damager != null) {
                if (plugin.activeCountdowns.containsKey(damager.getUniqueId()) || plugin.frozenPlayers.contains(damager.getUniqueId())) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();

        if (event instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent edbe = (EntityDamageByEntityEvent) event;
            if (edbe.getDamager() instanceof Player) {
                plugin.lastDamager.put(player.getUniqueId(), edbe.getDamager().getUniqueId());
                plugin.lastDamageTime.put(player.getUniqueId(), System.currentTimeMillis());
            } else if (edbe.getDamager() instanceof Projectile && ((Projectile) edbe.getDamager()).getShooter() instanceof Player) {
                plugin.lastDamager.put(player.getUniqueId(), ((Player) ((Projectile) edbe.getDamager()).getShooter()).getUniqueId());
                plugin.lastDamageTime.put(player.getUniqueId(), System.currentTimeMillis());
            }
        }

        if (plugin.activeCountdowns.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION || event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            String kitName = plugin.getKitName(player);
            if (kitName != null) {
                if (kitName.equalsIgnoreCase("fireballfight")) event.setDamage(2.0);
                else if (kitName.equalsIgnoreCase("tntsumo") || kitName.equalsIgnoreCase("fireballfightelo")) event.setDamage(0.0);
            }
        }

        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) return;

        if (player.getHealth() - event.getFinalDamage() <= 0) {
            try {
                if (plugin.isInFight(player)) {

                    if (plugin.mlgRushBedDeaths.remove(player.getUniqueId())) {
                        event.setCancelled(true);
                        return;
                    }

                    recordKill(player);
                    Object fight = null;
                    try { fight = plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), player); } catch (Exception ignored) {}

                    if (isPlayerBedBroken(player, fight)) return;

                    if (isInstantRespawnKit(player)) {
                        event.setCancelled(true);
                        handleDeathSequence(player, fight, false);
                        return;
                    }

                    if (isDeathTitleDisabled(player)) return;

                    event.setCancelled(true);
                    handleDeathSequence(player, fight, false);
                }
            } catch (Exception e) {}
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMoveVoidCheck(PlayerMoveEvent event) {
        if (!plugin.isHooked() || plugin.activeCountdowns.containsKey(event.getPlayer().getUniqueId())) return;

        if (plugin.frozenPlayers.contains(event.getPlayer().getUniqueId())) return;
        if (event.getTo().getBlockY() > plugin.voidYLimit) return;

        Player player = event.getPlayer();
        if (player.isDead() || plugin.titleCooldown.contains(player.getUniqueId())) return;

        try {
            if (plugin.isInFight(player)) {
                recordKill(player);
                Object fight = null;
                try { fight = plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), player); } catch(Exception ignored) {}

                if (isPlayerBedBroken(player, fight)) {
                    player.setHealth(0);
                    return;
                }

                if (isInstantRespawnKit(player)) {
                    handleDeathSequence(player, fight, true);
                    return;
                }

                if (isDeathTitleDisabled(player)) {
                    player.setHealth(0);
                    return;
                }

                handleDeathSequence(player, fight, true);

                player.setHealth(player.getMaxHealth());
                player.setFallDistance(0);
                player.setFoodLevel(20);
                player.setVelocity(new Vector(0, 0, 0));
                player.getInventory().clear();
                player.getInventory().setArmorContents(null);
                player.updateInventory();

                plugin.titleCooldown.add(player.getUniqueId());
                Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.titleCooldown.remove(player.getUniqueId()), 40L);
            }
        } catch (Exception e) {}
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();
        plugin.clearPlayerJoinState(uid);

        Location hub = plugin.resolveHubLocation(player);
        if (hub == null) return;
        plugin.hubOnJoinSpawn.put(uid, hub.clone());
        final Location finalHub = hub.clone();

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) { plugin.hubOnJoinSpawn.remove(uid); return; }
                try {
                    Object api = plugin.getStrikePracticeAPI();
                    if (api != null) {
                        api.getClass().getMethod("clear", Player.class, boolean.class, boolean.class)
                                .invoke(api, player, true, true);
                    }
                } catch (Exception ignored) {}
            }
        }.runTaskLater(plugin, 1L);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;
                player.teleport(finalHub);
            }
        }.runTaskLater(plugin, 2L);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;
                player.teleport(finalHub);
                plugin.hubOnJoinSpawn.remove(uid);
            }
        }.runTaskLater(plugin, 40L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onTeleportDuringHubRedirect(PlayerTeleportEvent event) {
        UUID uid = event.getPlayer().getUniqueId();
        Location hub = plugin.hubOnJoinSpawn.get(uid);
        if (hub == null) return;

        Location dest = event.getTo();
        if (dest != null && dest.getWorld() != null && dest.getWorld().equals(hub.getWorld())
                && dest.distanceSquared(hub) < 1.0) return;
        event.setTo(hub);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLeaveMatchCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.isHooked()) return;
        String cmd = event.getMessage().toLowerCase();
        if (!cmd.equals("/leavematch") && !cmd.equals("/strikepractice:leavematch") && !cmd.equals("/l")) return;

        if (cmd.equals("/l")) {
            cmd = "/leavematch";
            event.setMessage("/leavematch");
        }
        Player player = event.getPlayer();
        if (!plugin.isInFight(player)) {
            return;
        }

        event.setCancelled(true);

        UUID uid = player.getUniqueId();
        plugin.leavingMatchPlayers.add(uid);
        Location hub = null;
        try {
            Object api = plugin.getStrikePracticeAPI();
            if (api != null) {
                hub = (Location) api.getClass().getMethod("getSpawnLocation").invoke(api);
            }
        } catch (Exception ignored) {}
        if (hub == null) hub = player.getWorld().getSpawnLocation();
        plugin.hubOnJoinSpawn.put(uid, hub.clone());
        final Location finalHub = hub.clone();

        new BukkitRunnable() {
            @Override public void run() {
                if (!player.isOnline()) { plugin.hubOnJoinSpawn.remove(uid); return; }
                try {
                    Object api = plugin.getStrikePracticeAPI();
                    Object fight = plugin.getMGetFight().invoke(api, player);
                    if (fight != null && plugin.getMHandleDeath() != null) {
                        plugin.getMHandleDeath().invoke(fight, player);
                    } else if (api != null) {

                        api.getClass().getMethod("clear", Player.class, boolean.class, boolean.class)
                                .invoke(api, player, true, true);
                    }
                } catch (Exception ignored) {}
            }
        }.runTaskLater(plugin, 1L);

        new BukkitRunnable() {
            @Override public void run() {
                plugin.hubOnJoinSpawn.remove(uid);
                if (!player.isOnline()) return;
                player.teleport(finalHub);
            }
        }.runTaskLater(plugin, 40L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();

        if (plugin.isHooked() && plugin.isInFight(player)) {
            plugin.pendingHubOnJoin.add(uid);
        }
        plugin.cleanupPlayer(uid, true);
        if(plugin.blockReplenishManager != null) {
            plugin.blockReplenishManager.clearPlayerData(player);
        }
        if (plugin.bridgeBlockResetManager != null) {
            plugin.bridgeBlockResetManager.clearPlayerData(player);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        plugin.cleanupPlayer(event.getPlayer().getUniqueId(), false);
        plugin.sendTitle(event.getPlayer(), "", "", 0, 0, 0);
        if (plugin.hologramManager != null) {
            final Player respawned = event.getPlayer();
            new BukkitRunnable() {
                @Override public void run() {
                    if (respawned.isOnline())
                        plugin.hologramManager.reHideAllFromPlayer(respawned);
                }
            }.runTaskLater(plugin, 2L);
        }
    }

    private void handleDeathSequence(Player player, Object fight, boolean isVoid) {
        UUID uid = player.getUniqueId();
        if (plugin.activeCountdowns.containsKey(uid)) return;
        if (plugin.deathMessageCooldowns.containsKey(uid) && System.currentTimeMillis() - plugin.deathMessageCooldowns.get(uid) < 1000) return;

        plugin.deathMessageCooldowns.put(uid, System.currentTimeMillis());
        broadcastDeathMessage(player, isVoid, fight);
        playDeathSoundNow(player, fight);

        boolean showTitle = !isDeathTitleDisabled(player);
        int respawnTime = isInstantRespawnKit(player) ? 0 : plugin.getConfig().getInt("settings.default-respawn-time", 3);
        if (respawnTime == 0) showTitle = false;
        final boolean finalShowTitle = showTitle;

        Location arenaSpawn = plugin.arenaSpawnLocations.get(uid);
        if (arenaSpawn == null) arenaSpawn = player.getLocation().clone();

        plugin.respawnManager.startRespawn(player, arenaSpawn);

        BukkitTask task = new BukkitRunnable() {
            int timeLeft = respawnTime;
            @Override
            public void run() {
                if (!player.isOnline() || !plugin.isInFight(player)) {
                    plugin.activeCountdowns.remove(uid);
                    plugin.sendTitle(player, "", "", 0, 0, 0);
                    plugin.respawnManager.forceStop(player);
                    cancel();
                    return;
                }

                if (timeLeft <= 0) {
                    plugin.sendTitle(player, "", "", 0, 0, 0);
                    player.setHealth(player.getMaxHealth());
                    player.setFallDistance(0);
                    player.setFoodLevel(20);
                    plugin.activeCountdowns.remove(uid);
                    plugin.respawnManager.finishRespawn(player);
                    cancel();
                    return;
                }

                if (finalShowTitle) {
                    String subtitle = plugin.getMsg("death.subtitle-countdown").replace("<seconds>", String.valueOf(timeLeft));
                    plugin.sendTitle(player, plugin.getMsg("death.title"), subtitle, 0, 25, 10);
                }

                if (shouldUseRespawnChatCountdown(player)) {
                    String number = plugin.respawnChatNumbers.getOrDefault(timeLeft, String.valueOf(timeLeft));
                    String message = plugin.respawnChatFormat.replace("<number>", ChatColor.translateAlternateColorCodes('&', number));
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
                }
                timeLeft--;
            }
        }.runTaskTimer(plugin, 0L, plugin.respawnCountdownInterval);

        plugin.activeCountdowns.put(uid, task);
    }

    private void recordKill(Player victim) {
        UUID vUid = victim.getUniqueId();

        if (plugin.mlgRushBedDeaths.remove(vUid)) return;
        if (plugin.killCountCooldown.containsKey(vUid) && System.currentTimeMillis() - plugin.killCountCooldown.get(vUid) < 2000) return;
        plugin.killCountCooldown.put(vUid, System.currentTimeMillis());

        Player killer = victim.getKiller();
        if (killer == null) {
            UUID killerUUID = plugin.lastDamager.get(vUid);
            Long time = plugin.lastDamageTime.get(vUid);
            if (killerUUID != null && time != null && (System.currentTimeMillis() - time) < 10000) {
                killer = Bukkit.getPlayer(killerUUID);
            }
        }
        if (killer != null && !killer.getUniqueId().equals(vUid)) {
            plugin.playerMatchKills.put(killer.getUniqueId(), plugin.playerMatchKills.getOrDefault(killer.getUniqueId(), 0) + 1);

            if (plugin.leaderboardManager != null) {
                String kitName = plugin.getKitName(killer);
                if (kitName != null) {
                    plugin.leaderboardManager.addKill(killer.getUniqueId(), killer.getName(), kitName);
                }
            }
        }
    }

    private void playDeathSoundNow(Player victim, Object fight) {
        if (!plugin.soundEnabled || plugin.respawnSound == null) return;

        Player killer = victim.getKiller();
        if (killer == null) {
            UUID killerUUID = plugin.lastDamager.get(victim.getUniqueId());
            Long time = plugin.lastDamageTime.get(victim.getUniqueId());
            if (killerUUID != null && time != null && (System.currentTimeMillis() - time) < 10000) {
                killer = Bukkit.getPlayer(killerUUID);
            }
        }
        if (killer != null && killer.getUniqueId().equals(victim.getUniqueId())) killer = null;

        if (killer != null) {
            try { killer.playSound(killer.getLocation(), plugin.respawnSound, 1.0f, 1.0f); } catch (Exception ignored) {}
        }
    }

    private void broadcastDeathMessage(Player victim, boolean isVoid, Object fight) {
        String msg = "";
        Player killer = victim.getKiller();
        if (killer == null) {
            UUID killerUUID = plugin.lastDamager.get(victim.getUniqueId());
            Long time = plugin.lastDamageTime.get(victim.getUniqueId());
            if (killerUUID != null && time != null && (System.currentTimeMillis() - time) < 10000) killer = Bukkit.getPlayer(killerUUID);
        }
        if (killer != null && killer.getUniqueId().equals(victim.getUniqueId())) killer = null;

        if (fight == null && plugin.isHooked()) {
            try { fight = plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), victim); } catch (Exception ignored) {}
        }

        if (killer != null && plugin.getKillMessageManager() != null) {
            plugin.getKillMessageManager().sendKillMessage(killer, victim, fight);
        }

        String vColor = plugin.getTeamColorCode(victim, fight);
        if (plugin.partyFFAManager != null && fight != null && plugin.partyFFAManager.isPartyFFA(fight)) {
            String ffaCode = plugin.partyFFAManager.getFfaColorCode(victim);
            if (ffaCode != null) vColor = ffaCode;
        }
        if (!vColor.equals("§c") && !vColor.equals("§9")) return;

        String victimName = vColor + victim.getName();
        String kColor = (killer != null) ? plugin.getTeamColorCode(killer, fight) : "";
        if (killer != null && plugin.partyFFAManager != null && fight != null && plugin.partyFFAManager.isPartyFFA(fight)) {
            String ffaCode = plugin.partyFFAManager.getFfaColorCode(killer);
            if (ffaCode != null) kColor = ffaCode;
        }
        String killerName = (killer != null) ? kColor + killer.getName() : "";

        if (isVoid) msg = killer != null ? plugin.getMsg("death-void-kill").replace("<victim>", victimName).replace("<killer>", killerName) : plugin.getMsg("death-void-self").replace("<victim>", victimName);
        else {
            if (killer != null) msg = plugin.getMsg("death-kill").replace("<victim>", victimName).replace("<killer>", killerName);
            else return;
        }

        if (msg == null || msg.isEmpty()) return;

        UUID uuid = victim.getUniqueId();
        long now = System.currentTimeMillis();
        if (plugin.lastBroadcastMessage.containsKey(uuid) && msg.equals(plugin.lastBroadcastMessage.get(uuid)) && (now - plugin.lastBroadcastTime.getOrDefault(uuid, 0L) < 2000)) return;

        plugin.lastBroadcastMessage.put(uuid, msg);
        plugin.lastBroadcastTime.put(uuid, now);

        try {
            if (fight != null) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (plugin.isInFight(p)) {
                        Object pFight = plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), p);
                        if (pFight != null && pFight.equals(fight)) p.sendMessage(msg);
                    }
                }
            }
        } catch (Exception e) {}

        plugin.lastDamager.remove(victim.getUniqueId());
        plugin.lastDamageTime.remove(victim.getUniqueId());
    }

    @SuppressWarnings("unchecked")
    private boolean isPlayerBedBroken(Player player, Object fight) {
        if (fight == null) return false;

        if (plugin.partyFFAManager != null && plugin.partyFFAManager.isPartyFFA(fight)) {
            return plugin.partyFFAManager.isBedBroken(player);
        }

        if (plugin.getMIsBed1Broken() == null || plugin.getMIsBed2Broken() == null) return false;

        if (plugin.partySplitManager != null && plugin.partySplitManager.isPartySplit(fight)) {
            return plugin.partySplitManager.isBedBroken(player, fight);
        }

        if (plugin.partyVsPartyManager != null && plugin.partyVsPartyManager.isPartyVsParty(fight)) {
            return plugin.partyVsPartyManager.isBedBroken(player, fight);
        }

        try {
            if (plugin.getMGetFirstPlayer() != null) {
                Player p1 = (Player) plugin.getMGetFirstPlayer().invoke(fight);
                if (p1 == null) return false;
                if (p1.getUniqueId().equals(player.getUniqueId())) return (boolean) plugin.getMIsBed1Broken().invoke(fight);

                List<?> opponents = (List<?>) plugin.getMGetOpponents().invoke(fight, player);
                boolean p1IsEnemy = false;
                if (opponents != null) {
                    for (Object obj : opponents) {
                        if (obj instanceof String && ((String) obj).equals(p1.getName())) p1IsEnemy = true;
                        else if (obj instanceof Player && ((Player) obj).getUniqueId().equals(p1.getUniqueId())) p1IsEnemy = true;
                    }
                }
                return p1IsEnemy ? (boolean) plugin.getMIsBed2Broken().invoke(fight) : (boolean) plugin.getMIsBed1Broken().invoke(fight);
            }
        } catch (Exception e) {}
        return false;
    }

    private boolean isInstantRespawnKit(Player player) {
        if (!plugin.isHooked() || plugin.instantRespawnKits == null || plugin.instantRespawnKits.isEmpty()) return false;
        String kitName = plugin.getKitName(player);
        if (kitName == null) return false;
        for (String s : plugin.instantRespawnKits) if (s.equalsIgnoreCase(kitName)) return true;
        return false;
    }

    private boolean isDeathTitleDisabled(Player player) {
        if (!plugin.isHooked()) return false;
        try {
            if (plugin.deathDisabledKits != null && !plugin.deathDisabledKits.isEmpty()) {
                String kitName = plugin.getKitName(player);
                if (kitName != null) {
                    for (String s : plugin.deathDisabledKits) if (s.equalsIgnoreCase(kitName)) return true;
                }
            }
            if (plugin.deathDisabledArenas != null && !plugin.deathDisabledArenas.isEmpty() && plugin.getMGetArena() != null) {
                Object fight = plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), player);
                if (fight != null) {
                    Object arena = plugin.getMGetArena().invoke(fight);
                    if (arena != null) {
                        String arenaName = (String) arena.getClass().getMethod("getName").invoke(arena);
                        for (String s : plugin.deathDisabledArenas) if (s.equalsIgnoreCase(arenaName)) return true;
                    }
                }
            }
        } catch (Exception e) {}
        return false;
    }

    private boolean shouldUseRespawnChatCountdown(Player player) {
        if (!plugin.respawnChatCountdownEnabled || !plugin.isHooked()) return false;
        String kitName = plugin.getKitName(player);
        if (kitName == null) return false;
        for (String s : plugin.respawnChatCountdownKits) if (s.equalsIgnoreCase(kitName)) return true;
        return false;
    }
}
