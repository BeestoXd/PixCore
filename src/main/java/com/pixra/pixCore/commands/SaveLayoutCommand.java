package com.pixra.pixCore.commands;

import com.pixra.pixCore.PixCore;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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

            List<ItemStack> kitDefaultInv = null;
            try {
                Object rawInv = null;
                try { rawInv = baseKit.getClass().getMethod("getInv").invoke(baseKit); }
                catch (Exception ignored) {}
                if (rawInv == null) {
                    try { rawInv = baseKit.getClass().getMethod("getInventory").invoke(baseKit); }
                    catch (Exception ignored) {}
                }
                if (rawInv instanceof List) {
                    kitDefaultInv = new ArrayList<>((List<ItemStack>) rawInv);
                } else if (rawInv instanceof ItemStack[]) {
                    kitDefaultInv = new ArrayList<>(java.util.Arrays.asList((ItemStack[]) rawInv));
                }
            } catch (Exception ignored) {}

            List<ItemStack> kitRemainder = (kitDefaultInv != null)
                    ? new ArrayList<>(kitDefaultInv) : new ArrayList<>();
            boolean hasKitReference = !kitRemainder.isEmpty();

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

                if (item != null && !isBook) {
                    ItemStack matchedKitItem = null;
                    for (int j = 0; j < kitRemainder.size(); j++) {
                        ItemStack kitItem = kitRemainder.get(j);
                        if (kitItem != null && plugin.isItemMatch(item, kitItem)) {
                            matchedKitItem = kitItem;
                            kitRemainder.remove(j);
                            break;
                        }
                    }

                    if (matchedKitItem != null) {
                        ItemStack toSave = item.clone();
                        toSave.setAmount(matchedKitItem.getAmount());
                        newInv.add(toSave);
                    } else if (!hasKitReference) {
                        newInv.add(item.clone());
                    } else {
                        newInv.add(null);
                    }
                } else {
                    newInv.add(null);
                }
            }

            if (!plugin.saveLayoutToPreferredStorage(p, baseKit, newInv)) {
                p.sendMessage(ChatColor.RED + "An error occurred while saving layout.");
                return true;
            }

            p.sendMessage(ChatColor.GREEN + "Kit layout successfully saved! It will be used in your next matches permanently.");

        } catch (Exception e) {
            p.sendMessage(ChatColor.RED + "An error occurred while saving layout.");
            e.printStackTrace();
        }

        return true;
    }
}
