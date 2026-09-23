package org.blossomsuite.core.ui;

import org.blossomsuite.core.config.QolConfig;
import org.blossomsuite.core.util.CrosshairShapeRenderer;
import org.blossomsuite.core.util.CrosshairTintUtil;
import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.Selectable.SelectionType;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;

public final class CrosshairPreviewWidget implements Drawable, Element, Selectable {
   private final int x;
   private final int y;
   private final int w;
   private final int h;
   private final Supplier<QolConfig> cfg;

   public CrosshairPreviewWidget(int x, int y, int w, int h, Supplier<QolConfig> cfg) {
      this.x = x;
      this.y = y;
      this.w = w;
      this.h = h;
      this.cfg = cfg;
   }

   @Override
   public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
      ctx.fill(this.x, this.y, this.x + this.w, this.y + this.h, -15461356);
      ctx.drawBorder(this.x, this.y, this.w, this.h, -12961222);
      int pad = 6;
      int bgX1 = this.x + pad;
      int bgY1 = this.y + pad;
      int bgX2 = this.x + this.w - pad;
      int bgY2 = this.y + this.h - pad;
      int cell = 8;

      for (int yy = bgY1; yy < bgY2; yy += cell) {
         for (int xx = bgX1; xx < bgX2; xx += cell) {
            boolean dark = ((xx - bgX1) / cell + (yy - bgY1) / cell) % 2 == 0;
            int c = dark ? -14935012 : -14408668;
            ctx.fill(xx, yy, Math.min(xx + cell, bgX2), Math.min(yy + cell, bgY2), c);
         }
      }

      QolConfig q = this.cfg == null ? null : this.cfg.get();
      boolean tintEnabled = q != null && q.crosshairTintEnabled;
      int argb = tintEnabled ? CrosshairTintUtil.computeCrosshairArgb(q, System.currentTimeMillis()) : -1;
      int cx = this.x + this.w / 2;
      int cy = this.y + this.h / 2;
      if (q != null && q.crosshairShape != QolConfig.CrosshairShape.VANILLA) {
         CrosshairShapeRenderer.draw(ctx, cx, cy, q, argb);
      } else {
         QolConfig preview = q == null ? new QolConfig() : q;
         CrosshairShapeRenderer.draw(ctx, cx, cy, preview, argb);
      }

      TextRenderer tr = MinecraftClient.getInstance().textRenderer;
      Text status = tintEnabled ? Text.literal("Preview") : Text.literal("Preview (Tint OFF)");
      int textColor = tintEnabled ? -2236963 : -5592406;
      ctx.drawTextWithShadow(tr, status, this.x + 8, this.y + 6, textColor);
   }

   @Override
   public boolean isMouseOver(double mouseX, double mouseY) {
      return false;
   }

   @Override
   public void setFocused(boolean focused) {
   }

   @Override
   public boolean isFocused() {
      return false;
   }

   @Override
   public SelectionType getType() {
      return SelectionType.NONE;
   }

   @Override
   public void appendNarrations(NarrationMessageBuilder builder) {
   }
}
