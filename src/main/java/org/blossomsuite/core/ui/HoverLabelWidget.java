package org.blossomsuite.core.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

public class HoverLabelWidget extends ClickableWidget implements TooltipHolder {
   private Tooltip heldTooltip;

   public HoverLabelWidget(int x, int y, int width, int height, Text message, Tooltip tooltip) {
      super(x, y, width, height, message);
      this.active = false;
      this.visible = true;
      this.setTooltip(tooltip);
   }

   @Override
   public void setTooltip(Tooltip tooltip) {
      this.heldTooltip = tooltip;
      super.setTooltip(tooltip);
   }

   @Override
   public Tooltip heldTooltip() {
      return this.heldTooltip;
   }

   @Override
   protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
      TextRenderer tr = MinecraftClient.getInstance().textRenderer;
      int color = this.isHovered() ? Theme.ACCENT : Theme.TEXT;
      int textY = this.getY() + (this.getHeight() - 8) / 2;
      context.drawTextWithShadow(tr, this.getMessage(), this.getX(), textY, color);
   }

   @Override
   public void onClick(double mouseX, double mouseY) {
   }

   @Override
   protected void appendClickableNarrations(NarrationMessageBuilder builder) {
   }
}
