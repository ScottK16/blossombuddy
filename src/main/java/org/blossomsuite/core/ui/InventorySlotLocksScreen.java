package org.blossomsuite.core.ui;

import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.QolConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.qol.inventorysort.InventorySorter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public final class InventorySlotLocksScreen extends Screen {
   private final Screen parent;
   private int gridX;
   private int gridY;

   public InventorySlotLocksScreen(Screen parent) {
      super(Text.literal("Inventory Slot Locks"));
      this.parent = parent;
   }

   @Override
   protected void init() {
      this.gridX = (this.width - 180) / 2;
      this.gridY = Math.max(54, (this.height - 132) / 2);
      int buttonY = this.gridY + 104;
      int buttonW = 88;
      int gap = 6;
      int totalW = buttonW * 3 + gap * 2;
      int startX = (this.width - totalW) / 2;
      this.addDrawableChild(
         StyledButton.of(Text.literal("Sort"), b -> InventorySorter.sortPlayerInventory(this.client)).dimensions(startX, buttonY, buttonW, 20).build()
      );
      this.addDrawableChild(StyledButton.of(Text.literal("Clear Locks"), b -> {
         SuiteConfig.INSTANCE.QolConfig.clearInventorySortLocks();
         ConfigIO.saveIfDirty();
      }).dimensions(startX + buttonW + gap, buttonY, buttonW, 20).build());
      this.addDrawableChild(StyledButton.of(Text.literal("Done"), b -> {
         ConfigIO.saveIfDirty();
         if (this.client != null) {
            this.client.setScreen(this.parent);
         }
      }).dimensions(startX + (buttonW + gap) * 2, buttonY, buttonW, 20).build());
   }

   @Override
   public void close() {
      ConfigIO.saveIfDirty();
      if (this.client != null) {
         this.client.setScreen(this.parent);
      }
   }

   @Override
   public boolean shouldPause() {
      return false;
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (button == 0) {
         int slot = this.slotAt(mouseX, mouseY);
         if (slot >= 0) {
            SuiteConfig.INSTANCE.QolConfig.toggleInventorySortSlot(slot);
            ConfigIO.saveIfDirty();
            return true;
         }
      }

      return super.mouseClicked(mouseX, mouseY, button);
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      context.fill(0, 0, this.width, this.height, -301463544);
      this.drawCenteredText(context, this.title, this.width / 2, 22, -1);
      this.drawCenteredText(context, Text.literal("Click slots to lock or unlock them. Sorting skips locked slots."), this.width / 2, 36, -4671304);
      this.drawInventoryGrid(context, mouseX, mouseY);
      super.render(context, mouseX, mouseY, delta);
   }

   private void drawInventoryGrid(DrawContext context, int mouseX, int mouseY) {
      MinecraftClient client = MinecraftClient.getInstance();
      QolConfig cfg = SuiteConfig.INSTANCE.QolConfig;

      for (int row = 0; row < 3; row++) {
         for (int col = 0; col < 9; col++) {
            int invSlot = 9 + row * 9 + col;
            this.drawSlot(context, client, cfg, invSlot, this.gridX + col * 20, this.gridY + row * 20, mouseX, mouseY);
         }
      }

      int hotbarY = this.gridY + 68;

      for (int col = 0; col < 9; col++) {
         this.drawSlot(context, client, cfg, col, this.gridX + col * 20, hotbarY, mouseX, mouseY);
      }

      context.drawTextWithShadow(this.textRenderer, Text.literal("Inventory"), this.gridX, this.gridY - 12, -2039584);
      context.drawTextWithShadow(this.textRenderer, Text.literal("Hotbar"), this.gridX, hotbarY - 12, -2039584);
   }

   private void drawSlot(DrawContext context, MinecraftClient client, QolConfig cfg, int invSlot, int x, int y, int mouseX, int mouseY) {
      boolean locked = cfg.isInventorySortSlotLocked(invSlot);
      boolean hover = mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18;
      int fill = locked ? -12967390 : (hover ? -14013910 : -15263977);
      int border = locked ? -39322 : (hover ? -5592406 : -11184811);
      context.fill(x, y, x + 18, y + 18, fill);
      context.drawBorder(x, y, 18, 18, border);
      if (client != null && client.player != null && invSlot >= 0 && invSlot < client.player.getInventory().size()) {
         ItemStack stack = client.player.getInventory().getStack(invSlot);
         if (stack != null && !stack.isEmpty()) {
            context.drawItem(stack, x + 1, y + 1);
            context.drawStackOverlay(this.textRenderer, stack, x + 1, y + 1);
         }
      }

      if (locked) {
         context.fill(x + 1, y + 1, x + 17, y + 17, 1728000048);
         this.drawCenteredText(context, Text.literal("L"), x + 9, y + 5, -1);
      }
   }

   private void drawCenteredText(DrawContext context, Text text, int centerX, int y, int color) {
      context.drawTextWithShadow(this.textRenderer, text, centerX - this.textRenderer.getWidth(text) / 2, y, color);
   }

   private int slotAt(double mouseX, double mouseY) {
      int main = this.slotAtGrid(mouseX, mouseY, this.gridY, 9);
      return main >= 0 ? main : this.slotAtGrid(mouseX, mouseY, this.gridY + 68, 0);
   }

   private int slotAtGrid(double mouseX, double mouseY, int y, int startSlot) {
      if (mouseX < this.gridX || mouseX >= this.gridX + 180) {
         return -1;
      } else if (!(mouseY < y) && !(mouseY >= y + (startSlot == 0 ? 20 : 60))) {
         int col = (int)((mouseX - this.gridX) / 20.0);
         int row = (int)((mouseY - y) / 20.0);
         int local = row * 9 + col;
         int max = startSlot == 0 ? 9 : 27;
         return local >= 0 && local < max ? startSlot + local : -1;
      } else {
         return -1;
      }
   }
}
