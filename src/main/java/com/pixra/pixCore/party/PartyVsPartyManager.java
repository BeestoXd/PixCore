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

public class PartyVsPartyManager implements Listener {

    private final PixCore plugin;

    private Class<?> partyVsPartyClass;
    private Method   getParty1Method;
    private Method   getParty2Method;
    private Method   getPartyAlive1Method;
    private Method   getPartyAlive2Method;

    private Method partyGetPlayersMethod;
    private Method partyGetMembersNamesMethod;
    private Method partyGetMembersMethod;

    private Method forceEndPartyMethod;

    private final Map<Object, int[]>        bridgeScores     = new HashMap<>();
    private final Map<java.util.UUID, Long> portalCooldown   = new HashMap<>();
    private final java.util.Set<Object>     bridgeEndedFights = new java.util.HashSet<>();
    private final Map<Object, Player>       bridgeWinner     = new HashMap<>();

    public boolean isBridgeEndHandled(Object fight) { return bridgeEndedFights.contains(fight); }
    public int[] getBridgeScores(Object fight)       { return bridgeScores.getOrDefault(fight, new int[]{0, 0}); }
    public Player getBridgeWinner(Object fight)      { return bridgeWinner.get(fight); }

    public void onFightEnd(Object fight) {
        bridgeScores.remove(fight);
        bridgeWinner.remove(fight);
        new BukkitRunnable() {
            @Override public void run() { bridgeEndedFights.remove(fight); }
        }.runTaskLater(plugin, 40L);
        try {
            for (Player p : getAllPlayers(fight)) {
                if (p != null) portalCooldown.remove(p.getUniqueId());
            }
        } catch (Exception ignored) {}
    }

    public PartyVsPartyManager(PixCore plugin) {
        this.plugin = plugin;
        try {
            partyVsPartyClass    = Class.forName("ga.strikepractice.fights.party.partyfights.PartyVsParty");
            getParty1Method      = partyVsPartyClass.getMethod("getParty1");
            getParty2Method      = partyVsPartyClass.getMethod("getParty2");
            getPartyAlive1Method = partyVsPartyClass.getMethod("getPartyAlive1");
            getPartyAlive2Method = partyVsPartyClass.getMethod("getPartyAlive2");

            try {
                Class<?> partyClass = Class.forName("ga.strikepractice.party.Party");
                try { partyGetPlayersMethod      = partyClass.getMethod("getPlayers");      } catch (Exception ignored) {}
                try { partyGetMembersNamesMethod = partyClass.getMethod("getMembersNames"); } catch (Exception ignored) {}
                try { partyGetMembersMethod      = partyClass.getMethod("getMembers");      } catch (Exception ignored) {}
                try { forceEndPartyMethod = partyVsPartyClass.getMethod("forceEnd", partyClass); } catch (Exception ignored) {}
            } catch (Exception ignored) {}

        } catch (Exception e) {
            plugin.getLogger().warning("[PartyVsPartyManager] Could not hook PartyVsParty class — features disabled.");
        }
    }

    public boolean isPartyVsParty(Object fight) {
        return partyVsPartyClass != null && partyVsPartyClass.isInstance(fight);
    }

    @SuppressWarnings("unchecked")
    private List<Player> playersFromParty(Object party) {
        List<Player> result = new ArrayList<>();
        if (party == null) return result;

        if (partyGetPlayersMethod != null) {
            try {
                Object raw = partyGetPlayersMethod.invoke(party);
                if (raw instanceof List) {
                    for (Object o : (List<?>) raw) {
                        if (o instanceof Player && ((Player) o).isOnline())
                            result.add((Player) o);
                    }
                    if (!result.isEmpty()) return result;
                }
            } catch (Exception ignored) {}
        }

        if (partyGetMembersNamesMethod != null) {
            try {
                Object raw = partyGetMembersNamesMethod.invoke(party);
                if (raw instanceof Iterable) {
                    for (Object o : (Iterable<?>) raw) {
                        if (o instanceof String) {
                            Player p = Bukkit.getPlayer((String) o);
                            if (p != null && p.isOnline() && !result.contains(p)) result.add(p);
                        }
                    }
                    if (!result.isEmpty()) return result;
                }
            } catch (Exception ignored) {}
        }

        if (partyGetMembersMethod != null) {
            try {
                Object raw = partyGetMembersMethod.invoke(party);
                if (raw instanceof Iterable) {
                    for (Object o : (Iterable<?>) raw) {
                        if (o instanceof Player && ((Player) o).isOnline()) {
                            if (!result.contains(o)) result.add((Player) o);
                        } else if (o instanceof String) {
                            Player p = Bukkit.getPlayer((String) o);
                            if (p != null && p.isOnline() && !result.contains(p)) result.add(p);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    public List<Player> getParty1Players(Object fight) {
        List<Player> result = new ArrayList<>();
        if (!isPartyVsParty(fight)) return result;
        try {
            if (getParty1Method != null) {
                Object party1 = getParty1Method.invoke(fight);
                result = playersFromParty(party1);
            }
        } catch (Exception ignored) {}

        if (result.isEmpty() && getPartyAlive1Method != null) {
            try {
                HashSet<String> alive1 = (HashSet<String>) getPartyAlive1Method.invoke(fight);
                if (alive1 != null) {
                    for (String name : alive1) {
                        Player p = Bukkit.getPlayer(name);
                        if (p != null && p.isOnline()) result.add(p);
                    }
                }
            } catch (Exception ignored) {}
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public List<Player> getParty2Players(Object fight) {
        List<Player> result = new ArrayList<>();
        if (!isPartyVsParty(fight)) return result;
        try {
            if (getParty2Method != null) {
                Object party2 = getParty2Method.invoke(fight);
                result = playersFromParty(party2);
            }
        } catch (Exception ignored) {}

        if (result.isEmpty() && getPartyAlive2Method != null) {
            try {
                HashSet<String> alive2 = (HashSet<String>) getPartyAlive2Method.invoke(fight);
                if (alive2 != null) {
                    for (String name : alive2) {
                        Player p = Bukkit.getPlayer(name);
                        if (p != null && p.isOnline()) result.add(p);
                    }
                }
            } catch (Exception ignored) {}
        }
        return result;
    }

    public List<Player> getAllPlayers(Object fight) {
        List<Player> all = new ArrayList<>();
        all.addAll(getParty1Players(fight));
        for (Player p : getParty2Players(fight)) {
            if (!all.contains(p)) all.add(p);
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

    @SuppressWarnings("unchecked")
    public boolean isInParty1(Player player, Object fight) {
        if (!isPartyVsParty(fight)) return false;
        try {
            if (getParty1Method != null) {
                Object party1 = getParty1Method.invoke(fight);
                List<Player> p1Members = playersFromParty(party1);
                if (!p1Members.isEmpty()) {
                    for (Player m : p1Members) {
                        if (m.getUniqueId().equals(player.getUniqueId())) return true;
                    }
                    if (getParty2Method != null) {
                        Object party2 = getParty2Method.invoke(fight);
                        List<Player> p2Members = playersFromParty(party2);
                        for (Player m : p2Members) {
                            if (m.getUniqueId().equals(player.getUniqueId())) return false;
                        }
                    }
                }
            }

            if (getPartyAlive1Method != null) {
                HashSet<String> alive1 = (HashSet<String>) getPartyAlive1Method.invoke(fight);
                if (alive1 != null && alive1.contains(player.getName())) return true;
            }
            if (getPartyAlive2Method != null) {
                HashSet<String> alive2 = (HashSet<String>) getPartyAlive2Method.invoke(fight);
                if (alive2 != null && alive2.contains(player.getName())) return false;
            }

            Player ref = getParty1ReferencePlayer(fight);
            if (ref != null && plugin.getMPlayersAreTeammates() != null) {
                if (ref.getUniqueId().equals(player.getUniqueId())) return true;
                try {
                    return (boolean) plugin.getMPlayersAreTeammates().invoke(fight, ref, player);
                } catch (Exception ignored) {}
            }

        } catch (Exception ignored) {}
        return false;
    }

    public Player getParty1ReferencePlayer(Object fight) {
        List<Player> p1 = getParty1Players(fight);
        return p1.isEmpty() ? null : p1.get(0);
    }

    @SuppressWarnings("unchecked")
    public boolean isBedBroken(Player player, Object fight) {
        if (!isPartyVsParty(fight)) return false;
        try {
            boolean inParty1 = isInParty1(player, fight);
            if (inParty1 && plugin.getMIsBed1Broken() != null)
                return (boolean) plugin.getMIsBed1Broken().invoke(fight);
            if (!inParty1 && plugin.getMIsBed2Broken() != null)
                return (boolean) plugin.getMIsBed2Broken().invoke(fight);
        } catch (Exception e) { e.printStackTrace(); }
        return false;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFriendlyFire(EntityDamageByEntityEvent event) {
        if (partyVsPartyClass == null) return;
        if (!(event.getEntity() instanceof Player)) return;
        Player victim   = (Player) event.getEntity();
        Player attacker = resolveAttacker(event);
        if (attacker == null || attacker.equals(victim)) return;
        try {
            if (!plugin.isInFight(victim) || !plugin.isInFight(attacker)) return;
            Object fight = plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), victim);
            if (fight == null || !isPartyVsParty(fight)) return;
            if (plugin.getMPlayersAreTeammates() != null) {
                boolean teammates = (boolean) plugin.getMPlayersAreTeammates().invoke(fight, victim, attacker);
                if (teammates) event.setCancelled(true);
            }
        } catch (Exception ignored) {}
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFinalKill(EntityDamageByEntityEvent event) {
        if (partyVsPartyClass == null) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && event.getCause() != EntityDamageEvent.DamageCause.PROJECTILE) return;
        if (!(event.getEntity() instanceof Player)) return;
        Player victim = (Player) event.getEntity();
        if (victim.getHealth() - event.getFinalDamage() > 0) return;
        if (!plugin.isInFight(victim)) return;
        try {
            Object fight = plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), victim);
            if (fight == null || !isPartyVsParty(fight)) return;
            if (!isBedBroken(victim, fight)) return;
            Player attacker = resolveAttacker(event);
            broadcastFinalKill(fight, victim, attacker);
        } catch (Exception ignored) {}
    }

    private void broadcastFinalKill(Object fight, Player victim, Player attacker) {
        String victimColor    = isInParty1(victim, fight) ? "§9" : "§c";
        String coloredVictim  = victimColor + victim.getName() + ChatColor.RESET;
        String coloredAttacker;
        if (attacker != null) {
            String atkColor   = isInParty1(attacker, fight) ? "§9" : "§c";
            coloredAttacker   = atkColor + attacker.getName() + ChatColor.RESET;
        } else {
            coloredAttacker   = ChatColor.GRAY + "the environment";
        }
        String msg = plugin.getMsg("party.final-kill");
        if (msg == null || msg.isEmpty()) {
            msg = ChatColor.GOLD + "" + ChatColor.BOLD + "FINAL KILL! "
                    + coloredVictim + ChatColor.GRAY + " was killed by " + coloredAttacker;
        } else {
            msg = msg.replace("<victim>", coloredVictim).replace("<attacker>", coloredAttacker);
        }
        broadcastToFight(fight, msg);
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
    private void broadcastToFight(Object fight, String message) {
        try {
            if (plugin.getMGetPlayersInFight() != null) {
                List<Player> players = (List<Player>) plugin.getMGetPlayersInFight().invoke(fight);
                if (players != null) {
                    for (Player p : players) if (p != null && p.isOnline()) p.sendMessage(message);
                    return;
                }
            }
        } catch (Exception ignored) {}
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                if (plugin.isInFight(p)) {
                    Object pFight = plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), p);
                    if (fight.equals(pFight)) p.sendMessage(message);
                }
            } catch (Exception ignored) {}
        }
    }

    @SuppressWarnings("unchecked")
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBridgePortalMove(PlayerMoveEvent event) {
        if (partyVsPartyClass == null) return;
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
        if (fight == null || !isPartyVsParty(fight)) return;

        String kitName = plugin.getKitName(player);
        if (kitName == null) {
            try {
                Object kit = fight.getClass().getMethod("getKit").invoke(fight);
                if (kit != null) kitName = (String) kit.getClass().getMethod("getName").invoke(kit);
            } catch (Exception ignored) {}
        }
        if (kitName == null || !kitName.toLowerCase().contains("bridge")) return;

        boolean inParty1 = isInParty1(player, fight);
        Location mySpawn = plugin.arenaSpawnLocations.get(player.getUniqueId());
        Location oppSpawnRef = null;
        {
            List<Player> oppTeam = inParty1 ? getParty2Players(fight) : getParty1Players(fight);
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
        Location spawn = plugin.arenaSpawnLocations.get(player.getUniqueId());
        if (spawn != null) player.teleport(faceToward(spawn, oppSpawnRef));

        long now = System.currentTimeMillis();
        if (portalCooldown.getOrDefault(player.getUniqueId(), 0L) > now) return;
        portalCooldown.put(player.getUniqueId(), now + 3000L);

        final Object finalFight = fight;
        boolean party1Scored = isInParty1(player, fight);
        int[] scores = bridgeScores.computeIfAbsent(fight, k -> new int[]{0, 0});
        if (party1Scored) scores[0]++; else scores[1]++;

        int scoreLimit = plugin.getConfig().getInt("settings.bridge-party-score-limit", 5);
        int myScore    = party1Scored ? scores[0] : scores[1];

        String team1Color  = "§9";
        String team2Color  = "§c";
        String scorerColor = party1Scored ? team1Color : team2Color;

        String rawTitle    = plugin.getMsg("scored.title");
        String rawSubtitle = plugin.getMsg("scored.subtitle");
        if (rawTitle    == null || rawTitle.isEmpty())    rawTitle    = scorerColor + "§l<player>";
        if (rawSubtitle == null || rawSubtitle.isEmpty()) rawSubtitle = "§6Scored!";
        String titleMsg    = ChatColor.translateAlternateColorCodes('&', rawTitle.replace("<player>", player.getName()));
        String subtitleMsg = ChatColor.translateAlternateColorCodes('&', rawSubtitle.replace("<player>", player.getName()))
                + "  " + team1Color + scores[0] + " §8- " + team2Color + scores[1];

        Sound goalSound = plugin.getSoundByName("ENTITY_PLAYER_LEVELUP");
        if (goalSound == null) goalSound = plugin.getSoundByName("LEVEL_UP");
        final Sound finalGoalSound = goalSound;

        List<Player> allFightPlayers = getAllPlayers(fight);

        if (myScore >= scoreLimit) {
            bridgeScores.remove(fight);
            portalCooldown.remove(player.getUniqueId());
            bridgeEndedFights.add(fight);
            bridgeWinner.put(fight, player);

            final boolean finalParty1Won = party1Scored;
            final int finalScore1 = scores[0];
            final int finalScore2 = scores[1];

            for (Player p : allFightPlayers) {
                if (p != null && p.isOnline()) plugin.frozenPlayers.add(p.getUniqueId());
            }

            new BukkitRunnable() {
                @Override public void run() {
                    for (Player p : allFightPlayers) {
                        if (p == null || !p.isOnline()) continue;
                        boolean isWinTeam = finalParty1Won ? isInParty1(p, finalFight) : !isInParty1(p, finalFight);

                        String t, s;
                        if (finalParty1Won) {
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
                    try {
                        if (forceEndPartyMethod != null) {
                            Object loserParty = finalParty1Won
                                    ? getParty2Method.invoke(finalFight)
                                    : getParty1Method.invoke(finalFight);
                            if (loserParty != null) {
                                forceEndPartyMethod.invoke(finalFight, loserParty);
                                return;
                            }
                        }
                        finalFight.getClass().getMethod("forceEnd", String.class).invoke(finalFight, "");
                    } catch (Exception ignored) {}
                }
            }.runTaskLater(plugin, 60L);

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
                if (p != null && p.isOnline()) plugin.frozenPlayers.add(p.getUniqueId());
            }

            new BukkitRunnable() {
                @Override public void run() {
                    for (Player p : frozenList) {
                        if (p == null || !p.isOnline()) continue;
                        Location s = plugin.arenaSpawnLocations.get(p.getUniqueId());
                        if (s == null) continue;
                        boolean pInP1 = isInParty1(p, finalFight);
                        List<Player> opp = pInP1 ? getParty2Players(finalFight) : getParty1Players(finalFight);
                        Location oppS = null;
                        for (Player o : opp) { oppS = plugin.arenaSpawnLocations.get(o.getUniqueId()); if (oppS != null) break; }
                        p.teleport(faceToward(s, oppS));
                        plugin.applyStartKit(p, finalFight);
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
                        if (p != null && p.isOnline()) plugin.frozenPlayers.remove(p.getUniqueId());
                    }
                }
            }.runTaskLater(plugin, 20L);
            return;
        }

        final int maxSeconds = plugin.startCountdownDuration;
        new BukkitRunnable() {
            int current = maxSeconds;
            @Override public void run() {
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
}