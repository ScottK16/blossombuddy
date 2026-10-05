package org.blossomsuite.core.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
public class TabButtonWidget extends AbstractWidget {
   private final Runnable onPress;
   private final boolean selectedSupplierDisabled;
   private boolean selected;

   public TabButtonWidget(int x, int y, int w, int h, Component message, boolean selected, Runnable onPress) {
      super(x, y, w, h, message);
      this.onPress = onPress;
      this.selected = selected;
      this.selectedSupplierDisabled = false;
   }

   public void setSelected(boolean selected) {
      this.selected = selected;
   }

   @Override
   public void onClick(MouseButtonEvent inputEvent, boolean isDoubleClick) {
      double mouseX = inputEvent.x();
      double mouseY = inputEvent.y();
      if (!this.selected) {
         this.onPress.run();
      }
   }

   @Override
   protected void updateWidgetNarration(NarrationElementOutput builder) {
   }

   @Override
   protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
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
      Font tr = Minecraft.getInstance().font;
      context.text(tr, this.getMessage(), x1 + 10, y1 + (this.getHeight() - 8) / 2, color);
   }
}
