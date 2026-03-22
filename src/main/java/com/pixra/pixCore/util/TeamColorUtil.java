package com.pixra.pixCore.util;

import com.pixra.pixCore.hook.StrikePracticeHook;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.scoreboard.Team;

import java.util.List;

public class TeamColorUtil {

    private final StrikePracticeHook hook;

    public TeamColorUtil(StrikePracticeHook hook) {
        this.hook = hook;
    }

    @SuppressWarnings("unchecked")
    public String getTeamColorCode(Player p, Object fight) {
        if (fight != null) {
            try {
                java.lang.reflect.Method gt1 = fight.getClass().getMethod("getTeam1");
                java.lang.reflect.Method gt2 = fight.getClass().getMethod("getTeam2");
                java.util.HashSet<String> team1 = (java.util.HashSet<String>) gt1.invoke(fight);
                java.util.HashSet<String> team2 = (java.util.HashSet<String>) gt2.invoke(fight);
                if (team1 != null && team1.contains(p.getName())) return "§9";
                if (team2 != null && team2.contains(p.getName())) return "§c";
            } catch (Exception ignored) {}

            try {
                java.lang.reflect.Method gp1m = fight.getClass().getMethod("getParty1");
                java.lang.reflect.Method gp2m = fight.getClass().getMethod("getParty2");
                Object party1 = gp1m.invoke(fight);
                Object party2 = gp2m.invoke(fight);
                if (party1 != null && party2 != null) {
                    try {
                        java.lang.reflect.Method gpl = party1.getClass().getMethod("getPlayers");
                        Object pl1 = gpl.invoke(party1);
                        if (pl1 instanceof Iterable) {
                            for (Object m : (Iterable<?>) pl1) {
                                if (m instanceof Player && ((Player) m).getUniqueId().equals(p.getUniqueId())) return "§9";
                            }
                        }
                        Object pl2 = gpl.invoke(party2);
                        if (pl2 instanceof Iterable) {
                            for (Object m : (Iterable<?>) pl2) {
                                if (m instanceof Player && ((Player) m).getUniqueId().equals(p.getUniqueId())) return "§c";
                            }
                        }
                        if ((pl1 instanceof java.util.List && !((java.util.List<?>) pl1).isEmpty())
                                || (pl2 instanceof java.util.List && !((java.util.List<?>) pl2).isEmpty())) {
                        }
                    } catch (Exception ignored) {}
                    try {
                        java.lang.reflect.Method gmn = party1.getClass().getMethod("getMembersNames");
                        Object mn1 = gmn.invoke(party1);
                        if (mn1 instanceof Iterable) {
                            for (Object m : (Iterable<?>) mn1) {
                                if (p.getName().equals(m)) return "§9";
                            }
                        }
                        Object mn2 = gmn.invoke(party2);
                        if (mn2 instanceof Iterable) {
                            for (Object m : (Iterable<?>) mn2) {
                                if (p.getName().equals(m)) return "§c";
                            }
                        }
                    } catch (Exception ignored) {}
                    try {
                        java.lang.reflect.Method gm = party1.getClass().getMethod("getMembers");
                        Object mb1 = gm.invoke(party1);
                        if (mb1 instanceof Iterable) {
                            for (Object m : (Iterable<?>) mb1) {
                                if (m instanceof Player && ((Player) m).getUniqueId().equals(p.getUniqueId())) return "§9";
                                if (m instanceof String && p.getName().equals(m)) return "§9";
                            }
                        }
                        Object mb2 = gm.invoke(party2);
                        if (mb2 instanceof Iterable) {
                            for (Object m : (Iterable<?>) mb2) {
                                if (m instanceof Player && ((Player) m).getUniqueId().equals(p.getUniqueId())) return "§c";
                                if (m instanceof String && p.getName().equals(m)) return "§c";
                            }
                        }
                    } catch (Exception ignored) {}
                    try {
                        java.lang.reflect.Method gpa1 = fight.getClass().getMethod("getPartyAlive1");
                        java.lang.reflect.Method gpa2 = fight.getClass().getMethod("getPartyAlive2");
                        java.util.HashSet<String> alive1 = (java.util.HashSet<String>) gpa1.invoke(fight);
                        java.util.HashSet<String> alive2 = (java.util.HashSet<String>) gpa2.invoke(fight);
                        if (alive1 != null && alive1.contains(p.getName())) return "§9";
                        if (alive2 != null && alive2.contains(p.getName())) return "§c";
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}

            try {
                Player rep = null;
                if (hook.getMGetFirstPlayer() != null) {
                    try { rep = (Player) hook.getMGetFirstPlayer().invoke(fight); }
                    catch (Exception ignored) {}
                }
                if (rep == null && hook.getMGetPlayers() != null) {
                    List<Player> players = (List<Player>) hook.getMGetPlayers().invoke(fight);
                    if (players != null && !players.isEmpty()) rep = players.get(0);
                }
                if (rep != null) {
                    if (p.getUniqueId().equals(rep.getUniqueId())) return "§9";
                    boolean isTeammate = false;
                    if (hook.getMPlayersAreTeammates() != null) {
                        isTeammate = (boolean) hook.getMPlayersAreTeammates().invoke(fight, rep, p);
                    } else if (hook.getMGetTeammates() != null) {
                        List<String> teammates = (List<String>) hook.getMGetTeammates().invoke(fight, rep);
                        if (teammates != null && teammates.contains(p.getName())) isTeammate = true;
                    }
                    return isTeammate ? "§9" : "§c";
                }
            } catch (Exception ignored) {}
        }
        return getArmorColorCode(p);
    }

    @SuppressWarnings("unchecked")
    public Color getTeamColor(Player p, Object fight) {
        if (fight == null) return getTeamColor(p);
        String code = getTeamColorCode(p, fight);
        if (code.contains("§c") || code.contains("§d")) return Color.RED;
        if (code.contains("§9") || code.contains("§b")) return Color.BLUE;
        if (code.contains("§a"))                         return Color.LIME;
        if (code.contains("§e"))                         return Color.YELLOW;
        return Color.BLUE;
    }

    @SuppressWarnings("unchecked")
    public Color getTeamColor(Player p) {
        if (!hook.isHooked() || hook.getMGetFight() == null) return Color.BLUE;
        try {
            Object fight = hook.getMGetFight().invoke(hook.getAPI(), p);
            if (fight == null) return Color.BLUE;

            if (hook.getMGetFirstPlayer() != null) {
                Player p1 = (Player) hook.getMGetFirstPlayer().invoke(fight);
                if (p1 != null && p1.getUniqueId().equals(p.getUniqueId())) return Color.BLUE;
            }
            if (hook.getMGetSecondPlayer() != null) {
                Player p2 = (Player) hook.getMGetSecondPlayer().invoke(fight);
                if (p2 != null && p2.getUniqueId().equals(p.getUniqueId())) return Color.RED;
            }

            List<Player> players = null;
            if (hook.getMGetPlayersInFight() != null)
                players = (List<Player>) hook.getMGetPlayersInFight().invoke(fight);
            if ((players == null || players.isEmpty()) && hook.getMGetPlayers() != null)
                players = (List<Player>) hook.getMGetPlayers().invoke(fight);

            if (players != null && !players.isEmpty()) {
                Player rep = players.get(0);
                if (rep.getUniqueId().equals(p.getUniqueId())) return Color.BLUE;
                if (hook.getMPlayersAreTeammates() != null) {
                    boolean isTeammate = (boolean) hook.getMPlayersAreTeammates().invoke(fight, rep, p);
                    return isTeammate ? Color.BLUE : Color.RED;
                }
            }
        } catch (Exception ignored) {}
        return Color.BLUE;
    }

    public String getArmorColorCode(Player p) {
        ItemStack chest = p.getInventory().getChestplate();
        if (chest != null && chest.getType().name().contains("LEATHER_CHESTPLATE")) {
            LeatherArmorMeta meta  = (LeatherArmorMeta) chest.getItemMeta();
            Color            color = meta.getColor();
            if (isSimilarColor(color, Color.RED))                                       return "§c";
            if (isSimilarColor(color, Color.BLUE))                                      return "§9";
            if (isSimilarColor(color, Color.LIME))                                      return "§a";
            if (isSimilarColor(color, Color.YELLOW))                                    return "§e";
            if (isSimilarColor(color, Color.AQUA))                                      return "§b";
            if (isSimilarColor(color, Color.WHITE))                                     return "§f";
            if (isSimilarColor(color, Color.GRAY) || isSimilarColor(color, Color.SILVER)) return "§7";
            if (color.getRed()   > color.getBlue()  && color.getRed()   > color.getGreen()) return "§c";
            if (color.getBlue()  > color.getRed()   && color.getBlue()  > color.getGreen()) return "§9";
            if (color.getGreen() > color.getRed()   && color.getGreen() > color.getBlue())  return "§a";
        }

        String colorCode = "§f";
        try {
            Team team = null;
            try { team = p.getScoreboard().getPlayerTeam(p); }
            catch (NoSuchMethodError e) {
                try { team = p.getScoreboard().getEntryTeam(p.getName()); } catch (Exception ignored) {}
            }
            if (team != null) colorCode = extractColorCode(team.getPrefix());
        } catch (Exception ignored) {}

        if (colorCode.equals("§f")) colorCode = extractColorCode(p.getDisplayName());
        return colorCode;
    }

    public String getColorNameFromCode(String code) {
        if (code.contains("§c")) return "§cRed";
        if (code.contains("§9")) return "§9Blue";
        if (code.contains("§a")) return "§aGreen";
        if (code.contains("§e")) return "§eYellow";
        if (code.contains("§b")) return "§bAqua";
        if (code.contains("§d")) return "§dPink";
        if (code.contains("§7")) return "§7Gray";
        return code + "Opponent";
    }

    public void colorItem(ItemStack item, Color color) {
        if (item == null || item.getType() == org.bukkit.Material.AIR || color == null) return;
        String name = item.getType().name();
        if (name.contains("LEATHER_")) {
            LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
            meta.setColor(color);
            item.setItemMeta(meta);
        } else if (name.equals("WOOL") || name.equals("STAINED_CLAY")
                || name.equals("STAINED_GLASS") || name.equals("CARPET")) {
            Byte data = legacyColorData(color);
            if (data != null) item.setDurability(data);
        }
    }

    private Byte legacyColorData(Color color) {
        if (isSimilarColor(color, Color.WHITE))  return (byte) 0;
        if (isSimilarColor(color, Color.ORANGE)) return (byte) 1;
        if (isSimilarColor(color, Color.AQUA))   return (byte) 3;
        if (isSimilarColor(color, Color.YELLOW)) return (byte) 4;
        if (isSimilarColor(color, Color.LIME))   return (byte) 5;
        if (isSimilarColor(color, Color.PURPLE)) return (byte) 10;
        if (isSimilarColor(color, Color.BLUE))   return (byte) 11;
        if (isSimilarColor(color, Color.RED))    return (byte) 14;
        return null;
    }

    public boolean isItemMatch(ItemStack target, ItemStack poolItem) {
        if (target.getType() != poolItem.getType()) return false;
        String typeName = target.getType().name();
        if (typeName.contains("WOOL") || typeName.contains("CLAY")
                || typeName.contains("GLASS") || typeName.contains("CARPET")) return true;
        if (typeName.contains("POTION") || typeName.contains("INK_SACK"))
            return target.getDurability() == poolItem.getDurability();
        return true;
    }

    public boolean isSimilarColor(Color c1, Color c2) {
        int diff = Math.abs(c1.getRed()   - c2.getRed())
                + Math.abs(c1.getGreen() - c2.getGreen())
                + Math.abs(c1.getBlue()  - c2.getBlue());
        return diff < 30;
    }

    public String extractColorCode(String text) {
        String last = ChatColor.getLastColors(text);
        return (last == null || last.isEmpty()) ? "§f" : last;
    }
}
