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
        if (item == null || item.getType() == org.bukkit.Material.AIR) return;
        String name = item.getType().name();
        if (name.contains("LEATHER_")) {
            LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
            meta.setColor(color);
            item.setItemMeta(meta);
        } else if (name.equals("WOOL") || name.equals("STAINED_CLAY")
                || name.equals("STAINED_GLASS") || name.equals("CARPET")) {
            item.setDurability(color == Color.BLUE ? (byte) 11 : (byte) 14);
        }
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