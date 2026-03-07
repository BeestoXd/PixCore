package com.pixra.pixCore.listeners;

import com.pixra.pixCore.PixCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MatchListener implements Listener {

    private static class BedPair {
        public final Location footLoc;
        public final Location headLoc;
        public final byte footData;
        public final byte headData;

        public BedPair(Location footLoc, Location headLoc, byte footData, byte headData) {
            this.footLoc = footLoc;
            this.headLoc = headLoc;
            this.footData = footData;
            this.headData = headData;
        }
    }

    private final PixCore plugin;
    private final Map<String, List<BedPair>> cachedArenaBeds = new HashMap<>();

    private File customBedsFile;
    private org.bukkit.configuration.file.FileConfiguration customBedsConfig;
    private final List<BedPair> persistentBeds = new ArrayList<>();

    private final Set<Object> startedFights = new HashSet<>();
    private final Map<Object, Long> fightCountdownCooldown = new HashMap<>();
    private final Set<Object> partyCountdownFights = new HashSet<>();

    private final Set<UUID> matchEndedPlayers = new HashSet<>();
    private final Map<UUID, Location> pendingHubTeleports = new HashMap<>();
    private final Map<UUID, Set<UUID>> matchPlayerGroups = new HashMap<>();
    private final Map<UUID, Long> matchEndTimes = new HashMap<>();

    public MatchListener(PixCore plugin) {
        this.plugin = plugin;
        loadCustomBeds();
        registerStrikePracticeEvents();
    }

    private void loadCustomBeds() {
        customBedsFile = new File(plugin.getDataFolder(), "custombeds.yml");
        if (!customBedsFile.exists()) {
            try {
                customBedsFile.createNewFile();
            } catch (Exception e) {
            }
        }
        customBedsConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(customBedsFile);

        persistentBeds.clear();
        if (customBedsConfig.contains("beds")) {
            for (String key : customBedsConfig.getConfigurationSection("beds").getKeys(false)) {
                String path = "beds." + key;
                Location footLoc = (Location) customBedsConfig.get(path + ".footLoc");
                Location headLoc = (Location) customBedsConfig.get(path + ".headLoc");
                byte footData = (byte) customBedsConfig.getInt(path + ".footData");
                byte headData = (byte) customBedsConfig.getInt(path + ".headData");
                if (footLoc != null && headLoc != null) {
                    persistentBeds.add(new BedPair(footLoc, headLoc, footData, headData));
                }
            }
        }
    }

    private void saveCustomBed(BedPair bed) {
        persistentBeds.add(bed);
        String key = java.util.UUID.randomUUID().toString();
        String path = "beds." + key;
        customBedsConfig.set(path + ".footLoc", bed.footLoc);
        customBedsConfig.set(path + ".headLoc", bed.headLoc);
        customBedsConfig.set(path + ".footData", bed.footData);
        customBedsConfig.set(path + ".headData", bed.headData);
        try {
            customBedsConfig.save(customBedsFile);
        } catch (Exception e) {
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpecialBedPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        Player player = event.getPlayer();
        org.bukkit.inventory.ItemStack item = event.getItemInHand();

        if (item != null && item.getType().name().contains("BED") && item.hasItemMeta()
                && item.getItemMeta().hasDisplayName()) {
            if (item.getItemMeta().getDisplayName().contains("Arena Bed Fixer")) {
                Block foot = event.getBlockPlaced();

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (foot.getType().name().contains("BED")) {
                            byte footData = getBlockDataSafe(foot);
                            int dir = footData & 3;
                            int dx = 0, dz = 0;
                            if (dir == 0)
                                dz = 1;
                            else if (dir == 1)
                                dx = -1;
                            else if (dir == 2)
                                dz = -1;
                            else if (dir == 3)
                                dx = 1;

                            Block head = foot.getRelative(dx, 0, dz);
                            if (head.getType().name().contains("BED")) {
                                byte headData = getBlockDataSafe(head);
                                BedPair newBed = new BedPair(foot.getLocation(), head.getLocation(), footData,
                                        headData);
                                saveCustomBed(newBed);
                                player.sendMessage(ChatColor.GREEN
                                        + "[PixCore] Custom Bed saved successfully! It will perfectly restore in this arena.");
                            } else {
                                player.sendMessage(ChatColor.RED
                                        + "[PixCore] Failed to save bed. Make sure it is placed completely.");
                            }
                        }
                    }
                }.runTaskLater(plugin, 2L);
            }
        }
    }

    private void registerStrikePracticeEvents() {
        try {
            Class<? extends Event> duelStartClass = (Class<? extends Event>) Class
                    .forName("ga.strikepractice.events.DuelStartEvent");
            Bukkit.getPluginManager().registerEvent(duelStartClass, this, EventPriority.MONITOR,
                    (listener, event) -> handleDuelStart(event), plugin);

            Class<? extends Event> duelEndClass = (Class<? extends Event>) Class
                    .forName("ga.strikepractice.events.DuelEndEvent");
            Bukkit.getPluginManager().registerEvent(duelEndClass, this, EventPriority.LOWEST,
                    (listener, event) -> markDuelEndPlayers(event), plugin);
            Bukkit.getPluginManager().registerEvent(duelEndClass, this, EventPriority.MONITOR,
                    (listener, event) -> handleDuelEnd(event), plugin);

            Class<? extends Event> roundEndClass = (Class<? extends Event>) Class
                    .forName("ga.strikepractice.events.RoundEndEvent");
            Bukkit.getPluginManager().registerEvent(roundEndClass, this, EventPriority.MONITOR,
                    (listener, event) -> handleRoundEnd(event), plugin);

            try {
                Class<? extends Event> kitSelectClass = (Class<? extends Event>) Class
                        .forName("ga.strikepractice.events.KitSelectEvent");
                Bukkit.getPluginManager().registerEvent(kitSelectClass, this, EventPriority.MONITOR,
                        (listener, event) -> handlePartyKitSelect(event), plugin);
            } catch (Exception ignored) {
                plugin.getLogger()
                        .warning("[MatchListener] Could not register KitSelectEvent — party countdown may not work.");
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Could not register StrikePractice custom events.");
        }
    }

    @SuppressWarnings("unchecked")
    private void handlePartyKitSelect(Event event) {
        if (!plugin.startCountdownEnabled)
            return;
        try {
            Player player = (Player) event.getClass().getMethod("getPlayer").invoke(event);
            if (player == null || !player.isOnline())
                return;
            if (!plugin.isHooked() || !plugin.isInFight(player))
                return;

            Object fight = plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), player);
            if (fight == null)
                return;

            boolean isParty = (plugin.partySplitManager != null && plugin.partySplitManager.isPartySplit(fight))
                    || (plugin.partyVsPartyManager != null && plugin.partyVsPartyManager.isPartyVsParty(fight));
            if (!isParty)
                return;

            if (partyCountdownFights.contains(fight))
                return;
            partyCountdownFights.add(fight);
            fightCountdownCooldown.put(fight, System.currentTimeMillis());

            if (!startedFights.contains(fight)) {
                startedFights.add(fight);
                for (Player pp : resolveAllFightPlayers(fight, new ArrayList<>())) {
                    if (pp != null)
                        plugin.playerMatchKills.put(pp.getUniqueId(), 0);
                }
            }

            final Object finalFight = fight;
            final List<Player> allPlayers = resolveAllFightPlayers(fight, new ArrayList<>());

            String partyKit = null;
            if (!allPlayers.isEmpty() && allPlayers.get(0) != null) {
                partyKit = plugin.getKitName(allPlayers.get(0));
                if (partyKit == null) {
                    try {
                        Object k = fight.getClass().getMethod("getKit").invoke(fight);
                        if (k != null)
                            partyKit = (String) k.getClass().getMethod("getName").invoke(k);
                    } catch (Exception ignored) {
                    }
                }
            }
            startMatchCountdown(allPlayers, finalFight, true, partyKit);

            new BukkitRunnable() {
                @Override
                public void run() {
                    partyCountdownFights.remove(finalFight);
                }
            }.runTaskLater(plugin, 20L * 60 * 10);

        } catch (Exception ignored) {
        }
    }

    private byte getBlockDataSafe(Block block) {
        try {
            Method getData = block.getClass().getMethod("getData");
            return (byte) getData.invoke(block);
        } catch (Exception e) {
            return 0;
        }
    }

    private void placeBedRobust(Location footLoc, Location headLoc, byte footData, byte headData) {
        try {
            if (!footLoc.getChunk().isLoaded())
                footLoc.getChunk().load();
            if (!headLoc.getChunk().isLoaded())
                headLoc.getChunk().load();

            Block foot = footLoc.getBlock();
            Block head = headLoc.getBlock();

            org.bukkit.Material bedMat;
            try {
                bedMat = org.bukkit.Material.valueOf("BED_BLOCK");
            } catch (Exception e) {
                return;
            }

            try {
                Method setTypeIdAndData = Block.class.getMethod("setTypeIdAndData", int.class, byte.class,
                        boolean.class);
                Method getId = org.bukkit.Material.class.getMethod("getId");
                int bedId = (Integer) getId.invoke(bedMat);

                setTypeIdAndData.invoke(foot, 0, (byte) 0, false);
                setTypeIdAndData.invoke(head, 0, (byte) 0, false);

                setTypeIdAndData.invoke(foot, bedId, footData, false);
                setTypeIdAndData.invoke(head, bedId, headData, false);
                return;
            } catch (Exception legacyEx) {
            }

            foot.setType(org.bukkit.Material.AIR);
            head.setType(org.bukkit.Material.AIR);

            org.bukkit.block.BlockState footState = foot.getState();
            org.bukkit.block.BlockState headState = head.getState();

            footState.setType(bedMat);
            headState.setType(bedMat);

            try {
                Method setRawData = org.bukkit.block.BlockState.class.getMethod("setRawData", byte.class);
                setRawData.invoke(footState, footData);
                setRawData.invoke(headState, headData);
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            footState.update(true, false);
            headState.update(true, false);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMoveDuringCountdown(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!plugin.frozenPlayers.contains(player.getUniqueId()))
            return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null)
            return;
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ())
            return;
        event.setTo(new Location(from.getWorld(), from.getX(), to.getY(), from.getZ(), to.getYaw(), to.getPitch()));
    }

    @SuppressWarnings("unchecked")
    private String getPartyLeaderName(Object fight, Player referencePlayer) {
        if (referencePlayer == null)
            return null;
        try {
            if (plugin.partyVsPartyManager != null && plugin.partyVsPartyManager.isPartyVsParty(fight)) {
                boolean inParty1 = plugin.partyVsPartyManager.isInParty1(referencePlayer, fight);
                Method getPartyMethod = fight.getClass().getMethod(inParty1 ? "getParty1" : "getParty2");
                Object party = getPartyMethod.invoke(fight);
                if (party != null) {
                    for (String m : new String[] { "getLeader", "getLeaderName", "getLeaderUUID" }) {
                        try {
                            Object leader = party.getClass().getMethod(m).invoke(party);
                            if (leader instanceof Player)
                                return ((Player) leader).getName();
                            if (leader instanceof String)
                                return (String) leader;
                            if (leader instanceof java.util.UUID) {
                                Player lp = Bukkit.getPlayer((java.util.UUID) leader);
                                if (lp != null)
                                    return lp.getName();
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    List<Player> members = plugin.partyVsPartyManager.getParty1Players(fight);
                    if (!inParty1)
                        members = plugin.partyVsPartyManager.getParty2Players(fight);
                    if (!members.isEmpty())
                        return members.get(0).getName();
                }
            }

            if (plugin.partySplitManager != null && plugin.partySplitManager.isPartySplit(fight)) {
                boolean inTeam1 = plugin.partySplitManager.isInTeam1(referencePlayer, fight);
                List<Player> team = inTeam1
                        ? plugin.partySplitManager.getTeam1Players(fight)
                        : plugin.partySplitManager.getTeam2Players(fight);
                if (!team.isEmpty())
                    return team.get(0).getName();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public void handleDuelStart(Event event) {
        try {
            Object fight = event.getClass().getMethod("getFight").invoke(event);
            if (fight == null)
                return;

            if (plugin.duelScoreManager != null) {
                plugin.duelScoreManager.onFightStart(fight);
            }

            Object arena = (plugin.getMGetArena() != null) ? plugin.getMGetArena().invoke(fight) : null;
            List<Player> players = new ArrayList<>();

            if (plugin.getMGetPlayersInFight() != null) {
                try {
                    List<Player> found = (List<Player>) plugin.getMGetPlayersInFight().invoke(fight);
                    if (found != null)
                        players.addAll(found);
                } catch (Exception e) {
                }
            }

            if (players.isEmpty() && plugin.getMGetPlayers() != null) {
                try {
                    List<Player> found = (List<Player>) plugin.getMGetPlayers().invoke(fight);
                    if (found != null)
                        players.addAll(found);
                } catch (Exception e) {
                }
            }

            if (players.isEmpty()) {
                try {
                    List<Player> found = (List<Player>) fight.getClass().getMethod("getPlayers").invoke(fight);
                    if (found != null)
                        players.addAll(found);
                } catch (NoSuchMethodException e) {
                    if (plugin.getMGetFirstPlayer() != null)
                        players.add((Player) plugin.getMGetFirstPlayer().invoke(fight));
                    if (plugin.getMGetSecondPlayer() != null)
                        players.add((Player) plugin.getMGetSecondPlayer().invoke(fight));
                }
            }
            players.removeIf(p -> p == null);

            if (!players.isEmpty()) {

                String kitName = plugin.getKitName(players.get(0));
                if (kitName == null) {
                    try {
                        Object kit = fight.getClass().getMethod("getKit").invoke(fight);
                        if (kit != null)
                            kitName = (String) kit.getClass().getMethod("getName").invoke(kit);
                    } catch (Exception e) {
                    }
                }

                boolean isBridge = false;
                if (kitName != null) {
                    String kitLower = kitName.toLowerCase();
                    if (kitLower.contains("bridge") || kitLower.contains("thebridge")) {
                        isBridge = true;
                    }
                }

                boolean isFirstRound = !startedFights.contains(fight);
                if (isFirstRound) {
                    startedFights.add(fight);
                    for (Player p : players)
                        plugin.playerMatchKills.put(p.getUniqueId(), 0);
                }

                final boolean isPartyFight = (plugin.partySplitManager != null
                        && plugin.partySplitManager.isPartySplit(fight))
                        || (plugin.partyVsPartyManager != null && plugin.partyVsPartyManager.isPartyVsParty(fight));

                if (isPartyFight) {
                    final Object fightRef = fight;
                    final List<Player> seedPlayers = new ArrayList<>(players);
                    Runnable storeSpawns = () -> {
                        for (Player pp : resolveAllFightPlayers(fightRef, seedPlayers)) {
                            if (pp != null && pp.isOnline()
                                    && !plugin.arenaSpawnLocations.containsKey(pp.getUniqueId())) {
                                plugin.arenaSpawnLocations.put(pp.getUniqueId(), pp.getLocation().clone());
                            }
                        }
                    };
                    storeSpawns.run();
                    for (long d : new long[] { 3L, 8L, 20L }) {
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                storeSpawns.run();
                            }
                        }.runTaskLater(plugin, d);
                    }
                }

                long now = System.currentTimeMillis();
                long lastTime = fightCountdownCooldown.getOrDefault(fight, 0L);

                if (now - lastTime > 4000L) {
                    if (isFirstRound || !isBridge) {
                        if (!isPartyFight && (arena != null || isBridge)) {
                            fightCountdownCooldown.put(fight, now);
                            startMatchCountdown(players, fight, false, kitName);
                        }
                    }
                }

                if (plugin.getMSetBedwars() != null && kitName != null) {
                    try {
                        Player p = players.get(0);
                        boolean isBed = false;

                        List<String> bedRestoreKits = plugin.getConfig().getStringList("settings.bed-restore-kits");
                        if (bedRestoreKits != null && !bedRestoreKits.isEmpty()) {
                            for (String kit : bedRestoreKits) {
                                if (kit.equalsIgnoreCase(kitName)) {
                                    isBed = true;
                                    break;
                                }
                            }
                        } else {
                            isBed = kitName.toLowerCase().contains("bed") || kitName.toLowerCase().contains("fireball");
                        }

                        if (!isBed && plugin.respawnChatCountdownKits != null) {
                            for (String k : plugin.respawnChatCountdownKits)
                                if (k.equalsIgnoreCase(kitName))
                                    isBed = true;
                        }

                        if (isBed) {
                            Object kit = plugin.getStrikePracticeAPI().getClass().getMethod("getKit", Player.class)
                                    .invoke(plugin.getStrikePracticeAPI(), p);
                            plugin.getMSetBedwars().invoke(kit, true);

                            if (arena != null) {
                                try {
                                    String arenaName = (String) arena.getClass().getMethod("getName").invoke(arena);
                                    if (!cachedArenaBeds.containsKey(arenaName)) {
                                        saveArenaBeds(arenaName, arena);
                                    }

                                    forceFixBeds(arenaName, fight, 10L);

                                } catch (Exception ex) {
                                }
                            }
                        }
                    } catch (Exception ex) {
                    }
                }
            }
        } catch (Exception e) {
        }
    }

    private void saveArenaBeds(String arenaName, Object arena) {
        if (plugin.arenaBoundaryManager == null)
            return;
        Location min = plugin.arenaBoundaryManager.getCorner1(arena);
        Location max = plugin.arenaBoundaryManager.getCorner2(arena);
        if (min == null || max == null)
            return;

        int minX = Math.min(min.getBlockX(), max.getBlockX());
        int maxX = Math.max(min.getBlockX(), max.getBlockX());
        int minY = Math.min(min.getBlockY(), max.getBlockY());
        int maxY = Math.max(min.getBlockY(), max.getBlockY());
        int minZ = Math.min(min.getBlockZ(), max.getBlockZ());
        int maxZ = Math.max(min.getBlockZ(), max.getBlockZ());

        Map<String, BedPair> bedMap = new HashMap<>();
        org.bukkit.World world = min.getWorld();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block b = world.getBlockAt(x, y, z);
                    String typeName = b.getType().name();

                    if (typeName.contains("BED_BLOCK") || typeName.endsWith("_BED")) {
                        byte data = getBlockDataSafe(b);
                        int dir = data & 3;
                        boolean isHead = (data & 8) == 8;

                        int dx = 0, dz = 0;
                        if (dir == 0)
                            dz = 1;
                        else if (dir == 1)
                            dx = -1;
                        else if (dir == 2)
                            dz = -1;
                        else if (dir == 3)
                            dx = 1;

                        Location footLoc, headLoc;
                        if (isHead) {
                            headLoc = b.getLocation();
                            footLoc = headLoc.clone().add(-dx, 0, -dz);
                        } else {
                            footLoc = b.getLocation();
                            headLoc = footLoc.clone().add(dx, 0, dz);
                        }

                        String key = footLoc.toString();
                        if (!bedMap.containsKey(key)) {
                            bedMap.put(key, new BedPair(footLoc, headLoc, (byte) dir, (byte) (dir | 8)));
                        }
                    }
                }
            }
        }

        if (!bedMap.isEmpty()) {
            cachedArenaBeds.put(arenaName, new ArrayList<>(bedMap.values()));
        }
    }

    private void forceFixBeds(String arenaName, Object fight, long delayTicks) {
        new BukkitRunnable() {
            @Override
            public void run() {
                executeBedRestore(arenaName, fight);
            }
        }.runTaskLater(plugin, delayTicks);

        new BukkitRunnable() {
            @Override
            public void run() {
                executeBedRestore(arenaName, fight);
            }
        }.runTaskLater(plugin, delayTicks + 40L);
    }

    private void executeBedRestore(String arenaName, Object fight) {
        List<BedPair> bedsToRestore = new ArrayList<>();
        boolean usingPersistent = false;

        try {
            Object arena = (plugin.getMGetArena() != null && fight != null) ? plugin.getMGetArena().invoke(fight)
                    : null;
            if (arena != null && plugin.arenaBoundaryManager != null) {
                Location min = plugin.arenaBoundaryManager.getCorner1(arena);
                Location max = plugin.arenaBoundaryManager.getCorner2(arena);
                if (min != null && max != null) {
                    int minX = Math.min(min.getBlockX(), max.getBlockX());
                    int maxX = Math.max(min.getBlockX(), max.getBlockX());
                    int minZ = Math.min(min.getBlockZ(), max.getBlockZ());
                    int maxZ = Math.max(min.getBlockZ(), max.getBlockZ());

                    for (BedPair b : persistentBeds) {
                        Location loc = b.footLoc;
                        if (loc.getWorld().getName().equals(min.getWorld().getName()) &&
                                loc.getBlockX() >= minX && loc.getBlockX() <= maxX &&
                                loc.getBlockZ() >= minZ && loc.getBlockZ() <= maxZ) {

                            bedsToRestore.add(b);
                            usingPersistent = true;
                        }
                    }
                }
            }
        } catch (Exception e) {
        }

        if (!usingPersistent && cachedArenaBeds.containsKey(arenaName)) {
            bedsToRestore.addAll(cachedArenaBeds.get(arenaName));
        }

        for (BedPair bed : bedsToRestore) {
            placeBedRobust(bed.footLoc, bed.headLoc, bed.footData, bed.headData);
        }

        if (fight != null && plugin.getClsAbstractFight() != null && plugin.getClsAbstractFight().isInstance(fight)) {
            try {
                if (plugin.getFBed1Broken() != null)
                    plugin.getFBed1Broken().set(fight, false);
                if (plugin.getFBed2Broken() != null)
                    plugin.getFBed2Broken().set(fight, false);
            } catch (Exception ex) {
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Player> getPartyAllPlayers(Object fight) {
        if (plugin.partySplitManager != null && plugin.partySplitManager.isPartySplit(fight)) {
            return plugin.partySplitManager.getAllPlayers(fight);
        }
        if (plugin.partyVsPartyManager != null && plugin.partyVsPartyManager.isPartyVsParty(fight)) {
            return plugin.partyVsPartyManager.getAllPlayers(fight);
        }
        return new java.util.ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private List<Player> resolveAllFightPlayers(Object fight, List<Player> seed) {
        List<Player> result = new ArrayList<>();

        for (Player p : seed) {
            if (p != null && p.isOnline() && !result.contains(p))
                result.add(p);
        }

        for (Player p : getPartyAllPlayers(fight)) {
            if (p != null && p.isOnline() && !result.contains(p))
                result.add(p);
        }

        if (plugin.isHooked() && plugin.getMGetFight() != null && plugin.getStrikePracticeAPI() != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (result.contains(p))
                    continue;
                try {
                    if (!plugin.isInFight(p))
                        continue;
                    Object pFight = plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), p);
                    if (fight.equals(pFight))
                        result.add(p);
                } catch (Exception ignored) {
                }
            }
        }

        return result;
    }

    private void startMatchCountdown(List<Player> players, Object fight, boolean isPartyFight) {
        startMatchCountdown(players, fight, isPartyFight, null);
    }

    private void startMatchCountdown(List<Player> players, Object fight, boolean isPartyFight, String kitName) {
        final Object finalFight = fight;
        final boolean finalIsParty = isPartyFight;
        final String finalKit = kitName;

        final List<Player> countdownPlayers = finalIsParty
                ? resolveAllFightPlayers(fight, players)
                : new ArrayList<>(players);

        for (Player p : countdownPlayers) {
            if (p != null && p.isOnline()) {
                plugin.frozenPlayers.add(p.getUniqueId());
                if (plugin.hologramManager != null)
                    plugin.hologramManager.showForPlayer(p, finalKit);
            }
        }

        for (long delay : new long[] { 3L, 8L, 20L, 40L }) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (finalIsParty) {
                        for (Player lp : resolveAllFightPlayers(finalFight, countdownPlayers)) {
                            if (lp != null && lp.isOnline() && !countdownPlayers.contains(lp)) {
                                countdownPlayers.add(lp);
                                plugin.frozenPlayers.add(lp.getUniqueId());
                            }
                        }
                    }
                    for (Player fp : new ArrayList<>(countdownPlayers)) {
                        if (fp != null && fp.isOnline()) {
                            plugin.frozenPlayers.add(fp.getUniqueId());
                            if (plugin.hologramManager != null)
                                plugin.hologramManager.showForPlayer(fp, finalKit);
                            if (finalIsParty)
                                plugin.applyStartKit(fp, finalFight);
                            else
                                plugin.applyStartKit(fp);
                        }
                    }
                }
            }.runTaskLater(plugin, delay);
        }

        int maxSeconds = plugin.startCountdownDuration;
        new BukkitRunnable() {
            int current = maxSeconds;

            @Override
            public void run() {
                if (finalIsParty) {
                    for (Player lp : resolveAllFightPlayers(finalFight, countdownPlayers)) {
                        if (lp != null && lp.isOnline() && !countdownPlayers.contains(lp)) {
                            countdownPlayers.add(lp);
                            plugin.frozenPlayers.add(lp.getUniqueId());
                        }
                    }
                }

                if (current <= 0) {
                    for (Player p : new ArrayList<>(countdownPlayers)) {
                        if (p == null || !p.isOnline())
                            continue;
                        plugin.arenaSpawnLocations.put(p.getUniqueId(), p.getLocation().clone());
                        if (plugin.startMatchMessage != null && !plugin.startMatchMessage.isEmpty())
                            p.sendMessage(plugin.startMatchMessage);
                        if (plugin.startCountdownTitles != null && plugin.startCountdownTitles.containsKey(0))
                            plugin.sendTitle(p, plugin.startCountdownTitles.get(0), "", 0, 20, 10);
                        if (plugin.startCountdownSoundEnabled && plugin.startMatchSound != null)
                            p.playSound(p.getLocation(), plugin.startMatchSound, plugin.startCountdownVolume,
                                    plugin.startCountdownPitch);
                        plugin.frozenPlayers.remove(p.getUniqueId());
                        if (plugin.hologramManager != null)
                            plugin.hologramManager.hideForPlayer(p);
                        if (plugin.blockReplenishManager != null)
                            plugin.blockReplenishManager.scanPlayerInventory(p);
                    }
                    cancel();
                    return;
                }

                if (plugin.startCountdownEnabled) {
                    for (Player p : new ArrayList<>(countdownPlayers)) {
                        if (p == null || !p.isOnline())
                            continue;
                        if (plugin.startCountdownMessages != null)
                            p.sendMessage(plugin.startCountdownMessages.getOrDefault(current,
                                    ChatColor.RED + String.valueOf(current)));
                        if (plugin.startCountdownTitles != null && plugin.startCountdownTitles.containsKey(current))
                            plugin.sendTitle(p, plugin.startCountdownTitles.get(current), "", 0, 20, 0);
                        if (plugin.startCountdownSoundEnabled && plugin.startCountdownSounds != null) {
                            Sound s = plugin.startCountdownSounds.get(current);
                            if (s != null)
                                p.playSound(p.getLocation(), s, plugin.startCountdownVolume,
                                        plugin.startCountdownPitch);
                        }
                    }
                }
                current--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (!matchEndedPlayers.contains(uuid))
            return;

        if (pendingHubTeleports.containsKey(uuid)) {
            event.setCancelled(true);
            return;
        }

        Location dest = event.getTo();
        if (dest == null)
            return;

        event.setCancelled(true);

        long now = System.currentTimeMillis();
        Set<UUID> group = matchPlayerGroups.getOrDefault(uuid, new HashSet<>());
        if (group.isEmpty())
            group.add(uuid);

        for (UUID memberUUID : group) {
            if (!matchEndedPlayers.contains(memberUUID))
                continue;
            if (pendingHubTeleports.containsKey(memberUUID))
                continue;

            final Location hub = dest.clone();
            pendingHubTeleports.put(memberUUID, hub);

            long matchEndTime = matchEndTimes.getOrDefault(memberUUID, now);
            long elapsedTicks = (now - matchEndTime) / 50L;
            long delayTicks = Math.max(0L, 60L - elapsedTicks);

            final UUID finalMemberUUID = memberUUID;
            new BukkitRunnable() {
                @Override
                public void run() {
                    Player p = Bukkit.getPlayer(finalMemberUUID);
                    matchEndedPlayers.remove(finalMemberUUID);
                    Location hubLoc = pendingHubTeleports.remove(finalMemberUUID);
                    matchPlayerGroups.remove(finalMemberUUID);
                    matchEndTimes.remove(finalMemberUUID);
                    if (p != null && p.isOnline() && hubLoc != null) {
                        p.teleport(hubLoc);
                    }
                }
            }.runTaskLater(plugin, delayTicks);
        }
    }

    @SuppressWarnings("unchecked")
    private void markDuelEndPlayers(Event event) {
        try {
            final Object fight = event.getClass().getMethod("getFight").invoke(event);
            if (fight == null)
                return;

            List<Player> players = new ArrayList<>();
            if (plugin.getMGetPlayersInFight() != null) {
                try {
                    List<Player> temp = (List<Player>) plugin.getMGetPlayersInFight().invoke(fight);
                    if (temp != null)
                        players.addAll(temp);
                } catch (Exception ignored) {
                }
            }
            if (players.isEmpty() && plugin.getMGetPlayers() != null) {
                try {
                    List<Player> temp = (List<Player>) plugin.getMGetPlayers().invoke(fight);
                    if (temp != null)
                        players.addAll(temp);
                } catch (Exception ignored) {
                }
            }
            if (players.isEmpty()) {
                try {
                    Player w = (Player) event.getClass().getMethod("getWinner").invoke(event);
                    if (w != null)
                        players.add(w);
                } catch (Exception ignored) {
                }
                try {
                    Player l = (Player) event.getClass().getMethod("getLoser").invoke(event);
                    if (l != null)
                        players.add(l);
                } catch (Exception ignored) {
                }
            }

            long endTime = System.currentTimeMillis();
            Set<UUID> uuids = new HashSet<>();
            for (Player p : players) {
                if (p != null) {
                    uuids.add(p.getUniqueId());
                    matchEndedPlayers.add(p.getUniqueId());
                    matchEndTimes.put(p.getUniqueId(), endTime);
                }
            }
            for (UUID u : uuids) {
                matchPlayerGroups.put(u, new HashSet<>(uuids));
            }

            final List<Player> cleanup = new ArrayList<>(players);
            new BukkitRunnable() {
                @Override
                public void run() {
                    for (Player p : cleanup) {
                        if (p != null) {
                            UUID u = p.getUniqueId();
                            matchEndedPlayers.remove(u);
                            pendingHubTeleports.remove(u);
                            matchPlayerGroups.remove(u);
                            matchEndTimes.remove(u);
                        }
                    }
                }
            }.runTaskLater(plugin, 200L);
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    public void handleDuelEnd(Event event) {
        try {
            final Object fight = event.getClass().getMethod("getFight").invoke(event);

            Player winner = null;
            Player loser = null;
            try {
                winner = (Player) event.getClass().getMethod("getWinner").invoke(event);
            } catch (Exception ignored) {
            }
            try {
                loser = (Player) event.getClass().getMethod("getLoser").invoke(event);
            } catch (Exception ignored) {
            }

            List<Player> fightPlayers = new ArrayList<>();
            if (fight != null) {
                plugin.endedFightWinners.put(fight, winner != null ? winner.getUniqueId() : null);

                if (plugin.getMGetPlayersInFight() != null) {
                    try {
                        List<Player> temp = (List<Player>) plugin.getMGetPlayersInFight().invoke(fight);
                        if (temp != null)
                            fightPlayers.addAll(temp);
                    } catch (Exception ignored) {
                    }
                }
                if (fightPlayers.isEmpty() && plugin.getMGetPlayers() != null) {
                    try {
                        List<Player> temp = (List<Player>) plugin.getMGetPlayers().invoke(fight);
                        if (temp != null)
                            fightPlayers.addAll(temp);
                    } catch (Exception ignored) {
                    }
                }
                if (fightPlayers.isEmpty()) {
                    if (winner != null)
                        fightPlayers.add(winner);
                    if (loser != null)
                        fightPlayers.add(loser);
                }

                boolean isPartyFight = (plugin.partySplitManager != null
                        && plugin.partySplitManager.isPartySplit(fight))
                        || (plugin.partyVsPartyManager != null && plugin.partyVsPartyManager.isPartyVsParty(fight));
                if (isPartyFight) {
                    for (Player pp : resolveAllFightPlayers(fight, fightPlayers)) {
                        if (pp != null && !fightPlayers.contains(pp))
                            fightPlayers.add(pp);
                    }
                }
                fightPlayers.removeIf(p -> p == null);

                for (Player p : fightPlayers) {
                    matchEndedPlayers.add(p.getUniqueId());
                }
                final List<Player> finalFightPlayersForCleanup = new ArrayList<>(fightPlayers);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        for (Player p : finalFightPlayersForCleanup) {
                            if (p != null) {
                                matchEndedPlayers.remove(p.getUniqueId());
                                pendingHubTeleports.remove(p.getUniqueId());
                            }
                        }
                    }
                }.runTaskLater(plugin, 200L);

                if (winner == null && fight != null && !fightPlayers.isEmpty()) {
                    boolean isPartyFight2 = (plugin.partySplitManager != null
                            && plugin.partySplitManager.isPartySplit(fight))
                            || (plugin.partyVsPartyManager != null && plugin.partyVsPartyManager.isPartyVsParty(fight));
                    if (isPartyFight2) {
                        if (winner == null && plugin.partySplitManager != null)
                            winner = plugin.partySplitManager.getBridgeWinner(fight);
                        if (winner == null && plugin.partyVsPartyManager != null)
                            winner = plugin.partyVsPartyManager.getBridgeWinner(fight);

                        if (winner == null)
                            try {
                                Object winners = event.getClass().getMethod("getWinners").invoke(event);
                                if (winners instanceof Iterable) {
                                    for (Object obj : (Iterable<?>) winners) {
                                        if (obj instanceof Player) {
                                            winner = (Player) obj;
                                            break;
                                        }
                                        if (obj instanceof String) {
                                            Player p = org.bukkit.Bukkit.getPlayer((String) obj);
                                            if (p != null) {
                                                winner = p;
                                                break;
                                            }
                                        }
                                    }
                                }
                            } catch (Exception ignored) {
                            }

                        if (winner == null) {
                            try {
                                Boolean bed1Broken = (plugin.getMIsBed1Broken() != null)
                                        ? (Boolean) plugin.getMIsBed1Broken().invoke(fight)
                                        : null;
                                Boolean bed2Broken = (plugin.getMIsBed2Broken() != null)
                                        ? (Boolean) plugin.getMIsBed2Broken().invoke(fight)
                                        : null;
                                if (bed1Broken != null && bed2Broken != null) {
                                    for (Player candidate : fightPlayers) {
                                        String cc = plugin.getTeamColorCode(candidate, fight);
                                        boolean isTeam1 = cc.contains("9") || cc.contains("b");
                                        boolean isTeam2 = cc.contains("c") || cc.contains("d");
                                        if (isTeam1 && !bed1Broken) {
                                            winner = candidate;
                                            break;
                                        }
                                        if (isTeam2 && !bed2Broken) {
                                            winner = candidate;
                                            break;
                                        }
                                    }
                                }
                                if (winner == null && (bed1Broken != null || bed2Broken != null)) {
                                    Player refBlue = null, refRed = null;
                                    for (Player candidate : fightPlayers) {
                                        String cc = plugin.getTeamColorCode(candidate, fight);
                                        if (refBlue == null && (cc.contains("9") || cc.contains("b")))
                                            refBlue = candidate;
                                        if (refRed == null && (cc.contains("c") || cc.contains("d")))
                                            refRed = candidate;
                                    }
                                    if (refBlue != null && Boolean.FALSE.equals(bed1Broken))
                                        winner = refBlue;
                                    else if (refRed != null && Boolean.FALSE.equals(bed2Broken))
                                        winner = refRed;
                                }
                            } catch (Exception ignored) {
                            }
                        }

                        if (winner == null && plugin.partySplitManager != null
                                && plugin.partySplitManager.isPartySplit(fight)) {
                            try {
                                java.util.HashSet<String> alive1 = (java.util.HashSet<String>) fight.getClass()
                                        .getMethod("getAlive1").invoke(fight);
                                java.util.HashSet<String> alive2 = (java.util.HashSet<String>) fight.getClass()
                                        .getMethod("getAlive2").invoke(fight);
                                if (alive1 != null && alive2 != null) {
                                    if (!alive1.isEmpty() && alive2.isEmpty()) {
                                        for (String name : alive1) {
                                            Player cand = Bukkit.getPlayer(name);
                                            if (cand != null && cand.isOnline()) {
                                                winner = cand;
                                                break;
                                            }
                                        }
                                    } else if (alive1.isEmpty() && !alive2.isEmpty()) {
                                        for (String name : alive2) {
                                            Player cand = Bukkit.getPlayer(name);
                                            if (cand != null && cand.isOnline()) {
                                                winner = cand;
                                                break;
                                            }
                                        }
                                    }
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }

                boolean isPartyEndFightForMark = (plugin.partySplitManager != null
                        && plugin.partySplitManager.isPartySplit(fight))
                        || (plugin.partyVsPartyManager != null && plugin.partyVsPartyManager.isPartyVsParty(fight));
                if (isPartyEndFightForMark) {
                    for (Player pMark : fightPlayers)
                        if (pMark != null)
                            plugin.recentPartyEndedPlayers.add(pMark.getUniqueId());
                }

                for (Player p : fightPlayers) {
                    if (p == null)
                        continue;

                    String result = ChatColor.YELLOW + "" + ChatColor.BOLD + "DRAW!";
                    String kitName = plugin.getKitName(p);

                    if (winner != null) {
                        boolean isWinner = p.getUniqueId().equals(winner.getUniqueId());
                        if (!isWinner && plugin.getMPlayersAreTeammates() != null) {
                            try {
                                isWinner = (boolean) plugin.getMPlayersAreTeammates().invoke(fight, p, winner);
                            } catch (Exception ignored) {
                            }
                        }

                        if (isWinner) {
                            result = ChatColor.GREEN + "" + ChatColor.BOLD + "VICTORY";

                            if (p.getUniqueId().equals(winner.getUniqueId()) && plugin.leaderboardManager != null) {
                                plugin.leaderboardManager.addWin(winner.getUniqueId(), winner.getName(), kitName);
                            }
                        } else {
                            result = ChatColor.RED + "" + ChatColor.BOLD + "DEFEAT";

                            if (loser != null && p.getUniqueId().equals(loser.getUniqueId())
                                    && plugin.leaderboardManager != null) {
                                plugin.leaderboardManager.resetStreak(loser.getUniqueId(), kitName);
                            }
                        }
                    } else {
                        if (plugin.leaderboardManager != null) {
                            plugin.leaderboardManager.resetStreak(p.getUniqueId(), kitName);
                        }
                    }

                    plugin.playerMatchResults.put(p.getUniqueId(), result);
                }

                boolean isBestOf = false;
                int blueScore = 0;
                int redScore = 0;
                boolean isBlueWinner = false;

                try {
                    Method getBestOfMethod = fight.getClass().getMethod("getBestOf");
                    Object bestOf = getBestOfMethod.invoke(fight);
                    if (bestOf != null) {

                        int totalRounds = 1;
                        try {
                            Method getRoundsMethod = bestOf.getClass().getMethod("getRounds");
                            totalRounds = (int) getRoundsMethod.invoke(bestOf);
                        } catch (Exception ignored) {
                        }

                        if (totalRounds > 1) {
                            isBestOf = true;
                        }

                        if (!isBestOf && fightPlayers.size() > 0) {
                            String kitName = plugin.getKitName(fightPlayers.get(0));
                            if (kitName != null) {
                                String lower = kitName.toLowerCase();
                                if (lower.contains("bridge") || lower.contains("mlgrush")) {
                                    isBestOf = true;
                                }
                            }
                        }

                        Player bluePlayer = null;
                        Player redPlayer = null;

                        for (Player p : fightPlayers) {
                            if (p == null)
                                continue;
                            String color = plugin.getTeamColorCode(p, fight);
                            if (color.contains("9") || color.contains("b"))
                                bluePlayer = p;
                            else if (color.contains("c") || color.contains("d"))
                                redPlayer = p;
                        }

                        if (bluePlayer == null && fightPlayers.size() > 0)
                            bluePlayer = fightPlayers.get(0);
                        if (redPlayer == null && fightPlayers.size() > 1)
                            redPlayer = fightPlayers.get(fightPlayers.size() - 1);

                        Map<UUID, Integer> oldScores = plugin.matchScores.getOrDefault(fight, new HashMap<>());

                        try {
                            Method getRoundsWon = bestOf.getClass().getMethod("getRoundsWon");
                            Map<?, ?> scores = (Map<?, ?>) getRoundsWon.invoke(bestOf);
                            if (scores != null && !scores.isEmpty()) {
                                for (Map.Entry<?, ?> entry : scores.entrySet()) {
                                    Object key = entry.getKey();
                                    int score = entry.getValue() instanceof Number
                                            ? ((Number) entry.getValue()).intValue()
                                            : 0;

                                    if (bluePlayer != null && (key.equals(bluePlayer.getUniqueId())
                                            || key.equals(bluePlayer.getName()))) {
                                        blueScore = score;
                                    } else if (redPlayer != null && (key.equals(redPlayer.getUniqueId())
                                            || key.equals(redPlayer.getName()))) {
                                        redScore = score;
                                    }
                                }
                            }
                        } catch (Exception ignored) {
                        }

                        if (bluePlayer != null && blueScore == 0)
                            blueScore = oldScores.getOrDefault(bluePlayer.getUniqueId(), 0);
                        if (redPlayer != null && redScore == 0)
                            redScore = oldScores.getOrDefault(redPlayer.getUniqueId(), 0);

                        if (winner != null) {
                            String wColor = plugin.getTeamColorCode(winner, fight);
                            isBlueWinner = wColor.contains("9") || wColor.contains("b");
                        }
                    }
                } catch (Exception ignored) {
                }

                final boolean finalIsBestOf = isBestOf;
                final int finalBlueScore = blueScore;
                final int finalRedScore = redScore;
                final boolean finalIsBlueWinner = isBlueWinner;

                final boolean isDuelReq = plugin.duelScoreManager != null
                        && plugin.duelScoreManager.isDuelRequestFight(fight);

                if (isDuelReq && winner != null && loser != null) {
                    plugin.duelScoreManager.onFightEnd(fight, winner, loser);
                }

                plugin.matchScores.remove(fight);
                startedFights.remove(fight);
                fightCountdownCooldown.remove(fight);
                partyCountdownFights.remove(fight);
                if (plugin.partySplitManager != null)
                    plugin.partySplitManager.onFightEnd(fight);
                if (plugin.partyVsPartyManager != null)
                    plugin.partyVsPartyManager.onFightEnd(fight);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        plugin.endedFightWinners.remove(fight);
                        for (Player p : fightPlayers) {
                            if (p != null) {
                                plugin.playerMatchResults.remove(p.getUniqueId());
                                plugin.playerMatchKills.remove(p.getUniqueId());
                                plugin.recentPartyEndedPlayers.remove(p.getUniqueId());
                            }
                        }
                    }
                }.runTaskLater(plugin, 160L);

                if (fight != null && plugin.getMGetArena() != null) {
                    try {
                        Object arena = plugin.getMGetArena().invoke(fight);
                        if (arena != null) {
                            String arenaName = (String) arena.getClass().getMethod("getName").invoke(arena);
                            forceFixBeds(arenaName, fight, 80L);
                        }
                    } catch (Exception ex) {
                    }
                }

                for (Player p : fightPlayers) {
                    if (p == null)
                        continue;

                    boolean isWinner = false;
                    boolean isLoser = false;

                    if (winner != null) {
                        isWinner = p.getUniqueId().equals(winner.getUniqueId());
                        if (!isWinner && plugin.getMPlayersAreTeammates() != null) {
                            try {
                                isWinner = (boolean) plugin.getMPlayersAreTeammates().invoke(fight, p, winner);
                            } catch (Exception ignored) {
                            }
                        }
                    }

                    if (winner != null && !isWinner) {
                        isLoser = true;
                    }

                    boolean isPartyEndFight = (plugin.partySplitManager != null
                            && plugin.partySplitManager.isPartySplit(fight))
                            || (plugin.partyVsPartyManager != null && plugin.partyVsPartyManager.isPartyVsParty(fight));
                    if (!isWinner && !isLoser && !isPartyEndFight) {
                        continue;
                    }
                    if (!isWinner && !isLoser && isPartyEndFight) {
                        isLoser = true;
                    }

                    plugin.cleanupPlayer(p.getUniqueId(), false);

                    final boolean finalIsWinnerForSound = isWinner;
                    final Player finalWinner = winner;
                    final Player finalLoser = loser;
                    final boolean finalIsDuelReq = isDuelReq;
                    final boolean finalIsPartyFight = isPartyEndFight;

                    final String finalWinnerLeader;
                    final String finalLoserLeader;
                    if (isPartyEndFight && winner != null) {
                        finalWinnerLeader = getPartyLeaderName(fight, winner);
                        finalLoserLeader = isLoser ? getPartyLeaderName(fight, p) : null;
                    } else {
                        finalWinnerLeader = null;
                        finalLoserLeader = null;
                    }

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (p.isOnline()) {
                                if ((plugin.partySplitManager != null
                                        && plugin.partySplitManager.isBridgeEndHandled(fight))
                                        || (plugin.partyVsPartyManager != null
                                                && plugin.partyVsPartyManager.isBridgeEndHandled(fight))) {
                                    plugin.frozenPlayers.remove(p.getUniqueId());
                                    return;
                                }
                                if (finalIsWinnerForSound) {
                                    if (finalIsBestOf) {
                                        String t = finalIsBlueWinner ? plugin.getMsg("bestof.blue-wins.title")
                                                : plugin.getMsg("bestof.red-wins.title");
                                        String s = finalIsBlueWinner ? plugin.getMsg("bestof.blue-wins.subtitle")
                                                : plugin.getMsg("bestof.red-wins.subtitle");
                                        if (t == null || t.isEmpty())
                                            t = finalIsBlueWinner ? "&9&lBLUE WINS!" : "&c&lRED WINS!";
                                        if (s == null || s.isEmpty())
                                            s = finalIsBlueWinner ? "&9<blue_score> &8- &c<red_score>"
                                                    : "&c<red_score> &8- &9<blue_score>";
                                        s = s.replace("<blue_score>", String.valueOf(finalBlueScore))
                                                .replace("<red_score>", String.valueOf(finalRedScore));
                                        plugin.sendTitle(p, ChatColor.translateAlternateColorCodes('&', t),
                                                ChatColor.translateAlternateColorCodes('&', s), 10, 70, 20);
                                    } else if (finalIsPartyFight) {
                                        String t = plugin.getMsg("party.victory.title");
                                        String s = plugin.getMsg("party.victory.subtitle");
                                        if (t == null || t.isEmpty())
                                            t = "&e&lVICTORY!";
                                        if (s == null || s.isEmpty())
                                            s = "&fYour team won the Match!";
                                        String leader = finalWinnerLeader != null ? finalWinnerLeader
                                                : (finalWinner != null ? finalWinner.getName() : "Unknown");
                                        t = t.replace("<leader>", leader);
                                        s = s.replace("<leader>", leader);
                                        plugin.sendTitle(p, ChatColor.translateAlternateColorCodes('&', t),
                                                ChatColor.translateAlternateColorCodes('&', s), 10, 70, 20);
                                    } else if (finalIsDuelReq && finalLoser != null
                                            && plugin.duelScoreManager != null) {
                                        Player myOpponent = finalLoser;
                                        int myScore = plugin.duelScoreManager.getScore(p.getUniqueId(),
                                                myOpponent.getUniqueId());
                                        int opScore = plugin.duelScoreManager.getScore(myOpponent.getUniqueId(),
                                                p.getUniqueId());
                                        String title = ChatColor.translateAlternateColorCodes('&', "&e&lVICTORY!");
                                        String subtitle = ChatColor.translateAlternateColorCodes('&',
                                                "&a" + myScore + " &8- &c" + opScore);
                                        plugin.sendTitle(p, title, subtitle, 10, 70, 20);
                                    } else {
                                        plugin.sendTitle(p,
                                                plugin.getMsg("victory.title").replace("<player>",
                                                        finalWinner != null ? finalWinner.getName() : "Unknown"),
                                                plugin.getMsg("victory.subtitle").replace("<player>",
                                                        finalWinner != null ? finalWinner.getName() : "Unknown"),
                                                10, 70, 20);
                                    }
                                    plugin.playEndMatchSounds(p, true);
                                } else {
                                    if (finalIsBestOf) {
                                        String t = finalIsBlueWinner ? plugin.getMsg("bestof.blue-wins.title")
                                                : plugin.getMsg("bestof.red-wins.title");
                                        String s = finalIsBlueWinner ? plugin.getMsg("bestof.blue-wins.subtitle")
                                                : plugin.getMsg("bestof.red-wins.subtitle");
                                        if (t == null || t.isEmpty())
                                            t = finalIsBlueWinner ? "&9&lBLUE WINS!" : "&c&lRED WINS!";
                                        if (s == null || s.isEmpty())
                                            s = finalIsBlueWinner ? "&9<blue_score> &8- &c<red_score>"
                                                    : "&c<red_score> &8- &9<blue_score>";
                                        s = s.replace("<blue_score>", String.valueOf(finalBlueScore))
                                                .replace("<red_score>", String.valueOf(finalRedScore));
                                        plugin.sendTitle(p, ChatColor.translateAlternateColorCodes('&', t),
                                                ChatColor.translateAlternateColorCodes('&', s), 10, 70, 20);
                                    } else if (finalIsPartyFight) {
                                        String t = plugin.getMsg("party.defeat.title");
                                        String s = plugin.getMsg("party.defeat.subtitle");
                                        if (t == null || t.isEmpty())
                                            t = "&c&lDEFEAT!";
                                        if (s == null || s.isEmpty())
                                            s = "&c<opponent_leader> &fwon this Match!";
                                        String oppLeader = finalWinnerLeader != null ? finalWinnerLeader
                                                : (finalWinner != null ? finalWinner.getName() : "Unknown");
                                        t = t.replace("<opponent_leader>", oppLeader);
                                        s = s.replace("<opponent_leader>", oppLeader);
                                        plugin.sendTitle(p, ChatColor.translateAlternateColorCodes('&', t),
                                                ChatColor.translateAlternateColorCodes('&', s), 10, 70, 20);
                                    } else if (finalIsDuelReq && finalWinner != null
                                            && plugin.duelScoreManager != null) {
                                        Player myOpponent = finalWinner;
                                        int myScore = plugin.duelScoreManager.getScore(p.getUniqueId(),
                                                myOpponent.getUniqueId());
                                        int opScore = plugin.duelScoreManager.getScore(myOpponent.getUniqueId(),
                                                p.getUniqueId());
                                        String title = ChatColor.translateAlternateColorCodes('&', "&c&lDEFEAT!");
                                        String subtitle = ChatColor.translateAlternateColorCodes('&',
                                                "&c" + myScore + " &8- &a" + opScore);
                                        plugin.sendTitle(p, title, subtitle, 10, 70, 20);
                                    } else {
                                        String op = finalWinner != null ? finalWinner.getName() : "Unknown";
                                        plugin.sendTitle(p, plugin.getMsg("defeat.title").replace("<opponent>", op),
                                                plugin.getMsg("defeat.subtitle").replace("<opponent>", op), 10, 70, 20);
                                    }
                                    plugin.playEndMatchSounds(p, false);
                                    p.getWorld().strikeLightningEffect(p.getLocation());
                                }
                            }
                        }
                    }.runTaskLater(plugin, 5L);
                }
            }
        } catch (Exception e) {
        }
    }

    @SuppressWarnings("unchecked")
    public void handleRoundEnd(Event event) {
        try {
            final Object fight = event.getClass().getMethod("getFight").invoke(event);
            if (fight == null)
                return;

            Player tempWinner = null;
            for (Method m : event.getClass().getMethods()) {
                if ((m.getName().toLowerCase().contains("winner") || m.getName().equalsIgnoreCase("getPlayer"))
                        && m.getParameterTypes().length == 0) {
                    try {
                        Object res = m.invoke(event);
                        if (res instanceof Player) {
                            tempWinner = (Player) res;
                            break;
                        } else if (res instanceof UUID) {
                            tempWinner = Bukkit.getPlayer((UUID) res);
                            break;
                        } else if (res instanceof String) {
                            tempWinner = Bukkit.getPlayer((String) res);
                            break;
                        }
                    } catch (Exception ex) {
                    }
                }
            }

            Object tempBestOf = null;
            try {
                Method getBestOf = fight.getClass().getMethod("getBestOf");
                tempBestOf = getBestOf.invoke(fight);
            } catch (Exception ignored) {
            }

            final Player initialWinner = tempWinner;
            final Object bestOf = tempBestOf;

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (plugin.endedFightWinners.containsKey(fight)) {
                        return;
                    }

                    Player winner = initialWinner;

                    if (bestOf != null) {
                        try {
                            Method getRoundsWon = bestOf.getClass().getMethod("getRoundsWon");
                            Map<UUID, Integer> currentScores = (Map<UUID, Integer>) getRoundsWon.invoke(bestOf);
                            Map<UUID, Integer> oldScores = plugin.matchScores.getOrDefault(fight, new HashMap<>());

                            if (currentScores != null) {
                                for (Map.Entry<UUID, Integer> entry : currentScores.entrySet()) {
                                    UUID u = entry.getKey();
                                    int newScore = entry.getValue();
                                    int oldScore = oldScores.getOrDefault(u, 0);

                                    if (newScore > oldScore) {
                                        winner = Bukkit.getPlayer(u);
                                        break;
                                    }
                                }
                                plugin.matchScores.put(fight, new HashMap<>(currentScores));
                            }
                        } catch (Exception ex) {
                        }
                    }

                    if (winner == null)
                        return;

                    String kitName = plugin.getKitName(winner);
                    if (kitName == null) {
                        try {
                            Object kit = fight.getClass().getMethod("getKit").invoke(fight);
                            if (kit != null)
                                kitName = (String) kit.getClass().getMethod("getName").invoke(kit);
                        } catch (Exception e) {
                        }
                    }

                    if (kitName == null)
                        return;
                    String kitLower = kitName.toLowerCase();

                    boolean allowed = plugin.roundEndKits.isEmpty();
                    if (kitLower.contains("bridge") || kitLower.contains("thebridge")
                            || kitLower.contains("thebridgeelo")) {
                        allowed = true;
                    } else if (!allowed) {
                        for (String k : plugin.roundEndKits) {
                            if (kitLower.contains(k.toLowerCase())) {
                                allowed = true;
                                break;
                            }
                        }
                    }

                    if (!allowed)
                        return;

                    if (plugin.getMGetArena() != null) {
                        try {
                            Object arena = plugin.getMGetArena().invoke(fight);
                            if (arena != null) {
                                String arenaName = (String) arena.getClass().getMethod("getName").invoke(arena);
                                forceFixBeds(arenaName, fight, 80L);
                            }
                        } catch (Exception ex) {
                        }
                    }

                    List<Player> matchPlayers = new ArrayList<>();
                    try {
                        Method getPlayers = fight.getClass().getMethod("getPlayersInFight");
                        List<Player> fPlayers = (List<Player>) getPlayers.invoke(fight);
                        if (fPlayers != null)
                            matchPlayers.addAll(fPlayers);
                    } catch (Exception e) {
                        try {
                            Method getPlayers = fight.getClass().getMethod("getPlayers");
                            List<Player> fPlayers = (List<Player>) getPlayers.invoke(fight);
                            if (fPlayers != null)
                                matchPlayers.addAll(fPlayers);
                        } catch (Exception ignored) {
                        }
                    }
                    if (matchPlayers.isEmpty())
                        matchPlayers.add(winner);

                    Map<UUID, Integer> curScores = plugin.matchScores.getOrDefault(fight, new HashMap<>());
                    String winnerColor = plugin.getTeamColorCode(winner, fight);
                    Player loserPlayer = null;
                    for (Player p : matchPlayers) {
                        if (p != null && !p.getUniqueId().equals(winner.getUniqueId())) {
                            loserPlayer = p;
                            break;
                        }
                    }
                    String loserColor = loserPlayer != null ? plugin.getTeamColorCode(loserPlayer, fight) : "§c";
                    int wScore = curScores.getOrDefault(winner.getUniqueId(), 0);
                    int lScore = loserPlayer != null ? curScores.getOrDefault(loserPlayer.getUniqueId(), 0) : 0;

                    String rawScoredTitle = plugin.getMsg("scored.title");
                    String rawScoredSub = plugin.getMsg("scored.subtitle");
                    if (rawScoredTitle == null || rawScoredTitle.isEmpty())
                        rawScoredTitle = "&e&l<player>";
                    if (rawScoredSub == null || rawScoredSub.isEmpty())
                        rawScoredSub = "&6Scored!";
                    final String scoredTitle = ChatColor.translateAlternateColorCodes('&',
                            rawScoredTitle.replace("<player>", winner.getName()));
                    final String scoredSubtitle = ChatColor.translateAlternateColorCodes('&',
                            rawScoredSub.replace("<player>", winner.getName()))
                            + "  " + winnerColor + wScore + " §8- " + loserColor + lScore;

                    if (kitLower.contains("bridge") || kitLower.contains("thebridge")) {

                        final Object roundFight = fight;
                        for (Player p : matchPlayers) {
                            if (p != null && p.isOnline()) {
                                plugin.frozenPlayers.add(p.getUniqueId());
                                new BukkitRunnable() {
                                    @Override
                                    public void run() {
                                        if (p.isOnline())
                                            plugin.applyStartKit(p, roundFight);
                                    }
                                }.runTaskLater(plugin, 5L);
                                new BukkitRunnable() {
                                    @Override
                                    public void run() {
                                        if (p.isOnline())
                                            plugin.applyStartKit(p, roundFight);
                                    }
                                }.runTaskLater(plugin, 15L);
                            }
                        }

                        Sound goalSound = plugin.getSoundByName("LEVEL_UP");
                        if (goalSound == null)
                            goalSound = plugin.getSoundByName("ENTITY_PLAYER_LEVELUP");
                        final Sound finalGoalSound = goalSound;
                        for (Player p : matchPlayers) {
                            if (p != null && p.isOnline()) {
                                plugin.sendTitle(p, scoredTitle, scoredSubtitle, 2, 16, 2);
                                if (finalGoalSound != null)
                                    p.playSound(p.getLocation(), finalGoalSound, 1.0f, 1.0f);
                            }
                        }

                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (plugin.endedFightWinners.containsKey(fight)) {
                                    for (Player p : matchPlayers) {
                                        if (p != null)
                                            plugin.frozenPlayers.remove(p.getUniqueId());
                                    }
                                    return;
                                }
                                final int maxSeconds = plugin.startCountdownDuration > 0 ? plugin.startCountdownDuration
                                        : 5;
                                new BukkitRunnable() {
                                    int current = maxSeconds;

                                    @Override
                                    public void run() {
                                        if (plugin.endedFightWinners.containsKey(fight)) {
                                            for (Player p : matchPlayers) {
                                                if (p != null)
                                                    plugin.frozenPlayers.remove(p.getUniqueId());
                                            }
                                            cancel();
                                            return;
                                        }
                                        if (current <= 0) {
                                            for (Player p : matchPlayers) {
                                                if (p == null || !p.isOnline())
                                                    continue;
                                                if (plugin.startMatchMessage != null
                                                        && !plugin.startMatchMessage.isEmpty())
                                                    p.sendMessage(plugin.startMatchMessage);
                                                if (plugin.startCountdownTitles != null
                                                        && plugin.startCountdownTitles.containsKey(0))
                                                    plugin.sendTitle(p, plugin.startCountdownTitles.get(0), "", 0, 20,
                                                            10);
                                                if (plugin.startCountdownSoundEnabled && plugin.startMatchSound != null)
                                                    p.playSound(p.getLocation(), plugin.startMatchSound,
                                                            plugin.startCountdownVolume, plugin.startCountdownPitch);
                                                plugin.frozenPlayers.remove(p.getUniqueId());
                                            }
                                            cancel();
                                            return;
                                        }
                                        if (plugin.startCountdownEnabled) {
                                            for (Player p : matchPlayers) {
                                                if (p == null || !p.isOnline())
                                                    continue;
                                                if (plugin.startCountdownMessages != null)
                                                    p.sendMessage(plugin.startCountdownMessages.getOrDefault(current,
                                                            ChatColor.RED + String.valueOf(current)));
                                                if (plugin.startCountdownTitles != null
                                                        && plugin.startCountdownTitles.containsKey(current))
                                                    plugin.sendTitle(p, plugin.startCountdownTitles.get(current), "", 0,
                                                            20, 0);
                                                if (plugin.startCountdownSoundEnabled
                                                        && plugin.startCountdownSounds != null) {
                                                    Sound s = plugin.startCountdownSounds.get(current);
                                                    if (s != null)
                                                        p.playSound(p.getLocation(), s, plugin.startCountdownVolume,
                                                                plugin.startCountdownPitch);
                                                }
                                            }
                                        }
                                        current--;
                                    }
                                }.runTaskTimer(plugin, 0L, 20L);
                            }
                        }.runTaskLater(plugin, 22L);

                    } else {

                        Sound levelUp = plugin.getSoundByName("LEVEL_UP");
                        if (levelUp == null)
                            levelUp = plugin.getSoundByName("ENTITY_PLAYER_LEVELUP");
                        final Sound finalLevelUp = levelUp;
                        final String finalKitName = kitName;

                        fightCountdownCooldown.put(fight, System.currentTimeMillis() + 10000L);

                        new BukkitRunnable() {
                            int ticks = 20;
                            boolean soundPlayed = false;

                            @Override
                            public void run() {
                                if (ticks <= 0) {
                                    cancel();

                                    if (!plugin.endedFightWinners.containsKey(fight)) {
                                        fightCountdownCooldown.put(fight, System.currentTimeMillis());
                                        startMatchCountdown(matchPlayers, fight, false, finalKitName);
                                    }
                                    return;
                                }
                                for (Player p : matchPlayers) {
                                    if (p != null && p.isOnline()) {
                                        plugin.sendTitle(p, scoredTitle, scoredSubtitle, 0, 10, 0);
                                        if (!soundPlayed && finalLevelUp != null) {
                                            p.playSound(p.getLocation(), finalLevelUp, 1.0f, 1.0f);
                                        }
                                    }
                                }
                                soundPlayed = true;
                                ticks -= 5;
                            }
                        }.runTaskTimer(plugin, 0L, 5L);
                    }
                }
            }.runTaskLater(plugin, 2L);

        } catch (Exception e) {
        }
    }
}