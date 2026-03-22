package com.pixra.pixCore.party;

import com.pixra.pixCore.PixCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class PartySplitManager implements Listener {

    private final PixCore plugin;

    private Class<?> partySplitClass;
    private Method   getTeam1Method;
    private Method   getTeam2Method;
    private Method   getAlive1Method;
    private Method   getAlive2Method;

    private final Map<Object, int[]> bridgeScores = new HashMap<>();
    private final Map<java.util.UUID, Long> portalCooldown = new HashMap<>();
    private final java.util.Set<Object> bridgeEndedFights = new java.util.HashSet<>();
    private final Map<Object, Player> bridgeWinner = new HashMap<>();

    public boolean isBridgeEndHandled(Object fight) { return bridgeEndedFights.contains(fight); }
    public int[] getBridgeScores(Object fight)       { return bridgeScores.getOrDefault(fight, new int[]{0, 0}); }
    public Player getBridgeWinner(Object fight)      { return bridgeWinner.get(fight); }

    public PartySplitManager(PixCore plugin) {
        this.plugin = plugin;
        try {
            partySplitClass  = Class.forName("ga.strikepractice.fights.party.partyfights.PartySplit");
            getTeam1Method   = partySplitClass.getMethod("getTeam1");
            getTeam2Method   = partySplitClass.getMethod("getTeam2");
            getAlive1Method  = partySplitClass.getMethod("getAlive1");
            getAlive2Method  = partySplitClass.getMethod("getAlive2");
        } catch (Exception e) {
            plugin.getLogger().warning("[PartySplitManager] Could not hook PartySplit class — features disabled.");
        }
        registerKitSelectEvent();
    }

    @SuppressWarnings("unchecked")
    private void registerKitSelectEvent() {
        try {
            Class<? extends Event> kitSelectClass =
                    (Class<? extends Event>) Class.forName("ga.strikepractice.events.KitSelectEvent");
            Bukkit.getPluginManager().registerEvent(
                    kitSelectClass, this, EventPriority.MONITOR,
                    (listener, event) -> handleKitSelect(event), plugin);
        } catch (Exception e) {
            plugin.getLogger().warning("[PartySplitManager] Could not register KitSelectEvent — kit auto-apply disabled.");
        }
    }

    private void handleKitSelect(Event event) {
        if (partySplitClass == null) return;
        try {
            Player player = (Player) event.getClass().getMethod("getPlayer").invoke(event);
            if (player == null || !player.isOnline()) return;
            if (!plugin.isHooked() || !plugin.isInFight(player)) return;

            Object fight = plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), player);
            if (fight == null || !isPartySplit(fight)) return;

        } catch (Exception ignored) {}
    }

    public boolean isPartySplit(Object fight) {
        return partySplitClass != null && partySplitClass.isInstance(fight);
    }

    @SuppressWarnings("unchecked")
    public boolean isInTeam1(Player player, Object fight) {
        if (!isPartySplit(fight)) return false;
        try {
            HashSet<String> team1 = (HashSet<String>) getTeam1Method.invoke(fight);
            return team1 != null && team1.contains(player.getName());
        } catch (Exception ignored) {}
        return false;
    }

    @SuppressWarnings("unchecked")
    public List<Player> getTeam1Players(Object fight) {
        List<Player> result = new ArrayList<>();
        if (!isPartySplit(fight)) return result;
        try {
            HashSet<String> team1 = (HashSet<String>) getTeam1Method.invoke(fight);
            if (team1 != null) {
                for (String name : team1) {
                    Player p = Bukkit.getPlayer(name);
                    if (p != null && p.isOnline()) result.add(p);
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    @SuppressWarnings("unchecked")
    public List<Player> getTeam2Players(Object fight) {
        List<Player> result = new ArrayList<>();
        if (!isPartySplit(fight)) return result;
        try {
            HashSet<String> team2 = (HashSet<String>) getTeam2Method.invoke(fight);
            if (team2 != null) {
                for (String name : team2) {
                    Player p = Bukkit.getPlayer(name);
                    if (p != null && p.isOnline()) result.add(p);
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    public List<Player> getAllPlayers(Object fight) {
        List<Player> all = new ArrayList<>();
        all.addAll(getTeam1Players(fight));
        for (Player p : getTeam2Players(fight)) {
            if (!all.contains(p)) all.add(p);
        }
        return all;
    }

    public Player getTeam1ReferencePlayer(Object fight) {
        List<Player> t1 = getTeam1Players(fight);
        return t1.isEmpty() ? null : t1.get(0);
    }

    @SuppressWarnings("unchecked")
    public boolean isBedBroken(Player player, Object fight) {
        if (!isPartySplit(fight)) return false;
        try {
            boolean inTeam1 = isInTeam1(player, fight);
            if (inTeam1 && plugin.getMIsBed1Broken() != null)
                return (boolean) plugin.getMIsBed1Broken().invoke(fight);
            if (!inTeam1 && plugin.getMIsBed2Broken() != null)
                return (boolean) plugin.getMIsBed2Broken().invoke(fight);
        } catch (Exception e) { e.printStackTrace(); }
        return false;
    }

    public void onFightEnd(Object fight) {
        bridgeScores.remove(fight);
        bridgeWinner.remove(fight);
        new BukkitRunnable() {
            @Override public void run() { bridgeEndedFights.remove(fight); }
        }.runTaskLater(plugin, 40L);
        try {
            for (Player p : getAllPlayersInFight(fight)) {
                if (p != null) portalCooldown.remove(p.getUniqueId());
            }
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBridgePortalMove(PlayerMoveEvent event) {
        if (partySplitClass == null) return;
        if (event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        Player player = event.getPlayer();
        if (!plugin.isHooked() || !plugin.isInFight(player)) return;

        org.bukkit.Location toLoc = event.getTo();
        org.bukkit.block.Block feet  = toLoc.getBlock();
        org.bukkit.block.Block below = feet.getRelative(org.bukkit.block.BlockFace.DOWN);
        org.bukkit.block.Block portalBlock = null;
        if (feet.getType().name().contains("END_PORTAL") || feet.getType().name().contains("ENDER_PORTAL"))
            portalBlock = feet;
        else if (below.getType().name().contains("END_PORTAL") || below.getType().name().contains("ENDER_PORTAL"))
            portalBlock = below;
        if (portalBlock == null) return;

        Object fight;
        try {
            fight = plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), player);
        } catch (Exception e) { return; }
        if (fight == null || !isPartySplit(fight)) return;

        String kitName = plugin.getKitName(player);
        if (kitName == null) {
            try {
                Object kit = fight.getClass().getMethod("getKit").invoke(fight);
                if (kit != null) kitName = (String) kit.getClass().getMethod("getName").invoke(kit);
            } catch (Exception ignored) {}
        }
        if (kitName == null || !kitName.toLowerCase().contains("bridge")) return;

        boolean team1 = isInTeam1(player, fight);
        Location mySpawn = plugin.arenaSpawnLocations.get(player.getUniqueId());
        Location oppSpawnRef = null;
        {
            List<Player> oppTeam = team1 ? getTeam2Players(fight) : getTeam1Players(fight);
            for (Player opp : oppTeam) {
                oppSpawnRef = plugin.arenaSpawnLocations.get(opp.getUniqueId());
                if (oppSpawnRef != null) break;
            }
        }
        if (mySpawn != null && oppSpawnRef != null) {
            Location portalLoc = portalBlock.getLocation();
            double distToMy  = portalLoc.distanceSquared(mySpawn);
            double distToOpp = portalLoc.distanceSquared(oppSpawnRef);
            if (distToMy < distToOpp * 0.8) {
                event.setCancelled(true);
                player.teleport(faceToward(mySpawn, oppSpawnRef));
                return;
            }
        }

        event.setCancelled(true);

        long now = System.currentTimeMillis();
        if (portalCooldown.getOrDefault(player.getUniqueId(), 0L) > now) return;
        portalCooldown.put(player.getUniqueId(), now + 3000L);

        final Object finalFight = fight;
        boolean team1Scored = isInTeam1(player, fight);
        int[] scores = bridgeScores.computeIfAbsent(fight, k -> new int[]{0, 0});
        if (team1Scored) scores[0]++; else scores[1]++;

        int scoreLimit = plugin.getConfig().getInt("settings.bridge-party-score-limit", 5);
        int myScore    = team1Scored ? scores[0] : scores[1];

        String team1Color  = "§9";
        String team2Color  = "§c";
        String scorerColor = team1Scored ? team1Color : team2Color;

        String rawTitle    = plugin.getMsg("scored.title");
        String rawSubtitle = plugin.getMsg("scored.subtitle");
        if (rawTitle    == null || rawTitle.isEmpty())    rawTitle    = scorerColor + "§l<player>";
        if (rawSubtitle == null || rawSubtitle.isEmpty()) rawSubtitle = "§6Scored!";
        String titleMsg    = ChatColor.translateAlternateColorCodes('&',
                rawTitle.replace("<player>", player.getName()));
        String subtitleMsg = ChatColor.translateAlternateColorCodes('&',
                rawSubtitle.replace("<player>", player.getName()))
                + "  " + team1Color + scores[0] + " §8- " + team2Color + scores[1];

        Sound goalSound = plugin.getSoundByName("ENTITY_PLAYER_LEVELUP");
        if (goalSound == null) goalSound = plugin.getSoundByName("LEVEL_UP");
        final Sound finalGoalSound = goalSound;

        List<Player> allFightPlayers = getAllPlayersInFight(fight);

        if (myScore >= scoreLimit) {
            bridgeScores.remove(fight);
            portalCooldown.remove(player.getUniqueId());
            bridgeEndedFights.add(fight);
            bridgeWinner.put(fight, player);

            final boolean finalTeam1Won = team1Scored;
            final int finalScore1 = scores[0];
            final int finalScore2 = scores[1];

            final Location spectatorPos = (plugin.partyFFAManager != null)
                    ? plugin.partyFFAManager.getCenterAt100(finalFight) : null;
            for (Player p : allFightPlayers) {
                if (p == null || !p.isOnline()) continue;
                plugin.frozenPlayers.remove(p.getUniqueId());
                if (spectatorPos != null) p.teleport(spectatorPos);
                if (plugin.partyFFAManager != null)
                    plugin.partyFFAManager.applyCustomSpectator(p, allFightPlayers);
            }

            new BukkitRunnable() {
                @Override public void run() {
                    for (Player p : allFightPlayers) {
                        if (p == null || !p.isOnline()) continue;

                        boolean isWinTeam = finalTeam1Won ? isInTeam1(p, finalFight) : !isInTeam1(p, finalFight);

                        String t, s;
                        if (finalTeam1Won) {
                            t = plugin.getMsg("bestof.blue-wins.title");
                            s = plugin.getMsg("bestof.blue-wins.subtitle");
                            if (t == null || t.isEmpty()) t = "§9§lBLUE WINS!";
                            if (s == null || s.isEmpty()) s = "§9<blue_score> §8- §c<red_score>";
                        } else {
                            t = plugin.getMsg("bestof.red-wins.title");
                            s = plugin.getMsg("bestof.red-wins.subtitle");
                            if (t == null || t.isEmpty()) t = "§c§lRED WINS!";
                            if (s == null || s.isEmpty()) s = "§c<red_score> §8- §9<blue_score>";
                        }
                        s = s.replace("<blue_score>", String.valueOf(finalScore1))
                                .replace("<red_score>",  String.valueOf(finalScore2));

                        plugin.sendTitle(p, t, s, 10, 80, 20);
                        plugin.playEndMatchSounds(p, isWinTeam);
                        if (!isWinTeam) {
                            try { p.getWorld().strikeLightningEffect(p.getLocation()); } catch (Exception ignored) {}
                        }
                    }
                }
            }.runTaskLater(plugin, 10L);

            new BukkitRunnable() {
                @Override public void run() {
                    try { finalFight.getClass().getMethod("forceEnd", String.class).invoke(finalFight, ""); }
                    catch (Exception ignored) {}
                }
            }.runTaskLater(plugin, 12L);

        } else {
            for (Player p : allFightPlayers) {
                if (p == null || !p.isOnline()) continue;
                plugin.sendTitle(p, titleMsg, subtitleMsg, 5, 50, 15);
                if (finalGoalSound != null) {
                    try { p.playSound(p.getLocation(), finalGoalSound, 1.0f, 1.0f); } catch (Exception ignored) {}
                }
            }

            final List<Player> frozenList = new ArrayList<>(allFightPlayers);
            for (Player p : frozenList) {
                if (p != null && p.isOnline()) {
                    plugin.frozenPlayers.add(p.getUniqueId());
                }
            }

            for (Player p : frozenList) {
                if (p != null && p.isOnline())
                    plugin.forceRestoreKitBlocks(p, finalFight);
            }

            new BukkitRunnable() {
                @Override public void run() {
                    for (Player p : frozenList) {
                        if (p == null || !p.isOnline()) continue;
                        Location s = plugin.arenaSpawnLocations.get(p.getUniqueId());
                        if (s == null) continue;
                        boolean pTeam1 = isInTeam1(p, finalFight);
                        List<Player> opp = pTeam1 ? getTeam2Players(finalFight) : getTeam1Players(finalFight);
                        Location oppS = null;
                        for (Player o : opp) { oppS = plugin.arenaSpawnLocations.get(o.getUniqueId()); if (oppS != null) break; }
                        p.teleport(faceToward(s, oppS));
                    }
                }
            }.runTaskLater(plugin, 5L);

            startBridgeRoundCountdown(frozenList, finalFight);
        }
    }

    private void startBridgeRoundCountdown(List<Player> players, Object fight) {
        if (!plugin.startCountdownEnabled) {
            new BukkitRunnable() {
                @Override public void run() {
                    for (Player p : players) {
                        if (p == null || !p.isOnline()) continue;
                        plugin.frozenPlayers.remove(p.getUniqueId());
                        if (plugin.bridgeBlockResetManager != null)
                            plugin.bridgeBlockResetManager.scheduleRestore(p, 2L, 5L, 10L);
                    }
                }
            }.runTaskLater(plugin, 20L);
            return;
        }

        final int maxSeconds = 3;
        new BukkitRunnable() {
            int current = maxSeconds;
            @Override
            public void run() {
                if (bridgeEndedFights.contains(fight)) { cancel(); return; }
                try {
                    if ((boolean) fight.getClass().getMethod("hasEnded").invoke(fight)) { cancel(); return; }
                } catch (Exception ignored) {}

                if (current <= 0) {
                    for (Player p : players) {
                        if (p == null || !p.isOnline()) continue;
                        if (plugin.startMatchMessage != null && !plugin.startMatchMessage.isEmpty())
                            p.sendMessage(plugin.startMatchMessage);
                        if (plugin.startCountdownTitles != null && plugin.startCountdownTitles.containsKey(0))
                            plugin.sendTitle(p, plugin.startCountdownTitles.get(0), "", 0, 20, 10);
                        if (plugin.startCountdownSoundEnabled && plugin.startMatchSound != null)
                            p.playSound(p.getLocation(), plugin.startMatchSound,
                                    plugin.startCountdownVolume, plugin.startCountdownPitch);
                        plugin.frozenPlayers.remove(p.getUniqueId());
                        if (plugin.bridgeBlockResetManager != null)
                            plugin.bridgeBlockResetManager.scheduleRestore(p, 2L, 5L, 10L);
                    }
                    cancel();
                    return;
                }

                for (Player p : players) {
                    if (p == null || !p.isOnline()) continue;
                    if (plugin.startCountdownMessages != null)
                        p.sendMessage(plugin.startCountdownMessages.getOrDefault(current,
                                org.bukkit.ChatColor.RED + String.valueOf(current)));
                    if (plugin.startCountdownTitles != null && plugin.startCountdownTitles.containsKey(current))
                        plugin.sendTitle(p, plugin.startCountdownTitles.get(current), "", 0, 20, 0);
                    if (plugin.startCountdownSoundEnabled && plugin.startCountdownSounds != null) {
                        org.bukkit.Sound s = plugin.startCountdownSounds.get(current);
                        if (s != null) p.playSound(p.getLocation(), s,
                                plugin.startCountdownVolume, plugin.startCountdownPitch);
                    }
                }
                current--;
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private List<Player> getAllPlayersInFight(Object fight) {
        List<Player> all = new ArrayList<>(getTeam1Players(fight));
        for (Player p : getTeam2Players(fight)) if (!all.contains(p)) all.add(p);
        if (all.isEmpty()) {
            try {
                if (plugin.getMGetPlayersInFight() != null) {
                    @SuppressWarnings("unchecked")
                    List<Player> found = (List<Player>) plugin.getMGetPlayersInFight().invoke(fight);
                    if (found != null) all.addAll(found);
                }
            } catch (Exception ignored) {}
        }
        return all;
    }

    private Location faceToward(Location from, Location target) {
        if (target == null) return from;
        Location loc = from.clone();
        double dx = target.getX() - from.getX();
        double dz = target.getZ() - from.getZ();
        float yaw = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
        loc.setYaw(yaw);
        loc.setPitch(0f);
        return loc;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFriendlyFire(EntityDamageByEntityEvent event) {
        if (partySplitClass == null) return;
        if (!(event.getEntity() instanceof Player)) return;
        Player victim   = (Player) event.getEntity();
        Player attacker = resolveAttacker(event);
        if (attacker == null || attacker.equals(victim)) return;
        try {
            if (!plugin.isInFight(victim) || !plugin.isInFight(attacker)) return;
            Object fight = plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), victim);
            if (fight == null || !isPartySplit(fight)) return;
            if (plugin.getMPlayersAreTeammates() != null) {
                boolean teammates = (boolean) plugin.getMPlayersAreTeammates().invoke(fight, victim, attacker);
                if (teammates) event.setCancelled(true);
            }
        } catch (Exception ignored) {}
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFinalKill(EntityDamageByEntityEvent event) {
        if (partySplitClass == null) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && event.getCause() != EntityDamageEvent.DamageCause.PROJECTILE) return;
        if (!(event.getEntity() instanceof Player)) return;
        Player victim = (Player) event.getEntity();
        if (victim.getHealth() - event.getFinalDamage() > 0) return;
        if (!plugin.isInFight(victim)) return;
        try {
            Object fight = plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), victim);
            if (fight == null || !isPartySplit(fight)) return;
            if (!isBedBroken(victim, fight)) return;
            Player attacker = resolveAttacker(event);
            broadcastFinalKill(fight, victim, attacker);
        } catch (Exception ignored) {}
    }

    void broadcastFinalKill(Object fight, Player victim, Player attacker) {
        String victimColor    = isInTeam1(victim, fight) ? "§9" : "§c";
        String coloredVictim  = victimColor + victim.getName() + ChatColor.RESET;

        String coloredAttacker;
        if (attacker != null) {
            String atkColor   = isInTeam1(attacker, fight) ? "§9" : "§c";
            coloredAttacker   = atkColor + attacker.getName() + ChatColor.RESET;
        } else {
            coloredAttacker   = ChatColor.GRAY + "the environment";
        }

        String msg = plugin.getMsg("party.final-kill");
        if (msg == null || msg.isEmpty()) {
            msg = ChatColor.GOLD + "" + ChatColor.BOLD + "FINAL KILL! "
                    + coloredVictim
                    + ChatColor.GRAY + " was killed by "
                    + coloredAttacker;
        } else {
            msg = msg.replace("<victim>", coloredVictim)
                    .replace("<attacker>", coloredAttacker);
        }

        org.bukkit.Sound thunderSound = plugin.getSoundByName("ENTITY_LIGHTNING_BOLT_THUNDER");
        if (thunderSound == null) thunderSound = plugin.getSoundByName("AMBIENCE_THUNDER");

        broadcastToFight(fight, msg, thunderSound);
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) return (Player) event.getDamager();
        if (event.getDamager() instanceof Projectile) {
            Projectile proj = (Projectile) event.getDamager();
            if (proj.getShooter() instanceof Player) return (Player) proj.getShooter();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    void broadcastToFight(Object fight, String message, org.bukkit.Sound sound) {
        List<Player> targets = new ArrayList<>();
        try {
            if (plugin.getMGetPlayersInFight() != null) {
                List<Player> players = (List<Player>) plugin.getMGetPlayersInFight().invoke(fight);
                if (players != null) targets.addAll(players);
            }
        } catch (Exception ignored) {}

        if (targets.isEmpty()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                try {
                    if (plugin.isInFight(p)) {
                        Object pFight = plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), p);
                        if (fight.equals(pFight)) targets.add(p);
                    }
                } catch (Exception ignored) {}
            }
        }

        for (Player p : targets) {
            if (p == null || !p.isOnline()) continue;
            p.sendMessage(message);
            if (sound != null) {
                try { p.playSound(p.getLocation(), sound, 1.0f, 1.0f); } catch (Exception ignored) {}
            }
        }
    }

    void broadcastToFight(Object fight, String message) {
        broadcastToFight(fight, message, null);
    }
}
