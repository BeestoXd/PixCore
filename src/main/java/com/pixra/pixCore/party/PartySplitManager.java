package com.pixra.pixCore.party;

import com.pixra.pixCore.PixCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class PartySplitManager implements Listener {

    private final PixCore plugin;

    private Class<?> partySplitClass;
    private Method   getTeam1Method;
    private Method   getTeam2Method;
    private Method   getAlive1Method;
    private Method   getAlive2Method;

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
    void broadcastToFight(Object fight, String message) {
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
}