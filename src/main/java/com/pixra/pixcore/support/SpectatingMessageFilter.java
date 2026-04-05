package com.pixra.pixcore.support;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class SpectatingMessageFilter extends ChannelDuplexHandler {

    private static final String HANDLER = "pixcore_spectate_filter";

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (!isSpectatingPacket(msg)) {
            super.write(ctx, msg, promise);
        }
    }

    private static boolean isSpectatingPacket(Object pkt) {

        String cls = pkt.getClass().getSimpleName();
        if (!cls.contains("Chat")) return false;

        try {
            String str = ChatColor.stripColor(pkt.toString());
            if (str.contains("is now spectating") || str.contains("stopped spectating")) return true;
        } catch (Exception ignored) {}

        for (Field f : allFields(pkt.getClass())) {
            try {
                f.setAccessible(true);
                Object val = f.get(pkt);
                if (val == null || val == pkt) continue;
                String s = ChatColor.stripColor(val.toString());
                if (s.contains("is now spectating") || s.contains("stopped spectating")) return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    public static void inject(Player player) {
        Channel ch = getChannel(player);
        if (ch == null || ch.pipeline().get(HANDLER) != null) return;
        ch.pipeline().addBefore("packet_handler", HANDLER, new SpectatingMessageFilter());
    }

    private static Channel getChannel(Player player) {
        try {
            Object ep   = player.getClass().getMethod("getHandle").invoke(player);
            Object conn = findByNames(ep,   "playerConnection", "b", "c", "connection", "f", "e");
            Object nm   = findByNames(conn, "networkManager",   "a", "b", "h", "m", "network");

            for (Field f : allFields(nm.getClass())) {
                if (Channel.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    Channel ch = (Channel) f.get(nm);
                    if (ch != null) return ch;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static Object findByNames(Object obj, String... names) throws Exception {
        Class<?> c = obj.getClass();
        while (c != null && c != Object.class) {
            for (String name : names) {
                try {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    return f.get(obj);
                } catch (NoSuchFieldException ignored) {}
            }
            c = c.getSuperclass();
        }
        throw new Exception("Field not found in " + obj.getClass().getSimpleName());
    }

    private static List<Field> allFields(Class<?> c) {
        List<Field> list = new ArrayList<>();
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) list.add(f);
            c = c.getSuperclass();
        }
        return list;
    }
}
