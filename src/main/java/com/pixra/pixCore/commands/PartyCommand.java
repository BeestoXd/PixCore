package com.pixra.pixCore.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class PartyCommand implements CommandExecutor, Listener {

    /**
     * Intercepts /party at the LOWEST priority (first to fire) so it always
     * takes precedence over any other plugin that may register /party.
     * Redirects to /strikepractice:party <args>.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        String lower = message.toLowerCase();

        if (!lower.equals("/party") && !lower.startsWith("/party ")) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        String spCommand;
        if (lower.equals("/party")) {
            spCommand = "strikepractice:party";
        } else {
            spCommand = "strikepractice:party " + message.substring("/party ".length());
        }

        Bukkit.dispatchCommand(player, spCommand);
    }

    /**
     * Fallback executor for when Bukkit routes /party directly to this plugin
     * (e.g. if StrikePractice is not loaded and no event interception occurred).
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use /party.");
            return true;
        }

        Player player = (Player) sender;
        String spCommand = args.length == 0
                ? "strikepractice:party"
                : "strikepractice:party " + String.join(" ", args);

        Bukkit.dispatchCommand(player, spCommand);
        return true;
    }
}
