package com.pixra.pixCore.party;

import com.pixra.pixCore.PixCore;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.lang.reflect.Method;
import java.util.HashSet;

public class PartySplitManager implements Listener {

    private final PixCore plugin;
    private Class<?> partySplitClass;
    private Method getTeam1Method;
    private Method getTeam2Method;

    public PartySplitManager(PixCore plugin) {
        this.plugin = plugin;
        try {
            partySplitClass = Class.forName("ga.strikepractice.fights.party.partyfights.PartySplit");
            getTeam1Method = partySplitClass.getMethod("getTeam1");
            getTeam2Method = partySplitClass.getMethod("getTeam2");
        } catch (Exception e) {
            plugin.getLogger().warning("Could not hook into PartySplit class. PartySplit features will be disabled.");
        }
    }

    public boolean isPartySplit(Object fight) {
        return partySplitClass != null && partySplitClass.isInstance(fight);
    }

    public boolean isBedBroken(Player player, Object fight) {
        if (!isPartySplit(fight)) return false;
        try {
            @SuppressWarnings("unchecked")
            HashSet<String> team1 = (HashSet<String>) getTeam1Method.invoke(fight);
            @SuppressWarnings("unchecked")
            HashSet<String> team2 = (HashSet<String>) getTeam2Method.invoke(fight);

            boolean isTeam1 = team1 != null && team1.contains(player.getName());
            boolean isTeam2 = team2 != null && team2.contains(player.getName());

            if (isTeam1) {
                return (boolean) plugin.getMIsBed1Broken().invoke(fight);
            } else if (isTeam2) {
                return (boolean) plugin.getMIsBed2Broken().invoke(fight);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFriendlyFire(EntityDamageByEntityEvent event) {
        if (partySplitClass == null) return;

        if (!(event.getEntity() instanceof Player)) return;
        Player victim = (Player) event.getEntity();

        Player attacker = null;
        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile) {
            Projectile proj = (Projectile) event.getDamager();
            if (proj.getShooter() instanceof Player) {
                attacker = (Player) proj.getShooter();
            }
        }

        if (attacker == null) return;
        if (attacker.equals(victim)) return;

        try {
            if (plugin.isInFight(victim) && plugin.isInFight(attacker)) {
                Object fight = plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), victim);
                if (fight != null && isPartySplit(fight)) {
                    @SuppressWarnings("unchecked")
                    HashSet<String> team1 = (HashSet<String>) getTeam1Method.invoke(fight);
                    @SuppressWarnings("unchecked")
                    HashSet<String> team2 = (HashSet<String>) getTeam2Method.invoke(fight);

                    if (team1 != null && team2 != null) {
                        boolean victimInTeam1 = team1.contains(victim.getName());
                        boolean attackerInTeam1 = team1.contains(attacker.getName());

                        boolean victimInTeam2 = team2.contains(victim.getName());
                        boolean attackerInTeam2 = team2.contains(attacker.getName());

                        if ((victimInTeam1 && attackerInTeam1) || (victimInTeam2 && attackerInTeam2)) {
                            event.setCancelled(true);
                        }
                    }
                }
            }
        } catch (Exception e) {
        }
    }
}