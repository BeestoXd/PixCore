package com.pixra.pixcore.command;

import com.pixra.pixcore.PixCore;
import com.pixra.pixcore.feature.ffa.PixFfaManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.StringJoiner;

public class PixFfaCommand implements CommandExecutor {

    private final PixCore plugin;

    public PixFfaCommand(PixCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        PixFfaManager manager = plugin.pixFfaManager;
        if (manager == null) {
            sender.sendMessage(ChatColor.RED + "PixFFA manager is not ready yet.");
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player player) {
                if (manager.isArenaPlayer(player)) {
                    manager.leaveArena(player, true);
                } else {
                    manager.joinArena(player);
                }
                return true;
            }

            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("join")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Only players can use this command.");
                return true;
            }
            manager.joinArena(player);
            return true;
        }

        if (sub.equals("leave")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Only players can use this command.");
                return true;
            }
            if (!manager.leaveArena(player, true)) {
                sender.sendMessage(ChatColor.RED + "You are not currently in the FFA arena.");
            }
            return true;
        }

        if (sub.equals("reload")) {
            if (!sender.hasPermission("pixcore.admin")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission.");
                return true;
            }
            manager.shutdown(true);
            manager.reload();
            manager.bootstrapOnlinePlayers();
            sender.sendMessage(ChatColor.GREEN + "[PixFFA] pixffa.yml reloaded successfully.");
            return true;
        }

        if (sub.equals("pos")) {
            if (!sender.hasPermission("pixcore.admin")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission.");
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Only players can set the FFA position.");
                return true;
            }
            manager.setArenaPosition(player);
            return true;
        }

        if (sub.equals("void")) {
            if (!sender.hasPermission("pixcore.admin")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /pixffa void <y>");
                return true;
            }

            double value;
            try {
                value = Double.parseDouble(args[1]);
            } catch (NumberFormatException exception) {
                sender.sendMessage(ChatColor.RED + "Void height must be a valid number.");
                return true;
            }

            if (!manager.setVoidYThreshold(value)) {
                sender.sendMessage(ChatColor.RED + "Failed to update the void height.");
                return true;
            }

            sender.sendMessage(ChatColor.GREEN + "[PixFFA] Void height set to " + manager.getVoidYThreshold() + ".");
            return true;
        }

        if (sub.equals("battlekit")) {
            if (args.length < 2) {
                sendBattlekitHelp(sender);
                return true;
            }

            String battlekitSub = args[1].toLowerCase();

            if (battlekitSub.equals("list")) {
                StringJoiner joiner = new StringJoiner(ChatColor.GRAY + ", " + ChatColor.GOLD);
                for (String name : manager.getBattlekitNames()) {
                    joiner.add(name);
                }
                String active = manager.getActiveBattlekitName();
                String battlekits = joiner.length() == 0 ? ChatColor.RED + "none" : ChatColor.GOLD + joiner.toString();
                sender.sendMessage(ChatColor.GOLD + "Battlekit: " + battlekits);
                sender.sendMessage(ChatColor.YELLOW + "Active: " + ChatColor.GOLD + (active != null ? active : "-"));
                sender.sendMessage(ChatColor.YELLOW + "Build: " + ChatColor.GOLD + manager.isBuildEnabled());
                return true;
            }

            if (!sender.hasPermission("pixcore.admin")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission.");
                return true;
            }

            if (battlekitSub.equals("build")) {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /pixffa battlekit build <true|false>");
                    return true;
                }

                boolean value = Boolean.parseBoolean(args[2]);
                manager.setBuildEnabled(value);
                sender.sendMessage(ChatColor.GREEN + "[PixFFA] Build toggle set to " + value + ".");
                return true;
            }

            if (battlekitSub.equals("select")) {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /pixffa battlekit select <name>");
                    return true;
                }

                if (!manager.selectBattlekit(args[2])) {
                    sender.sendMessage(ChatColor.RED + "Battlekit not found.");
                    return true;
                }

                sender.sendMessage(ChatColor.GREEN + "[PixFFA] Active battlekit set to " + args[2].toLowerCase() + ".");
                return true;
            }

            if (battlekitSub.equals("create")) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can create a battlekit from their inventory.");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /pixffa battlekit create <name>");
                    return true;
                }

                if (!manager.createBattlekit(player, args[2])) {
                    sender.sendMessage(ChatColor.RED + "Failed to create the battlekit. The name may already exist.");
                    return true;
                }

                sender.sendMessage(ChatColor.GREEN + "[PixFFA] Battlekit " + args[2].toLowerCase() + " created successfully.");
                return true;
            }

            if (battlekitSub.equals("save")) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can save a battlekit.");
                    return true;
                }

                String kitName = args.length >= 3 ? args[2] : "";
                if (!manager.saveBattlekit(player, kitName)) {
                    sender.sendMessage(ChatColor.RED + "Failed to save the battlekit. Use /pixffa battlekit save <name> or enter edit mode first.");
                    return true;
                }

                String savedName = !kitName.isEmpty() ? kitName.toLowerCase() : "current";
                sender.sendMessage(ChatColor.GREEN + "[PixFFA] Battlekit " + savedName + " saved successfully.");
                return true;
            }

            if (battlekitSub.equals("edit")) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can edit a battlekit.");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /pixffa battlekit edit <name>");
                    return true;
                }

                if (!manager.startBattlekitEdit(player, args[2])) {
                    sender.sendMessage(ChatColor.RED + "Failed to enter battlekit edit mode.");
                    return true;
                }

                sender.sendMessage(ChatColor.GREEN + "[PixFFA] You are now editing battlekit " + args[2].toLowerCase() + ".");
                sender.sendMessage(ChatColor.YELLOW + "Use /pixffa battlekit save " + args[2].toLowerCase() + " when you are done.");
                return true;
            }

            if (battlekitSub.equals("cancel")) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can cancel battlekit editing.");
                    return true;
                }

                if (!manager.cancelBattlekitEdit(player)) {
                    sender.sendMessage(ChatColor.RED + "You are not editing any battlekit.");
                    return true;
                }

                sender.sendMessage(ChatColor.GREEN + "[PixFFA] Battlekit editing has been canceled.");
                return true;
            }

            sendBattlekitHelp(sender);
            return true;
        }

        sendHelp(sender);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "PixFFA Commands:");
        sender.sendMessage(ChatColor.YELLOW + "/pixffa" + ChatColor.GRAY + " - Join or leave the FFA arena");
        sender.sendMessage(ChatColor.YELLOW + "/pixffa join" + ChatColor.GRAY + " - Join the FFA arena");
        sender.sendMessage(ChatColor.YELLOW + "/pixffa leave" + ChatColor.GRAY + " - Leave the FFA arena");
        if (sender.hasPermission("pixcore.admin")) {
            sender.sendMessage(ChatColor.YELLOW + "/pixffa pos" + ChatColor.GRAY + " - Set the FFA respawn position");
            sender.sendMessage(ChatColor.YELLOW + "/pixffa void <y>" + ChatColor.GRAY + " - Set the FFA void height");
            sender.sendMessage(ChatColor.YELLOW + "/pixffa reload" + ChatColor.GRAY + " - Reload pixffa.yml");
            sendBattlekitHelp(sender);
        }
    }

    private void sendBattlekitHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "PixFFA Battlekit:");
        sender.sendMessage(ChatColor.YELLOW + "/pixffa battlekit list" + ChatColor.GRAY + " - View the battlekit list");
        sender.sendMessage(ChatColor.YELLOW + "/pixffa battlekit create <name>" + ChatColor.GRAY + " - Create a battlekit from your current inventory");
        sender.sendMessage(ChatColor.YELLOW + "/pixffa battlekit save <name>" + ChatColor.GRAY + " - Save your inventory to a battlekit");
        sender.sendMessage(ChatColor.YELLOW + "/pixffa battlekit edit <name>" + ChatColor.GRAY + " - Load a battlekit for editing");
        sender.sendMessage(ChatColor.YELLOW + "/pixffa battlekit cancel" + ChatColor.GRAY + " - Cancel battlekit editing");
        sender.sendMessage(ChatColor.YELLOW + "/pixffa battlekit select <name>" + ChatColor.GRAY + " - Change the active battlekit");
        sender.sendMessage(ChatColor.YELLOW + "/pixffa battlekit build <true|false>" + ChatColor.GRAY + " - Toggle access to the FFA arena");
    }
}
