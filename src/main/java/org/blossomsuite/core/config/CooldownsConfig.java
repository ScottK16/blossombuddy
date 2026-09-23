package org.blossomsuite.core.config;

import org.blossomsuite.core.cooldowns.CooldownsMode;

public final class CooldownsConfig {
   public boolean showHud = true;
   public boolean showHotbar = true;
   public CooldownsConfig.HotbarIndicatorStyle hotbarIndicatorStyle = CooldownsConfig.HotbarIndicatorStyle.TOP_BAR;
   public boolean legacyActiveOnly = false;
   public boolean trackInventory = true;
   public boolean trackOffhand = true;
   public boolean trackArmor = true;
   public CooldownsConfig.InventoryHudMode inventoryHudMode = CooldownsConfig.InventoryHudMode.ONLY_USABLE;
   public float positionX = 0.5F;
   public float positionY = 0.5F;
   public float scale = 1.0F;
   public float backgroundOpacity = 0.0F;
   public CooldownsMode mode = CooldownsMode.FULL;
   public boolean completeSound = true;
   public float completeSoundVolume = 0.6F;
   public boolean completeMessage = true;
   public CooldownsConfig.CompleteMessageLocation completeMessageLocation = CooldownsConfig.CompleteMessageLocation.CHAT;
   public int completeThresholdInSecs = 60;

   public void toggleShowHud() {
      this.showHud = !this.showHud;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleShowHotbar() {
      this.showHotbar = !this.showHotbar;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void cycleHotbarIndicatorStyle() {
      CooldownsConfig.HotbarIndicatorStyle[] all = CooldownsConfig.HotbarIndicatorStyle.values();
      int i = this.hotbarIndicatorStyle == null ? 0 : this.hotbarIndicatorStyle.ordinal();
      this.hotbarIndicatorStyle = all[(i + 1) % all.length];
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleLegacyActiveOnly() {
      this.legacyActiveOnly = !this.legacyActiveOnly;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleTrackInventory() {
      this.trackInventory = !this.trackInventory;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleTrackOffhand() {
      this.trackOffhand = !this.trackOffhand;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleTrackArmor() {
      this.trackArmor = !this.trackArmor;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void cycleInventoryHudMode() {
      CooldownsConfig.InventoryHudMode[] all = CooldownsConfig.InventoryHudMode.values();
      int i = this.inventoryHudMode == null ? 0 : this.inventoryHudMode.ordinal();
      this.inventoryHudMode = all[(i + 1) % all.length];
      SuiteConfig.INSTANCE.markDirty();
   }

   public void setPosition(float positionX, float positionY) {
      this.positionX = positionX;
      this.positionY = positionY;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void setScale(float scale) {
      this.scale = scale;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void setCooldownsHudMode(CooldownsMode mode) {
      this.mode = mode;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleCompleteSound() {
      this.completeSound = !this.completeSound;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleCompleteMessage() {
      this.completeMessage = !this.completeMessage;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void cycleCompleteMessageLocation() {
      CooldownsConfig.CompleteMessageLocation[] all = CooldownsConfig.CompleteMessageLocation.values();
      int i = this.completeMessageLocation == null ? 0 : this.completeMessageLocation.ordinal();
      this.completeMessageLocation = all[(i + 1) % all.length];
      SuiteConfig.INSTANCE.markDirty();
   }

   public void setCompleteThresholdInSecs(int completeThresholdInSecs) {
      this.completeThresholdInSecs = Math.max(completeThresholdInSecs, 0);
      SuiteConfig.INSTANCE.markDirty();
   }

   public void setCompleteSoundVolume(float completeSoundVolume) {
      if (completeSoundVolume > 1.0F) {
         this.completeSoundVolume = 1.0F;
      } else {
         this.completeSoundVolume = completeSoundVolume;
      }

      SuiteConfig.INSTANCE.markDirty();
   }

   public enum CompleteMessageLocation {
      CHAT,
      SCREEN;
   }

   public enum HotbarIndicatorStyle {
      TOP_BAR,
      VANILLA_SWIPE;
   }

   public enum InventoryHudMode {
      ALL_INVENTORY,
      ONLY_USABLE;
   }
}
