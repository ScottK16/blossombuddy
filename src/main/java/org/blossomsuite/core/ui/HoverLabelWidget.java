package org.blossomsuite.core.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
public class HoverLabelWidget extends AbstractWidget implements TooltipHolder {
   private Tooltip heldTooltip;

   public HoverLabelWidget(int x, int y, int width, int height, Component message, Tooltip tooltip) {
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
   protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      Font tr = Minecraft.getInstance().font;
      int color = this.isHovered() ? Theme.ACCENT : Theme.TEXT;
      int textY = this.getY() + (this.getHeight() - 8) / 2;
      context.text(tr, this.getMessage(), this.getX(), textY, color);
   }

   @Override
   public void onClick(MouseButtonEvent inputEvent, boolean isDoubleClick) {
      double mouseX = inputEvent.x();
      double mouseY = inputEvent.y();
   }

   @Override
   protected void updateWidgetNarration(NarrationElementOutput builder) {
   }
}
