package com.pixra.pixCore.placeholders;

import com.pixra.pixCore.PixCore;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class PixCorePlaceholders extends PlaceholderExpansion {

    private final PixCore plugin;

    public PixCorePlaceholders(PixCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "pixcore";
    }

    @Override
    public String getAuthor() {
        return "Pixra";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    private String getBedStatusRaw(Object fight, boolean isBlue, Player player, Player p1) {
        if (fight == null || p1 == null) return ChatColor.RED + "\u2718";
        ChatColor teamColor = isBlue ? ChatColor.BLUE : ChatColor.RED;

        boolean bedBroken = false;
        if (plugin.getMIsBed1Broken() != null && plugin.getMIsBed2Broken() != null) {
            try {
                bedBroken = (boolean) (isBlue ? plugin.getMIsBed1Broken().invoke(fight) : plugin.getMIsBed2Broken().invoke(fight));
            } catch (Exception ignored) {}
        }

        if (!bedBroken) {
            return ChatColor.GREEN + "\u2714";
        }

        int aliveCount = 0;
        if (plugin.getMGetPlayersInFight() != null) {
            try {
                @SuppressWarnings("unchecked")
                List<Player> alivePlayers = (List<Player>) plugin.getMGetPlayersInFight().invoke(fight);
                if (alivePlayers != null) {
                    for (Player p : alivePlayers) {
                        boolean isTeam1 = p.getUniqueId().equals(p1.getUniqueId());
                        if (!isTeam1 && plugin.getMPlayersAreTeammates() != null) {
                            isTeam1 = (boolean) plugin.getMPlayersAreTeammates().invoke(fight, p1, p);
                        } else if (!isTeam1 && plugin.getMGetTeammates() != null) {
                            @SuppressWarnings("unchecked")
                            List<String> tms = (List<String>) plugin.getMGetTeammates().invoke(fight, p1);
                            if (tms != null && tms.contains(p.getName())) isTeam1 = true;
                        }

                        if (isBlue && isTeam1) aliveCount++;
                        else if (!isBlue && !isTeam1) aliveCount++;
                    }
                }
            } catch (Exception ignored) {}
        }

        if (aliveCount > 0) {
            return teamColor + String.valueOf(aliveCount);
        } else {
            return ChatColor.RED + "\u2718";
        }
    }

    private String getTeamIndicatorRaw(Object fight, boolean isBlue, Player player, Player p1) {
        if (fight == null || p1 == null) return "";
        boolean isTeam1 = player.getUniqueId().equals(p1.getUniqueId());
        if (!isTeam1 && plugin.getMPlayersAreTeammates() != null) {
            try {
                isTeam1 = (boolean) plugin.getMPlayersAreTeammates().invoke(fight, p1, player);
            } catch (Exception ignored) {}
        } else if (!isTeam1 && plugin.getMGetTeammates() != null) {
            try {
                @SuppressWarnings("unchecked")
                List<String> tms = (List<String>) plugin.getMGetTeammates().invoke(fight, p1);
                if (tms != null && tms.contains(player.getName())) isTeam1 = true;
            } catch (Exception ignored) {}
        }

        if (isBlue && isTeam1) {
            return " &7YOU";
        } else if (!isBlue && !isTeam1) {
            return " &7YOU";
        }
        return "";
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null || !plugin.isHooked()) return "";

        if (identifier.equalsIgnoreCase("highest_winstreak")) {
            if (plugin.leaderboardManager != null) {
                return plugin.leaderboardManager.getHighestWinstreakString(player.getUniqueId());
            }
            return "0";
        }

        boolean isEnded = plugin.getPlayerMatchResults().containsKey(player.getUniqueId());

        Object strikeAPI = plugin.getStrikePracticeAPI();
        Object fight = null;
        try {
            fight = strikeAPI.getClass().getMethod("getFight", Player.class).invoke(strikeAPI, player);
        } catch (Exception ignored) {}

        Player p1 = null;
        if (fight != null && plugin.getMGetFirstPlayer() != null) {
            try {
                p1 = (Player) plugin.getMGetFirstPlayer().invoke(fight);
            } catch (Exception ignored) {}
        }

        boolean isBedwars = false;
        boolean isBestOf = false;
        if (fight != null) {
            try {
                Method mGetKit = strikeAPI.getClass().getMethod("getKit", Player.class);
                Object kit = mGetKit.invoke(strikeAPI, player);
                if (kit != null) {
                    String kitName = (String) kit.getClass().getMethod("getName").invoke(kit);
                    if (kitName != null) {
                        String lowerKit = kitName.toLowerCase();
                        if (lowerKit.contains("bed") || lowerKit.contains("fireball")) {
                            isBedwars = true;
                        }
                        if (lowerKit.contains("bridge")) {
                            isBestOf = true;
                        }
                    }
                }
            } catch (Exception ignored) {}

            try {
                Class<?> bestOfClass = Class.forName("ga.strikepractice.fights.BestOfFight");
                if (bestOfClass.isInstance(fight)) {
                    isBestOf = true;
                }
            } catch (Exception ignored) {}
        }

        if (identifier.startsWith("board_")) {
            int lineNum;
            try {
                lineNum = Integer.parseInt(identifier.substring(6));
            } catch (NumberFormatException e) {
                return "";
            }

            List<String> lines = new ArrayList<>();

            if (isEnded) {
                lines.add(plugin.getPlayerMatchResults().get(player.getUniqueId()));
            } else {
                if (isBedwars) {
                    lines.add("&9B Blue: " + getBedStatusRaw(fight, true, player, p1) + getTeamIndicatorRaw(fight, true, player, p1));
                    lines.add("&cR Red: " + getBedStatusRaw(fight, false, player, p1) + getTeamIndicatorRaw(fight, false, player, p1));
                    lines.add("");
                    lines.add("&fKills: &a" + plugin.getPlayerMatchKills().getOrDefault(player.getUniqueId(), 0));
                } else if (isBestOf) {
                    lines.add("&fKills: &a" + plugin.getPlayerMatchKills().getOrDefault(player.getUniqueId(), 0));
                }
            }

            if (lineNum >= 1 && lineNum <= lines.size()) {
                return lines.get(lineNum - 1);
            } else {
                return isEnded ? "[display=!<ended>]" : "[display=<ended>]";
            }
        }

        if (identifier.equalsIgnoreCase("result")) {
            if (isEnded) return plugin.getPlayerMatchResults().get(player.getUniqueId());
            return "";
        }

        if (identifier.equalsIgnoreCase("kills")) {
            if (isEnded) return "";
            if (!isBedwars && !isBestOf) return "";
            return String.valueOf(plugin.getPlayerMatchKills().getOrDefault(player.getUniqueId(), 0));
        }

        if (fight == null || p1 == null) return "";

        if (identifier.startsWith("bed_blue") || identifier.startsWith("bed_red")) {
            if (!isBedwars) return "";
            boolean isBlue = identifier.contains("blue");
            String prefix = isBlue ? "&9B Blue: " : "&cR Red: ";
            String bedStatus = getBedStatusRaw(fight, isBlue, player, p1);
            String teamIndicator = getTeamIndicatorRaw(fight, isBlue, player, p1);

            if (identifier.endsWith("_line")) return prefix + bedStatus + teamIndicator;
            else return bedStatus;
        }

        if (identifier.equalsIgnoreCase("team_indicator_blue") || identifier.equalsIgnoreCase("team_indicator_red")) {
            if (!isBedwars) return "";
            boolean isBlue = identifier.equalsIgnoreCase("team_indicator_blue");
            return getTeamIndicatorRaw(fight, isBlue, player, p1);
        }

        return null;
    }
}