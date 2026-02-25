package com.pixra.pixCore.managers;

import com.pixra.pixCore.PixCore;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.List;

public class KillMessageManager {

    private final PixCore plugin;
    private File configFile;
    private FileConfiguration config;

    public KillMessageManager(PixCore plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "killmessage.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            plugin.saveResource("killmessage.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        plugin.getLogger().info("killmessage.yml loaded.");
    }

    public void sendKillMessage(Player killer, Player victim, Object fight) {
        if (!config.getBoolean("enabled", true)) return;

        String kitName = plugin.getKitName(killer);
        if (kitName == null) return;

        List<String> allowedKits = config.getStringList("allowed-kits");
        boolean isAllowed = false;

        if (allowedKits == null || allowedKits.isEmpty()) {
            isAllowed = true;
        } else {
            for (String kit : allowedKits) {
                if (kit.equalsIgnoreCase(kitName)) {
                    isAllowed = true;
                    break;
                }
            }
        }

        if (!isAllowed) return;

        String messageFormat = config.getString("message", "&c&lKILL! <player>");

        String victimColor = plugin.getTeamColorCode(victim, fight);
        String formattedName = victimColor + victim.getName();

        String msg = ChatColor.translateAlternateColorCodes('&', messageFormat)
                .replace("<player>", formattedName);

        String type = config.getString("display-type", "ACTION_BAR").toUpperCase();

        if (type.equals("ACTION_BAR")) {
            sendActionBar(killer, msg);
        } else if (type.equals("TITLE")) {
            plugin.sendTitle(killer, "", msg, 5, 20, 5);
        } else {
            killer.sendMessage(msg);
        }
    }

    private void sendActionBar(Player player, String message) {
        try {
            try {
                Object spigot = player.getClass().getMethod("spigot").invoke(player);
                Class<?> chatMessageTypeClass = Class.forName("net.md_5.bungee.api.ChatMessageType");
                Class<?> textComponentClass = Class.forName("net.md_5.bungee.api.chat.TextComponent");
                Class<?> baseComponentArrayClass = Class.forName("[Lnet.md_5.bungee.api.chat.BaseComponent;");

                Object chatMessageType = Enum.valueOf((Class<Enum>) chatMessageTypeClass, "ACTION_BAR");
                Object textComponents = textComponentClass.getMethod("fromLegacyText", String.class).invoke(null, message);

                spigot.getClass().getMethod("sendMessage", chatMessageTypeClass, baseComponentArrayClass)
                        .invoke(spigot, chatMessageType, textComponents);
                return;
            } catch (Exception ignored) {}

            Class<?> iChatBaseComponentClass = getNMSClass("IChatBaseComponent");
            Class<?> packetPlayOutChatClass = getNMSClass("PacketPlayOutChat");

            if (iChatBaseComponentClass != null && packetPlayOutChatClass != null) {
                Class<?> chatSerializerClass = iChatBaseComponentClass.getDeclaredClasses()[0];
                Object chatComponent = chatSerializerClass.getMethod("a", String.class)
                        .invoke(null, "{\"text\":\"" + message.replace("\"", "\\\"") + "\"}");

                Object packet;
                try {
                    packet = packetPlayOutChatClass.getConstructor(iChatBaseComponentClass, byte.class)
                            .newInstance(chatComponent, (byte) 2);
                } catch (NoSuchMethodException e) {
                    packet = packetPlayOutChatClass.getConstructor(iChatBaseComponentClass).newInstance(chatComponent);
                }
                sendPacket(player, packet);
            }
        } catch (Exception e) {
            plugin.sendTitle(player, "", message, 5, 20, 5);
        }
    }

    private void sendPacket(Player player, Object packet) {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object playerConnection = handle.getClass().getField("playerConnection").get(handle);
            playerConnection.getClass().getMethod("sendPacket", getNMSClass("Packet")).invoke(playerConnection, packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Class<?> getNMSClass(String name) {
        try {
            String version = org.bukkit.Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            return Class.forName("net.minecraft.server." + version + "." + name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}