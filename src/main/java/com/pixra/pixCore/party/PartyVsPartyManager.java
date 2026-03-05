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
}