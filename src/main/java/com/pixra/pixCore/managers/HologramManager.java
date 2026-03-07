package com.pixra.pixCore.managers;

import com.pixra.pixCore.PixCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.*;

public class HologramManager {

    private final PixCore plugin;

    private final Map<UUID, List<ArmorStand>> playerHolograms = new HashMap<>();
    private final Set<String> registeredKits = new HashSet<>();
    private File perPlayerCfgFile;
    private FileConfiguration perPlayerCfg;

    private final Map<Integer, List<ArmorStand>> standingHolograms = new HashMap<>();
    private final Map<Integer, Integer> rotationIndex = new HashMap<>();
    private BukkitTask rotationTask = null;
    private BukkitTask countdownTask = null;
    private BukkitTask glowTask = null;
    private int glowFrame = 0;

    private File standingCfgFile;
    private FileConfiguration standingCfg;

    private static final double FORWARD = 4.0;
    private static final double SIDE = 3.0;
    private static final double LINE_GAP = -0.27;
    private static final int LINES_PLAYER = 8;
    private static final int LINES_STAND = 13;
    private static final double STAND_Y_OFFSET = 1.0;

    private static final Set<String> VALID_CATEGORIES = new HashSet<>(Arrays.asList("ws", "wins", "kills"));
    private static final Set<String> VALID_PERIODS = new HashSet<>(
            Arrays.asList("daily", "weekly", "monthly", "lifetime"));

    public HologramManager(PixCore plugin) {
        this.plugin = plugin;
        loadPerPlayerConfig();
        loadStandingConfig();
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanAllStandingGhosts();
                respawnAllStanding();
                startRotationTask();
                startCountdownUpdater();
                startGlowAnimation();
            }
        }.runTaskLater(plugin, 60L);
    }

    private void loadPerPlayerConfig() {
        File folder = new File(plugin.getDataFolder(), "playerdata");
        if (!folder.exists())
            folder.mkdirs();
        perPlayerCfgFile = new File(folder, "holograms.yml");
        if (!perPlayerCfgFile.exists()) {
            try {
                perPlayerCfgFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        perPlayerCfg = YamlConfiguration.loadConfiguration(perPlayerCfgFile);
        registeredKits.clear();
        for (String k : perPlayerCfg.getStringList("registered-kits"))
            registeredKits.add(k.toLowerCase());
    }

    private void savePerPlayerConfig() {
        perPlayerCfg.set("registered-kits", new ArrayList<>(registeredKits));
        try {
            perPlayerCfg.save(perPlayerCfgFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadStandingConfig() {
        standingCfgFile = new File(plugin.getDataFolder(), "standing-holograms.yml");
        if (!standingCfgFile.exists()) {
            plugin.saveResource("standing-holograms.yml", false);
            if (!standingCfgFile.exists()) {
                try {
                    standingCfgFile.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        standingCfg = YamlConfiguration.loadConfiguration(standingCfgFile);

        if (!standingCfg.contains("rotation-interval-seconds"))
            standingCfg.set("rotation-interval-seconds", 30);
        if (!standingCfg.contains("rotation-kits"))
            standingCfg.set("rotation-kits", Arrays.asList("bedfight", "sumo"));
        if (!standingCfg.contains("holograms"))
            standingCfg.set("holograms", new HashMap<>());
        saveStandingConfig();
    }

    private void saveStandingConfig() {
        try {
            standingCfg.save(standingCfgFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private List<String> getRotationKits() {
        List<String> kits = standingCfg.getStringList("rotation-kits");
        List<String> result = new ArrayList<>();
        for (String k : kits)
            result.add(k.toLowerCase());
        return result.isEmpty() ? Collections.singletonList("unknown") : result;
    }

    private int getRotationIntervalSeconds() {
        return Math.max(10, standingCfg.getInt("rotation-interval-seconds", 30));
    }

    public void reload() {
        hideAll();
        despawnAllStanding();
        if (rotationTask != null) {
            rotationTask.cancel();
            rotationTask = null;
        }
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        if (glowTask != null) {
            glowTask.cancel();
            glowTask = null;
        }
        loadPerPlayerConfig();
        loadStandingConfig();
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanAllStandingGhosts();
                respawnAllStanding();
                startRotationTask();
                startCountdownUpdater();
                startGlowAnimation();
            }
        }.runTaskLater(plugin, 20L);
    }

    public void registerKit(Player admin, String kitName) {
        kitName = strip(kitName);
        boolean added = registeredKits.add(kitName);
        savePerPlayerConfig();
        if (added) {
            admin.sendMessage(color("&a[PixCore] Kit &e" + kitName.toUpperCase()
                    + " &aregistered! Leaderboard hologram will appear in front of each player "
                    + "during a &e" + kitName.toUpperCase() + " &amatch countdown in every arena."));
        } else {
            admin.sendMessage(color("&e[PixCore] Kit &f" + kitName.toUpperCase() + " &ewas already registered."));
        }
    }

    public void unregisterKit(Player admin, String kitName) {
        kitName = strip(kitName);
        if (registeredKits.remove(kitName)) {
            savePerPlayerConfig();
            admin.sendMessage(color("&a[PixCore] Kit &e" + kitName.toUpperCase() + " &aunregistered."));
        } else {
            admin.sendMessage(color("&cKit &e" + kitName + " &cwas not registered."));
        }
    }

    public void registerKitConsole(String kitName) {
        registeredKits.add(strip(kitName));
        savePerPlayerConfig();
    }

    public void unregisterKitConsole(String kitName) {
        registeredKits.remove(strip(kitName));
        savePerPlayerConfig();
    }

    public Set<String> getRegisteredKits() {
        return Collections.unmodifiableSet(registeredKits);
    }

    public void showForPlayer(Player player, String kitName) {
        if (kitName == null)
            return;
        kitName = strip(kitName);
        if (!registeredKits.contains(kitName))
            return;
        if (plugin.leaderboardManager == null)
            return;
        if (!plugin.leaderboardManager.isKitEnabled(kitName))
            return;

        hideForPlayer(player);

        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().clone().setY(0);
        if (dir.lengthSquared() < 0.001)
            dir = new Vector(0, 0, 1);
        dir.normalize();

        Location anchor = eye.clone().add(dir.clone().multiply(FORWARD));
        anchor.setY(eye.getY() + 1.2);

        Vector left = new Vector(-dir.getZ(), 0, dir.getX()).normalize().multiply(SIDE);
        Vector right = new Vector(dir.getZ(), 0, -dir.getX()).normalize().multiply(SIDE);

        List<ArmorStand> stands = new ArrayList<>();
        stands.addAll(spawnColumnForPlayer(anchor.clone().add(left), buildPlayerLeft(kitName), LINES_PLAYER, player));
        stands.addAll(spawnColumnForPlayer(anchor.clone().add(right), buildPlayerRight(kitName), LINES_PLAYER, player));
        playerHolograms.put(player.getUniqueId(), stands);
    }

    public void hideForPlayer(Player player) {
        despawnList(playerHolograms.remove(player.getUniqueId()));
    }

    public void removeAllHolograms() {
        hideAll();
    }

    public void hideAll() {
        for (List<ArmorStand> s : new ArrayList<>(playerHolograms.values()))
            despawnList(s);
        playerHolograms.clear();
        despawnAllStanding();
        if (glowTask != null) {
            glowTask.cancel();
            glowTask = null;
        }
    }

    public boolean placeStandingHologram(Player admin, String category, String period) {
        category = category.toLowerCase();
        period = period.toLowerCase();

        if (!VALID_CATEGORIES.contains(category)) {
            admin.sendMessage(color("&cInvalid category. Use: &ews &c| &ewins &c| &ekills"));
            return false;
        }
        if (!VALID_PERIODS.contains(period)) {
            admin.sendMessage(color("&cInvalid period. Use: &edaily &c| &eweekly &c| &emonthly &c| &elifetime"));
            return false;
        }
        if (category.equals("ws") && !period.equals("daily")) {
            admin.sendMessage(color("&cWinstreak &e(ws)&c only supports period &edaily&c."));
            return false;
        }

        int id = nextStandingId();

        Location loc = admin.getLocation();
        double y = loc.getY() + STAND_Y_OFFSET;

        String path = "holograms." + id;
        standingCfg.set(path + ".world", loc.getWorld().getName());
        standingCfg.set(path + ".x", loc.getX());
        standingCfg.set(path + ".y", y);
        standingCfg.set(path + ".z", loc.getZ());
        standingCfg.set(path + ".category", category);
        standingCfg.set(path + ".period", period);
        saveStandingConfig();

        rotationIndex.put(id, 0);
        spawnStanding(id);

        admin.sendMessage(color("&a[PixCore] Standing hologram &e#" + id
                + " &aplaced! Category: &e" + category.toUpperCase()
                + " &a| Period: &e" + period.toUpperCase()
                + " &a| Rotates kits: &e" + String.join(", ", getRotationKits())));
        return true;
    }

    public void removeStandingHologram(Player admin, int id) {
        if (!standingCfg.contains("holograms." + id)) {
            admin.sendMessage(color("&cStanding hologram &e#" + id + " &cnot found."));
            return;
        }
        despawnStanding(id);
        standingCfg.set("holograms." + id, null);
        saveStandingConfig();
        admin.sendMessage(color("&a[PixCore] Standing hologram &e#" + id + " &aremoved."));
    }

    public void listStandingHolograms(Player admin) {
        ConfigurationSection sec = standingCfg.getConfigurationSection("holograms");
        if (sec == null || sec.getKeys(false).isEmpty()) {
            admin.sendMessage(color("&7No standing holograms placed."));
            return;
        }
        admin.sendMessage(color("&b&lStanding Holograms:"));
        for (String key : sec.getKeys(false)) {
            String path = "holograms." + key;
            String world = standingCfg.getString(path + ".world", "?");
            double x = standingCfg.getDouble(path + ".x");
            double y = standingCfg.getDouble(path + ".y");
            double z = standingCfg.getDouble(path + ".z");
            String category = standingCfg.getString(path + ".category", "?");
            String period = standingCfg.getString(path + ".period", "?");
            admin.sendMessage(color("  &e#" + key + " &7| &f" + category.toUpperCase() + " " + period
                    + " &7| " + world + " " + (int) x + "," + (int) y + "," + (int) z));
        }
    }

    private void respawnAllStanding() {
        ConfigurationSection sec = standingCfg.getConfigurationSection("holograms");
        if (sec == null)
            return;
        for (String key : sec.getKeys(false)) {
            try {
                int id = Integer.parseInt(key);
                if (!rotationIndex.containsKey(id))
                    rotationIndex.put(id, 0);
                despawnStanding(id);
                spawnStanding(id);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void spawnStanding(int id) {
        String path = "holograms." + id;
        String worldName = standingCfg.getString(path + ".world");
        if (worldName == null)
            return;
        World world = Bukkit.getWorld(worldName);
        if (world == null)
            return;

        double x = standingCfg.getDouble(path + ".x");
        double y = standingCfg.getDouble(path + ".y");
        double z = standingCfg.getDouble(path + ".z");
        String category = standingCfg.getString(path + ".category", "ws");
        String period = standingCfg.getString(path + ".period", "daily");

        List<String> kits = getRotationKits();
        int idx = rotationIndex.getOrDefault(id, 0) % kits.size();
        String kit = kits.get(idx);

        Location top = new Location(world, x, y, z);
        List<String> lines = buildStandingLines(category, period, kit);

        despawnStanding(id);
        standingHolograms.put(id, spawnColumn(top, lines, LINES_STAND));
    }

    private void despawnStanding(int id) {
        despawnList(standingHolograms.remove(id));
    }

    private void despawnAllStanding() {
        for (int id : new ArrayList<>(standingHolograms.keySet()))
            despawnStanding(id);
        standingHolograms.clear();
    }

    private void startRotationTask() {
        if (rotationTask != null)
            rotationTask.cancel();
        long intervalTicks = getRotationIntervalSeconds() * 20L;
        rotationTask = new BukkitRunnable() {
            @Override
            public void run() {
                ConfigurationSection sec = standingCfg.getConfigurationSection("holograms");
                if (sec == null)
                    return;
                List<String> kits = getRotationKits();
                for (String key : sec.getKeys(false)) {
                    try {
                        int id = Integer.parseInt(key);
                        int next = (rotationIndex.getOrDefault(id, 0) + 1) % kits.size();
                        rotationIndex.put(id, next);
                        spawnStanding(id);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }.runTaskTimer(plugin, intervalTicks, intervalTicks);
    }

    private void startCountdownUpdater() {
        if (countdownTask != null)
            countdownTask.cancel();
        countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (plugin.leaderboardManager == null)
                    return;
                ConfigurationSection sec = standingCfg.getConfigurationSection("holograms");
                if (sec == null)
                    return;
                for (String key : sec.getKeys(false)) {
                    try {
                        int id = Integer.parseInt(key);
                        List<ArmorStand> stands = standingHolograms.get(id);
                        if (stands == null || stands.size() < 3)
                            continue;
                        String period = standingCfg.getString("holograms." + id + ".period", "daily");
                        String newCountdown;
                        switch (period) {
                            case "daily":
                                newCountdown = plugin.leaderboardManager.getDailyCountdown();
                                break;
                            case "weekly":
                                newCountdown = plugin.leaderboardManager.getWeeklyCountdown();
                                break;
                            case "monthly":
                                newCountdown = plugin.leaderboardManager.getMonthlyCountdown();
                                break;
                            default:
                                newCountdown = null;
                                break;
                        }
                        if (newCountdown == null)
                            continue;
                        ArmorStand countdownStand = stands.get(2);
                        if (countdownStand != null && !countdownStand.isDead()) {
                            countdownStand.setCustomName(color("&7Resets in: &c" + newCountdown));
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private int nextStandingId() {
        ConfigurationSection sec = standingCfg.getConfigurationSection("holograms");
        if (sec == null)
            return 1;
        int max = 0;
        for (String k : sec.getKeys(false)) {
            try {
                max = Math.max(max, Integer.parseInt(k));
            } catch (NumberFormatException ignored) {
            }
        }
        return max + 1;
    }

    private List<String> buildPlayerLeft(String kit) {
        List<String> lines = new ArrayList<>();
        lines.add("&b&l✦ DAILY WINSTREAK ✦");
        lines.add("&eKit: &f" + kit.toUpperCase());
        lines.add("&7Resets in: &c" + plugin.leaderboardManager.getDailyCountdown());
        lines.addAll(topN(plugin.leaderboardManager.getTop("winstreak", "daily", kit, 5), 5));
        return padTo(lines, LINES_PLAYER);
    }

    private List<String> buildPlayerRight(String kit) {
        List<String> lines = new ArrayList<>();
        lines.add("&6&l✦ MONTHLY TOP WINS ✦");
        lines.add("&eKit: &f" + kit.toUpperCase());
        lines.add("&7Resets in: &c" + plugin.leaderboardManager.getMonthlyCountdown());
        lines.addAll(topN(plugin.leaderboardManager.getTop("wins", "monthly", kit, 5), 5));
        return padTo(lines, LINES_PLAYER);
    }

    private List<String> buildStandingLines(String category, String period, String kit) {
        List<String> lines = new ArrayList<>();

        String titleColor, titleLabel, categoryLabel;
        switch (category) {
            case "ws":
                titleColor = "&b";
                titleLabel = "✦ WINSTREAK ✦";
                categoryLabel = "Winstreak";
                break;
            case "wins":
                titleColor = "&a";
                titleLabel = "✦ TOP WINS ✦";
                categoryLabel = "Wins";
                break;
            default:
                titleColor = "&c";
                titleLabel = "✦ TOP KILLS ✦";
                categoryLabel = "Kills";
                break;
        }

        String periodLabel = period.substring(0, 1).toUpperCase() + period.substring(1);
        String countdown;
        switch (period) {
            case "daily":
                countdown = plugin.leaderboardManager.getDailyCountdown();
                break;
            case "weekly":
                countdown = plugin.leaderboardManager.getWeeklyCountdown();
                break;
            case "monthly":
                countdown = plugin.leaderboardManager.getMonthlyCountdown();
                break;
            default:
                countdown = null;
                break;
        }

        String dataCategory = category.equals("ws") ? "winstreak" : category;

        lines.add(titleColor + "&l" + periodLabel + " " + titleLabel);
        lines.add("&eKit: &f" + kit.toUpperCase());
        if (countdown != null) {
            lines.add("&7Resets in: &c" + countdown);
        } else {
            lines.add("&7Lifetime stats");
        }
        lines.addAll(topN(plugin.leaderboardManager.getTop(dataCategory, period, kit, 10), 10));
        return padTo(lines, LINES_STAND);
    }

    private List<String> topN(List<Map.Entry<String, Integer>> top, int n) {
        String[] colors = { "&a", "&e", "&6", "&f", "&7", "&7", "&7", "&7", "&7", "&7" };
        String[] icons = { "⚑ ", "★ ", "★ ", "  ", "  ", "  ", "  ", "  ", "  ", "  " };
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String c = i < colors.length ? colors[i] : "&7";
            String ic = i < icons.length ? icons[i] : "  ";
            if (i < top.size()) {
                Map.Entry<String, Integer> e = top.get(i);
                lines.add(c + ic + (i + 1) + ". &f" + e.getKey() + " &8- &d" + e.getValue());
            } else {
                lines.add(c + (i + 1) + ". &8&o-");
            }
        }
        return lines;
    }

    private List<String> padTo(List<String> lines, int size) {
        while (lines.size() < size)
            lines.add(" ");
        return lines.subList(0, size);
    }

    private List<ArmorStand> spawnColumn(Location top, List<String> lines, int maxLines) {
        return spawnColumnForPlayer(top, lines, maxLines, null);
    }

    private List<ArmorStand> spawnColumnForPlayer(Location top, List<String> lines, int maxLines, Player owner) {
        List<ArmorStand> stands = new ArrayList<>();
        Location cur = top.clone();
        int count = Math.min(lines.size(), maxLines);
        for (int i = 0; i < count; i++) {
            ArmorStand as = (ArmorStand) cur.getWorld().spawnEntity(cur, EntityType.ARMOR_STAND);
            as.setVisible(false);
            as.setGravity(false);
            as.setCustomNameVisible(true);
            as.setBasePlate(false);
            as.setCustomName(color(lines.get(i)));
            try {
                as.setPersistent(false);
            } catch (NoSuchMethodError ignored) {
            }
            try {
                as.setMarker(true);
            } catch (NoSuchMethodError ignored) {
            }
            stands.add(as);
            if (owner != null) {
                sendDestroyPacketToOthers(as, owner);
            }
            cur = cur.clone().add(0, LINE_GAP, 0);
        }
        return stands;
    }

    private void sendDestroyPacketToOthers(ArmorStand as, Player owner) {
        try {
            int entityId = as.getEntityId();

            String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            Class<?> packetClass;
            Object packet;

            try {
                packetClass = Class.forName("net.minecraft.network.protocol.game.PacketPlayOutEntityDestroy");
                try {
                    packet = packetClass.getConstructor(int.class).newInstance(entityId);
                } catch (NoSuchMethodException e) {
                    packet = packetClass.getConstructor(int[].class).newInstance((Object) new int[] { entityId });
                }
            } catch (ClassNotFoundException ex) {
                packetClass = Class.forName("net.minecraft.server." + version + ".PacketPlayOutEntityDestroy");
                packet = packetClass.getConstructor(int[].class).newInstance((Object) new int[] { entityId });
            }

            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.getUniqueId().equals(owner.getUniqueId()))
                    continue;
                try {
                    Object craftPlayer = other.getClass().getMethod("getHandle").invoke(other);
                    Object playerConn = craftPlayer.getClass().getField("playerConnection").get(craftPlayer);
                    playerConn.getClass().getMethod("sendPacket", getPacketClass(version)).invoke(playerConn, packet);
                } catch (Exception e2) {
                    try {
                        Object craftPlayer = other.getClass().getMethod("getHandle").invoke(other);
                        Object conn = null;
                        for (java.lang.reflect.Field f : craftPlayer.getClass().getFields()) {
                            String fname = f.getType().getSimpleName();
                            if (fname.equals("PlayerConnection") || fname.equals("ServerGamePacketListenerImpl")) {
                                conn = f.get(craftPlayer);
                                break;
                            }
                        }
                        if (conn == null) {
                            Class<?> cls = craftPlayer.getClass();
                            outer: while (cls != null) {
                                for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                                    f.setAccessible(true);
                                    String fname = f.getType().getSimpleName();
                                    if (fname.equals("PlayerConnection")
                                            || fname.equals("ServerGamePacketListenerImpl")) {
                                        conn = f.get(craftPlayer);
                                        break outer;
                                    }
                                }
                                cls = cls.getSuperclass();
                            }
                        }
                        if (conn != null) {
                            for (Method m : conn.getClass().getMethods()) {
                                if (m.getName().equals("sendPacket") || m.getName().equals("send")) {
                                    if (m.getParameterCount() == 1) {
                                        m.invoke(conn, packet);
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private Class<?> getPacketClass(String version) {
        try {
            return Class.forName("net.minecraft.network.protocol.Packet");
        } catch (ClassNotFoundException e) {
            try {
                return Class.forName("net.minecraft.server." + version + ".Packet");
            } catch (ClassNotFoundException ex) {
                return Object.class;
            }
        }
    }

    private void despawnList(List<ArmorStand> stands) {
        if (stands == null)
            return;
        for (ArmorStand as : stands)
            if (as != null && !as.isDead())
                as.remove();
    }

    private void cleanAllStandingGhosts() {
        ConfigurationSection sec = standingCfg.getConfigurationSection("holograms");
        if (sec == null)
            return;
        for (String key : sec.getKeys(false)) {
            try {
                int id = Integer.parseInt(key);
                String path = "holograms." + id;
                String wn = standingCfg.getString(path + ".world");
                if (wn == null)
                    continue;
                World w = Bukkit.getWorld(wn);
                if (w == null)
                    continue;
                Location loc = new Location(w,
                        standingCfg.getDouble(path + ".x"),
                        standingCfg.getDouble(path + ".y"),
                        standingCfg.getDouble(path + ".z"));
                cleanGhostsNear(loc, 5.0);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void cleanGhostsNear(Location center, double radius) {
        if (center == null || center.getWorld() == null)
            return;
        try {
            for (org.bukkit.entity.Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
                if (!(e instanceof ArmorStand))
                    continue;
                ArmorStand as = (ArmorStand) e;
                if (as.isVisible() || as.hasBasePlate())
                    continue;
                String n = as.getCustomName();
                if (n == null || n.trim().isEmpty()
                        || n.contains("WINSTREAK") || n.contains("TOP WINS") || n.contains("TOP KILLS")
                        || n.contains("Kit:") || n.contains("Resets in:") || n.contains("Lifetime")
                        || n.contains("✦") || n.contains("⚑") || n.contains("★"))
                    as.remove();
            }
        } catch (Exception ignored) {
        }
    }

    private void startGlowAnimation() {
        if (glowTask != null)
            glowTask.cancel();
        glowTask = new BukkitRunnable() {
            @Override
            public void run() {
                glowFrame++;

                for (Map.Entry<Integer, List<ArmorStand>> entry : new HashMap<>(standingHolograms).entrySet()) {
                    int id = entry.getKey();
                    List<ArmorStand> stands = entry.getValue();
                    if (stands == null || stands.isEmpty())
                        continue;
                    ArmorStand titleStand = stands.get(0);
                    if (titleStand == null || titleStand.isDead())
                        continue;

                    String category = standingCfg.getString("holograms." + id + ".category", "ws");
                    String period = standingCfg.getString("holograms." + id + ".period", "daily");
                    String periodLabel = period.substring(0, 1).toUpperCase() + period.substring(1);

                    String titleColor, titleLabel;
                    switch (category) {
                        case "ws":
                            titleColor = "&b";
                            titleLabel = "✦ WINSTREAK ✦";
                            break;
                        case "wins":
                            titleColor = "&a";
                            titleLabel = "✦ TOP WINS ✦";
                            break;
                        default:
                            titleColor = "&c";
                            titleLabel = "✦ TOP KILLS ✦";
                            break;
                    }

                    titleStand.setCustomName(
                            color(buildGlowTitle(periodLabel + " " + titleLabel, titleColor, glowFrame)));
                }

                for (List<ArmorStand> stands : new ArrayList<>(playerHolograms.values())) {
                    if (stands == null || stands.size() < LINES_PLAYER + 1)
                        continue;

                    ArmorStand leftTitle = stands.get(0);
                    ArmorStand rightTitle = stands.get(LINES_PLAYER);

                    if (leftTitle != null && !leftTitle.isDead())
                        leftTitle.setCustomName(color(buildGlowTitle("✦ DAILY WINSTREAK ✦", "&b", glowFrame)));
                    if (rightTitle != null && !rightTitle.isDead())
                        rightTitle.setCustomName(color(buildGlowTitle("✦ MONTHLY TOP WINS ✦", "&6", glowFrame)));
                }
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    private String buildGlowTitle(String text, String baseColor, int frame) {
        String dimColor = getDimColor(baseColor);
        int len = text.length();
        int glowPos = frame % (len + 6);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            int dist = Math.abs(i - glowPos);
            if (dist == 0) {
                sb.append("&f&l");
            } else if (dist == 1) {
                sb.append(baseColor).append("&l");
            } else {
                sb.append(dimColor).append("&l");
            }
            sb.append(text.charAt(i));
        }
        return sb.toString();
    }

    private String getDimColor(String baseColor) {
        switch (baseColor) {
            case "&b":
                return "&3";
            case "&a":
                return "&2";
            case "&c":
                return "&4";
            case "&6":
                return "&e";
            default:
                return "&8";
        }
    }

    private String strip(String s) {
        return ChatColor.stripColor(s).toLowerCase();
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}