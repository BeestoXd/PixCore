package com.pixra.pixCore.listeners;

import com.pixra.pixCore.PixCore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.Location;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

public class BedListener implements Listener {

    private final PixCore plugin;

    private final Set<Location> processedBedHalves = new HashSet<>();
    private final Map<UUID, Long> mlgRushCooldown = new HashMap<>();

    public BedListener(PixCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        String typeName = event.getBlock().getType().name();
        if (typeName.contains("BED_BLOCK") || typeName.endsWith("_BED")) {
            event.setCancelled(true);
        }
    }

    @SuppressWarnings("unchecked")
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockDamageOwnBed(BlockDamageEvent event) {
        if (!plugin.isHooked()) return;
        String typeName = event.getBlock().getType().name();
        if (!typeName.contains("BED_BLOCK") && !typeName.endsWith("_BED")) return;
        Player player = event.getPlayer();
        if (!plugin.isInFight(player)) return;
        try {
            Object fight = plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), player);
            if (fight == null) return;
            if (plugin.partyFFAManager != null && plugin.partyFFAManager.isPartyFFA(fight)) {
                if (plugin.partyFFAManager.isOwnBed(player, event.getBlock(), fight)) {
                    event.setCancelled(true);
                    plugin.sendCooldownMessage(player, "bed-break-self");
                }
            }
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreakOwnBedEarly(BlockBreakEvent event) {
        if (!plugin.isHooked()) return;
        String typeName = event.getBlock().getType().name();
        if (!typeName.contains("BED_BLOCK") && !typeName.endsWith("_BED")) return;
        Player player = event.getPlayer();
        if (!plugin.isInFight(player)) return;
        try {
            Object fight = plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), player);
            if (fight == null) return;
            Block bedBlock = event.getBlock();
            if (plugin.partyFFAManager != null && plugin.partyFFAManager.isPartyFFA(fight)) {
                if (plugin.partyFFAManager.isOwnBed(player, bedBlock, fight)) {
                    event.setCancelled(true);
                }
            } else {
                Block otherHalf = null;
                BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
                for (BlockFace face : faces) {
                    Block rel = bedBlock.getRelative(face);
                    if (rel.getType().name().contains("BED_BLOCK") || rel.getType().name().endsWith("_BED")) {
                        otherHalf = rel; break;
                    }
                }
                boolean isP1Team = false;
                Player p1 = null;
                if (plugin.getMGetFirstPlayer() != null) {
                    try { p1 = (Player) plugin.getMGetFirstPlayer().invoke(fight); } catch (Exception ignored) {}
                }
                if (p1 != null && p1.getUniqueId().equals(player.getUniqueId())) isP1Team = true;
                if (isOwnBed(player, bedBlock, otherHalf, fight, isP1Team)) {
                    event.setCancelled(true);
                }

                if (!event.isCancelled()) {
                    String kitName = plugin.getKitName(player);
                    if (kitName != null && (kitName.equalsIgnoreCase("mlgrush")
                            || kitName.equalsIgnoreCase("mlgrushelo"))) {
                        event.setCancelled(true);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreakCountdown(BlockBreakEvent event) {
        if (plugin.frozenPlayers.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            plugin.sendCooldownMessage(event.getPlayer(), "block-break-denied-start");
        }
    }

    @SuppressWarnings("unchecked")
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.isHooked()) return;

        if (processedBedHalves.contains(event.getBlock().getLocation())) {
            processedBedHalves.remove(event.getBlock().getLocation());
            return;
        }

        Player player = event.getPlayer();
        String typeName = event.getBlock().getType().name();

        if (typeName.contains("BED_BLOCK") || typeName.endsWith("_BED")) {
            try {
                if (plugin.isInFight(player)) {
                    Object fight = plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), player);
                    if (fight != null) {

                        boolean isP1Team = false;

                        if (plugin.partySplitManager != null && plugin.partySplitManager.isPartySplit(fight)) {
                            isP1Team = plugin.partySplitManager.isInTeam1(player, fight);

                        } else if (plugin.partyVsPartyManager != null && plugin.partyVsPartyManager.isPartyVsParty(fight)) {
                            isP1Team = plugin.partyVsPartyManager.isInParty1(player, fight);

                        } else {
                            Player p1 = null;
                            if (plugin.getMGetFirstPlayer() != null) {
                                try { p1 = (Player) plugin.getMGetFirstPlayer().invoke(fight); } catch (Exception ignored) {}
                            }
                            if (p1 == null && plugin.getMGetPlayersInFight() != null) {
                                try {
                                    List<Player> fPlayers = (List<Player>) plugin.getMGetPlayersInFight().invoke(fight);
                                    if (fPlayers != null && !fPlayers.isEmpty()) p1 = fPlayers.get(0);
                                } catch (Exception ignored) {}
                            }
                            if (p1 != null) {
                                if (p1.getUniqueId().equals(player.getUniqueId())) isP1Team = true;
                                else if (plugin.getMPlayersAreTeammates() != null) {
                                    try { isP1Team = (boolean) plugin.getMPlayersAreTeammates().invoke(fight, p1, player); } catch (Exception ignored) {}
                                } else if (plugin.getMGetTeammates() != null) {
                                    try {
                                        List<String> tms = (List<String>) plugin.getMGetTeammates().invoke(fight, p1);
                                        if (tms != null && tms.contains(player.getName())) isP1Team = true;
                                    } catch (Exception ignored) {}
                                }
                            }
                        }

                        String enemyColorCode = "§f";
                        String enemyTeamFullName = "§fOpponent";
                        String playerColorCode = plugin.getTeamColorCode(player, fight);

                        if (plugin.partyFFAManager != null && plugin.partyFFAManager.isPartyFFA(fight)) {
                            String ffaCode = plugin.partyFFAManager.getFfaColorCode(player);
                            if (ffaCode != null) playerColorCode = ffaCode;
                        }
                        String playerTeamFullName = plugin.getColorNameFromCode(playerColorCode);

                        if (playerColorCode.equals("§9")) { enemyColorCode = "§c"; enemyTeamFullName = "§cRed"; }
                        else if (playerColorCode.equals("§c")) { enemyColorCode = "§9"; enemyTeamFullName = "§9Blue"; }
                        else {
                            if (plugin.getMGetOpponents() != null) {
                                List<String> opponents = (List<String>) plugin.getMGetOpponents().invoke(fight, player);
                                if (opponents != null && !opponents.isEmpty()) {
                                    for (String oppName : opponents) {
                                        Player opp = Bukkit.getPlayer(oppName);
                                        if (opp != null) {
                                            enemyColorCode = plugin.getTeamColorCode(opp, fight);
                                            enemyTeamFullName = plugin.getColorNameFromCode(enemyColorCode);
                                            break;
                                        }
                                    }
                                }
                            }
                        }

                        Block bedBlock = event.getBlock();
                        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
                        Block otherHalf = null;
                        for (BlockFace face : faces) {
                            Block rel = bedBlock.getRelative(face);
                            if (rel.getType().name().contains("BED_BLOCK") || rel.getType().name().endsWith("_BED")) {
                                otherHalf = rel; break;
                            }
                        }

                        if (plugin.partyFFAManager != null && plugin.partyFFAManager.isPartyFFA(fight)) {
                            if (plugin.partyFFAManager.isOwnBed(player, bedBlock, fight)) {
                                event.setCancelled(true);
                                plugin.sendCooldownMessage(player, "bed-break-self");
                                return;
                            }
                        } else if (isOwnBed(player, bedBlock, otherHalf, fight, isP1Team)) {
                            event.setCancelled(true);
                            plugin.sendCooldownMessage(player, "bed-break-self");
                            return;
                        }

                        String kitName = plugin.getKitName(player);
                        boolean isMlgRush = kitName != null && (kitName.equalsIgnoreCase("mlgrush")
                                || kitName.equalsIgnoreCase("mlgrushelo"));
                        if (isMlgRush) {

                            event.setCancelled(true);

                            if (mlgRushCooldown.containsKey(player.getUniqueId())
                                    && System.currentTimeMillis() - mlgRushCooldown.get(player.getUniqueId()) < 2000) {
                                return;
                            }
                            mlgRushCooldown.put(player.getUniqueId(), System.currentTimeMillis());

                            try {

                                Player opponent = null;
                                if (plugin.getMGetPlayersInFight() != null) {
                                    List<Player> fPlayers = (List<Player>) plugin.getMGetPlayersInFight().invoke(fight);
                                    if (fPlayers != null) {
                                        for (Player pInFight : fPlayers) {
                                            if (!pInFight.getUniqueId().equals(player.getUniqueId())) {
                                                opponent = pInFight;
                                                break;
                                            }
                                        }
                                    }
                                }
                                if (opponent == null && plugin.getMGetOpponents() != null) {
                                    List<String> ops = (List<String>) plugin.getMGetOpponents().invoke(fight, player);
                                    if (ops != null && !ops.isEmpty()) {
                                        opponent = Bukkit.getPlayer(ops.get(0));
                                    }
                                }

                                if (opponent != null) {

                                    plugin.sendTitle(player,   "§a§lBED DESTROYED!", "§fYou scored a point!", 5, 30, 10);
                                    plugin.sendTitle(opponent, "§c§lBED DESTROYED!", "§f" + player.getName() + " scored a point!", 5, 30, 10);

                                    plugin.mlgRushBedDeaths.add(opponent.getUniqueId());
                                    opponent.setNoDamageTicks(0);
                                    opponent.setHealth(0.1);
                                    opponent.damage(10000.0, player);
                                    Player finalOpponent = opponent;
                                    Object finalFight    = fight;
                                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                        try {
                                            if (plugin.isInFight(finalOpponent) && plugin.getMHandleDeath() != null) {
                                                plugin.getMHandleDeath().invoke(finalFight, finalOpponent);
                                            }
                                        } catch (Exception ignored) {}
                                    }, 2L);
                                }

                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            return;
                        }

                        if (otherHalf != null) {
                            processedBedHalves.add(otherHalf.getLocation());
                            if (!event.isCancelled()) {

                                BlockBreakEvent fakeEvent = new BlockBreakEvent(otherHalf, player);
                                Bukkit.getPluginManager().callEvent(fakeEvent);
                            }
                            String ot = otherHalf.getType().name();
                            if (ot.contains("BED_BLOCK") || ot.endsWith("_BED")) {
                                otherHalf.setType(Material.AIR);
                            }
                            processedBedHalves.remove(otherHalf.getLocation());
                        }

                        if (event.isCancelled()) {

                            String mt = bedBlock.getType().name();
                            if (mt.contains("BED_BLOCK") || mt.endsWith("_BED")) {
                                bedBlock.setType(Material.AIR);
                            }
                        }

                        boolean isPartyFFAFight = plugin.partyFFAManager != null
                                && plugin.partyFFAManager.isPartyFFA(fight);
                        if (isPartyFFAFight) {

                            plugin.partyFFAManager.handleBedBreak(bedBlock, fight);
                        } else if (plugin.getClsAbstractFight() != null
                                && plugin.getClsAbstractFight().isInstance(fight)
                                && plugin.getFBed1Broken() != null && plugin.getFBed2Broken() != null) {
                            try {
                                if (isP1Team) plugin.getFBed2Broken().set(fight, true);
                                else plugin.getFBed1Broken().set(fight, true);
                            } catch (Exception ex) {}
                        }

                        String msg = plugin.getMsg("bed-destroy");
                        if (msg != null && !msg.isEmpty()) {
                            msg = msg.replace("<player>", player.getDisplayName()).replace("<color>", playerColorCode).replace("<bed_color>", enemyColorCode).replace("<enemy_team>", enemyTeamFullName).replace("<player_team>", playerTeamFullName);

                            List<Player> fightPlayers = new ArrayList<>();
                            if (plugin.getMGetPlayersInFight() != null) {
                                try { List<Player> temp = (List<Player>) plugin.getMGetPlayersInFight().invoke(fight); if (temp != null) fightPlayers.addAll(temp); } catch (Exception ignored) {}
                            }
                            if (fightPlayers.isEmpty() && plugin.getMGetPlayers() != null) {
                                try { List<Player> temp = (List<Player>) plugin.getMGetPlayers().invoke(fight); if (temp != null) fightPlayers.addAll(temp); } catch (Exception ignored) {}
                            }
                            if (fightPlayers.isEmpty()) {
                                for (Player p : Bukkit.getOnlinePlayers()) {
                                    if (plugin.isInFight(p) && fight.equals(plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), p))) fightPlayers.add(p);
                                }
                            }

                            for (Player p : fightPlayers) {
                                p.sendMessage(msg);
                                boolean pIsP1Team = false;
                                if (plugin.partySplitManager != null && plugin.partySplitManager.isPartySplit(fight)) {
                                    pIsP1Team = plugin.partySplitManager.isInTeam1(p, fight);
                                } else if (plugin.partyVsPartyManager != null && plugin.partyVsPartyManager.isPartyVsParty(fight)) {
                                    pIsP1Team = plugin.partyVsPartyManager.isInParty1(p, fight);
                                } else {
                                    if (plugin.getMPlayersAreTeammates() != null) {
                                        try { pIsP1Team = (boolean) plugin.getMPlayersAreTeammates().invoke(fight, player, p)
                                                ? isP1Team : !isP1Team; } catch (Exception ignored) {}
                                    }
                                    if (p.getUniqueId().equals(player.getUniqueId())) pIsP1Team = isP1Team;
                                }
                                boolean isVictim = (isP1Team && !pIsP1Team) || (!isP1Team && pIsP1Team);
                                if (isVictim && plugin.getBedDestroyTitleManager() != null) plugin.getBedDestroyTitleManager().sendBedDestroyTitle(p);
                            }
                        }
                    }
                }
            } catch (Exception e) {}
        }
    }

    private boolean isOwnBed(Player player, Block bedBlock, Block otherHalf, Object fight, boolean isP1Team) {
        try {
            Object arena = (plugin.getMGetArena() != null) ? plugin.getMGetArena().invoke(fight) : null;
            if (arena != null) {
                Location loc1 = (Location) arena.getClass().getMethod("getLoc1").invoke(arena);
                Location loc2 = (Location) arena.getClass().getMethod("getLoc2").invoke(arena);
                if (loc1 != null && loc2 != null) {
                    double bedDist1 = bedBlock.getLocation().distanceSquared(loc1);
                    double bedDist2 = bedBlock.getLocation().distanceSquared(loc2);
                    boolean bedNearSpawn1 = bedDist1 < bedDist2;

                    Location playerSpawn = plugin.arenaSpawnLocations.get(player.getUniqueId());
                    if (playerSpawn != null) {
                        double spDist1 = playerSpawn.distanceSquared(loc1);
                        double spDist2 = playerSpawn.distanceSquared(loc2);
                        boolean playerNearSpawn1 = spDist1 < spDist2;
                        return bedNearSpawn1 == playerNearSpawn1;
                    }

                    return (isP1Team && bedNearSpawn1) || (!isP1Team && !bedNearSpawn1);
                }
            }
        } catch (Exception ignored) {}

        String colorCode = plugin.getTeamColorCode(player, fight);
        if (checkBlockColorMatch(bedBlock.getRelative(BlockFace.DOWN), colorCode)) return true;
        if (otherHalf != null && checkBlockColorMatch(otherHalf.getRelative(BlockFace.DOWN), colorCode)) return true;
        return false;
    }

    private boolean checkBlockColorMatch(Block block, String colorCode) {
        if (block == null) return false;
        String type = block.getType().name();
        if (type.contains("WOOL") || type.contains("CLAY") || type.contains("GLASS") || type.contains("TERRACOTTA")) {
            try {
                @SuppressWarnings("deprecation")
                byte data = block.getData();
                boolean isBlue = colorCode.equals("§9") || colorCode.equals("§b");
                boolean isRed = colorCode.equals("§c") || colorCode.equals("§d");
                if (isBlue && (data == 11 || data == 3 || data == 9)) return true;
                if (isRed && (data == 14 || data == 6 || data == 10)) return true;
            } catch (Exception ignored) {}
        }
        return false;
    }
}
