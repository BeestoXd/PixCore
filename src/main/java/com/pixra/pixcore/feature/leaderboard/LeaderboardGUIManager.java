package com.pixra.pixcore.feature.leaderboard;

import com.pixra.pixcore.PixCore;
import me.clip.placeholderapi.PlaceholderAPI;
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
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class LeaderboardGUIManager implements Listener {

    private static final int MAIN_PAGES = 4;
    private static final int DETAIL_SIZE = 54;

    private final PixCore plugin;
    private File guiFile;
    private FileConfiguration guiConfig;

    private String titleMain;
    private String itemTop10Format;
    private String itemResetFormat;
    private String globalTop10Format;

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
                File parent = guiFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                guiFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        guiConfig = YamlConfiguration.loadConfiguration(guiFile);
        boolean saveNeeded = false;

        saveNeeded |= setDefault("titles.main-menu", "&8Leaderboards");
        saveNeeded |= setDefault("titles.item-top10-format", "&7Top 10 {type} {period}:");
        saveNeeded |= setDefault("titles.item-reset-format", "&7Reset in: &c{time}");
        saveNeeded |= setDefault("titles.global-top10-format", "&7Top 10 {type}:");

        saveNeeded |= setDefault("gui-settings.winstreak-daily.name", "&b&lDAILY STREAK");
        saveNeeded |= setDefault("gui-settings.winstreak-daily.lore", "&7Click to view Daily Streak");
        saveNeeded |= setDefault("gui-settings.winstreak-daily.slot", 22);
        saveNeeded |= setDefault("gui-settings.winstreak-daily.material", "NAME_TAG");

        saveNeeded |= setDefault("gui-settings.wins-daily.name", "&a&lDAILY TOP WINS");
        saveNeeded |= setDefault("gui-settings.wins-daily.lore", "&7Click to view Daily Top Wins");
        saveNeeded |= setDefault("gui-settings.wins-daily.slot", 20);
        saveNeeded |= setDefault("gui-settings.wins-daily.material", "NAME_TAG");

        saveNeeded |= setDefault("gui-settings.wins-weekly.name", "&e&lWEEKLY TOP WINS");
        saveNeeded |= setDefault("gui-settings.wins-weekly.lore", "&7Click to view Weekly Top Wins");
        saveNeeded |= setDefault("gui-settings.wins-weekly.slot", 21);
        saveNeeded |= setDefault("gui-settings.wins-weekly.material", "NAME_TAG");

        saveNeeded |= setDefault("gui-settings.wins-monthly.name", "&6&lMONTHLY TOP WINS");
        saveNeeded |= setDefault("gui-settings.wins-monthly.lore", "&7Click to view Monthly Top Wins");
        saveNeeded |= setDefault("gui-settings.wins-monthly.slot", 23);
        saveNeeded |= setDefault("gui-settings.wins-monthly.material", "NAME_TAG");

        saveNeeded |= setDefault("gui-settings.wins-lifetime.name", "&c&lLIFETIME TOP WINS");
        saveNeeded |= setDefault("gui-settings.wins-lifetime.lore", "&7Click to view Lifetime Top Wins");
        saveNeeded |= setDefault("gui-settings.wins-lifetime.slot", 24);
        saveNeeded |= setDefault("gui-settings.wins-lifetime.material", "NAME_TAG");

        saveNeeded |= setDefault("gui-settings.kills-daily.name", "&a&lDAILY TOP KILLS");
        saveNeeded |= setDefault("gui-settings.kills-daily.lore", "&7Click to view Daily Top Kills");
        saveNeeded |= setDefault("gui-settings.kills-daily.slot", 20);
        saveNeeded |= setDefault("gui-settings.kills-daily.material", "NAME_TAG");

        saveNeeded |= setDefault("gui-settings.kills-weekly.name", "&e&lWEEKLY TOP KILLS");
        saveNeeded |= setDefault("gui-settings.kills-weekly.lore", "&7Click to view Weekly Top Kills");
        saveNeeded |= setDefault("gui-settings.kills-weekly.slot", 21);
        saveNeeded |= setDefault("gui-settings.kills-weekly.material", "NAME_TAG");

        saveNeeded |= setDefault("gui-settings.kills-monthly.name", "&6&lMONTHLY TOP KILLS");
        saveNeeded |= setDefault("gui-settings.kills-monthly.lore", "&7Click to view Monthly Top Kills");
        saveNeeded |= setDefault("gui-settings.kills-monthly.slot", 23);
        saveNeeded |= setDefault("gui-settings.kills-monthly.material", "NAME_TAG");

        saveNeeded |= setDefault("gui-settings.kills-lifetime.name", "&c&lLIFETIME TOP KILLS");
        saveNeeded |= setDefault("gui-settings.kills-lifetime.lore", "&7Click to view Lifetime Top Kills");
        saveNeeded |= setDefault("gui-settings.kills-lifetime.slot", 24);
        saveNeeded |= setDefault("gui-settings.kills-lifetime.material", "NAME_TAG");

        saveNeeded |= setDefault("gui-settings.deaths-lifetime.name", "&4&lLIFETIME TOP DEATHS");
        saveNeeded |= setDefault("gui-settings.deaths-lifetime.lore", "&7Click to view Lifetime Top Deaths");
        saveNeeded |= setDefault("gui-settings.deaths-lifetime.slot", 20);
        saveNeeded |= setDefault("gui-settings.deaths-lifetime.material", "SKELETON_SKULL");

        saveNeeded |= setDefault("gui-settings.playtime-lifetime.name", "&b&lLIFETIME TOP PLAYTIME");
        saveNeeded |= setDefault("gui-settings.playtime-lifetime.lore", "&7Click to view Lifetime Top Playtime");
        saveNeeded |= setDefault("gui-settings.playtime-lifetime.slot", 22);
        saveNeeded |= setDefault("gui-settings.playtime-lifetime.material", "CLOCK");

        saveNeeded |= setDefault("gui-settings.elo-lifetime.name", "&6&lRANKED ELO");
        saveNeeded |= setDefault("gui-settings.elo-lifetime.lore", "&7Click to view Ranked Elo per kit");
        saveNeeded |= setDefault("gui-settings.elo-lifetime.slot", 24);
        saveNeeded |= setDefault("gui-settings.elo-lifetime.material", "DIAMOND_SWORD");

        saveNeeded |= setDefault("gui-settings.close.name", "&c&lCLOSE");
        saveNeeded |= setDefault("gui-settings.close.lore", "&7Close the menu");
        saveNeeded |= setDefault("gui-settings.close.slot", 40);
        saveNeeded |= setDefault("gui-settings.close.material", "NETHER_STAR");

        saveNeeded |= setDefault("gui-settings.next-page.name", "&a&lNEXT PAGE");
        saveNeeded |= setDefault("gui-settings.next-page.lore", "&7Go to next page");
        saveNeeded |= setDefault("gui-settings.next-page.slot", 41);
        saveNeeded |= setDefault("gui-settings.next-page.material", "ARROW");

        saveNeeded |= setDefault("gui-settings.prev-page.name", "&c&lPREVIOUS PAGE");
        saveNeeded |= setDefault("gui-settings.prev-page.lore", "&7Go to previous page");
        saveNeeded |= setDefault("gui-settings.prev-page.slot", 39);
        saveNeeded |= setDefault("gui-settings.prev-page.material", "ARROW");

        saveNeeded |= setDefault("gui-settings.back.name", "&c&lBACK");
        saveNeeded |= setDefault("gui-settings.back.lore", "&7Return to Main Menu");
        saveNeeded |= setDefault("gui-settings.back.slot", 48);
        saveNeeded |= setDefault("gui-settings.back.material", "ARROW");

        saveNeeded |= setDefault("gui-settings.period-close.name", "&c&lCLOSE");
        saveNeeded |= setDefault("gui-settings.period-close.lore", "&7Close the menu");
        saveNeeded |= setDefault("gui-settings.period-close.slot", 50);
        saveNeeded |= setDefault("gui-settings.period-close.material", "NETHER_STAR");

        if (saveNeeded) {
            saveConfig();
        }

        titleMain = color(guiConfig.getString("titles.main-menu", "&8Leaderboards"));
        itemTop10Format = guiConfig.getString("titles.item-top10-format", "&7Top 10 {type} {period}:");
        itemResetFormat = guiConfig.getString("titles.item-reset-format", "&7Reset in: &c{time}");
        globalTop10Format = guiConfig.getString("titles.global-top10-format", "&7Top 10 {type}:");
    }

    private boolean setDefault(String path, Object value) {
        if (guiConfig.contains(path)) {
            return false;
        }
        guiConfig.set(path, value);
        return true;
    }

    public void saveConfig() {
        try {
            guiConfig.save(guiFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setGuiItem(String kitName, int slot, ItemStack item) {
        String normalizedKit = normalizeKit(kitName);
        String path = "items." + normalizedKit;
        guiConfig.set(path + ".slot", slot);
        guiConfig.set(path + ".material", item.getType().name());
        guiConfig.set(path + ".data", item.getDurability());
        saveConfig();
    }

    public void removeGuiItem(String kitName) {
        guiConfig.set("items." + normalizeKit(kitName), null);
        saveConfig();
    }

    public void openMainMenu(Player player) {
        openMainMenu(player, 1);
    }

    public void openMainMenu(Player player, int page) {
        int targetPage = Math.max(1, Math.min(MAIN_PAGES, page));
        String inventoryTitle = truncateInventoryTitle(color(titleMain + " &7(" + targetPage + "/" + MAIN_PAGES + ")"));
        Inventory inventory = Bukkit.createInventory(null, 45, inventoryTitle);

        if (targetPage == 1) {
            inventory.setItem(getSlot("gui-settings.winstreak-daily.slot", 22), createConfigItem("gui-settings.winstreak-daily"));
            inventory.setItem(getSlot("gui-settings.next-page.slot", 41), createConfigItem("gui-settings.next-page"));
        } else if (targetPage == 2) {
            inventory.setItem(getSlot("gui-settings.wins-daily.slot", 20), createConfigItem("gui-settings.wins-daily"));
            inventory.setItem(getSlot("gui-settings.wins-weekly.slot", 21), createConfigItem("gui-settings.wins-weekly"));
            inventory.setItem(getSlot("gui-settings.wins-monthly.slot", 23), createConfigItem("gui-settings.wins-monthly"));
            inventory.setItem(getSlot("gui-settings.wins-lifetime.slot", 24), createConfigItem("gui-settings.wins-lifetime"));
            inventory.setItem(getSlot("gui-settings.prev-page.slot", 39), createConfigItem("gui-settings.prev-page"));
            inventory.setItem(getSlot("gui-settings.next-page.slot", 41), createConfigItem("gui-settings.next-page"));
        } else if (targetPage == 3) {
            inventory.setItem(getSlot("gui-settings.kills-daily.slot", 20), createConfigItem("gui-settings.kills-daily"));
            inventory.setItem(getSlot("gui-settings.kills-weekly.slot", 21), createConfigItem("gui-settings.kills-weekly"));
            inventory.setItem(getSlot("gui-settings.kills-monthly.slot", 23), createConfigItem("gui-settings.kills-monthly"));
            inventory.setItem(getSlot("gui-settings.kills-lifetime.slot", 24), createConfigItem("gui-settings.kills-lifetime"));
            inventory.setItem(getSlot("gui-settings.prev-page.slot", 39), createConfigItem("gui-settings.prev-page"));
            inventory.setItem(getSlot("gui-settings.next-page.slot", 41), createConfigItem("gui-settings.next-page"));
        } else {
            inventory.setItem(getSlot("gui-settings.deaths-lifetime.slot", 20), createConfigItem("gui-settings.deaths-lifetime"));
            inventory.setItem(getSlot("gui-settings.playtime-lifetime.slot", 22), createConfigItem("gui-settings.playtime-lifetime"));
            inventory.setItem(getSlot("gui-settings.elo-lifetime.slot", 24), createConfigItem("gui-settings.elo-lifetime"));
            inventory.setItem(getSlot("gui-settings.prev-page.slot", 39), createConfigItem("gui-settings.prev-page"));
        }

        inventory.setItem(getSlot("gui-settings.close.slot", 40), createConfigItem("gui-settings.close"));
        player.openInventory(inventory);
        activeMenus.put(player.getUniqueId(), "main:" + targetPage);
    }

    public void openPeriodMenu(Player player, String category, String period) {
        String normalizedCategory = plugin.leaderboardManager.normalizeCategory(category);
        String normalizedPeriod = plugin.leaderboardManager.normalizePeriod(period);
        String inventoryTitle = truncateInventoryTitle(color("&8" + normalizedPeriod.toUpperCase(Locale.ROOT) + " "
                + plugin.leaderboardManager.getCategoryDisplayName(normalizedCategory).toUpperCase(Locale.ROOT)));

        Inventory inventory = Bukkit.createInventory(null, DETAIL_SIZE, inventoryTitle);
        updateKitMenu(inventory, normalizedCategory, normalizedPeriod);
        player.openInventory(inventory);
        activeMenus.put(player.getUniqueId(), "kits:" + normalizedCategory + ":" + normalizedPeriod);
    }

    public void openGlobalMenu(Player player, String category) {
        String normalizedCategory = plugin.leaderboardManager.normalizeCategory(category);
        String inventoryTitle = truncateInventoryTitle(color("&8LIFETIME "
                + plugin.leaderboardManager.getCategoryDisplayName(normalizedCategory).toUpperCase(Locale.ROOT)));

        Inventory inventory = Bukkit.createInventory(null, DETAIL_SIZE, inventoryTitle);
        updateGlobalMenu(inventory, normalizedCategory);
        player.openInventory(inventory);
        activeMenus.put(player.getUniqueId(), "global:" + normalizedCategory);
    }

    private void updateKitMenu(Inventory inventory, String category, String period) {
        inventory.clear();
        Set<String> renderedKitIdentities = new HashSet<>();

        ConfigurationSection itemsSection = guiConfig.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String kitName : itemsSection.getKeys(false)) {
                ConfigurationSection kitSection = itemsSection.getConfigurationSection(kitName);
                if (kitSection == null) {
                    continue;
                }

                String displayKitName = "elo".equals(category)
                        ? plugin.leaderboardManager.resolveLeaderboardKitDisplayName(category, kitName)
                        : kitName;
                String kitIdentity = plugin.leaderboardManager.resolveLeaderboardKitIdentity(category, kitName);
                if ("elo".equals(category)) {
                    if (displayKitName == null || displayKitName.trim().isEmpty()
                            || "global_elo".equalsIgnoreCase(kitIdentity)
                            || !renderedKitIdentities.add(kitIdentity)) {
                        continue;
                    }
                }

                int slot = kitSection.getInt("slot");
                Material material = resolveMaterial(kitSection.getString("material"), Material.STONE);
                short data = (short) kitSection.getInt("data");

                ItemStack item = new ItemStack(material, 1, data);
                ItemMeta meta = item.getItemMeta();
                if (meta == null) {
                    continue;
                }

                meta.setDisplayName(color("&e&l" + displayKitName.toUpperCase(Locale.ROOT)));

                List<String> lore = new ArrayList<>();
                lore.add("");
                lore.add(color(itemTop10Format
                        .replace("{type}", plugin.leaderboardManager.getCategoryDisplayName(category))
                        .replace("{period}", period.toUpperCase(Locale.ROOT))));

                List<LeaderboardManager.DisplayEntry> topEntries =
                        plugin.leaderboardManager.getDisplayTop(category, period, displayKitName, 10);
                lore.addAll(buildTopLore(topEntries));
                lore.add("");

                String footer = buildFooterLine(category, period);
                if (!footer.isEmpty()) {
                    lore.add(color(footer));
                }

                meta.setLore(lore);
                item.setItemMeta(meta);

                if (slot >= 0 && slot < inventory.getSize()) {
                    inventory.setItem(slot, item);
                }
            }
        }

        inventory.setItem(getSlot("gui-settings.back.slot", 48), createConfigItem("gui-settings.back"));
        inventory.setItem(getSlot("gui-settings.period-close.slot", 50), createConfigItem("gui-settings.period-close"));
    }

    private void updateGlobalMenu(Inventory inventory, String category) {
        inventory.clear();

        Material icon = "playtime".equals(category)
                ? resolveMaterial("CLOCK", Material.STONE)
                : resolveMaterial("SKELETON_SKULL", Material.STONE);

        ItemStack item = new ItemStack(icon, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color("&e&l" + plugin.leaderboardManager.getCategoryDisplayName(category).toUpperCase(Locale.ROOT)));

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(color(globalTop10Format.replace("{type}", plugin.leaderboardManager.getCategoryDisplayName(category))));
            lore.addAll(buildTopLore(plugin.leaderboardManager.getDisplayTop(category, "lifetime", null, 10)));
            lore.add("");
            lore.add(color(buildFooterLine(category, "lifetime")));

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        inventory.setItem(22, item);
        inventory.setItem(getSlot("gui-settings.back.slot", 48), createConfigItem("gui-settings.back"));
        inventory.setItem(getSlot("gui-settings.period-close.slot", 50), createConfigItem("gui-settings.period-close"));
    }

    private List<String> buildTopLore(List<LeaderboardManager.DisplayEntry> topEntries) {
        List<String> lore = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            int rank = index + 1;
            String rankColor = rank == 1 ? "&a" : (rank == 2 ? "&e" : (rank == 3 ? "&6" : "&f"));
            if (index < topEntries.size()) {
                LeaderboardManager.DisplayEntry entry = topEntries.get(index);
                String playerName = entry.getPlayerName();
                lore.add(color(rankColor + rank + ". "
                        + resolvePrefix(playerName)
                        + "&f" + playerName
                        + "&r " + resolveTag(playerName)
                        + "&r &7- &d" + entry.getDisplayValue()));
            } else {
                lore.add(color(rankColor + rank + ". &7&o---"));
            }
        }
        return lore;
    }

    private String buildFooterLine(String category, String period) {
        if ("daily".equals(period)) {
            return itemResetFormat.replace("{time}", plugin.leaderboardManager.getDailyCountdown());
        }
        if ("weekly".equals(period)) {
            return itemResetFormat.replace("{time}", plugin.leaderboardManager.getWeeklyCountdown());
        }
        if ("monthly".equals(period)) {
            return itemResetFormat.replace("{time}", plugin.leaderboardManager.getMonthlyCountdown());
        }
        if ("elo".equals(category)) {
            return "&7Current ranked ladder";
        }
        if ("playtime".equals(category)) {
            return "&7Total time spent on server";
        }
        return "&7Lifetime leaderboard";
    }

    private ItemStack createConfigItem(String path) {
        Material material = resolveMaterial(guiConfig.getString(path + ".material"), Material.STONE);
        String name = guiConfig.getString(path + ".name", "&cUnknown");
        String loreLine = guiConfig.getString(path + ".lore", "");
        return createItem(material, 0, name, loreLine);
    }

    private ItemStack createItem(Material material, int data, String name, String loreLine) {
        ItemStack item = new ItemStack(material != null ? material : Material.STONE, 1, (short) data);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            List<String> lore = new ArrayList<>();
            if (loreLine != null && !loreLine.isEmpty()) {
                lore.add(color(loreLine));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private Material resolveMaterial(String materialName, Material fallback) {
        if (materialName == null || materialName.isEmpty()) {
            return fallback;
        }

        Material material = Material.getMaterial(materialName);
        if (material == null && "BED".equalsIgnoreCase(materialName)) {
            material = Material.getMaterial("RED_BED");
        }
        if (material == null && "CLOCK".equalsIgnoreCase(materialName)) {
            material = Material.getMaterial("WATCH");
        }
        if (material == null && "SKELETON_SKULL".equalsIgnoreCase(materialName)) {
            material = Material.getMaterial("SKULL_ITEM");
        }
        return material != null ? material : fallback;
    }

    private int getSlot(String path, int fallback) {
        return guiConfig.getInt(path, fallback);
    }

    private String normalizeKit(String kitName) {
        if (kitName == null) {
            return "";
        }
        return ChatColor.stripColor(kitName).toLowerCase(Locale.ROOT);
    }

    private String truncateInventoryTitle(String title) {
        return title.length() > 32 ? title.substring(0, 32) : title;
    }

    private void startUpdaterTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (activeMenus.isEmpty()) {
                    return;
                }

                List<UUID> staleMenus = new ArrayList<>();
                for (Map.Entry<UUID, String> entry : activeMenus.entrySet()) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player == null || !player.isOnline()) {
                        staleMenus.add(entry.getKey());
                        continue;
                    }

                    Inventory topInventory;
                    try {
                        Object view = player.getClass().getMethod("getOpenInventory").invoke(player);
                        topInventory = (Inventory) view.getClass().getMethod("getTopInventory").invoke(view);
                    } catch (Exception ignored) {
                        staleMenus.add(entry.getKey());
                        continue;
                    }

                    if (topInventory == null || topInventory.getSize() != DETAIL_SIZE) {
                        continue;
                    }

                    String active = entry.getValue();
                    if (active.startsWith("kits:")) {
                        String[] parts = active.split(":", 3);
                        if (parts.length == 3) {
                            updateKitMenu(topInventory, parts[1], parts[2]);
                        }
                    } else if (active.startsWith("global:")) {
                        String[] parts = active.split(":", 2);
                        if (parts.length == 2) {
                            updateGlobalMenu(topInventory, parts[1]);
                        }
                    }
                }

                for (UUID uuid : staleMenus) {
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
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        UUID uuid = player.getUniqueId();
        if (!activeMenus.containsKey(uuid)) {
            return;
        }

        event.setCancelled(true);
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) {
            return;
        }

        int slot = event.getRawSlot();
        String active = activeMenus.get(uuid);

        if (active.startsWith("main:")) {
            handleMainMenuClick(player, active, slot);
            return;
        }

        if (slot == getSlot("gui-settings.back.slot", 48)) {
            openMainMenu(player, resolveBackPage(active));
        } else if (slot == getSlot("gui-settings.period-close.slot", 50)) {
            player.closeInventory();
        }
    }

    private void handleMainMenuClick(Player player, String active, int slot) {
        int page = 1;
        try {
            page = Integer.parseInt(active.split(":")[1]);
        } catch (Exception ignored) {
        }

        if (slot == getSlot("gui-settings.close.slot", 40)) {
            player.closeInventory();
            return;
        }

        if (page == 1) {
            if (slot == getSlot("gui-settings.winstreak-daily.slot", 22)) {
                openPeriodMenu(player, "winstreak", "daily");
            } else if (slot == getSlot("gui-settings.next-page.slot", 41)) {
                openMainMenu(player, 2);
            }
            return;
        }

        if (page == 2) {
            if (slot == getSlot("gui-settings.wins-daily.slot", 20)) {
                openPeriodMenu(player, "wins", "daily");
            } else if (slot == getSlot("gui-settings.wins-weekly.slot", 21)) {
                openPeriodMenu(player, "wins", "weekly");
            } else if (slot == getSlot("gui-settings.wins-monthly.slot", 23)) {
                openPeriodMenu(player, "wins", "monthly");
            } else if (slot == getSlot("gui-settings.wins-lifetime.slot", 24)) {
                openPeriodMenu(player, "wins", "lifetime");
            } else if (slot == getSlot("gui-settings.prev-page.slot", 39)) {
                openMainMenu(player, 1);
            } else if (slot == getSlot("gui-settings.next-page.slot", 41)) {
                openMainMenu(player, 3);
            }
            return;
        }

        if (page == 3) {
            if (slot == getSlot("gui-settings.kills-daily.slot", 20)) {
                openPeriodMenu(player, "kills", "daily");
            } else if (slot == getSlot("gui-settings.kills-weekly.slot", 21)) {
                openPeriodMenu(player, "kills", "weekly");
            } else if (slot == getSlot("gui-settings.kills-monthly.slot", 23)) {
                openPeriodMenu(player, "kills", "monthly");
            } else if (slot == getSlot("gui-settings.kills-lifetime.slot", 24)) {
                openPeriodMenu(player, "kills", "lifetime");
            } else if (slot == getSlot("gui-settings.prev-page.slot", 39)) {
                openMainMenu(player, 2);
            } else if (slot == getSlot("gui-settings.next-page.slot", 41)) {
                openMainMenu(player, 4);
            }
            return;
        }

        if (slot == getSlot("gui-settings.deaths-lifetime.slot", 20)) {
            openGlobalMenu(player, "deaths");
        } else if (slot == getSlot("gui-settings.playtime-lifetime.slot", 22)) {
            openGlobalMenu(player, "playtime");
        } else if (slot == getSlot("gui-settings.elo-lifetime.slot", 24)) {
            openPeriodMenu(player, "elo", "lifetime");
        } else if (slot == getSlot("gui-settings.prev-page.slot", 39)) {
            openMainMenu(player, 3);
        }
    }

    private int resolveBackPage(String active) {
        if (active.startsWith("global:")) {
            return 4;
        }

        if (!active.startsWith("kits:")) {
            return 1;
        }

        String[] parts = active.split(":", 3);
        if (parts.length < 2) {
            return 1;
        }

        String category = parts[1];
        if ("wins".equals(category)) {
            return 2;
        }
        if ("kills".equals(category)) {
            return 3;
        }
        if ("elo".equals(category)) {
            return 4;
        }
        return 1;
    }

    private String resolvePrefix(String playerName) {
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            String rawPrefix = PlaceholderAPI.setPlaceholders(online, "%luckperms_prefix%");
            String resolved = "%luckperms_prefix%".equals(rawPrefix) ? "" : rawPrefix;
            plugin.prefixCache.put(playerName, resolved);
            return resolved;
        }
        return plugin.prefixCache.getOrDefault(playerName, "");
    }

    private String resolveTag(String playerName) {
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            String rawTag = PlaceholderAPI.setPlaceholders(online, "%deluxetags_tag%");
            String resolved = "%deluxetags_tag%".equals(rawTag) ? "" : rawTag;
            plugin.tagCache.put(playerName, resolved);
            return resolved;
        }
        return plugin.tagCache.getOrDefault(playerName, "");
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
