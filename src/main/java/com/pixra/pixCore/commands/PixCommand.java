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

                sender.sendMessage(ChatColor.GREEN + "[PixCore] Plugin configurations reloaded!");
                return true;
            }

            if (args[0].equalsIgnoreCase("bed")) {
                if (!sender.hasPermission("pixcore.admin")) {
                    sender.sendMessage(ChatColor.RED + "You do not have permission.");
                    return true;
                }

                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can use this command.");
                    return true;
                }

                Player player = (Player) sender;

                Material bedMaterial = Material.getMaterial("RED_BED");
                if (bedMaterial == null) {
                    bedMaterial = Material.getMaterial("BED");
                }
                if (bedMaterial == null) {
                    bedMaterial = Material.STONE;
                }

                ItemStack item = new ItemStack(bedMaterial);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.AQUA + "Arena Bed Fixer");
                    List<String> lore = new ArrayList<>();
                    lore.add(ChatColor.GRAY + "Break an extra bed part in arena");
                    lore.add(ChatColor.GRAY + "to register it correctly!");
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }

                player.getInventory().addItem(item);
                player.sendMessage(ChatColor.GREEN + "[PixCore] You received the Arena Bed Fixer.");
                return true;
            }

            if (args[0].equalsIgnoreCase("leaderboard")) {
                if (args.length >= 2 && args[1].equalsIgnoreCase("add")) {
                    if (!sender.hasPermission("pixcore.admin")) {
                        sender.sendMessage(ChatColor.RED + "You do not have permission.");
                        return true;
                    }
                    if (args.length >= 3) {
                        String kitName = args[2];
                        if (plugin.hologramManager != null) {
                            if (sender instanceof Player) {
                                plugin.hologramManager.registerKit((Player) sender, kitName);
                            } else {
                                plugin.hologramManager.registerKitConsole(kitName);
                                sender.sendMessage(ChatColor.GREEN + "[PixCore] Kit " + kitName.toUpperCase() + " registered for leaderboard holograms.");
                            }
                        }
                        return true;
                    }
                    sender.sendMessage(ChatColor.RED + "Usage: /pix leaderboard add <kit>");
                    return true;
                }

                if (args.length >= 2 && args[1].equalsIgnoreCase("remove")) {
                    if (!sender.hasPermission("pixcore.admin")) {
                        sender.sendMessage(ChatColor.RED + "You do not have permission.");
                        return true;
                    }
                    if (args.length >= 3) {
                        String kitName = args[2];
                        if (plugin.hologramManager != null) {
                            if (sender instanceof Player) {
                                plugin.hologramManager.unregisterKit((Player) sender, kitName);
                            } else {
                                plugin.hologramManager.unregisterKitConsole(kitName);
                                sender.sendMessage(ChatColor.GREEN + "[PixCore] Kit " + kitName.toUpperCase() + " unregistered.");
                            }
                        }
                        return true;
                    }
                    sender.sendMessage(ChatColor.RED + "Usage: /pix leaderboard remove <kit>");
                    return true;
                }

                if (args.length >= 2 && args[1].equalsIgnoreCase("set")) {
                    if (!sender.hasPermission("pixcore.admin")) {
                        sender.sendMessage(ChatColor.RED + "You do not have permission.");
                        return true;
                    }
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(ChatColor.RED + "Only players can place holograms.");
                        return true;
                    }
                    if (args.length >= 4) {
                        String category = args[2];
                        String period   = args[3];
                        if (plugin.hologramManager != null)
                            plugin.hologramManager.placeStandingHologram((Player) sender, category, period);
                    } else {
                        sender.sendMessage(ChatColor.RED + "Usage: /pix leaderboard set <ws|wins|kills> <daily|weekly|monthly|lifetime>");
                        sender.sendMessage(ChatColor.GRAY + "Note: ws only supports 'daily'.");
                    }
                    return true;
                }

                if (args.length >= 2 && args[1].equalsIgnoreCase("setremove")) {
                    if (!sender.hasPermission("pixcore.admin")) {
                        sender.sendMessage(ChatColor.RED + "You do not have permission.");
                        return true;
                    }
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(ChatColor.RED + "Only players can use this.");
                        return true;
                    }
                    if (args.length >= 3) {
                        try {
                            int id = Integer.parseInt(args[2]);
                            if (plugin.hologramManager != null)
                                plugin.hologramManager.removeStandingHologram((Player) sender, id);
                        } catch (NumberFormatException e) {
                            sender.sendMessage(ChatColor.RED + "ID must be a number.");
                        }
                    } else {
                        sender.sendMessage(ChatColor.RED + "Usage: /pix leaderboard setremove <id>");
                    }
                    return true;
                }

                if (args.length >= 2 && args[1].equalsIgnoreCase("setlist")) {
                    if (!sender.hasPermission("pixcore.admin")) {
                        sender.sendMessage(ChatColor.RED + "You do not have permission.");
                        return true;
                    }
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(ChatColor.RED + "Only players can use this.");
                        return true;
                    }
                    if (plugin.hologramManager != null)
                        plugin.hologramManager.listStandingHolograms((Player) sender);
                    return true;
                }

                if (args.length >= 2 && args[1].equalsIgnoreCase("enable")) {
                    if (!sender.hasPermission("pixcore.admin")) {
                        sender.sendMessage(ChatColor.RED + "You do not have permission.");
                        return true;
                    }
                    if (args.length >= 3) {
                        String kitName = args[2];
                        plugin.leaderboardManager.setKitEnabled(kitName, true);
                        sender.sendMessage(ChatColor.GREEN + "[PixCore] Leaderboard for kit " + kitName + " has been ENABLED.");
                    } else {
                        plugin.leaderboardManager.setGlobalEnabled(true);
                        sender.sendMessage(ChatColor.GREEN + "[PixCore] Global Leaderboards have been ENABLED.");
                    }
                    return true;
                }

                if (args.length >= 2 && args[1].equalsIgnoreCase("disable")) {
                    if (!sender.hasPermission("pixcore.admin")) {
                        sender.sendMessage(ChatColor.RED + "You do not have permission.");
                        return true;
                    }
                    if (args.length >= 3) {
                        String kitName = args[2];
                        plugin.leaderboardManager.setKitEnabled(kitName, false);
                        sender.sendMessage(ChatColor.YELLOW + "[PixCore] Leaderboard for kit " + kitName + " has been DISABLED.");
                    } else {
                        plugin.leaderboardManager.setGlobalEnabled(false);
                        sender.sendMessage(ChatColor.YELLOW + "[PixCore] Global Leaderboards have been DISABLED.");
                    }
                    return true;
                }

                if (args.length >= 2 && args[1].equalsIgnoreCase("gui")) {
                    if (!sender.hasPermission("pixcore.admin")) {
                        sender.sendMessage(ChatColor.RED + "You do not have permission.");
                        return true;
                    }

                    if (args.length >= 5 && args[2].equalsIgnoreCase("set")) {
                        if (!(sender instanceof Player)) {
                            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
                            return true;
                        }
                        Player player = (Player) sender;
                        String kitName = args[3];
                        int slot;
                        try {
                            slot = Integer.parseInt(args[4]);
                        } catch (NumberFormatException e) {
                            sender.sendMessage(ChatColor.RED + "Slot must be a number!");
                            return true;
                        }

                        ItemStack hand = player.getItemInHand();
                        if (hand == null || hand.getType() == Material.AIR) {
                            sender.sendMessage(ChatColor.RED + "You must hold an item in your hand to set it as icon!");
                            return true;
                        }

                        plugin.leaderboardGUIManager.setGuiItem(kitName, slot, hand);
                        sender.sendMessage(ChatColor.GREEN + "[PixCore] Kit " + kitName + " added to GUI at slot " + slot + ".");
                        return true;
                    }

                    if (args.length >= 4 && args[2].equalsIgnoreCase("remove")) {
                        String kitName = args[3];
                        plugin.leaderboardGUIManager.removeGuiItem(kitName);
                        sender.sendMessage(ChatColor.GREEN + "[PixCore] Kit " + kitName + " removed from GUI.");
                        return true;
                    }

                    sender.sendMessage(ChatColor.RED + "Usage: /pix leaderboard gui set <kit> <slot> OR /pix leaderboard gui remove <kit>");
                    return true;
                }

                if (args.length >= 2 && args[1].equalsIgnoreCase("backup")) {
                    if (!sender.hasPermission("pixcore.admin")) {
                        sender.sendMessage(ChatColor.RED + "You do not have permission.");
                        return true;
                    }

                    String prefix = args.length >= 3 ? args[2] : "manual";
                    plugin.leaderboardManager.backupData(prefix);
                    sender.sendMessage(ChatColor.GREEN + "[PixCore] Data leaderboard berhasil dibackup dengan prefix '" + prefix + "'.");
                    return true;
                }

                if (args.length >= 2 && args[1].equalsIgnoreCase("backups")) {
                    if (!sender.hasPermission("pixcore.admin")) {
                        sender.sendMessage(ChatColor.RED + "You do not have permission.");
                        return true;
                    }

                    List<String> files = plugin.leaderboardManager.getBackupFiles();
                    if (files.isEmpty()) {
                        sender.sendMessage(ChatColor.YELLOW + "Belum ada file backup.");
                        return true;
                    }

                    sender.sendMessage(ChatColor.GREEN + "=== File Backup Leaderboard ===");
                    for (int i = 0; i < Math.min(10, files.size()); i++) {
                        sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.AQUA + files.get(i));
                    }
                    if (files.size() > 10) {
                        sender.sendMessage(ChatColor.GRAY + "... dan " + (files.size() - 10) + " file lainnya.");
                    }
                    return true;
                }

                if (args.length >= 2 && args[1].equalsIgnoreCase("restore")) {
                    if (!sender.hasPermission("pixcore.admin")) {
                        sender.sendMessage(ChatColor.RED + "You do not have permission.");
                        return true;
                    }

                    if (args.length < 3) {
                        sender.sendMessage(ChatColor.RED + "Usage: /pix leaderboard restore <nama_file.yml>");
                        return true;
                    }

                    String fileName = args[2];
                    if (plugin.leaderboardManager.restoreData(fileName)) {
                        sender.sendMessage(ChatColor.GREEN + "[PixCore] Data leaderboard berhasil dipulihkan dari " + fileName + "!");
                        if (plugin.hologramManager != null) plugin.hologramManager.reload();
                    } else {
                        sender.sendMessage(ChatColor.RED + "[PixCore] Gagal memulihkan data. File tidak ditemukan.");
                    }
                    return true;
                }

                if (args.length >= 2 && args[1].equalsIgnoreCase("reset")) {
                    if (!sender.hasPermission("pixcore.admin")) {
                        sender.sendMessage(ChatColor.RED + "You do not have permission.");
                        return true;
                    }

                    if (args.length == 3 && args[2].equalsIgnoreCase("all")) {
                        plugin.leaderboardManager.resetAllData();
                        if (plugin.hologramManager != null) plugin.hologramManager.reload();
                        sender.sendMessage(ChatColor.GREEN + "[PixCore] Berhasil menghapus SELURUH data leaderboard (Winstreak, Wins, Kills untuk semua Kits)!");
                        return true;
                    }

                    if (args.length >= 4) {
                        String category = args[2].toLowerCase();
                        String kitName = args[3];

                        if (!category.equals("ws") && !category.equals("winstreak") && !category.equals("wins") && !category.equals("kills")) {
                            sender.sendMessage(ChatColor.RED + "Kategori tidak valid! Gunakan: ws, wins, atau kills.");
                            return true;
                        }

                        plugin.leaderboardManager.resetCategoryData(category, kitName);
                        if (plugin.hologramManager != null) plugin.hologramManager.reload();
                        sender.sendMessage(ChatColor.GREEN + "[PixCore] Berhasil menghapus data " + category.toUpperCase() + " untuk kit " + kitName.toUpperCase() + "!");
                        return true;
                    }

                    sender.sendMessage(ChatColor.RED + "Usage: /pix leaderboard reset <ws|wins|kills|all> [kit]");
                    return true;
                }
            }
        }

        sender.sendMessage(ChatColor.YELLOW + "=== PixCore Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/pix reload" + ChatColor.GRAY + " - Reload configurations");
        sender.sendMessage(ChatColor.YELLOW + "/pix bed" + ChatColor.GRAY + " - Get the custom Arena Bed Fixer");
        sender.sendMessage(ChatColor.YELLOW + "/pix leaderboard add <kit>" + ChatColor.GRAY + " - Register kit for countdown holograms");
        sender.sendMessage(ChatColor.YELLOW + "/pix leaderboard remove <kit>" + ChatColor.GRAY + " - Unregister kit countdown hologram");
        sender.sendMessage(ChatColor.YELLOW + "/pix leaderboard set <ws|wins|kills> <period>" + ChatColor.GRAY + " - Place standing hologram at your position");
        sender.sendMessage(ChatColor.YELLOW + "/pix leaderboard setremove <id>" + ChatColor.GRAY + " - Remove standing hologram by ID");
        sender.sendMessage(ChatColor.YELLOW + "/pix leaderboard setlist" + ChatColor.GRAY + " - List all standing holograms");
        sender.sendMessage(ChatColor.YELLOW + "/pix leaderboard enable [kit]" + ChatColor.GRAY + " - Enable leaderboard");
        sender.sendMessage(ChatColor.YELLOW + "/pix leaderboard disable [kit]" + ChatColor.GRAY + " - Disable leaderboard");
        sender.sendMessage(ChatColor.YELLOW + "/pix leaderboard gui set <kit> <slot>" + ChatColor.GRAY + " - Set GUI item (hold item)");
        sender.sendMessage(ChatColor.YELLOW + "/pix leaderboard gui remove <kit>" + ChatColor.GRAY + " - Remove GUI item");
        sender.sendMessage(ChatColor.YELLOW + "/pix leaderboard backup" + ChatColor.GRAY + " - Backup data manual");
        sender.sendMessage(ChatColor.YELLOW + "/pix leaderboard backups" + ChatColor.GRAY + " - List data backup");
        sender.sendMessage(ChatColor.YELLOW + "/pix leaderboard restore <file>" + ChatColor.GRAY + " - Restore data");
        sender.sendMessage(ChatColor.YELLOW + "/pix leaderboard reset <ws/wins/kills> <kit>" + ChatColor.GRAY + " - Reset specific data");
        sender.sendMessage(ChatColor.YELLOW + "/pix leaderboard reset all" + ChatColor.GRAY + " - Reset ALL leaderboard data");

        return true;
    }
}