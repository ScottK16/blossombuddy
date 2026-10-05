package org.blossomsuite.core.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
public class LabelWidget extends AbstractWidget {
   private final int color;

   public LabelWidget(int x, int y, int width, int height, Component message) {
      super(x, y, width, height, message);
      this.active = false;
      this.visible = true;
      this.color = Theme.TEXT;
   }

   public LabelWidget(int x, int y, int width, int height, Component message, int color) {
      super(x, y, width, height, message);
      this.active = false;
      this.visible = true;
      this.color = color;
   }

   @Override
   protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      Font tr = Minecraft.getInstance().font;
      int textY = this.getY() + (this.getHeight() - 8) / 2;
      context.text(tr, this.getMessage(), this.getX(), textY, this.color);
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
