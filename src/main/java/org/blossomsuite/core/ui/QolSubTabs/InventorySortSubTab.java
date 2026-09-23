package org.blossomsuite.core.ui.QolSubTabs;

import org.blossomsuite.core.ui.StyledButton;

import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.QolConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.qol.inventorysort.InventorySorter;
import org.blossomsuite.core.ui.HoverLabelWidget;
import org.blossomsuite.core.ui.SuiteSettingsScreen;
import org.blossomsuite.core.ui.SuiteSubTab;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public final class InventorySortSubTab implements SuiteSubTab {
   private static final int SLOT_STEP = 20;
   private static final int SLOT_SIZE = 18;

   @Override
   public String titleKey() {
      return "suitecore.tab.inventory_sort";
   }

   @Override
   public void build(SuiteSettingsScreen screen, int contentTopOffset) {
      QolConfig cfg = SuiteConfig.INSTANCE.QolConfig;
      int x = screen.contentX();
      int w = screen.contentW();
      int y = screen.bodyContentY() - screen.scrollOffset() + contentTopOffset;
      int rowH = 20;
      screen.addContentWidget(
         new HoverLabelWidget(
            x, y, 180, 12, Text.literal("Inventory Management"), Tooltip.of(Text.literal("Sorts inventory and manages container deposit controls."))
         )
      );
      y += 16;
      y = this.addToggleRow(screen, x, w, y, rowH, "Enabled", cfg.inventorySortEnabled, "When OFF, inventory management actions will not move items.", () -> {
         cfg.inventorySortEnabled = !cfg.inventorySortEnabled;
         SuiteConfig.INSTANCE.markDirty();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      });
      y = this.addToggleRow(
         screen,
         x,
         w,
         y,
         rowH,
         "Stack Matching Items",
         cfg.inventorySortStackMatching,
         "When ON, sorting first combines matching partial stacks in unlocked slots.",
         () -> {
            cfg.inventorySortStackMatching = !cfg.inventorySortStackMatching;
            SuiteConfig.INSTANCE.markDirty();
            ConfigIO.saveIfDirty();
            screen.rebuildPreserveScroll();
         }
      );
      y = this.addToggleRow(
         screen,
         x,
         w,
         y,
         rowH,
         "Container Buttons",
         cfg.inventoryManagementShowContainerButtons,
         "Shows Sort, Deposit All, and Deposit Matching buttons on supported storage containers.",
         () -> {
            cfg.inventoryManagementShowContainerButtons = !cfg.inventoryManagementShowContainerButtons;
            SuiteConfig.INSTANCE.markDirty();
            ConfigIO.saveIfDirty();
            screen.rebuildPreserveScroll();
         }
      );
      y = this.addToggleRow(
         screen,
         x,
         w,
         y,
         rowH,
         "Deposit Locked Slots",
         cfg.inventoryManagementDepositIgnoresLockedSlots,
         "When ON, Deposit All and Deposit Matching can move locked player slots. When OFF, deposits only move unlocked slots.",
         () -> {
            cfg.inventoryManagementDepositIgnoresLockedSlots = !cfg.inventoryManagementDepositIgnoresLockedSlots;
            SuiteConfig.INSTANCE.markDirty();
            ConfigIO.saveIfDirty();
            screen.rebuildPreserveScroll();
         }
      );
      screen.addContentWidget(
         new HoverLabelWidget(
            x,
            y + 2,
            220,
            12,
            Text.literal("Slot Locks"),
            Tooltip.of(Text.literal("Click a slot to lock or unlock it. Sorting skips locked slots. Deposits follow the Deposit Locked Slots setting."))
         )
      );
      y += 16;
      int gridX = x + Math.max(0, (w - 180) / 2);
      screen.addContentWidget(
         new HoverLabelWidget(gridX, y + 2, 120, 12, Text.literal("Inventory"), Tooltip.of(Text.literal("Main inventory slots. Red slots are locked.")))
      );
      y += 14;

      for (int row = 0; row < 3; row++) {
         for (int col = 0; col < 9; col++) {
            int invSlot = 9 + row * 9 + col;
            screen.addContentWidget(new InventorySortSubTab.InventoryLockSlotWidget(gridX + col * 20, y + row * 20, invSlot));
         }
      }

      y += 68;
      screen.addContentWidget(
         new HoverLabelWidget(gridX, y + 2, 120, 12, Text.literal("Hotbar"), Tooltip.of(Text.literal("Hotbar slots. Red slots are locked.")))
      );
      y += 14;

      for (int col = 0; col < 9; col++) {
         screen.addContentWidget(new InventorySortSubTab.InventoryLockSlotWidget(gridX + col * 20, y, col));
      }

      y += 32;
      y = this.addButtonRow(
         screen,
         x,
         w,
         y,
         rowH,
         "Sort Inventory",
         "Sort",
         "Sorts unlocked player inventory slots. Locked slots stay untouched.",
         () -> InventorySorter.sortPlayerInventory(MinecraftClient.getInstance())
      );
      this.addButtonRow(screen, x, w, y, rowH, "Clear Locks", "Clear", "Unlocks every inventory and hotbar slot used by Inventory Management.", () -> {
         cfg.clearInventorySortLocks();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      });
   }

   @Override
   public void removed() {
      ConfigIO.saveIfDirty();
   }

   @Override
   public int contentHeight(SuiteSettingsScreen screen, int contentTopOffset) {
      return contentTopOffset + 16 + 144 + 16 + 14 + 60 + 8 + 14 + 20 + 12 + 30;
   }

   private int addToggleRow(SuiteSettingsScreen screen, int x, int w, int y, int rowH, String label, boolean enabled, String tooltip, Runnable onPress) {
      screen.addContentWidget(new HoverLabelWidget(x, y + 6, 180, 12, Text.literal(label), Tooltip.of(Text.literal(tooltip))));
      ButtonWidget button = StyledButton.of(Text.literal(enabled ? "ON" : "OFF"), b -> onPress.run()).dimensions(x + w - 80, y, 80, rowH).build();
      button.setTooltip(Tooltip.of(Text.literal(tooltip)));
      screen.addContentWidget(button);
      return y + 24;
   }

   private int addButtonRow(SuiteSettingsScreen screen, int x, int w, int y, int rowH, String label, String buttonText, String tooltip, Runnable onPress) {
      screen.addContentWidget(new HoverLabelWidget(x, y + 6, 180, 12, Text.literal(label), Tooltip.of(Text.literal(tooltip))));
      ButtonWidget button = StyledButton.of(Text.literal(buttonText), b -> onPress.run()).dimensions(x + w - 100, y, 100, rowH).build();
      button.setTooltip(Tooltip.of(Text.literal(tooltip)));
      screen.addContentWidget(button);
      return y + 24;
   }

   private static final class InventoryLockSlotWidget extends ClickableWidget {
      private final int invSlot;

      private InventoryLockSlotWidget(int x, int y, int invSlot) {
         super(x, y, 18, 18, Text.literal("Slot " + (invSlot + 1)));
         this.invSlot = invSlot;
         this.setTooltip(Tooltip.of(Text.literal("Click to lock or unlock this slot.")));
      }

      @Override
      public void onClick(double mouseX, double mouseY) {
         SuiteConfig.INSTANCE.QolConfig.toggleInventorySortSlot(this.invSlot);
         ConfigIO.saveIfDirty();
      }

      @Override
      protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
         MinecraftClient client = MinecraftClient.getInstance();
         QolConfig cfg = SuiteConfig.INSTANCE.QolConfig;
         boolean locked = cfg.isInventorySortSlotLocked(this.invSlot);
         boolean hover = this.isHovered();
         int x = this.getX();
         int y = this.getY();
         int fill = locked ? -12967390 : (hover ? -14013910 : -15263977);
         int border = locked ? -39322 : (hover ? -5592406 : -11184811);
         context.fill(x, y, x + 18, y + 18, fill);
         context.drawBorder(x, y, 18, 18, border);
         if (client != null && client.player != null && this.invSlot >= 0 && this.invSlot < client.player.getInventory().size()) {
            ItemStack stack = client.player.getInventory().getStack(this.invSlot);
            if (stack != null && !stack.isEmpty()) {
               context.drawItem(stack, x + 1, y + 1);
               context.drawStackOverlay(client.textRenderer, stack, x + 1, y + 1);
            }
         }

         if (locked) {
            context.fill(x + 1, y + 1, x + 18 - 1, y + 18 - 1, 1728000048);
            Text lock = Text.literal("L");
            int textX = x + 9 - client.textRenderer.getWidth(lock) / 2;
            context.drawTextWithShadow(client.textRenderer, lock, textX, y + 5, -1);
         }
      }

      @Override
      protected void appendClickableNarrations(NarrationMessageBuilder builder) {
      }
   }
}
