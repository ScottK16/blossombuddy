package org.blossomsuite.core.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

/** Drop-in for {@link SliderWidget} with the BlossomBuddy look: a pink filled track and a round-edged knob. */
public abstract class StyledSlider extends SliderWidget {
   private static final int KNOB_W = 8;

   public StyledSlider(int x, int y, int width, int height, Text message, double value) {
      super(x, y, width, height, message, value);
   }

   @Override
   public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
      boolean hot = this.isHovered() || this.isFocused();
      int x1 = this.getX();
      int y1 = this.getY();
      int x2 = x1 + this.getWidth();
      int y2 = y1 + this.getHeight();
      Theme.roundBox(context, x1, y1, x2, y2, 3, hot ? Theme.ACCENT_DIM : Theme.CONTROL_EDGE, Theme.CONTROL);

      int knobX = x1 + (int)((this.getWidth() - KNOB_W) * this.value);
      Theme.roundRect(context, x1 + 2, y1 + 2, Math.max(x1 + 2, knobX + KNOB_W / 2), y2 - 2, 2, Theme.ACCENT_FILL);
      Theme.roundRect(context, knobX, y1 + 1, knobX + KNOB_W, y2 - 1, 3, hot ? Theme.ACCENT : Theme.ACCENT_DIM);

      int textY = y1 + (this.getHeight() - 8) / 2;
      context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, this.getMessage(), x1 + this.getWidth() / 2, textY, Theme.TEXT);
   }
}
