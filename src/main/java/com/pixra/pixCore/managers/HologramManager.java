package com.pixra.pixCore.managers;

import com.pixra.pixCore.PixCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class HologramManager {

    private final PixCore plugin;
    private final Map<UUID, List<ArmorStand>> activeHolograms = new HashMap<>();

    private final Map<String, List<ArmorStand>> staticHolograms = new HashMap<>();
    private final Map<String, Long> previewTime = new HashMap<>();
    private File holoFile;
    private FileConfiguration holoConfig;

    public HologramManager(PixCore plugin) {
        this.plugin = plugin;
        loadHoloConfig();

        new BukkitRunnable() {
            @Override
            public void run() {
                clearAllGhosts();
                startUpdaterTask();
            }
        }.runTaskLater(plugin, 40L);
    }

    private void loadHoloConfig() {
        File folder = new File(plugin.getDataFolder(), "playerdata");
        if (!folder.exists()) folder.mkdirs();
        holoFile = new File(folder, "holograms.yml");
        if (!holoFile.exists()) {
            try { holoFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        holoConfig = YamlConfiguration.loadConfiguration(holoFile);
    }

    private void saveHoloConfig() {
        try { holoConfig.save(holoFile); } catch (IOException e) { e.printStackTrace(); }
    }

    public void reload() {
        loadHoloConfig();
        removeAllHolograms();
        clearAllGhosts();
    }

    public void createStaticHologram(Player player, String kitName) {
        kitName = ChatColor.stripColor(kitName).toLowerCase();

        removeStaticHologram(kitName);

        Location eyeLoc = player.getEyeLocation();

        holoConfig.set("leaderboards." + kitName + ".world", eyeLoc.getWorld().getName());
        holoConfig.set("leaderboards." + kitName + ".x", eyeLoc.getX());
        holoConfig.set("leaderboards." + kitName + ".y", eyeLoc.getY());
        holoConfig.set("leaderboards." + kitName + ".z", eyeLoc.getZ());
        holoConfig.set("leaderboards." + kitName + ".yaw", eyeLoc.getYaw());
        holoConfig.set("leaderboards." + kitName + ".pitch", eyeLoc.getPitch());
        saveHoloConfig();

        previewTime.put(kitName, System.currentTimeMillis() + 10000);
        player.sendMessage(ChatColor.GREEN + "[PixCore] Hologram berhasil disimpan! (Hologram ini hanya akan terlihat saat fase starting-countdown match berjalan). Preview 10 detik...");
        spawnStaticHologram(kitName, eyeLoc);
    }

    public void removeStaticHologram(String kitName) {
        kitName = ChatColor.stripColor(kitName).toLowerCase();

        String path = "leaderboards." + kitName;
        if (holoConfig.contains(path)) {
            World world = Bukkit.getWorld(holoConfig.getString(path + ".world"));
            if (world != null) {
                double x = holoConfig.getDouble(path + ".x");
                double y = holoConfig.getDouble(path + ".y");
                double z = holoConfig.getDouble(path + ".z");
                Location loc = new Location(world, x, y, z);

                clearGhostHolograms(loc, 10.0);
            }
        }

        removeStaticHologramEntities(kitName);
        holoConfig.set(path, null);
        saveHoloConfig();
    }

    private void removeStaticHologramEntities(String kitName) {
        List<ArmorStand> stands = staticHolograms.remove(kitName);
        if (stands != null) {
            for (ArmorStand stand : stands) {
                if (stand != null && !stand.isDead()) stand.remove();
            }
        }
    }

    private void clearAllGhosts() {
        if (!holoConfig.contains("leaderboards")) return;
        for (String kitName : holoConfig.getConfigurationSection("leaderboards").getKeys(false)) {
            String path = "leaderboards." + kitName;
            World world = Bukkit.getWorld(holoConfig.getString(path + ".world"));
            if (world == null) continue;

            double x = holoConfig.getDouble(path + ".x");
            double y = holoConfig.getDouble(path + ".y");
            double z = holoConfig.getDouble(path + ".z");
            Location loc = new Location(world, x, y, z);

            clearGhostHolograms(loc, 10.0);
        }
    }

    private void clearGhostHolograms(Location center, double radius) {
        if (center == null || center.getWorld() == null) return;
        try {
            for (Entity ent : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
                if (ent instanceof ArmorStand) {
                    ArmorStand as = (ArmorStand) ent;
                    if (!as.isVisible() && !as.hasBasePlate()) {
                        String name = as.getCustomName();
                        if (name == null ||
                                name.contains("WINSTREAK") ||
                                name.contains("Kit:") ||
                                name.contains("Reset in:") ||
                                name.contains("Win") ||
                                name.equals("Armor Stand") ||
                                name.equals(" ") ||
                                name.equals(ChatColor.translateAlternateColorCodes('&', "&r")) ||
                                name.matches(".*\\d+\\..*")) {
                            as.remove();
                        }
                    }
                }
            }
        } catch (Exception e) {}
    }

    private void spawnStaticHologram(String kitName, Location baseLoc) {
        Vector direction = baseLoc.getDirection();
        direction.setY(0).normalize();

        Vector leftDir = new Vector(-direction.getZ(), 0, direction.getX()).normalize().multiply(3.5);
        Location leftHoloLoc = baseLoc.clone().add(direction.clone().multiply(4.0)).add(leftDir);
        leftHoloLoc.setY(leftHoloLoc.getY() + 1.5);

        Vector rightDir = new Vector(direction.getZ(), 0, -direction.getX()).normalize().multiply(3.5);
        Location rightHoloLoc = baseLoc.clone().add(direction.clone().multiply(4.0)).add(rightDir);
        rightHoloLoc.setY(rightHoloLoc.getY() + 1.5);

        List<ArmorStand> stands = new ArrayList<>();
        stands.addAll(spawnHologramLines(leftHoloLoc, generateDailyLines(kitName)));
        stands.addAll(spawnHologramLines(rightHoloLoc, generateMonthlyLines(kitName)));

        staticHolograms.put(kitName, stands);
    }

    private void startUpdaterTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!holoConfig.contains("leaderboards")) return;

                for (String kitName : holoConfig.getConfigurationSection("leaderboards").getKeys(false)) {
                    String path = "leaderboards." + kitName;
                    World world = Bukkit.getWorld(holoConfig.getString(path + ".world"));
                    if (world == null) continue;

                    double x = holoConfig.getDouble(path + ".x");
                    double y = holoConfig.getDouble(path + ".y");
                    double z = holoConfig.getDouble(path + ".z");
                    float yaw = (float) holoConfig.getDouble(path + ".yaw");
                    float pitch = (float) holoConfig.getDouble(path + ".pitch");

                    Location loc = new Location(world, x, y, z, yaw, pitch);

                    boolean hasPlayerInCountdown = false;
                    if (loc.getChunk().isLoaded()) {
                        for (Player p : world.getPlayers()) {
                            if (p.getLocation().distanceSquared(loc) <= 900) {
                                if (plugin.frozenPlayers.contains(p.getUniqueId())) {
                                    hasPlayerInCountdown = true;
                                    break;
                                }
                            }
                        }
                    }

                    boolean isPreview = previewTime.getOrDefault(kitName, 0L) > System.currentTimeMillis();

                    if (hasPlayerInCountdown || isPreview) {
                        if (!staticHolograms.containsKey(kitName)) {
                            clearGhostHolograms(loc, 10.0);
                            spawnStaticHologram(kitName, loc);
                        }

                        List<ArmorStand> stands = staticHolograms.get(kitName);
                        boolean needsRespawn = false;
                        if (stands == null || stands.size() < 18) {
                            needsRespawn = true;
                        } else {
                            for (ArmorStand stand : stands) {
                                if (stand == null || !stand.isValid() || stand.isDead()) {
                                    needsRespawn = true;
                                    break;
                                }
                            }
                        }

                        if (needsRespawn) {
                            removeStaticHologramEntities(kitName);
                            clearGhostHolograms(loc, 10.0);
                            spawnStaticHologram(kitName, loc);
                            continue;
                        }

                        List<String> dailyLines = generateDailyLines(kitName);
                        List<String> monthlyLines = generateMonthlyLines(kitName);

                        for (int i = 0; i < 9; i++) {
                            ArmorStand stand = stands.get(i);
                            if (stand != null && !stand.isDead()) {
                                stand.setCustomName(ChatColor.translateAlternateColorCodes('&', dailyLines.get(i)));
                            }
                        }
                        for (int i = 0; i < 9; i++) {
                            ArmorStand stand = stands.get(i + 9);
                            if (stand != null && !stand.isDead()) {
                                stand.setCustomName(ChatColor.translateAlternateColorCodes('&', monthlyLines.get(i)));
                            }
                        }
                    } else {
                        if (staticHolograms.containsKey(kitName)) {
                            removeStaticHologramEntities(kitName);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void spawnLeaderboardHolograms(Player player, String kitName) {
        if (kitName == null) return;
        kitName = ChatColor.stripColor(kitName).toLowerCase();

        if (holoConfig.contains("leaderboards." + kitName)) {
            return;
        }

        removeHolograms(player);

        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection();
        direction.setY(0).normalize();

        Vector leftDir = new Vector(-direction.getZ(), 0, direction.getX()).normalize().multiply(3.5);
        Location leftHoloLoc = eyeLoc.clone().add(direction.clone().multiply(4.0)).add(leftDir);
        leftHoloLoc.setY(leftHoloLoc.getY() + 1.5);

        Vector rightDir = new Vector(direction.getZ(), 0, -direction.getX()).normalize().multiply(3.5);
        Location rightHoloLoc = eyeLoc.clone().add(direction.clone().multiply(4.0)).add(rightDir);
        rightHoloLoc.setY(rightHoloLoc.getY() + 1.5);

        List<ArmorStand> spawnedStands = new ArrayList<>();
        spawnedStands.addAll(spawnHologramLines(leftHoloLoc, generateDailyLines(kitName)));
        spawnedStands.addAll(spawnHologramLines(rightHoloLoc, generateMonthlyLines(kitName)));

        activeHolograms.put(player.getUniqueId(), spawnedStands);
    }

    public void removeHolograms(Player player) {
        List<ArmorStand> stands = activeHolograms.remove(player.getUniqueId());
        if (stands != null) {
            for (ArmorStand stand : stands) {
                if (stand != null && !stand.isDead()) stand.remove();
            }
        }
    }

    public void removeAllHolograms() {
        for (List<ArmorStand> stands : activeHolograms.values()) {
            if (stands != null) {
                for (ArmorStand stand : stands) {
                    if (stand != null && !stand.isDead()) stand.remove();
                }
            }
        }
        activeHolograms.clear();

        for (String kitName : staticHolograms.keySet()) {
            removeStaticHologramEntities(kitName);
        }
    }

    private List<String> generateDailyLines(String kitName) {
        List<String> lines = new ArrayList<>();
        lines.add("&b&lDAILY WINSTREAK");
        lines.add("&eKit: &f" + kitName.toUpperCase());
        lines.add("&7Reset in: &c" + plugin.leaderboardManager.getDailyCountdown());
        lines.add("&r");
        lines.addAll(getPaddedTop5(plugin.leaderboardManager.getTop5("daily", kitName)));
        return lines;
    }

    private List<String> generateMonthlyLines(String kitName) {
        List<String> lines = new ArrayList<>();
        lines.add("&6&lMONTHLY WINSTREAK");
        lines.add("&eKit: &f" + kitName.toUpperCase());
        lines.add("&7Reset in: &c" + plugin.leaderboardManager.getMonthlyCountdown());
        lines.add("&r");
        lines.addAll(getPaddedTop5(plugin.leaderboardManager.getTop5("monthly", kitName)));
        return lines;
    }

    private List<String> getPaddedTop5(List<Map.Entry<String, Integer>> top5) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            int rank = i + 1;
            String color = rank == 1 ? "&a" : (rank == 2 ? "&e" : (rank == 3 ? "&6" : "&f"));

            if (i < top5.size()) {
                Map.Entry<String, Integer> entry = top5.get(i);
                lines.add(color + rank + ". &f" + entry.getKey() + " &7- &d" + entry.getValue() + " Win");
            } else {
                lines.add(color + rank + ". &7&o-");
            }
        }
        return lines;
    }

    private List<ArmorStand> spawnHologramLines(Location startLoc, List<String> lines) {
        List<ArmorStand> stands = new ArrayList<>();
        Location currentLoc = startLoc.clone();

        for (String line : lines) {
            ArmorStand stand = (ArmorStand) startLoc.getWorld().spawnEntity(currentLoc, EntityType.ARMOR_STAND);
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setCustomNameVisible(true);
            stand.setCustomName(ChatColor.translateAlternateColorCodes('&', line));
            stand.setBasePlate(false);

            try { stand.setPersistent(false); } catch (NoSuchMethodError ignored) {}
            try { stand.setMarker(true); } catch (NoSuchMethodError ignored) {}

            stands.add(stand);

            currentLoc.subtract(0, 0.25, 0);
        }
        return stands;
    }
}