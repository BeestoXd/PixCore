package com.pixra.pixCore.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;

public final class TitleUtil {

    private TitleUtil() {}

    public static void sendTitle(Player player, String title, String subtitle,
                                 int fadeIn, int stay, int fadeOut) {
        if (title    == null) title    = "";
        if (subtitle == null) subtitle = "";
        try {
            player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
        } catch (NoSuchMethodError e) {
            try {
                sendTitleNMS(player, title, subtitle, fadeIn, stay, fadeOut);
            } catch (Exception ignored) {}
        }
    }

    private static void sendTitleNMS(Player player, String title, String subtitle,
                                     int fadeIn, int stay, int fadeOut) throws Exception {
        Class<?> packetClass   = getNMSClass("PacketPlayOutTitle");
        Class<?> chatClass     = getNMSClass("IChatBaseComponent");
        Class<?> enumAction    = packetClass.getDeclaredClasses()[0];

        Object chatTitle    = chatClass.getDeclaredClasses()[0]
                .getMethod("a", String.class).invoke(null, "{\"text\":\"" + title + "\"}");
        Object chatSubtitle = chatClass.getDeclaredClasses()[0]
                .getMethod("a", String.class).invoke(null, "{\"text\":\"" + subtitle + "\"}");

        Constructor<?> ctor = packetClass.getConstructor(enumAction, chatClass, int.class, int.class, int.class);

        sendPacket(player, ctor.newInstance(enumAction.getField("TIMES").get(null),   null,         fadeIn, stay, fadeOut));
        sendPacket(player, ctor.newInstance(enumAction.getField("TITLE").get(null),   chatTitle,    fadeIn, stay, fadeOut));
        sendPacket(player, ctor.newInstance(enumAction.getField("SUBTITLE").get(null), chatSubtitle, fadeIn, stay, fadeOut));
    }

    private static void sendPacket(Player player, Object packet) {
        try {
            Object handle     = player.getClass().getMethod("getHandle").invoke(player);
            Object connection = handle.getClass().getField("playerConnection").get(handle);
            connection.getClass()
                    .getMethod("sendPacket", getNMSClass("Packet"))
                    .invoke(connection, packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Class<?> getNMSClass(String name) {
        String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
        try {
            return Class.forName("net.minecraft.server." + version + "." + name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}