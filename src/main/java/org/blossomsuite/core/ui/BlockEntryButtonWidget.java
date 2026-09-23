package org.blossomsuite.core.ui;

import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public final class BlockEntryButtonWidget extends ClickableWidget {
   private final Block block;
   private final Runnable onPress;
   private boolean highlighted = false;

   public BlockEntryButtonWidget(int x, int y, int width, int height, Block block, Text message, Runnable onPress) {
      super(x, y, width, height, message);
      this.block = block;
      this.onPress = onPress;
   }

   public void setHighlighted(boolean highlighted) {
      this.highlighted = highlighted;
   }

   @Override
   public void onClick(double mouseX, double mouseY) {
      if (this.onPress != null) {
         this.onPress.run();
      }
   }

   @Override
   protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
      int x = this.getX();
      int y = this.getY();
      int w = this.getWidth();
      int h = this.getHeight();
      int fill = this.highlighted ? -14404056 : (this.isHovered() ? -14408668 : -15066598);
      int border = this.highlighted ? -10300817 : (this.isHovered() ? -7829368 : -11908534);
      context.fill(x, y, x + w, y + h, fill);
      context.drawBorder(x, y, w, h, border);
      if (this.block != null && this.block.asItem() != null) {
         context.drawItem(new ItemStack(this.block.asItem()), x + 3, y + 2);
      }

      TextRenderer tr = MinecraftClient.getInstance().textRenderer;
      int textY = y + (h - 9) / 2;
      context.drawTextWithShadow(tr, this.getMessage(), x + 24, textY, this.highlighted ? -1 : -2039584);
   }

   @Override
   protected void appendClickableNarrations(NarrationMessageBuilder builder) {
   }
}
