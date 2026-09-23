package org.blossomsuite.core.config;

public final class KeybindsConfig {
   public static final String MODIFIER_ONLY_KEY = "suitecore.modifier_only";
   public static final String LEGACY_BLOSSOM_MODIFIER_ONLY_KEY = "blossomsuite.modifier_only";
   public static final String LEGACY_MYSTIC_MODIFIER_ONLY_KEY = "mysticsuite.modifier_only";
   public static final int MOD_CTRL = 1;
   public static final int MOD_SHIFT = 2;
   public static final int MOD_ALT = 4;
   public static final int MOD_LEFT_CTRL = 8;
   public static final int MOD_RIGHT_CTRL = 16;
   public static final int MOD_LEFT_SHIFT = 32;
   public static final int MOD_RIGHT_SHIFT = 64;
   public static final int MOD_LEFT_ALT = 128;
   public static final int MOD_RIGHT_ALT = 256;
   public static final int ALL_MODS = 511;
   public KeybindsConfig.Chord openSettings = new KeybindsConfig.Chord();
   public KeybindsConfig.Chord toggleEditMode = new KeybindsConfig.Chord();
   public KeybindsConfig.Chord pauseResume = new KeybindsConfig.Chord();
   public KeybindsConfig.Chord resetSession = new KeybindsConfig.Chord();
   public KeybindsConfig.Chord resetSegment = new KeybindsConfig.Chord();
   public KeybindsConfig.Chord toggleAutoSwapper = new KeybindsConfig.Chord();
   public KeybindsConfig.Chord toggleAutoDropper = new KeybindsConfig.Chord();
   public KeybindsConfig.Chord toggleRentalsPause = new KeybindsConfig.Chord();
   public KeybindsConfig.Chord clearExpiredRentals = new KeybindsConfig.Chord();
   public KeybindsConfig.Chord runAutoDropper = new KeybindsConfig.Chord();
   public KeybindsConfig.Chord runCondense = new KeybindsConfig.Chord();
   public KeybindsConfig.Chord sortInventory = new KeybindsConfig.Chord();
   public KeybindsConfig.Chord sortContainer = new KeybindsConfig.Chord();
   public KeybindsConfig.Chord sortAll = new KeybindsConfig.Chord();
   public KeybindsConfig.Chord depositAllToContainer = new KeybindsConfig.Chord();
   public KeybindsConfig.Chord depositMatchingToContainer = new KeybindsConfig.Chord();
   public KeybindsConfig.Chord withdrawAllFromContainer = new KeybindsConfig.Chord();
   public KeybindsConfig.Chord withdrawMatchingFromContainer = new KeybindsConfig.Chord();
   public KeybindsConfig.Chord toggleMarryChat = new KeybindsConfig.Chord();
   public KeybindsConfig.Chord toggleHolePuncherMode = new KeybindsConfig.Chord();
   public KeybindsConfig.Chord toggleHolePuncherEnabled = new KeybindsConfig.Chord();
   public KeybindsConfig.Chord toggleHolePuncherMarkers = new KeybindsConfig.Chord();
   public KeybindsConfig.Chord setMiningTrack = new KeybindsConfig.Chord();
   public KeybindsConfig.Chord clearMiningTrack = new KeybindsConfig.Chord();
   public KeybindsConfig.Chord toggleMiningTrackIndicator = new KeybindsConfig.Chord();
   public KeybindsConfig.Chord toggleToolLock = new KeybindsConfig.Chord();
   public KeybindsConfig.Chord toggleToolLockSlot = new KeybindsConfig.Chord();
   public KeybindsConfig.Chord useAdvertiser = new KeybindsConfig.Chord();
   public KeybindsConfig.Chord cycleAdvertiserProfile = new KeybindsConfig.Chord();
   public KeybindsConfig.Chord sendWelcomeMessage = new KeybindsConfig.Chord();
   public KeybindsConfig.Chord toggleStaffChat = new KeybindsConfig.Chord();

   public static final class Chord {
      public String key = "";
      public String extraKey = "";
      public int mods = 0;
      public boolean blockVanilla = false;
   }
}
