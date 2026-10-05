package org.blossomsuite.core.hud;

import org.blossomsuite.core.config.SuiteConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
public final class QolIndicatorsHud {
   private QolIndicatorsHud() {
   }

   public static void render(GuiGraphicsExtractor ctx, Minecraft client) {
      if (client != null && client.player != null) {
         SuiteConfig cfg = SuiteConfig.INSTANCE;
         if (cfg == null || cfg.QolConfig == null) {
            ;
         }
      }
   }
}
