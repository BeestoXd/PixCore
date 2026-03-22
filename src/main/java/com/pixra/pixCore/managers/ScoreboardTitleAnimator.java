package com.pixra.pixCore.managers;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;

public class ScoreboardTitleAnimator {

    private static final int TICKS_PER_FRAME = 5;

    private static final String[] FRAMES = {
        "&f&lP&a&lRACTICE",
        "&e&lP&f&lR&a&lACTICE",
        "&a&lP&e&lR&f&lA&a&lCTICE",
        "&a&lPR&e&lA&f&lC&a&lTICE",
        "&a&lPRA&e&lC&f&lT&a&lICE",
        "&a&lPRAC&e&lT&f&lI&a&lCE",
        "&a&lPRACT&e&lI&f&lC&a&lE",
        "&a&lPRACTI&e&lC&f&lE",
        "&a&lPRACTICE",
        "&a&lPRACTICE",
        "&a&lPRACTICE"
    };

    private BukkitTask task;

    public void start(Plugin plugin) {
        task = new BukkitRunnable() {
            private int    tick       = 0;
            private int    frameIndex = 0;
            private String current   = color(FRAMES[0]);

            @Override
            public void run() {

                if (tick % TICKS_PER_FRAME == 0) {
                    current    = color(FRAMES[frameIndex]);
                    frameIndex = (frameIndex + 1) % FRAMES.length;
                }
                tick++;

                for (Player player : Bukkit.getOnlinePlayers()) {
                    try {
                        Objective obj = player.getScoreboard()
                                              .getObjective(DisplaySlot.SIDEBAR);
                        if (obj != null) {
                            obj.setDisplayName(current);
                        }
                    } catch (Exception ignored) {}
                }
            }

            private String color(String s) {
                return ChatColor.translateAlternateColorCodes('&', s);
            }

        }.runTaskTimer(plugin, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
