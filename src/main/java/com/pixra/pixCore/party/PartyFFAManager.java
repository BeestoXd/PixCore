package com.pixra.pixCore.party;

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
import org.bukkit.block.BlockFace;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.Color;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PartyFFAManager implements Listener {

    private final PixCore plugin;

    private Class<?> partyFFAClass;
    private Method getPartyMethod;
    private Method getAliveMethod;
    private Method hasEndedMethod;

    private Method partyGetPlayersMethod;
    private Method partyGetMembersNamesMethod;
    private Method partyGetMembersMethod;

    private static final Color[] FFA_COLORS = {
            Color.BLUE, Color.RED, Color.LIME, Color.YELLOW,
            Color.AQUA, Color.WHITE, Color.PURPLE, Color.ORANGE
    };

    private final Set<UUID> pendingRespawnRedirect = new HashSet<>();
    private final Set<UUID> bedBrokenPlayers = new HashSet<>();
    private final Map<UUID, ItemStack[]> savedArmorColors = new HashMap<>();
    private final Map<UUID, Color> playerFfaColors = new HashMap<>();

    private final Map<Object, Location[]> pendingFightSpawns = new HashMap<>();

    private final Map<UUID, Location> pendingInitialSpawns = new HashMap<>();

    private final Map<Object, Map<UUID, Integer>> ffaBridgeScores         = new HashMap<>();
    private final Map<UUID, Long>                 ffaBridgePortalCooldown = new HashMap<>();
    private final Set<Object>                     ffaBridgeEndedFights    = new HashSet<>();

    private final Set<UUID>                       ffaBridgeCustomSpectators = new HashSet<>();

    public PartyFFAManager(PixCore plugin) {
        this.plugin = plugin;
        try {
            partyFFAClass = Class.forName("ga.strikepractice.fights.party.partyfights.PartyFFA");
            getPartyMethod = partyFFAClass.getMethod("getParty");
            getAliveMethod = partyFFAClass.getMethod("getAlive");
            try {
                hasEndedMethod = partyFFAClass.getMethod("hasEnded");
            } catch (Exception ignored) {
            }

            try {
                Class<?> partyClass = Class.forName("ga.strikepractice.party.Party");
                try {
                    partyGetPlayersMethod = partyClass.getMethod("getPlayers");
                } catch (Exception ignored) {
                }
                try {
                    partyGetMembersNamesMethod = partyClass.getMethod("getMembersNames");
                } catch (Exception ignored) {
                }
                try {
                    partyGetMembersMethod = partyClass.getMethod("getMembers");
                } catch (Exception ignored) {
                }
            } catch (Exception ignored) {
            }

        } catch (Exception e) {
            plugin.getLogger().warning("[PartyFFAManager] Could not hook PartyFFA class — features disabled.");
        }
        registerEvents();
    }

    public void clearPlayerState(UUID uid) {
        pendingRespawnRedirect.remove(uid);
        bedBrokenPlayers.remove(uid);
        savedArmorColors.remove(uid);
        playerFfaColors.remove(uid);
        pendingInitialSpawns.remove(uid);
        ffaBridgePortalCooldown.remove(uid);
        ffaBridgeCustomSpectators.remove(uid);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        clearPlayerState(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearPlayerState(event.getPlayer().getUniqueId());
    }

    @SuppressWarnings("unchecked")
    private void registerEvents() {
        try {
            Class<? extends Event> startClass = (Class<? extends Event>) Class
                    .forName("ga.strikepractice.events.PartyFFAStartEvent");

            Bukkit.getPluginManager().registerEvent(startClass, this, EventPriority.LOWEST,
                    (listener, event) -> overrideArenaCenter(event), plugin);

            Bukkit.getPluginManager().registerEvent(startClass, this, EventPriority.MONITOR,
                    (listener, event) -> handleFightStart(event), plugin);

        } catch (Exception e) {
            plugin.getLogger().warning("[PartyFFAManager] Could not register PartyFFAStartEvent.");
        }

        try {
            Class<? extends Event> endClass = (Class<? extends Event>) Class
                    .forName("ga.strikepractice.events.PartyFFAEndEvent");
            Bukkit.getPluginManager().registerEvent(endClass, this, EventPriority.MONITOR,
                    (listener, event) -> handleFightEnd(event), plugin);
        } catch (Exception e) {
            plugin.getLogger().warning("[PartyFFAManager] Could not register PartyFFAEndEvent.");
        }
    }

    public boolean isPartyFFA(Object fight) {
        return partyFFAClass != null && partyFFAClass.isInstance(fight);
    }

    public void startRespawnRedirect(Player player) {
        if (!isPlayerInPartyFFA(player))
            return;
        Location spawn = plugin.arenaSpawnLocations.get(player.getUniqueId());
        if (spawn == null)
            return;

        pendingInitialSpawns.remove(player.getUniqueId());
        pendingRespawnRedirect.add(player.getUniqueId());
        final Location fixedSpawn = spawn.clone();
        final UUID uid = player.getUniqueId();

        new BukkitRunnable() {
            @Override
            public void run() {
                if (pendingRespawnRedirect.remove(uid) && player.isOnline()) {
                    player.teleport(fixedSpawn);
                }
            }
        }.runTaskLater(plugin, 5L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        UUID uid = event.getPlayer().getUniqueId();

        if (pendingRespawnRedirect.remove(uid)) {
            Location spawn = plugin.arenaSpawnLocations.get(uid);
            if (spawn != null && event.getTo() != null) {
                event.setTo(spawn.clone());
            }
        } else {

            Location initialSpawn = pendingInitialSpawns.get(uid);
            if (initialSpawn != null && event.getTo() != null) {
                event.setTo(initialSpawn.clone());
            }
        }
    }

    public String getFfaColorCode(Player p) {
        Color color = playerFfaColors.get(p.getUniqueId());
        if (color == null) return null;
        if (color.equals(Color.BLUE))   return "§9";
        if (color.equals(Color.RED))    return "§c";
        if (color.equals(Color.LIME))   return "§a";
        if (color.equals(Color.YELLOW)) return "§e";
        if (color.equals(Color.AQUA))   return "§b";
        if (color.equals(Color.WHITE))  return "§f";
        if (color.equals(Color.PURPLE)) return "§5";
        if (color.equals(Color.ORANGE)) return "§6";
        return "§f";
    }

    public boolean restoreArmorColor(Player p) {
        Color color = playerFfaColors.get(p.getUniqueId());
        if (color == null)
            return false;

        boolean changed = colorArmorContents(p, color);
        changed |= colorMainInventory(p, color);
        if (changed)
            p.updateInventory();
        return changed;
    }

    private ItemStack[] cloneArmor(ItemStack[] armor) {
        ItemStack[] copy = new ItemStack[armor.length];
        for (int i = 0; i < armor.length; i++)
            copy[i] = armor[i] != null ? armor[i].clone() : null;
        return copy;
    }

    private void applyFfaColorFull(Player p, Color color) {
        boolean changed = colorArmorContents(p, color);
        changed |= colorMainInventory(p, color);
        if (changed)
            p.updateInventory();
    }

    private boolean colorArmorContents(Player p, Color color) {
        ItemStack[] armor = p.getInventory().getArmorContents();
        if (armor == null)
            return false;
        ItemStack[] recolored = cloneArmor(armor);
        for (ItemStack item : recolored)
            plugin.colorItem(item, color);
        return applyArmorContents(p, recolored);
    }

    private boolean colorMainInventory(Player p, Color color) {
        boolean changed = false;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack current = p.getInventory().getItem(slot);
            if (isEmpty(current))
                continue;

            ItemStack recolored = current.clone();
            plugin.colorItem(recolored, color);
            if (itemStacksEqual(current, recolored))
                continue;

            p.getInventory().setItem(slot, recolored);
            changed = true;
        }
        return changed;
    }

    private boolean applyArmorContents(Player p, ItemStack[] armor) {
        if (itemStackArraysEqual(p.getInventory().getArmorContents(), armor))
            return false;
        p.getInventory().setArmorContents(cloneArmor(armor));
        return true;
    }

    private boolean itemStackArraysEqual(ItemStack[] first, ItemStack[] second) {
        int max = Math.max(first != null ? first.length : 0, second != null ? second.length : 0);
        for (int i = 0; i < max; i++) {
            ItemStack a = (first != null && i < first.length) ? first[i] : null;
            ItemStack b = (second != null && i < second.length) ? second[i] : null;
            if (!itemStacksEqual(a, b))
                return false;
        }
        return true;
    }

    private boolean itemStacksEqual(ItemStack first, ItemStack second) {
        if (isEmpty(first) && isEmpty(second))
            return true;
        return java.util.Objects.equals(first, second);
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == org.bukkit.Material.AIR;
    }

    public void markBedBroken(Player player) {
        bedBrokenPlayers.add(player.getUniqueId());
    }

    public boolean isBedBroken(Player player) {
        return bedBrokenPlayers.contains(player.getUniqueId());
    }

    public void handleBedBreak(Block bedBlock, Object fight) {
        if (!isPartyFFA(fight))
            return;
        Location bedCenter = bedBlock.getLocation().add(0.5, 0.5, 0.5);
        Player bedOwner = null;
        double minDist = Double.MAX_VALUE;
        for (Player p : getAllPlayers(fight)) {
            if (p == null)
                continue;
            Location pSpawn = plugin.arenaSpawnLocations.get(p.getUniqueId());
            if (pSpawn == null)
                continue;
            double dist = bedCenter.distanceSquared(pSpawn);
            if (dist < minDist) {
                minDist = dist;
                bedOwner = p;
            }
        }
        if (bedOwner != null) {
            markBedBroken(bedOwner);
        }
    }

    public boolean isPartyFFAFight(Player player) {
        return isPlayerInPartyFFA(player);
    }

    private Object getArenaFromFight(Object fight) {
        Method mGetArena = plugin.getMGetArena();
        if (mGetArena != null) {
            try {
                return mGetArena.invoke(fight);
            } catch (Exception ignored) {
            }
        }
        try {
            return fight.getClass().getMethod("getArena").invoke(fight);
        } catch (Exception ignored) {
        }
        return null;
    }

    private void overrideArenaCenter(Event event) {
        try {
            Object fight = event.getClass().getMethod("getFight").invoke(event);
            if (fight == null || !isPartyFFA(fight))
                return;
            Object arena = getArenaFromFight(fight);
            if (arena == null)
                return;

            Location loc1 = null, loc2 = null;
            try {
                loc1 = (Location) arena.getClass().getMethod("getLoc1").invoke(arena);
            } catch (Exception ignored) {
            }
            try {
                loc2 = (Location) arena.getClass().getMethod("getLoc2").invoke(arena);
            } catch (Exception ignored) {
            }
            if (loc1 == null && loc2 == null)
                return;
            if (loc1 == null)
                loc1 = loc2;
            if (loc2 == null)
                loc2 = loc1;

            if (loc1 == null || loc2 == null)
                return;

            pendingFightSpawns.put(fight, new Location[] { loc1.clone(), loc2.clone() });

            final Location centerOverride = loc1.clone();
            try {
                arena.getClass().getMethod("setCenter", Location.class).invoke(arena, centerOverride);
            } catch (Exception ignored) {
            }
        } catch (Exception ignored) {
        }
    }

    private void handleFightStart(Event event) {
        if (partyFFAClass == null)
            return;
        try {
            Object fight = event.getClass().getMethod("getFight").invoke(event);
            if (fight == null || !isPartyFFA(fight))
                return;

            List<Player> players = getAllPlayers(fight);

            for (Player p : players) {
                if (p != null) {
                    plugin.playerMatchKills.put(p.getUniqueId(), 0);
                }
            }

            final Object arena = getArenaFromFight(fight);

            if (arena != null) {
                Location loc1E = null, loc2E = null;
                try {
                    loc1E = (Location) arena.getClass().getMethod("getLoc1").invoke(arena);
                } catch (Exception ignored) {
                }
                try {
                    loc2E = (Location) arena.getClass().getMethod("getLoc2").invoke(arena);
                } catch (Exception ignored) {
                }
                if (loc1E != null && loc2E != null) {
                    for (int i = 0; i < players.size(); i++) {
                        Player ep = players.get(i);
                        if (ep == null || !ep.isOnline())
                            continue;
                        Location assigned = (i % 2 == 0) ? loc1E.clone() : loc2E.clone();
                        pendingInitialSpawns.put(ep.getUniqueId(), assigned);
                        plugin.arenaSpawnLocations.put(ep.getUniqueId(), assigned);
                    }
                }
            }

            applyFightStartState(fight, arena, players);

            new BukkitRunnable() {
                @Override
                public void run() {

                    Location[] spawns = null;
                    if (arena != null) {
                        Location loc1 = null, loc2 = null;
                        try {
                            loc1 = (Location) arena.getClass().getMethod("getLoc1").invoke(arena);
                        } catch (Exception ignored) {
                        }
                        try {
                            loc2 = (Location) arena.getClass().getMethod("getLoc2").invoke(arena);
                        } catch (Exception ignored) {
                        }
                        if (loc1 != null && loc2 != null)
                            spawns = new Location[] { loc1.clone(), loc2.clone() };
                        else if (loc1 != null)
                            spawns = new Location[] { loc1.clone(), loc1.clone() };
                        else if (loc2 != null)
                            spawns = new Location[] { loc2.clone(), loc2.clone() };
                    }

                    if (spawns == null)
                        spawns = pendingFightSpawns.remove(fight);
                    else
                        pendingFightSpawns.remove(fight);

                    List<Player> current = getAllPlayers(fight);
                    for (int i = 0; i < current.size(); i++) {
                        Player p = current.get(i);
                        if (p == null || !p.isOnline())
                            continue;

                        if (spawns != null) {
                            Location spawn = (i % 2 == 0) ? spawns[0] : spawns[1];

                            pendingInitialSpawns.putIfAbsent(p.getUniqueId(), spawn.clone());
                            plugin.arenaSpawnLocations.put(p.getUniqueId(), spawn.clone());
                            p.teleport(spawn);
                        } else {
                            plugin.arenaSpawnLocations.put(p.getUniqueId(), p.getLocation().clone());
                        }

                        Color c = FFA_COLORS[i % FFA_COLORS.length];
                        playerFfaColors.put(p.getUniqueId(), c);

                        plugin.applyLayoutOnly(p);
                        applyFfaColorFull(p, c);
                        ItemStack[] armor = p.getInventory().getArmorContents();
                        if (armor != null)
                            savedArmorColors.put(p.getUniqueId(), cloneArmor(armor));
                    }
                }
            }.runTaskLater(plugin, 1L);

            new BukkitRunnable() {
                @Override
                public void run() {
                    for (Player p : getAllPlayers(fight)) {
                        if (p == null || !p.isOnline())
                            continue;
                        Location assigned = pendingInitialSpawns.remove(p.getUniqueId());
                        if (assigned != null) {
                            plugin.arenaSpawnLocations.put(p.getUniqueId(), assigned);
                            p.teleport(assigned);
                        }
                    }
                }
            }.runTaskLater(plugin, 60L);

            if (plugin.startCountdownEnabled)
                startCountdown(players, fight);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void applyFightStartState(Object fight, Object arena, List<Player> fallbackPlayers) {

        Location[] spawns = null;
        if (arena != null) {
            Location loc1 = null, loc2 = null;
            try {
                loc1 = (Location) arena.getClass().getMethod("getLoc1").invoke(arena);
            } catch (Exception ignored) {
            }
            try {
                loc2 = (Location) arena.getClass().getMethod("getLoc2").invoke(arena);
            } catch (Exception ignored) {
            }
            if (loc1 != null && loc2 != null)
                spawns = new Location[] { loc1.clone(), loc2.clone() };
            else if (loc1 != null)
                spawns = new Location[] { loc1.clone(), loc1.clone() };
            else if (loc2 != null)
                spawns = new Location[] { loc2.clone(), loc2.clone() };
        }

        if (spawns == null)
            spawns = pendingFightSpawns.get(fight);

        List<Player> current = getAllPlayers(fight);
        if ((current == null || current.isEmpty()) && fallbackPlayers != null)
            current = new ArrayList<>(fallbackPlayers);
        if (current == null)
            return;

        for (int i = 0; i < current.size(); i++) {
            Player p = current.get(i);
            if (p == null || !p.isOnline())
                continue;

            if (spawns != null) {
                Location spawn = (i % 2 == 0) ? spawns[0] : spawns[1];
                if (spawn != null) {
                    pendingInitialSpawns.putIfAbsent(p.getUniqueId(), spawn.clone());
                    plugin.arenaSpawnLocations.put(p.getUniqueId(), spawn.clone());
                    p.teleport(spawn);
                }
            } else {
                plugin.arenaSpawnLocations.put(p.getUniqueId(), p.getLocation().clone());
            }

            Color c = FFA_COLORS[i % FFA_COLORS.length];
            playerFfaColors.put(p.getUniqueId(), c);
            plugin.applyLayoutOnly(p);
            applyFfaColorFull(p, c);
            ItemStack[] armor = p.getInventory().getArmorContents();
            if (armor != null)
                savedArmorColors.put(p.getUniqueId(), cloneArmor(armor));
            plugin.syncLayoutInstant(p, 6);
        }
    }

    private void handleFightEnd(Event event) {
        if (partyFFAClass == null)
            return;
        try {
            Object fight = event.getClass().getMethod("getFight").invoke(event);
            if (fight == null || !isPartyFFA(fight))
                return;

            Player winner = null;
            try {
                winner = (Player) event.getClass().getMethod("getWinner").invoke(event);
            } catch (Exception ignored) {
            }

            List<Player> allPlayers = getAllPlayers(fight);
            if (allPlayers.isEmpty() && plugin.getMGetPlayersInFight() != null) {
                try {
                    @SuppressWarnings("unchecked")
                    List<Player> api = (List<Player>) plugin.getMGetPlayersInFight().invoke(fight);
                    if (api != null)
                        allPlayers.addAll(api);
                } catch (Exception ignored) {
                }
            }

            final Player finalWinner = winner;

            boolean bridgeHandled = ffaBridgeEndedFights.contains(fight);
            if (!bridgeHandled) {
                for (Player p : allPlayers) {
                    if (p == null || !p.isOnline())
                        continue;
                    boolean isWinner = finalWinner != null && p.getUniqueId().equals(finalWinner.getUniqueId());
                    sendEndTitle(p, isWinner, finalWinner);
                }
            }

            pendingFightSpawns.remove(fight);

            for (Player p : allPlayers) {
                if (p != null) {
                    plugin.recentPartyEndedPlayers.add(p.getUniqueId());
                    bedBrokenPlayers.remove(p.getUniqueId());
                    pendingRespawnRedirect.remove(p.getUniqueId());
                    pendingInitialSpawns.remove(p.getUniqueId());
                    savedArmorColors.remove(p.getUniqueId());
                    playerFfaColors.remove(p.getUniqueId());
                    ffaBridgePortalCooldown.remove(p.getUniqueId());
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startCountdown(List<Player> players, Object fight) {
        for (Player p : players) {
            if (p == null || !p.isOnline())
                continue;
            plugin.frozenPlayers.add(p.getUniqueId());
            plugin.activeStartCountdownPlayers.add(p.getUniqueId());
        }

        final int maxSeconds = plugin.startCountdownDuration;
        new BukkitRunnable() {
            int current = maxSeconds;

            @Override
            public void run() {
                if (hasEndedMethod != null) {
                    try {
                        if ((boolean) hasEndedMethod.invoke(fight)) {
                            unfreezeAll(players);
                            cancel();
                            return;
                        }
                    } catch (Exception ignored) {
                    }
                }

                if (current <= 0) {
                    for (Player p : players) {
                        if (p == null || !p.isOnline())
                            continue;

                        plugin.arenaSpawnLocations.put(p.getUniqueId(), p.getLocation().clone());
                        if (plugin.startMatchMessage != null && !plugin.startMatchMessage.isEmpty())
                            p.sendMessage(plugin.startMatchMessage);
                        if (plugin.startCountdownTitles != null && plugin.startCountdownTitles.containsKey(0))
                            plugin.sendTitle(p, plugin.startCountdownTitles.get(0), "", 0, 20, 10);
                        if (plugin.startCountdownSoundEnabled && plugin.startMatchSound != null)
                            p.playSound(p.getLocation(), plugin.startMatchSound,
                                    plugin.startCountdownVolume, plugin.startCountdownPitch);
                        plugin.frozenPlayers.remove(p.getUniqueId());
                        plugin.activeStartCountdownPlayers.remove(p.getUniqueId());
                    }
                    cancel();
                    return;
                }

                for (Player p : players) {
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
                            p.playSound(p.getLocation(), s,
                                    plugin.startCountdownVolume, plugin.startCountdownPitch);
                    }
                }
                current--;
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void unfreezeAll(List<Player> players) {
        for (Player p : players) {
            if (p != null && p.isOnline()) {
                plugin.frozenPlayers.remove(p.getUniqueId());
                plugin.activeStartCountdownPlayers.remove(p.getUniqueId());
            }
        }
    }

    public Location getCenterAt100(Object fight) {
        try {
            Object arena = getArenaFromFight(fight);
            if (arena != null) {
                Location loc1 = null, loc2 = null;
                try { loc1 = (Location) arena.getClass().getMethod("getLoc1").invoke(arena); } catch (Exception ignored) {}
                try { loc2 = (Location) arena.getClass().getMethod("getLoc2").invoke(arena); } catch (Exception ignored) {}
                if (loc1 != null && loc2 != null)
                    return new Location(loc1.getWorld(),
                            (loc1.getX() + loc2.getX()) / 2.0, 100,
                            (loc1.getZ() + loc2.getZ()) / 2.0);
                if (loc1 != null) return new Location(loc1.getWorld(), loc1.getX(), 100, loc1.getZ());
                if (loc2 != null) return new Location(loc2.getWorld(), loc2.getX(), 100, loc2.getZ());
            }
        } catch (Exception ignored) {}

        double sumX = 0, sumZ = 0;
        org.bukkit.World world = null;
        int count = 0;
        for (Player p : getAllPlayers(fight)) {
            Location s = plugin.arenaSpawnLocations.get(p.getUniqueId());
            if (s != null) { sumX += s.getX(); sumZ += s.getZ(); world = s.getWorld(); count++; }
        }
        if (count > 0 && world != null) return new Location(world, sumX / count, 100, sumZ / count);
        return null;
    }

    private void giveSpectatorCompass(Player p) {
        p.getInventory().clear();
        org.bukkit.inventory.ItemStack compass = new org.bukkit.inventory.ItemStack(org.bukkit.Material.COMPASS);
        org.bukkit.inventory.meta.ItemMeta meta = compass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "Return to Lobby");
            compass.setItemMeta(meta);
        }
        p.getInventory().setItem(8, compass);
        p.updateInventory();
    }

    public void applyCustomSpectator(Player p, java.util.List<Player> group) {
        ffaBridgeCustomSpectators.add(p.getUniqueId());
        p.setGameMode(org.bukkit.GameMode.ADVENTURE);
        p.setAllowFlight(true);
        p.setFlying(true);

        p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.INVISIBILITY,
                Integer.MAX_VALUE, 1, false, false));
        for (Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (!group.contains(online)) online.hidePlayer(p);
        }

        p.setHealth(p.getMaxHealth());
        p.setFoodLevel(20);
        p.setFireTicks(0);

        p.getInventory().clear();
        org.bukkit.inventory.ItemStack compass =
                new org.bukkit.inventory.ItemStack(org.bukkit.Material.COMPASS);
        org.bukkit.inventory.meta.ItemMeta meta = compass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "Return to Lobby");
            compass.setItemMeta(meta);
        }
        p.getInventory().setItem(8, compass);
        p.updateInventory();
    }

    public void cleanupBridgeCustomSpectator(Player p) {
        if (!ffaBridgeCustomSpectators.remove(p.getUniqueId())) return;
        p.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
        p.setAllowFlight(false);
        try { p.setFlying(false); } catch (Exception ignored) {}
        for (Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
            online.showPlayer(p);
        }
    }

    public boolean isInCustomSpectator(java.util.UUID uid) {
        return ffaBridgeCustomSpectators.contains(uid);
    }

    private void sendEndTitle(Player p, boolean isWinner, Player winner) {
        if (isWinner) {
            String t = plugin.getMsg("party.ffa.victory.title");
            String s = plugin.getMsg("party.ffa.victory.subtitle");
            if (t == null || t.isEmpty())
                t = "&e&lVICTORY!";
            if (s == null || s.isEmpty())
                s = "&fYou won the FFA!";
            plugin.sendTitle(p,
                    ChatColor.translateAlternateColorCodes('&', t),
                    ChatColor.translateAlternateColorCodes('&', s),
                    10, 70, 20);
            plugin.playEndMatchSounds(p, true);
        } else {
            String t = plugin.getMsg("party.ffa.defeat.title");
            String s = plugin.getMsg("party.ffa.defeat.subtitle");
            if (t == null || t.isEmpty())
                t = "&c&lDEFEAT!";
            if (s == null || s.isEmpty())
                s = "&c<winner> &fwon the FFA!";
            if (winner != null)
                s = s.replace("<winner>", winner.getName());
            plugin.sendTitle(p,
                    ChatColor.translateAlternateColorCodes('&', t),
                    ChatColor.translateAlternateColorCodes('&', s),
                    10, 70, 20);
            plugin.playEndMatchSounds(p, false);
        }
    }

    public boolean isOwnBed(Player player, Block bedBlock, Object fight) {
        if (!isPartyFFA(fight))
            return false;
        Location playerSpawn = plugin.arenaSpawnLocations.get(player.getUniqueId());
        if (playerSpawn == null)
            return false;

        Location bedLoc = bedBlock.getLocation().add(0.5, 0.5, 0.5);
        double playerBedDist = bedLoc.distanceSquared(playerSpawn);

        boolean anyOpponentSpawnFound = false;
        for (Player other : getAllPlayers(fight)) {
            if (other == null || other.getUniqueId().equals(player.getUniqueId()))
                continue;
            Location otherSpawn = plugin.arenaSpawnLocations.get(other.getUniqueId());
            if (otherSpawn == null)
                continue;
            anyOpponentSpawnFound = true;
            if (bedLoc.distanceSquared(otherSpawn) < playerBedDist)
                return false;
        }
        return anyOpponentSpawnFound;
    }

    private boolean isPlayerInPartyFFA(Player player) {
        if (!plugin.isHooked() || plugin.getMGetFight() == null)
            return false;
        try {
            Object fight = plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), player);
            return isPartyFFA(fight);
        } catch (Exception ignored) {
        }
        return false;
    }

    public boolean isBridgeEndHandled(Object fight) {
        return ffaBridgeEndedFights.contains(fight);
    }

    public void onFightEnd(Object fight) {
        ffaBridgeScores.remove(fight);
        ffaBridgeEndedFights.remove(fight);
    }

    @SuppressWarnings("unchecked")
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPartyFFABridgePortalMove(PlayerMoveEvent event) {
        int scoreLimit = plugin.getConfig().getInt("settings.bridge-score-limit", 0);
        if (scoreLimit <= 0) return;
        if (event instanceof PlayerTeleportEvent) return;
        if (event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        Player player = event.getPlayer();
        if (!plugin.isHooked() || !plugin.isInFight(player)) return;

        Block feet  = event.getTo().getBlock();
        Block below = feet.getRelative(BlockFace.DOWN);
        Block portalBlock = null;
        if (feet.getType().name().contains("END_PORTAL") || feet.getType().name().contains("ENDER_PORTAL"))
            portalBlock = feet;
        else if (below.getType().name().contains("END_PORTAL") || below.getType().name().contains("ENDER_PORTAL"))
            portalBlock = below;
        if (portalBlock == null) return;

        Object fight;
        try {
            fight = plugin.getMGetFight().invoke(plugin.getStrikePracticeAPI(), player);
        } catch (Exception e) { return; }
        if (fight == null || !isPartyFFA(fight)) return;

        if (ffaBridgeEndedFights.contains(fight)) return;

        String kitName = plugin.getKitName(player);
        if (kitName == null) {
            try {
                Object kit = fight.getClass().getMethod("getKit").invoke(fight);
                if (kit != null) kitName = (String) kit.getClass().getMethod("getName").invoke(kit);
            } catch (Exception ignored) {}
        }
        if (kitName == null || !kitName.toLowerCase().contains("bridge")) return;

        Location mySpawn = plugin.arenaSpawnLocations.get(player.getUniqueId());

        Location portalLoc = portalBlock.getLocation();
        double myDist = mySpawn != null ? portalLoc.distanceSquared(mySpawn) : Double.MAX_VALUE;
        boolean closerToOpponent = false;
        for (Player other : getAllPlayers(fight)) {
            if (other == null || other.getUniqueId().equals(player.getUniqueId())) continue;
            Location otherSpawn = plugin.arenaSpawnLocations.get(other.getUniqueId());
            if (otherSpawn == null) continue;
            if (portalLoc.distanceSquared(otherSpawn) < myDist * 0.8) {
                closerToOpponent = true;
                break;
            }
        }

        event.setCancelled(true);

        if (!closerToOpponent) {
            if (mySpawn != null) player.teleport(mySpawn);
            return;
        }

        long now = System.currentTimeMillis();
        if (ffaBridgePortalCooldown.getOrDefault(player.getUniqueId(), 0L) > now) return;
        ffaBridgePortalCooldown.put(player.getUniqueId(), now + 3000L);

        Map<UUID, Integer> scores = ffaBridgeScores.computeIfAbsent(fight, k -> new HashMap<>());
        int myScore = scores.merge(player.getUniqueId(), 1, Integer::sum);

        String scorerColor = getFfaColorCode(player);
        if (scorerColor == null) scorerColor = "§e";

        List<Player> allPlayers = getAllPlayers(fight);

        String rawTitle    = plugin.getMsg("scored.title");
        String rawSubtitle = plugin.getMsg("scored.subtitle");
        if (rawTitle    == null || rawTitle.isEmpty())    rawTitle    = scorerColor + "§l<player>";
        if (rawSubtitle == null || rawSubtitle.isEmpty()) rawSubtitle = "§6Scored!";
        final String titleMsg    = org.bukkit.ChatColor.translateAlternateColorCodes('&',
                rawTitle.replace("<player>", player.getName()));

        StringBuilder scoreBuilder = new StringBuilder();
        for (Player sp : allPlayers) {
            if (sp == null) continue;
            if (scoreBuilder.length() > 0) scoreBuilder.append("§8 - ");
            String spColor = getFfaColorCode(sp);
            if (spColor == null) spColor = "§e";
            scoreBuilder.append(spColor).append(scores.getOrDefault(sp.getUniqueId(), 0));
        }
        final String subtitleMsg = org.bukkit.ChatColor.translateAlternateColorCodes('&',
                rawSubtitle.replace("<player>", player.getName()))
                + "  " + scoreBuilder;

        Sound goalSound = plugin.getSoundByName("ENTITY_PLAYER_LEVELUP");
        if (goalSound == null) goalSound = plugin.getSoundByName("LEVEL_UP");
        final Sound finalGoalSound = goalSound;

        final Object finalFight  = fight;
        final Player finalScorer = player;

        if (myScore >= scoreLimit) {

            ffaBridgeScores.remove(fight);
            ffaBridgePortalCooldown.remove(player.getUniqueId());
            ffaBridgeEndedFights.add(fight);

            final Location spectatorPos = getCenterAt100(finalFight);
            for (Player p : allPlayers) {
                if (p == null || !p.isOnline()) continue;
                plugin.frozenPlayers.remove(p.getUniqueId());
                if (spectatorPos != null) p.teleport(spectatorPos);
                applyCustomSpectator(p, allPlayers);
            }

            new BukkitRunnable() {
                @Override public void run() {
                    for (Player p : allPlayers) {
                        if (p == null || !p.isOnline()) continue;
                        boolean isWinner = p.getUniqueId().equals(finalScorer.getUniqueId());
                        sendEndTitle(p, isWinner, finalScorer);
                        plugin.playEndMatchSounds(p, isWinner);
                        if (!isWinner) {
                            try { p.getWorld().strikeLightningEffect(p.getLocation()); } catch (Exception ignored) {}
                        }
                    }
                }
            }.runTaskLater(plugin, 10L);

            new BukkitRunnable() {
                @Override public void run() {
                    try { finalFight.getClass().getMethod("forceEnd", String.class).invoke(finalFight, ""); }
                    catch (Exception ignored) {}
                }
            }.runTaskLater(plugin, 12L);

        } else {

            for (Player p : allPlayers) {
                if (p == null || !p.isOnline()) continue;
                plugin.sendTitle(p, titleMsg, subtitleMsg, 5, 50, 15);
                if (finalGoalSound != null) {
                    try { p.playSound(p.getLocation(), finalGoalSound, 1.0f, 1.0f); } catch (Exception ignored) {}
                }
                plugin.frozenPlayers.add(p.getUniqueId());
            }

            for (Player p : allPlayers) {
                if (p != null && p.isOnline())
                    plugin.forceRestoreKitBlocks(p, finalFight);
            }

            final List<Player> frozenList = new ArrayList<>(allPlayers);
            new BukkitRunnable() {
                @Override public void run() {

                    if (ffaBridgeEndedFights.contains(finalFight)) return;
                    for (Player p : frozenList) {
                        if (p == null || !p.isOnline()) continue;
                        Location spawn = plugin.arenaSpawnLocations.get(p.getUniqueId());
                        if (spawn != null) p.teleport(spawn);
                    }
                }
            }.runTaskLater(plugin, 5L);

            startBridgeCountdown(frozenList, finalFight);
        }
    }

    private void startBridgeCountdown(List<Player> players, Object fight) {
        if (!plugin.startCountdownEnabled) {
            new BukkitRunnable() {
                @Override public void run() {
                    for (Player p : players) {
                        if (p == null || !p.isOnline()) continue;
                        plugin.frozenPlayers.remove(p.getUniqueId());
                        if (plugin.bridgeBlockResetManager != null)
                            plugin.bridgeBlockResetManager.scheduleRestore(p, 2L, 5L, 10L);
                    }
                }
            }.runTaskLater(plugin, 20L);
            return;
        }
        final int maxSeconds = 3;
        new BukkitRunnable() {
            int current = maxSeconds;
            @Override
            public void run() {
                if (hasEndedMethod != null) {
                    try {
                        if ((boolean) hasEndedMethod.invoke(fight)) { unfreezeAll(players); cancel(); return; }
                    } catch (Exception ignored) {}
                }

                if (ffaBridgeEndedFights.contains(fight)) { unfreezeAll(players); cancel(); return; }
                if (current <= 0) {
                    for (Player p : players) {
                        if (p == null || !p.isOnline()) continue;
                        if (plugin.startMatchMessage != null && !plugin.startMatchMessage.isEmpty())
                            p.sendMessage(plugin.startMatchMessage);
                        if (plugin.startCountdownSoundEnabled && plugin.startMatchSound != null)
                            p.playSound(p.getLocation(), plugin.startMatchSound,
                                    plugin.startCountdownVolume, plugin.startCountdownPitch);
                        plugin.frozenPlayers.remove(p.getUniqueId());
                        if (plugin.bridgeBlockResetManager != null)
                            plugin.bridgeBlockResetManager.scheduleRestore(p, 2L, 5L, 10L);
                    }
                    cancel();
                    return;
                }
                for (Player p : players) {
                    if (p == null || !p.isOnline()) continue;
                    if (plugin.startCountdownMessages != null)
                        p.sendMessage(plugin.startCountdownMessages.getOrDefault(current,
                                ChatColor.RED + String.valueOf(current)));
                    if (plugin.startCountdownSoundEnabled && plugin.startCountdownSounds != null) {
                        Sound s = plugin.startCountdownSounds.get(current);
                        if (s != null) p.playSound(p.getLocation(), s,
                                plugin.startCountdownVolume, plugin.startCountdownPitch);
                    }
                }
                current--;
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    @SuppressWarnings("unchecked")
    public List<Player> getAllPlayers(Object fight) {
        List<Player> result = new ArrayList<>();
        if (!isPartyFFA(fight))
            return result;
        try {
            Object party = getPartyMethod.invoke(fight);
            if (party != null) {
                if (partyGetPlayersMethod != null) {
                    try {
                        Object raw = partyGetPlayersMethod.invoke(party);
                        if (raw instanceof List) {
                            for (Object o : (List<?>) raw)
                                if (o instanceof Player && ((Player) o).isOnline())
                                    result.add((Player) o);
                            if (!result.isEmpty())
                                return result;
                        }
                    } catch (Exception ignored) {
                    }
                }
                if (partyGetMembersNamesMethod != null) {
                    try {
                        Object raw = partyGetMembersNamesMethod.invoke(party);
                        if (raw instanceof Iterable) {
                            for (Object o : (Iterable<?>) raw) {
                                if (o instanceof String) {
                                    Player p = Bukkit.getPlayer((String) o);
                                    if (p != null && p.isOnline() && !result.contains(p))
                                        result.add(p);
                                }
                            }
                            if (!result.isEmpty())
                                return result;
                        }
                    } catch (Exception ignored) {
                    }
                }
                if (partyGetMembersMethod != null) {
                    try {
                        Object raw = partyGetMembersMethod.invoke(party);
                        if (raw instanceof Iterable) {
                            for (Object o : (Iterable<?>) raw) {
                                if (o instanceof Player && ((Player) o).isOnline()) {
                                    if (!result.contains(o))
                                        result.add((Player) o);
                                } else if (o instanceof String) {
                                    Player p = Bukkit.getPlayer((String) o);
                                    if (p != null && p.isOnline() && !result.contains(p))
                                        result.add(p);
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            if (result.isEmpty() && getAliveMethod != null) {
                try {
                    HashSet<String> alive = (HashSet<String>) getAliveMethod.invoke(fight);
                    if (alive != null)
                        for (String name : alive) {
                            Player p = Bukkit.getPlayer(name);
                            if (p != null && p.isOnline())
                                result.add(p);
                        }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        if (result.isEmpty() && plugin.getMGetPlayersInFight() != null) {
            try {
                List<Player> api = (List<Player>) plugin.getMGetPlayersInFight().invoke(fight);
                if (api != null)
                    result.addAll(api);
            } catch (Exception ignored) {
            }
        }
        return result;
    }
}
