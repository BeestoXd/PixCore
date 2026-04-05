package com.pixra.pixcore.feature.combat;

import com.pixra.pixcore.PixCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class BoxingTrackerManager implements Listener {

    private static final int HITS_TO_WIN = 100;
    private static final String DEFAULT_COMBO_LINE = "  1st to " + HITS_TO_WIN + "!";

    private final PixCore plugin;
    private final Map<Object, BoxingFightState> fightStates = new HashMap<>();

    public BoxingTrackerManager(PixCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player attacker = resolveAttacker(event);
        if (attacker == null) {
            return;
        }

        Player victim = (Player) event.getEntity();
        if (attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        if (!plugin.isHooked() || !plugin.isInFight(attacker) || !plugin.isInFight(victim)) {
            return;
        }

        Object fight = getFight(attacker);
        Object victimFight = getFight(victim);
        if (fight == null || victimFight == null || !fight.equals(victimFight)) {
            return;
        }

        String kitName = plugin.getKitName(attacker);
        if (!isTrackingKit(kitName)) {
            return;
        }

        BoxingFightState state = fightStates.computeIfAbsent(fight, key -> new BoxingFightState());
        state.ensurePlayer(attacker.getUniqueId());
        state.ensurePlayer(victim.getUniqueId());
        if (state.isEnding()) {
            return;
        }

        int attackerHits = state.recordHit(attacker.getUniqueId(), victim.getUniqueId());
        if (attackerHits >= HITS_TO_WIN && state.beginEnding()) {
            endBoxingFight(fight, victim, state);
        }
    }

    public boolean isTrackingKit(String kitName) {
        return kitName != null && kitName.toLowerCase().contains("boxing");
    }

    public void onFightStart(Object fight, Iterable<Player> players, String kitName) {
        if (fight == null || !isTrackingKit(kitName)) {
            return;
        }

        BoxingFightState state = new BoxingFightState();
        if (players != null) {
            for (Player player : players) {
                if (player != null) {
                    state.ensurePlayer(player.getUniqueId());
                }
            }
        }
        fightStates.put(fight, state);
    }

    public void onFightEnd(Object fight, Iterable<Player> players) {
        if (fight != null) {
            fightStates.remove(fight);
        }

        if (players != null) {
            for (Player player : players) {
                if (player != null) {
                    clearPlayer(player.getUniqueId());
                }
            }
        }
    }

    public void clearPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }

        Iterator<Map.Entry<Object, BoxingFightState>> iterator = fightStates.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Object, BoxingFightState> entry = iterator.next();
            BoxingFightState state = entry.getValue();
            state.removePlayer(playerId);
            if (state.isEmpty()) {
                iterator.remove();
            }
        }
    }

    public void clearAll() {
        fightStates.clear();
    }

    public int getHits(Player player) {
        BoxingFightState state = getState(player);
        if (state == null || player == null) {
            return 0;
        }
        return state.getHits(player.getUniqueId());
    }

    public int getOpponentHits(Player player) {
        UUID opponentId = getOpponentId(player);
        BoxingFightState state = getState(player);
        if (state == null || opponentId == null) {
            return 0;
        }
        return state.getHits(opponentId);
    }

    public String getHitDeltaText(Player player) {
        int delta = getHits(player) - getOpponentHits(player);
        String sign = delta >= 0 ? "+" : "";
        ChatColor color = delta >= 0 ? ChatColor.GREEN : ChatColor.RED;
        return color + "(" + sign + delta + ")";
    }

    public String getComboLine(Player player) {
        if (player == null) {
            return ChatColor.WHITE + DEFAULT_COMBO_LINE;
        }

        BoxingFightState state = getState(player);
        if (state == null) {
            return ChatColor.WHITE + DEFAULT_COMBO_LINE;
        }

        UUID playerId = player.getUniqueId();
        UUID opponentId = getOpponentId(player);
        int ownCombo = state.getCombo(playerId);
        int opponentCombo = opponentId != null ? state.getCombo(opponentId) : 0;

        if (ownCombo > 0) {
            return ChatColor.GREEN.toString() + ownCombo + " combo";
        }
        if (opponentCombo > 0) {
            return ChatColor.RED.toString() + opponentCombo + " combo";
        }
        return ChatColor.WHITE + DEFAULT_COMBO_LINE;
    }

    private BoxingFightState getState(Player player) {
        if (player == null || !plugin.isHooked()) {
            return null;
        }

        Object fight = getFight(player);
        if (fight == null) {
            return null;
        }
        return fightStates.get(fight);
    }

    private UUID getOpponentId(Player player) {
        if (player == null || !plugin.isHooked()) {
            return null;
        }

        Object fight = getFight(player);
        if (fight == null) {
            return null;
        }

        try {
            if (plugin.getMGetPlayersInFight() != null) {
                @SuppressWarnings("unchecked")
                java.util.List<Player> players = (java.util.List<Player>) plugin.getMGetPlayersInFight().invoke(fight);
                if (players != null) {
                    for (Player other : players) {
                        if (other != null && !other.getUniqueId().equals(player.getUniqueId())) {
                            return other.getUniqueId();
                        }
                    }
                }
            }

            if (plugin.getMGetPlayers() != null) {
                @SuppressWarnings("unchecked")
                java.util.List<Player> players = (java.util.List<Player>) plugin.getMGetPlayers().invoke(fight);
                if (players != null) {
                    for (Player other : players) {
                        if (other != null && !other.getUniqueId().equals(player.getUniqueId())) {
                            return other.getUniqueId();
                        }
                    }
                }
            }

            if (plugin.getMGetFirstPlayer() != null && plugin.getMGetSecondPlayer() != null) {
                Player first = (Player) plugin.getMGetFirstPlayer().invoke(fight);
                Player second = (Player) plugin.getMGetSecondPlayer().invoke(fight);
                if (first != null && second != null) {
                    return player.getUniqueId().equals(first.getUniqueId())
                            ? second.getUniqueId()
                            : first.getUniqueId();
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    private Object getFight(Player player) {
        if (player == null || !plugin.isHooked() || plugin.getMGetFight() == null || plugin.getStrikePracticeAPI() == null) {
            return null;
        }

        try {
            return plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), player);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            return (Player) event.getDamager();
        }

        if (event.getDamager() instanceof org.bukkit.entity.Projectile) {
            org.bukkit.entity.Projectile projectile = (org.bukkit.entity.Projectile) event.getDamager();
            if (projectile.getShooter() instanceof Player) {
                return (Player) projectile.getShooter();
            }
        }

        return null;
    }

    private void endBoxingFight(Object fight, Player loser, BoxingFightState state) {
        if (fight == null || loser == null || plugin.getMHandleDeath() == null) {
            state.resetEnding();
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Object currentFight = getFight(loser);
                if (currentFight == null || !fight.equals(currentFight)) {
                    return;
                }
                plugin.getMHandleDeath().invoke(fight, loser);
            } catch (Exception e) {
                state.resetEnding();
                plugin.getLogger().warning("[BoxingTrackerManager] Could not end boxing fight at "
                        + HITS_TO_WIN + " hits: " + e.getMessage());
            }
        });
    }

    private static final class BoxingFightState {
        private final Map<UUID, Integer> hits = new HashMap<>();
        private final Map<UUID, Integer> combos = new HashMap<>();
        private boolean ending;

        private void ensurePlayer(UUID playerId) {
            hits.putIfAbsent(playerId, 0);
            combos.putIfAbsent(playerId, 0);
        }

        private int recordHit(UUID attackerId, UUID victimId) {
            int updatedHits = getHits(attackerId) + 1;
            hits.put(attackerId, updatedHits);
            combos.put(attackerId, getCombo(attackerId) + 1);
            combos.put(victimId, 0);
            return updatedHits;
        }

        private int getHits(UUID playerId) {
            return hits.getOrDefault(playerId, 0);
        }

        private int getCombo(UUID playerId) {
            return combos.getOrDefault(playerId, 0);
        }

        private void removePlayer(UUID playerId) {
            hits.remove(playerId);
            combos.remove(playerId);
        }

        private boolean isEnding() {
            return ending;
        }

        private boolean beginEnding() {
            if (ending) {
                return false;
            }
            ending = true;
            return true;
        }

        private void resetEnding() {
            ending = false;
        }

        private boolean isEmpty() {
            return hits.isEmpty() && combos.isEmpty();
        }
    }
}
