package com.pixra.pixCore.managers;

import com.pixra.pixCore.PixCore;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.io.File;
import java.util.List;

public class HitActionBarManager implements Listener {

    private final PixCore plugin;
    private File configFile;
    private FileConfiguration config;

    public HitActionBarManager(PixCore plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "hitactionbar.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            plugin.saveResource("hitactionbar.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        plugin.getLogger().info("hitactionbar.yml loaded.");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerHit(EntityDamageByEntityEvent event) {
        if (!config.getBoolean("enabled", true)) return;

        Player damager = null;
        if (event.getDamager() instanceof Player) {
            damager = (Player) event.getDamager();
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile) {
            org.bukkit.entity.Projectile proj = (org.bukkit.entity.Projectile) event.getDamager();
            if (proj.getShooter() instanceof Player) {
                damager = (Player) proj.getShooter();
            }
        }

        if (damager == null) return;
        if (!(event.getEntity() instanceof Player)) return;

        Player victim = (Player) event.getEntity();

        if (damager.getUniqueId().equals(victim.getUniqueId())) return;

        if (!plugin.isHooked()) return;

        try {
            if (!plugin.isInFight(damager) || !plugin.isInFight(victim)) return;

            List<String> allowedKits = config.getStringList("allowed-kits");
            if (allowedKits != null && !allowedKits.isEmpty()) {
                String kitName = plugin.getKitName(damager);
                boolean allowed = false;
                if (kitName != null) {
                    for (String kit : allowedKits) {
                        if (kit.equalsIgnoreCase(kitName)) {
                            allowed = true;
                            break;
                        }
                    }
                }
                if (!allowed) return;
            }

            double finalHealth = victim.getHealth() - event.getFinalDamage();
            if (finalHealth < 0) finalHealth = 0;

            double maxHealth = victim.getMaxHealth();
            double absorption = 0;
            try {
                absorption = victim.getAbsorptionAmount();
            } catch (NoSuchMethodError e) {
                // Ignore for older versions
            }

            String heartSymbol = config.getString("heart-symbol", "❤");
            String fullColor = config.getString("full-heart-color", "&c");
            String halfColor = config.getString("half-heart-color", "&d");
            String emptyColor = config.getString("empty-heart-color", "&0");
            String absorptionColor = config.getString("absorption-heart-color", "&e");

            int totalHearts = (int) Math.ceil(maxHealth / 2.0);
            int healthPoints = (int) Math.ceil(finalHealth);
            int absorptionPoints = (int) Math.ceil(absorption);

            StringBuilder healthBar = new StringBuilder();

            int fullHeartsAmount = healthPoints / 2;
            int halfHeartAmount = healthPoints % 2;
            int emptyHeartsAmount = totalHearts - fullHeartsAmount - halfHeartAmount;
            if (emptyHeartsAmount < 0) emptyHeartsAmount = 0;

            if (fullHeartsAmount > 0) {
                healthBar.append(fullColor);
                for (int i = 0; i < fullHeartsAmount; i++) healthBar.append(heartSymbol);
            }
            if (halfHeartAmount > 0) {
                healthBar.append(halfColor).append(heartSymbol);
            }
            if (emptyHeartsAmount > 0) {
                healthBar.append(emptyColor);
                for (int i = 0; i < emptyHeartsAmount; i++) healthBar.append(heartSymbol);
            }

            int absFull = absorptionPoints / 2;
            int absHalf = absorptionPoints % 2;
            if (absFull > 0 || absHalf > 0) {
                healthBar.append(absorptionColor);
                for (int i = 0; i < absFull + absHalf; i++) healthBar.append(heartSymbol);
            }

            String format = config.getString("message", "<player> &r<health_bar>");

            // --- BAGIAN YANG DIUBAH ---
            // Mengambil warna kustom dari konfigurasi (default &e / kuning jika tidak diatur)
            String nameColor = config.getString("player-name-color", "&e");
            String victimName = nameColor + victim.getName();
            // --------------------------

            String msg = format.replace("<player>", victimName)
                    .replace("<health_bar>", healthBar.toString())
                    .replace("<health>", String.format("%.1f", finalHealth / 2.0));

            msg = ChatColor.translateAlternateColorCodes('&', msg);

            sendActionBar(damager, msg);

        } catch (Exception e) {
            e.printStackTrace();
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
        } catch (Exception e) {}
    }

    private void sendPacket(Player player, Object packet) {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object playerConnection = handle.getClass().getField("playerConnection").get(handle);
            playerConnection.getClass().getMethod("sendPacket", getNMSClass("Packet")).invoke(playerConnection, packet);
        } catch (Exception e) {}
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