package org.blossomsuite.core.hud;

import org.blossomsuite.core.config.SuiteConfig;

public final class HudScaleUtil {
   private static final int SCREEN_MARGIN = 4;
   private static final float AUTO_MIN_SCALE = 0.1F;

   private HudScaleUtil() {
   }

   public static float classicScale(float savedScale, float classicMin, float max) {
      return clamp(savedScale, classicMin, max);
   }

   public static float scaleFor(float savedScale, float classicMin, float max, int baseW, int baseH, int screenW, int screenH) {
      SuiteConfig.HudScalingMode mode = SuiteConfig.INSTANCE.hudScalingMode;
      float scale = mode == SuiteConfig.HudScalingMode.GUI_ADAPTIVE ? 1.0F : classicScale(savedScale, classicMin, max);
      if (mode == SuiteConfig.HudScalingMode.CLASSIC) {
         return scale;
      }

      int availableW = Math.max(1, screenW - 8);
      int availableH = Math.max(1, screenH - 8);
      float fitW = baseW <= 0 ? scale : (float)availableW / baseW;
      float fitH = baseH <= 0 ? scale : (float)availableH / baseH;
      return clamp(Math.min(scale, Math.min(fitW, fitH)), 0.1F, max);
   }

   public static float savedScaleForDrawnScale(float drawnScale, float classicMin, float max) {
      return clamp(drawnScale, classicMin, max);
   }

   private static float clamp(float value, float min, float max) {
      if (value < min) {
         return min;
      } else {
         return value > max ? max : value;
      }
   }
}
