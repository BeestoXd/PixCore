package com.pixra.pixcore.feature.bed;

import com.pixra.pixcore.PixCore;
import com.pixra.pixcore.support.BlockCompatibility;
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
import org.bukkit.Location;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

public class BedListener implements Listener {

    private static final long BED_BREAK_DEDUP_WINDOW_MS = 1500L;

    private final PixCore plugin;

    private final Map<String, Long> recentlyHandledBeds = new HashMap<>();
    private final Map<UUID, Long> mlgRushCooldown = new HashMap<>();

    public BedListener(PixCore plugin) {
        this.plugin = plugin;
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
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
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
                    plugin.sendCooldownMessage(player, "bed-break-self");
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
                    plugin.sendCooldownMessage(player, "bed-break-self");
                }

                if (!event.isCancelled()) {
                    String kitName = resolveKitName(player, fight);
                    if (kitName != null && (kitName.equalsIgnoreCase("mlgrush")
                            || kitName.equalsIgnoreCase("mlgrushelo"))) {
                        if (handleEarlyPartyMlgRushBedBreak(event, player, fight, kitName, bedBlock, otherHalf)) {
                            return;
                        }
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
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.isHooked()) return;

        Player player = event.getPlayer();
        Block bedBlock = event.getBlock();
        String typeName = bedBlock.getType().name();

        if (typeName.contains("BED_BLOCK") || typeName.endsWith("_BED")) {
            try {
                if (plugin.isInFight(player)) {
                    Object fight = plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), player);
                    if (fight != null) {
                        List<Block> bedCluster = findConnectedBedCluster(bedBlock);
                        Block otherHalf = findBedHalf(bedBlock, bedCluster);
                        if (isRecentlyHandledBedCluster(bedCluster)) {
                            event.setCancelled(true);
                            forceClearBedCluster(bedCluster, bedBlock, true);
                            return;
                        }

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

                        if (plugin.getMatchListener() != null) {
                            plugin.getMatchListener().rememberBrokenBedForFight(fight, bedBlock, otherHalf);
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

                        String kitName = resolveKitName(player, fight);
                        boolean isMlgRush = kitName != null && (kitName.equalsIgnoreCase("mlgrush")
                                || kitName.equalsIgnoreCase("mlgrushelo"));
                        if (isMlgRush) {

                            event.setCancelled(true);

                            if (mlgRushCooldown.containsKey(player.getUniqueId())
                                    && System.currentTimeMillis() - mlgRushCooldown.get(player.getUniqueId()) < 2000) {
                                return;
                            }
                            mlgRushCooldown.put(player.getUniqueId(), System.currentTimeMillis());
                            markBedHandled(bedCluster);
                            forceClearBedCluster(bedCluster, bedBlock, true);

                            try {
                                if (plugin.partySplitManager != null
                                        && plugin.partySplitManager.isPartySplit(fight)
                                        && plugin.partySplitManager.handleRoundScoredBedBreak(player, fight, kitName)) {
                                    return;
                                }
                                if (plugin.partyVsPartyManager != null
                                        && plugin.partyVsPartyManager.isPartyVsParty(fight)
                                        && plugin.partyVsPartyManager.handleRoundScoredBedBreak(player, fight, kitName)) {
                                    return;
                                }

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
                                    plugin.sendTitle(opponent, "§c§lBED DESTROYED!", "§f" + plugin.getEffectivePlayerName(player) + " scored a point!", 5, 30, 10);

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

                        // Handle the bed break ourselves so 1.8 does not leave an orphan HEAD/FOOT
                        // that only disappears after a delayed physics update.
                        event.setCancelled(true);
                        markBedHandled(bedCluster);
                        forceClearBedCluster(bedCluster, bedBlock, true);

                        boolean isPartyFFAFight = plugin.partyFFAManager != null
                                && plugin.partyFFAManager.isPartyFFA(fight);
                        if (isPartyFFAFight) {

                            plugin.partyFFAManager.handleBedBreak(bedBlock, fight);
                        } else {
                            // Set reflection flags on StrikePractice's fight object
                            if (plugin.getClsAbstractFight() != null
                                    && plugin.getClsAbstractFight().isInstance(fight)
                                    && plugin.getFBed1Broken() != null && plugin.getFBed2Broken() != null) {
                                try {
                                    if (isP1Team) plugin.getFBed2Broken().set(fight, true);
                                    else plugin.getFBed1Broken().set(fight, true);
                                } catch (Exception ex) {}
                            }
                            // Track the victim (whose bed was broken) independently of reflection
                            try {
                                Player victim = null;
                                if (plugin.getMGetPlayersInFight() != null) {
                                    List<Player> fPlayers = (List<Player>) plugin.getMGetPlayersInFight().invoke(fight);
                                    if (fPlayers != null) {
                                        for (Player fp : fPlayers) {
                                            if (fp != null && !fp.getUniqueId().equals(player.getUniqueId())) {
                                                victim = fp;
                                                break;
                                            }
                                        }
                                    }
                                }
                                if (victim == null && plugin.getMGetOpponents() != null) {
                                    List<?> oppList = (List<?>) plugin.getMGetOpponents().invoke(fight, player);
                                    if (oppList != null) {
                                        for (Object obj : oppList) {
                                            Player fp = null;
                                            if (obj instanceof Player) fp = (Player) obj;
                                            else if (obj instanceof String) fp = Bukkit.getPlayer((String) obj);
                                            if (fp != null) { victim = fp; break; }
                                        }
                                    }
                                }
                                if (victim != null) {
                                    plugin.bedFightBedBrokenPlayers.add(victim.getUniqueId());
                                }
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
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    private String resolveKitName(Player player, Object fight) {
        String kitName = player != null ? plugin.getKitName(player) : null;
        if ((kitName == null || kitName.isEmpty()) && fight != null) {
            try {
                Object kit = fight.getClass().getMethod("getKit").invoke(fight);
                if (kit != null) {
                    kitName = (String) kit.getClass().getMethod("getName").invoke(kit);
                }
            } catch (Exception ignored) {}
        }
        return kitName;
    }

    private boolean handleEarlyPartyMlgRushBedBreak(BlockBreakEvent event, Player player, Object fight,
                                                    String kitName, Block bedBlock, Block otherHalf) {
        if (fight == null || player == null) {
            return false;
        }

        boolean isPartySplitFight = plugin.partySplitManager != null && plugin.partySplitManager.isPartySplit(fight);
        boolean isPartyVsPartyFight = plugin.partyVsPartyManager != null && plugin.partyVsPartyManager.isPartyVsParty(fight);
        if (!isPartySplitFight && !isPartyVsPartyFight) {
            return false;
        }

        event.setCancelled(true);

        long now = System.currentTimeMillis();
        Long lastBreak = mlgRushCooldown.get(player.getUniqueId());
        if (lastBreak != null && now - lastBreak < 2000L) {
            return true;
        }
        mlgRushCooldown.put(player.getUniqueId(), now);

        if (plugin.getMatchListener() != null) {
            plugin.getMatchListener().rememberBrokenBedForFight(fight, bedBlock, otherHalf);
        }
        List<Block> bedCluster = findConnectedBedCluster(bedBlock);
        markBedHandled(bedCluster);
        forceClearBedCluster(bedCluster, bedBlock, true);
        if (plugin.getMatchListener() != null) {
            plugin.getMatchListener().restoreFightBedsForRound(fight, 1L);
        }

        if (isPartySplitFight) {
            return plugin.partySplitManager.handleRoundScoredBedBreak(player, fight, kitName);
        }
        return plugin.partyVsPartyManager.handleRoundScoredBedBreak(player, fight, kitName);
    }

    private void clearBedBlock(Block block) {
        if (block == null) {
            return;
        }

        String typeName = block.getType().name();
        if (typeName.contains("BED_BLOCK") || typeName.endsWith("_BED")) {
            BlockCompatibility.setBlockType(block, Material.AIR, false);
        }
    }

    private void forceClearBedCluster(List<Block> bedCluster, Block brokenBlock, boolean clearBrokenHalfImmediately) {
        List<Location> locations = new ArrayList<>();
        if (bedCluster != null) {
            for (Block block : bedCluster) {
                if (block == null) {
                    continue;
                }
                Location location = block.getLocation();
                if (!containsLocation(locations, location)) {
                    locations.add(location);
                }
                if (clearBrokenHalfImmediately || !sameBlock(block, brokenBlock)) {
                    clearBedBlock(block);
                }
            }
        }

        if (locations.isEmpty() && brokenBlock != null) {
            locations.add(brokenBlock.getLocation());
        }

        if (locations.isEmpty()) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Location location : locations) {
                clearBedAt(location);
            }
        });
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Location location : locations) {
                clearBedAt(location);
            }
        }, 2L);
    }

    private void clearBedAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        clearBedBlock(location.getBlock());
    }

    private Block findBedHalf(Block bedBlock, List<Block> bedCluster) {
        if (bedBlock == null || bedCluster == null) {
            return null;
        }

        for (Block block : bedCluster) {
            if (!sameBlock(block, bedBlock)) {
                return block;
            }
        }
        return null;
    }

    private List<Block> findConnectedBedCluster(Block origin) {
        List<Block> cluster = new ArrayList<>();
        if (origin == null) {
            return cluster;
        }

        Deque<Block> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(origin);

        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        while (!queue.isEmpty() && cluster.size() < 8) {
            Block block = queue.poll();
            String key = getBlockKey(block);
            if (key == null || !visited.add(key)) {
                continue;
            }
            if (!isBedBlock(block)) {
                continue;
            }

            cluster.add(block);
            for (BlockFace face : faces) {
                queue.add(block.getRelative(face));
            }
        }

        return cluster;
    }

    private String getBlockKey(Block block) {
        if (block == null || block.getWorld() == null) {
            return null;
        }
        Location loc = block.getLocation();
        return block.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    private boolean isRecentlyHandledBedCluster(List<Block> bedCluster) {
        if (bedCluster == null || bedCluster.isEmpty()) {
            return false;
        }

        long now = System.currentTimeMillis();
        recentlyHandledBeds.entrySet().removeIf(entry -> now - entry.getValue() > BED_BREAK_DEDUP_WINDOW_MS);

        for (Block block : bedCluster) {
            String blockKey = getBlockKey(block);
            if (blockKey == null) {
                continue;
            }
            Long handledAt = recentlyHandledBeds.get(blockKey);
            if (handledAt != null && now - handledAt <= BED_BREAK_DEDUP_WINDOW_MS) {
                return true;
            }
        }
        return false;
    }

    private void markBedHandled(List<Block> bedCluster) {
        if (bedCluster == null || bedCluster.isEmpty()) {
            return;
        }

        long handledAt = System.currentTimeMillis();
        for (Block block : bedCluster) {
            String blockKey = getBlockKey(block);
            if (blockKey != null) {
                recentlyHandledBeds.put(blockKey, handledAt);
            }
        }
    }

    private boolean containsLocation(List<Location> locations, Location target) {
        if (target == null) {
            return false;
        }
        for (Location location : locations) {
            if (location != null
                    && location.getWorld() != null
                    && target.getWorld() != null
                    && location.getWorld().getName().equals(target.getWorld().getName())
                    && location.getBlockX() == target.getBlockX()
                    && location.getBlockY() == target.getBlockY()
                    && location.getBlockZ() == target.getBlockZ()) {
                return true;
            }
        }
        return false;
    }

    private boolean sameBlock(Block first, Block second) {
        String firstKey = getBlockKey(first);
        String secondKey = getBlockKey(second);
        return firstKey != null && firstKey.equals(secondKey);
    }

    private boolean isBedBlock(Block block) {
        if (block == null) {
            return false;
        }
        String typeName = block.getType().name();
        return typeName.contains("BED_BLOCK") || typeName.endsWith("_BED");
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
