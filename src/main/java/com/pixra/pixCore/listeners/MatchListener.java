package com.pixra.pixCore.listeners;

import com.pixra.pixCore.PixCore;
import com.pixra.pixCore.managers.BedRestoreManager;
import com.pixra.pixCore.managers.CountdownManager;
import com.pixra.pixCore.util.TitleUtil;
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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MatchListener implements Listener {

    private final PixCore           plugin;
    private final BedRestoreManager bedRestoreManager;
    private final CountdownManager  countdownManager;

    private final Set<Object>         startedFights           = new HashSet<>();
    private final Map<Object, Long>   fightCountdownCooldown  = new HashMap<>();

    public MatchListener(PixCore plugin) {
        this.plugin            = plugin;
        this.bedRestoreManager = new BedRestoreManager(plugin);
        this.countdownManager  = new CountdownManager(plugin);
        registerStrikePracticeEvents();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpecialBedPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        Player player = event.getPlayer();
        org.bukkit.inventory.ItemStack item = event.getItemInHand();

        if (item == null || !item.getType().name().contains("BED")) return;
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return;
        if (!item.getItemMeta().getDisplayName().contains("Arena Bed Fixer")) return;

        Block foot = event.getBlockPlaced();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!foot.getType().name().contains("BED")) return;

                byte footData = bedRestoreManager.getBlockDataSafe(foot);
                int  dir      = footData & 3;
                int  dx = 0, dz = 0;
                if      (dir == 0) dz =  1;
                else if (dir == 1) dx = -1;
                else if (dir == 2) dz = -1;
                else if (dir == 3) dx =  1;

                Block head = foot.getRelative(dx, 0, dz);
                if (head.getType().name().contains("BED")) {
                    byte headData = bedRestoreManager.getBlockDataSafe(head);
                    bedRestoreManager.saveCustomBed(
                            foot.getLocation(), head.getLocation(), footData, headData);
                    player.sendMessage(ChatColor.GREEN
                            + "[PixCore] Custom Bed saved successfully! It will perfectly restore in this arena.");
                } else {
                    player.sendMessage(ChatColor.RED
                            + "[PixCore] Failed to save bed. Make sure it is placed completely.");
                }
            }
        }.runTaskLater(plugin, 2L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMoveDuringCountdown(PlayerMoveEvent event) {
        Player   player = event.getPlayer();
        if (!plugin.frozenPlayers.contains(player.getUniqueId())) return;
        Location from   = event.getFrom();
        Location to     = event.getTo();
        if (to == null) return;
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) return;
        event.setTo(new Location(from.getWorld(), from.getX(), to.getY(), from.getZ(), to.getYaw(), to.getPitch()));
    }

    private void registerStrikePracticeEvents() {
        try {
            register("ga.strikepractice.events.DuelStartEvent",
                    (listener, event) -> handleDuelStart(event));
            register("ga.strikepractice.events.DuelEndEvent",
                    (listener, event) -> handleDuelEnd(event));
            register("ga.strikepractice.events.RoundEndEvent",
                    (listener, event) -> handleRoundEnd(event));
        } catch (Exception e) {
            plugin.getLogger().warning("[PixCore] Could not register StrikePractice custom events.");
        }
    }

    @SuppressWarnings("unchecked")
    private void register(String className,
                          org.bukkit.plugin.EventExecutor executor) throws Exception {
        Class<? extends Event> cls = (Class<? extends Event>) Class.forName(className);
        Bukkit.getPluginManager().registerEvent(cls, this, EventPriority.MONITOR, executor, plugin);
    }

    @SuppressWarnings("unchecked")
    public void handleDuelStart(Event event) {
        try {
            Object fight = event.getClass().getMethod("getFight").invoke(event);
            if (fight == null) return;

            if (plugin.duelScoreManager != null) {
                plugin.duelScoreManager.onFightStart(fight);
            }

            Object         arena   = (plugin.getMGetArena() != null) ? plugin.getMGetArena().invoke(fight) : null;
            List<Player>   players = new ArrayList<>();

            if (plugin.getMGetPlayers() != null) {
                try {
                    List<Player> found = (List<Player>) plugin.getMGetPlayers().invoke(fight);
                    if (found != null) players.addAll(found);
                } catch (Exception ignored) {}
            }
            if (players.isEmpty()) {
                try {
                    players.addAll((List<Player>) fight.getClass().getMethod("getPlayers").invoke(fight));
                } catch (NoSuchMethodException e) {
                    if (plugin.getMGetFirstPlayer()  != null) players.add((Player) plugin.getMGetFirstPlayer().invoke(fight));
                    if (plugin.getMGetSecondPlayer() != null) players.add((Player) plugin.getMGetSecondPlayer().invoke(fight));
                }
            }
            players.removeIf(p -> p == null);
            if (players.isEmpty()) return;

            String  kitName  = plugin.getKitName(players.get(0));
            if (kitName == null) {
                try {
                    Object kit = fight.getClass().getMethod("getKit").invoke(fight);
                    if (kit != null) kitName = (String) kit.getClass().getMethod("getName").invoke(kit);
                } catch (Exception ignored) {}
            }

            boolean isBridge    = kitName != null
                    && (kitName.toLowerCase().contains("bridge") || kitName.toLowerCase().contains("thebridge"));
            boolean isFirstRound = !startedFights.contains(fight);

            if (isFirstRound) {
                startedFights.add(fight);
                for (Player p : players) plugin.playerMatchKills.put(p.getUniqueId(), 0);
            }

            long now      = System.currentTimeMillis();
            long lastTime = fightCountdownCooldown.getOrDefault(fight, 0L);

            if (now - lastTime > 4000L && (isFirstRound || !isBridge)) {
                fightCountdownCooldown.put(fight, now);
                countdownManager.startMatchCountdown(players);
            }

            if (plugin.getMSetBedwars() != null && kitName != null) {
                try {
                    Player  p          = players.get(0);
                    boolean isBedKit   = isBedKit(kitName);

                    if (!isBedKit && plugin.respawnChatCountdownKits != null) {
                        for (String k : plugin.respawnChatCountdownKits) {
                            if (k.equalsIgnoreCase(kitName)) { isBedKit = true; break; }
                        }
                    }

                    if (isBedKit) {
                        Object kit = plugin.getStrikePracticeAPI().getClass()
                                .getMethod("getKit", Player.class).invoke(plugin.getStrikePracticeAPI(), p);
                        plugin.getMSetBedwars().invoke(kit, true);

                        if (arena != null) {
                            String arenaName = (String) arena.getClass().getMethod("getName").invoke(arena);
                            if (!bedRestoreManager.hasCachedBeds(arenaName)) {
                                bedRestoreManager.saveArenaBeds(arenaName, arena);
                            }
                            bedRestoreManager.forceFixBeds(arenaName, fight, 10L);
                        }
                    }
                } catch (Exception ignored) {}
            }

        } catch (Exception ignored) {}
    }

    private boolean isBedKit(String kitName) {
        String lower = kitName.toLowerCase();
        List<String> bedRestoreKits = plugin.getConfig().getStringList("settings.bed-restore-kits");
        if (bedRestoreKits != null && !bedRestoreKits.isEmpty()) {
            for (String k : bedRestoreKits) if (k.equalsIgnoreCase(kitName)) return true;
        } else {
            return lower.contains("bed") || lower.contains("fireball");
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public void handleDuelEnd(Event event) {
        try {
            final Object fight  = event.getClass().getMethod("getFight").invoke(event);
            Player       winner = (Player) event.getClass().getMethod("getWinner").invoke(event);
            Player       loser  = (Player) event.getClass().getMethod("getLoser").invoke(event);

            List<Player> fightPlayers = new ArrayList<>();
            if (fight != null) {
                plugin.endedFightWinners.put(fight, winner != null ? winner.getUniqueId() : null);

                if (plugin.getMGetPlayersInFight() != null) {
                    try { List<Player> t = (List<Player>) plugin.getMGetPlayersInFight().invoke(fight); if (t != null) fightPlayers.addAll(t); }
                    catch (Exception ignored) {}
                }
                if (fightPlayers.isEmpty() && plugin.getMGetPlayers() != null) {
                    try { List<Player> t = (List<Player>) plugin.getMGetPlayers().invoke(fight); if (t != null) fightPlayers.addAll(t); }
                    catch (Exception ignored) {}
                }
                if (fightPlayers.isEmpty()) {
                    if (winner != null) fightPlayers.add(winner);
                    if (loser  != null) fightPlayers.add(loser);
                }

                for (Player p : fightPlayers) {
                    if (p == null) continue;
                    String kitName = plugin.getKitName(p);
                    String result  = ChatColor.YELLOW + "" + ChatColor.BOLD + "DRAW!";

                    if (winner != null) {
                        boolean isWinner = p.getUniqueId().equals(winner.getUniqueId());
                        if (!isWinner && plugin.getMPlayersAreTeammates() != null) {
                            try { isWinner = (boolean) plugin.getMPlayersAreTeammates().invoke(fight, p, winner); }
                            catch (Exception ignored) {}
                        }
                        if (isWinner) {
                            result = ChatColor.GREEN + "" + ChatColor.BOLD + "VICTORY";
                            if (p.getUniqueId().equals(winner.getUniqueId()) && plugin.leaderboardManager != null) {
                                plugin.leaderboardManager.addWin(winner.getUniqueId(), winner.getName(), kitName);
                            }
                        } else {
                            result = ChatColor.RED + "" + ChatColor.BOLD + "DEFEAT";
                            if (loser != null && p.getUniqueId().equals(loser.getUniqueId()) && plugin.leaderboardManager != null) {
                                plugin.leaderboardManager.resetStreak(loser.getUniqueId(), kitName);
                            }
                        }
                    } else {
                        if (plugin.leaderboardManager != null)
                            plugin.leaderboardManager.resetStreak(p.getUniqueId(), kitName);
                    }
                    plugin.playerMatchResults.put(p.getUniqueId(), result);
                }

                boolean isBestOf   = false;
                int     blueScore  = 0, redScore = 0;
                boolean isBlueWinner = false;

                try {
                    Method    getBestOfM = fight.getClass().getMethod("getBestOf");
                    Object    bestOf     = getBestOfM.invoke(fight);
                    if (bestOf != null) {
                        int totalRounds = 1;
                        try { totalRounds = (int) bestOf.getClass().getMethod("getRounds").invoke(bestOf); }
                        catch (Exception ignored) {}
                        if (totalRounds > 1) isBestOf = true;

                        if (!isBestOf && !fightPlayers.isEmpty()) {
                            String kn = plugin.getKitName(fightPlayers.get(0));
                            if (kn != null) {
                                String lower = kn.toLowerCase();
                                if (lower.contains("bridge") || lower.contains("mlgrush")) isBestOf = true;
                            }
                        }

                        Player bluePlayer = null, redPlayer = null;
                        for (Player p : fightPlayers) {
                            if (p == null) continue;
                            String color = plugin.getTeamColorCode(p, fight);
                            if      (color.contains("9") || color.contains("b")) bluePlayer = p;
                            else if (color.contains("c") || color.contains("d")) redPlayer  = p;
                        }
                        if (bluePlayer == null && !fightPlayers.isEmpty())           bluePlayer = fightPlayers.get(0);
                        if (redPlayer  == null && fightPlayers.size() > 1)          redPlayer  = fightPlayers.get(fightPlayers.size() - 1);

                        Map<UUID, Integer> oldScores = plugin.matchScores.getOrDefault(fight, new HashMap<>());
                        try {
                            Map<?, ?> scores = (Map<?, ?>) bestOf.getClass().getMethod("getRoundsWon").invoke(bestOf);
                            if (scores != null) {
                                for (Map.Entry<?, ?> entry : scores.entrySet()) {
                                    Object key   = entry.getKey();
                                    int    score = entry.getValue() instanceof Number ? ((Number) entry.getValue()).intValue() : 0;
                                    if (bluePlayer != null && (key.equals(bluePlayer.getUniqueId()) || key.equals(bluePlayer.getName()))) blueScore = score;
                                    else if (redPlayer != null && (key.equals(redPlayer.getUniqueId()) || key.equals(redPlayer.getName()))) redScore = score;
                                }
                            }
                        } catch (Exception ignored) {}

                        if (bluePlayer != null && blueScore == 0) blueScore = oldScores.getOrDefault(bluePlayer.getUniqueId(), 0);
                        if (redPlayer  != null && redScore  == 0) redScore  = oldScores.getOrDefault(redPlayer.getUniqueId(),  0);

                        if (winner != null) {
                            String wColor = plugin.getTeamColorCode(winner, fight);
                            isBlueWinner  = wColor.contains("9") || wColor.contains("b");
                        }
                    }
                } catch (Exception ignored) {}

                final boolean finalIsBestOf     = isBestOf;
                final int     finalBlueScore    = blueScore;
                final int     finalRedScore     = redScore;
                final boolean finalIsBlueWinner = isBlueWinner;
                final boolean isDuelReq = plugin.duelScoreManager != null
                        && plugin.duelScoreManager.isDuelRequestFight(fight);

                if (isDuelReq && winner != null && loser != null) {
                    plugin.duelScoreManager.onFightEnd(fight, winner, loser);
                }

                plugin.matchScores.remove(fight);
                startedFights.remove(fight);
                fightCountdownCooldown.remove(fight);

                new BukkitRunnable() {
                    @Override public void run() {
                        plugin.endedFightWinners.remove(fight);
                        for (Player p : fightPlayers) {
                            if (p == null) continue;
                            plugin.playerMatchResults.remove(p.getUniqueId());
                            plugin.playerMatchKills.remove(p.getUniqueId());
                        }
                    }
                }.runTaskLater(plugin, 160L);

                if (fight != null && plugin.getMGetArena() != null) {
                    try {
                        Object arena = plugin.getMGetArena().invoke(fight);
                        if (arena != null) {
                            String arenaName = (String) arena.getClass().getMethod("getName").invoke(arena);
                            bedRestoreManager.forceFixBeds(arenaName, fight, 80L);
                        }
                    } catch (Exception ignored) {}
                }

                for (Player p : fightPlayers) {
                    if (p == null) continue;

                    boolean isWinner = false, isLoser = false;
                    if (winner != null) {
                        isWinner = p.getUniqueId().equals(winner.getUniqueId());
                        if (!isWinner && plugin.getMPlayersAreTeammates() != null) {
                            try { isWinner = (boolean) plugin.getMPlayersAreTeammates().invoke(fight, p, winner); }
                            catch (Exception ignored) {}
                        }
                    }
                    if (!isWinner && loser != null) isLoser = true;
                    if (!isWinner && !isLoser)      continue;

                    plugin.cleanupPlayer(p.getUniqueId(), false);

                    final boolean finalIsWinner  = isWinner;
                    final Player  finalWinner    = winner;
                    final Player  finalLoser     = loser;
                    final boolean finalIsDuelReq = isDuelReq;

                    new BukkitRunnable() {
                        @Override public void run() {
                            if (!p.isOnline()) return;
                            if (finalIsWinner) {
                                sendEndTitle(p, fight, true,  finalIsBestOf, finalIsBlueWinner,
                                        finalBlueScore, finalRedScore, finalIsDuelReq, finalWinner, finalLoser);
                                plugin.playEndMatchSounds(p, true);
                            } else {
                                sendEndTitle(p, fight, false, finalIsBestOf, finalIsBlueWinner,
                                        finalBlueScore, finalRedScore, finalIsDuelReq, finalWinner, finalLoser);
                                plugin.playEndMatchSounds(p, false);
                                p.getWorld().strikeLightningEffect(p.getLocation());
                            }
                        }
                    }.runTaskLater(plugin, 5L);
                }
            }
        } catch (Exception ignored) {}
    }

    private void sendEndTitle(Player p, Object fight, boolean isWinner,
                              boolean isBestOf, boolean isBlueWinner,
                              int blueScore, int redScore,
                              boolean isDuelReq, Player winner, Player loser) {
        if (isBestOf) {
            String t = isBlueWinner ? plugin.getMsg("bestof.blue-wins.title")    : plugin.getMsg("bestof.red-wins.title");
            String s = isBlueWinner ? plugin.getMsg("bestof.blue-wins.subtitle") : plugin.getMsg("bestof.red-wins.subtitle");
            if (t == null || t.isEmpty()) t = isBlueWinner ? "&9&lBLUE WINS!" : "&c&lRED WINS!";
            if (s == null || s.isEmpty()) s = isBlueWinner ? "&9<blue_score> &8- &c<red_score>" : "&c<red_score> &8- &9<blue_score>";
            s = s.replace("<blue_score>", String.valueOf(blueScore))
                    .replace("<red_score>",  String.valueOf(redScore));
            TitleUtil.sendTitle(p, ChatColor.translateAlternateColorCodes('&', t),
                    ChatColor.translateAlternateColorCodes('&', s), 10, 70, 20);

        } else if (isDuelReq && plugin.duelScoreManager != null) {
            Player opponent = isWinner ? loser : winner;
            if (opponent == null) return;
            int myScore  = plugin.duelScoreManager.getScore(p.getUniqueId(), opponent.getUniqueId());
            int opScore  = plugin.duelScoreManager.getScore(opponent.getUniqueId(), p.getUniqueId());
            if (isWinner) {
                TitleUtil.sendTitle(p,
                        ChatColor.translateAlternateColorCodes('&', "&e&lVICTORY!"),
                        ChatColor.translateAlternateColorCodes('&', "&a" + myScore + " &8- &c" + opScore),
                        10, 70, 20);
            } else {
                TitleUtil.sendTitle(p,
                        ChatColor.translateAlternateColorCodes('&', "&c&lDEFEAT!"),
                        ChatColor.translateAlternateColorCodes('&', "&c" + myScore + " &8- &a" + opScore),
                        10, 70, 20);
            }

        } else if (isWinner) {
            String name = winner != null ? winner.getName() : "Unknown";
            TitleUtil.sendTitle(p,
                    plugin.getMsg("victory.title").replace("<player>", name),
                    plugin.getMsg("victory.subtitle").replace("<player>", name),
                    10, 70, 20);
        } else {
            String op = winner != null ? winner.getName() : "Unknown";
            TitleUtil.sendTitle(p,
                    plugin.getMsg("defeat.title").replace("<opponent>", op),
                    plugin.getMsg("defeat.subtitle").replace("<opponent>", op),
                    10, 70, 20);
        }
    }

    @SuppressWarnings("unchecked")
    public void handleRoundEnd(Event event) {
        try {
            final Object fight = event.getClass().getMethod("getFight").invoke(event);
            if (fight == null) return;

            Player tempWinner = null;
            for (Method m : event.getClass().getMethods()) {
                if ((m.getName().toLowerCase().contains("winner")
                        || m.getName().equalsIgnoreCase("getPlayer"))
                        && m.getParameterTypes().length == 0) {
                    try {
                        Object res = m.invoke(event);
                        if      (res instanceof Player) { tempWinner = (Player) res; break; }
                        else if (res instanceof UUID)   { tempWinner = Bukkit.getPlayer((UUID) res); break; }
                        else if (res instanceof String) { tempWinner = Bukkit.getPlayer((String) res); break; }
                    } catch (Exception ignored) {}
                }
            }

            Object tempBestOf = null;
            try { tempBestOf = fight.getClass().getMethod("getBestOf").invoke(fight); }
            catch (Exception ignored) {}

            final Player initialWinner = tempWinner;
            final Object bestOf        = tempBestOf;

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (plugin.endedFightWinners.containsKey(fight)) return;

                    Player winner = initialWinner;

                    if (bestOf != null) {
                        try {
                            Map<UUID, Integer> currentScores = (Map<UUID, Integer>)
                                    bestOf.getClass().getMethod("getRoundsWon").invoke(bestOf);
                            Map<UUID, Integer> oldScores     = plugin.matchScores.getOrDefault(fight, new HashMap<>());

                            if (currentScores != null) {
                                for (Map.Entry<UUID, Integer> entry : currentScores.entrySet()) {
                                    UUID id       = entry.getKey();
                                    int  newScore = entry.getValue();
                                    int  oldScore = oldScores.getOrDefault(id, 0);
                                    if (newScore > oldScore) {
                                        winner = Bukkit.getPlayer(id); break;
                                    }
                                }
                            }
                            plugin.matchScores.put(fight, new HashMap<>(currentScores != null ? currentScores : new HashMap<>()));
                        } catch (Exception ignored) {}
                    }

                    if (winner == null) return;

                    String kitName = plugin.getKitName(winner);
                    if (kitName == null) {
                        try {
                            Object kit = fight.getClass().getMethod("getKit").invoke(fight);
                            if (kit != null) kitName = (String) kit.getClass().getMethod("getName").invoke(kit);
                        } catch (Exception ignored) {}
                    }
                    if (kitName == null) return;

                    String kitLower = kitName.toLowerCase();

                    boolean allowed = plugin.roundEndKits.isEmpty();
                    if (kitLower.contains("bridge") || kitLower.contains("thebridge") || kitLower.contains("thebridgeelo")) {
                        allowed = true;
                    } else if (!allowed) {
                        for (String k : plugin.roundEndKits) {
                            if (kitLower.contains(k.toLowerCase())) { allowed = true; break; }
                        }
                    }
                    if (!allowed) return;

                    if (plugin.getMGetArena() != null) {
                        try {
                            Object arena = plugin.getMGetArena().invoke(fight);
                            if (arena != null) {
                                String arenaName = (String) arena.getClass().getMethod("getName").invoke(arena);
                                bedRestoreManager.forceFixBeds(arenaName, fight, 80L);
                            }
                        } catch (Exception ignored) {}
                    }

                    List<Player> matchPlayers = new ArrayList<>();
                    try {
                        List<Player> fp = (List<Player>) fight.getClass().getMethod("getPlayersInFight").invoke(fight);
                        if (fp != null) matchPlayers.addAll(fp);
                    } catch (Exception e) {
                        try {
                            List<Player> fp = (List<Player>) fight.getClass().getMethod("getPlayers").invoke(fight);
                            if (fp != null) matchPlayers.addAll(fp);
                        } catch (Exception ignored) {}
                    }
                    if (matchPlayers.isEmpty()) matchPlayers.add(winner);

                    final Player finalWinner = winner;

                    if (kitLower.contains("bridge") || kitLower.contains("thebridge")) {
                        handleBridgeRoundEnd(matchPlayers, finalWinner, fight);
                    } else {
                        String titleRaw    = plugin.getMsg("round-end.title");
                        String subtitleRaw = plugin.getMsg("round-end.subtitle")
                                .replace("<winner>", finalWinner.getName());
                        if (titleRaw == null || titleRaw.isEmpty()) titleRaw = "&aRound Over";

                        String title    = ChatColor.translateAlternateColorCodes('&', titleRaw);
                        String subtitle = ChatColor.translateAlternateColorCodes('&', subtitleRaw);

                        for (Player p : matchPlayers) {
                            if (p != null && p.isOnline()) {
                                TitleUtil.sendTitle(p, title, subtitle, 5, 50, 15);
                            }
                        }
                    }
                }
            }.runTaskLater(plugin, 2L);

        } catch (Exception ignored) {}
    }

    private void handleBridgeRoundEnd(List<Player> matchPlayers, Player winner, Object fight) {
        for (Player p : matchPlayers) {
            if (p == null || !p.isOnline()) continue;
            plugin.frozenPlayers.add(p.getUniqueId());
            new BukkitRunnable() {
                @Override public void run() { if (p.isOnline()) plugin.applyStartKit(p); }
            }.runTaskLater(plugin, 5L);
            new BukkitRunnable() {
                @Override public void run() { if (p.isOnline()) plugin.applyStartKit(p); }
            }.runTaskLater(plugin, 15L);
        }

        String color         = plugin.getTeamColorCode(winner, fight);
        String bridgeTitleRaw = color + winner.getName() + " " + color + "scored!";
        final  String bridgeTitle = ChatColor.translateAlternateColorCodes('&', bridgeTitleRaw);

        Sound levelUp   = plugin.getSoundByName("LEVEL_UP");
        if (levelUp == null) levelUp = plugin.getSoundByName("ENTITY_PLAYER_LEVELUP");
        Sound tickSound = plugin.getSoundByName("NOTE_STICKS");
        if (tickSound == null) tickSound = plugin.getSoundByName("UI_BUTTON_CLICK");
        if (tickSound == null) tickSound = plugin.getSoundByName("CLICK");
        Sound startSound = plugin.getSoundByName("FIREWORK_BLAST");
        if (startSound == null) startSound = plugin.getSoundByName("ENTITY_EXPERIENCE_ORB_PICKUP");
        if (startSound == null) startSound = plugin.getSoundByName("ORB_PICKUP");

        final Sound finalLevelUp   = levelUp;
        final Sound finalTick      = tickSound;
        final Sound finalStart     = startSound;

        new BukkitRunnable() {
            int ticks = 100;

            @Override
            public void run() {
                if (ticks < 0 || plugin.endedFightWinners.containsKey(fight)) {
                    for (Player p : matchPlayers) {
                        if (p != null) plugin.frozenPlayers.remove(p.getUniqueId());
                    }
                    this.cancel();
                    return;
                }

                int    seconds     = (int) Math.ceil(ticks / 20.0);
                String subtitleRaw = ticks > 0 ? "&aStarting in " + seconds + "s..." : "&aStarted!";
                String subtitle    = ChatColor.translateAlternateColorCodes('&', subtitleRaw);

                for (Player p : matchPlayers) {
                    if (p == null || !p.isOnline()) continue;
                    TitleUtil.sendTitle(p, bridgeTitle, subtitle, 0, 15, (ticks == 0 ? 15 : 0));

                    if (ticks == 100 && finalLevelUp != null) {
                        p.playSound(p.getLocation(), finalLevelUp, 1.0f, 1.0f);
                    }
                    if (ticks % 20 == 0) {
                        if (ticks > 0 && finalTick  != null) p.playSound(p.getLocation(), finalTick,  1.0f, 1.0f);
                        if (ticks == 0 && finalStart != null) p.playSound(p.getLocation(), finalStart, 1.0f, 1.0f);
                    }
                }
                ticks -= 5;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }
}