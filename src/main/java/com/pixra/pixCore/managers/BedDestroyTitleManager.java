package com.pixra.pixCore.managers;

import com.pixra.pixCore.PixCore;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;

public class BedDestroyTitleManager {

    private final PixCore plugin;

    public BedDestroyTitleManager(PixCore plugin) {
        this.plugin = plugin;
    }

    public void sendBedDestroyTitle(Player victim) {
        if (plugin.getActiveCountdowns().containsKey(victim.getUniqueId())) {
            return;
        }

        String title = plugin.getConfig().getString("messages.bed-destroyed-title", "&c&lBED DESTROYED!");
        String subtitle = plugin.getConfig().getString("messages.bed-destroyed-subtitle", "&eYou will no longer respawn!");

        title = ChatColor.translateAlternateColorCodes('&', title);
        subtitle = ChatColor.translateAlternateColorCodes('&', subtitle);

        plugin.sendTitle(victim, title, subtitle, 10, 70, 20);

        Sound soundToPlay = getSoundSafe("ENTITY_ENDER_DRAGON_GROWL");
        if (soundToPlay == null) {
            soundToPlay = getSoundSafe("ENDERDRAGON_GROWL");
        }

        if (soundToPlay != null) {
            try {
                victim.playSound(victim.getLocation(), soundToPlay, 1.0f, 1.0f);
            } catch (Exception ignored) {
            }
        }
    }

    private Sound getSoundSafe(String soundName) {
        try {
            Field field = Sound.class.getField(soundName.toUpperCase());
            return (Sound) field.get(null);
        } catch (Exception | Error e) {
            return null;
        }
    }
}