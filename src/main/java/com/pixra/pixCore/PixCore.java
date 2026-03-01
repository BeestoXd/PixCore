package com.pixra.pixCore;

import com.pixra.pixCore.arena.ArenaBoundaryManager;
import com.pixra.pixCore.commands.LeaderboardCommand;
import com.pixra.pixCore.commands.SaveLayoutCommand;
import com.pixra.pixCore.listeners.*;
import com.pixra.pixCore.managers.*;
import com.pixra.pixCore.knockback.MLGRushKnockback;
import com.pixra.pixCore.party.PartySplitManager;
import com.pixra.pixCore.placeholders.PixCorePlaceholders;
import com.pixra.pixCore.respawn.RespawnManager;
import com.pixra.pixCore.commands.PixCommand;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

public class PixCore extends JavaPlugin {

    private Object strikePracticeAPI;
    private Method mGetFight;
    private Method mIsInFight;
    private Method mGetKit;
    private Method mGetOpponents;
    private Method mIsBed1Broken;
    private Method mIsBed2Broken;
    private Method mGetFirstPlayer;
    private Method mGetSecondPlayer;
    private Method mAddSpectator;
    private Method mRemoveSpectator;
    private Method mGetLastSelectedEditedKit;
    private Method mRespawnInFight;
    private Method mKitApply;
    private Method mGetArena;
    private Method mArenaGetMin;
    private Method mArenaGetMax;
    public boolean arenaReflectionLoaded = false;
    private Class<?> clsBestOfFight;
    private Method mGetBestOf;
    private Class<?> clsBestOf;
    private Method mGetCurrentRound;
    private Method mGetRounds;
    private Method mHandleWin;
    private Method mHandleDeath; // Ditambahkan untuk trigger kill/round end native SP
    public boolean bestOfReflectionLoaded = false;
    private Method mGetPlayers;
    private Method mGetTeammates;
    private Method mPlayersAreTeammates;
    private Method mPlayersAreOpponents;
    private Method mGetPlayersInFight;
    private Method mGetPlayerNames;
    private Class<?> clsAbstractFight;
    private Field fBed1Broken;
    private Field fBed2Broken;
    private Method mSetBedwars;

    private Method mBattleKitGetIcon;
    private Method mBattleKitGetKitStatic;
    private Method mBattleKitGiveKit;
    private Method mBattleKitGetInv;
    private Method mBattleKitGetHelmet;
    private Method mBattleKitGetChestplate;
    private Method mBattleKitGetLeggings;
    private Method mBattleKitGetBoots;

    private boolean isHooked = false;

    public int voidYLimit;
    public boolean buildRestrictionsEnabled;
    public int maxBuildY;
    public boolean checkArenaBorders;
    public List<String> buildRestrictionKits;
    public List<String> roundEndKits;
    public List<String> bowCooldownKits;

    public File tntTickFile;
    public FileConfiguration tntTickConfig;
    public EntityType tntEntityType;
    public boolean hasSetSource;

    private File noFallDamageFile;
    private FileConfiguration noFallDamageConfig;

    public boolean bowCooldownEnabled;
    public int bowCooldownSeconds;
    public int respawnCountdownInterval;
    public boolean bowBoostEnabled;
    public double bowBoostHorizontal;
    public double bowBoostVertical;

    public boolean startCountdownEnabled;
    public Map<Integer, String> startCountdownTitles;
    public int startCountdownDuration;
    public Map<Integer, String> startCountdownMessages;
    public String startMatchMessage;

    public List<String> deathDisabledKits;
    public List<String> instantRespawnKits;
    public List<String> deathDisabledArenas;

    public boolean startCountdownSoundEnabled;
    public float startCountdownVolume;
    public float startCountdownPitch;
    public Map<Integer, Sound> startCountdownSounds;
    public Sound startMatchSound;

    public boolean respawnChatCountdownEnabled;
    public Set<String> respawnChatCountdownKits;
    public Map<Integer, String> respawnChatNumbers;
    public String respawnChatFormat;

    public Sound respawnSound;
    public boolean soundEnabled = false;

    public RespawnManager respawnManager;
    public ArenaBoundaryManager arenaBoundaryManager;
    private BlockDisappearManager blockDisappearManager;
    private HitRewardManager hitRewardManager;
    public CustomKnockbackManager customKnockbackManager;
    private SnowballManager snowballManager;
    private TntMechanicsManager tntMechanicsManager;
    public BlockReplenishManager blockReplenishManager;
    private ItemMechanicsManager itemMechanicsManager;
    private NoFallDamageManager noFallDamageManager;
    public BowHitMessageManager bowHitMessageManager;
    private BedDestroyTitleManager bedDestroyTitleManager;
    public StickFightManager stickFightManager;
    private KillMessageManager killMessageManager;
    public HitActionBarManager hitActionBarManager;
    public MLGRushKnockback mlgrushKnockback;
    public PartySplitManager partySplitManager;

    public LeaderboardManager leaderboardManager;
    public HologramManager hologramManager;
    public LeaderboardGUIManager leaderboardGUIManager;

    public final Set<UUID> frozenPlayers = new HashSet<>();
    public boolean isCountdownRunning = false;
    public final Map<UUID, Long> bowCooldowns = new HashMap<>();
    public final Set<UUID> titleCooldown = new HashSet<>();
    public final Map<UUID, BukkitTask> activeCountdowns = new HashMap<>();
    public final Map<UUID, Long> msgCooldowns = new HashMap<>();
    public final Map<UUID, Long> deathMessageCooldowns = new HashMap<>();
    public final Map<UUID, String> lastBroadcastMessage = new HashMap<>();
    public final Map<UUID, Long> lastBroadcastTime = new HashMap<>();
    public final Map<UUID, UUID> lastDamager = new HashMap<>();
    public final Map<UUID, Long> lastDamageTime = new HashMap<>();
    public final Map<UUID, Location> arenaSpawnLocations = new HashMap<>();
    public final Map<Object, UUID> endedFightWinners = new HashMap<>();
    public final Map<UUID, String> playerMatchResults = new HashMap<>();
    public final Map<UUID, Integer> playerMatchKills = new HashMap<>();
    public final Map<UUID, Long> killCountCooldown = new HashMap<>();
    public final Map<Object, Map<UUID, Integer>> matchScores = new HashMap<>();

    public Object getStrikePracticeAPI() { return strikePracticeAPI; }
    public Method getMGetKit() { return mGetKit; }

    public Method getMGetFirstPlayer() { return mGetFirstPlayer; }
    public Method getMGetSecondPlayer() { return mGetSecondPlayer; }
    public Method getMPlayersAreTeammates() { return mPlayersAreTeammates; }
    public Method getMGetTeammates() { return mGetTeammates; }
    public Method getMGetOpponents() { return mGetOpponents; }
    public Method getMGetPlayersInFight() { return mGetPlayersInFight; }
    public Method getMGetPlayers() { return mGetPlayers; }
    public Method getMGetFight() { return mGetFight; }
    public Method getMGetArena() { return mGetArena; }
    public Method getMIsInFight() { return mIsInFight; }
    public Method getMIsBed1Broken() { return mIsBed1Broken; }
    public Method getMIsBed2Broken() { return mIsBed2Broken; }
    public Method getMSetBedwars() { return mSetBedwars; }
    public Class<?> getClsAbstractFight() { return clsAbstractFight; }
    public Field getFBed1Broken() { return fBed1Broken; }
    public Field getFBed2Broken() { return fBed2Broken; }
    public Class<?> getClsBestOfFight() { return clsBestOfFight; }
    public Method getMGetBestOf() { return mGetBestOf; }
    public Method getMHandleWin() { return mHandleWin; }
    public Method getMHandleDeath() { return mHandleDeath; }
    public boolean isHooked() { return isHooked; }
    public Map<Object, UUID> getEndedFightWinners() { return endedFightWinners; }
    public Map<UUID, String> getPlayerMatchResults() { return playerMatchResults; }
    public Map<UUID, Integer> getPlayerMatchKills() { return playerMatchKills; }
    public FileConfiguration getNoFallDamageConfig() { return noFallDamageConfig; }
    public BedDestroyTitleManager getBedDestroyTitleManager() { return bedDestroyTitleManager; }
    public KillMessageManager getKillMessageManager() { return killMessageManager; }
    public Map<UUID, BukkitTask> getActiveCountdowns() { return activeCountdowns; }
    public Map<UUID, Location> getArenaSpawnLocations() { return arenaSpawnLocations; }

    @Override
    public void onEnable() {
        this.respawnManager = new RespawnManager(this);
        this.blockDisappearManager = new BlockDisappearManager(this);
        this.hitRewardManager = new HitRewardManager(this);
        this.customKnockbackManager = new CustomKnockbackManager(this);
        this.snowballManager = new SnowballManager(this);
        this.tntMechanicsManager = new TntMechanicsManager(this);
        this.blockReplenishManager = new BlockReplenishManager(this);
        this.itemMechanicsManager = new ItemMechanicsManager(this);
        this.noFallDamageManager = new NoFallDamageManager(this);
        this.bowHitMessageManager = new BowHitMessageManager(this);
        this.bedDestroyTitleManager = new BedDestroyTitleManager(this);
        this.stickFightManager = new StickFightManager(this);
        this.killMessageManager = new KillMessageManager(this);
        this.hitActionBarManager = new HitActionBarManager(this);
        this.mlgrushKnockback = new MLGRushKnockback(this);
        this.partySplitManager = new PartySplitManager(this);

        this.leaderboardManager = new LeaderboardManager(this);
        this.hologramManager = new HologramManager(this);
        this.leaderboardGUIManager = new LeaderboardGUIManager(this);

        clearAllCaches();

        new BukkitRunnable() {
            @Override
            public void run() {
                if (leaderboardManager != null) {
                    leaderboardManager.backupData("auto");
                }
            }
        }.runTaskTimerAsynchronously(this, 72000L, 72000L);

        saveDefaultConfig();
        loadConfigValues();
        loadTntTickConfig();
        loadNoFallDamageConfig();

        setupTntVariables();

        if (getCommand("pix") != null) {
            getCommand("pix").setExecutor(new PixCommand(this));
        }

        if (getCommand("savelayout") != null) {
            getCommand("savelayout").setExecutor(new SaveLayoutCommand(this));
        }

        if (getCommand("leaderboard") != null) {
            getCommand("leaderboard").setExecutor(new LeaderboardCommand(this));
        }

        hookStrikePractice();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PixCorePlaceholders(this).register();
        }

        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new MatchListener(this), this);
        getServer().getPluginManager().registerEvents(new BedListener(this), this);
        getServer().getPluginManager().registerEvents(new GameplayListener(this), this);

        getServer().getPluginManager().registerEvents(this.blockDisappearManager, this);
        getServer().getPluginManager().registerEvents(this.hitRewardManager, this);
        getServer().getPluginManager().registerEvents(this.customKnockbackManager, this);
        getServer().getPluginManager().registerEvents(this.snowballManager, this);
        getServer().getPluginManager().registerEvents(this.tntMechanicsManager, this);
        getServer().getPluginManager().registerEvents(this.blockReplenishManager, this);
        getServer().getPluginManager().registerEvents(this.itemMechanicsManager, this);
        getServer().getPluginManager().registerEvents(this.noFallDamageManager, this);
        getServer().getPluginManager().registerEvents(this.bowHitMessageManager, this);
        getServer().getPluginManager().registerEvents(this.stickFightManager, this);
        getServer().getPluginManager().registerEvents(this.hitActionBarManager, this);
        getServer().getPluginManager().registerEvents(this.mlgrushKnockback, this);
        getServer().getPluginManager().registerEvents(this.partySplitManager, this);
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        clearAllCaches();
        if (this.hologramManager != null) {
            this.hologramManager.removeAllHolograms();
        }
    }

    public void clearAllCaches() {
        activeCountdowns.values().forEach(BukkitTask::cancel);
        activeCountdowns.clear();
        titleCooldown.clear();
        msgCooldowns.clear();
        lastDamager.clear();
        lastDamageTime.clear();
        bowCooldowns.clear();
        deathMessageCooldowns.clear();
        lastBroadcastMessage.clear();
        lastBroadcastTime.clear();
        arenaSpawnLocations.clear();
        endedFightWinners.clear();
        playerMatchResults.clear();
        playerMatchKills.clear();
        killCountCooldown.clear();
        matchScores.clear();
    }

    private void setupTntVariables() {
        try {
            tntEntityType = EntityType.valueOf("PRIMED_TNT");
        } catch (IllegalArgumentException e) {
            tntEntityType = EntityType.valueOf("TNT");
        }

        try {
            org.bukkit.entity.TNTPrimed.class.getMethod("setSource", org.bukkit.entity.Entity.class);
            hasSetSource = true;
        } catch (NoSuchMethodException e) {
            hasSetSource = false;
        }
    }

    private void hookStrikePractice() {
        if (Bukkit.getPluginManager().getPlugin("StrikePractice") == null) return;
        try {
            Class<?> mainClass = Class.forName("ga.strikepractice.StrikePractice");
            this.strikePracticeAPI = mainClass.getMethod("getAPI").invoke(null);
            Class<?> apiClass = this.strikePracticeAPI.getClass();

            this.mGetFight = apiClass.getMethod("getFight", Player.class);
            this.mIsInFight = apiClass.getMethod("isInFight", Player.class);
            this.mGetKit = apiClass.getMethod("getKit", Player.class);
            this.mAddSpectator = apiClass.getMethod("addSpectator", Player.class);
            this.mRemoveSpectator = apiClass.getMethod("removeSpectator", Player.class, boolean.class);
            this.mGetLastSelectedEditedKit = apiClass.getMethod("getLastSelectedEditedKit", Player.class);
            this.mRespawnInFight = apiClass.getMethod("respawnInFight", Player.class);

            try { this.mKitApply = mGetKit.getReturnType().getMethod("giveKit", Player.class); } catch (Exception ex) { this.mKitApply = null; }

            Class<?> fightClass = Class.forName("ga.strikepractice.fights.Fight");
            this.mGetOpponents = fightClass.getMethod("getOpponents", Player.class);
            try { this.mHandleDeath = fightClass.getMethod("handleDeath", Player.class); } catch (Exception ignored) {}
            try { this.mGetTeammates = fightClass.getMethod("getTeammates", Player.class); } catch (Exception ignored) {}
            try { this.mPlayersAreTeammates = fightClass.getMethod("playersAreTeammates", Player.class, Player.class); } catch (Exception ignored) {}
            try { this.mPlayersAreOpponents = fightClass.getMethod("playersAreOpponents", Player.class, Player.class); } catch (Exception ignored) {}
            try { this.mGetPlayers = fightClass.getMethod("getPlayersInFight"); } catch (Exception e) { try { this.mGetPlayers = fightClass.getMethod("getPlayers"); } catch (Exception ignored) {} }
            try { this.mGetPlayersInFight = fightClass.getMethod("getPlayersInFight"); } catch (Exception ignored) {}
            try { this.mGetPlayerNames = fightClass.getMethod("getPlayerNames"); } catch (Exception ignored) {}
            try { this.mGetArena = fightClass.getMethod("getArena"); } catch (Exception e) { try { this.mGetArena = Class.forName("ga.strikepractice.fights.AbstractFight").getMethod("getArena"); } catch (Exception ignored) {} }

            try {
                Class<?> arenaClass = Class.forName("ga.strikepractice.fights.arena.Arena");
                this.mArenaGetMin = arenaClass.getMethod("getMin");
                this.mArenaGetMax = arenaClass.getMethod("getMax");
                this.arenaReflectionLoaded = true;
            } catch (Exception e) { this.arenaReflectionLoaded = false; }

            try {
                this.clsBestOfFight = Class.forName("ga.strikepractice.fights.BestOfFight");
                this.mGetBestOf = this.clsBestOfFight.getMethod("getBestOf");
                this.clsBestOf = Class.forName("ga.strikepractice.fights.duel.BestOf");
                this.mGetCurrentRound = this.clsBestOf.getMethod("getCurrentRound");
                this.mGetRounds = this.clsBestOf.getMethod("getRounds");
                this.mHandleWin = this.clsBestOf.getMethod("handleWin", UUID.class);
                this.bestOfReflectionLoaded = true;
            } catch (Exception e) { this.bestOfReflectionLoaded = false; }

            try {
                this.clsAbstractFight = Class.forName("ga.strikepractice.fights.AbstractFight");
                this.mIsBed1Broken = this.clsAbstractFight.getMethod("isBed1Broken");
                this.mIsBed2Broken = this.clsAbstractFight.getMethod("isBed2Broken");
                this.fBed1Broken = this.clsAbstractFight.getDeclaredField("bed1Broken");
                this.fBed1Broken.setAccessible(true);
                this.fBed2Broken = this.clsAbstractFight.getDeclaredField("bed2Broken");
                this.fBed2Broken.setAccessible(true);
                Class<?> duelClass = Class.forName("ga.strikepractice.fights.duel.Duel");
                this.mGetFirstPlayer = duelClass.getMethod("getFirstPlayer");
                try { this.mGetSecondPlayer = duelClass.getMethod("getSecondPlayer"); } catch (Exception ignored) {}
            } catch (Exception ex) {}

            try {
                Class<?> battleKitClass = Class.forName("ga.strikepractice.battlekit.BattleKit");
                this.mSetBedwars = battleKitClass.getMethod("setBedwars", boolean.class);
                this.mBattleKitGetIcon = battleKitClass.getMethod("getIcon");
                this.mBattleKitGetKitStatic = battleKitClass.getMethod("getKit", Player.class, ItemStack.class, boolean.class);
                this.mBattleKitGiveKit = battleKitClass.getMethod("giveKit", Player.class);
                this.mBattleKitGetInv = battleKitClass.getMethod("getInv");
                this.mBattleKitGetHelmet = battleKitClass.getMethod("getHelmet");
                this.mBattleKitGetChestplate = battleKitClass.getMethod("getChestplate");
                this.mBattleKitGetLeggings = battleKitClass.getMethod("getLeggings");
                this.mBattleKitGetBoots = battleKitClass.getMethod("getBoots");
            } catch (Exception ex) {}

            if (arenaReflectionLoaded) {
                this.arenaBoundaryManager = new ArenaBoundaryManager(strikePracticeAPI, mGetFight, mGetArena, mArenaGetMin, mArenaGetMax);
            }
            this.isHooked = true;

        } catch (Exception e) {
            this.isHooked = false;
        }
    }

    public void loadConfigValues() {
        reloadConfig();
        this.voidYLimit = getConfig().getInt("settings.void-y-trigger", -64);
        this.buildRestrictionsEnabled = getConfig().getBoolean("settings.build-restrictions.enabled", true);
        this.maxBuildY = getConfig().getInt("settings.build-restrictions.max-build-y", 100);
        this.checkArenaBorders = getConfig().getBoolean("settings.build-restrictions.check-arena-borders", true);
        this.buildRestrictionKits = getConfig().getStringList("settings.build-restrictions.kits");
        this.roundEndKits = getConfig().getStringList("settings.round-end-kits");
        this.bowCooldownKits = getConfig().getStringList("settings.bow-cooldown.kits");
        this.deathDisabledKits = getConfig().getStringList("settings.death.disabled-kits");
        this.instantRespawnKits = getConfig().getStringList("settings.death.instant-respawn-kits");
        this.respawnChatCountdownEnabled = getConfig().getBoolean("settings.respawn.chat-countdown.enabled", false);
        this.respawnChatFormat = getConfig().getString("settings.respawn.chat-countdown.format", "&eRespawning in &r<number>");
        this.respawnChatCountdownKits = new HashSet<>(getConfig().getStringList("settings.respawn.chat-countdown.kits"));

        this.respawnChatNumbers = new HashMap<>();
        ConfigurationSection respawnNumberSection = getConfig().getConfigurationSection("settings.respawn.chat-countdown.numbers");
        if (respawnNumberSection != null) {
            for (String key : respawnNumberSection.getKeys(false)) {
                try { respawnChatNumbers.put(Integer.parseInt(key), respawnNumberSection.getString(key)); } catch (Exception ignored) {}
            }
        }

        this.startCountdownSounds = new HashMap<>();
        this.startCountdownSoundEnabled = getConfig().getBoolean("settings.start-countdown.sounds.enabled", false);
        this.startCountdownVolume = (float) getConfig().getDouble("settings.start-countdown.sounds.volume", 1.0);
        this.startCountdownPitch = (float) getConfig().getDouble("settings.start-countdown.sounds.pitch", 1.0);

        ConfigurationSection soundSection = getConfig().getConfigurationSection("settings.start-countdown.sounds.per-second");
        if (soundSection != null) {
            for (String key : soundSection.getKeys(false)) {
                try {
                    Sound sound = getSoundByName(soundSection.getString(key));
                    if (sound != null) startCountdownSounds.put(Integer.parseInt(key), sound);
                } catch (Exception e) {}
            }
        }

        this.startMatchSound = getSoundByName(getConfig().getString("settings.start-countdown.sounds.start-sound", null));
        this.respawnCountdownInterval = getConfig().getInt("settings.respawn-countdown-interval", 20);
        this.bowCooldownEnabled = getConfig().getBoolean("settings.bow-cooldown.enabled", false);
        this.bowCooldownSeconds = getConfig().getInt("settings.bow-cooldown.seconds", 3);
        this.bowBoostEnabled = getConfig().getBoolean("settings.bow-boost.enabled", true);
        this.bowBoostHorizontal = getConfig().getDouble("settings.bow-boost.horizontal", 1.35);
        this.bowBoostVertical = getConfig().getDouble("settings.bow-boost.vertical", 0.42);
        this.startCountdownDuration = getConfig().getInt("settings.start-countdown.duration", 5);

        this.startCountdownMessages = new HashMap<>();
        ConfigurationSection msgSection = getConfig().getConfigurationSection("settings.start-countdown.messages");
        if (msgSection != null) {
            for (String key : msgSection.getKeys(false)) {
                try { startCountdownMessages.put(Integer.parseInt(key), ChatColor.translateAlternateColorCodes('&', msgSection.getString(key))); } catch (Exception e) {}
            }
        }

        this.startMatchMessage = ChatColor.translateAlternateColorCodes('&', getConfig().getString("settings.start-countdown.start-message", "&aMatch Started!"));
        this.startCountdownEnabled = getConfig().getBoolean("settings.start-countdown.enabled", false);

        this.startCountdownTitles = new HashMap<>();
        ConfigurationSection section = getConfig().getConfigurationSection("settings.start-countdown.titles");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try { startCountdownTitles.put(Integer.parseInt(key), ChatColor.translateAlternateColorCodes('&', section.getString(key))); } catch (Exception e) {}
            }
        }

        String soundName = getConfig().getString("settings.respawn-sound", "NOTE_PLING");
        this.respawnSound = getSoundByName(soundName);
        if(this.respawnSound == null && soundName.equalsIgnoreCase("ORB_PICKUP")) {
            this.respawnSound = getSoundByName("ENTITY_EXPERIENCE_ORB_PICKUP");
        }
        this.soundEnabled = (this.respawnSound != null);
    }

    public void loadTntTickConfig() {
        tntTickFile = new File(getDataFolder(), "tnttick.yml");
        if (!tntTickFile.exists()) {
            tntTickFile.getParentFile().mkdirs();
            saveResource("tnttick.yml", false);
        }
        tntTickConfig = YamlConfiguration.loadConfiguration(tntTickFile);
    }

    public void loadNoFallDamageConfig() {
        noFallDamageFile = new File(getDataFolder(), "nofalldamage.yml");
        if (!noFallDamageFile.exists()) {
            noFallDamageFile.getParentFile().mkdirs();
            saveResource("nofalldamage.yml", false);
        }
        noFallDamageConfig = YamlConfiguration.loadConfiguration(noFallDamageFile);
    }

    public Sound getSoundByName(String name) {
        if (name == null) return null;
        try { return (Sound) Sound.class.getField(name.toUpperCase()).get(null); } catch (Exception e) { return null; }
    }

    public boolean isInFight(Player player) {
        if (!isHooked || strikePracticeAPI == null || mIsInFight == null) return false;
        try { return (boolean) mIsInFight.invoke(strikePracticeAPI, player); } catch (Exception e) { return false; }
    }

    public String getKitName(Player player) {
        if (!isHooked || mGetKit == null || strikePracticeAPI == null) return null;
        try {
            Object kit = mGetKit.invoke(strikePracticeAPI, player);
            if (kit != null) return (String) kit.getClass().getMethod("getName").invoke(kit);
        } catch (Exception e) {}
        return null;
    }

    public void addSpectator(Player p) {
        if (!isHooked || mAddSpectator == null) return;
        try { mAddSpectator.invoke(strikePracticeAPI, p); } catch (Exception e) {}
    }

    public void removeSpectator(Player p, boolean clearAndTeleport) {
        if (!isHooked || mRemoveSpectator == null) return;
        try { mRemoveSpectator.invoke(strikePracticeAPI, p, clearAndTeleport); } catch (Exception e) {}
    }

    public void respawnInFight(Player p) {
        if (!isHooked || mRespawnInFight == null) return;
        try { mRespawnInFight.invoke(strikePracticeAPI, p); } catch (Exception e) {}
    }

    public Color getTeamColor(Player p) {
        if (!isHooked || mGetFight == null) return Color.BLUE;
        try {
            Object fight = mGetFight.invoke(strikePracticeAPI, p);
            if (fight == null) return Color.BLUE;

            if (mGetFirstPlayer != null) {
                Player p1 = (Player) mGetFirstPlayer.invoke(fight);
                if (p1 != null && p1.getUniqueId().equals(p.getUniqueId())) return Color.BLUE;
            }
            if (mGetSecondPlayer != null) {
                Player p2 = (Player) mGetSecondPlayer.invoke(fight);
                if (p2 != null && p2.getUniqueId().equals(p.getUniqueId())) return Color.RED;
            }

            List<Player> players = null;
            if (mGetPlayersInFight != null) players = (List<Player>) mGetPlayersInFight.invoke(fight);
            if ((players == null || players.isEmpty()) && mGetPlayers != null) players = (List<Player>) mGetPlayers.invoke(fight);

            if (players != null && !players.isEmpty()) {
                Player rep = players.get(0);
                if (rep.getUniqueId().equals(p.getUniqueId())) return Color.BLUE;

                if (mPlayersAreTeammates != null) {
                    boolean isTeammate = (boolean) mPlayersAreTeammates.invoke(fight, rep, p);
                    return isTeammate ? Color.BLUE : Color.RED;
                }
            }
        } catch (Exception e) {}
        return Color.BLUE;
    }

    public void colorItem(ItemStack item, Color color) {
        if (item == null || item.getType() == org.bukkit.Material.AIR) return;
        String name = item.getType().name();

        if (name.contains("LEATHER_")) {
            LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
            meta.setColor(color);
            item.setItemMeta(meta);
        } else if (name.equals("WOOL") || name.equals("STAINED_CLAY") || name.equals("STAINED_GLASS") || name.equals("CARPET")) {
            byte data = (color == Color.BLUE) ? (byte) 11 : (byte) 14;
            item.setDurability(data);
        }
    }

    private boolean isItemMatch(ItemStack target, ItemStack poolItem) {
        if (target.getType() != poolItem.getType()) return false;
        String typeName = target.getType().name();

        if (typeName.contains("WOOL") || typeName.contains("CLAY") || typeName.contains("GLASS") || typeName.contains("CARPET")) {
            return true;
        }
        if (typeName.contains("POTION") || typeName.contains("INK_SACK")) {
            return target.getDurability() == poolItem.getDurability();
        }

        return true;
    }

    @SuppressWarnings("unchecked")
    public void applyStartKit(Player p) {
        if (!isHooked || mGetKit == null || strikePracticeAPI == null) return;
        try {
            Object baseKit = mGetKit.invoke(strikePracticeAPI, p);
            if (baseKit == null) return;

            String baseName = ChatColor.stripColor((String) baseKit.getClass().getMethod("getName").invoke(baseKit)).toLowerCase();
            ItemStack baseIcon = null;
            try { baseIcon = (ItemStack) mBattleKitGetIcon.invoke(baseKit); } catch (Exception ignored) {}
            String baseIconName = (baseIcon != null && baseIcon.hasItemMeta() && baseIcon.getItemMeta().hasDisplayName())
                    ? ChatColor.stripColor(baseIcon.getItemMeta().getDisplayName()).toLowerCase() : null;

            // SISTEM BARU: Deteksi jika inventory HANYA berisi buku kit editor / item lobi
            boolean hasActualItems = false;
            for (ItemStack item : p.getInventory().getContents()) {
                if (item != null && item.getType() != org.bukkit.Material.AIR) {
                    String type = item.getType().name();
                    boolean isLobbyItem = false;
                    if (type.contains("BOOK") || type.contains("BED") || type.contains("NAME_TAG") || type.contains("PAPER") || type.contains("EMERALD") || type.contains("COMPASS") || type.contains("WATCH") || type.contains("CLOCK") || type.contains("CHEST") || type.contains("SLIME")) {
                        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                            String name = ChatColor.stripColor(item.getItemMeta().getDisplayName()).toLowerCase();
                            if (name.contains("layout") || name.contains("kit") || name.contains("default") || name.contains("edit") || name.contains("leave") || name.contains("spectate") || name.contains("play") || name.contains("options") || (baseIconName != null && name.equals(baseIconName))) {
                                isLobbyItem = true;
                            }
                        }
                    }
                    if (!isLobbyItem) {
                        hasActualItems = true;
                        break;
                    }
                }
            }

            // Jika isinya KOSONG atau HANYA ADA BUKU LOBI, kita hapus lalu panggil kit secara paksa!
            if (!hasActualItems) {
                p.getInventory().clear();
                if (mBattleKitGiveKit != null) {
                    mBattleKitGiveKit.invoke(baseKit, p);
                } else if (mKitApply != null) {
                    mKitApply.invoke(baseKit, p);
                }
                p.updateInventory();
            }

            ItemStack helmet = null, chest = null, legs = null, boots = null;
            try {
                if (mBattleKitGetHelmet != null) helmet = (ItemStack) mBattleKitGetHelmet.invoke(baseKit);
                if (mBattleKitGetChestplate != null) chest = (ItemStack) mBattleKitGetChestplate.invoke(baseKit);
                if (mBattleKitGetLeggings != null) legs = (ItemStack) mBattleKitGetLeggings.invoke(baseKit);
                if (mBattleKitGetBoots != null) boots = (ItemStack) mBattleKitGetBoots.invoke(baseKit);
            } catch (Exception ignored) {}

            List<Object> allKits = new ArrayList<>();

            // 1. Load StrikePractice native kits as fallback
            File spFolder = new File(getDataFolder().getParentFile(), "StrikePractice");
            File pdFile = new File(spFolder, "playerdata/" + p.getUniqueId().toString() + ".yml");
            if (pdFile.exists()) {
                YamlConfiguration pdConfig = YamlConfiguration.loadConfiguration(pdFile);
                List<?> spKits = pdConfig.getList("kits");
                if (spKits != null) {
                    allKits.addAll(spKits);
                }
            }

            // 2. Load PixCore layouts and PREPEND them so they are checked first!
            File pixFile = new File(getDataFolder(), "layouts/" + p.getUniqueId().toString() + ".yml");
            if (pixFile.exists()) {
                YamlConfiguration pixConfig = YamlConfiguration.loadConfiguration(pixFile);
                List<?> pixKits = pixConfig.getList("kits");
                if (pixKits != null) {
                    for (int i = pixKits.size() - 1; i >= 0; i--) {
                        allKits.add(0, pixKits.get(i));
                    }
                }
            }

            if (!allKits.isEmpty()) {
                for (Object customKit : allKits) {
                    if (customKit == null) continue;

                    boolean isMatch = false;
                    List<ItemStack> yamlInv = null;

                    if (!Map.class.isAssignableFrom(customKit.getClass())) {
                        ItemStack icon = null;
                        try { icon = (ItemStack) mBattleKitGetIcon.invoke(customKit); } catch (Exception ignored) {}
                        String dName = (icon != null && icon.hasItemMeta() && icon.getItemMeta().hasDisplayName()) ? ChatColor.stripColor(icon.getItemMeta().getDisplayName()).toLowerCase() : "";
                        String kName = "";
                        try { kName = ChatColor.stripColor((String) customKit.getClass().getMethod("getName").invoke(customKit)).toLowerCase(); } catch (Exception ignored) {}

                        if (dName.contains(baseName) || kName.contains(baseName)) {
                            isMatch = true;
                            try { yamlInv = (List<ItemStack>) customKit.getClass().getMethod("getInventory").invoke(customKit); } catch (Exception ignored) {}
                        }
                    } else {
                        Map<?, ?> map = (Map<?, ?>) customKit;
                        ItemStack icon = map.get("icon") instanceof ItemStack ? (ItemStack) map.get("icon") : null;
                        String dName = (icon != null && icon.hasItemMeta() && icon.getItemMeta().hasDisplayName()) ? ChatColor.stripColor(icon.getItemMeta().getDisplayName()).toLowerCase() : "";
                        String kName = map.containsKey("name") ? ChatColor.stripColor(String.valueOf(map.get("name"))).toLowerCase() : "";

                        if (dName.contains(baseName) || kName.contains(baseName)) {
                            isMatch = true;
                            if (map.get("inventory") instanceof List) {
                                yamlInv = (List<ItemStack>) map.get("inventory");
                            }
                        }
                    }

                    if (isMatch && yamlInv != null) {
                        ItemStack[] currentContents = p.getInventory().getContents();
                        List<ItemStack> pool = new ArrayList<>();

                        for (int i = 0; i < 36 && i < currentContents.length; i++) {
                            ItemStack item = currentContents[i];
                            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                                boolean isBook = false;
                                if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                                    String name = ChatColor.stripColor(item.getItemMeta().getDisplayName()).toLowerCase();
                                    if (baseIconName != null && name.equals(baseIconName)) isBook = true;
                                    String type = item.getType().name();
                                    if (type.contains("BOOK") || type.contains("BED") || type.contains("NAME_TAG") || type.contains("PAPER") || type.contains("EMERALD")) {
                                        if (name.contains("layout") || name.contains("kit") || name.contains("default") || name.contains("#") || name.contains("edit")) {
                                            isBook = true;
                                        }
                                    }
                                }
                                if (!isBook) {
                                    pool.add(item.clone());
                                }
                            }
                        }

                        ItemStack[] newContents = new ItemStack[currentContents.length];
                        for (int i = 0; i < yamlInv.size() && i < newContents.length; i++) {
                            ItemStack target = yamlInv.get(i);
                            if (target == null || target.getType() == org.bukkit.Material.AIR) continue;

                            ItemStack matched = null;
                            for (int j = 0; j < pool.size(); j++) {
                                ItemStack poolItem = pool.get(j);
                                if (isItemMatch(target, poolItem)) {
                                    matched = poolItem;
                                    pool.remove(j);
                                    break;
                                }
                            }

                            if (matched != null) {
                                newContents[i] = matched;
                            } else {
                                // Cegah buku tertempel jika kita sedang merestore layout TheBridge
                                boolean isBook = false;
                                if (target.getType().name().contains("BOOK") || target.getType().name().contains("NAME_TAG") || target.getType().name().contains("PAPER")) {
                                    if (target.hasItemMeta() && target.getItemMeta().hasDisplayName()) {
                                        String name = ChatColor.stripColor(target.getItemMeta().getDisplayName()).toLowerCase();
                                        if (name.contains("layout") || name.contains("kit") || name.contains("default") || name.contains("edit") || (baseIconName != null && name.equals(baseIconName))) {
                                            isBook = true;
                                        }
                                    }
                                }
                                if (!isBook) {
                                    newContents[i] = target.clone();
                                }
                            }
                        }

                        for (ItemStack leftover : pool) {
                            for (int i = 0; i < newContents.length; i++) {
                                if (newContents[i] == null) {
                                    newContents[i] = leftover;
                                    break;
                                }
                            }
                        }

                        p.getInventory().setContents(newContents);
                        break;
                    }
                }
            }

            Color teamColor = getTeamColor(p);

            ItemStack[] contents = p.getInventory().getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack item = contents[i];
                if (item != null && item.getType() != org.bukkit.Material.AIR) {
                    boolean isBook = false;
                    if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                        String name = ChatColor.stripColor(item.getItemMeta().getDisplayName()).toLowerCase();
                        if (baseIconName != null && name.equals(baseIconName)) isBook = true;
                        String type = item.getType().name();
                        if (type.contains("BOOK") || type.contains("BED") || type.contains("NAME_TAG") || type.contains("PAPER") || type.contains("EMERALD")) {
                            if (name.contains("layout") || name.contains("kit") || name.contains("default") || name.contains("#") || name.contains("edit")) {
                                isBook = true;
                            }
                        }
                    }
                    if (isBook) {
                        contents[i] = null;
                    } else {
                        colorItem(item, teamColor);
                    }
                }
            }
            p.getInventory().setContents(contents);

            ItemStack[] armor = new ItemStack[4];
            armor[0] = (boots != null) ? boots.clone() : p.getInventory().getBoots();
            armor[1] = (legs != null) ? legs.clone() : p.getInventory().getLeggings();
            armor[2] = (chest != null) ? chest.clone() : p.getInventory().getChestplate();
            armor[3] = (helmet != null) ? helmet.clone() : p.getInventory().getHelmet();

            for (int i = 0; i < armor.length; i++) {
                colorItem(armor[i], teamColor);
            }
            p.getInventory().setArmorContents(armor);

            p.updateInventory();

        } catch (Exception e) { e.printStackTrace(); }
    }

    public void applyKit(Player p) {
        applyStartKit(p);
    }

    public void cleanupPlayer(UUID uid, boolean isQuit) {
        if (activeCountdowns.containsKey(uid)) {
            activeCountdowns.get(uid).cancel();
            activeCountdowns.remove(uid);
        }
        titleCooldown.remove(uid);
        msgCooldowns.remove(uid);
        lastDamager.remove(uid);
        lastDamageTime.remove(uid);
        bowCooldowns.remove(uid);
        killCountCooldown.remove(uid);
        frozenPlayers.remove(uid); // DITAMBAHKAN AGAR LEPAS DARI FREEZE JIKA KELUAR ATAU BERSIH
        if (isQuit) {
            deathMessageCooldowns.remove(uid);
            lastBroadcastMessage.remove(uid);
            lastBroadcastTime.remove(uid);
            arenaSpawnLocations.remove(uid);
            playerMatchKills.remove(uid);
        }
        if (respawnManager != null) {
            respawnManager.forceStop(Bukkit.getPlayer(uid));
        }
    }

    public void sendCooldownMessage(Player player, String configKey) {
        long lastMsgTime = msgCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (System.currentTimeMillis() - lastMsgTime > 500) {
            String msg = getMsg(configKey);
            if ((msg == null || msg.isEmpty()) && configKey.equals("bed-break-self")) {
                msg = ChatColor.RED + "You can't break your own bed!";
            }
            if (msg != null && !msg.isEmpty()) {
                player.sendMessage(msg);
            }
            msgCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    public String getMsg(String path) {
        if (getConfig().contains(path)) return ChatColor.translateAlternateColorCodes('&', getConfig().getString(path));
        if (getConfig().contains("messages." + path)) return ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages." + path));
        return "";
    }

    public void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (title == null) title = "";
        if (subtitle == null) subtitle = "";
        try {
            player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
        } catch (NoSuchMethodError e) {
            try { sendTitleNMS(player, title, subtitle, fadeIn, stay, fadeOut); } catch (Exception ex) {}
        }
    }

    private void sendTitleNMS(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) throws Exception {
        Class<?> packetPlayOutTitle = getNMSClass("PacketPlayOutTitle");
        Class<?> iChatBaseComponent = getNMSClass("IChatBaseComponent");
        Class<?> enumTitleAction = packetPlayOutTitle.getDeclaredClasses()[0];
        Object chatTitle = iChatBaseComponent.getDeclaredClasses()[0].getMethod("a", String.class).invoke(null, "{\"text\":\"" + title + "\"}");
        Object chatSubtitle = iChatBaseComponent.getDeclaredClasses()[0].getMethod("a", String.class).invoke(null, "{\"text\":\"" + subtitle + "\"}");
        Constructor<?> titleConstructor = packetPlayOutTitle.getConstructor(enumTitleAction, iChatBaseComponent, int.class, int.class, int.class);

        Object packetTimes = titleConstructor.newInstance(enumTitleAction.getField("TIMES").get(null), null, fadeIn, stay, fadeOut);
        sendPacket(player, packetTimes);
        Object packetTitle = titleConstructor.newInstance(enumTitleAction.getField("TITLE").get(null), chatTitle, fadeIn, stay, fadeOut);
        sendPacket(player, packetTitle);
        Object packetSubtitle = titleConstructor.newInstance(enumTitleAction.getField("SUBTITLE").get(null), chatSubtitle, fadeIn, stay, fadeOut);
        sendPacket(player, packetSubtitle);
    }

    private void sendPacket(Player player, Object packet) {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object playerConnection = handle.getClass().getField("playerConnection").get(handle);
            playerConnection.getClass().getMethod("sendPacket", getNMSClass("Packet")).invoke(playerConnection, packet);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private Class<?> getNMSClass(String name) {
        String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
        try { return Class.forName("net.minecraft.server." + version + "." + name); } catch (ClassNotFoundException e) { return null; }
    }

    @SuppressWarnings("unchecked")
    public String getTeamColorCode(Player p, Object fight) {
        if (fight != null) {
            try {
                Player rep = null;
                if (mGetFirstPlayer != null) {
                    try { rep = (Player) mGetFirstPlayer.invoke(fight); } catch (Exception ignored) {}
                }
                if (rep == null && mGetPlayers != null) {
                    List<Player> players = (List<Player>) mGetPlayers.invoke(fight);
                    if (players != null && !players.isEmpty()) rep = players.get(0);
                }
                if (rep != null) {
                    if (p.getUniqueId().equals(rep.getUniqueId())) return "§9";
                    boolean isTeammate = false;
                    if (mPlayersAreTeammates != null) {
                        isTeammate = (boolean) mPlayersAreTeammates.invoke(fight, rep, p);
                    } else if (mGetTeammates != null) {
                        List<String> teammates = (List<String>) mGetTeammates.invoke(fight, rep);
                        if (teammates != null && teammates.contains(p.getName())) isTeammate = true;
                    }
                    return isTeammate ? "§9" : "§c";
                }
            } catch (Exception e) {}
        }
        return getArmorColorCode(p);
    }

    private String getArmorColorCode(Player p) {
        ItemStack chest = p.getInventory().getChestplate();
        if (chest != null && chest.getType().name().contains("LEATHER_CHESTPLATE")) {
            LeatherArmorMeta meta = (LeatherArmorMeta) chest.getItemMeta();
            Color color = meta.getColor();
            if (isSimilarColor(color, Color.RED)) return "§c";
            if (isSimilarColor(color, Color.BLUE)) return "§9";
            if (isSimilarColor(color, Color.LIME)) return "§a";
            if (isSimilarColor(color, Color.YELLOW)) return "§e";
            if (isSimilarColor(color, Color.AQUA)) return "§b";
            if (isSimilarColor(color, Color.WHITE)) return "§f";
            if (isSimilarColor(color, Color.GRAY) || isSimilarColor(color, Color.SILVER)) return "§7";
            if (color.getRed() > color.getBlue() && color.getRed() > color.getGreen()) return "§c";
            if (color.getBlue() > color.getRed() && color.getBlue() > color.getGreen()) return "§9";
            if (color.getGreen() > color.getRed() && color.getGreen() > color.getBlue()) return "§a";
        }
        String colorCode = "§f";
        try {
            Team team = null;
            try { team = p.getScoreboard().getPlayerTeam(p); }
            catch (NoSuchMethodError e) { try { team = p.getScoreboard().getEntryTeam(p.getName()); } catch (Exception ex) {} }
            if (team != null) colorCode = extractColorCode(team.getPrefix());
        } catch (Exception e) {}
        if (colorCode.equals("§f")) colorCode = extractColorCode(p.getDisplayName());
        return colorCode;
    }

    private boolean isSimilarColor(Color c1, Color c2) {
        int diff = Math.abs(c1.getRed() - c2.getRed()) + Math.abs(c1.getGreen() - c2.getGreen()) + Math.abs(c1.getBlue() - c2.getBlue());
        return diff < 30;
    }

    private String extractColorCode(String text) {
        String lastColors = ChatColor.getLastColors(text);
        return (lastColors == null || lastColors.isEmpty()) ? "§f" : lastColors;
    }

    public String getColorNameFromCode(String code) {
        if (code.contains("§c")) return "§cRed";
        if (code.contains("§9")) return "§9Blue";
        if (code.contains("§a")) return "§aGreen";
        if (code.contains("§e")) return "§eYellow";
        if (code.contains("§b")) return "§bAqua";
        if (code.contains("§d")) return "§dPink";
        if (code.contains("§7")) return "§7Gray";
        return code + "Opponent";
    }
}