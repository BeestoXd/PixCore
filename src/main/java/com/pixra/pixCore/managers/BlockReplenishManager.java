package com.pixra.pixCore.managers;

import com.pixra.pixCore.PixCore;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BlockReplenishManager implements Listener {

    private final PixCore plugin;
    private final Map<UUID, Map<String, Integer>> playerInventorySnapshots = new HashMap<>();
    private boolean isLegacy = false;

    public BlockReplenishManager(PixCore plugin) {
        this.plugin = plugin;
        try {
            BlockPlaceEvent.class.getMethod("getHand");
            isLegacy = false;
        } catch (NoSuchMethodException e) {
            isLegacy = true;
        }
    }

    public void scanPlayerInventory(Player player) {
        if (!plugin.getConfig().getBoolean("settings.block-replenish.enabled", false)) return;

        String kitName = plugin.getKitName(player);
        if (kitName == null) return;

        List<String> allowedKits = plugin.getConfig().getStringList("settings.block-replenish.kits");
        boolean isAllowed = false;
        for (String kit : allowedKits) {
            if (kit.equalsIgnoreCase(kitName)) {
                isAllowed = true;
                break;
            }
        }
        if (!isAllowed) return;

        Map<String, Integer> snapshot = new HashMap<>();

        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                if (item.getType() == Material.TNT) continue;

                if (item.getType().isBlock()) {
                    String key = getItemKey(item);
                    int currentStored = snapshot.getOrDefault(key, 0);
                    if (item.getAmount() > currentStored) {
                        snapshot.put(key, item.getAmount());
                    }
                }
            }
        }

        if (!snapshot.isEmpty()) {
            playerInventorySnapshots.put(player.getUniqueId(), snapshot);
        }
    }

    public void clearPlayerData(Player player) {
        playerInventorySnapshots.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!playerInventorySnapshots.containsKey(uuid)) return;

        ItemStack itemInHand = event.getItemInHand();

        if (itemInHand != null && itemInHand.getType() == Material.TNT) return;

        String key = getItemKey(itemInHand);

        Map<String, Integer> snapshot = playerInventorySnapshots.get(uuid);

        if (snapshot.containsKey(key)) {
            if (itemInHand.getAmount() <= 1) {
                int originalAmount = snapshot.get(key);

                ItemStack newItem = itemInHand.clone();
                newItem.setAmount(originalAmount);

                if (isLegacy) {
                    player.getInventory().setItemInHand(newItem);
                } else {
                    handleNewerHandCheck(event, player, newItem);
                }

                player.updateInventory();
            }
        }
    }

    private void handleNewerHandCheck(BlockPlaceEvent event, Player player, ItemStack newItem) {
        try {
            Method getHand = BlockPlaceEvent.class.getMethod("getHand");
            Object hand = getHand.invoke(event);

            if (hand.toString().equals("HAND")) {
                try {
                    player.getInventory().getClass().getMethod("setItemInMainHand", ItemStack.class).invoke(player.getInventory(), newItem);
                } catch (Exception e) {
                    player.getInventory().setItemInHand(newItem);
                }
            } else {
                try {
                    player.getInventory().getClass().getMethod("setItemInOffHand", ItemStack.class).invoke(player.getInventory(), newItem);
                } catch (Exception e) {
                }
            }
        } catch (Exception e) {
            player.getInventory().setItemInHand(newItem);
        }
    }

    @SuppressWarnings("deprecation")
    private String getItemKey(ItemStack item) {
        return item.getType().name() + ":" + item.getDurability();
    }
}