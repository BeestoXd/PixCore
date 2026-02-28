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
                if (plugin.leaderboardGUIManager != null) plugin.leaderboardGUIManager.loadConfig();

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

            // =====================================
            // COMMAND: /pix leaderboard ...
            // =====================================
            if (args[0].equalsIgnoreCase("leaderboard")) {
                if (!sender.hasPermission("pixcore.admin")) {
                    sender.sendMessage(ChatColor.RED + "You do not have permission.");
                    return true;
                }

                if (args.length < 2) {
                    sender.sendMessage(ChatColor.YELLOW + "Usage: /pix leaderboard <add|remove|enable|disable|gui|backup|backups|restore> ...");
                    return true;
                }

                String action = args[1].toLowerCase();

                // --- BACKUP & RESTORE COMMANDS ---
                if (action.equals("backup")) {
                    if (plugin.leaderboardManager != null) {
                        plugin.leaderboardManager.backupData("manual");
                        sender.sendMessage(ChatColor.GREEN + "[PixCore] Data Leaderboard berhasil di-backup secara manual!");
                    }
                    return true;
                }

                if (action.equals("backups")) {
                    if (plugin.leaderboardManager != null) {
                        List<String> backups = plugin.leaderboardManager.getBackupFiles();
                        sender.sendMessage(ChatColor.YELLOW + "=== Available Backups ===");
                        if (backups.isEmpty()) {
                            sender.sendMessage(ChatColor.GRAY + "Belum ada file backup.");
                        } else {
                            for (int i = 0; i < Math.min(backups.size(), 10); i++) {
                                sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.AQUA + backups.get(i));
                            }
                            if (backups.size() > 10) {
                                sender.sendMessage(ChatColor.GRAY + "... dan " + (backups.size() - 10) + " lainnya.");
                            }
                        }
                    }
                    return true;
                }

                if (action.equals("restore")) {
                    if (args.length < 3) {
                        sender.sendMessage(ChatColor.YELLOW + "Usage: /pix leaderboard restore <nama_file.yml>");
                        return true;
                    }
                    String fileName = args[2];
                    if (!fileName.endsWith(".yml")) fileName += ".yml";

                    if (plugin.leaderboardManager != null) {
                        boolean success = plugin.leaderboardManager.restoreData(fileName);
                        if (success) {
                            sender.sendMessage(ChatColor.GREEN + "[PixCore] Sukses mere-store Leaderboard dari " + fileName + "!");
                            if (plugin.hologramManager != null) plugin.hologramManager.reload();
                        } else {
                            sender.sendMessage(ChatColor.RED + "[PixCore] Gagal restore! File backup '" + fileName + "' tidak ditemukan.");
                        }
                    }
                    return true;
                }

                if (action.equals("enable") || action.equals("disable")) {
                    boolean enable = action.equals("enable");
                    if (args.length == 2) {
                        if (plugin.leaderboardManager != null) {
                            plugin.leaderboardManager.setGlobalEnabled(enable);
                            sender.sendMessage(ChatColor.GREEN + "[PixCore] Leaderboard (All Kits) globally " + (enable ? "enabled" : "disabled") + "!");
                        }
                    } else {
                        String kitName = ChatColor.stripColor(args[2]).toLowerCase();
                        if (plugin.leaderboardManager != null) {
                            plugin.leaderboardManager.setKitEnabled(kitName, enable);
                            sender.sendMessage(ChatColor.GREEN + "[PixCore] Leaderboard for kit '" + ChatColor.YELLOW + kitName + ChatColor.GREEN + "' has been " + (enable ? "enabled" : "disabled") + "!");
                        }
                    }
                    return true;
                }

                // NEW GUI SETUP COMMANDS
                if (action.equals("gui")) {
                    if (args.length < 4) {
                        sender.sendMessage(ChatColor.YELLOW + "Usage: /pix leaderboard gui <set|remove> <kit> [slot]");
                        return true;
                    }
                    String guiAction = args[2].toLowerCase();
                    String kitName = ChatColor.stripColor(args[3]).toLowerCase();

                    if (guiAction.equals("set")) {
                        if (!(sender instanceof Player)) {
                            sender.sendMessage(ChatColor.RED + "Only players can set GUI items.");
                            return true;
                        }
                        Player p = (Player) sender;
                        if (args.length < 5) {
                            p.sendMessage(ChatColor.YELLOW + "Usage: /pix leaderboard gui set <kit> <slot>");
                            return true;
                        }
                        int slot;
                        try { slot = Integer.parseInt(args[4]); } catch (Exception e) { p.sendMessage(ChatColor.RED + "Slot must be number."); return true; }

                        ItemStack item;
                        try { item = p.getInventory().getItemInMainHand(); }
                        catch (NoSuchMethodError e) { item = p.getItemInHand(); }

                        if (item == null || item.getType() == Material.AIR) {
                            p.sendMessage(ChatColor.RED + "You must hold an item in your hand!");
                            return true;
                        }

                        if (plugin.leaderboardGUIManager != null) {
                            plugin.leaderboardGUIManager.setGuiItem(kitName, slot, item);
                            p.sendMessage(ChatColor.GREEN + "[PixCore] GUI item for '" + kitName + "' set successfully at slot " + slot + "!");
                        }
                    } else if (guiAction.equals("remove")) {
                        if (plugin.leaderboardGUIManager != null) {
                            plugin.leaderboardGUIManager.removeGuiItem(kitName);
                            sender.sendMessage(ChatColor.GREEN + "[PixCore] GUI item for '" + kitName + "' removed.");
                        }
                    } else {
                        sender.sendMessage(ChatColor.YELLOW + "Usage: /pix leaderboard gui <set|remove> <kit> [slot]");
                    }
                    return true;
                }

                // POS COMMANDS (HOLOGRAMS)
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.YELLOW + "Usage: /pix leaderboard <add|remove> <kit_name> <pos>");
                    return true;
                }

                String kitName = ChatColor.stripColor(args[2]).toLowerCase();

                int pos;
                try {
                    pos = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Posisi <pos> harus berupa angka! Contoh: 1");
                    return true;
                }

                if (action.equals("add")) {
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(ChatColor.RED + "Only players in-game can add a leaderboard hologram.");
                        return true;
                    }
                    Player p = (Player) sender;

                    if (plugin.hologramManager != null) {
                        plugin.hologramManager.createStaticHologram(p, kitName, pos);
                        p.sendMessage(ChatColor.GREEN + "[PixCore] Winstreak Leaderboard untuk kit '" + ChatColor.YELLOW + kitName + ChatColor.GREEN + "' di posisi " + pos + " berhasil ditambahkan!");
                    } else {
                        p.sendMessage(ChatColor.RED + "HologramManager belum terinisialisasi.");
                    }

                } else if (action.equals("remove")) {
                    if (plugin.hologramManager != null) {
                        plugin.hologramManager.removeStaticHologram(kitName, pos);
                        sender.sendMessage(ChatColor.GREEN + "[PixCore] Winstreak Leaderboard untuk kit '" + ChatColor.YELLOW + kitName + ChatColor.GREEN + "' di posisi " + pos + " berhasil dihapus!");
                    } else {
                        sender.sendMessage(ChatColor.RED + "HologramManager belum terinisialisasi.");
                    }

                } else {
                    sender.sendMessage(ChatColor.YELLOW + "Usage: /pix leaderboard <add|remove> <kit_name> <pos>");
                }
                return true;
            }
        }

        // Tampilkan semua Help usage
        sender.sendMessage(ChatColor.YELLOW + "=== PixCore Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/pix reload" + ChatColor.GRAY + " - Reload configurations");
        sender.sendMessage(ChatColor.YELLOW + "/pix bed" + ChatColor.GRAY + " - Get the custom Arena Bed Fixer");
        sender.sendMessage(ChatColor.YELLOW + "/pix leaderboard add <kit> <pos>" + ChatColor.GRAY + " - Place Hologram (e.g. 1)");
        sender.sendMessage(ChatColor.YELLOW + "/pix leaderboard remove <kit> <pos>" + ChatColor.GRAY + " - Remove Hologram");
        sender.sendMessage(ChatColor.YELLOW + "/pix leaderboard enable [kit]" + ChatColor.GRAY + " - Enable leaderboard");
        sender.sendMessage(ChatColor.YELLOW + "/pix leaderboard disable [kit]" + ChatColor.GRAY + " - Disable leaderboard");
        sender.sendMessage(ChatColor.YELLOW + "/pix leaderboard gui set <kit> <slot>" + ChatColor.GRAY + " - Set GUI item (hold item)");
        sender.sendMessage(ChatColor.YELLOW + "/pix leaderboard gui remove <kit>" + ChatColor.GRAY + " - Remove GUI item");
        sender.sendMessage(ChatColor.YELLOW + "/pix leaderboard backup" + ChatColor.GRAY + " - Backup data manual");
        sender.sendMessage(ChatColor.YELLOW + "/pix leaderboard backups" + ChatColor.GRAY + " - List data backup");
        sender.sendMessage(ChatColor.YELLOW + "/pix leaderboard restore <file>" + ChatColor.GRAY + " - Restore data");
        return true;
    }
}