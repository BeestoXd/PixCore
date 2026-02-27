package com.pixra.pixCore.commands;

import com.pixra.pixCore.PixCore;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LeaderboardCommand implements CommandExecutor {

    private final PixCore plugin;

    public LeaderboardCommand(PixCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can open the Leaderboard GUI.");
            return true;
        }

        Player p = (Player) sender;
        if (plugin.leaderboardGUIManager != null) {
            plugin.leaderboardGUIManager.openMainMenu(p);
        } else {
            p.sendMessage(ChatColor.RED + "Leaderboard GUI Manager is not initialized.");
        }
        return true;
    }
}