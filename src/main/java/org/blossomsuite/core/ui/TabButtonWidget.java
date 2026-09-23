package org.blossomsuite.core.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

public class TabButtonWidget extends ClickableWidget {
   private final Runnable onPress;
   private final boolean selectedSupplierDisabled;
   private boolean selected;

   public TabButtonWidget(int x, int y, int w, int h, Text message, boolean selected, Runnable onPress) {
      super(x, y, w, h, message);
      this.onPress = onPress;
      this.selected = selected;
      this.selectedSupplierDisabled = false;
   }

   public void setSelected(boolean selected) {
      this.selected = selected;
   }

   @Override
   public void onClick(double mouseX, double mouseY) {
      if (!this.selected) {
         this.onPress.run();
      }
   }

   @Override
   protected void appendClickableNarrations(NarrationMessageBuilder builder) {
   }

   @Override
   protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
      boolean hover = this.isHovered();
      int x1 = this.getX();
      int y1 = this.getY();
      int x2 = x1 + this.getWidth();
      int y2 = y1 + this.getHeight();
      if (this.selected) {
         Theme.roundRect(context, x1, y1, x2, y2, 3, Theme.ACCENT_FILL);
         Theme.roundRect(context, x1, y1 + 3, x1 + 3, y2 - 3, 1, Theme.ACCENT);
      } else if (hover) {
         Theme.roundRect(context, x1, y1, x2, y2, 3, Theme.CONTROL_HOVER);
      }

      int color = this.selected ? Theme.ACCENT : (hover ? Theme.TEXT : Theme.TEXT_DIM);
      TextRenderer tr = MinecraftClient.getInstance().textRenderer;
      context.drawTextWithShadow(tr, this.getMessage(), x1 + 10, y1 + (this.getHeight() - 8) / 2, color);
   }
}
