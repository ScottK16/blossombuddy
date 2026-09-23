package org.blossomsuite.core.hud;

import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.blossomsuite.core.config.FeatureConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.mapart.MapArtModels;
import org.blossomsuite.core.mapart.MapArtState;
import org.blossomsuite.core.util.HudStyleUtil;

/** The block list of the currently looked-up map art design ({@code /buddy mapart <code>}), movable like any other HUD panel. */
public final class MapArtListHud extends PanelHud {
   public static final MapArtListHud INSTANCE = new MapArtListHud();
   private static final int WIDTH = 170;
   private static final int ROW_H = 10;
   private static final int MAX_ROWS = 10;

   private MapArtListHud() {
   }

   @Override
   public String id() {
      return "mapartlist";
   }

   @Override
   protected FeatureConfig.Panel panel() {
      return FeatureConfig.INSTANCE.mapart.listPanel;
   }

   @Override
   protected boolean shown() {
      return FeatureConfig.INSTANCE.mapart.showList;
   }

   @Override
   protected int backgroundColor(float opacity) {
      return HudStyleUtil.withAlpha(0xFFFFFF, opacity);
   }

   public void render(DrawContext ctx, MinecraftClient client) {
      if (!this.shown() || client.player == null || !SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
         return;
      }

      TextRenderer tr = client.textRenderer;
      MapArtModels.Project project = MapArtState.INSTANCE.project();
      List<MapArtModels.Material> materials = project == null ? List.of() : project.materials;
      int shown = Math.min(materials.size(), MAX_ROWS);
      int extra = materials.size() - shown;
      int rowCount = Math.max(1, shown + (extra > 0 ? 1 : 0));
      int baseH = 6 + rowCount * ROW_H + 6;

      this.draw(ctx, client, WIDTH, baseH, true, c -> {
         if (materials.isEmpty()) {
            c.drawTextWithShadow(tr, Text.literal("No map art loaded").formatted(Formatting.DARK_GRAY), 6, 6, -1);
            return;
         }

         int y = 6;
         for (int i = 0; i < shown; i++) {
            MapArtModels.Material m = materials.get(i);
            c.drawTextWithShadow(tr, Text.literal(m.count + "x " + m.block), 6, y, -1);
            y += ROW_H;
         }

         if (extra > 0) {
            c.drawTextWithShadow(tr, Text.literal("+" + extra + " more").formatted(Formatting.DARK_GRAY), 6, y, -1);
         }
      });
   }
}
