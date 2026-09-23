package org.blossomsuite.core.config;

public final class VoteConfig {
   public boolean showHud = false;
   public boolean sendData = false;
   public boolean displayCurrentServer = false;
   public VoteConfig.NotifyWhen notifyWhen = VoteConfig.NotifyWhen.OFF;
   public boolean countdownScreenNotification = false;
   public float positionX = 0.5F;
   public float positionY = 0.5F;
   public float scale = 1.0F;
   public float backgroundOpacity = 0.0F;

   public void toggleShowHud() {
      this.showHud = !this.showHud;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleDisplayCurrentServer() {
      this.displayCurrentServer = !this.displayCurrentServer;
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

   public enum NotifyWhen {
      OFF,
      COUNTDOWN,
      ONGOING,
      BOTH;
   }
}
