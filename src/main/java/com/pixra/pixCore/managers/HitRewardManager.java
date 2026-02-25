package com.pixra.pixCore.managers;

import com.pixra.pixCore.PixCore;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class HitRewardManager implements Listener {

    private final PixCore plugin;

    public HitRewardManager(PixCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerHit(EntityDamageByEntityEvent event) {
        if (!plugin.getConfig().getBoolean("settings.hit-reward.enabled", false)) {
            return;
        }

        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof Player)) {
            return;
        }

        Player attacker = (Player) event.getDamager();

        String kitName = plugin.getKitName(attacker);
        if (kitName == null) return;

        ConfigurationSection kitsSection = plugin.getConfig().getConfigurationSection("settings.hit-reward.kits");
        if (kitsSection == null) return;

        for (String key : kitsSection.getKeys(false)) {
            if (key.equalsIgnoreCase(kitName)) {
                ConfigurationSection itemSection = kitsSection.getConfigurationSection(key);
                if (itemSection != null) {
                    giveCustomReward(attacker, itemSection);
                }
                break;
            }
        }
    }

    private void giveCustomReward(Player player, ConfigurationSection section) {
        String materialName = section.getString("material", "TNT");
        Material mat = Material.getMaterial(materialName);
        if (mat == null) {
            try {
                mat = Material.valueOf("TNT");
            } catch (Exception ignored) {}
        }

        if (mat == null) return;

        int amount = section.getInt("amount", 1);
        int maxAmount = section.getInt("max-amount", -1);

        ItemStack item = new ItemStack(mat, amount);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            if (section.contains("name")) {
                String displayName = section.getString("name");
                if (displayName != null) {
                    meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
                }
            }

            if (section.contains("lore")) {
                List<String> lore = section.getStringList("lore");
                List<String> colorLore = new ArrayList<>();
                for (String line : lore) {
                    colorLore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                meta.setLore(colorLore);
            }

            if (section.contains("enchants")) {
                ConfigurationSection enchantSection = section.getConfigurationSection("enchants");
                if (enchantSection != null) {
                    for (String enchantName : enchantSection.getKeys(false)) {
                        int level = enchantSection.getInt(enchantName);
                        Enchantment enchantment = getEnchantmentByName(enchantName);
                        if (enchantment != null) {
                            meta.addEnchant(enchantment, level, true);
                        }
                    }
                }
            }
            item.setItemMeta(meta);
        }

        if (maxAmount > 0) {
            int currentCount = 0;

            for (ItemStack invItem : player.getInventory().getContents()) {
                if (invItem != null && invItem.isSimilar(item)) {
                    currentCount += invItem.getAmount();
                }
            }

            if (currentCount >= maxAmount) {
                return;
            }

            int spaceLeft = maxAmount - currentCount;
            if (spaceLeft < amount) {
                item.setAmount(spaceLeft);
            }
        }

        player.getInventory().addItem(item);
    }

    private Enchantment getEnchantmentByName(String name) {
        Enchantment enchant = Enchantment.getByName(name.toUpperCase());
        if (enchant == null) {
            if (name.equalsIgnoreCase("SHARPNESS")) return Enchantment.getByName("DAMAGE_ALL");
            if (name.equalsIgnoreCase("PROTECTION")) return Enchantment.getByName("PROTECTION_ENVIRONMENTAL");
            if (name.equalsIgnoreCase("UNBREAKING")) return Enchantment.getByName("DURABILITY");
        }
        return enchant;
    }
}