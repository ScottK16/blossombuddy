package org.blossomsuite.core.hud;

import org.blossomsuite.core.config.SuiteConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public final class QolIndicatorsHud {
   private QolIndicatorsHud() {
   }

   public static void render(DrawContext ctx, MinecraftClient client) {
      if (client != null && client.player != null) {
         SuiteConfig cfg = SuiteConfig.INSTANCE;
         if (cfg == null || cfg.QolConfig == null) {
            ;
         }
      }
   }
}
