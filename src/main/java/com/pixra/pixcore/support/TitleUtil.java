package com.pixra.pixcore.support;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Constructor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TitleUtil {

    private TitleUtil() {}

    public static void sendGlowingTitle(Plugin plugin, Player player,
                                        String formattedTitle, String subtitle,
                                        int fadeIn, int stay, int fadeOut) {
        if (formattedTitle == null) formattedTitle = "";
        if (subtitle == null) subtitle = "";

        String plainText = formattedTitle.replaceAll("§[0-9a-fA-Fk-oK-OrR]", "");
        if (plainText.isEmpty()) {
            sendTitle(player, formattedTitle, subtitle, fadeIn, stay, fadeOut);
            return;
        }

        Matcher cm = Pattern.compile("§([0-9a-f])").matcher(formattedTitle);
        final String baseCode = cm.find() ? ("§" + cm.group(1) + "§l") : "§f§l";

        final int len = plainText.length();
        final int frameTicks = 2;
        final int totalFrames = stay / frameTicks;
        final String fSubtitle = subtitle;
        final String fPlain = plainText;
        final int fFadeIn = fadeIn;
        final int fFadeOut = fadeOut;

        final String startWorld = player.getWorld().getName();

        new BukkitRunnable() {
            int pos   = 0;
            int count = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !player.getWorld().getName().equals(startWorld)) {
                    try { player.resetTitle(); } catch (Exception ignored) {}
                    cancel();
                    return;
                }
                boolean isFirst = (count == 0);
                boolean isLast  = (count >= totalFrames);
                String frame = buildGlowFrame(fPlain, pos, baseCode, "§f§l");
                sendTitle(player, frame, fSubtitle,
                        isFirst ? fFadeIn : 0,
                        5,
                        isLast  ? fFadeOut : 0);
                if (isLast) { cancel(); return; }
                count++;
                pos = (pos + 1) % (len + 1);
            }
        }.runTaskTimer(plugin, 0L, (long) frameTicks);
    }

    private static String buildGlowFrame(String text, int center, String base, String glow) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            sb.append(Math.abs(i - center) <= 1 ? glow : base);
            sb.append(text.charAt(i));
        }
        return sb.toString();
    }

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
