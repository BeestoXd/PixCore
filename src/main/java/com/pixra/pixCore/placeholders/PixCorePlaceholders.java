package com.pixra.pixCore.placeholders;

import com.pixra.pixCore.PixCore;
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

    private static final String CIRCLE_FILLED = "█";
    private static final String CIRCLE_EMPTY  = "░";

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
        for (int i = 0; i < total; i++)
            sb.append(i < scored ? color + CIRCLE_FILLED : "§8" + CIRCLE_EMPTY);
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

    private List<String> buildEnded(Player p) {
        List<String> lines = new ArrayList<>();
        String result = plugin.getPlayerMatchResults().get(p.getUniqueId());
        lines.add(result != null ? result : "");
        if (plugin.duelScoreManager != null && plugin.duelScoreManager.hasRecentDuel(p.getUniqueId())) {
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
        String blueYou = playerIsBlue ? " §7(You)" : "";
        String redYou  = playerIsBlue ? "" : " §7(You)";
        lines.add("§9B §fBlue: " + bedStatus(fight, true,  p1) + blueYou);
        lines.add("§cR §fRed: "  + bedStatus(fight, false, p1) + redYou);
        lines.add("");
        lines.add("§fKills: §a" + plugin.playerMatchKills.getOrDefault(p.getUniqueId(), 0));
        lines.add("");
        lines.add("§fPing: §7" + getPing(p) + "ms");
        return lines;
    }

    private List<String> buildBestof(Player p, Object fight, Player p1) {
        List<String> lines = new ArrayList<>();
        int rounds = 3, myScore = 0, oppScore = 0;
        try {
            Object bestOf = fight.getClass().getMethod("getBestOf").invoke(fight);
            if (bestOf != null) {
                rounds = (int) bestOf.getClass().getMethod("getRounds").invoke(bestOf);
                @SuppressWarnings("unchecked")
                Map<UUID, Integer> won = (Map<UUID, Integer>) bestOf.getClass().getMethod("getRoundsWon").invoke(bestOf);
                if (won != null)
                    for (Map.Entry<UUID, Integer> e : won.entrySet()) {
                        if (e.getKey().equals(p.getUniqueId())) myScore  = e.getValue();
                        else                                      oppScore = e.getValue();
                    }
            }
        } catch (Exception ignored) {}

        boolean isT1 = p1 != null && p.getUniqueId().equals(p1.getUniqueId());
        String myC  = isT1 ? "§9" : "§c";
        String oppC = isT1 ? "§c" : "§9";

        lines.add("§fYou:   " + circles(myScore,  rounds, myC));
        lines.add("§fThem:  " + circles(oppScore, rounds, oppC));
        lines.add("");
        lines.add("§fGoals: §a" + myScore);
        lines.add("§fKills: §a" + plugin.playerMatchKills.getOrDefault(p.getUniqueId(), 0));
        lines.add("");
        lines.add("§fPing: §7" + getPing(p) + "ms");
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
        String blueYou = playerIsBlue ? " §7(You)" : "";
        String redYou  = playerIsBlue ? "" : " §7(You)";
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
        lines.add("§fGoals: §a" + myScore);
        lines.add("§fKills: §a" + plugin.playerMatchKills.getOrDefault(p.getUniqueId(), 0));
        lines.add("");
        lines.add("§fPing: §7" + getPing(p) + "ms");
        return lines;
    }

    private List<String> buildPartyGeneric(Player p, Object fight) {
        List<String> lines = new ArrayList<>();
        lines.add("§fKills: §a" + plugin.playerMatchKills.getOrDefault(p.getUniqueId(), 0));
        lines.add("");
        lines.add("§fPing: §7" + getPing(p) + "ms");
        return lines;
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null || !plugin.isHooked()) return "";

        if (identifier.equalsIgnoreCase("highest_winstreak"))
            return plugin.leaderboardManager != null
                    ? plugin.leaderboardManager.getHighestWinstreakString(player.getUniqueId()) : "0";

        boolean isEnded = plugin.getPlayerMatchResults().containsKey(player.getUniqueId());
        Object fight    = getFight(player);
        String kitName  = getKitName(player, fight);

        boolean isParty   = fight != null && (
                (plugin.partySplitManager   != null && plugin.partySplitManager.isPartySplit(fight)) ||
                        (plugin.partyVsPartyManager != null && plugin.partyVsPartyManager.isPartyVsParty(fight)));
        boolean isBedwars = kitName.contains("bed") || kitName.contains("fireball");
        boolean isBridge  = kitName.contains("bridge");
        boolean isBestOf  = isBridge || kitName.contains("mlgrush") || kitName.contains("stickfight");
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
            } else if (fight == null) {
                lines = new ArrayList<>();
            } else if (isParty) {
                if (isBedwars)     lines = buildPartyBedwars(player, fight);
                else if (isBridge) lines = buildPartyBridge(player, fight);
                else               lines = buildPartyGeneric(player, fight);
            } else if (isBedwars && p1 != null) {
                lines = buildBedwars(player, fight, p1);
            } else {
                lines = new ArrayList<>();
            }

            if (lineNum >= 1 && lineNum <= lines.size())
                return ChatColor.translateAlternateColorCodes('&', lines.get(lineNum - 1));
            return isEnded ? "[display=!<ended>]" : "[display=<ended>]";
        }

        if (identifier.equalsIgnoreCase("result"))
            return isEnded ? plugin.getPlayerMatchResults().getOrDefault(player.getUniqueId(), "") : "";

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
            int lim = plugin.getConfig().getInt("settings.bridge-party-score-limit", 5);
            return circles(t1 ? sc[0] : sc[1], lim, t1 ? "§9" : "§c");
        }
        if (identifier.equalsIgnoreCase("party_circles_enemy")) {
            if (fight == null || !isParty) return "";
            boolean t1 = isPartyTeam1(player, fight); int[] sc = partyBridgeScores(fight);
            int lim = plugin.getConfig().getInt("settings.bridge-party-score-limit", 5);
            return circles(t1 ? sc[1] : sc[0], lim, t1 ? "§c" : "§9");
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