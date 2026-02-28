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

    private String titleMain;
    private String titlePeriodPrefix;
    private String itemTop10Format;
    private String itemResetFormat;

    private String dailyName, dailyLore;
    private String weeklyName, weeklyLore;
    private String monthlyName, monthlyLore;
    private String closeName, closeLore;
    private String backName, backLore;

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

        boolean saveNeeded = false;

        if (!guiConfig.contains("titles.main-menu")) {
            guiConfig.set("titles.main-menu", "&8Leaderboard Winstreak");
            guiConfig.set("titles.period-menu-prefix", "&8Top Winstreak: ");
            guiConfig.set("titles.item-top10-format", "&7Top 10 {period} Winstreak:");
            guiConfig.set("titles.item-reset-format", "&7Reset in: &c{time}");

            guiConfig.set("gui-settings.daily.name", "&b&lDAILY");
            guiConfig.set("gui-settings.daily.lore", "&7Click to view Daily Top 10");
            guiConfig.set("gui-settings.weekly.name", "&a&lWEEKLY");
            guiConfig.set("gui-settings.weekly.lore", "&7Click to view Weekly Top 10");
            guiConfig.set("gui-settings.monthly.name", "&6&lMONTHLY");
            guiConfig.set("gui-settings.monthly.lore", "&7Click to view Monthly Top 10");
            guiConfig.set("gui-settings.close.name", "&c&lCLOSE");
            guiConfig.set("gui-settings.close.lore", "&7Close the menu");
            guiConfig.set("gui-settings.back.name", "&c&lBACK");
            guiConfig.set("gui-settings.back.lore", "&7Return to Main Menu");
            saveNeeded = true;
        }

        if (saveNeeded) {
            saveConfig();
        }

        titleMain = ChatColor.translateAlternateColorCodes('&', guiConfig.getString("titles.main-menu", "&8Leaderboard Winstreak"));
        titlePeriodPrefix = ChatColor.translateAlternateColorCodes('&', guiConfig.getString("titles.period-menu-prefix", "&8Top Winstreak: "));
        itemTop10Format = guiConfig.getString("titles.item-top10-format", "&7Top 10 {period} Winstreak:");
        itemResetFormat = guiConfig.getString("titles.item-reset-format", "&7Reset in: &c{time}");

        dailyName = guiConfig.getString("gui-settings.daily.name", "&b&lDAILY");
        dailyLore = guiConfig.getString("gui-settings.daily.lore", "&7Click to view Daily Top 10");
        weeklyName = guiConfig.getString("gui-settings.weekly.name", "&a&lWEEKLY");
        weeklyLore = guiConfig.getString("gui-settings.weekly.lore", "&7Click to view Weekly Top 10");
        monthlyName = guiConfig.getString("gui-settings.monthly.name", "&6&lMONTHLY");
        monthlyLore = guiConfig.getString("gui-settings.monthly.lore", "&7Click to view Monthly Top 10");
        closeName = guiConfig.getString("gui-settings.close.name", "&c&lCLOSE");
        closeLore = guiConfig.getString("gui-settings.close.lore", "&7Close the menu");
        backName = guiConfig.getString("gui-settings.back.name", "&c&lBACK");
        backLore = guiConfig.getString("gui-settings.back.lore", "&7Return to Main Menu");
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
        String invTitle = titleMain;
        if (invTitle.length() > 32) invTitle = invTitle.substring(0, 32);

        Inventory inv = Bukkit.createInventory(null, 36, invTitle);

        inv.setItem(11, createItem(Material.NAME_TAG, 0, dailyName, dailyLore));
        inv.setItem(13, createItem(Material.NAME_TAG, 0, weeklyName, weeklyLore));
        inv.setItem(15, createItem(Material.NAME_TAG, 0, monthlyName, monthlyLore));

        inv.setItem(31, createItem(Material.NETHER_STAR, 0, closeName, closeLore));

        player.openInventory(inv);
    }

    public void openPeriodMenu(Player player, String periodRaw) {
        String invTitle = titlePeriodPrefix + periodRaw.toUpperCase();
        if (invTitle.length() > 32) invTitle = invTitle.substring(0, 32);

        Inventory inv = Bukkit.createInventory(null, 54, invTitle);

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
                lore.add(ChatColor.translateAlternateColorCodes('&', itemTop10Format.replace("{period}", periodRaw.toUpperCase())));

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

                lore.add(ChatColor.translateAlternateColorCodes('&', itemResetFormat.replace("{time}", countdown)));

                meta.setLore(lore);
                item.setItemMeta(meta);

                if (slot >= 0 && slot < 54) {
                    inv.setItem(slot, item);
                }
            }
        }

        inv.setItem(49, createItem(Material.ARROW, 0, backName, backLore));
    }

    private ItemStack createItem(Material mat, int data, String name, String loreLine) {
        if (mat == null) mat = Material.STONE;
        if (name == null) name = "&cError: Name Null";
        if (loreLine == null) loreLine = "";

        ItemStack item = new ItemStack(mat, 1, (short) data);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            List<String> lore = new ArrayList<>();
            if (!loreLine.isEmpty()) {
                lore.add(ChatColor.translateAlternateColorCodes('&', loreLine));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
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

        String expectedMain = ChatColor.stripColor(titleMain);
        if (expectedMain.length() > 32) expectedMain = expectedMain.substring(0, 32);

        String expectedPrefix = ChatColor.stripColor(titlePeriodPrefix);

        if (strippedTitle.equals(expectedMain)) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

            Player player = (Player) event.getWhoClicked();
            int slot = event.getRawSlot();

            if (slot == 11) {
                openPeriodMenu(player, "Daily");
            } else if (slot == 13) {
                openPeriodMenu(player, "Weekly");
            } else if (slot == 15) {
                openPeriodMenu(player, "Monthly");
            } else if (slot == 31) {
                player.closeInventory();
            }
        } else if (strippedTitle.startsWith(expectedPrefix)) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

            Player player = (Player) event.getWhoClicked();
            int slot = event.getRawSlot();

            if (slot == 49) {
                openMainMenu(player);
            }
        }
    }
}