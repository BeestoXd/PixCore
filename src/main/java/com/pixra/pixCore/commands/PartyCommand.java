package com.pixra.pixCore.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PartyCommand implements CommandExecutor {

    public PartyCommand(org.bukkit.plugin.Plugin plugin) {
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use /party.");
            return true;
        }

        sender.sendMessage(ChatColor.RED + "StrikePractice is not loaded.");
        return true;
    }
}
