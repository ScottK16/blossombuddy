package org.blossomsuite.core.util;

import org.blossomsuite.core.config.QolConfig;

public final class CrosshairTintUtil {
   private CrosshairTintUtil() {
   }

   public static int computeCrosshairArgb(QolConfig q, long nowMs) {
      if (q == null) {
         return -1;
      }

      int a = Math.round(clamp01(q.crosshairA) * 255.0F);
      int r;
      int g;
      int b;
      if (q.crosshairRainbow) {
         int period = q.crosshairRainbowPeriodMs <= 0 ? 2000 : q.crosshairRainbowPeriodMs;
         float hue = (float)((double)(nowMs % period) / period);
         float[] rgb = hsvToRgb(hue, 1.0F, 1.0F);
         r = Math.round(clamp01(rgb[0]) * 255.0F);
         g = Math.round(clamp01(rgb[1]) * 255.0F);
         b = Math.round(clamp01(rgb[2]) * 255.0F);
      } else {
         r = Math.max(0, Math.min(255, q.crosshairR));
         g = Math.max(0, Math.min(255, q.crosshairG));
         b = Math.max(0, Math.min(255, q.crosshairB));
      }

      return a << 24 | r << 16 | g << 8 | b;
   }

   private static float clamp01(float v) {
      if (v < 0.0F) {
         return 0.0F;
      } else {
         return v > 1.0F ? 1.0F : v;
      }
   }

   private static float[] hsvToRgb(float h, float s, float v) {
      h -= (float)Math.floor(h);
      s = clamp01(s);
      v = clamp01(v);
      float c = v * s;
      float hp = h * 6.0F;
      float x = c * (1.0F - Math.abs(hp % 2.0F - 1.0F));
      float r1;
      float g1;
      float b1;
      if (hp < 1.0F) {
         r1 = c;
         g1 = x;
         b1 = 0.0F;
      } else if (hp < 2.0F) {
         r1 = x;
         g1 = c;
         b1 = 0.0F;
      } else if (hp < 3.0F) {
         r1 = 0.0F;
         g1 = c;
         b1 = x;
      } else if (hp < 4.0F) {
         r1 = 0.0F;
         g1 = x;
         b1 = c;
      } else if (hp < 5.0F) {
         r1 = x;
         g1 = 0.0F;
         b1 = c;
      } else {
         r1 = c;
         g1 = 0.0F;
         b1 = x;
      }

      float m = v - c;
      return new float[]{r1 + m, g1 + m, b1 + m};
   }
}
