package org.blossomsuite.core.util;

public final class HudStyleUtil {
   private HudStyleUtil() {
   }

   public static int withAlpha(int rgb, float alpha01) {
      alpha01 = Math.max(0.0F, Math.min(1.0F, alpha01));
      int a = Math.round(alpha01 * 255.0F) & 0xFF;
      return a << 24 | rgb & 16777215;
   }

   public static int panelBg(float opacity) {
      return withAlpha(0, opacity);
   }

   public static int panelHeader(float opacity) {
      return withAlpha(0, Math.min(1.0F, opacity + 0.12F));
   }

   public static int panelDivider(float opacity) {
      return withAlpha(16777215, Math.min(1.0F, opacity * 0.6F));
   }

   public static int panelSection(float opacity) {
      return withAlpha(0, Math.min(1.0F, opacity + 0.06F));
   }
}
