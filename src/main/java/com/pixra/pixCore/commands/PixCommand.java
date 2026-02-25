package com.pixra.pixCore.commands;

import com.pixra.pixCore.PixCore;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class PixCommand implements CommandExecutor {

    private final PixCore plugin;

    public PixCommand(PixCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0) {

            if (args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("pixcore.admin")) {
                    sender.sendMessage(ChatColor.RED + "You do not have permission.");
                    return true;
                }

                plugin.clearAllCaches();
                plugin.loadConfigValues();
                plugin.loadTntTickConfig();
                plugin.loadNoFallDamageConfig();

                if (plugin.customKnockbackManager != null) plugin.customKnockbackManager.loadConfig();
                if (plugin.bowHitMessageManager != null) plugin.bowHitMessageManager.loadConfig();
                if (plugin.stickFightManager != null) plugin.stickFightManager.loadConfig();
                if (plugin.getKillMessageManager() != null) plugin.getKillMessageManager().loadConfig();
                if (plugin.hitActionBarManager != null) plugin.hitActionBarManager.loadConfig();

                if (plugin.leaderboardManager != null) plugin.leaderboardManager.reload();
                if (plugin.hologramManager != null) plugin.hologramManager.reload();

                sender.sendMessage(ChatColor.GREEN + "[PixCore] Configuration reloaded successfully.");
                return true;
            }

            if (args[0].equalsIgnoreCase("bed")) {
                if (!sender.hasPermission("pixcore.admin")) {
                    sender.sendMessage(ChatColor.RED + "You do not have permission.");
                    return true;
                }

                if (sender instanceof Player) {
                    Player p = (Player) sender;

                    Material bedMat = Material.getMaterial("BED");
                    if (bedMat == null) {
                        bedMat = Material.getMaterial("RED_BED");
                    }

                    ItemStack bed = new ItemStack(bedMat);
                    ItemMeta meta = bed.getItemMeta();
                    meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "Arena Bed Fixer");
                    List<String> lore = new ArrayList<>();
                    lore.add(ChatColor.GRAY + "Place this bed in your arena");
                    lore.add(ChatColor.GRAY + "to permanently save its exact");
                    lore.add(ChatColor.GRAY + "position for perfect restorations.");
                    meta.setLore(lore);
                    bed.setItemMeta(meta);

                    p.getInventory().addItem(bed);
                    p.sendMessage(ChatColor.GREEN + "[PixCore] You received the Arena Bed Fixer!");
                } else {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command.");
                }
                return true;
            }

            if (args[0].equalsIgnoreCase("leaderboard")) {
                if (!sender.hasPermission("pixcore.admin")) {
                    sender.sendMessage(ChatColor.RED + "You do not have permission.");
                    return true;
                }

                if (args.length < 3) {
                    sender.sendMessage(ChatColor.YELLOW + "Usage: /pix leaderboard <add|remove> <kit_name>");
                    return true;
                }

                String action = args[1].toLowerCase();
                String kitName = ChatColor.stripColor(args[2]).toLowerCase();

                if (action.equals("add")) {
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(ChatColor.RED + "Only players in-game can add a leaderboard hologram.");
                        return true;
                    }
                    Player p = (Player) sender;

                    if (plugin.hologramManager != null) {
                        plugin.hologramManager.createStaticHologram(p, kitName);
                        p.sendMessage(ChatColor.GREEN + "[PixCore] Winstreak Leaderboard untuk kit '" + ChatColor.YELLOW + kitName + ChatColor.GREEN + "' berhasil ditambahkan di depan pandanganmu!");
                    } else {
                        p.sendMessage(ChatColor.RED + "HologramManager belum terinisialisasi.");
                    }

                } else if (action.equals("remove")) {
                    if (plugin.hologramManager != null) {
                        plugin.hologramManager.removeStaticHologram(kitName);
                        sender.sendMessage(ChatColor.GREEN + "[PixCore] Winstreak Leaderboard untuk kit '" + ChatColor.YELLOW + kitName + ChatColor.GREEN + "' berhasil dihapus!");
                    } else {
                        sender.sendMessage(ChatColor.RED + "HologramManager belum terinisialisasi.");
                    }

                } else {
                    sender.sendMessage(ChatColor.YELLOW + "Usage: /pix leaderboard <add|remove> <kit_name>");
                }
                return true;
            }
        }

        sender.sendMessage(ChatColor.YELLOW + "=== PixCore Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/pix reload" + ChatColor.GRAY + " - Reload configurations");
        sender.sendMessage(ChatColor.YELLOW + "/pix bed" + ChatColor.GRAY + " - Get the custom Arena Bed Fixer");
        sender.sendMessage(ChatColor.YELLOW + "/pix leaderboard add <kit>" + ChatColor.GRAY + " - Place Winstreak Hologram");
        sender.sendMessage(ChatColor.YELLOW + "/pix leaderboard remove <kit>" + ChatColor.GRAY + " - Remove Winstreak Hologram");
        return true;
    }
}