package org.blossomsuite.core.config;

public final class MiningHudConfig {
   public boolean showHud = false;
   public float positionX = 0.5F;
   public float positionY = 0.55F;
   public float scale = 1.0F;
   public float backgroundOpacity = 0.15F;

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
