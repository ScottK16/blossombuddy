package org.blossomsuite.core.config;

import java.util.ArrayList;
import java.util.List;

public final class QolConfig {
   public boolean autoSwapperEnabled = false;
   public int autoSwapperActiveProfile = 0;
   public final List<QolConfig.AutoSwapperProfile> autoSwapperProfiles = new ArrayList<>();
   public int autoSwapperActiveCustomGroup = 0;
   public final List<QolConfig.AutoSwapCustomGroup> autoSwapperCustomGroups = new ArrayList<>();
   public int autoSwapperDebounceMs = 100;
   public static final int DEBOUNCE_MIN_MS = 0;
   public static final int DEBOUNCE_MAX_MS = 1000;
   public boolean autoDropperEnabled = false;
   public boolean autoDropperOnPickup = true;
   public boolean autoDropperOnSneak = false;
   public int autoDropperDelayMs = 50;
   public int autoDropperMaxStacksPerTick = 8;
   public boolean autoDropperIncludeHotbar = false;
   public boolean autoDropperProtectSelectedSlot = true;
   public boolean autoDropperPauseWhileScreenOpen = true;
   public boolean autoDropperPauseWhileSneaking = false;
   public boolean autoDropperPauseOnPlayerAttack = true;
   public boolean autoDropperPauseOnAttackingPlayer = true;
   public boolean autoDropperPauseOnTargetingPlayer = true;
   public int autoDropperActiveCustomGroup = 0;
   public final List<QolConfig.AutoDropRule> autoDropperRules = new ArrayList<>();
   public final List<QolConfig.AutoDropCustomGroup> autoDropperCustomGroups = new ArrayList<>();
   public boolean inventorySortEnabled = true;
   public boolean inventorySortStackMatching = false;
   public boolean inventoryManagementShowContainerButtons = true;
   public boolean inventoryManagementDepositIgnoresLockedSlots = true;
   public long inventorySortLockedSlotsMask = 0L;
   public boolean autoFlyOnRwWorldLoad = false;
   public boolean fishingEnabled = false;
   public String fishingSoundId = "minecraft:entity.experience_orb.pickup";
   public float fishingVolume = 1.0F;
   public float fishingPitch = 1.0F;
   public int fishingCooldownMs = 250;
   public boolean toolLockEnabled = false;
   public int toolLockActiveProfile = 0;
   public final List<QolConfig.ToolLockProfile> toolLockProfiles = new ArrayList<>();
   public boolean toolLockBlockOnInteractBlock = false;
   public int toolLockLockedSlotsMask = 0;
   public int toolLockLeftClickLockedSlotsMask = 0;
   public QolConfig.ToolLockReportMode toolLockReportMode = QolConfig.ToolLockReportMode.NOTICE;
   public boolean holePuncherEnabled = true;
   public boolean holePuncherGuided = false;
   public int holePuncherVisualMode = 0;
   public boolean holePuncherMarkersEnabled = true;
   public int holePuncherAutoSwapSlot = 1;
   public boolean holePuncherFailureScreenMessage = false;
   public boolean holePuncherFailureChatMessage = false;
   public boolean holePuncherFailureNoticeMessage = false;
   public boolean miningResumeAfterDrops = true;
   public boolean miningTrackIndicator = false;
   public String miningTrackDir = "";
   public int miningTrackCoord = 0;
   public int miningTrackRangeBlocks = 0;
   public int miningTrackLineThickness = 1;
   public QolConfig.MiningTrackColorMode miningTrackOnTrackColorMode = QolConfig.MiningTrackColorMode.SOLID;
   public int miningTrackOnTrackR = 38;
   public int miningTrackOnTrackG = 255;
   public int miningTrackOnTrackB = 51;
   public QolConfig.MiningTrackColorMode miningTrackOffTrackColorMode = QolConfig.MiningTrackColorMode.SOLID;
   public int miningTrackOffTrackR = 255;
   public int miningTrackOffTrackG = 51;
   public int miningTrackOffTrackB = 51;
   public boolean crosshairTintEnabled = false;
   public boolean crosshairRainbow = false;
   public int crosshairR = 255;
   public int crosshairG = 255;
   public int crosshairB = 255;
   public float crosshairA = 1.0F;
   public int crosshairRainbowPeriodMs = 2000;
   public QolConfig.CrosshairShape crosshairShape = QolConfig.CrosshairShape.VANILLA;
   public int crosshairSize = 8;
   public int crosshairGap = 2;
   public int crosshairThickness = 2;
   public boolean targetBlockOutlineEnabled = false;
   public QolConfig.TargetBlockOutlineColorMode targetBlockOutlineColorMode = QolConfig.TargetBlockOutlineColorMode.SOLID;
   public int targetBlockOutlineR = 255;
   public int targetBlockOutlineG = 255;
   public int targetBlockOutlineB = 255;
   public float targetBlockOutlineA = 1.0F;
   public static final float FISHING_VOLUME_MIN = 0.0F;
   public static final float FISHING_VOLUME_MAX = 1.0F;
   public static final float FISHING_PITCH_MIN = 0.5F;
   public static final float FISHING_PITCH_MAX = 2.0F;
   public static final int FISHING_COOLDOWN_MIN_MS = 0;
   public static final int FISHING_COOLDOWN_MAX_MS = 1000;

   public void toggleAutoSwapperEnabled() {
      this.autoSwapperEnabled = !this.autoSwapperEnabled;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void setFishingSoundId(String soundId) {
      this.fishingSoundId = soundId;
      SuiteConfig.INSTANCE.markDirty();
   }

   public boolean isInventorySortSlotLocked(int slotIndex) {
      return slotIndex >= 0 && slotIndex <= 35 ? (this.inventorySortLockedSlotsMask & 1L << slotIndex) != 0L : false;
   }

   public void toggleInventorySortSlot(int slotIndex) {
      if (slotIndex >= 0 && slotIndex <= 35) {
         this.inventorySortLockedSlotsMask ^= 1L << slotIndex;
         SuiteConfig.INSTANCE.markDirty();
      }
   }

   public void clearInventorySortLocks() {
      this.inventorySortLockedSlotsMask = 0L;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void setFishingVolume(float volume) {
      this.fishingVolume = Math.max(0.0F, Math.min(1.0F, volume));
      SuiteConfig.INSTANCE.markDirty();
   }

   public void setFishingPitch(float pitch) {
      this.fishingPitch = Math.max(0.5F, Math.min(2.0F, pitch));
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleToolLockEnabled() {
      this.toolLockEnabled = !this.toolLockEnabled;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleToolLockBlockOnInteractBlock() {
      this.toolLockBlockOnInteractBlock = !this.toolLockBlockOnInteractBlock;
      this.syncActiveToolLockProfile();
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleToolLockSlot(int hotbarIndex0to8) {
      if (hotbarIndex0to8 >= 0 && hotbarIndex0to8 <= 8) {
         int bit = 1 << hotbarIndex0to8;
         boolean wasLocked = (this.toolLockLockedSlotsMask & bit) != 0;
         this.toolLockLockedSlotsMask ^= bit;
         boolean nowLocked = (this.toolLockLockedSlotsMask & bit) != 0;
         if (!wasLocked && nowLocked) {
            this.toolLockEnabled = true;
         }

         this.syncActiveToolLockProfile();
         SuiteConfig.INSTANCE.markDirty();
      }
   }

   public boolean isToolLockSlotLocked(int hotbarIndex0to8) {
      return hotbarIndex0to8 >= 0 && hotbarIndex0to8 <= 8 ? (this.toolLockLockedSlotsMask & 1 << hotbarIndex0to8) != 0 : false;
   }

   public boolean isToolLockLeftClickSlotLocked(int hotbarIndex0to8) {
      return hotbarIndex0to8 >= 0 && hotbarIndex0to8 <= 8 ? (this.toolLockLeftClickLockedSlotsMask & 1 << hotbarIndex0to8) != 0 : false;
   }

   public void toggleToolLockLeftClickSlot(int hotbarIndex0to8) {
      if (hotbarIndex0to8 >= 0 && hotbarIndex0to8 <= 8) {
         int bit = 1 << hotbarIndex0to8;
         this.toolLockLeftClickLockedSlotsMask ^= bit;
         if ((this.toolLockLeftClickLockedSlotsMask & bit) != 0) {
            this.toolLockEnabled = true;
         }

         this.syncActiveToolLockProfile();
         SuiteConfig.INSTANCE.markDirty();
      }
   }

   public void cycleToolLockReportMode() {
      this.toolLockReportMode = switch (this.toolLockReportMode == null ? QolConfig.ToolLockReportMode.NOTICE : this.toolLockReportMode) {
         case OFF -> QolConfig.ToolLockReportMode.CHAT;
         case CHAT -> QolConfig.ToolLockReportMode.NOTICE;
         case NOTICE -> QolConfig.ToolLockReportMode.OFF;
      };
      this.syncActiveToolLockProfile();
      SuiteConfig.INSTANCE.markDirty();
   }

   public QolConfig.ToolLockProfile getActiveToolLockProfile() {
      this.ensureToolLockProfiles();
      int idx = this.clampToolLockActiveProfile();
      return this.toolLockProfiles.get(idx);
   }

   public void applyToolLockProfile(int index) {
      this.ensureToolLockProfiles();
      this.toolLockActiveProfile = Math.max(0, Math.min(index, this.toolLockProfiles.size() - 1));
      QolConfig.ToolLockProfile profile = this.toolLockProfiles.get(this.toolLockActiveProfile);
      this.applyToolLockProfile(profile);
      SuiteConfig.INSTANCE.markDirty();
   }

   public void addToolLockProfile(String name) {
      this.ensureToolLockProfiles();
      this.syncActiveToolLockProfile();
      QolConfig.ToolLockProfile profile = new QolConfig.ToolLockProfile();
      profile.name = name != null && !name.isBlank() ? name.trim() : "Profile " + (this.toolLockProfiles.size() + 1);
      this.toolLockProfiles.add(profile);
      this.applyToolLockProfile(this.toolLockProfiles.size() - 1);
   }

   public void deleteActiveToolLockProfile() {
      this.ensureToolLockProfiles();
      if (this.toolLockProfiles.size() > 1) {
         int idx = this.clampToolLockActiveProfile();
         this.toolLockProfiles.remove(idx);
         if (this.toolLockActiveProfile >= this.toolLockProfiles.size()) {
            this.toolLockActiveProfile = this.toolLockProfiles.size() - 1;
         }

         this.applyToolLockProfile(this.toolLockActiveProfile);
      }
   }

   public void renameActiveToolLockProfile(String name) {
      QolConfig.ToolLockProfile profile = this.getActiveToolLockProfile();
      profile.name = name != null && !name.isBlank() ? name.trim() : "Profile";
      SuiteConfig.INSTANCE.markDirty();
   }

   public void ensureToolLockProfiles() {
      if (this.toolLockProfiles.isEmpty()) {
         QolConfig.ToolLockProfile profile = new QolConfig.ToolLockProfile();
         profile.name = "Default";
         profile.blockOnInteractBlock = this.toolLockBlockOnInteractBlock;
         profile.lockedSlotsMask = sanitizeToolLockMask(this.toolLockLockedSlotsMask);
         profile.leftClickLockedSlotsMask = sanitizeToolLockMask(this.toolLockLeftClickLockedSlotsMask);
         profile.reportMode = this.toolLockReportMode == null ? QolConfig.ToolLockReportMode.NOTICE : this.toolLockReportMode;
         this.toolLockProfiles.add(profile);
         this.toolLockActiveProfile = 0;
      }

      this.clampToolLockActiveProfile();
   }

   public void syncActiveToolLockProfile() {
      this.ensureToolLockProfiles();
      QolConfig.ToolLockProfile profile = this.toolLockProfiles.get(this.toolLockActiveProfile);
      profile.blockOnInteractBlock = this.toolLockBlockOnInteractBlock;
      profile.lockedSlotsMask = sanitizeToolLockMask(this.toolLockLockedSlotsMask);
      profile.leftClickLockedSlotsMask = sanitizeToolLockMask(this.toolLockLeftClickLockedSlotsMask);
      profile.reportMode = this.toolLockReportMode == null ? QolConfig.ToolLockReportMode.NOTICE : this.toolLockReportMode;
   }

   public void applyActiveToolLockProfile() {
      this.ensureToolLockProfiles();
      this.applyToolLockProfile(this.toolLockProfiles.get(this.toolLockActiveProfile));
   }

   private void applyToolLockProfile(QolConfig.ToolLockProfile profile) {
      if (profile == null) {
         profile = new QolConfig.ToolLockProfile();
      }

      this.toolLockBlockOnInteractBlock = profile.blockOnInteractBlock;
      this.toolLockLockedSlotsMask = sanitizeToolLockMask(profile.lockedSlotsMask);
      this.toolLockLeftClickLockedSlotsMask = sanitizeToolLockMask(profile.leftClickLockedSlotsMask);
      this.toolLockReportMode = profile.reportMode == null ? QolConfig.ToolLockReportMode.NOTICE : profile.reportMode;
      if ((this.toolLockLockedSlotsMask != 0 || this.toolLockLeftClickLockedSlotsMask != 0) && !this.toolLockEnabled) {
         this.toolLockEnabled = true;
      }
   }

   private int clampToolLockActiveProfile() {
      if (this.toolLockProfiles.isEmpty()) {
         this.toolLockActiveProfile = 0;
         return 0;
      }

      if (this.toolLockActiveProfile < 0) {
         this.toolLockActiveProfile = 0;
      }

      if (this.toolLockActiveProfile >= this.toolLockProfiles.size()) {
         this.toolLockActiveProfile = this.toolLockProfiles.size() - 1;
      }

      return this.toolLockActiveProfile;
   }

   public static int sanitizeToolLockMask(int mask) {
      return Math.max(0, Math.min(511, mask));
   }

   public void toggleHolePuncherEnabled() {
      this.holePuncherEnabled = !this.holePuncherEnabled;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleHolePuncherMarkersEnabled() {
      this.holePuncherMarkersEnabled = !this.holePuncherMarkersEnabled;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleHolePuncherFailureScreenMessage() {
      this.holePuncherFailureScreenMessage = !this.holePuncherFailureScreenMessage;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleHolePuncherFailureChatMessage() {
      this.holePuncherFailureChatMessage = !this.holePuncherFailureChatMessage;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleHolePuncherFailureNoticeMessage() {
      this.holePuncherFailureNoticeMessage = !this.holePuncherFailureNoticeMessage;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void cycleHolePuncherAutoSwapSlot() {
      int s = this.holePuncherAutoSwapSlot;
      if (s < 1) {
         s = 1;
      }

      if (s > 9) {
         s = 9;
      }

      if (++s > 9) {
         s = 1;
      }

      this.holePuncherAutoSwapSlot = s;
      SuiteConfig.INSTANCE.markDirty();
   }

   public static final class AutoDropCustomGroup {
      public String id = "";
      public String name = "Custom Group";
      public boolean enabled = true;
      public final List<QolConfig.AutoDropGroupItem> items = new ArrayList<>();
      public final List<String> itemIds = new ArrayList<>();
   }

   public static final class AutoDropGroupItem {
      public String itemId = "minecraft:cobblestone";
      public int minimumAmount = 0;
      public int keepItems = 0;
      public int keepStacks = 0;
      public String componentFilter = "";
   }

   public static final class AutoDropRule {
      public QolConfig.AutoDropTargetType type = QolConfig.AutoDropTargetType.ITEM;
      public String itemId = "minecraft:cobblestone";
      public String customGroupId = "";
      public int minimumAmount = 0;
      public int keepItems = 0;
      public int keepStacks = 0;
      public boolean enabled = true;
      public String componentFilter = "";
   }

   public enum AutoDropTargetType {
      ITEM,
      CUSTOM;
   }

   public static final class AutoSwapCustomGroup {
      public String id = "";
      public String name = "Custom Group";
      public final List<String> blockIds = new ArrayList<>();
   }

   public enum AutoSwapGrouping {
      ORES,
      LOGS,
      PLANKS,
      SHOVEL_MINEABLE,
      PICKAXE_MINEABLE,
      AXE_MINEABLE,
      HOE_MINEABLE,
      SHEARS_MINEABLE,
      SWORD_EFFICIENT,
      LEAVES,
      MUSHROOMS,
      SPAWNERS,
      DIRT_LIKE,
      SAND_LIKE,
      GLASS,
      WOOL,
      SAPLINGS,
      CROPS,
      FLOWERS,
      SMALL_FLOWERS,
      AMETHYST,
      LIGHT_EMITTING,
      DECORATIVE_LIGHTS,
      TERRACOTTA,
      CONCRETE,
      ICE,
      RAILS,
      REDSTONE_COMPONENTS;
   }

   public static final class AutoSwapRule {
      public QolConfig.AutoSwapTargetType type = QolConfig.AutoSwapTargetType.GROUP;
      public QolConfig.AutoSwapGrouping group = QolConfig.AutoSwapGrouping.ORES;
      public String blockId = "minecraft:stone";
      public String customGroupId = "";
      public int slot = 1;
   }

   public enum AutoSwapTargetType {
      GROUP,
      BLOCK,
      CUSTOM;
   }

   public static final class AutoSwapperProfile {
      public String name = "Profile";
      public boolean useDefaultTool = true;
      public int defaultSlot = 1;
      public final List<QolConfig.AutoSwapRule> rules = new ArrayList<>();
   }

   public enum CrosshairShape {
      VANILLA,
      PLUS,
      DOT,
      CIRCLE,
      SQUARE,
      X,
      T;
   }

   public enum MiningTrackColorMode {
      SOLID,
      RAINBOW;
   }

   public enum TargetBlockOutlineColorMode {
      SOLID,
      RAINBOW;
   }

   public static final class ToolLockProfile {
      public String name = "Default";
      public boolean blockOnInteractBlock = false;
      public int lockedSlotsMask = 0;
      public int leftClickLockedSlotsMask = 0;
      public QolConfig.ToolLockReportMode reportMode = QolConfig.ToolLockReportMode.NOTICE;
   }

   public enum ToolLockReportMode {
      OFF,
      CHAT,
      NOTICE;
   }
}
