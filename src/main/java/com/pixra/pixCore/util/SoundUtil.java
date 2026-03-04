package com.pixra.pixCore.util;

import org.bukkit.Bukkit;
import org.bukkit.Sound;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class SoundUtil {

    private SoundUtil() {}

    public static Sound getSoundByName(String name) {
        if (name == null || name.isEmpty()
                || name.equalsIgnoreCase("none")
                || name.equalsIgnoreCase("null")) return null;

        name = name.trim().toUpperCase();

        Sound direct = getSoundSafe(name);
        if (direct != null) return direct;

        String[] fallbacks = resolveFallbacks(name);
        for (String fallback : fallbacks) {
            Sound s = getSoundSafe(fallback);
            if (s != null) return s;
        }

        Bukkit.getLogger().warning("[PixCore] Gagal memuat sound: " + name
                + " (Tidak ada fallback yang cocok di versi server ini)");
        return null;
    }

    public static Sound getSoundSafe(String name) {
        if (name == null || name.isEmpty()) return null;
        name = name.toUpperCase();

        try {
            for (Object constant : Sound.class.getEnumConstants()) {
                if (((Enum<?>) constant).name().equals(name)) return (Sound) constant;
            }
        } catch (Exception ignored) {}

        try {
            Field field = Sound.class.getField(name);
            Object obj  = field.get(null);
            if (obj instanceof Sound) return (Sound) obj;
        } catch (Exception ignored) {}

        try {
            Class<?> nsk  = Class.forName("org.bukkit.NamespacedKey");
            Object   key  = nsk.getMethod("minecraft", String.class).invoke(null, name.toLowerCase());
            Class<?> reg  = Class.forName("org.bukkit.Registry");
            Object   sreg = reg.getField("SOUND").get(null);
            Sound    s    = (Sound) sreg.getClass().getMethod("get", nsk).invoke(sreg, key);
            if (s != null) return s;
        } catch (Exception ignored) {}

        try {
            Method valueOf = Sound.class.getMethod("valueOf", String.class);
            return (Sound) valueOf.invoke(null, name);
        } catch (Exception ignored) {}

        return null;
    }

    private static String[] resolveFallbacks(String name) {
        if (name.contains("FIREWORK") && name.contains("BLAST")) {
            return new String[]{"ENTITY_FIREWORK_ROCKET_BLAST", "ENTITY_FIREWORK_BLAST",
                    "FIREWORK_BLAST", "FIREWORK_LARGE_BLAST", "ENTITY_FIREWORK_ROCKET_LARGE_BLAST"};
        }
        if (name.contains("FIREWORK") && name.contains("LAUNCH")) {
            return new String[]{"ENTITY_FIREWORK_ROCKET_LAUNCH", "ENTITY_FIREWORK_LAUNCH", "FIREWORK_LAUNCH"};
        }
        if (name.contains("FIREWORK") && name.contains("TWINKLE")) {
            return new String[]{"ENTITY_FIREWORK_ROCKET_TWINKLE_FAR", "ENTITY_FIREWORK_TWINKLE_FAR",
                    "FIREWORK_TWINKLE_FAR", "ENTITY_FIREWORK_ROCKET_TWINKLE", "FIREWORK_TWINKLE"};
        }
        if (name.contains("PLING")) {
            return new String[]{"BLOCK_NOTE_BLOCK_PLING", "BLOCK_NOTE_PLING", "NOTE_PLING"};
        }
        if (name.contains("STICK") || name.contains("HAT")) {
            return new String[]{"BLOCK_NOTE_BLOCK_HAT", "BLOCK_NOTE_HAT", "NOTE_STICKS"};
        }
        if (name.contains("ORB_PICKUP")) {
            return new String[]{"ENTITY_EXPERIENCE_ORB_PICKUP", "ORB_PICKUP"};
        }
        if (name.contains("LEVEL_UP") || name.contains("LEVELUP")) {
            return new String[]{"ENTITY_PLAYER_LEVELUP", "LEVEL_UP"};
        }
        if (name.contains("GROWL")) {
            return new String[]{"ENTITY_ENDER_DRAGON_GROWL", "ENTITY_ENDERDRAGON_GROWL", "ENDERDRAGON_GROWL"};
        }
        if (name.contains("CLICK")) {
            return new String[]{"UI_BUTTON_CLICK", "BLOCK_WOODEN_BUTTON_CLICK_ON", "WOOD_CLICK", "CLICK"};
        }
        if (name.contains("WOOL")) {
            return new String[]{"BLOCK_WOOL_BREAK", "DIG_WOOL"};
        }
        if (name.contains("STONE") && (name.contains("BREAK") || name.contains("DIG"))) {
            return new String[]{"BLOCK_STONE_BREAK", "DIG_STONE"};
        }
        return new String[0];
    }
}