package com.pixra.pixcore.feature.leaderboard;

import com.pixra.pixcore.PixCore;
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

import me.clip.placeholderapi.PlaceholderAPI;

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
    private String itemTop10Format;
    private String itemResetFormat;

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
            guiConfig.set("titles.main-menu", "&8Leaderboards");
            saveNeeded = true;
        }
        if (!guiConfig.contains("titles.item-top10-format")) {
            guiConfig.set("titles.item-top10-format", "&7Top 10 {type} {period}:");
            guiConfig.set("titles.item-reset-format", "&7Reset in: &c{time}");
            saveNeeded = true;
        }

        if (!guiConfig.contains("gui-settings.page-3-support")) {
            guiConfig.set("gui-settings.page-3-support", true);

            guiConfig.set("gui-settings.winstreak-daily.name", "&b&lDAILY STREAK");
            guiConfig.set("gui-settings.winstreak-daily.lore", "&7Click to view Daily Streak");
            guiConfig.set("gui-settings.winstreak-daily.slot", 22);
            guiConfig.set("gui-settings.winstreak-daily.material", "NAME_TAG");

            guiConfig.set("gui-settings.wins-daily.name", "&a&lDAILY TOP WINS");
            guiConfig.set("gui-settings.wins-daily.lore", "&7Click to view Daily Top Wins");
            guiConfig.set("gui-settings.wins-daily.slot", 20);
            guiConfig.set("gui-settings.wins-daily.material", "NAME_TAG");

            guiConfig.set("gui-settings.wins-weekly.name", "&e&lWEEKLY TOP WINS");
            guiConfig.set("gui-settings.wins-weekly.lore", "&7Click to view Weekly Top Wins");
            guiConfig.set("gui-settings.wins-weekly.slot", 21);
            guiConfig.set("gui-settings.wins-weekly.material", "NAME_TAG");

            guiConfig.set("gui-settings.wins-monthly.name", "&6&lMONTHLY TOP WINS");
            guiConfig.set("gui-settings.wins-monthly.lore", "&7Click to view Monthly Top Wins");
            guiConfig.set("gui-settings.wins-monthly.slot", 23);
            guiConfig.set("gui-settings.wins-monthly.material", "NAME_TAG");

            guiConfig.set("gui-settings.wins-lifetime.name", "&c&lLIFETIME TOP WINS");
            guiConfig.set("gui-settings.wins-lifetime.lore", "&7Click to view Lifetime Top Wins");
            guiConfig.set("gui-settings.wins-lifetime.slot", 24);
            guiConfig.set("gui-settings.wins-lifetime.material", "NAME_TAG");

            guiConfig.set("gui-settings.kills-daily.name", "&a&lDAILY TOP KILLS");
            guiConfig.set("gui-settings.kills-daily.lore", "&7Click to view Daily Top KILLS");
            guiConfig.set("gui-settings.kills-daily.slot", 20);
            guiConfig.set("gui-settings.kills-daily.material", "NAME_TAG");

            guiConfig.set("gui-settings.kills-weekly.name", "&e&lWEEKLY TOP KILLS");
            guiConfig.set("gui-settings.kills-weekly.lore", "&7Click to view Weekly Top KILLS");
            guiConfig.set("gui-settings.kills-weekly.slot", 21);
            guiConfig.set("gui-settings.kills-weekly.material", "NAME_TAG");

            guiConfig.set("gui-settings.kills-monthly.name", "&6&lMONTHLY TOP KILLS");
            guiConfig.set("gui-settings.kills-monthly.lore", "&7Click to view Monthly Top KILLS");
            guiConfig.set("gui-settings.kills-monthly.slot", 23);
            guiConfig.set("gui-settings.kills-monthly.material", "NAME_TAG");

            guiConfig.set("gui-settings.kills-lifetime.name", "&c&lLIFETIME TOP KILLS");
            guiConfig.set("gui-settings.kills-lifetime.lore", "&7Click to view Lifetime Top KILLS");
            guiConfig.set("gui-settings.kills-lifetime.slot", 24);
            guiConfig.set("gui-settings.kills-lifetime.material", "NAME_TAG");

            guiConfig.set("gui-settings.close.name", "&c&lCLOSE");
            guiConfig.set("gui-settings.close.lore", "&7Close the menu");
            guiConfig.set("gui-settings.close.slot", 40);
            guiConfig.set("gui-settings.close.material", "NETHER_STAR");

            guiConfig.set("gui-settings.next-page.name", "&a&lNEXT PAGE");
            guiConfig.set("gui-settings.next-page.lore", "&7Go to next page");
            guiConfig.set("gui-settings.next-page.slot", 41);
            guiConfig.set("gui-settings.next-page.material", "ARROW");

            guiConfig.set("gui-settings.prev-page.name", "&c&lPREVIOUS PAGE");
            guiConfig.set("gui-settings.prev-page.lore", "&7Go to previous page");
            guiConfig.set("gui-settings.prev-page.slot", 39);
            guiConfig.set("gui-settings.prev-page.material", "ARROW");

            guiConfig.set("gui-settings.back.name", "&c&lBACK");
            guiConfig.set("gui-settings.back.lore", "&7Return to Main Menu");
            guiConfig.set("gui-settings.back.slot", 48);
            guiConfig.set("gui-settings.back.material", "ARROW");

            guiConfig.set("gui-settings.period-close.name", "&c&lCLOSE");
            guiConfig.set("gui-settings.period-close.lore", "&7Close the menu");
            guiConfig.set("gui-settings.period-close.slot", 50);
            guiConfig.set("gui-settings.period-close.material", "NETHER_STAR");

            saveNeeded = true;
        }

        if (saveNeeded) {
            saveConfig();
        }

        titleMain = ChatColor.translateAlternateColorCodes('&', guiConfig.getString("titles.main-menu", "&8Leaderboards"));
        itemTop10Format = guiConfig.getString("titles.item-top10-format", "&7Top 10 {type} {period}:");
        itemResetFormat = guiConfig.getString("titles.item-reset-format", "&7Reset in: &c{time}");
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

    private ItemStack createConfigItem(String path) {
        String name = guiConfig.getString(path + ".name", "&cName");
        String loreLine = guiConfig.getString(path + ".lore", "");
        String matName = guiConfig.getString(path + ".material", "STONE");

        Material mat = Material.getMaterial(matName);
        if (mat == null && matName.equalsIgnoreCase("BED")) {
            mat = Material.getMaterial("RED_BED");
        }
        if (mat == null) mat = Material.STONE;

        return createItem(mat, 0, name, loreLine);
    }

    public void openMainMenu(Player player) {
        openMainMenu(player, 1);
    }

    public void openMainMenu(Player player, int page) {
        String invTitle = titleMain + " &7(" + page + "/3)";
        invTitle = ChatColor.translateAlternateColorCodes('&', invTitle);
        if (invTitle.length() > 32) invTitle = invTitle.substring(0, 32);

        Inventory inv = Bukkit.createInventory(null, 45, invTitle);

        if (page == 1) {
            inv.setItem(guiConfig.getInt("gui-settings.winstreak-daily.slot", 22), createConfigItem("gui-settings.winstreak-daily"));
            inv.setItem(guiConfig.getInt("gui-settings.next-page.slot", 41), createConfigItem("gui-settings.next-page"));
        } else if (page == 2) {
            inv.setItem(guiConfig.getInt("gui-settings.wins-daily.slot", 20), createConfigItem("gui-settings.wins-daily"));
            inv.setItem(guiConfig.getInt("gui-settings.wins-weekly.slot", 21), createConfigItem("gui-settings.wins-weekly"));
            inv.setItem(guiConfig.getInt("gui-settings.wins-monthly.slot", 23), createConfigItem("gui-settings.wins-monthly"));
            inv.setItem(guiConfig.getInt("gui-settings.wins-lifetime.slot", 24), createConfigItem("gui-settings.wins-lifetime"));
            inv.setItem(guiConfig.getInt("gui-settings.prev-page.slot", 39), createConfigItem("gui-settings.prev-page"));
            inv.setItem(guiConfig.getInt("gui-settings.next-page.slot", 41), createConfigItem("gui-settings.next-page"));
        } else if (page == 3) {
            inv.setItem(guiConfig.getInt("gui-settings.kills-daily.slot", 20), createConfigItem("gui-settings.kills-daily"));
            inv.setItem(guiConfig.getInt("gui-settings.kills-weekly.slot", 21), createConfigItem("gui-settings.kills-weekly"));
            inv.setItem(guiConfig.getInt("gui-settings.kills-monthly.slot", 23), createConfigItem("gui-settings.kills-monthly"));
            inv.setItem(guiConfig.getInt("gui-settings.kills-lifetime.slot", 24), createConfigItem("gui-settings.kills-lifetime"));
            inv.setItem(guiConfig.getInt("gui-settings.prev-page.slot", 39), createConfigItem("gui-settings.prev-page"));
        }

        inv.setItem(guiConfig.getInt("gui-settings.close.slot", 40), createConfigItem("gui-settings.close"));

        player.openInventory(inv);
        activeMenus.put(player.getUniqueId(), "main:" + page);
    }

    public void openPeriodMenu(Player player, String category, String period) {
        String capCat = category.substring(0, 1).toUpperCase() + category.substring(1);
        String capPer = period.toUpperCase();

        String invTitle = "&8" + capPer + " " + capCat;
        invTitle = ChatColor.translateAlternateColorCodes('&', invTitle);
        if (invTitle.length() > 32) invTitle = invTitle.substring(0, 32);

        Inventory inv = Bukkit.createInventory(null, 54, invTitle);

        updatePeriodMenu(inv, category, period);

        player.openInventory(inv);
        activeMenus.put(player.getUniqueId(), category + ":" + period);
    }

    private void updatePeriodMenu(Inventory inv, String category, String period) {
        ConfigurationSection itemsSection = guiConfig.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String kitName : itemsSection.getKeys(false)) {
                ConfigurationSection kitSec = itemsSection.getConfigurationSection(kitName);
                int slot = kitSec.getInt("slot");
                String matName = kitSec.getString("material");
                short data = (short) kitSec.getInt("data");

                Material mat = Material.getMaterial(matName);
                if (mat == null && matName.equalsIgnoreCase("BED")) {
                    mat = Material.getMaterial("RED_BED");
                }
                if (mat == null) mat = Material.STONE;

                ItemStack item = new ItemStack(mat, 1, data);
                ItemMeta meta = item.getItemMeta();

                String displayKitName = kitName.toUpperCase();
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&e&l" + displayKitName));

                List<String> lore = new ArrayList<>();
                lore.add("");

                String capCat = category.substring(0, 1).toUpperCase() + category.substring(1);
                String formattedHeader = itemTop10Format.replace("{type}", capCat).replace("{period}", period.toUpperCase());
                lore.add(ChatColor.translateAlternateColorCodes('&', formattedHeader));

                List<Map.Entry<String, Integer>> top10 = plugin.leaderboardManager.getTop(category, period, kitName, 10);
                String suffix = category.equalsIgnoreCase("kills") ? "Kills" : "Win";

                for (int i = 0; i < 10; i++) {
                    int rank = i + 1;
                    String color = rank == 1 ? "&a" : (rank == 2 ? "&e" : (rank == 3 ? "&6" : "&f"));
                    if (i < top10.size()) {
                        Map.Entry<String, Integer> entry = top10.get(i);
                        String pName = entry.getKey();
                        lore.add(ChatColor.translateAlternateColorCodes('&', color + rank + ". " + resolvePrefix(pName) + "&f" + pName + "&r " + resolveTag(pName) + "&r &7- &d" + entry.getValue() + " " + suffix));
                    } else {
                        lore.add(ChatColor.translateAlternateColorCodes('&', color + rank + ". &7&o---"));
                    }
                }

                lore.add("");
                String countdown = "Never";
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

        inv.setItem(guiConfig.getInt("gui-settings.back.slot", 48), createConfigItem("gui-settings.back"));
        inv.setItem(guiConfig.getInt("gui-settings.period-close.slot", 50), createConfigItem("gui-settings.period-close"));
    }

    private String resolvePrefix(String playerName) {
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            String lp = PlaceholderAPI.setPlaceholders(online, "%luckperms_prefix%");
            String result = "%luckperms_prefix%".equals(lp) ? "" : lp;
            plugin.prefixCache.put(playerName, result);
            return result;
        }
        return plugin.prefixCache.getOrDefault(playerName, "");
    }

    private String resolveTag(String playerName) {
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            String tag = PlaceholderAPI.setPlaceholders(online, "%deluxetags_tag%");
            String result = "%deluxetags_tag%".equals(tag) ? "" : tag;
            plugin.tagCache.put(playerName, result);
            return result;
        }
        return plugin.tagCache.getOrDefault(playerName, "");
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
                        continue;
                    }

                    String active = entry.getValue();
                    if (!active.startsWith("main:")) {
                        String[] parts = active.split(":");
                        if (parts.length == 2) {
                            updatePeriodMenu(topInv, parts[0], parts[1]);
                        }
                    }
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

        Player player = (Player) event.getWhoClicked();
        UUID uuid = player.getUniqueId();

        if (activeMenus.containsKey(uuid)) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

            int slot = event.getRawSlot();
            String active = activeMenus.get(uuid);

            if (active.startsWith("main:")) {
                int page = 1;
                try { page = Integer.parseInt(active.split(":")[1]); } catch (Exception ignored) {}

                if (slot == guiConfig.getInt("gui-settings.close.slot", 40)) {
                    player.closeInventory();
                } else if (page == 1) {
                    if (slot == guiConfig.getInt("gui-settings.winstreak-daily.slot", 22)) openPeriodMenu(player, "winstreak", "daily");
                    else if (slot == guiConfig.getInt("gui-settings.next-page.slot", 41)) openMainMenu(player, 2);
                } else if (page == 2) {
                    if (slot == guiConfig.getInt("gui-settings.wins-daily.slot", 20)) openPeriodMenu(player, "wins", "daily");
                    else if (slot == guiConfig.getInt("gui-settings.wins-weekly.slot", 21)) openPeriodMenu(player, "wins", "weekly");
                    else if (slot == guiConfig.getInt("gui-settings.wins-monthly.slot", 23)) openPeriodMenu(player, "wins", "monthly");
                    else if (slot == guiConfig.getInt("gui-settings.wins-lifetime.slot", 24)) openPeriodMenu(player, "wins", "lifetime");
                    else if (slot == guiConfig.getInt("gui-settings.prev-page.slot", 39)) openMainMenu(player, 1);
                    else if (slot == guiConfig.getInt("gui-settings.next-page.slot", 41)) openMainMenu(player, 3);
                } else if (page == 3) {
                    if (slot == guiConfig.getInt("gui-settings.kills-daily.slot", 20)) openPeriodMenu(player, "kills", "daily");
                    else if (slot == guiConfig.getInt("gui-settings.kills-weekly.slot", 21)) openPeriodMenu(player, "kills", "weekly");
                    else if (slot == guiConfig.getInt("gui-settings.kills-monthly.slot", 23)) openPeriodMenu(player, "kills", "monthly");
                    else if (slot == guiConfig.getInt("gui-settings.kills-lifetime.slot", 24)) openPeriodMenu(player, "kills", "lifetime");
                    else if (slot == guiConfig.getInt("gui-settings.prev-page.slot", 39)) openMainMenu(player, 2);
                }
            } else {
                if (slot == guiConfig.getInt("gui-settings.back.slot", 48)) {
                    String cat = active.split(":")[0];
                    if (cat.equals("kills")) {
                        openMainMenu(player, 3);
                    } else if (cat.equals("wins")) {
                        openMainMenu(player, 2);
                    } else {
                        openMainMenu(player, 1);
                    }
                } else if (slot == guiConfig.getInt("gui-settings.period-close.slot", 50)) {
                    player.closeInventory();
                }
            }
        }
    }
}
