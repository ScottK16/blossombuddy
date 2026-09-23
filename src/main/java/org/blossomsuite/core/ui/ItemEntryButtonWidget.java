package org.blossomsuite.core.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public final class ItemEntryButtonWidget extends ClickableWidget {
   private final Item item;
   private final Runnable onPress;
   private boolean highlighted = false;

   public ItemEntryButtonWidget(int x, int y, int width, int height, Item item, Text message, Runnable onPress) {
      super(x, y, width, height, message);
      this.item = item;
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
      if (this.item != null) {
         context.drawItem(new ItemStack(this.item), x + 3, y + 2);
      }

      TextRenderer tr = MinecraftClient.getInstance().textRenderer;
      context.drawTextWithShadow(tr, this.getMessage(), x + 24, y + (h - 9) / 2, this.highlighted ? -1 : -2039584);
   }

   @Override
   protected void appendClickableNarrations(NarrationMessageBuilder builder) {
   }
}
