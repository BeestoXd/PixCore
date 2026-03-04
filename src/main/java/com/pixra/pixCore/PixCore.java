package com.pixra.pixCore;

import com.pixra.pixCore.arena.ArenaBoundaryManager;
import com.pixra.pixCore.commands.LeaderboardCommand;
import com.pixra.pixCore.commands.SaveLayoutCommand;
import com.pixra.pixCore.duels.DuelScoreManager;
import com.pixra.pixCore.hook.StrikePracticeHook;
import com.pixra.pixCore.listeners.*;
import com.pixra.pixCore.managers.*;
import com.pixra.pixCore.knockback.MLGRushKnockback;
import com.pixra.pixCore.party.PartySplitManager;
import com.pixra.pixCore.placeholders.PixCorePlaceholders;
import com.pixra.pixCore.respawn.RespawnManager;
import com.pixra.pixCore.commands.PixCommand;
import com.pixra.pixCore.util.SoundUtil;
import com.pixra.pixCore.util.TeamColorUtil;
import com.pixra.pixCore.util.TitleUtil;
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

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

public class PixCore extends JavaPlugin {

    private final StrikePracticeHook hook = new StrikePracticeHook();

    private TeamColorUtil teamColorUtil;

    public int     voidYLimit;
    public boolean buildRestrictionsEnabled;
    public int     maxBuildY;
    public boolean checkArenaBorders;
    public List<String> buildRestrictionKits;
    public List<String> roundEndKits;
    public List<String> bowCooldownKits;
    public List<String> deathDisabledKits;
    public List<String> instantRespawnKits;
    public List<String> deathDisabledArenas;

    public File              tntTickFile;
    public FileConfiguration tntTickConfig;
    public EntityType        tntEntityType;
    public boolean           hasSetSource;

    private File              noFallDamageFile;
    private FileConfiguration noFallDamageConfig;

    public boolean bowCooldownEnabled;
    public int     bowCooldownSeconds;
    public int     respawnCountdownInterval;
    public boolean bowBoostEnabled;
    public double  bowBoostHorizontal;
    public double  bowBoostVertical;

    public boolean              startCountdownEnabled;
    public Map<Integer, String> startCountdownTitles;
    public int                  startCountdownDuration;
    public Map<Integer, String> startCountdownMessages;
    public String               startMatchMessage;

    public boolean              startCountdownSoundEnabled;
    public float                startCountdownVolume;
    public float                startCountdownPitch;
    public Map<Integer, Sound>  startCountdownSounds;
    public Sound                startMatchSound;

    public boolean endMatchSoundEnabled;
    public Sound   victorySoundPrimary;
    public Sound   victorySoundSecondary;
    public Sound   defeatSoundPrimary;
    public Sound   defeatSoundSecondary;

    public boolean           respawnChatCountdownEnabled;
    public Set<String>       respawnChatCountdownKits;
    public Map<Integer, String> respawnChatNumbers;
    public String            respawnChatFormat;

    public Sound   respawnSound;
    public boolean soundEnabled = false;

    public RespawnManager         respawnManager;
    public ArenaBoundaryManager   arenaBoundaryManager;
    private BlockDisappearManager  blockDisappearManager;
    private HitRewardManager       hitRewardManager;
    public CustomKnockbackManager  customKnockbackManager;
    private SnowballManager        snowballManager;
    private TntMechanicsManager    tntMechanicsManager;
    public BlockReplenishManager   blockReplenishManager;
    private ItemMechanicsManager   itemMechanicsManager;
    private NoFallDamageManager    noFallDamageManager;
    public BowHitMessageManager    bowHitMessageManager;
    private BedDestroyTitleManager bedDestroyTitleManager;
    public StickFightManager       stickFightManager;
    private KillMessageManager     killMessageManager;
    public HitActionBarManager     hitActionBarManager;
    public MLGRushKnockback        mlgrushKnockback;
    public PartySplitManager       partySplitManager;
    public DuelScoreManager        duelScoreManager;
    public LeaderboardManager      leaderboardManager;
    public HologramManager         hologramManager;
    public LeaderboardGUIManager   leaderboardGUIManager;

    public final Set<UUID>               frozenPlayers           = new HashSet<>();
    public       boolean                 isCountdownRunning      = false;
    public final Map<UUID, Long>         bowCooldowns            = new HashMap<>();
    public final Set<UUID>               titleCooldown           = new HashSet<>();
    public final Map<UUID, BukkitTask>   activeCountdowns        = new HashMap<>();
    public final Map<UUID, Long>         msgCooldowns            = new HashMap<>();
    public final Map<UUID, Long>         deathMessageCooldowns   = new HashMap<>();
    public final Map<UUID, String>       lastBroadcastMessage    = new HashMap<>();
    public final Map<UUID, Long>         lastBroadcastTime       = new HashMap<>();
    public final Map<UUID, UUID>         lastDamager             = new HashMap<>();
    public final Map<UUID, Long>         lastDamageTime          = new HashMap<>();
    public final Map<UUID, Location>     arenaSpawnLocations     = new HashMap<>();
    public final Map<Object, UUID>       endedFightWinners       = new HashMap<>();
    public final Map<UUID, String>       playerMatchResults      = new HashMap<>();
    public final Map<UUID, Integer>      playerMatchKills        = new HashMap<>();
    public final Map<UUID, Long>         killCountCooldown       = new HashMap<>();
    public final Map<Object, Map<UUID, Integer>> matchScores     = new HashMap<>();

    @Override
    public void onEnable() {
        this.duelScoreManager        = new DuelScoreManager(this);
        this.respawnManager          = new RespawnManager(this);
        this.blockDisappearManager   = new BlockDisappearManager(this);
        this.hitRewardManager        = new HitRewardManager(this);
        this.customKnockbackManager  = new CustomKnockbackManager(this);
        this.snowballManager         = new SnowballManager(this);
        this.tntMechanicsManager     = new TntMechanicsManager(this);
        this.blockReplenishManager   = new BlockReplenishManager(this);
        this.itemMechanicsManager    = new ItemMechanicsManager(this);
        this.noFallDamageManager     = new NoFallDamageManager(this);
        this.bowHitMessageManager    = new BowHitMessageManager(this);
        this.bedDestroyTitleManager  = new BedDestroyTitleManager(this);
        this.stickFightManager       = new StickFightManager(this);
        this.killMessageManager      = new KillMessageManager(this);
        this.hitActionBarManager     = new HitActionBarManager(this);
        this.mlgrushKnockback        = new MLGRushKnockback(this);
        this.partySplitManager       = new PartySplitManager(this);
        this.leaderboardManager      = new LeaderboardManager(this);
        this.hologramManager         = new HologramManager(this);
        this.leaderboardGUIManager   = new LeaderboardGUIManager(this);

        clearAllCaches();

        new BukkitRunnable() {
            @Override public void run() {
                if (leaderboardManager != null) leaderboardManager.backupData("auto");
            }
        }.runTaskTimerAsynchronously(this, 72000L, 72000L);

        saveDefaultConfig();
        loadConfigValues();
        loadTntTickConfig();
        loadNoFallDamageConfig();
        setupTntVariables();

        if (getCommand("pix")         != null) getCommand("pix").setExecutor(new PixCommand(this));
        if (getCommand("savelayout")  != null) getCommand("savelayout").setExecutor(new SaveLayoutCommand(this));
        if (getCommand("leaderboard") != null) getCommand("leaderboard").setExecutor(new LeaderboardCommand(this));

        hookStrikePractice();
        this.teamColorUtil = new TeamColorUtil(hook);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PixCorePlaceholders(this).register();
        }

        getServer().getPluginManager().registerEvents(new CombatListener(this),  this);
        getServer().getPluginManager().registerEvents(new MatchListener(this),   this);
        getServer().getPluginManager().registerEvents(new BedListener(this),     this);
        getServer().getPluginManager().registerEvents(new GameplayListener(this), this);

        getServer().getPluginManager().registerEvents(this.blockDisappearManager,  this);
        getServer().getPluginManager().registerEvents(this.hitRewardManager,       this);
        getServer().getPluginManager().registerEvents(this.customKnockbackManager, this);
        getServer().getPluginManager().registerEvents(this.snowballManager,        this);
        getServer().getPluginManager().registerEvents(this.tntMechanicsManager,    this);
        getServer().getPluginManager().registerEvents(this.blockReplenishManager,  this);
        getServer().getPluginManager().registerEvents(this.itemMechanicsManager,   this);
        getServer().getPluginManager().registerEvents(this.noFallDamageManager,    this);
        getServer().getPluginManager().registerEvents(this.bowHitMessageManager,   this);
        getServer().getPluginManager().registerEvents(this.stickFightManager,      this);
        getServer().getPluginManager().registerEvents(this.hitActionBarManager,    this);
        getServer().getPluginManager().registerEvents(this.mlgrushKnockback,       this);
        getServer().getPluginManager().registerEvents(this.partySplitManager,      this);
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        clearAllCaches();
        if (this.hologramManager != null) this.hologramManager.removeAllHolograms();
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

    private void hookStrikePractice() {
        hook.hook();
        this.arenaReflectionLoaded  = hook.arenaReflectionLoaded;
        this.bestOfReflectionLoaded = hook.bestOfReflectionLoaded;
        if (hook.arenaReflectionLoaded) {
            this.arenaBoundaryManager = new ArenaBoundaryManager(
                    hook.getAPI(),
                    hook.getMGetFight(),
                    hook.getMGetArena(),
                    hook.getMArenaGetMin(),
                    hook.getMArenaGetMax()
            );
        }
    }

    public boolean isHooked()                      { return hook.isHooked(); }
    public Object  getStrikePracticeAPI()           { return hook.getAPI(); }

    public boolean arenaReflectionLoaded           = false;
    public boolean bestOfReflectionLoaded          = false;

    public boolean isInFight(Player player)         { return hook.isInFight(player); }
    public String  getKitName(Player player)        { return hook.getKitName(player); }
    public void    addSpectator(Player p)           { hook.addSpectator(p); }
    public void    removeSpectator(Player p, boolean c) { hook.removeSpectator(p, c); }
    public void    respawnInFight(Player p)         { hook.respawnInFight(p); }

    public Method getMGetFight()            { return hook.getMGetFight(); }
    public Method getMIsInFight()           { return hook.getMIsInFight(); }
    public Method getMGetKit()              { return hook.getMGetKit(); }
    public Method getMGetFirstPlayer()      { return hook.getMGetFirstPlayer(); }
    public Method getMGetSecondPlayer()     { return hook.getMGetSecondPlayer(); }
    public Method getMPlayersAreTeammates() { return hook.getMPlayersAreTeammates(); }
    public Method getMGetTeammates()        { return hook.getMGetTeammates(); }
    public Method getMGetOpponents()        { return hook.getMGetOpponents(); }
    public Method getMGetPlayersInFight()   { return hook.getMGetPlayersInFight(); }
    public Method getMGetPlayers()          { return hook.getMGetPlayers(); }
    public Method getMGetArena()            { return hook.getMGetArena(); }
    public Method getMIsBed1Broken()        { return hook.getMIsBed1Broken(); }
    public Method getMIsBed2Broken()        { return hook.getMIsBed2Broken(); }
    public Method getMSetBedwars()          { return hook.getMSetBedwars(); }
    public Method getMHandleWin()           { return hook.getMHandleWin(); }
    public Method getMHandleDeath()         { return hook.getMHandleDeath(); }
    public Method getMGetBestOf()           { return hook.getMGetBestOf(); }

    public Class<?> getClsAbstractFight()  { return hook.getClsAbstractFight(); }
    public Class<?> getClsBestOfFight()    { return hook.getClsBestOfFight(); }

    public Field getFBed1Broken()          { return hook.getFBed1Broken(); }
    public Field getFBed2Broken()          { return hook.getFBed2Broken(); }

    public Map<Object, UUID>       getEndedFightWinners()  { return endedFightWinners; }
    public Map<UUID, String>       getPlayerMatchResults() { return playerMatchResults; }
    public Map<UUID, Integer>      getPlayerMatchKills()   { return playerMatchKills; }
    public FileConfiguration       getNoFallDamageConfig() { return noFallDamageConfig; }
    public BedDestroyTitleManager  getBedDestroyTitleManager() { return bedDestroyTitleManager; }
    public KillMessageManager      getKillMessageManager()     { return killMessageManager; }
    public Map<UUID, BukkitTask>   getActiveCountdowns()       { return activeCountdowns; }
    public Map<UUID, Location>     getArenaSpawnLocations()    { return arenaSpawnLocations; }

    public String getTeamColorCode(Player p, Object fight)  { return teamColorUtil.getTeamColorCode(p, fight); }
    public Color  getTeamColor(Player p)                    { return teamColorUtil.getTeamColor(p); }
    public String getColorNameFromCode(String code)         { return teamColorUtil.getColorNameFromCode(code); }
    public void   colorItem(ItemStack item, Color color)    { teamColorUtil.colorItem(item, color); }

    public Sound getSoundByName(String name) { return SoundUtil.getSoundByName(name); }

    public void sendTitle(Player player, String title, String subtitle,
                          int fadeIn, int stay, int fadeOut) {
        TitleUtil.sendTitle(player, title, subtitle, fadeIn, stay, fadeOut);
    }

    public void loadConfigValues() {
        reloadConfig();
        this.voidYLimit              = getConfig().getInt("settings.void-y-trigger", -64);
        this.buildRestrictionsEnabled = getConfig().getBoolean("settings.build-restrictions.enabled", true);
        this.maxBuildY               = getConfig().getInt("settings.build-restrictions.max-build-y", 100);
        this.checkArenaBorders       = getConfig().getBoolean("settings.build-restrictions.check-arena-borders", true);
        this.buildRestrictionKits    = getConfig().getStringList("settings.build-restrictions.kits");
        this.roundEndKits            = getConfig().getStringList("settings.round-end-kits");
        this.bowCooldownKits         = getConfig().getStringList("settings.bow-cooldown.kits");
        this.deathDisabledKits       = getConfig().getStringList("settings.death.disabled-kits");
        this.instantRespawnKits      = getConfig().getStringList("settings.death.instant-respawn-kits");
        this.respawnChatCountdownEnabled = getConfig().getBoolean("settings.respawn.chat-countdown.enabled", false);
        this.respawnChatFormat       = getConfig().getString("settings.respawn.chat-countdown.format", "&eRespawning in &r<number>");
        this.respawnChatCountdownKits = new HashSet<>(getConfig().getStringList("settings.respawn.chat-countdown.kits"));

        this.respawnChatNumbers = new HashMap<>();
        ConfigurationSection respawnNums = getConfig().getConfigurationSection("settings.respawn.chat-countdown.numbers");
        if (respawnNums != null) {
            for (String key : respawnNums.getKeys(false)) {
                try { respawnChatNumbers.put(Integer.parseInt(key), respawnNums.getString(key)); }
                catch (Exception ignored) {}
            }
        }

        this.startCountdownSounds        = new HashMap<>();
        this.startCountdownSoundEnabled  = resolve("settings.start-countdown.sounds.enabled",
                "start-countdown.sounds.enabled", false);
        this.startCountdownVolume        = (float) getConfig().getDouble("settings.start-countdown.sounds.volume", 1.0);
        this.startCountdownPitch         = (float) getConfig().getDouble("settings.start-countdown.sounds.pitch", 1.0);

        ConfigurationSection soundSection = getConfig().getConfigurationSection("settings.start-countdown.sounds.per-second");
        if (soundSection == null) soundSection = getConfig().getConfigurationSection("start-countdown.sounds.per-second");
        if (soundSection != null) {
            for (String key : soundSection.getKeys(false)) {
                try {
                    Sound s = SoundUtil.getSoundByName(soundSection.getString(key));
                    if (s != null) startCountdownSounds.put(Integer.parseInt(key), s);
                } catch (Exception ignored) {}
            }
        }

        String startSoundPath  = getConfig().contains("settings.start-countdown.sounds.start-sound")
                ? "settings.start-countdown.sounds.start-sound" : "start-countdown.sounds.start-sound";
        this.startMatchSound   = SoundUtil.getSoundByName(getConfig().getString(startSoundPath, null));

        this.endMatchSoundEnabled  = resolve("settings.end-match.sounds.enabled", "end-match.sounds.enabled", true);
        this.victorySoundPrimary   = SoundUtil.getSoundByName(getConfig().getString(
                resolveKey("settings.end-match.sounds.victory-primary", "end-match.sounds.victory-primary"), "FIREWORK_LAUNCH"));
        this.victorySoundSecondary = SoundUtil.getSoundByName(getConfig().getString(
                resolveKey("settings.end-match.sounds.victory-secondary", "end-match.sounds.victory-secondary"), "FIREWORK_TWINKLE_FAR"));
        this.defeatSoundPrimary    = SoundUtil.getSoundByName(getConfig().getString(
                resolveKey("settings.end-match.sounds.defeat-primary", "end-match.sounds.defeat-primary"), "FIREWORK_LAUNCH"));
        this.defeatSoundSecondary  = SoundUtil.getSoundByName(getConfig().getString(
                resolveKey("settings.end-match.sounds.defeat-secondary", "end-match.sounds.defeat-secondary"), "FIREWORK_TWINKLE_FAR"));

        this.respawnCountdownInterval = getConfig().getInt("settings.respawn-countdown-interval", 20);
        this.bowCooldownEnabled  = getConfig().getBoolean("settings.bow-cooldown.enabled", false);
        this.bowCooldownSeconds  = getConfig().getInt("settings.bow-cooldown.seconds", 3);
        this.bowBoostEnabled     = getConfig().getBoolean("settings.bow-boost.enabled", true);
        this.bowBoostHorizontal  = getConfig().getDouble("settings.bow-boost.horizontal", 1.35);
        this.bowBoostVertical    = getConfig().getDouble("settings.bow-boost.vertical", 0.42);
        this.startCountdownDuration = getConfig().getInt("settings.start-countdown.duration", 5);

        this.startCountdownMessages = new HashMap<>();
        ConfigurationSection msgSection = getConfig().getConfigurationSection("settings.start-countdown.messages");
        if (msgSection == null) msgSection = getConfig().getConfigurationSection("start-countdown.messages");
        if (msgSection != null) {
            for (String key : msgSection.getKeys(false)) {
                try { startCountdownMessages.put(Integer.parseInt(key),
                        ChatColor.translateAlternateColorCodes('&', msgSection.getString(key))); }
                catch (Exception ignored) {}
            }
        }

        this.startMatchMessage   = ChatColor.translateAlternateColorCodes('&', getConfig().getString(
                resolveKey("settings.start-countdown.start-message", "start-countdown.start-message"), "&aMatch Started!"));
        this.startCountdownEnabled = resolve("settings.start-countdown.enabled", "start-countdown.enabled", false);

        this.startCountdownTitles = new HashMap<>();
        ConfigurationSection titleSection = getConfig().getConfigurationSection("settings.start-countdown.titles");
        if (titleSection == null) titleSection = getConfig().getConfigurationSection("start-countdown.titles");
        if (titleSection != null) {
            for (String key : titleSection.getKeys(false)) {
                try { startCountdownTitles.put(Integer.parseInt(key),
                        ChatColor.translateAlternateColorCodes('&', titleSection.getString(key))); }
                catch (Exception ignored) {}
            }
        }

        String soundName    = getConfig().getString("settings.respawn-sound", "NOTE_PLING");
        this.respawnSound   = SoundUtil.getSoundByName(soundName);
        this.soundEnabled   = (this.respawnSound != null);
    }

    private boolean resolve(String primary, String fallback, boolean def) {
        if (getConfig().contains(primary))  return getConfig().getBoolean(primary, def);
        return getConfig().getBoolean(fallback, def);
    }

    private String resolveKey(String primary, String fallback) {
        return getConfig().contains(primary) ? primary : fallback;
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

    public boolean isInFightAny(Player player)  { return isInFight(player); }

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
        frozenPlayers.remove(uid);
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

    public void playEndMatchSounds(Player player, boolean isVictory) {
        if (!endMatchSoundEnabled || player == null || !player.isOnline() || !isVictory) return;

        Sound primary   = victorySoundPrimary;
        Sound secondary = victorySoundSecondary;

        if (secondary != null) {
            try { player.playSound(player.getLocation(), secondary, 1.0f, 1.0f); }
            catch (Exception ignored) {}
        }

        new BukkitRunnable() {
            int elapsedTicks = 0;
            @Override public void run() {
                if (!player.isOnline() || elapsedTicks > 60) { this.cancel(); return; }
                try { if (primary != null) player.playSound(player.getLocation(), primary, 1.0f, 1.0f); }
                catch (Exception ignored) {}
                elapsedTicks += 10;
            }
        }.runTaskTimer(this, 0L, 10L);
    }

    public void sendCooldownMessage(Player player, String configKey) {
        long lastMsgTime = msgCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (System.currentTimeMillis() - lastMsgTime <= 500) return;

        String msg = getMsg(configKey);
        if (msg == null || msg.isEmpty()) {
            if      (configKey.equals("bed-break-self"))             msg = ChatColor.RED + "You can't break your own bed!";
            else if (configKey.equals("block-place-denied-start"))   msg = ChatColor.RED + "You cannot place blocks while the match is starting!";
            else if (configKey.equals("block-place-denied"))         msg = ChatColor.RED + "You cannot place blocks while respawning!";
            else if (configKey.equals("block-break-denied-start"))   msg = ChatColor.RED + "You cannot break blocks while the match is starting!";
        }
        if (msg != null && !msg.isEmpty()) player.sendMessage(msg);
        msgCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public String getMsg(String path) {
        if (getConfig().contains(path))              return ChatColor.translateAlternateColorCodes('&', getConfig().getString(path));
        if (getConfig().contains("messages." + path)) return ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages." + path));
        return "";
    }

    @SuppressWarnings("unchecked")
    public void applyStartKit(Player p) {
        if (!isHooked() || hook.getMGetKit() == null || hook.getAPI() == null) return;
        try {
            Object baseKit = hook.getMGetKit().invoke(hook.getAPI(), p);
            if (baseKit == null) return;

            String    baseName = ChatColor.stripColor(
                    (String) baseKit.getClass().getMethod("getName").invoke(baseKit)).toLowerCase();
            ItemStack baseIcon = null;
            try { baseIcon = (ItemStack) hook.mBattleKitGetIcon.invoke(baseKit); } catch (Exception ignored) {}
            String baseIconName = (baseIcon != null && baseIcon.hasItemMeta() && baseIcon.getItemMeta().hasDisplayName())
                    ? ChatColor.stripColor(baseIcon.getItemMeta().getDisplayName()).toLowerCase() : null;

            boolean hasActualItems = false;
            for (ItemStack item : p.getInventory().getContents()) {
                if (item != null && item.getType() != org.bukkit.Material.AIR) {
                    boolean isLobbyItem = false;
                    String  type        = item.getType().name();
                    if (type.contains("BOOK") || type.contains("BED") || type.contains("NAME_TAG")
                            || type.contains("PAPER") || type.contains("EMERALD") || type.contains("COMPASS")
                            || type.contains("WATCH") || type.contains("CLOCK") || type.contains("CHEST")
                            || type.contains("SLIME")) {
                        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                            String name = ChatColor.stripColor(item.getItemMeta().getDisplayName()).toLowerCase();
                            if (name.contains("layout") || name.contains("kit") || name.contains("default")
                                    || name.contains("edit") || name.contains("leave") || name.contains("spectate")
                                    || name.contains("play") || name.contains("options")
                                    || (baseIconName != null && name.equals(baseIconName))) {
                                isLobbyItem = true;
                            }
                        }
                    }
                    if (!isLobbyItem) { hasActualItems = true; break; }
                }
            }

            if (!hasActualItems) {
                p.getInventory().clear();
                if (hook.mBattleKitGiveKit != null) {
                    hook.mBattleKitGiveKit.invoke(baseKit, p);
                } else if (hook.getMKitApply() != null) {
                    hook.getMKitApply().invoke(baseKit, p);
                }
                p.updateInventory();
            }

            ItemStack helmet = null, chest = null, legs = null, boots = null;
            try {
                if (hook.mBattleKitGetHelmet     != null) helmet = (ItemStack) hook.mBattleKitGetHelmet.invoke(baseKit);
                if (hook.mBattleKitGetChestplate != null) chest  = (ItemStack) hook.mBattleKitGetChestplate.invoke(baseKit);
                if (hook.mBattleKitGetLeggings   != null) legs   = (ItemStack) hook.mBattleKitGetLeggings.invoke(baseKit);
                if (hook.mBattleKitGetBoots      != null) boots  = (ItemStack) hook.mBattleKitGetBoots.invoke(baseKit);
            } catch (Exception ignored) {}

            List<Object> allKits = new ArrayList<>();

            File spFolder = new File(getDataFolder().getParentFile(), "StrikePractice");
            File pdFile   = new File(spFolder, "playerdata/" + p.getUniqueId() + ".yml");
            if (pdFile.exists()) {
                List<?> spKits = YamlConfiguration.loadConfiguration(pdFile).getList("kits");
                if (spKits != null) allKits.addAll(spKits);
            }
            File pixFile = new File(getDataFolder(), "layouts/" + p.getUniqueId() + ".yml");
            if (pixFile.exists()) {
                List<?> pixKits = YamlConfiguration.loadConfiguration(pixFile).getList("kits");
                if (pixKits != null) {
                    for (int i = pixKits.size() - 1; i >= 0; i--) allKits.add(0, pixKits.get(i));
                }
            }

            if (!allKits.isEmpty()) {
                for (Object customKit : allKits) {
                    if (customKit == null) continue;
                    boolean        isMatch = false;
                    List<ItemStack> yamlInv = null;

                    if (!Map.class.isAssignableFrom(customKit.getClass())) {
                        ItemStack icon  = null;
                        try { icon = (ItemStack) hook.mBattleKitGetIcon.invoke(customKit); } catch (Exception ignored) {}
                        String dName = (icon != null && icon.hasItemMeta() && icon.getItemMeta().hasDisplayName())
                                ? ChatColor.stripColor(icon.getItemMeta().getDisplayName()).toLowerCase() : "";
                        String kName = "";
                        try { kName = ChatColor.stripColor((String) customKit.getClass().getMethod("getName").invoke(customKit)).toLowerCase(); }
                        catch (Exception ignored) {}
                        if (dName.contains(baseName) || kName.contains(baseName)) {
                            isMatch = true;
                            try { yamlInv = (List<ItemStack>) customKit.getClass().getMethod("getInventory").invoke(customKit); }
                            catch (Exception ignored) {}
                        }
                    } else {
                        Map<?, ?> map   = (Map<?, ?>) customKit;
                        ItemStack icon  = map.get("icon") instanceof ItemStack ? (ItemStack) map.get("icon") : null;
                        String    dName = (icon != null && icon.hasItemMeta() && icon.getItemMeta().hasDisplayName())
                                ? ChatColor.stripColor(icon.getItemMeta().getDisplayName()).toLowerCase() : "";
                        String    kName = map.containsKey("name")
                                ? ChatColor.stripColor(String.valueOf(map.get("name"))).toLowerCase() : "";
                        if (dName.contains(baseName) || kName.contains(baseName)) {
                            isMatch = true;
                            if (map.get("inventory") instanceof List) yamlInv = (List<ItemStack>) map.get("inventory");
                        }
                    }

                    if (isMatch && yamlInv != null) {
                        ItemStack[]     current  = p.getInventory().getContents();
                        List<ItemStack> pool     = new ArrayList<>();
                        final String    finalIconName = baseIconName;

                        for (int i = 0; i < 36 && i < current.length; i++) {
                            ItemStack item = current[i];
                            if (item == null || item.getType() == org.bukkit.Material.AIR) continue;
                            if (!isLobbyItem(item, finalIconName)) pool.add(item.clone());
                        }

                        ItemStack[] newContents = new ItemStack[current.length];
                        for (int i = 0; i < yamlInv.size() && i < newContents.length; i++) {
                            ItemStack target = yamlInv.get(i);
                            if (target == null || target.getType() == org.bukkit.Material.AIR) continue;
                            ItemStack matched = null;
                            for (int j = 0; j < pool.size(); j++) {
                                if (teamColorUtil.isItemMatch(target, pool.get(j))) {
                                    matched = pool.remove(j); break;
                                }
                            }
                            if (matched != null) {
                                newContents[i] = matched;
                            } else if (!isLobbyItem(target, finalIconName)) {
                                newContents[i] = target.clone();
                            }
                        }
                        for (ItemStack leftover : pool) {
                            for (int i = 0; i < newContents.length; i++) {
                                if (newContents[i] == null) { newContents[i] = leftover; break; }
                            }
                        }
                        p.getInventory().setContents(newContents);
                        break;
                    }
                }
            }

            Color       teamColor = teamColorUtil.getTeamColor(p);
            ItemStack[] contents  = p.getInventory().getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack item = contents[i];
                if (item == null || item.getType() == org.bukkit.Material.AIR) continue;
                if (isLobbyItem(item, baseIconName)) { contents[i] = null; }
                else { teamColorUtil.colorItem(item, teamColor); }
            }
            p.getInventory().setContents(contents);

            ItemStack[] armor = new ItemStack[4];
            armor[0] = (boots  != null) ? boots.clone()  : p.getInventory().getBoots();
            armor[1] = (legs   != null) ? legs.clone()   : p.getInventory().getLeggings();
            armor[2] = (chest  != null) ? chest.clone()  : p.getInventory().getChestplate();
            armor[3] = (helmet != null) ? helmet.clone() : p.getInventory().getHelmet();
            for (ItemStack a : armor) teamColorUtil.colorItem(a, teamColor);
            p.getInventory().setArmorContents(armor);
            p.updateInventory();

        } catch (Exception e) { e.printStackTrace(); }
    }

    private boolean isLobbyItem(ItemStack item, String baseIconName) {
        if (item == null || item.getType() == org.bukkit.Material.AIR) return false;
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return false;
        String name = ChatColor.stripColor(item.getItemMeta().getDisplayName()).toLowerCase();
        if (baseIconName != null && name.equals(baseIconName)) return true;
        String type = item.getType().name();
        if (type.contains("BOOK") || type.contains("BED") || type.contains("NAME_TAG")
                || type.contains("PAPER") || type.contains("EMERALD")) {
            return name.contains("layout") || name.contains("kit") || name.contains("default")
                    || name.contains("#") || name.contains("edit");
        }
        return false;
    }

    public void applyKit(Player p) { applyStartKit(p); }
}