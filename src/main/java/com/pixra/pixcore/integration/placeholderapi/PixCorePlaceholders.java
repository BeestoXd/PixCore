package com.pixra.pixcore.integration.placeholderapi;

import com.pixra.pixcore.PixCore;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PixCorePlaceholders extends PlaceholderExpansion {

    private final PixCore plugin;

    private static final String CIRCLE_FILLED = "⬤";
    private static final String CIRCLE_EMPTY  = "⬤";

    private static final String[] TITLE_FRAMES = {
        "&f&lP&a&lRACTICE",
        "&e&lP&f&lR&a&lACTICE",
        "&a&lP&e&lR&f&lA&a&lCTICE",
        "&a&lPR&e&lA&f&lC&a&lTICE",
        "&a&lPRA&e&lC&f&lT&a&lICE",
        "&a&lPRAC&e&lT&f&lI&a&lCE",
        "&a&lPRACT&e&lI&f&lC&a&lE",
        "&a&lPRACTI&e&lC&f&lE",
        "&a&lPRACTICE",
        "&a&lPRACTICE",
        "&a&lPRACTICE"
    };

    public PixCorePlaceholders(PixCore plugin) {
        this.plugin = plugin;
    }

    @Override public String getIdentifier() { return "pixcore"; }
    @Override public String getAuthor()     { return "Pixra"; }
    @Override public String getVersion()    { return "1.0"; }
    @Override public boolean persist()      { return true; }

    private Object getFight(Player p) {
        if (!plugin.isHooked()) return null;
        try {
            return plugin.getStrikePracticeAPI().getClass()
                    .getMethod("getFight", Player.class)
                    .invoke(plugin.getStrikePracticeAPI(), p);
        } catch (Exception e) { return null; }
    }

    private String getKitName(Player p, Object fight) {
        String n = plugin.getKitName(p);
        if (n != null) return n.toLowerCase();
        if (plugin.pearlFightManager != null && plugin.pearlFightManager.isTracked(p)) return "pearlfight";
        if (fight == null) return "";
        try {
            Object kit = fight.getClass().getMethod("getKit").invoke(fight);
            if (kit != null) {
                n = (String) kit.getClass().getMethod("getName").invoke(kit);
                if (n != null) return n.toLowerCase();
            }
        } catch (Exception ignored) {}
        return "";
    }

    private boolean areTeammates(Object fight, Player a, Player b) {
        if (plugin.getMPlayersAreTeammates() == null) return false;
        try { return (boolean) plugin.getMPlayersAreTeammates().invoke(fight, a, b); }
        catch (Exception e) { return false; }
    }

    private boolean isBed1Broken(Object fight) {
        try { return plugin.getMIsBed1Broken() != null && (boolean) plugin.getMIsBed1Broken().invoke(fight); }
        catch (Exception e) { return false; }
    }

    private boolean isBed2Broken(Object fight) {
        try { return plugin.getMIsBed2Broken() != null && (boolean) plugin.getMIsBed2Broken().invoke(fight); }
        catch (Exception e) { return false; }
    }

    private String bedStatus(Object fight, boolean team1, Player p1) {
        boolean broken = team1 ? isBed1Broken(fight) : isBed2Broken(fight);
        if (!broken) return "§a✔";
        int alive = 0;
        try {
            if (plugin.getMGetPlayersInFight() != null) {
                @SuppressWarnings("unchecked")
                List<Player> list = (List<Player>) plugin.getMGetPlayersInFight().invoke(fight);
                if (list != null)
                    for (Player p : list) {
                        boolean isT1 = p.getUniqueId().equals(p1.getUniqueId()) || areTeammates(fight, p1, p);
                        if (team1 == isT1) alive++;
                    }
            }
        } catch (Exception ignored) {}
        String c = team1 ? "§9" : "§c";
        return alive > 0 ? c + alive : "§c✘";
    }

    private String circles(int scored, int total, String color) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < total; i++) {
            if (i < scored) sb.append(color).append(CIRCLE_FILLED);
            else            sb.append("&7").append(CIRCLE_EMPTY);
        }
        return sb.toString();
    }

    private int getPing(Player p) {
        try {
            Object handle = p.getClass().getMethod("getHandle").invoke(p);
            return handle.getClass().getField("ping").getInt(handle);
        } catch (Exception e) {
            try { return (int) p.getClass().getMethod("getPing").invoke(p); }
            catch (Exception ex) { return 0; }
        }
    }

    private boolean isPartyTeam1(Player p, Object fight) {
        if (plugin.partySplitManager != null && plugin.partySplitManager.isPartySplit(fight))
            return plugin.partySplitManager.isInTeam1(p, fight);
        if (plugin.partyVsPartyManager != null && plugin.partyVsPartyManager.isPartyVsParty(fight))
            return plugin.partyVsPartyManager.isInParty1(p, fight);
        return false;
    }

    @SuppressWarnings("unchecked")
    private int partyAliveCount(Object fight, boolean team1) {
        try {
            if (plugin.partySplitManager != null && plugin.partySplitManager.isPartySplit(fight)) {
                HashSet<String> a = (HashSet<String>) fight.getClass()
                        .getMethod(team1 ? "getAlive1" : "getAlive2").invoke(fight);
                return a != null ? a.size() : 0;
            }
            if (plugin.partyVsPartyManager != null && plugin.partyVsPartyManager.isPartyVsParty(fight)) {
                HashSet<String> a = (HashSet<String>) fight.getClass()
                        .getMethod(team1 ? "getPartyAlive1" : "getPartyAlive2").invoke(fight);
                return a != null ? a.size() : 0;
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private int[] partyBridgeScores(Object fight) {
        if (plugin.partySplitManager != null && plugin.partySplitManager.isPartySplit(fight))
            return plugin.partySplitManager.getBridgeScores(fight);
        if (plugin.partyVsPartyManager != null && plugin.partyVsPartyManager.isPartyVsParty(fight))
            return plugin.partyVsPartyManager.getBridgeScores(fight);
        return new int[]{0, 0};
    }

    private int getBestOfLimit(Object fight, String kitName, int fallback) {
        int rounds = fallback;
        try {
            Object bestOf = fight.getClass().getMethod("getBestOf").invoke(fight);
            if (bestOf != null) {
                Object rawRounds = bestOf.getClass().getMethod("getRounds").invoke(bestOf);
                if (rawRounds instanceof Number && ((Number) rawRounds).intValue() > 0) {
                    rounds = ((Number) rawRounds).intValue();
                }
            }
        } catch (Exception ignored) {}
        return plugin.getBestOfScoreLimit(kitName, rounds);
    }

    private int getTrackedBestOfScore(Object fight, Player player) {
        if (fight == null || player == null) {
            return 0;
        }

        int score = 0;
        try {
            Object bestOf = fight.getClass().getMethod("getBestOf").invoke(fight);
            if (bestOf != null) {
                @SuppressWarnings("unchecked")
                Map<UUID, Integer> won = (Map<UUID, Integer>) bestOf.getClass().getMethod("getRoundsWon").invoke(bestOf);
                if (won != null) {
                    score = won.getOrDefault(player.getUniqueId(), 0);
                }
            }
        } catch (Exception ignored) {}

        if (score == 0 && plugin.matchScores.containsKey(fight)) {
            score = plugin.matchScores.get(fight).getOrDefault(player.getUniqueId(), 0);
        }
        return score;
    }

    private List<Player> getPartySidePlayers(Object fight, boolean team1) {
        if (fight == null) {
            return new ArrayList<>();
        }
        if (plugin.partySplitManager != null && plugin.partySplitManager.isPartySplit(fight)) {
            return team1 ? plugin.partySplitManager.getTeam1Players(fight)
                    : plugin.partySplitManager.getTeam2Players(fight);
        }
        if (plugin.partyVsPartyManager != null && plugin.partyVsPartyManager.isPartyVsParty(fight)) {
            return team1 ? plugin.partyVsPartyManager.getParty1Players(fight)
                    : plugin.partyVsPartyManager.getParty2Players(fight);
        }
        return new ArrayList<>();
    }

    private int getPartySideBestOfScore(Object fight, boolean team1) {
        int bestScore = 0;
        for (Player sidePlayer : getPartySidePlayers(fight, team1)) {
            bestScore = Math.max(bestScore, getTrackedBestOfScore(fight, sidePlayer));
        }
        return bestScore;
    }

    private String getPartySideLabel(Object fight, boolean team1) {
        List<Player> players = getPartySidePlayers(fight, team1);
        if (!players.isEmpty() && players.get(0) != null) {
            return plugin.getEffectivePlayerName(players.get(0));
        }
        return team1 ? "Blue" : "Red";
    }

    private Player getPartySideReferencePlayer(Object fight, boolean team1) {
        Player selected = null;
        for (Player candidate : getPartySidePlayers(fight, team1)) {
            if (candidate == null) {
                continue;
            }
            if (selected == null || candidate.getName().compareToIgnoreCase(selected.getName()) < 0) {
                selected = candidate;
            }
        }
        return selected;
    }

    private Player getPartyOpponent(Player player, Object fight) {
        if (player == null || fight == null) {
            return null;
        }

        Player opponent = getPartySideReferencePlayer(fight, !isPartyTeam1(player, fight));
        if (opponent != null && !opponent.getUniqueId().equals(player.getUniqueId())) {
            return opponent;
        }
        return null;
    }

    private int getPearlFightStartingLives() {
        if (plugin.pearlFightManager != null) {
            return plugin.pearlFightManager.getStartingLives();
        }
        return plugin.getConfig().getInt("settings.pearlfight.lives", 3);
    }

    private int getPearlFightLives(Player player) {
        if (player == null) {
            return getPearlFightStartingLives();
        }
        if (plugin.pearlFightManager != null) {
            return plugin.pearlFightManager.getLives(player);
        }
        return getPearlFightStartingLives();
    }

    @SuppressWarnings("unchecked")
    private Player getOpponent(Player player, Object fight) {
        if (player == null || fight == null) return null;

        try {
            if (plugin.getMGetPlayersInFight() != null) {
                List<Player> players = (List<Player>) plugin.getMGetPlayersInFight().invoke(fight);
                if (players != null)
                    for (Player other : players)
                        if (other != null && !other.getUniqueId().equals(player.getUniqueId()))
                            return other;
            }
        } catch (Exception ignored) {}

        try {
            if (plugin.getMGetPlayers() != null) {
                List<Player> players = (List<Player>) plugin.getMGetPlayers().invoke(fight);
                if (players != null)
                    for (Player other : players)
                        if (other != null && !other.getUniqueId().equals(player.getUniqueId()))
                            return other;
            }
        } catch (Exception ignored) {}

        try {
            Player first = plugin.getMGetFirstPlayer() != null
                    ? (Player) plugin.getMGetFirstPlayer().invoke(fight) : null;
            Player second = plugin.getMGetSecondPlayer() != null
                    ? (Player) plugin.getMGetSecondPlayer().invoke(fight) : null;
            if (first != null && second != null)
                return player.getUniqueId().equals(first.getUniqueId()) ? second : first;
        } catch (Exception ignored) {}

        return null;
    }

    private List<String> buildEnded(Player p) {
        List<String> lines = new ArrayList<>();
        String result = plugin.getPlayerMatchResults().get(p.getUniqueId());
        String duration = plugin.getPlayerMatchDurations().getOrDefault(p.getUniqueId(), "00:00");
        lines.add(result != null ? result : "");
        lines.add("");
        lines.add("\u00A7fDuration: \u00A77" + duration);
        lines.add("");
        if (false && plugin.duelScoreManager != null && plugin.duelScoreManager.hasRecentDuel(p.getUniqueId())) {
            UUID opp = plugin.duelScoreManager.getLastOpponent(p.getUniqueId());
            if (opp != null) {
                int my = plugin.duelScoreManager.getScore(p.getUniqueId(), opp);
                int op = plugin.duelScoreManager.getScore(opp, p.getUniqueId());
                boolean win = result != null && result.contains("VICTORY");
                lines.add("");
                lines.add(win ? "§a" + my + " §8- §c" + op : "§c" + my + " §8- §a" + op);
            }
        }
        return lines;
    }

    private List<String> buildBedwars(Player p, Object fight, Player p1) {
        List<String> lines = new ArrayList<>();
        boolean playerIsBlue = p.getUniqueId().equals(p1.getUniqueId()) || areTeammates(fight, p1, p);
        String blueYou = playerIsBlue ? " §7YOU" : "";
        String redYou  = playerIsBlue ? "" : " §7YOU";
        lines.add("§9B §fBlue: " + bedStatus(fight, true,  p1) + blueYou);
        lines.add("§cR §fRed: "  + bedStatus(fight, false, p1) + redYou);
        lines.add("");
        lines.add("§fKills: §a" + plugin.playerMatchKills.getOrDefault(p.getUniqueId(), 0));
        lines.add("");
        lines.add("§fPing: §7" + getPing(p) + "ms");
        return lines;
    }

    private List<String> buildBestof(Player p, Object fight, Player p1, String kitName) {
        List<String> lines = new ArrayList<>();
        int rounds = 3, p1Score = 0, p2Score = 0;
        try {
            Object bestOf = fight.getClass().getMethod("getBestOf").invoke(fight);
            if (bestOf != null) {
                int r = (int) bestOf.getClass().getMethod("getRounds").invoke(bestOf);
                if (r > 0) rounds = r;
                @SuppressWarnings("unchecked")
                Map<UUID, Integer> won = (Map<UUID, Integer>) bestOf.getClass().getMethod("getRoundsWon").invoke(bestOf);
                if (won != null)
                    for (Map.Entry<UUID, Integer> e : won.entrySet()) {
                        if (e.getKey().equals(p1.getUniqueId())) p1Score = e.getValue();
                        else                                      p2Score = e.getValue();
                    }
            }
        } catch (Exception ignored) {}

        boolean isStickfight = kitName != null && kitName.contains("stickfight");

        if (isStickfight) {
            int limit = rounds;
            if (plugin.bestofConfig != null) {
                org.bukkit.configuration.ConfigurationSection sec =
                        plugin.bestofConfig.getConfigurationSection(kitName);
                if (sec != null) limit = sec.getInt("score-limit", rounds);
            }
            String p2Name = "?";
            try {
                if (plugin.getMGetPlayersInFight() != null) {
                    @SuppressWarnings("unchecked")
                    List<Player> fightPlayers = (List<Player>) plugin.getMGetPlayersInFight().invoke(fight);
                    if (fightPlayers != null)
                        for (Player pl : fightPlayers)
                            if (!pl.getUniqueId().equals(p1.getUniqueId())) { p2Name = plugin.getEffectivePlayerName(pl); break; }
                }
            } catch (Exception ignored) {}
            lines.add("§fWins:");
            lines.add("§f " + plugin.getEffectivePlayerName(p1) + "§7: §a" + p1Score + "/" + limit);
            lines.add("§f " + p2Name + "§7: §a" + p2Score + "/" + limit);
        } else {
            int displayRounds = (rounds + 1) / 2;
            lines.add("&9[B] " + circles(p1Score, displayRounds, "&9"));
            lines.add("&c[R] " + circles(p2Score, displayRounds, "&c"));
            lines.add("");
            lines.add("§fKills: §a" + plugin.playerMatchKills.getOrDefault(p.getUniqueId(), 0));
        }
        return lines;
    }

    private List<String> buildPearlFight(Player p, Object fight) {
        List<String> lines = new ArrayList<>();
        int ownLives = getPearlFightLives(p);
        int opponentLives = getPearlFightStartingLives();
        if (plugin.pearlFightManager != null) {
            opponentLives = plugin.pearlFightManager.getOpponentLives(p, fight);
        }

        lines.add("\u00A7fLives");
        lines.add("\u00A7f\u2022 You: \u00A7a" + ownLives);
        lines.add("\u00A7f\u2022 Them: \u00A7a" + opponentLives);
        lines.add("");
        return lines;
    }

    private List<String> buildGeneric(Player p) {
        List<String> lines = new ArrayList<>();
        lines.add("§fKills: §a" + plugin.playerMatchKills.getOrDefault(p.getUniqueId(), 0));
        lines.add("");
        lines.add("§fPing: §7" + getPing(p) + "ms");
        return lines;
    }

    private List<String> buildPartyBedwars(Player p, Object fight) {
        boolean bed1Broken = isBed1Broken(fight);
        boolean bed2Broken = isBed2Broken(fight);
        int alive1 = partyAliveCount(fight, true);
        int alive2 = partyAliveCount(fight, false);

        String blue = bed1Broken ? (alive1 > 0 ? "§9" + alive1 : "§c✘") : "§a✔";
        String red  = bed2Broken ? (alive2 > 0 ? "§c" + alive2 : "§c✘") : "§a✔";

        boolean playerIsBlue = isPartyTeam1(p, fight);
        String blueYou = playerIsBlue ? " §7YOU" : "";
        String redYou  = playerIsBlue ? "" : " §7YOU";
        List<String> lines = new ArrayList<>();
        lines.add("§9B §fBlue: " + blue + blueYou);
        lines.add("§cR §fRed: "  + red  + redYou);
        lines.add("");
        lines.add("§fKills: §a" + plugin.playerMatchKills.getOrDefault(p.getUniqueId(), 0));
        lines.add("");
        lines.add("§fPing: §7" + getPing(p) + "ms");
        return lines;
    }

    private List<String> buildPartyBridge(Player p, Object fight) {
        if (plugin != null) {
            int[] sc = partyBridgeScores(fight);
            String kitName = getKitName(p, fight);
            int limit = getBestOfLimit(fight, kitName,
                    plugin.getConfig().getInt("settings.bridge-party-score-limit", 5));
            int blueScore = sc[0];
            int redScore = sc[1];
            String timeLeftBr = (plugin.matchDurationManager != null)
                    ? plugin.matchDurationManager.getTimeLeft(fight) : "";
            if ((timeLeftBr == null || timeLeftBr.isEmpty()) && plugin.matchDurationManager != null) {
                timeLeftBr = plugin.matchDurationManager.getConfiguredTimeLeft(kitName);
            }
            if (timeLeftBr == null || timeLeftBr.isEmpty()) {
                timeLeftBr = "00:00";
            }

            List<String> lines = new ArrayList<>();
            lines.add("&c[R] " + circles(redScore, limit, "&c"));
            lines.add("&9[B] " + circles(blueScore, limit, "&9"));
            lines.add("");
            lines.add("&fTime Left: &a" + timeLeftBr);
            lines.add("");
            lines.add("&fKills: &a" + plugin.playerMatchKills.getOrDefault(p.getUniqueId(), 0));
            lines.add("");
            lines.add("&fYour Ping: &a" + getPing(p) + " ms");
            lines.add("");
            return lines;
        }

        boolean t1 = isPartyTeam1(p, fight);
        int[] sc  = partyBridgeScores(fight);
        int limit    = plugin.getConfig().getInt("settings.bridge-party-score-limit", 5);
        int myScore  = t1 ? sc[0] : sc[1];
        int oppScore = t1 ? sc[1] : sc[0];
        String myC  = t1 ? "§9" : "§c";
        String oppC = t1 ? "§c" : "§9";

        List<String> lines = new ArrayList<>();
        lines.add("§fYou:  " + circles(myScore,  limit, myC));
        lines.add("§fThem: " + circles(oppScore, limit, oppC));
        lines.add("");
        String timeLeftBr = (plugin.matchDurationManager != null) ? plugin.matchDurationManager.getTimeLeft(fight) : "";
        if (!timeLeftBr.isEmpty()) {
            lines.add("§fTime Left: §a" + timeLeftBr);
        } else {
            lines.add("§fGoals: §a" + myScore);
        }
        lines.add("§fKills: §a" + plugin.playerMatchKills.getOrDefault(p.getUniqueId(), 0));
        lines.add("");
        lines.add("§fPing: §7" + getPing(p) + "ms");
        return lines;
    }

    private List<String> buildPartyStickfightBestOf(Player p, Object fight, String kitName) {
        List<String> lines = new ArrayList<>();
        int limit = getBestOfLimit(fight, kitName, 5);
        int[] scores = partyBridgeScores(fight);
        int team1Score = scores[0];
        int team2Score = scores[1];
        String team1Label = getPartySideLabel(fight, true);
        String team2Label = getPartySideLabel(fight, false);

        lines.add("&fWins:");
        lines.add("&f " + team1Label + ": &a" + team1Score + "/&a" + limit);
        lines.add("&f " + team2Label + ": &a" + team2Score + "/&a" + limit);
        lines.add("");
        lines.add("&fYour Ping: &a" + getPing(p) + " ms");
        lines.add("");
        return lines;
    }

    private List<String> buildPartyPearlFight(Player p, Object fight) {
        List<String> lines = new ArrayList<>();
        Player opponent = getPartyOpponent(p, fight);
        int ownLives = getPearlFightLives(p);
        int opponentLives = getPearlFightLives(opponent);
        int opponentPing = opponent != null ? getPing(opponent) : 0;

        lines.add("&fLives");
        lines.add("&f\u2022 You: &a" + ownLives);
        lines.add("&f\u2022 Them: &a" + opponentLives);
        lines.add("");
        lines.add("&fYour Ping: &a" + getPing(p) + " ms");
        lines.add("&fTheir Ping: &a" + opponentPing + " ms");
        lines.add("");
        return lines;
    }

    private List<String> buildPartyGeneric(Player p, Object fight) {
        List<String> lines = new ArrayList<>();
        String timeLeftG = (plugin.matchDurationManager != null) ? plugin.matchDurationManager.getTimeLeft(fight) : "";
        if (!timeLeftG.isEmpty()) {
            lines.add("§fTime Left: §a" + timeLeftG);
            lines.add("");
        }
        lines.add("§fKills: §a" + plugin.playerMatchKills.getOrDefault(p.getUniqueId(), 0));
        lines.add("");
        lines.add("§fPing: §7" + getPing(p) + "ms");
        return lines;
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {

        if (identifier.equalsIgnoreCase("title")) {
            int frame = (int) ((System.currentTimeMillis() / 1000L) % TITLE_FRAMES.length);
            return ChatColor.translateAlternateColorCodes('&', TITLE_FRAMES[frame]);
        }

        if (player == null || !plugin.isHooked()) return "";

        if (identifier.equalsIgnoreCase("highest_winstreak"))
            return plugin.leaderboardManager != null
                    ? plugin.leaderboardManager.getHighestWinstreakString(player.getUniqueId()) : "0";

        boolean isEnded = plugin.getPlayerMatchResults().containsKey(player.getUniqueId());
        Object fight    = getFight(player);
        boolean trackedPearlFight = plugin.pearlFightManager != null && plugin.pearlFightManager.isTracked(player);
        if (fight == null && trackedPearlFight) {
            fight = plugin.pearlFightManager.getTrackedFight(player);
        }
        String kitName  = getKitName(player, fight);

        boolean isParty   = fight != null && (
                (plugin.partySplitManager   != null && plugin.partySplitManager.isPartySplit(fight)) ||
                        (plugin.partyVsPartyManager != null && plugin.partyVsPartyManager.isPartyVsParty(fight)));
        boolean isPearlFight = kitName.contains("pearlfight") || trackedPearlFight;
        boolean isBedwars = kitName.contains("bed") || kitName.contains("fireball");
        boolean isBridge  = plugin.isRoundScoredKit(kitName);
        boolean isBestOf  = isBridge || kitName.contains("stickfight");
        boolean isBoxing  = plugin.boxingTrackerManager != null && plugin.boxingTrackerManager.isTrackingKit(kitName);
        if (!isBestOf && fight != null) {
            try {
                Object bo = fight.getClass().getMethod("getBestOf").invoke(fight);
                if (bo != null && (int) bo.getClass().getMethod("getRounds").invoke(bo) > 1)
                    isBestOf = true;
            } catch (Exception ignored) {}
        }

        Player p1 = null;
        if (fight != null && plugin.getMGetFirstPlayer() != null) {
            try { p1 = (Player) plugin.getMGetFirstPlayer().invoke(fight); }
            catch (Exception ignored) {}
        }

        if (identifier.startsWith("board_")) {
            int lineNum;
            try { lineNum = Integer.parseInt(identifier.substring(6)); }
            catch (NumberFormatException e) { return ""; }

            List<String> lines;
            if (isEnded) {
                lines = buildEnded(player);
            } else if (fight == null && !isPearlFight) {
                lines = new ArrayList<>();
            } else if (isParty) {
                if (isBedwars)     lines = buildPartyBedwars(player, fight);
                else if (isBridge) lines = buildPartyBridge(player, fight);
                else if (kitName.contains("stickfight")) lines = buildPartyStickfightBestOf(player, fight, kitName);
                else if (isPearlFight) lines = buildPartyPearlFight(player, fight);
                else               lines = buildPartyGeneric(player, fight);
            } else if (isPearlFight) {
                lines = buildPearlFight(player, fight);
            } else if (isBedwars && p1 != null) {
                lines = buildBedwars(player, fight, p1);
            } else if (isBestOf && p1 != null) {
                lines = buildBestof(player, fight, p1, kitName);
            } else {
                lines = new ArrayList<>();
            }

            if (lineNum >= 1 && lineNum <= lines.size())
                return ChatColor.translateAlternateColorCodes('&', lines.get(lineNum - 1));
            return isEnded ? "[display=!<ended>]" : "[display=<ended>]";
        }

        if (identifier.equalsIgnoreCase("result"))
            return isEnded ? plugin.getPlayerMatchResults().getOrDefault(player.getUniqueId(), "") : "";

        if (identifier.equalsIgnoreCase("match_end_active"))
            return isEnded ? "true" : "false";

        if (identifier.equalsIgnoreCase("duel_time_left_line")) {
            if (isEnded || fight == null || isPearlFight || !isBestOf || plugin.matchDurationManager == null) {
                return "[display=<ended>]";
            }

            String timeLeft = plugin.matchDurationManager.getTimeLeft(fight);
            if (timeLeft == null || timeLeft.isEmpty()) {
                return "[display=<ended>]";
            }
            return ChatColor.WHITE + "Time Left: " + ChatColor.GREEN + timeLeft;
        }

        if (identifier.equalsIgnoreCase("duel_prestart_opponent")) {
            if (isEnded || fight == null || isParty || isBedwars || isBestOf || isPearlFight)
                return "[display=<ended>]";
            Player opponent = getOpponent(player, fight);
            if (opponent == null)
                return "[display=<ended>]";
            return ChatColor.WHITE + "Opponent: " + ChatColor.GREEN + plugin.getEffectivePlayerName(opponent);
        }

        if (identifier.equalsIgnoreCase("duel_prestart_spacer")) {
            if (isEnded || fight == null || isParty || isBedwars || isBestOf || isPearlFight)
                return "[display=<ended>]";
            return "";
        }

        if (identifier.equalsIgnoreCase("duel_bestof_spacer")) {
            if (isEnded || fight == null || isPearlFight || !isBestOf)
                return "[display=<ended>]";
            return "";
        }

        if (identifier.equalsIgnoreCase("duel_ping_spacer")) {
            if (isEnded || fight == null || isPearlFight)
                return "[display=<ended>]";
            return "";
        }

        if (identifier.equalsIgnoreCase("boxing_hit_delta")) {
            if (!isBoxing || plugin.boxingTrackerManager == null) {
                return ChatColor.GREEN + "(+0)";
            }
            return plugin.boxingTrackerManager.getHitDeltaText(player);
        }

        if (identifier.equalsIgnoreCase("boxing_hits")) {
            if (!isBoxing || plugin.boxingTrackerManager == null) {
                return "0";
            }
            return String.valueOf(plugin.boxingTrackerManager.getHits(player));
        }

        if (identifier.equalsIgnoreCase("boxing_hits_opponent")) {
            if (!isBoxing || plugin.boxingTrackerManager == null) {
                return "0";
            }
            return String.valueOf(plugin.boxingTrackerManager.getOpponentHits(player));
        }

        if (identifier.equalsIgnoreCase("boxing_combo_line")) {
            if (!isBoxing || plugin.boxingTrackerManager == null) {
                return ChatColor.WHITE + "  1st to 100!";
            }
            return plugin.boxingTrackerManager.getComboLine(player);
        }

        if (identifier.equalsIgnoreCase("kills")) {
            if (isEnded) return "";
            return String.valueOf(plugin.playerMatchKills.getOrDefault(player.getUniqueId(), 0));
        }

        if (identifier.equalsIgnoreCase("goals")) {
            if (isEnded || fight == null) return "";
            if (isParty && isBridge) {
                boolean t1 = isPartyTeam1(player, fight);
                int[] sc = partyBridgeScores(fight);
                return String.valueOf(t1 ? sc[0] : sc[1]);
            }
            int score = 0;
            try {
                Object bo = fight.getClass().getMethod("getBestOf").invoke(fight);
                if (bo != null) {
                    @SuppressWarnings("unchecked")
                    Map<UUID, Integer> won = (Map<UUID, Integer>) bo.getClass().getMethod("getRoundsWon").invoke(bo);
                    if (won != null) score = won.getOrDefault(player.getUniqueId(), 0);
                }
            } catch (Exception ignored) {}
            if (score == 0 && plugin.matchScores.containsKey(fight))
                score = plugin.matchScores.get(fight).getOrDefault(player.getUniqueId(), 0);
            return String.valueOf(score);
        }

        if (identifier.equalsIgnoreCase("ping"))
            return getPing(player) + "ms";

        if (identifier.equalsIgnoreCase("opponent_ping")) {
            if (fight == null) {
                return "";
            }
            Player opponent = isParty ? getPartyOpponent(player, fight) : getOpponent(player, fight);
            return opponent != null ? String.valueOf(getPing(opponent)) : "";
        }

        if (identifier.equalsIgnoreCase("time_left")) {
            if (fight == null || plugin.matchDurationManager == null) return "";
            return plugin.matchDurationManager.getTimeLeft(fight);
        }

        if (identifier.equalsIgnoreCase("is_ending_soon")) {
            if (fight == null || plugin.matchDurationManager == null) return "false";
            return plugin.matchDurationManager.isEndingSoon(fight) ? "true" : "false";
        }

        if (identifier.equalsIgnoreCase("party_team")) {
            if (fight == null || !isParty) return "";
            return isPartyTeam1(player, fight) ? "§9Blue" : "§cRed";
        }
        if (identifier.equalsIgnoreCase("party_alive_self")) {
            if (fight == null || !isParty) return "";
            return String.valueOf(partyAliveCount(fight, isPartyTeam1(player, fight)));
        }
        if (identifier.equalsIgnoreCase("party_alive_enemy")) {
            if (fight == null || !isParty) return "";
            return String.valueOf(partyAliveCount(fight, !isPartyTeam1(player, fight)));
        }
        if (identifier.equalsIgnoreCase("party_score_self")) {
            if (fight == null || !isParty) return "";
            boolean t1 = isPartyTeam1(player, fight); int[] sc = partyBridgeScores(fight);
            return String.valueOf(t1 ? sc[0] : sc[1]);
        }
        if (identifier.equalsIgnoreCase("party_score_enemy")) {
            if (fight == null || !isParty) return "";
            boolean t1 = isPartyTeam1(player, fight); int[] sc = partyBridgeScores(fight);
            return String.valueOf(t1 ? sc[1] : sc[0]);
        }
        if (identifier.equalsIgnoreCase("party_circles_self")) {
            if (fight == null || !isParty) return "";
            boolean t1 = isPartyTeam1(player, fight); int[] sc = partyBridgeScores(fight);
            String partyKitName = getKitName(player, fight);
            int lim = getBestOfLimit(fight, partyKitName,
                    plugin.getConfig().getInt("settings.bridge-party-score-limit", 5));
            return ChatColor.translateAlternateColorCodes('&', circles(t1 ? sc[0] : sc[1], lim, t1 ? "§9" : "§c"));
        }
        if (identifier.equalsIgnoreCase("party_circles_enemy")) {
            if (fight == null || !isParty) return "";
            boolean t1 = isPartyTeam1(player, fight); int[] sc = partyBridgeScores(fight);
            String partyKitName = getKitName(player, fight);
            int lim = getBestOfLimit(fight, partyKitName,
                    plugin.getConfig().getInt("settings.bridge-party-score-limit", 5));
            return ChatColor.translateAlternateColorCodes('&', circles(t1 ? sc[1] : sc[0], lim, t1 ? "§c" : "§9"));
        }

        if (identifier.equalsIgnoreCase("duel_circles_blue") || identifier.equalsIgnoreCase("duel_circles_red")) {
            if (fight == null || p1 == null || isParty || !isBestOf) return "[display=<ended>]";
            boolean wantBlue = identifier.equalsIgnoreCase("duel_circles_blue");
            int rounds = 3, p1Score = 0, p2Score = 0;
            try {
                Object bestOf = fight.getClass().getMethod("getBestOf").invoke(fight);
                if (bestOf != null) {
                    int r = (int) bestOf.getClass().getMethod("getRounds").invoke(bestOf);
                    if (r > 0) rounds = r;
                    @SuppressWarnings("unchecked")
                    Map<UUID, Integer> won = (Map<UUID, Integer>) bestOf.getClass().getMethod("getRoundsWon").invoke(bestOf);
                    if (won != null)
                        for (Map.Entry<UUID, Integer> e : won.entrySet()) {
                            if (e.getKey().equals(p1.getUniqueId())) p1Score = e.getValue();
                            else                                      p2Score = e.getValue();
                        }
                }
            } catch (Exception ignored) {}
            int displayRounds = (rounds + 1) / 2;
            int score    = wantBlue ? p1Score : p2Score;
            String color = wantBlue ? "&9" : "&c";
            String label = wantBlue ? "&9[B] " : "&c[R] ";
            return ChatColor.translateAlternateColorCodes('&', label + circles(score, displayRounds, color) + "&r");
        }

        if (fight != null && p1 != null && (identifier.startsWith("bed_blue") || identifier.startsWith("bed_red"))) {
            if (!isBedwars) return "";
            boolean isBlue = identifier.contains("blue");
            String status = bedStatus(fight, isBlue, p1);
            return identifier.endsWith("_line")
                    ? (isBlue ? "§9B §fBlue: " : "§cR §fRed: ") + status
                    : status;
        }

        return null;
    }
}
