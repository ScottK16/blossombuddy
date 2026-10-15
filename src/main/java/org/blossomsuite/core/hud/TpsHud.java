package org.blossomsuite.core.hud;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.blossomsuite.core.config.FeatureConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.tps.TpsTracker;

/** The server's estimated ticks per second, in a small movable panel. */
public final class TpsHud extends PanelHud {
   public static final TpsHud INSTANCE = new TpsHud();
   private static final int WIDTH = 64;
   private static final int HEIGHT = 18;

   private TpsHud() {
   }

   @Override
   public String id() {
      return "tps";
   }

   @Override
   protected FeatureConfig.Panel panel() {
      return FeatureConfig.INSTANCE.tps.panel;
   }

   @Override
   protected boolean shown() {
      return FeatureConfig.INSTANCE.tps.show;
   }

   @Override
   protected int[] defaultTopLeft(int screenW, int screenH, int w, int h) {
      return new int[]{screenW - w - 6, 6};
   }

   public static ChatFormatting colorFor(double tps) {
      return switch (TpsTracker.grade(tps)) {
         case 0 -> ChatFormatting.GREEN;
         case 1 -> ChatFormatting.YELLOW;
         case 2 -> ChatFormatting.RED;
         default -> ChatFormatting.GRAY;
      };
   }

   public void render(GuiGraphicsExtractor ctx, Minecraft client) {
      if (!this.shown() || client.player == null || !SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
         return;
      }

      double tps = TpsTracker.INSTANCE.tps(System.currentTimeMillis());
      Font tr = client.font;
      Component text = Component.literal("TPS ").withStyle(ChatFormatting.GRAY)
         .append(Component.literal(TpsTracker.format(tps)).withStyle(colorFor(tps), ChatFormatting.BOLD));
      this.draw(ctx, client, WIDTH, HEIGHT, true, c -> c.text(tr, text, 6, 5, -1));
   }
}
