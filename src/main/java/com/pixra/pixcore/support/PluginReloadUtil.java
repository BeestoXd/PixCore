package com.pixra.pixcore.support;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PluginReloadUtil {

    public static boolean reloadPlugin(String pluginName, StringBuilder error) {
        PluginManager pm = Bukkit.getPluginManager();
        Plugin target = pm.getPlugin(pluginName);

        if (target == null) {
            error.append("Plugin '").append(pluginName).append("' tidak ditemukan.");
            return false;
        }

        File pluginFile = getPluginFile(target);
        if (pluginFile == null || !pluginFile.exists()) {
            error.append("File JAR untuk '").append(pluginName).append("' tidak dapat ditemukan.");
            return false;
        }

        Bukkit.getScheduler().cancelTasks(target);

        HandlerList.unregisterAll(target);

        try {
            pm.disablePlugin(target);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[PixCore] Warning during disablePlugin: " + e.getMessage());
        }

        unregisterCommands(pm, target);

        ClassLoader cl = target.getClass().getClassLoader();
        cleanupJavaPluginLoader(cl, target.getName());

        removeFromPluginManager(pm, target);

        if (cl instanceof URLClassLoader) {
            try {
                ((URLClassLoader) cl).close();
            } catch (Exception ignored) {}
        }

        try {
            Plugin fresh = pm.loadPlugin(pluginFile);
            if (fresh == null) {
                error.append("loadPlugin() mengembalikan null untuk '").append(pluginName).append("'.");
                return false;
            }
            pm.enablePlugin(fresh);
            return true;
        } catch (Exception e) {
            error.append(e.getClass().getSimpleName()).append(": ").append(e.getMessage());
            Bukkit.getLogger().severe("[PixCore] Error reloading " + pluginName + ": " + e.getMessage());
            return false;
        }
    }

    private static File getPluginFile(Plugin plugin) {
        try {
            Method m = JavaPlugin.class.getDeclaredMethod("getFile");
            m.setAccessible(true);
            return (File) m.invoke(plugin);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void cleanupJavaPluginLoader(ClassLoader cl, String pluginName) {
        try {

            Field loaderField = findField(cl.getClass(), "loader");
            loaderField.setAccessible(true);
            Object jpl = loaderField.get(cl);
            if (jpl == null) return;

            try {
                Field f = findField(jpl.getClass(), "loaders");
                f.setAccessible(true);
                Object obj = f.get(jpl);
                if (obj instanceof List)      ((List) obj).remove(cl);
                else if (obj instanceof Map)  ((Map) obj).values().remove(cl);
            } catch (Exception ignored) {}

            try {
                Field f = findField(jpl.getClass(), "classes");
                f.setAccessible(true);
                Map<String, Class<?>> map = (Map<String, Class<?>>) f.get(jpl);
                map.entrySet().removeIf(e -> e.getValue().getClassLoader() == cl);
            } catch (Exception ignored) {}

        } catch (Exception ignored) {}

        try {
            for (Field f : cl.getClass().getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                Object obj = f.get(null);
                if (obj instanceof Map) {
                    Map<Object, Object> m = (Map<Object, Object>) obj;
                    m.entrySet().removeIf(e ->
                            pluginName.equalsIgnoreCase(String.valueOf(e.getKey()))
                            || cl.equals(e.getValue()));
                }
            }
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    private static void removeFromPluginManager(PluginManager pm, Plugin plugin) {
        try {
            Field pluginsField = findField(pm.getClass(), "plugins");
            pluginsField.setAccessible(true);
            List<Plugin> plugins = (List<Plugin>) pluginsField.get(pm);
            plugins.remove(plugin);
        } catch (Exception ignored) {}

        try {
            Field lookupField = findField(pm.getClass(), "lookupNames");
            lookupField.setAccessible(true);
            Map<String, Plugin> lookupNames = (Map<String, Plugin>) lookupField.get(pm);
            lookupNames.values().removeIf(p -> p.getName().equalsIgnoreCase(plugin.getName()));
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    private static void unregisterCommands(PluginManager pm, Plugin plugin) {
        try {

            Field cmField = findField(Bukkit.getServer().getClass(), "commandMap");
            cmField.setAccessible(true);
            Object commandMap = cmField.get(Bukkit.getServer());

            Field knownField = findField(commandMap.getClass(), "knownCommands");
            knownField.setAccessible(true);
            Map<String, Command> known = (Map<String, Command>) knownField.get(commandMap);

            List<String> remove = new ArrayList<>();
            for (Map.Entry<String, Command> entry : known.entrySet()) {
                if (entry.getValue() instanceof org.bukkit.command.PluginCommand) {
                    org.bukkit.command.PluginCommand pc =
                            (org.bukkit.command.PluginCommand) entry.getValue();
                    if (pc.getPlugin() != null &&
                            pc.getPlugin().getName().equalsIgnoreCase(plugin.getName())) {
                        remove.add(entry.getKey());
                    }
                }
            }
            remove.forEach(known::remove);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[PixCore] Could not unregister commands for "
                    + plugin.getName() + ": " + e.getMessage());
        }
    }

    private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name + " not found in " + clazz.getName());
    }
}
