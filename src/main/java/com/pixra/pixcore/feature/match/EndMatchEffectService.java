package com.pixra.pixcore.feature.match;

import com.pixra.pixcore.PixCore;
import com.pixra.pixcore.support.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

public final class EndMatchEffectService {

    private final PixCore plugin;

    private boolean soundEnabled;
    private Sound victorySoundPrimary;
    private Sound victorySoundSecondary;
    private Sound defeatSoundPrimary;
    private Sound defeatSoundSecondary;
    private boolean fireworksEnabled;
    private int fireworkBursts;
    private int fireworkIntervalTicks;
    private boolean fireworksMatchViewersOnly;
    private double effectMaxDistance;
    private double hubExclusionRadius;

    public EndMatchEffectService(PixCore plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        FileConfiguration config = plugin.getConfig();

        this.soundEnabled = resolveBoolean(config, true,
                "settings.end-match.sounds.enabled",
                "end-match.sounds.enabled",
                "settings.sounds.enabled");
        this.victorySoundPrimary = SoundUtil.getSoundByName(resolveString(config, "FIREWORK_LAUNCH",
                "settings.end-match.sounds.victory-primary",
                "end-match.sounds.victory-primary",
                "settings.sounds.victory-primary"));
        this.victorySoundSecondary = SoundUtil.getSoundByName(resolveString(config, "FIREWORK_TWINKLE_FAR",
                "settings.end-match.sounds.victory-secondary",
                "end-match.sounds.victory-secondary",
                "settings.sounds.victory-secondary"));
        this.defeatSoundPrimary = SoundUtil.getSoundByName(resolveString(config, "FIREWORK_LAUNCH",
                "settings.end-match.sounds.defeat-primary",
                "end-match.sounds.defeat-primary",
                "settings.sounds.defeat-primary"));
        this.defeatSoundSecondary = SoundUtil.getSoundByName(resolveString(config, "FIREWORK_TWINKLE_FAR",
                "settings.end-match.sounds.defeat-secondary",
                "end-match.sounds.defeat-secondary",
                "settings.sounds.defeat-secondary"));

        this.fireworksEnabled = resolveBoolean(config, true,
                "settings.end-match.fireworks.enabled",
                "end-match.fireworks.enabled",
                "settings.fireworks.enabled");
        this.fireworkBursts = Math.max(1, resolveInt(config, 4,
                "settings.end-match.fireworks.bursts",
                "end-match.fireworks.bursts",
                "settings.fireworks.bursts"));
        this.fireworkIntervalTicks = Math.max(4, resolveInt(config, 10,
                "settings.end-match.fireworks.interval-ticks",
                "end-match.fireworks.interval-ticks",
                "settings.fireworks.interval-ticks"));
        this.fireworksMatchViewersOnly = resolveBoolean(config, true,
                "settings.end-match.fireworks.visible-to-match-viewers-only",
                "end-match.fireworks.visible-to-match-viewers-only",
                "settings.fireworks.visible-to-match-viewers-only");
        this.effectMaxDistance = Math.max(16.0, resolveDouble(config, 48.0,
                "settings.end-match.effects.max-distance",
                "end-match.effects.max-distance",
                "settings.fireworks.max-distance"));
        this.hubExclusionRadius = Math.max(8.0, resolveDouble(config, 24.0,
                "settings.end-match.effects.hub-exclusion-radius",
                "end-match.effects.hub-exclusion-radius",
                "settings.fireworks.hub-exclusion-radius"));
    }

    public void play(Player player, boolean isVictory) {
        play(player, isVictory, java.util.Collections.singletonList(player));
    }

    public void play(Player player, boolean isVictory, Collection<? extends Player> viewers) {
        if (player == null || !player.isOnline()) return;

        if (isVictory) {
            if (soundEnabled && victorySoundSecondary != null) {
                playSoundToViewers(collectMatchViewers(player, viewers), player.getLocation(), victorySoundSecondary);
            }

            if (fireworksEnabled) {
                launchVictoryEffects(player, viewers);
            } else if (soundEnabled) {
                repeatPrimarySound(player, viewers, victorySoundPrimary);
            }
            return;
        }

        if (!soundEnabled) return;

        if (defeatSoundSecondary != null) {
            try { player.playSound(player.getLocation(), defeatSoundSecondary, 1.0f, 1.0f); }
            catch (Exception ignored) {}
        }

        repeatPrimarySound(player, java.util.Collections.singletonList(player), defeatSoundPrimary);
    }

    private void repeatPrimarySound(Player anchor, Collection<? extends Player> viewers, Sound primary) {
        if (primary == null) return;

        new BukkitRunnable() {
            int elapsedTicks = 0;

            @Override public void run() {
                if (!anchor.isOnline() || elapsedTicks > 60) {
                    cancel();
                    return;
                }

                playSoundToViewers(collectMatchViewers(anchor, viewers), anchor.getLocation(), primary);
                elapsedTicks += 10;
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private void launchVictoryEffects(Player winner, Collection<? extends Player> viewers) {
        new BukkitRunnable() {
            int burstIndex = 0;

            @Override public void run() {
                if (!winner.isOnline() || burstIndex >= fireworkBursts) {
                    cancel();
                    return;
                }

                List<Player> soundViewers = collectMatchViewers(winner, viewers);
                List<Player> visualViewers = collectVisualViewers(winner, viewers);
                Location launchLocation = computeLaunchLocation(winner, burstIndex);
                Vector velocity = computeLaunchVelocity(winner, burstIndex);
                boolean flying = isFlyingLike(winner);

                spawnLaunchParticles(launchLocation, velocity, visualViewers, burstIndex, flying);
                scheduleBurst(winner, launchLocation, velocity, visualViewers, burstIndex, flying);

                if (soundEnabled && victorySoundPrimary != null) {
                    playSoundToViewers(soundViewers, launchLocation, victorySoundPrimary);
                }

                burstIndex++;
            }
        }.runTaskTimer(plugin, 0L, fireworkIntervalTicks);
    }

    private void spawnLaunchParticles(Location launchLocation, Vector velocity,
                                      Collection<? extends Player> viewers, int burstIndex, boolean flying) {
        if (launchLocation == null || launchLocation.getWorld() == null) return;

        int travelSteps = flying ? 6 : 4;
        Vector travel = velocity.clone();
        if (travel.lengthSquared() < 0.01) {
            travel = new Vector(0.0, flying ? 0.55 : 0.42, 0.0);
        }
        Vector stepVector = travel.clone().multiply(0.34);
        Color[] palette = getPalette(burstIndex);
        double spread = flying ? 0.24 : 0.16;
        int cloudCount = flying ? 16 : 10;
        int sparkCount = flying ? 8 : 5;

        for (Player viewer : viewers) {
            if (!shouldSendToViewer(viewer, launchLocation)) continue;

            boolean modernCloud = spawnParticleCompat(viewer, launchLocation, "CLOUD",
                    cloudCount, spread, 0.10, spread, 0.01);
            boolean modernSpark = spawnParticleCompat(viewer, launchLocation.clone().add(0.0, 0.18, 0.0),
                    "FIREWORK", sparkCount, spread * 0.85, 0.12, spread * 0.85, 0.01)
                    || spawnParticleCompat(viewer, launchLocation.clone().add(0.0, 0.18, 0.0),
                    "FIREWORKS_SPARK", sparkCount, spread * 0.85, 0.12, spread * 0.85, 0.01);

            if (!modernCloud && !modernSpark) {
                playLegacyLaunchEffects(viewer, launchLocation, flying);
            }

            for (int step = 0; step < travelSteps; step++) {
                Location point = launchLocation.clone().add(stepVector.clone().multiply(step + 1));
                Color stepColor = palette[step % palette.length];
                spawnMixedColorParticle(viewer, point, stepColor, flying ? 1.35f : 1.0f);
                if (step % 2 == 0) {
                    spawnMixedColorParticle(viewer, point.clone().add(0.06, 0.0, -0.06),
                            palette[(step + 1) % palette.length], 0.85f);
                }
            }
        }
    }

    private void scheduleBurst(Player winner, Location launchLocation, Vector velocity,
                               Collection<? extends Player> viewers, int burstIndex, boolean flying) {
        int travelTicks = flying ? 6 : 4;
        new BukkitRunnable() {
            @Override public void run() {
                if (!winner.isOnline() || launchLocation.getWorld() == null) {
                    cancel();
                    return;
                }

                Location burstCenter = computeBurstCenter(winner, launchLocation, velocity, flying);
                spawnBurstParticles(burstCenter, viewers, burstIndex, flying);
                cancel();
            }
        }.runTaskLater(plugin, travelTicks);
    }

    private Location computeBurstCenter(Player player, Location launchLocation, Vector velocity, boolean flying) {
        Vector carry = velocity.clone().setY(0.0).multiply(1.45);
        double vertical = flying ? 3.6 : (!isOnGroundCompat(player) || Math.abs(player.getVelocity().getY()) > 0.15 ? 3.0 : 2.45);
        return launchLocation.clone().add(carry).add(0.0, vertical, 0.0);
    }

    private void spawnBurstParticles(Location center, Collection<? extends Player> viewers,
                                     int burstIndex, boolean flying) {
        if (center == null || center.getWorld() == null) return;

        Color[] palette = getPalette(burstIndex);
        int points = flying ? 42 : 30;
        double radius = flying ? 1.65 : 1.30;

        for (Player viewer : viewers) {
            if (!shouldSendToViewer(viewer, center)) continue;

            boolean sparkPlayed = spawnParticleCompat(viewer, center, "FIREWORK",
                    flying ? 26 : 18, radius * 0.50, radius * 0.35, radius * 0.50, 0.02)
                    || spawnParticleCompat(viewer, center, "FIREWORKS_SPARK",
                    flying ? 26 : 18, radius * 0.50, radius * 0.35, radius * 0.50, 0.02);
            if (!sparkPlayed) {
                playEffectCompat(viewer, center, "FIREWORKS_SPARK", 0);
            }

            for (int i = 0; i < points; i++) {
                Vector offset = randomBurstOffset(radius, flying);
                Location point = center.clone().add(offset);
                Color color = palette[i % palette.length];
                spawnMixedColorParticle(viewer, point, color, 1.25f);

                if (i % 3 == 0) {
                    Color accent = palette[(i + 1) % palette.length];
                    spawnMixedColorParticle(viewer, point.clone().add(offset.clone().multiply(0.10)), accent, 0.85f);
                }
            }
        }
    }

    private Vector randomBurstOffset(double radius, boolean flying) {
        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
        double theta = random.nextDouble(0.0, Math.PI * 2.0);
        double phi = random.nextDouble(0.0, Math.PI);
        double scale = radius * (0.45 + random.nextDouble() * (flying ? 0.75 : 0.60));

        double x = Math.sin(phi) * Math.cos(theta) * scale;
        double y = Math.cos(phi) * scale * 0.85;
        double z = Math.sin(phi) * Math.sin(theta) * scale;
        return new Vector(x, y, z);
    }

    private Color[] getPalette(int burstIndex) {
        switch (burstIndex % 4) {
            case 1:
                return new Color[]{Color.AQUA, Color.WHITE, Color.BLUE, Color.TEAL};
            case 2:
                return new Color[]{Color.FUCHSIA, Color.ORANGE, Color.RED, Color.PURPLE};
            case 3:
                return new Color[]{Color.LIME, Color.YELLOW, Color.GREEN, Color.WHITE};
            default:
                return new Color[]{Color.ORANGE, Color.YELLOW, Color.RED, Color.WHITE};
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean spawnParticleCompat(Player viewer, Location location, String particleName, int count,
                                        double offsetX, double offsetY, double offsetZ, double extra) {
        try {
            Class<?> particleClass = Class.forName("org.bukkit.Particle");
            Object particle = Enum.valueOf((Class<? extends Enum>) particleClass.asSubclass(Enum.class), particleName);
            Method spawnParticle = viewer.getClass().getMethod("spawnParticle", particleClass, Location.class,
                    int.class, double.class, double.class, double.class, double.class);
            spawnParticle.invoke(viewer, particle, location, count, offsetX, offsetY, offsetZ, extra);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void playLegacyLaunchEffects(Player viewer, Location location, boolean flying) {
        playEffectCompat(viewer, location, "FIREWORK_SHOOT", 0);
        playEffectCompat(viewer, location, "SMOKE", 4);
        if (flying) {
            playEffectCompat(viewer, location.clone().add(0.0, 0.25, 0.0), "SMOKE", 4);
        }
    }

    private void spawnMixedColorParticle(Player viewer, Location location, Color color, float size) {
        if (spawnModernDustParticle(viewer, location, color, size)) {
            return;
        }
        spawnLegacyColoredDust(viewer, location, color);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean spawnModernDustParticle(Player viewer, Location location, Color color, float size) {
        try {
            Class<?> particleClass = Class.forName("org.bukkit.Particle");
            Object particle;
            try {
                particle = Enum.valueOf((Class<? extends Enum>) particleClass.asSubclass(Enum.class), "DUST");
            } catch (Throwable ignored) {
                particle = Enum.valueOf((Class<? extends Enum>) particleClass.asSubclass(Enum.class), "REDSTONE");
            }

            Class<?> dustOptionsClass = Class.forName("org.bukkit.Particle$DustOptions");
            Object dustOptions = dustOptionsClass.getConstructor(Color.class, float.class).newInstance(color, size);

            for (Method method : viewer.getClass().getMethods()) {
                if (!method.getName().equals("spawnParticle")) continue;
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 9 && params[0] == particleClass && params[1] == Location.class && params[8] == Object.class) {
                    method.invoke(viewer, particle, location, 1, 0.0, 0.0, 0.0, 0.0, dustOptions);
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private void spawnLegacyColoredDust(Player viewer, Location location, Color color) {
        if (sendLegacyColoredDustPacket(viewer, location, color)) {
            return;
        }

        try {
            Object spigot = viewer.getClass().getMethod("spigot").invoke(viewer);
            Method playEffect = spigot.getClass().getMethod("playEffect", Location.class, Effect.class,
                    int.class, int.class, float.class, float.class, float.class, float.class, int.class, int.class);

            float red = normalizeLegacyColor(color.getRed());
            float green = normalizeLegacyColor(color.getGreen());
            float blue = normalizeLegacyColor(color.getBlue());
            playEffect.invoke(spigot, location, Effect.valueOf("COLOURED_DUST"),
                    0, 0, red, green, blue, 1.0f, 0, 64);
        } catch (Throwable ignored) {
            playEffectCompat(viewer, location, "FIREWORKS_SPARK", 0);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean sendLegacyColoredDustPacket(Player viewer, Location location, Color color) {
        try {
            String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            Class<?> enumParticleClass = Class.forName("net.minecraft.server." + version + ".EnumParticle");
            Object particle = Enum.valueOf((Class<? extends Enum>) enumParticleClass.asSubclass(Enum.class), "REDSTONE");
            Class<?> packetClass = Class.forName("net.minecraft.server." + version + ".PacketPlayOutWorldParticles");
            Class<?> packetBase = Class.forName("net.minecraft.server." + version + ".Packet");

            Constructor<?> ctor = packetClass.getConstructor(enumParticleClass, boolean.class,
                    float.class, float.class, float.class,
                    float.class, float.class, float.class,
                    float.class, int.class, int[].class);

            float red = normalizeLegacyColor(color.getRed());
            float green = normalizeLegacyColor(color.getGreen());
            float blue = normalizeLegacyColor(color.getBlue());

            Object packet = ctor.newInstance(particle, true,
                    (float) location.getX(), (float) location.getY(), (float) location.getZ(),
                    red, green, blue, 1.0f, 0, new int[0]);

            Object handle = viewer.getClass().getMethod("getHandle").invoke(viewer);
            Object connection = handle.getClass().getField("playerConnection").get(handle);
            connection.getClass().getMethod("sendPacket", packetBase).invoke(connection, packet);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private float normalizeLegacyColor(int channel) {
        float normalized = channel / 255.0f;
        return normalized <= 0.0f ? 1.0E-4f : normalized;
    }

    private void playEffectCompat(Player viewer, Location location, String effectName, int data) {
        try {
            Effect effect = Effect.valueOf(effectName);
            viewer.playEffect(location, effect, data);
        } catch (Throwable ignored) {}
    }

    private void playSoundToViewers(Collection<? extends Player> viewers, Location location, Sound sound) {
        if (sound == null || location == null) return;

        for (Player viewer : viewers) {
            if (!shouldSendToViewer(viewer, location)) continue;
            try { viewer.playSound(location, sound, 1.0f, 1.0f); }
            catch (Exception ignored) {}
        }
    }

    private List<Player> collectMatchViewers(Player anchor, Collection<? extends Player> viewers) {
        LinkedHashMap<UUID, Player> collected = new LinkedHashMap<>();

        if (anchor != null && anchor.isOnline()) {
            collected.put(anchor.getUniqueId(), anchor);
        }

        if (viewers != null) {
            for (Player viewer : viewers) {
                if (viewer == null || !viewer.isOnline()) continue;
                if (anchor != null && anchor.getWorld() != viewer.getWorld()) continue;
                collected.put(viewer.getUniqueId(), viewer);
            }
        }

        return new ArrayList<>(collected.values());
    }

    private List<Player> collectVisualViewers(Player anchor, Collection<? extends Player> viewers) {
        if (fireworksMatchViewersOnly || anchor == null || !anchor.isOnline()) {
            return collectMatchViewers(anchor, viewers);
        }

        LinkedHashMap<UUID, Player> collected = new LinkedHashMap<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online == null || !online.isOnline()) continue;
            if (online.getWorld() != anchor.getWorld()) continue;
            collected.put(online.getUniqueId(), online);
        }
        return new ArrayList<>(collected.values());
    }

    private boolean shouldSendToViewer(Player viewer, Location effectLocation) {
        if (viewer == null || !viewer.isOnline() || effectLocation == null || effectLocation.getWorld() == null) {
            return false;
        }
        if (viewer.getWorld() != effectLocation.getWorld()) return false;

        double maxDistanceSquared = effectMaxDistance * effectMaxDistance;
        try {
            if (viewer.getLocation().distanceSquared(effectLocation) > maxDistanceSquared) {
                return false;
            }
        } catch (Exception ignored) {
            return false;
        }

        Location hub = plugin.resolveHubLocation(viewer);
        if (hub != null && hub.getWorld() == effectLocation.getWorld()) {
            double hubRadiusSquared = hubExclusionRadius * hubExclusionRadius;
            try {
                if (viewer.getLocation().distanceSquared(hub) <= hubRadiusSquared) {
                    return false;
                }
            } catch (Exception ignored) {}
        }

        return true;
    }

    private Location computeLaunchLocation(Player player, int burstIndex) {
        Location base = player.getLocation().clone();
        Vector velocity = player.getVelocity().clone();
        Vector horizontalVelocity = velocity.clone().setY(0.0);
        Vector direction = horizontalVelocity.lengthSquared() > 0.02
                ? horizontalVelocity.clone()
                : base.getDirection().clone().setY(0.0);

        if (direction.lengthSquared() < 1.0E-4) {
            double yawRadians = Math.toRadians(base.getYaw());
            direction = new Vector(-Math.sin(yawRadians), 0.0, Math.cos(yawRadians));
        }
        direction.normalize();

        Vector side = new Vector(-direction.getZ(), 0.0, direction.getX()).normalize();
        double horizontalSpeed = horizontalVelocity.length();
        boolean flying = isFlyingLike(player);
        boolean airborne = flying || !isOnGroundCompat(player) || Math.abs(velocity.getY()) > 0.15;

        double sideOffset = (burstIndex % 2 == 0 ? 0.72 : -0.72) * (horizontalSpeed > 0.55 ? 1.15 : 1.0);
        double backOffset = horizontalSpeed > 0.12 ? Math.min(1.40, 0.45 + (horizontalSpeed * 1.35)) : 0.24;
        double yOffset = 0.18;

        if (flying) {
            yOffset = -1.10;
        } else if (airborne && velocity.getY() > 0.12) {
            yOffset = -0.55;
        } else if (airborne) {
            yOffset = -0.28;
        }

        Location launch = base.add(0.0, 0.20, 0.0);
        launch.add(side.multiply(sideOffset));
        launch.subtract(direction.multiply(backOffset));
        launch.add(0.0, yOffset, 0.0);
        return launch;
    }

    private Vector computeLaunchVelocity(Player player, int burstIndex) {
        Vector playerVelocity = player.getVelocity().clone();
        Vector horizontalVelocity = playerVelocity.clone().setY(0.0);
        Vector lateralCarry = horizontalVelocity.lengthSquared() > 0.02
                ? horizontalVelocity.clone().multiply(0.55)
                : new Vector(0.0, 0.0, 0.0);

        Vector look = player.getLocation().getDirection().clone().setY(0.0);
        if (look.lengthSquared() < 1.0E-4) {
            look = new Vector(0.0, 0.0, 1.0);
        }
        look.normalize();

        Vector sideNudge = new Vector(-look.getZ(), 0.0, look.getX()).normalize()
                .multiply(burstIndex % 2 == 0 ? 0.10 : -0.10);
        double verticalBoost = isFlyingLike(player)
                ? 1.15
                : (!isOnGroundCompat(player) || Math.abs(playerVelocity.getY()) > 0.15 ? 0.95 : 0.78);

        return lateralCarry.add(sideNudge).setY(verticalBoost);
    }

    private boolean isFlyingLike(Player player) {
        return player.isFlying() || isGlidingCompat(player) || (player.getAllowFlight() && !isOnGroundCompat(player));
    }

    private boolean isGlidingCompat(Player player) {
        try {
            Method method = player.getClass().getMethod("isGliding");
            Object result = method.invoke(player);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isOnGroundCompat(Player player) {
        try {
            Method method = player.getClass().getMethod("isOnGround");
            Object result = method.invoke(player);
            if (result instanceof Boolean) return (Boolean) result;
        } catch (Throwable ignored) {}

        try {
            Location feet = player.getLocation().clone().subtract(0.0, 0.15, 0.0);
            return feet.getBlock().getType() != org.bukkit.Material.AIR;
        } catch (Throwable ignored) {
            return Math.abs(player.getVelocity().getY()) < 0.08;
        }
    }

    private boolean resolveBoolean(FileConfiguration config, boolean def, String... paths) {
        for (String path : paths) {
            if (path != null && config.contains(path)) {
                return config.getBoolean(path, def);
            }
        }
        return def;
    }

    private int resolveInt(FileConfiguration config, int def, String... paths) {
        for (String path : paths) {
            if (path != null && config.contains(path)) {
                return config.getInt(path, def);
            }
        }
        return def;
    }

    private double resolveDouble(FileConfiguration config, double def, String... paths) {
        for (String path : paths) {
            if (path != null && config.contains(path)) {
                return config.getDouble(path, def);
            }
        }
        return def;
    }

    private String resolveString(FileConfiguration config, String def, String... paths) {
        for (String path : paths) {
            if (path != null && config.contains(path)) {
                return config.getString(path, def);
            }
        }
        return def;
    }
}
