package com.pixra.pixCore.managers;

import com.pixra.pixCore.PixCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LeaderboardGUIManager implements Listener {

    private final PixCore plugin;
    private File guiFile;
    private FileConfiguration guiConfig;

    private final String MAIN_TITLE = ChatColor.DARK_GRAY + "Leaderboard Winstreak";
    private final String PERIOD_TITLE_PREFIX = ChatColor.DARK_GRAY + "Top Winstreak: ";

    private final Map<UUID, String> activeMenus = new HashMap<>();

    public LeaderboardGUIManager(PixCore plugin) {
        this.plugin = plugin;
        loadConfig();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startUpdaterTask();
    }

    public void loadConfig() {
        guiFile = new File(plugin.getDataFolder(), "leaderboard_gui.yml");
        if (!guiFile.exists()) {
            try {
                guiFile.getParentFile().mkdirs();
                guiFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        guiConfig = YamlConfiguration.loadConfiguration(guiFile);
    }

    public void saveConfig() {
        try {
            guiConfig.save(guiFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setGuiItem(String kitName, int slot, ItemStack item) {
        kitName = ChatColor.stripColor(kitName).toLowerCase();
        String path = "items." + kitName;
        guiConfig.set(path + ".slot", slot);
        guiConfig.set(path + ".material", item.getType().name());
        guiConfig.set(path + ".data", item.getDurability());
        saveConfig();
    }

    public void removeGuiItem(String kitName) {
        kitName = ChatColor.stripColor(kitName).toLowerCase();
        guiConfig.set("items." + kitName, null);
        saveConfig();
    }

    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 36, MAIN_TITLE);

        inv.setItem(11, createItem(Material.NAME_TAG, 0, "&b&lDAILY", "&7Click to view Daily Top 10"));
        inv.setItem(13, createItem(Material.NAME_TAG, 0, "&a&lWEEKLY", "&7Click to view Weekly Top 10"));
        inv.setItem(15, createItem(Material.NAME_TAG, 0, "&6&lMONTHLY", "&7Click to view Monthly Top 10"));

        inv.setItem(31, createItem(Material.NETHER_STAR, 0, "&c&lCLOSE", "&7Close the menu"));

        player.openInventory(inv);
    }

    public void openPeriodMenu(Player player, String periodRaw) {
        String title = PERIOD_TITLE_PREFIX + periodRaw.toUpperCase();
        Inventory inv = Bukkit.createInventory(null, 54, title);

        updatePeriodMenu(inv, periodRaw);

        player.openInventory(inv);

        new BukkitRunnable() {
            @Override
            public void run() {
                activeMenus.put(player.getUniqueId(), periodRaw);
            }
        }.runTaskLater(plugin, 1L);
    }

    private void updatePeriodMenu(Inventory inv, String periodRaw) {
        String period = periodRaw.toLowerCase();

        ConfigurationSection itemsSection = guiConfig.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String kitName : itemsSection.getKeys(false)) {
                ConfigurationSection kitSec = itemsSection.getConfigurationSection(kitName);
                int slot = kitSec.getInt("slot");
                String matName = kitSec.getString("material");
                short data = (short) kitSec.getInt("data");

                Material mat = Material.getMaterial(matName);
                if (mat == null) mat = Material.STONE;

                ItemStack item = new ItemStack(mat, 1, data);
                ItemMeta meta = item.getItemMeta();

                String displayKitName = kitName.toUpperCase();
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&e&l" + displayKitName));

                List<String> lore = new ArrayList<>();
                lore.add("");
                lore.add(ChatColor.translateAlternateColorCodes('&', "&7Top 10 " + periodRaw + " Winstreak:"));

                List<Map.Entry<String, Integer>> top10 = plugin.leaderboardManager.getTop(period, kitName, 10);

                for (int i = 0; i < 10; i++) {
                    int rank = i + 1;
                    String color = rank == 1 ? "&a" : (rank == 2 ? "&e" : (rank == 3 ? "&6" : "&f"));
                    if (i < top10.size()) {
                        Map.Entry<String, Integer> entry = top10.get(i);
                        lore.add(ChatColor.translateAlternateColorCodes('&', color + rank + ". &f" + entry.getKey() + " &7- &d" + entry.getValue() + " Win"));
                    } else {
                        lore.add(ChatColor.translateAlternateColorCodes('&', color + rank + ". &7&o---"));
                    }
                }

                lore.add("");
                String countdown = "";
                if (period.equals("daily")) countdown = plugin.leaderboardManager.getDailyCountdown();
                else if (period.equals("weekly")) countdown = plugin.leaderboardManager.getWeeklyCountdown();
                else if (period.equals("monthly")) countdown = plugin.leaderboardManager.getMonthlyCountdown();

                lore.add(ChatColor.translateAlternateColorCodes('&', "&7Reset in: &c" + countdown));

                meta.setLore(lore);
                item.setItemMeta(meta);

                if (slot >= 0 && slot < 54) {
                    inv.setItem(slot, item);
                }
            }
        }

        inv.setItem(49, createItem(Material.ARROW, 0, "&c&lBACK", "&7Return to Main Menu"));
    }

    private ItemStack createItem(Material mat, int data, String name, String loreLine) {
        ItemStack item = new ItemStack(mat, 1, (short) data);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.translateAlternateColorCodes('&', loreLine));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void startUpdaterTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (activeMenus.isEmpty()) return;

                List<UUID> toRemove = new ArrayList<>();
                for (Map.Entry<UUID, String> entry : activeMenus.entrySet()) {
                    Player player = Bukkit.getPlayer(entry.getKey());

                    if (player == null || !player.isOnline()) {
                        toRemove.add(entry.getKey());
                        continue;
                    }

                    Inventory topInv;
                    try {
                        Object view = player.getClass().getMethod("getOpenInventory").invoke(player);
                        topInv = (Inventory) view.getClass().getMethod("getTopInventory").invoke(view);
                    } catch (Exception e) {
                        toRemove.add(entry.getKey());
                        continue;
                    }

                    if (topInv == null || topInv.getSize() != 54) {
                        toRemove.add(entry.getKey());
                        continue;
                    }

                    updatePeriodMenu(topInv, entry.getValue());
                }

                for (UUID uuid : toRemove) {
                    activeMenus.remove(uuid);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        activeMenus.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        String title = "";
        try {
            Object view = event.getClass().getMethod("getView").invoke(event);
            title = (String) view.getClass().getMethod("getTitle").invoke(view);
        } catch (Exception e) {
            try {
                title = (String) event.getInventory().getClass().getMethod("getName").invoke(event.getInventory());
            } catch (Exception ignored) {}
        }

        if (title == null || title.isEmpty()) return;

        String strippedTitle = ChatColor.stripColor(title);

        if (strippedTitle.equals(ChatColor.stripColor(MAIN_TITLE))) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta() || !clicked.getItemMeta().hasDisplayName()) return;

            Player player = (Player) event.getWhoClicked();
            String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

            if (name.equals("DAILY")) {
                openPeriodMenu(player, "Daily");
            } else if (name.equals("WEEKLY")) {
                openPeriodMenu(player, "Weekly");
            } else if (name.equals("MONTHLY")) {
                openPeriodMenu(player, "Monthly");
            } else if (name.equals("CLOSE")) {
                player.closeInventory();
            }
        } else if (strippedTitle.startsWith(ChatColor.stripColor(PERIOD_TITLE_PREFIX))) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta() || !clicked.getItemMeta().hasDisplayName()) return;

            Player player = (Player) event.getWhoClicked();
            String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

            if (name.equals("BACK")) {
                openMainMenu(player);
            }
        }
    }
}