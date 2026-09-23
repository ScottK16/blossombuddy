package org.blossomsuite.core.util;

import org.blossomsuite.core.config.QolConfig;
import java.awt.Color;

public final class TargetBlockOutlineColorUtil {
   private TargetBlockOutlineColorUtil() {
   }

   public static int computeArgb(QolConfig q, long nowMs) {
      if (q == null) {
         return -1;
      }

      int a = Math.round(clamp01(q.targetBlockOutlineA) * 255.0F);
      QolConfig.TargetBlockOutlineColorMode mode = q.targetBlockOutlineColorMode == null
         ? QolConfig.TargetBlockOutlineColorMode.SOLID
         : q.targetBlockOutlineColorMode;
      int r;
      int g;
      int b;
      if (mode == QolConfig.TargetBlockOutlineColorMode.RAINBOW) {
         int period = 3500;
         float hue = (float)((double)(nowMs % period) / period);
         int rgb = Color.HSBtoRGB(hue, 0.85F, 1.0F);
         r = rgb >> 16 & 0xFF;
         g = rgb >> 8 & 0xFF;
         b = rgb & 0xFF;
      } else {
         r = clampColor(q.targetBlockOutlineR);
         g = clampColor(q.targetBlockOutlineG);
         b = clampColor(q.targetBlockOutlineB);
      }

      return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | b & 0xFF;
   }

   private static int clampColor(int v) {
      return Math.max(0, Math.min(255, v));
   }

   private static float clamp01(float v) {
      if (v < 0.0F) {
         return 0.0F;
      } else {
         return v > 1.0F ? 1.0F : v;
      }
   }
}
