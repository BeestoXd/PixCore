package com.pixra.pixCore.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class StrikePracticeHook {

    private Object strikePracticeAPI;

    private Method mGetFight;
    private Method mIsInFight;
    private Method mGetKit;
    private Method mGetOpponents;
    private Method mGetFirstPlayer;
    private Method mGetSecondPlayer;
    private Method mGetPlayers;
    private Method mGetTeammates;
    private Method mPlayersAreTeammates;
    private Method mPlayersAreOpponents;
    private Method mGetPlayersInFight;
    private Method mGetPlayerNames;
    private Method mGetArena;
    private Method mArenaGetMin;
    private Method mArenaGetMax;
    private Method mArenaGetSpawn1;
    private Method mArenaGetSpawn2;
    private Method mHandleDeath;
    private Method mGetBlockChanges;

    private Method mAddSpectator;
    private Method mRemoveSpectator;
    private Method mGetLastSelectedEditedKit;
    private Method mRespawnInFight;
    private Method mKitApply;

    private Method mIsBed1Broken;
    private Method mIsBed2Broken;
    private Method mSetBedwars;
    private Class<?> clsAbstractFight;
    private Field fBed1Broken;
    private Field fBed2Broken;

    private Class<?> clsBestOfFight;
    private Method mGetBestOf;
    private Class<?> clsBestOf;
    private Method mGetCurrentRound;
    private Method mGetRounds;
    private Method mHandleWin;

    public Method mBattleKitGetIcon;
    public Method mBattleKitGetKitStatic;
    public Method mBattleKitGiveKit;
    public Method mBattleKitGetInv;
    public Method mBattleKitGetHelmet;
    public Method mBattleKitGetChestplate;
    public Method mBattleKitGetLeggings;
    public Method mBattleKitGetBoots;

    private boolean hooked = false;
    public boolean arenaReflectionLoaded = false;
    public boolean bestOfReflectionLoaded = false;

    public void hook() {
        if (Bukkit.getPluginManager().getPlugin("StrikePractice") == null) return;
        try {
            Class<?> mainClass = Class.forName("ga.strikepractice.StrikePractice");
            this.strikePracticeAPI = mainClass.getMethod("getAPI").invoke(null);
            Class<?> apiClass = this.strikePracticeAPI.getClass();

            this.mGetFight               = apiClass.getMethod("getFight", Player.class);
            this.mIsInFight              = apiClass.getMethod("isInFight", Player.class);
            this.mGetKit                 = apiClass.getMethod("getKit", Player.class);
            this.mAddSpectator           = apiClass.getMethod("addSpectator", Player.class);
            this.mRemoveSpectator        = apiClass.getMethod("removeSpectator", Player.class, boolean.class);
            this.mGetLastSelectedEditedKit = apiClass.getMethod("getLastSelectedEditedKit", Player.class);
            this.mRespawnInFight         = apiClass.getMethod("respawnInFight", Player.class);

            try { this.mKitApply = mGetKit.getReturnType().getMethod("giveKit", Player.class); }
            catch (Exception ignored) { this.mKitApply = null; }

            Class<?> fightClass = Class.forName("ga.strikepractice.fights.Fight");
            this.mGetOpponents = fightClass.getMethod("getOpponents", Player.class);
            try { this.mHandleDeath        = fightClass.getMethod("handleDeath", Player.class); }         catch (Exception ignored) {}
            for (String n : new String[]{"getBlockChanges", "getChangedBlocks", "getBlockCache"}) {
                try { this.mGetBlockChanges = fightClass.getMethod(n); break; } catch (Exception ignored) {}
            }
            try { this.mGetTeammates       = fightClass.getMethod("getTeammates", Player.class); }        catch (Exception ignored) {}
            try { this.mPlayersAreTeammates  = fightClass.getMethod("playersAreTeammates", Player.class, Player.class); } catch (Exception ignored) {}
            try { this.mPlayersAreOpponents  = fightClass.getMethod("playersAreOpponents", Player.class, Player.class); } catch (Exception ignored) {}
            try {
                this.mGetPlayers = fightClass.getMethod("getPlayersInFight");
            } catch (Exception e) {
                try { this.mGetPlayers = fightClass.getMethod("getPlayers"); } catch (Exception ignored) {}
            }
            try { this.mGetPlayersInFight = fightClass.getMethod("getPlayersInFight"); } catch (Exception ignored) {}
            try { this.mGetPlayerNames    = fightClass.getMethod("getPlayerNames"); }    catch (Exception ignored) {}
            try {
                this.mGetArena = fightClass.getMethod("getArena");
            } catch (Exception e) {
                try { this.mGetArena = Class.forName("ga.strikepractice.fights.AbstractFight").getMethod("getArena"); }
                catch (Exception ignored) {}
            }

            try {
                Class<?> arenaClass  = Class.forName("ga.strikepractice.arena.Arena");
                this.mArenaGetMin    = arenaClass.getMethod("getCorner1");
                this.mArenaGetMax    = arenaClass.getMethod("getCorner2");
                try { this.mArenaGetSpawn1 = arenaClass.getMethod("getSpawn1"); } catch (Exception ignored) {}
                try { this.mArenaGetSpawn2 = arenaClass.getMethod("getSpawn2"); } catch (Exception ignored) {}
                this.arenaReflectionLoaded = true;
            } catch (Exception e) {
                this.arenaReflectionLoaded = false;
            }

            try {
                this.clsBestOfFight    = Class.forName("ga.strikepractice.fights.BestOfFight");
                this.mGetBestOf        = this.clsBestOfFight.getMethod("getBestOf");
                this.clsBestOf         = Class.forName("ga.strikepractice.fights.duel.BestOf");
                this.mGetCurrentRound  = this.clsBestOf.getMethod("getCurrentRound");
                this.mGetRounds        = this.clsBestOf.getMethod("getRounds");
                this.mHandleWin        = this.clsBestOf.getMethod("handleWin", java.util.UUID.class);
                this.bestOfReflectionLoaded = true;
            } catch (Exception e) {
                this.bestOfReflectionLoaded = false;
            }

            try {
                this.clsAbstractFight  = Class.forName("ga.strikepractice.fights.AbstractFight");
                this.mIsBed1Broken     = this.clsAbstractFight.getMethod("isBed1Broken");
                this.mIsBed2Broken     = this.clsAbstractFight.getMethod("isBed2Broken");
                this.fBed1Broken       = this.clsAbstractFight.getDeclaredField("bed1Broken");
                this.fBed1Broken.setAccessible(true);
                this.fBed2Broken       = this.clsAbstractFight.getDeclaredField("bed2Broken");
                this.fBed2Broken.setAccessible(true);
                Class<?> duelClass     = Class.forName("ga.strikepractice.fights.duel.Duel");
                this.mGetFirstPlayer   = duelClass.getMethod("getFirstPlayer");
                try { this.mGetSecondPlayer = duelClass.getMethod("getSecondPlayer"); } catch (Exception ignored) {}
            } catch (Exception ignored) {}

            try {
                Class<?> battleKitClass      = Class.forName("ga.strikepractice.battlekit.BattleKit");
                this.mSetBedwars             = battleKitClass.getMethod("setBedwars", boolean.class);
                this.mBattleKitGetIcon       = battleKitClass.getMethod("getIcon");
                this.mBattleKitGetKitStatic  = battleKitClass.getMethod("getKit", Player.class, ItemStack.class, boolean.class);
                this.mBattleKitGiveKit       = battleKitClass.getMethod("giveKit", Player.class);
                this.mBattleKitGetInv        = battleKitClass.getMethod("getInv");
                this.mBattleKitGetHelmet     = battleKitClass.getMethod("getHelmet");
                this.mBattleKitGetChestplate = battleKitClass.getMethod("getChestplate");
                this.mBattleKitGetLeggings   = battleKitClass.getMethod("getLeggings");
                this.mBattleKitGetBoots      = battleKitClass.getMethod("getBoots");
            } catch (Exception ignored) {}

            this.hooked = true;

        } catch (Exception e) {
            this.hooked = false;
        }
    }

    public boolean isInFight(Player player) {
        if (!hooked || strikePracticeAPI == null || mIsInFight == null) return false;
        try { return (boolean) mIsInFight.invoke(strikePracticeAPI, player); }
        catch (Exception e) { return false; }
    }

    public String getKitName(Player player) {
        if (!hooked || mGetKit == null || strikePracticeAPI == null) return null;
        try {
            Object kit = mGetKit.invoke(strikePracticeAPI, player);
            if (kit != null) return (String) kit.getClass().getMethod("getName").invoke(kit);
        } catch (Exception ignored) {}
        return null;
    }

    public void addSpectator(Player p) {
        if (!hooked || mAddSpectator == null) return;
        try { mAddSpectator.invoke(strikePracticeAPI, p); } catch (Exception ignored) {}
    }

    public void removeSpectator(Player p, boolean clearAndTeleport) {
        if (!hooked || mRemoveSpectator == null) return;
        try { mRemoveSpectator.invoke(strikePracticeAPI, p, clearAndTeleport); } catch (Exception ignored) {}
    }

    public void respawnInFight(Player p) {
        if (!hooked || mRespawnInFight == null) return;
        try { mRespawnInFight.invoke(strikePracticeAPI, p); } catch (Exception ignored) {}
    }

    public boolean isHooked()           { return hooked; }
    public Object  getAPI()             { return strikePracticeAPI; }

    public Method getMGetFight()             { return mGetFight; }
    public Method getMIsInFight()            { return mIsInFight; }
    public Method getMGetKit()               { return mGetKit; }
    public Method getMGetFirstPlayer()       { return mGetFirstPlayer; }
    public Method getMGetSecondPlayer()      { return mGetSecondPlayer; }
    public Method getMGetOpponents()         { return mGetOpponents; }
    public Method getMPlayersAreTeammates()  { return mPlayersAreTeammates; }
    public Method getMGetTeammates()         { return mGetTeammates; }
    public Method getMGetPlayersInFight()    { return mGetPlayersInFight; }
    public Method getMGetPlayers()           { return mGetPlayers; }
    public Method getMGetArena()             { return mGetArena; }
    public Method getMArenaGetMin()          { return mArenaGetMin; }
    public Method getMArenaGetMax()          { return mArenaGetMax; }
    public Method getMArenaGetSpawn1()       { return mArenaGetSpawn1; }
    public Method getMArenaGetSpawn2()       { return mArenaGetSpawn2; }
    public Method getMIsBed1Broken()         { return mIsBed1Broken; }
    public Method getMIsBed2Broken()         { return mIsBed2Broken; }
    public Method getMSetBedwars()           { return mSetBedwars; }
    public Method getMKitApply()             { return mKitApply; }
    public Method getMHandleWin()            { return mHandleWin; }
    public Method getMHandleDeath()          { return mHandleDeath; }
    public Method getMGetBestOf()            { return mGetBestOf; }

    public Class<?> getClsAbstractFight()  { return clsAbstractFight; }
    public Class<?> getClsBestOfFight()    { return clsBestOfFight; }

    public Field getFBed1Broken()          { return fBed1Broken; }
    public Field getFBed2Broken()          { return fBed2Broken; }

    public Method getMGetLastSelectedEditedKit() { return mGetLastSelectedEditedKit; }
    public Method getMGetBlockChanges()          { return mGetBlockChanges; }
}
