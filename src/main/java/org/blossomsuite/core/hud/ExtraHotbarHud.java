package org.blossomsuite.core.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.blossomsuite.core.config.FeatureConfig;
import org.blossomsuite.core.util.HudStyleUtil;
import org.blossomsuite.core.config.SuiteConfig;

/**
 * Shows the inventory rows above the hotbar so a second (and third) hotbar is always in view. Row 1 is the
 * inventory row directly above the hotbar (inventory slots 27-35), row 2 the one above that (18-26).
 */
public final class ExtraHotbarHud extends PanelHud {
   public static final ExtraHotbarHud INSTANCE = new ExtraHotbarHud();
   private static final int SLOT = 20;
   private static final int ROW_H = 22;
   private static final int W = 9 * SLOT + 2;

   private ExtraHotbarHud() {
   }

   @Override
   public String id() {
      return "extrahotbar";
   }

   @Override
   protected FeatureConfig.Panel panel() {
      return FeatureConfig.INSTANCE.hotbar.panel;
   }

   @Override
   protected boolean shown() {
      return FeatureConfig.INSTANCE.hotbar.show;
   }

   @Override
   protected int[] defaultTopLeft(int screenW, int screenH, int w, int h) {
      // above the armour / health / food bars, centred on the hotbar
      return new int[]{(screenW - w) / 2, screenH - 62 - h};
   }

   /** Inventory index of a column in an extra row: row 1 -> 27.., row 2 -> 18.. */
   public static int inventoryIndex(int row, int column) {
      return 36 - 9 * row + column;
   }

   public void render(DrawContext ctx, MinecraftClient client) {
      FeatureConfig.Hotbar cfg = FeatureConfig.INSTANCE.hotbar;
      if (!cfg.show || client.player == null || !SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
         return;
      }

      PlayerInventory inv = client.player.getInventory();
      int rows = Math.max(1, Math.min(2, cfg.extraRows));
      int baseH = rows * ROW_H;
      this.draw(ctx, client, W, baseH, false, c -> {
         float opacity = cfg.panel.opacity;
         for (int row = 1; row <= rows; row++) {
            // row 1 sits nearest the real hotbar, so it is drawn at the bottom
            int top = (rows - row) * ROW_H;
            c.fill(0, top, W, top + ROW_H, HudStyleUtil.panelBg(Math.max(0.35F, opacity)));
            c.fill(0, top, W, top + 1, HudStyleUtil.panelDivider(opacity));
            for (int col = 0; col < 9; col++) {
               ItemStack stack = inv.getStack(inventoryIndex(row, col));
               int x = 1 + col * SLOT + 2;
               int y = top + 3;
               if (!stack.isEmpty()) {
                  c.drawItem(stack, x, y);
                  c.drawStackOverlay(client.textRenderer, stack, x, y);
               }
            }
         }
      });
   }
}
