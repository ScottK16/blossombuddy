package org.blossomsuite.core.mixin.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.entity.boss.BossBar;
import org.blossomsuite.core.jobs.overflow.OverflowTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the cosmetic "levels" bar for a maxed-out Jobs boss bar in place of the vanilla one.
 * Based on Jobs Overflow XP (MIT, Mills).
 */
@Mixin(BossBarHud.class)
public abstract class BossBarHudMixin {
   private static final int BAR_WIDTH = 182;
   private static final int BAR_HEIGHT = 5;

   @Inject(method = "renderBossBar(Lnet/minecraft/client/gui/DrawContext;IILnet/minecraft/entity/boss/BossBar;)V", at = @At("HEAD"), cancellable = true)
   private void suitecore$levelsBar(DrawContext context, int x, int y, BossBar bossBar, CallbackInfo ci) {
      OverflowTracker.LevelsBar bar = OverflowTracker.INSTANCE.levelsBarFor(bossBar.getUuid());
      if (bar == null) {
         return;
      }

      int top = y + 1;
      int filled = (int)(BAR_WIDTH * bar.progress());
      context.fill(x - 2, top - 2, x + BAR_WIDTH + 2, top + BAR_HEIGHT + 2, 0xFF1E1E1E);
      context.fill(x - 1, top - 1, x + BAR_WIDTH + 1, top + BAR_HEIGHT + 1, 0xFF3C3C3C);
      context.fill(x, top, x + BAR_WIDTH, top + BAR_HEIGHT, 0xFF2A2A2A);
      for (int i = 0; i < filled; i++) {
         float t = (float)i / (BAR_WIDTH - 1);
         context.fill(x + i, top, x + i + 1, top + BAR_HEIGHT, suitecore$gradient(t, bar.baseColor(), bar.lightColor()));
      }

      ci.cancel();
   }

   private static int suitecore$gradient(float t, int base, int light) {
      float local = t < 0.5F ? t / 0.5F : (t - 0.5F) / 0.5F;
      int from = t < 0.5F ? base : light;
      int to = t < 0.5F ? light : base;
      int r = suitecore$lerp(from >> 16 & 0xFF, to >> 16 & 0xFF, local);
      int g = suitecore$lerp(from >> 8 & 0xFF, to >> 8 & 0xFF, local);
      int b = suitecore$lerp(from & 0xFF, to & 0xFF, local);
      return 0xFF000000 | r << 16 | g << 8 | b;
   }

   private static int suitecore$lerp(int a, int b, float t) {
      return (int)(a + (b - a) * t);
   }
}
