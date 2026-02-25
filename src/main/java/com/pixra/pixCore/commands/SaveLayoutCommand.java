package com.pixra.pixCore.commands;

import com.pixra.pixCore.PixCore;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;

public class SaveLayoutCommand implements CommandExecutor {

    private final PixCore plugin;

    public SaveLayoutCommand(PixCore plugin) {
        this.plugin = plugin;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player p = (Player) sender;

        if (!plugin.isInFight(p)) {
            p.sendMessage(ChatColor.RED + "You can only use this command while in a match!");
            return true;
        }

        try {
            Object baseKit = plugin.getMGetKit().invoke(plugin.getStrikePracticeAPI(), p);
            if (baseKit == null) {
                p.sendMessage(ChatColor.RED + "Could not find your current kit.");
                return true;
            }

            String baseName = ChatColor.stripColor((String) baseKit.getClass().getMethod("getName").invoke(baseKit)).toLowerCase();

            File spFolder = new File(plugin.getDataFolder().getParentFile(), "StrikePractice");
            File pdFile = new File(spFolder, "playerdata/" + p.getUniqueId().toString() + ".yml");

            YamlConfiguration pdConfig;
            if (pdFile.exists()) {
                pdConfig = YamlConfiguration.loadConfiguration(pdFile);
            } else {
                pdConfig = new YamlConfiguration();
            }

            List<Object> customKits = (List<Object>) pdConfig.getList("kits");
            if (customKits == null) {
                customKits = new ArrayList<>();
            }

            boolean updated = false;

            ItemStack[] currentContents = p.getInventory().getContents();
            List<ItemStack> newInv = new ArrayList<>();
            for (int i = 0; i < 36 && i < currentContents.length; i++) {
                ItemStack item = currentContents[i];
                boolean isBook = false;

                if (item != null) {
                    String type = item.getType().name();
                    if (type.contains("BOOK") || type.contains("BED") || type.contains("NAME_TAG") || type.contains("PAPER") || type.contains("EMERALD")) {
                        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                            String dName = ChatColor.stripColor(item.getItemMeta().getDisplayName()).toLowerCase();
                            if (dName.contains("layout") || dName.contains("kit") || dName.contains("default") || dName.contains("edit") || dName.contains(baseName)) {
                                isBook = true;
                            }
                        }
                    }
                }
                newInv.add((item != null && !isBook) ? item.clone() : null);
            }

            for (int i = 0; i < customKits.size(); i++) {
                Object customKit = customKits.get(i);
                boolean isMatch = false;

                if (!Map.class.isAssignableFrom(customKit.getClass())) {
                    String kName = "";
                    try { kName = ChatColor.stripColor((String) customKit.getClass().getMethod("getName").invoke(customKit)).toLowerCase(); } catch (Exception ignored) {}
                    if (kName.contains(baseName)) isMatch = true;
                } else {
                    Map<String, Object> map = (Map<String, Object>) customKit;
                    String kName = map.containsKey("name") ? ChatColor.stripColor(String.valueOf(map.get("name"))).toLowerCase() : "";
                    if (kName.contains(baseName)) isMatch = true;
                }

                if (isMatch) {
                    if (customKit instanceof Map) {
                        Map<String, Object> map = (Map<String, Object>) customKit;
                        map.put("inventory", newInv);
                        customKits.set(i, map);
                    } else {
                        Map<String, Object> map = new LinkedHashMap<>();
                        map.put("==", "BattleKit");
                        try { map.put("name", customKit.getClass().getMethod("getName").invoke(customKit)); } catch(Exception e){}
                        try { map.put("icon", customKit.getClass().getMethod("getIcon").invoke(customKit)); } catch(Exception e){}
                        map.put("inventory", newInv);
                        customKits.set(i, map);
                    }
                    updated = true;
                    break;
                }
            }

            if (!updated) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("==", "BattleKit");
                try { map.put("name", baseKit.getClass().getMethod("getName").invoke(baseKit)); } catch(Exception e){}
                try { map.put("icon", baseKit.getClass().getMethod("getIcon").invoke(baseKit)); } catch(Exception e){}
                map.put("inventory", newInv);
                customKits.add(map);
            }

            pdConfig.set("kits", customKits);
            pdConfig.save(pdFile);

            p.sendMessage(ChatColor.GREEN + "Kit layout successfully saved! It will be used in your next matches.");

        } catch (Exception e) {
            p.sendMessage(ChatColor.RED + "An error occurred while saving layout.");
            e.printStackTrace();
        }

        return true;
    }
}