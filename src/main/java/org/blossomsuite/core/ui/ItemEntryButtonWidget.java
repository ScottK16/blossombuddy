package org.blossomsuite.core.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
public final class ItemEntryButtonWidget extends AbstractWidget {
   private final Item item;
   private final Runnable onPress;
   private boolean highlighted = false;

   public ItemEntryButtonWidget(int x, int y, int width, int height, Item item, Component message, Runnable onPress) {
      super(x, y, width, height, message);
      this.item = item;
      this.onPress = onPress;
   }

   public void setHighlighted(boolean highlighted) {
      this.highlighted = highlighted;
   }

   @Override
   public void onClick(MouseButtonEvent inputEvent, boolean isDoubleClick) {
      double mouseX = inputEvent.x();
      double mouseY = inputEvent.y();
      if (this.onPress != null) {
         this.onPress.run();
      }
   }

   @Override
   protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      int x = this.getX();
      int y = this.getY();
      int w = this.getWidth();
      int h = this.getHeight();
      int fill = this.highlighted ? -14404056 : (this.isHovered() ? -14408668 : -15066598);
      int border = this.highlighted ? -10300817 : (this.isHovered() ? -7829368 : -11908534);
      context.fill(x, y, x + w, y + h, fill);
      context.outline(x, y, w, h, border);
      if (this.item != null) {
         context.item(org.blossomsuite.core.util.StackUtil.safeStack(this.item), x + 3, y + 2);
      }

      Font tr = Minecraft.getInstance().font;
      context.text(tr, this.getMessage(), x + 24, y + (h - 9) / 2, this.highlighted ? -1 : -2039584);
   }

   @Override
   protected void updateWidgetNarration(NarrationElementOutput builder) {
   }
}
