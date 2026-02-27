package com.pixra.pixCore.listeners;

import com.pixra.pixCore.PixCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    // Konfigurasi untuk Bed Permanen (Arena Bed Fixer)
    private File customBedsFile;
    private org.bukkit.configuration.file.FileConfiguration customBedsConfig;
    private final List<BedPair> persistentBeds = new ArrayList<>();

    public MatchListener(PixCore plugin) {
        this.plugin = plugin;
        loadCustomBeds();
        registerStrikePracticeEvents();
    }

    // --- FITUR ARENA BED FIXER --- //

    private void loadCustomBeds() {
        customBedsFile = new File(plugin.getDataFolder(), "custombeds.yml");
        if (!customBedsFile.exists()) {
            try { customBedsFile.createNewFile(); } catch (Exception e) {}
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
        try { customBedsConfig.save(customBedsFile); } catch (Exception e) {}
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpecialBedPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        Player player = event.getPlayer();
        org.bukkit.inventory.ItemStack item = event.getItemInHand();

        if (item != null && item.getType().name().contains("BED") && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            if (item.getItemMeta().getDisplayName().contains("Arena Bed Fixer")) {
                Block foot = event.getBlockPlaced();

                // Menunggu 2 tick agar blok bagian Head terbentuk secara default oleh minecraft
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (foot.getType().name().contains("BED")) {
                            byte footData = getBlockDataSafe(foot);
                            int dir = footData & 3;
                            int dx = 0, dz = 0;
                            if (dir == 0) dz = 1;
                            else if (dir == 1) dx = -1;
                            else if (dir == 2) dz = -1;
                            else if (dir == 3) dx = 1;

                            Block head = foot.getRelative(dx, 0, dz);
                            if (head.getType().name().contains("BED")) {
                                byte headData = getBlockDataSafe(head);
                                BedPair newBed = new BedPair(foot.getLocation(), head.getLocation(), footData, headData);
                                saveCustomBed(newBed);
                                player.sendMessage(ChatColor.GREEN + "[PixCore] Custom Bed saved successfully! It will perfectly restore in this arena.");
                            } else {
                                player.sendMessage(ChatColor.RED + "[PixCore] Failed to save bed. Make sure it is placed completely.");
                            }
                        }
                    }
                }.runTaskLater(plugin, 2L);
            }
        }
    }
    // --- AKHIR FITUR ARENA BED FIXER --- //

    private void registerStrikePracticeEvents() {
        try {
            Class<? extends Event> duelStartClass = (Class<? extends Event>) Class.forName("ga.strikepractice.events.DuelStartEvent");
            Bukkit.getPluginManager().registerEvent(duelStartClass, this, EventPriority.MONITOR, (listener, event) -> handleDuelStart(event), plugin);

            Class<? extends Event> duelEndClass = (Class<? extends Event>) Class.forName("ga.strikepractice.events.DuelEndEvent");
            Bukkit.getPluginManager().registerEvent(duelEndClass, this, EventPriority.MONITOR, (listener, event) -> handleDuelEnd(event), plugin);

            Class<? extends Event> roundEndClass = (Class<? extends Event>) Class.forName("ga.strikepractice.events.RoundEndEvent");
            Bukkit.getPluginManager().registerEvent(roundEndClass, this, EventPriority.MONITOR, (listener, event) -> handleRoundEnd(event), plugin);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not register StrikePractice custom events.");
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
            // Paksa load chunk agar tidak glitch
            if (!footLoc.getChunk().isLoaded()) footLoc.getChunk().load();
            if (!headLoc.getChunk().isLoaded()) headLoc.getChunk().load();

            Block foot = footLoc.getBlock();
            Block head = headLoc.getBlock();

            org.bukkit.Material bedMat;
            try {
                bedMat = org.bukkit.Material.valueOf("BED_BLOCK");
            } catch (Exception e) {
                return;
            }

            // CARA 1 (Paling Ampuh di 1.8.8): Menggunakan Reflection langsung ke Method setTypeIdAndData
            try {
                Method setTypeIdAndData = Block.class.getMethod("setTypeIdAndData", int.class, byte.class, boolean.class);
                Method getId = org.bukkit.Material.class.getMethod("getId");
                int bedId = (Integer) getId.invoke(bedMat);

                // Bersihkan secara manual dulu dengan 0 (AIR) dan applyPhysics = false
                setTypeIdAndData.invoke(foot, 0, (byte) 0, false);
                setTypeIdAndData.invoke(head, 0, (byte) 0, false);

                // Pasang kembali Bed-nya dengan sempurna
                setTypeIdAndData.invoke(foot, bedId, footData, false);
                setTypeIdAndData.invoke(head, bedId, headData, false);
                return; // Berhasil menggunakan metode direct, langsung keluar
            } catch (Exception legacyEx) {
                // Abaikan, server Anda kemungkinan versi 1.13+ dan metode ini tidak ada
                // Jadi kita turun ke fallback di bawah ini
            }

            // CARA 2 (Fallback untuk versi server baru):
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
        if (!plugin.frozenPlayers.contains(player.getUniqueId())) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) return;
        event.setTo(new Location(from.getWorld(), from.getX(), to.getY(), from.getZ(), to.getYaw(), to.getPitch()));
    }

    @SuppressWarnings("unchecked")
    public void handleDuelStart(Event event) {
        try {
            Object fight = event.getClass().getMethod("getFight").invoke(event);
            if (fight == null) return;

            Object arena = (plugin.getMGetArena() != null) ? plugin.getMGetArena().invoke(fight) : null;
            List<Player> players = new ArrayList<>();

            if (plugin.getMGetPlayers() != null) {
                try {
                    List<Player> found = (List<Player>) plugin.getMGetPlayers().invoke(fight);
                    if (found != null) players.addAll(found);
                } catch (Exception e) {}
            }

            if (players.isEmpty()) {
                try {
                    List<Player> found = (List<Player>) fight.getClass().getMethod("getPlayers").invoke(fight);
                    if (found != null) players.addAll(found);
                } catch (NoSuchMethodException e) {
                    if (plugin.getMGetFirstPlayer() != null) players.add((Player) plugin.getMGetFirstPlayer().invoke(fight));
                    if (plugin.getMGetSecondPlayer() != null) players.add((Player) plugin.getMGetSecondPlayer().invoke(fight));
                }
            }
            players.removeIf(p -> p == null);

            if (!players.isEmpty()) {
                startMatchCountdown(players);
                for (Player p : players) plugin.playerMatchKills.put(p.getUniqueId(), 0);

                if (plugin.getMSetBedwars() != null) {
                    try {
                        Player p = players.get(0);
                        String kitName = plugin.getKitName(p);

                        if (kitName != null) {
                            boolean isBed = false;

                            List<String> bedRestoreKits = plugin.getConfig().getStringList("settings.bed-restore-kits");
                            if (bedRestoreKits != null && !bedRestoreKits.isEmpty()) {
                                for (String kit : bedRestoreKits) {
                                    if (kit.equalsIgnoreCase(kitName)) {
                                        isBed = true; break;
                                    }
                                }
                            } else {
                                isBed = kitName.toLowerCase().contains("bed") || kitName.toLowerCase().contains("fireball");
                            }

                            if (!isBed && plugin.respawnChatCountdownKits != null) {
                                for (String k : plugin.respawnChatCountdownKits) if (k.equalsIgnoreCase(kitName)) isBed = true;
                            }

                            if (isBed) {
                                Object kit = plugin.getStrikePracticeAPI().getClass().getMethod("getKit", Player.class).invoke(plugin.getStrikePracticeAPI(), p);
                                plugin.getMSetBedwars().invoke(kit, true);

                                if (arena != null) {
                                    try {
                                        String arenaName = (String) arena.getClass().getMethod("getName").invoke(arena);
                                        if (!cachedArenaBeds.containsKey(arenaName)) {
                                            saveArenaBeds(arenaName, arena);
                                        }

                                        forceFixBeds(arenaName, fight, 10L);

                                    } catch (Exception ex) {}
                                }
                            }
                        }
                    } catch (Exception ex) {}
                }
            }
        } catch (Exception e) {}
    }

    private void saveArenaBeds(String arenaName, Object arena) {
        if (plugin.arenaBoundaryManager == null) return;
        Location min = plugin.arenaBoundaryManager.getCorner1(arena);
        Location max = plugin.arenaBoundaryManager.getCorner2(arena);
        if (min == null || max == null) return;

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
                        if (dir == 0) dz = 1;
                        else if (dir == 1) dx = -1;
                        else if (dir == 2) dz = -1;
                        else if (dir == 3) dx = 1;

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

    // Melakukan Fix Bed secara dijadwalkan ganda untuk memastikan Schematic tidak menimpanya
    private void forceFixBeds(String arenaName, Object fight, long delayTicks) {
        new BukkitRunnable() {
            @Override
            public void run() {
                executeBedRestore(arenaName, fight);
            }
        }.runTaskLater(plugin, delayTicks);

        // Eksekusi Backup! (Diulangi kembali setelah sekian detik untuk menjamin menimpa glitch Map Reset StrikePractice)
        new BukkitRunnable() {
            @Override
            public void run() {
                executeBedRestore(arenaName, fight);
            }
        }.runTaskLater(plugin, delayTicks + 40L);
    }

    // Pemisahan logika restore Bed agar bisa dipanggil berkali-kali secara aman
    private void executeBedRestore(String arenaName, Object fight) {
        List<BedPair> bedsToRestore = new ArrayList<>();
        boolean usingPersistent = false;

        // 1. Cek Bed Permanen (Arena Bed Fixer) yang berada di area map ini
        try {
            Object arena = (plugin.getMGetArena() != null && fight != null) ? plugin.getMGetArena().invoke(fight) : null;
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
        } catch (Exception e) {}

        // 2. Jika tidak ada Bed Permanen di area tersebut, gunakan fallback scanning standar
        if (!usingPersistent && cachedArenaBeds.containsKey(arenaName)) {
            bedsToRestore.addAll(cachedArenaBeds.get(arenaName));
        }

        // Restore
        for (BedPair bed : bedsToRestore) {
            placeBedRobust(bed.footLoc, bed.headLoc, bed.footData, bed.headData);
        }

        if (fight != null && plugin.getClsAbstractFight() != null && plugin.getClsAbstractFight().isInstance(fight)) {
            try {
                if (plugin.getFBed1Broken() != null) plugin.getFBed1Broken().set(fight, false);
                if (plugin.getFBed2Broken() != null) plugin.getFBed2Broken().set(fight, false);
            } catch (Exception ex) {}
        }
    }

    private void startMatchCountdown(List<Player> players) {
        // Hapus plugin.isCountdownRunning = true; agar banyak arena dapat memulai countdown secara paralel
        for (Player p : players) {
            if (p != null && p.isOnline()) {
                plugin.frozenPlayers.add(p.getUniqueId());

                // --- MUNCULKAN HOLOGRAM LEADERBOARD FALLBACK SAAT MULAI COUNTDOWN ---
                if (plugin.hologramManager != null) {
                    String kitName = plugin.getKitName(p);
                    plugin.hologramManager.spawnLeaderboardHolograms(p, kitName);
                }

                // Menunda apply custom kit beberapa tick agar menimpa default dari StrikePractice (Double Apply)
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (p.isOnline()) {
                            plugin.applyStartKit(p);
                        }
                    }
                }.runTaskLater(plugin, 5L);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (p.isOnline()) {
                            plugin.applyStartKit(p);
                        }
                    }
                }.runTaskLater(plugin, 15L);
            }
        }
        int maxSeconds = plugin.startCountdownDuration;
        new BukkitRunnable() {
            int current = maxSeconds;
            @Override
            public void run() {
                if (current <= 0) {
                    for (Player p : players) {
                        if (p != null) {
                            // PASTIKAN status frozen dan hologram dihapus (meskipun player tiba-tiba offline)
                            plugin.frozenPlayers.remove(p.getUniqueId());
                            if (plugin.hologramManager != null) {
                                plugin.hologramManager.removeHolograms(p);
                            }
                        }

                        if (p == null || !p.isOnline()) continue;

                        plugin.arenaSpawnLocations.put(p.getUniqueId(), p.getLocation().clone());
                        if (plugin.startMatchMessage != null && !plugin.startMatchMessage.isEmpty()) p.sendMessage(plugin.startMatchMessage);
                        if (plugin.startCountdownTitles != null && plugin.startCountdownTitles.containsKey(0)) plugin.sendTitle(p, plugin.startCountdownTitles.get(0), "", 0, 20, 10);
                        if (plugin.startCountdownSoundEnabled && plugin.startMatchSound != null) p.playSound(p.getLocation(), plugin.startMatchSound, plugin.startCountdownVolume, plugin.startCountdownPitch);
                        if(plugin.blockReplenishManager != null) plugin.blockReplenishManager.scanPlayerInventory(p);
                    }
                    cancel();
                    return;
                }
                if (plugin.startCountdownEnabled) {
                    for (Player p : players) {
                        if (p == null || !p.isOnline()) continue;
                        if (plugin.startCountdownMessages != null) p.sendMessage(plugin.startCountdownMessages.getOrDefault(current, ChatColor.RED + String.valueOf(current)));
                        if (plugin.startCountdownTitles != null && plugin.startCountdownTitles.containsKey(current)) plugin.sendTitle(p, plugin.startCountdownTitles.get(current), "", 0, 20, 0);
                        if (plugin.startCountdownSoundEnabled && plugin.startCountdownSounds != null) {
                            Sound s = plugin.startCountdownSounds.get(current);
                            if (s != null) p.playSound(p.getLocation(), s, plugin.startCountdownVolume, plugin.startCountdownPitch);
                        }
                    }
                }
                current--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    @SuppressWarnings("unchecked")
    public void handleDuelEnd(Event event) {
        try {
            final Object fight = event.getClass().getMethod("getFight").invoke(event);
            Player winner = (Player) event.getClass().getMethod("getWinner").invoke(event);
            Player loser = (Player) event.getClass().getMethod("getLoser").invoke(event);

            if (fight != null) {
                plugin.endedFightWinners.put(fight, winner != null ? winner.getUniqueId() : null);

                List<Player> fightPlayers = new ArrayList<>();
                if (plugin.getMGetPlayersInFight() != null) {
                    try { List<Player> temp = (List<Player>) plugin.getMGetPlayersInFight().invoke(fight); if (temp != null) fightPlayers.addAll(temp); } catch (Exception ignored) {}
                }
                if (fightPlayers.isEmpty() && plugin.getMGetPlayers() != null) {
                    try { List<Player> temp = (List<Player>) plugin.getMGetPlayers().invoke(fight); if (temp != null) fightPlayers.addAll(temp); } catch (Exception ignored) {}
                }
                if (fightPlayers.isEmpty()) { if (winner != null) fightPlayers.add(winner); if (loser != null) fightPlayers.add(loser); }

                for (Player p : fightPlayers) {
                    if (p == null) continue;

                    String result = ChatColor.YELLOW + "" + ChatColor.BOLD + "DRAW!";
                    String kitName = plugin.getKitName(p); // Ambil nama kit dari player yang sedang diproses

                    if (winner != null) {
                        boolean isWinner = p.getUniqueId().equals(winner.getUniqueId());
                        if (!isWinner && plugin.getMPlayersAreTeammates() != null) {
                            try { isWinner = (boolean) plugin.getMPlayersAreTeammates().invoke(fight, p, winner); } catch (Exception ignored) {}
                        }

                        if (isWinner) {
                            result = ChatColor.GREEN + "" + ChatColor.BOLD + "VICTORY";
                            // Tambah Win Streak untuk Pemenang
                            if (p.getUniqueId().equals(winner.getUniqueId()) && plugin.leaderboardManager != null) {
                                plugin.leaderboardManager.addWin(winner.getUniqueId(), winner.getName(), kitName);
                            }
                        } else {
                            result = ChatColor.RED + "" + ChatColor.BOLD + "DEFEAT";
                            // Putus (Reset) Streak untuk yang Kalah
                            if (loser != null && p.getUniqueId().equals(loser.getUniqueId()) && plugin.leaderboardManager != null) {
                                plugin.leaderboardManager.resetStreak(loser.getUniqueId(), kitName);
                            }
                        }
                    } else {
                        // Jika tidak ada pemenang (DRAW), Putus Streak semuanya
                        if (plugin.leaderboardManager != null) {
                            plugin.leaderboardManager.resetStreak(p.getUniqueId(), kitName);
                        }
                    }

                    plugin.playerMatchResults.put(p.getUniqueId(), result);
                }

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        plugin.endedFightWinners.remove(fight);
                        for (Player p : fightPlayers) {
                            if (p != null) {
                                plugin.playerMatchResults.remove(p.getUniqueId());
                                plugin.playerMatchKills.remove(p.getUniqueId());
                            }
                        }
                    }
                }.runTaskLater(plugin, 160L);
            }

            if (fight != null && plugin.getMGetArena() != null) {
                try {
                    Object arena = plugin.getMGetArena().invoke(fight);
                    if (arena != null) {
                        String arenaName = (String) arena.getClass().getMethod("getName").invoke(arena);
                        forceFixBeds(arenaName, fight, 80L);
                    }
                } catch (Exception ex) {}
            }

            if (winner != null) {
                plugin.cleanupPlayer(winner.getUniqueId(), false);
                new BukkitRunnable() { @Override public void run() { if (winner.isOnline()) plugin.sendTitle(winner, plugin.getMsg("victory.title").replace("<player>", winner.getName()), plugin.getMsg("victory.subtitle").replace("<player>", winner.getName()), 10, 70, 20); } }.runTaskLater(plugin, 5L);
            }
            if (loser != null) {
                plugin.cleanupPlayer(loser.getUniqueId(), false);
                new BukkitRunnable() { @Override public void run() { if (loser.isOnline()) { String op = winner != null ? winner.getName() : "Unknown"; plugin.sendTitle(loser, plugin.getMsg("defeat.title").replace("<opponent>", op), plugin.getMsg("defeat.subtitle").replace("<opponent>", op), 10, 70, 20); } } }.runTaskLater(plugin, 5L);
            }
        } catch (Exception e) {}
    }

    public void handleRoundEnd(Event event) {
        try {
            Method getWinnerMethod;
            try { getWinnerMethod = event.getClass().getMethod("getWinner"); } catch (Exception e) { getWinnerMethod = event.getClass().getMethod("getRoundWinner"); }
            Player winner = (Player) getWinnerMethod.invoke(event);
            if (winner != null) {
                String kitName = plugin.getKitName(winner);
                boolean allowed = plugin.roundEndKits.isEmpty();
                if(!allowed && kitName != null) { for(String k : plugin.roundEndKits) if(k.equalsIgnoreCase(kitName)) { allowed = true; break;} }
                if(!allowed) return;

                Object fight = plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), winner);
                if (fight == null) return;

                if (plugin.getMGetArena() != null) {
                    try {
                        Object arena = plugin.getMGetArena().invoke(fight);
                        if (arena != null) {
                            String arenaName = (String) arena.getClass().getMethod("getName").invoke(arena);
                            forceFixBeds(arenaName, fight, 80L);
                        }
                    } catch (Exception ex) {}
                }

                String title = plugin.getMsg("round-end.title");
                String subtitle = plugin.getMsg("round-end.subtitle").replace("<winner>", winner.getName());
                plugin.sendTitle(winner, title, subtitle, 0, 60, 20);
                if (plugin.isHooked()) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getUniqueId().equals(winner.getUniqueId())) continue;
                        if (plugin.isInFight(p) && fight.equals(plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), p))) {
                            plugin.sendTitle(p, title, subtitle, 0, 60, 20);
                        }
                    }
                }
            }
        } catch (Exception e) {}
    }
}