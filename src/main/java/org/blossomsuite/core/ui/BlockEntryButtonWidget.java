package org.blossomsuite.core.ui;

import net.minecraft.world.level.block.Block;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
public final class BlockEntryButtonWidget extends AbstractWidget {
   private final Block block;
   private final Runnable onPress;
   private boolean highlighted = false;

   public BlockEntryButtonWidget(int x, int y, int width, int height, Block block, Component message, Runnable onPress) {
      super(x, y, width, height, message);
      this.block = block;
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
      if (this.block != null && this.block.asItem() != null) {
         context.item(org.blossomsuite.core.util.StackUtil.safeStack(this.block.asItem()), x + 3, y + 2);
      }

      Font tr = Minecraft.getInstance().font;
      int textY = y + (h - 9) / 2;
      context.text(tr, this.getMessage(), x + 24, textY, this.highlighted ? -1 : -2039584);
   }

   @Override
   protected void updateWidgetNarration(NarrationElementOutput builder) {
   }
}
