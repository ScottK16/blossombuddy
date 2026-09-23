package org.blossomsuite.core.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.blossomsuite.core.config.FeatureConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.mapart.MapArtState;
import org.blossomsuite.core.util.HudStyleUtil;

/** The picture of the currently open map art design ({@code /buddy mapart}), movable like any other HUD panel. */
public final class MapArtPreviewHud extends PanelHud {
   public static final MapArtPreviewHud INSTANCE = new MapArtPreviewHud();
   private static final int SIZE = 100;

   private MapArtPreviewHud() {
   }

   @Override
   public String id() {
      return "mapartpreview";
   }

   @Override
   protected FeatureConfig.Panel panel() {
      return FeatureConfig.INSTANCE.mapart.previewPanel;
   }

   @Override
   protected boolean shown() {
      return FeatureConfig.INSTANCE.mapart.showPreview;
   }

   @Override
   protected int backgroundColor(float opacity) {
      return HudStyleUtil.withAlpha(0xFFFFFF, opacity);
   }

   public void render(DrawContext ctx, MinecraftClient client) {
      if (!this.shown() || client.player == null || !SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
         return;
      }

      this.draw(ctx, client, SIZE, SIZE, true, c -> {
         Identifier thumb = MapArtState.INSTANCE.thumbnailId();
         int tw = MapArtState.INSTANCE.thumbnailWidth();
         int th = MapArtState.INSTANCE.thumbnailHeight();
         if (thumb != null && tw > 0 && th > 0) {
            c.drawTexture(RenderPipelines.GUI_TEXTURED, thumb, 2, 2, 0.0F, 0.0F, SIZE - 4, SIZE - 4, tw, th, tw, th);
         } else {
            String label = MapArtState.INSTANCE.project() == null ? "No map art loaded" : "(no preview)";
            c.drawCenteredTextWithShadow(client.textRenderer, Text.literal(label).formatted(Formatting.DARK_GRAY), SIZE / 2, SIZE / 2 - 4, -1);
         }
      });
   }
}
