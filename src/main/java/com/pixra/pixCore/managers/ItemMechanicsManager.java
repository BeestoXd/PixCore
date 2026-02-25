package com.pixra.pixCore.managers;

import com.pixra.pixCore.PixCore;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.List;

public class ItemMechanicsManager implements Listener {

    private final PixCore plugin;

    public ItemMechanicsManager(PixCore plugin) {
        this.plugin = plugin;
    }

    private boolean makeItemUnbreakable(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        boolean metaChanged = false;

        try {
            Method isUnbreakable = ItemMeta.class.getMethod("isUnbreakable");
            boolean unb = (boolean) isUnbreakable.invoke(meta);
            if (!unb) {
                Method setUnbreakable = ItemMeta.class.getMethod("setUnbreakable", boolean.class);
                setUnbreakable.invoke(meta, true);
                metaChanged = true;
            }
        } catch (NoSuchMethodException e) {
            try {
                Method spigotMethod = ItemMeta.class.getMethod("spigot");
                Object spigotObj = spigotMethod.invoke(meta);

                Class<?> spigotInterface = Class.forName("org.bukkit.inventory.meta.ItemMeta$Spigot");

                Method isUnbreakable = spigotInterface.getMethod("isUnbreakable");
                boolean unb = (boolean) isUnbreakable.invoke(spigotObj);

                if (!unb) {
                    Method setUnbreakable = spigotInterface.getMethod("setUnbreakable", boolean.class);
                    setUnbreakable.invoke(spigotObj, true);
                    metaChanged = true;
                }
            } catch (Exception ignored) {
            }
        } catch (Exception ignored) {}

        if (metaChanged) {
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
        }

        return metaChanged;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        if (!plugin.getConfig().getBoolean("settings.item-mechanics.unbreakable-tools", true)) {
            return;
        }

        ItemStack item = event.getPlayer().getInventory().getItem(event.getNewSlot());
        if (item != null) {
            String type = item.getType().name();
            if (type.endsWith("_SWORD") || type.endsWith("_AXE") || type.endsWith("_PICKAXE") ||
                    type.endsWith("_SPADE") || type.endsWith("_SHOVEL") || type.equals("SHEARS")) {

                if (makeItemUnbreakable(item)) {
                    event.getPlayer().updateInventory();
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (!plugin.getConfig().getBoolean("settings.item-mechanics.unbreakable-tools", true)) {
            return;
        }

        String type = event.getItem().getType().name();
        if (type.endsWith("_SWORD") ||
                type.endsWith("_AXE") ||
                type.endsWith("_PICKAXE") ||
                type.endsWith("_SPADE") ||
                type.endsWith("_SHOVEL") ||
                type.equals("SHEARS")) {

            event.setCancelled(true);

            ItemStack item = event.getItem();

            item.setDurability((short) 0);

            makeItemUnbreakable(item);

            event.getPlayer().updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShearsDrop(PlayerDropItemEvent event) {
        if (!plugin.getConfig().getBoolean("settings.item-mechanics.prevent-shears-drop", true)) {
            return;
        }

        if (event.getItemDrop().getItemStack().getType() == Material.SHEARS) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        if (!plugin.getConfig().getBoolean("settings.item-mechanics.prevent-armor-removal.enabled", true)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        if (event.getSlotType() == InventoryType.SlotType.ARMOR) {
            String kitName = plugin.getKitName(player);
            if (kitName != null) {
                List<String> lockedKits = plugin.getConfig().getStringList("settings.item-mechanics.prevent-armor-removal.kits");
                for (String k : lockedKits) {
                    if (k.equalsIgnoreCase(kitName)) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }

        if (event.getAction() == org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY && event.getCurrentItem() != null) {
            String type = event.getCurrentItem().getType().name();
            if (type.endsWith("_HELMET") || type.endsWith("_CHESTPLATE") || type.endsWith("_LEGGINGS") || type.endsWith("_BOOTS")) {
                String kitName = plugin.getKitName(player);
                if (kitName != null) {
                    List<String> lockedKits = plugin.getConfig().getStringList("settings.item-mechanics.prevent-armor-removal.kits");
                    for (String k : lockedKits) {
                        if (k.equalsIgnoreCase(kitName)) {
                            event.setCancelled(true);
                            return;
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorDrop(PlayerDropItemEvent event) {
        if (!plugin.getConfig().getBoolean("settings.item-mechanics.prevent-armor-removal.enabled", true)) {
            return;
        }

        String type = event.getItemDrop().getItemStack().getType().name();
        if (type.endsWith("_HELMET") || type.endsWith("_CHESTPLATE") || type.endsWith("_LEGGINGS") || type.endsWith("_BOOTS")) {
            Player player = event.getPlayer();
            String kitName = plugin.getKitName(player);
            if (kitName != null) {
                List<String> lockedKits = plugin.getConfig().getStringList("settings.item-mechanics.prevent-armor-removal.kits");
                for (String k : lockedKits) {
                    if (k.equalsIgnoreCase(kitName)) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }
}