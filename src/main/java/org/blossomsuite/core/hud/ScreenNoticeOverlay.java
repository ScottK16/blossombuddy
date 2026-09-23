package org.blossomsuite.core.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

public final class ScreenNoticeOverlay {
   private static final long DEFAULT_DISPLAY_MS = 1800L;
   private static final int DEFAULT_RGB = 16733525;
   private static String message = "";
   private static long shownAtMs = 0L;
   private static long displayMs = 1800L;
   private static int rgb = 16733525;
   private static ItemStack iconStack = ItemStack.EMPTY;

   private ScreenNoticeOverlay() {
   }

   public static void show(String text) {
      show(text, 16733525, 1800L);
   }

   public static void show(String text, int rgbColor, long durationMs) {
      show(text, ItemStack.EMPTY, rgbColor, durationMs);
   }

   public static void show(String text, ItemStack icon, int rgbColor, long durationMs) {
      message = text == null ? "" : text.trim();
      iconStack = icon != null && !icon.isEmpty() ? icon.copy() : ItemStack.EMPTY;
      rgb = rgbColor & 16777215;
      displayMs = Math.max(1L, durationMs);
      shownAtMs = message.isBlank() ? 0L : System.currentTimeMillis();
   }

   public static void render(DrawContext ctx, MinecraftClient client) {
      if (ctx != null && client != null && client.player != null) {
         if (client.options == null || !client.options.hudHidden) {
            if (!message.isBlank() && shownAtMs > 0L) {
               long age = System.currentTimeMillis() - shownAtMs;
               if (age >= displayMs) {
                  clear();
               } else {
                  long fadeMs = Math.min(600L, displayMs);
                  long solidMs = Math.max(0L, displayMs - fadeMs);
                  float fade = age <= solidMs ? 1.0F : Math.max(0.0F, 1.0F - (float)(age - solidMs) / (float)fadeMs);
                  int alpha = Math.max(0, Math.min(255, Math.round(255.0F * fade)));
                  if (alpha > 0) {
                     int screenW = client.getWindow().getScaledWidth();
                     int screenH = client.getWindow().getScaledHeight();
                     int textW = client.textRenderer.getWidth(message);
                     boolean hasIcon = iconStack != null && !iconStack.isEmpty();
                     int iconW = hasIcon ? 20 : 0;
                     int totalW = iconW + textW;
                     int x = (screenW - totalW) / 2;
                     int y = Math.max(42, screenH / 2 - 72);
                     int color = alpha << 24 | rgb;
                     if (hasIcon) {
                        ctx.drawItem(iconStack, x, y - 4);
                     }

                     ctx.drawTextWithShadow(client.textRenderer, message, x + iconW, y, color);
                  }
               }
            }
         }
      }
   }

   private static void clear() {
      message = "";
      shownAtMs = 0L;
      displayMs = 1800L;
      rgb = 16733525;
      iconStack = ItemStack.EMPTY;
   }
}
