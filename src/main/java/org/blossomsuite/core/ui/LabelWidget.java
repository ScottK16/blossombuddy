package org.blossomsuite.core.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

public class LabelWidget extends ClickableWidget {
   private final int color;

   public LabelWidget(int x, int y, int width, int height, Text message) {
      super(x, y, width, height, message);
      this.active = false;
      this.visible = true;
      this.color = Theme.TEXT;
   }

   public LabelWidget(int x, int y, int width, int height, Text message, int color) {
      super(x, y, width, height, message);
      this.active = false;
      this.visible = true;
      this.color = color;
   }

   @Override
   protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
      TextRenderer tr = MinecraftClient.getInstance().textRenderer;
      int textY = this.getY() + (this.getHeight() - 8) / 2;
      context.drawTextWithShadow(tr, this.getMessage(), this.getX(), textY, this.color);
   }

   @Override
   public void onClick(double mouseX, double mouseY) {
   }

   @Override
   protected void appendClickableNarrations(NarrationMessageBuilder builder) {
   }
}
