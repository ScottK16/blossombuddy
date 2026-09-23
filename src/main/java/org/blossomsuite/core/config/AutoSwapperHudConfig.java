package org.blossomsuite.core.config;

public final class AutoSwapperHudConfig {
   public boolean showHud = false;
   public boolean showHeader = true;
   public float positionX = -1.0F;
   public float positionY = -1.0F;
   public float scale = 1.0F;
   public float backgroundOpacity = 0.2F;

   public void toggleShowHud() {
      this.showHud = !this.showHud;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleShowHeader() {
      this.showHeader = !this.showHeader;
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
}
