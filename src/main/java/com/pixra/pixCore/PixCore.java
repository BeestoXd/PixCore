package com.pixra.pixCore;

import com.pixra.pixCore.arena.ArenaBoundaryManager;
import com.pixra.pixCore.commands.LeaderboardCommand;
import com.pixra.pixCore.commands.SaveLayoutCommand;
import com.pixra.pixCore.duels.DuelScoreManager;
import com.pixra.pixCore.hook.StrikePracticeHook;
import com.pixra.pixCore.listeners.*;
import com.pixra.pixCore.managers.*;
import com.pixra.pixCore.managers.MatchDurationManager;
import com.pixra.pixCore.party.PartySplitManager;
import com.pixra.pixCore.party.PartyFFAManager;
import com.pixra.pixCore.party.PartyVsPartyManager;
import com.pixra.pixCore.placeholders.PixCorePlaceholders;
import com.pixra.pixCore.respawn.RespawnManager;
import com.pixra.pixCore.commands.PartyCommand;
import com.pixra.pixCore.commands.PixCommand;
import com.pixra.pixCore.util.SoundUtil;
import com.pixra.pixCore.util.TeamColorUtil;
import com.pixra.pixCore.util.TitleUtil;
import me.clip.placeholderapi.PlaceholderAPI;
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
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
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

    private File              arrowGiveFile;
    private FileConfiguration arrowGiveConfig;

    public File              bestofFile;
    public FileConfiguration bestofConfig;
    public boolean            bowAutoGiveArrowEnabled;
    public List<String>       bowAutoGiveArrowKits;

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
    private SnowballManager        snowballManager;
    public TntMechanicsManager       tntMechanicsManager;
    public FireballKnockbackManager  fireballKnockbackManager;
    public FireballCooldownManager   fireballCooldownManager;
    public BlockReplenishManager   blockReplenishManager;
    public BridgeBlockResetManager bridgeBlockResetManager;
    private ItemMechanicsManager   itemMechanicsManager;
    private NoFallDamageManager    noFallDamageManager;
    public BowHitMessageManager    bowHitMessageManager;
    private BedDestroyTitleManager bedDestroyTitleManager;
    public StickFightManager       stickFightManager;
    private KillMessageManager     killMessageManager;
    public HitActionBarManager     hitActionBarManager;
    public PartySplitManager       partySplitManager;
    public PartyFFAManager         partyFFAManager;
    public PartyVsPartyManager     partyVsPartyManager;
    public DuelScoreManager        duelScoreManager;
    public LeaderboardManager      leaderboardManager;
    public HologramManager         hologramManager;
    public LeaderboardGUIManager   leaderboardGUIManager;
    public VoidManager             voidManager;
    public ScoreboardTitleAnimator scoreboardTitleAnimator;
    public MatchDurationManager    matchDurationManager;

    public final Set<Object>             drawFights              = new HashSet<>();

    public final Map<String, String>     prefixCache             = new HashMap<>();
    public final Map<String, String>     tagCache                = new HashMap<>();

    public final Set<UUID>               frozenPlayers           = new HashSet<>();
    public final Set<UUID>               roundTransitionPlayers  = new HashSet<>();
    public final Set<UUID>               activeStartCountdownPlayers = new HashSet<>();
    public final Set<UUID>               pendingHubOnJoin            = new HashSet<>();
    public final Map<UUID, Location>     hubOnJoinSpawn              = new HashMap<>();
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

    public final Set<UUID>               mlgRushBedDeaths        = new HashSet<>();
    public final Map<Object, Map<UUID, Integer>> matchScores     = new HashMap<>();
    public final Set<UUID>               recentPartyEndedPlayers = new HashSet<>();
    public final Set<UUID>               leavingMatchPlayers     = new HashSet<>();

    @Override
    public void onEnable() {
        this.duelScoreManager        = new DuelScoreManager(this);
        this.respawnManager          = new RespawnManager(this);
        this.blockDisappearManager   = new BlockDisappearManager(this);
        this.hitRewardManager        = new HitRewardManager(this);
        this.snowballManager         = new SnowballManager(this);
        this.tntMechanicsManager        = new TntMechanicsManager(this);
        this.fireballKnockbackManager   = new FireballKnockbackManager(this);
        this.fireballCooldownManager    = new FireballCooldownManager(this);
        this.blockReplenishManager   = new BlockReplenishManager(this);
        this.bridgeBlockResetManager = new BridgeBlockResetManager(this);
        this.itemMechanicsManager    = new ItemMechanicsManager(this);
        this.noFallDamageManager     = new NoFallDamageManager(this);
        this.bowHitMessageManager    = new BowHitMessageManager(this);
        this.bedDestroyTitleManager  = new BedDestroyTitleManager(this);
        this.stickFightManager       = new StickFightManager(this);
        this.killMessageManager      = new KillMessageManager(this);
        this.hitActionBarManager     = new HitActionBarManager(this);
        this.partySplitManager       = new PartySplitManager(this);
        this.partyFFAManager         = new PartyFFAManager(this);
        this.partyVsPartyManager     = new PartyVsPartyManager(this);
        this.leaderboardManager      = new LeaderboardManager(this);
        this.hologramManager         = new HologramManager(this);
        this.leaderboardGUIManager   = new LeaderboardGUIManager(this);
        this.voidManager             = new VoidManager(this);
        this.scoreboardTitleAnimator = new ScoreboardTitleAnimator();
        this.scoreboardTitleAnimator.start(this);
        this.matchDurationManager    = new MatchDurationManager(this);

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
        loadArrowGiveConfig();
        loadBestofConfig();
        setupTntVariables();

        PartyCommand partyCommand = new PartyCommand(this);
        if (getCommand("party") != null) getCommand("party").setExecutor(partyCommand);

        if (getCommand("pix")         != null) getCommand("pix").setExecutor(new PixCommand(this));
        if (getCommand("savelayout")  != null) getCommand("savelayout").setExecutor(new SaveLayoutCommand(this));
        if (getCommand("leaderboard") != null) getCommand("leaderboard").setExecutor(new LeaderboardCommand(this));

        hookStrikePractice();
        this.teamColorUtil = new TeamColorUtil(hook);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PixCorePlaceholders(this).register();
        }

        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onJoin(PlayerJoinEvent e) {
                Bukkit.getScheduler().runTaskLater(PixCore.this, () -> updatePrefixTagCache(e.getPlayer()), 20L);
                com.pixra.pixCore.util.SpectatingMessageFilter.inject(e.getPlayer());
            }
        }, this);

        for (Player p : Bukkit.getOnlinePlayers()) {
            com.pixra.pixCore.util.SpectatingMessageFilter.inject(p);
        }

        new BukkitRunnable() {
            @Override public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) updatePrefixTagCache(p);
            }
        }.runTaskTimer(this, 6000L, 6000L);

        getServer().getPluginManager().registerEvents(new CombatListener(this),    this);
        getServer().getPluginManager().registerEvents(new MatchListener(this),     this);
        getServer().getPluginManager().registerEvents(new BedListener(this),       this);
        getServer().getPluginManager().registerEvents(new GameplayListener(this),  this);
        getServer().getPluginManager().registerEvents(this.blockDisappearManager,  this);
        getServer().getPluginManager().registerEvents(this.hitRewardManager,       this);
        getServer().getPluginManager().registerEvents(this.snowballManager,        this);
        getServer().getPluginManager().registerEvents(this.tntMechanicsManager,         this);
        getServer().getPluginManager().registerEvents(this.fireballKnockbackManager,    this);
        getServer().getPluginManager().registerEvents(this.fireballCooldownManager,     this);
        getServer().getPluginManager().registerEvents(this.blockReplenishManager,  this);
        getServer().getPluginManager().registerEvents(this.itemMechanicsManager,   this);
        getServer().getPluginManager().registerEvents(this.noFallDamageManager,    this);
        getServer().getPluginManager().registerEvents(this.bowHitMessageManager,   this);
        getServer().getPluginManager().registerEvents(this.stickFightManager,      this);
        getServer().getPluginManager().registerEvents(this.hitActionBarManager,    this);
        getServer().getPluginManager().registerEvents(this.partySplitManager,      this);
        getServer().getPluginManager().registerEvents(this.partyFFAManager,        this);
        getServer().getPluginManager().registerEvents(this.partyVsPartyManager,    this);
        getServer().getPluginManager().registerEvents(this.voidManager,            this);
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
        activeStartCountdownPlayers.clear();
        pendingHubOnJoin.clear();
        hubOnJoinSpawn.clear();
        endedFightWinners.clear();
        playerMatchResults.clear();
        playerMatchKills.clear();
        killCountCooldown.clear();
        matchScores.clear();
        recentPartyEndedPlayers.clear();
        drawFights.clear();
    }

    private void hookStrikePractice() {
        hook.hook();
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
    public com.pixra.pixCore.hook.StrikePracticeHook getHook() { return hook; }

    public boolean isInFight(Player player)         { return hook.isInFight(player); }
    public String  getKitName(Player player)        { return hook.getKitName(player); }
    public void    addSpectator(Player p)           { hook.addSpectator(p); }
    public void    removeSpectator(Player p, boolean c) { hook.removeSpectator(p, c); }
    public void    respawnInFight(Player p)         { hook.respawnInFight(p); }

    public Location resolveHubLocation(Player player) {
        Location hub = null;
        try {
            Object api = getStrikePracticeAPI();
            if (api != null) {
                hub = (Location) api.getClass().getMethod("getSpawnLocation").invoke(api);
            }
        } catch (Exception ignored) {}
        if (hub == null && player != null && player.getWorld() != null) {
            hub = player.getWorld().getSpawnLocation();
        }
        return hub != null ? hub.clone() : null;
    }

    public BlockDisappearManager getBlockDisappearManager() { return blockDisappearManager; }
    public void suppressBlockDisappearReturn(java.util.UUID uuid) { if (blockDisappearManager != null) blockDisappearManager.suppressItemReturn(uuid); }
    public void cancelBlockDisappear(java.util.UUID uuid)         { if (blockDisappearManager != null) blockDisappearManager.cancelPlayerTasks(uuid); }
    public void unsuppressBlockDisappear(java.util.UUID uuid)     { if (blockDisappearManager != null) blockDisappearManager.unsuppressPlayer(uuid); }

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

    public void updatePrefixTagCache(Player player) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return;
        String prefix = PlaceholderAPI.setPlaceholders(player, "%luckperms_prefix%");
        String tag    = PlaceholderAPI.setPlaceholders(player, "%deluxetags_tag%");
        prefixCache.put(player.getName(), "%luckperms_prefix%".equals(prefix) ? "" : prefix);
        tagCache.put(player.getName(),    "%deluxetags_tag%".equals(tag)    ? "" : tag);
    }

    public String getTeamColorCode(Player p, Object fight)  { return teamColorUtil.getTeamColorCode(p, fight); }
    public Color  getTeamColor(Player p)                    { return teamColorUtil.getTeamColor(p); }
    public String getColorNameFromCode(String code)         { return teamColorUtil.getColorNameFromCode(code); }
    public void   colorItem(ItemStack item, Color color)    { teamColorUtil.colorItem(item, color); }
    public boolean isItemMatch(ItemStack target, ItemStack poolItem) { return teamColorUtil.isItemMatch(target, poolItem); }
    public boolean saveLayoutToPreferredStorage(Player player, Object currentKit, List<ItemStack> inventory) {
        return saveLayoutToPreferredStorageInternal(player, currentKit, inventory);
    }
    public void syncLayoutInstant(Player player) { syncLayoutInstant(player, 2); }
    public void syncLayoutInstant(Player player, int verificationTicks) {
        if (player == null || !player.isOnline()) return;

        reapplyLayout(player);
        if (verificationTicks <= 0) return;

        new BukkitRunnable() {
            int remaining = verificationTicks;

            @Override
            public void run() {
                if (!player.isOnline() || !isInFight(player)) {
                    cancel();
                    return;
                }

                reapplyLayout(player);
                remaining--;
                if (remaining <= 0) cancel();
            }
        }.runTaskTimer(this, 1L, 1L);
    }

    public Sound getSoundByName(String name) { return SoundUtil.getSoundByName(name); }

    public void sendTitle(Player player, String title, String subtitle,
                          int fadeIn, int stay, int fadeOut) {
        TitleUtil.sendTitle(player, title, subtitle, fadeIn, stay, fadeOut);
    }

    public void loadConfigValues() {
        reloadConfig();
        this.voidYLimit              = getConfig().getInt("settings.void-y-trigger", -64);
        this.buildRestrictionsEnabled = getConfig().getBoolean("settings.build-restrictions.enabled", true);
        this.roundEndKits            = getConfig().getStringList("settings.bestof-scored.kits");
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
        this.startCountdownEnabled = resolve("settings.start-countdown.enabled", "start-countdown.enabled", true);

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

    public void loadArrowGiveConfig() {
        arrowGiveFile = new File(getDataFolder(), "arrowgive.yml");
        if (!arrowGiveFile.exists()) {
            arrowGiveFile.getParentFile().mkdirs();
            saveResource("arrowgive.yml", false);
        }
        arrowGiveConfig = YamlConfiguration.loadConfiguration(arrowGiveFile);
        this.bowAutoGiveArrowEnabled = arrowGiveConfig.getBoolean("enabled", false);
        this.bowAutoGiveArrowKits    = arrowGiveConfig.getStringList("kits");
    }

    public void loadBestofConfig() {
        bestofFile = new File(getDataFolder(), "bestof.yml");
        if (!bestofFile.exists()) {
            bestofFile.getParentFile().mkdirs();
            try {
                saveResource("bestof.yml", false);
            } catch (Exception e) {
                try (java.io.PrintWriter pw = new java.io.PrintWriter(bestofFile)) {
                    pw.println("stickfight:");
                    pw.println("  score-limit: 5");
                    pw.println("stickfightelo:");
                    pw.println("  score-limit: 5");
                } catch (Exception ignored) {}
            }
        }
        bestofConfig = YamlConfiguration.loadConfiguration(bestofFile);
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

    public void clearPlayerJoinState(UUID uid) {
        cleanupPlayer(uid, false);
        pendingHubOnJoin.remove(uid);
        hubOnJoinSpawn.remove(uid);
        activeStartCountdownPlayers.remove(uid);
        deathMessageCooldowns.remove(uid);
        lastBroadcastMessage.remove(uid);
        lastBroadcastTime.remove(uid);
        arenaSpawnLocations.remove(uid);
        playerMatchResults.remove(uid);
        playerMatchKills.remove(uid);
        recentPartyEndedPlayers.remove(uid);
        leavingMatchPlayers.remove(uid);
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
        if (fireballCooldownManager != null) fireballCooldownManager.clearPlayer(uid);
        killCountCooldown.remove(uid);
        frozenPlayers.remove(uid);
        if (isQuit) {
            if (activeStartCountdownPlayers.contains(uid)) {
                pendingHubOnJoin.add(uid);
            }
            activeStartCountdownPlayers.remove(uid);
            deathMessageCooldowns.remove(uid);
            lastBroadcastMessage.remove(uid);
            lastBroadcastTime.remove(uid);
            arenaSpawnLocations.remove(uid);
            playerMatchKills.remove(uid);
        }
        if (hologramManager != null) {
            hologramManager.clearPlayerHologram(uid);
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
            else if (configKey.equals("block-place-denied-start"))       msg = ChatColor.RED + "You cannot place blocks while the match is starting!";
            else if (configKey.equals("block-place-denied-start-round")) msg = ChatColor.RED + "You cannot place blocks while the round is starting!";
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

    public void reapplyLayout(Player p) {
        if (!isHooked() || hook.getMGetKit() == null || hook.getAPI() == null) return;
        if (!p.isOnline() || !isInFight(p)) return;
        try {
            Object currentKit = hook.getMGetKit().invoke(hook.getAPI(), p);
            Object preferredKit = resolvePreferredLayoutKit(p, currentKit);
            Object activeKit = preferredKit != null ? preferredKit : currentKit;
            if (activeKit == null) return;
            String baseName = normalizeLayoutKey(getBattleKitName(currentKit != null ? currentKit : activeKit));

            Object fightObj = null;
            try {
                if (hook.getMGetFight() != null) fightObj = hook.getMGetFight().invoke(hook.getAPI(), p);
            } catch (Exception ignored) {}

            Color teamColor = (partyFFAManager != null && partyFFAManager.isPartyFFA(fightObj))
                    ? null
                    : teamColorUtil.getTeamColor(p, fightObj);

            StoredLayoutSnapshot storedLayout = resolveStoredLayout(p, baseName, activeKit);
            List<ItemStack> matched = storedLayout != null ? storedLayout.inventory : null;
            List<ItemStack> baseInventory = getBattleKitInventory(activeKit);

            List<ItemStack> layoutInventory = matched != null ? matched : baseInventory;

            ItemStack kitHelmet = getBattleKitPiece(hook.mBattleKitGetHelmet, activeKit);
            ItemStack kitChest  = getBattleKitPiece(hook.mBattleKitGetChestplate, activeKit);
            ItemStack kitLegs   = getBattleKitPiece(hook.mBattleKitGetLeggings, activeKit);
            ItemStack kitBoots  = getBattleKitPiece(hook.mBattleKitGetBoots, activeKit);
            ItemStack curHelmet = p.getInventory().getHelmet();
            ItemStack curChest  = p.getInventory().getChestplate();
            ItemStack curLegs   = p.getInventory().getLeggings();
            ItemStack curBoots  = p.getInventory().getBoots();

            boolean inventoryChanged = false;
            if (layoutInventory != null) {

                ItemStack[] newContents = new ItemStack[36];
                for (int i = 0; i < layoutInventory.size() && i < 36; i++) {
                    ItemStack item = layoutInventory.get(i);
                    if (item != null && item.getType() != org.bukkit.Material.AIR)
                        newContents[i] = item.clone();
                }

                for (ItemStack item : newContents) teamColorUtil.colorItem(item, teamColor);
                inventoryChanged = applyMainInventory(p, newContents);
            }

            ItemStack[] armor = new ItemStack[4];
            armor[0] = kitBoots  != null ? kitBoots.clone()  : (curBoots  != null ? curBoots.clone()  : null);
            armor[1] = kitLegs   != null ? kitLegs.clone()   : (curLegs   != null ? curLegs.clone()   : null);
            armor[2] = kitChest  != null ? kitChest.clone()  : (curChest  != null ? curChest.clone()  : null);
            armor[3] = kitHelmet != null ? kitHelmet.clone() : (curHelmet != null ? curHelmet.clone() : null);
            for (ItemStack a : armor) teamColorUtil.colorItem(a, teamColor);
            boolean armorChanged = applyArmorContents(p, armor);

            if (partyFFAManager != null && partyFFAManager.isPartyFFA(fightObj)) {
                boolean restored = partyFFAManager.restoreArmorColor(p);
                if (!restored && (inventoryChanged || armorChanged)) p.updateInventory();
            } else if (inventoryChanged || armorChanged) {
                p.updateInventory();
            }

        } catch (Exception e) { e.printStackTrace(); }
    }

    @SuppressWarnings("deprecation")
    public void forceRestoreKitBlocks(Player player, Object fight) {
        if (!isHooked() || hook.getMGetKit() == null || hook.getAPI() == null) return;
        if (!player.isOnline()) return;
        try {
            Object currentKit = hook.getMGetKit().invoke(hook.getAPI(), player);
            if (currentKit == null) return;

            Object preferredKit = resolvePreferredLayoutKit(player, currentKit);
            Object activeKit = preferredKit != null ? preferredKit : currentKit;
            String baseName = normalizeLayoutKey(getBattleKitName(currentKit != null ? currentKit : activeKit));
            StoredLayoutSnapshot storedLayout = resolveStoredLayout(player, baseName, activeKit);
            List<ItemStack> matched = storedLayout != null ? storedLayout.inventory : null;
            List<ItemStack> baseInventory = getBattleKitInventory(activeKit);
            List<ItemStack> layoutInventory = matched != null ? matched : baseInventory;
            if (layoutInventory == null || layoutInventory.isEmpty()) return;

            Object fightObj = fight;
            if (fightObj == null) {
                try {
                    if (hook.getMGetFight() != null)
                        fightObj = hook.getMGetFight().invoke(hook.getAPI(), player);
                } catch (Exception ignored) {}
            }

            Color teamColor;
            String ffaCode = (partyFFAManager != null) ? partyFFAManager.getFfaColorCode(player) : null;
            if (ffaCode != null) {
                teamColor = ffaCode.contains("§9") ? Color.BLUE : Color.RED;
            } else {
                teamColor = teamColorUtil.getTeamColor(player, fightObj);
            }

            boolean changed = false;
            for (int i = 0; i < Math.min(layoutInventory.size(), 36); i++) {
                ItemStack kitItem = layoutInventory.get(i);
                if (kitItem == null || kitItem.getType() == org.bukkit.Material.AIR) continue;
                if (kitItem.getType() == org.bukkit.Material.TNT) continue;
                if (!kitItem.getType().isBlock()) continue;

                ItemStack toGive = kitItem.clone();
                if (teamColor != null) teamColorUtil.colorItem(toGive, teamColor);
                player.getInventory().setItem(i, toGive);
                changed = true;
            }
            if (changed) player.updateInventory();
        } catch (Exception ignored) {}
    }

    public void applyLayoutOnly(Player p) {
        if (!isHooked() || hook.getMGetKit() == null || hook.getAPI() == null) return;
        if (!p.isOnline() || !isInFight(p)) return;
        try {
            Object currentKit = hook.getMGetKit().invoke(hook.getAPI(), p);
            Object preferredKit = resolvePreferredLayoutKit(p, currentKit);
            Object activeKit = preferredKit != null ? preferredKit : currentKit;
            if (activeKit == null) return;
            String baseName = normalizeLayoutKey(getBattleKitName(currentKit != null ? currentKit : activeKit));

            StoredLayoutSnapshot storedLayout = resolveStoredLayout(p, baseName, activeKit);
            List<ItemStack> matched = storedLayout != null ? storedLayout.inventory : null;
            if (matched == null && activeKit != currentKit) matched = getBattleKitInventory(activeKit);
            if (matched == null) return;

            ItemStack[] newContents = new ItemStack[36];
            for (int i = 0; i < matched.size() && i < 36; i++) {
                ItemStack item = matched.get(i);
                if (item != null && item.getType() != org.bukkit.Material.AIR)
                    newContents[i] = item.clone();
            }
            if (applyMainInventory(p, newContents)) p.updateInventory();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private List<ItemStack> getBattleKitInventory(Object battleKit) {
        if (battleKit == null) return null;
        try {
            if (hook.mBattleKitGetInv != null) {
                Object raw = hook.mBattleKitGetInv.invoke(battleKit);
                if (raw instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<ItemStack> cast = (List<ItemStack>) raw;
                    return cast;
                }
                if (raw instanceof ItemStack[]) return Arrays.asList((ItemStack[]) raw);
            }

            Object raw = battleKit.getClass().getMethod("getInventory").invoke(battleKit);
            if (raw instanceof List) {
                @SuppressWarnings("unchecked")
                List<ItemStack> cast = (List<ItemStack>) raw;
                return cast;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private ItemStack getBattleKitPiece(Method getter, Object battleKit) {
        if (getter == null || battleKit == null) return null;
        try {
            ItemStack piece = (ItemStack) getter.invoke(battleKit);
            return piece != null ? piece.clone() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private ItemStack[] toMainInventoryArray(List<ItemStack> source) {
        ItemStack[] contents = new ItemStack[36];
        if (source == null) return contents;
        for (int i = 0; i < source.size() && i < 36; i++) {
            ItemStack item = source.get(i);
            if (item != null && item.getType() != org.bukkit.Material.AIR)
                contents[i] = item.clone();
        }
        return contents;
    }

    private boolean applyMainInventory(Player player, ItemStack[] newContents) {
        boolean changed = false;
        for (int i = 0; i < 36; i++) {
            ItemStack next = (newContents != null && i < newContents.length) ? newContents[i] : null;
            if (itemStacksEqual(player.getInventory().getItem(i), next)) continue;
            player.getInventory().setItem(i, next != null ? next.clone() : null);
            changed = true;
        }
        return changed;
    }

    private boolean applyArmorContents(Player player, ItemStack[] armor) {
        if (itemStackArraysEqual(player.getInventory().getArmorContents(), armor)) return false;
        ItemStack[] cloned = new ItemStack[4];
        for (int i = 0; i < cloned.length; i++) {
            if (armor != null && i < armor.length && armor[i] != null) cloned[i] = armor[i].clone();
        }
        player.getInventory().setArmorContents(cloned);
        return true;
    }

    private boolean itemStackArraysEqual(ItemStack[] first, ItemStack[] second) {
        int max = Math.max(first != null ? first.length : 0, second != null ? second.length : 0);
        for (int i = 0; i < max; i++) {
            ItemStack a = (first != null && i < first.length) ? first[i] : null;
            ItemStack b = (second != null && i < second.length) ? second[i] : null;
            if (!itemStacksEqual(a, b)) return false;
        }
        return true;
    }

    private boolean itemStacksEqual(ItemStack first, ItemStack second) {
        if (isEmpty(first) && isEmpty(second)) return true;
        return Objects.equals(first, second);
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == org.bukkit.Material.AIR;
    }

    private Object resolvePreferredLayoutKit(Player player, Object currentKit) {
        Object editedKit = getLastSelectedEditedKit(player);
        if (editedKit == null) return currentKit;
        if (currentKit == null) return editedKit;

        String baseName = normalizeLayoutKey(getBattleKitName(currentKit));
        KitIdentity editedIdentity = extractKitIdentity(editedKit);
        if (matchesKey(editedIdentity.name, baseName, true)
                || matchesKey(editedIdentity.display, baseName, true)
                || matchesKey(editedIdentity.name, baseName, false)
                || matchesKey(editedIdentity.display, baseName, false)) {
            return editedKit;
        }
        return currentKit;
    }

    private Object getLastSelectedEditedKit(Player player) {
        if (player == null || !isHooked() || hook.getAPI() == null || hook.getMGetLastSelectedEditedKit() == null)
            return null;
        try {
            return hook.getMGetLastSelectedEditedKit().invoke(hook.getAPI(), player);
        } catch (Exception ignored) {
            return null;
        }
    }

    private StoredLayoutSnapshot resolveStoredLayout(Player player, String baseName, Object preferredKit) {
        StoredLayoutSnapshot best = null;

        File spFile = getStrikePracticePlayerDataFile(player.getUniqueId());
        if (spFile != null && spFile.exists()) {
            try {
                YamlConfiguration spConfig = YamlConfiguration.loadConfiguration(spFile);
                List<ItemStack> spLayout = findLayoutMatchInPlayerData(spConfig, baseName, preferredKit);
                if (spLayout != null) best = new StoredLayoutSnapshot(spLayout, spFile.lastModified());
            } catch (Exception ignored) {}
        }

        File legacyFile = getLegacyLayoutFile(player.getUniqueId());
        if (legacyFile.exists()) {
            try {
                List<?> legacyKits = YamlConfiguration.loadConfiguration(legacyFile).getList("kits");
                List<ItemStack> legacyLayout = findLayoutMatch(legacyKits, baseName, preferredKit);
                if (legacyLayout != null && (best == null || legacyFile.lastModified() > best.lastModified)) {
                    best = new StoredLayoutSnapshot(legacyLayout, legacyFile.lastModified());
                }
            } catch (Exception ignored) {}
        }

        return best;
    }

    private File getStrikePracticePlayerDataFile(UUID uuid) {
        org.bukkit.plugin.Plugin strikePractice = Bukkit.getPluginManager().getPlugin("StrikePractice");
        if (strikePractice == null) return null;

        File folder = new File(strikePractice.getDataFolder(), "playerdata");
        if (!folder.exists() && !folder.mkdirs()) return null;
        return new File(folder, uuid + ".yml");
    }

    private File getLegacyLayoutFile(UUID uuid) {
        return new File(new File(getDataFolder(), "layouts"), uuid + ".yml");
    }

    private boolean saveLayoutToPreferredStorageInternal(Player player, Object currentKit, List<ItemStack> inventory) {
        if (player == null || inventory == null) return false;

        Object preferredKit = resolvePreferredLayoutKit(player, currentKit);
        Object templateKit = preferredKit != null ? preferredKit : currentKit;
        if (templateKit == null) return false;

        String baseName = normalizeLayoutKey(getBattleKitName(currentKit != null ? currentKit : templateKit));
        List<ItemStack> snapshot = cloneInventoryList(inventory);

        boolean savedToStrikePractice = saveLayoutToStrikePracticePlayerData(
                player.getUniqueId(), baseName, templateKit, snapshot);
        boolean saved = savedToStrikePractice
                || saveLayoutToLegacyLayouts(player.getUniqueId(), baseName, templateKit, snapshot);
        if (saved) {
            applyLayoutToRuntimeKit(templateKit, snapshot);
            if (savedToStrikePractice) removeLegacyLayoutEntry(player.getUniqueId(), baseName, templateKit);
        }
        return saved;
    }

    @SuppressWarnings("unchecked")
    private boolean saveLayoutToStrikePracticePlayerData(UUID uuid, String baseName, Object preferredKit, List<ItemStack> inventory) {
        File spFile = getStrikePracticePlayerDataFile(uuid);
        if (spFile == null) return false;

        try {
            YamlConfiguration config = spFile.exists()
                    ? YamlConfiguration.loadConfiguration(spFile)
                    : new YamlConfiguration();

            boolean updated = false;
            Object customKit = config.get("custom-kit");
            if (customKit != null && matchesKitEntry(customKit, baseName, preferredKit)) {
                config.set("custom-kit", buildUpdatedKitEntry(customKit, preferredKit, inventory));
                updated = true;
            }

            List<Object> kits = (List<Object>) config.getList("kits");
            if (kits == null) kits = new ArrayList<>();

            int matchIndex = findMatchingKitIndex(kits, baseName, preferredKit);
            if (matchIndex >= 0) {
                kits.set(matchIndex, buildUpdatedKitEntry(kits.get(matchIndex), preferredKit, inventory));
                updated = true;
            } else if (!updated) {
                kits.add(buildUpdatedKitEntry(null, preferredKit, inventory));
                updated = true;
            }

            if (!updated) return false;

            config.set("kits", kits);
            config.save(spFile);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private boolean saveLayoutToLegacyLayouts(UUID uuid, String baseName, Object preferredKit, List<ItemStack> inventory) {
        File legacyFile = getLegacyLayoutFile(uuid);
        File parent = legacyFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) return false;

        try {
            YamlConfiguration config = legacyFile.exists()
                    ? YamlConfiguration.loadConfiguration(legacyFile)
                    : new YamlConfiguration();

            List<Object> kits = (List<Object>) config.getList("kits");
            if (kits == null) kits = new ArrayList<>();

            int matchIndex = findMatchingKitIndex(kits, baseName, preferredKit);
            if (matchIndex >= 0) kits.set(matchIndex, buildUpdatedKitEntry(kits.get(matchIndex), preferredKit, inventory));
            else kits.add(buildUpdatedKitEntry(null, preferredKit, inventory));

            config.set("kits", kits);
            config.save(legacyFile);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private void removeLegacyLayoutEntry(UUID uuid, String baseName, Object preferredKit) {
        File legacyFile = getLegacyLayoutFile(uuid);
        if (!legacyFile.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(legacyFile);
            List<Object> kits = (List<Object>) config.getList("kits");
            if (kits == null || kits.isEmpty()) {
                legacyFile.delete();
                return;
            }

            int matchIndex = findMatchingKitIndex(kits, baseName, preferredKit);
            if (matchIndex < 0) return;

            kits.remove(matchIndex);
            if (kits.isEmpty()) {
                legacyFile.delete();
                return;
            }

            config.set("kits", kits);
            config.save(legacyFile);
        } catch (Exception ignored) {}
    }

    private void applyLayoutToRuntimeKit(Object preferredKit, List<ItemStack> inventory) {
        if (preferredKit == null || inventory == null) return;
        try {
            Method setInventory = preferredKit.getClass().getMethod("setInventory", List.class);
            setInventory.invoke(preferredKit, cloneInventoryList(inventory));
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    private Object buildUpdatedKitEntry(Object existingEntry, Object templateKit, List<ItemStack> inventory) {
        Map<String, Object> map = existingEntry instanceof Map
                ? new LinkedHashMap<>((Map<String, Object>) existingEntry)
                : new LinkedHashMap<>();

        map.put("==", "BattleKit");

        String name = existingEntry instanceof Map
                ? map.containsKey("name") ? String.valueOf(map.get("name")) : getBattleKitName(templateKit)
                : getBattleKitName(templateKit);
        ItemStack icon = existingEntry instanceof Map && map.get("icon") instanceof ItemStack
                ? ((ItemStack) map.get("icon")).clone()
                : getBattleKitIcon(templateKit);

        if (name != null && !name.isEmpty()) map.put("name", name);
        if (icon != null) map.put("icon", icon.clone());
        map.put("inventory", cloneInventoryList(inventory));
        return map;
    }

    private List<ItemStack> cloneInventoryList(List<ItemStack> inventory) {
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack item : inventory) copy.add(item != null ? item.clone() : null);
        return copy;
    }

    private String getBattleKitName(Object battleKit) {
        if (battleKit == null) return "";
        try {
            Object raw = battleKit.getClass().getMethod("getName").invoke(battleKit);
            return raw != null ? String.valueOf(raw) : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private ItemStack getBattleKitIcon(Object battleKit) {
        if (battleKit == null) return null;
        try {
            if (hook.mBattleKitGetIcon != null) {
                ItemStack icon = (ItemStack) hook.mBattleKitGetIcon.invoke(battleKit);
                return icon != null ? icon.clone() : null;
            }
            ItemStack icon = (ItemStack) battleKit.getClass().getMethod("getIcon").invoke(battleKit);
            return icon != null ? icon.clone() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizeLayoutKey(String value) {
        return value == null ? "" : ChatColor.stripColor(value).toLowerCase(Locale.ROOT);
    }

    private String extractItemDisplayName(ItemStack icon) {
        if (icon == null || !icon.hasItemMeta() || !icon.getItemMeta().hasDisplayName()) return "";
        return normalizeLayoutKey(icon.getItemMeta().getDisplayName());
    }

    private List<ItemStack> findLayoutMatchInPlayerData(YamlConfiguration config, String baseName, Object preferredKit) {
        Object customKit = config.get("custom-kit");
        if (customKit != null) {
            List<ItemStack> customLayout = findLayoutMatch(Collections.singletonList(customKit), baseName, preferredKit);
            if (customLayout != null) return customLayout;
        }
        return findLayoutMatch(config.getList("kits"), baseName, preferredKit);
    }

    private int findMatchingKitIndex(List<?> kits, String baseName, Object preferredKit) {
        if (kits == null || kits.isEmpty()) return -1;
        for (int pass = 0; pass < 4; pass++) {
            for (int i = 0; i < kits.size(); i++) {
                Object entry = kits.get(i);
                if (entry == null) continue;
                if (matchesKitEntry(entry, baseName, preferredKit, pass)) return i;
            }
        }
        return -1;
    }

    private boolean matchesKitEntry(Object entry, String baseName, Object preferredKit) {
        for (int pass = 0; pass < 4; pass++) {
            if (matchesKitEntry(entry, baseName, preferredKit, pass)) return true;
        }
        return false;
    }

    private boolean matchesKitEntry(Object entry, String baseName, Object preferredKit, int pass) {
        return matchesKitIdentity(extractKitIdentity(entry), baseName,
                normalizeLayoutKey(getBattleKitName(preferredKit)),
                extractItemDisplayName(getBattleKitIcon(preferredKit)),
                pass);
    }

    private boolean matchesKitIdentity(KitIdentity identity, String baseName,
                                       String preferredName, String preferredDisplay, int pass) {
        if (identity == null) return false;
        switch (pass) {
            case 0:
                return matchesKey(identity.name, preferredName, true)
                        || matchesKey(identity.display, preferredName, true)
                        || matchesKey(identity.name, preferredDisplay, true)
                        || matchesKey(identity.display, preferredDisplay, true);
            case 1:
                return matchesKey(identity.name, preferredName, false)
                        || matchesKey(identity.display, preferredName, false)
                        || matchesKey(identity.name, preferredDisplay, false)
                        || matchesKey(identity.display, preferredDisplay, false);
            case 2:
                return matchesKey(identity.name, baseName, true)
                        || matchesKey(identity.display, baseName, true);
            case 3:
                return matchesKey(identity.name, baseName, false)
                        || matchesKey(identity.display, baseName, false);
            default:
                return false;
        }
    }

    private boolean matchesKey(String candidate, String reference, boolean exact) {
        if (candidate == null || candidate.isEmpty() || reference == null || reference.isEmpty()) return false;
        if (exact) return candidate.equals(reference);
        return candidate.startsWith(reference) || reference.startsWith(candidate);
    }

    private KitIdentity extractKitIdentity(Object entry) {
        if (entry == null) return new KitIdentity("", "");
        if (entry instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) entry;
            String name = map.containsKey("name") ? normalizeLayoutKey(String.valueOf(map.get("name"))) : "";
            ItemStack icon = map.get("icon") instanceof ItemStack ? (ItemStack) map.get("icon") : null;
            return new KitIdentity(name, extractItemDisplayName(icon));
        }
        return new KitIdentity(
                normalizeLayoutKey(getBattleKitName(entry)),
                extractItemDisplayName(getBattleKitIcon(entry))
        );
    }

    @SuppressWarnings("unchecked")
    private List<ItemStack> extractInventory(Object entry) {
        if (entry == null) return null;
        if (entry instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) entry;
            if (map.get("inventory") instanceof List) return (List<ItemStack>) map.get("inventory");
            return null;
        }

        try {
            if (hook.mBattleKitGetInv != null) {
                Object raw = hook.mBattleKitGetInv.invoke(entry);
                if (raw instanceof List) return (List<ItemStack>) raw;
                if (raw instanceof ItemStack[]) return Arrays.asList((ItemStack[]) raw);
            }
            Object raw = entry.getClass().getMethod("getInventory").invoke(entry);
            if (raw instanceof List) return (List<ItemStack>) raw;
        } catch (Exception ignored) {}
        return null;
    }

    private List<ItemStack> findLayoutMatch(List<?> kits, String baseName, Object preferredKit) {
        if (kits == null || kits.isEmpty()) return null;
        for (int pass = 0; pass < 4; pass++) {
            for (Object customKit : kits) {
                if (customKit == null || !matchesKitEntry(customKit, baseName, preferredKit, pass)) continue;
                List<ItemStack> inventory = extractInventory(customKit);
                if (inventory != null) return inventory;
            }
        }
        return null;
    }

    private static final class StoredLayoutSnapshot {
        private final List<ItemStack> inventory;
        private final long lastModified;

        private StoredLayoutSnapshot(List<ItemStack> inventory, long lastModified) {
            this.inventory = inventory;
            this.lastModified = lastModified;
        }
    }

    private static final class KitIdentity {
        private final String name;
        private final String display;

        private KitIdentity(String name, String display) {
            this.name = name != null ? name : "";
            this.display = display != null ? display : "";
        }
    }
}
