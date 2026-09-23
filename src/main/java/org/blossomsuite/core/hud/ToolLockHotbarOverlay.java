package org.blossomsuite.core.hud;

import org.blossomsuite.core.config.QolConfig;
import org.blossomsuite.core.config.SuiteConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public final class ToolLockHotbarOverlay {
   private ToolLockHotbarOverlay() {
   }

   public static void render(DrawContext ctx, MinecraftClient client) {
      if (ctx != null && client != null) {
         if (client.player != null) {
            if (client.options == null || !client.options.hudHidden) {
               SuiteConfig cfg = SuiteConfig.INSTANCE;
               if (cfg != null && cfg.QolConfig != null) {
                  QolConfig q = cfg.QolConfig;
                  int locked = q.toolLockLockedSlotsMask;
                  int leftLocked = q.toolLockLeftClickLockedSlotsMask;
                  if ((locked | leftLocked) != 0) {
                     int sw = client.getWindow().getScaledWidth();
                     int sh = client.getWindow().getScaledHeight();
                     int hotbarLeft = sw / 2 - 91;
                     int hotbarTop = sh - 22;
                     int color = q.toolLockEnabled ? -8585317 : -4208683;
                     int outline = -872415232;

                     for (int slot = 0; slot < 9; slot++) {
                        boolean swap = (locked & 1 << slot) != 0;
                        boolean left = (leftLocked & 1 << slot) != 0;
                        if (swap || left) {
                           int x = hotbarLeft + slot * 20 + 14;
                           int y = hotbarTop + 4;
                           drawLock(ctx, x, y, color, outline);
                           if (left) {
                              ctx.fill(x - 12, y, x - 6, y + 7, outline);
                              ctx.fill(x - 11, y + 1, x - 7, y + 6, -39322);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void drawLock(DrawContext ctx, int x, int y, int color, int outline) {
      ctx.fill(x + 1, y + 0, x + 5, y + 1, outline);
      ctx.fill(x + 0, y + 1, x + 1, y + 3, outline);
      ctx.fill(x + 5, y + 1, x + 6, y + 3, outline);
      ctx.fill(x + 0, y + 3, x + 6, y + 7, outline);
      ctx.fill(x + 2, y + 1, x + 4, y + 3, color);
      ctx.fill(x + 1, y + 4, x + 5, y + 6, color);
      ctx.fill(x + 3, y + 5, x + 4, y + 6, -2013265920);
   }
}
