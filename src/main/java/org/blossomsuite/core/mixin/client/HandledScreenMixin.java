package org.blossomsuite.core.mixin.client;

import org.blossomsuite.core.config.SuiteConfig;
import net.minecraft.client.input.MouseButtonEvent;
import org.blossomsuite.core.qol.inventorysort.InventorySorter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class HandledScreenMixin extends Screen {
   private static final int BUTTON_SIZE = 10;
   private static final int BUTTON_GAP = 2;
   private static final int SORT_BUTTON_X_OFFSET = -8;
   private static final int TRANSFER_BUTTON_X_OFFSET = -8;
   private static final int TRANSFER_BUTTON_Y_OFFSET = 3;
   private static final Component SORT_ICON = Component.literal("\u21c5");
   private static final Component DEPOSIT_ALL_ICON = Component.literal("\u21e7");
   private static final Component WITHDRAW_ALL_ICON = Component.literal("\u21e9");
   private static final Component DEPOSIT_MATCHING_ICON = Component.literal("\u21e5");
   private static final Component WITHDRAW_MATCHING_ICON = Component.literal("\u21e4");
   @Unique
   private HandledScreenMixin.IconButton suitecore$depositAllButton;
   @Unique
   private HandledScreenMixin.IconButton suitecore$depositMatchingButton;
   @Shadow
   protected int leftPos;
   @Shadow
   protected int topPos;
   @Shadow
   @Final
   protected int imageWidth;
   @Shadow
   protected int inventoryLabelY;
   @Shadow
   @Final
   protected AbstractContainerMenu menu;

   protected HandledScreenMixin(Component title) {
      super(title);
   }

   @Inject(method = "init", at = @At("TAIL"))
   private void suitecore$addContainerUtilityButtons(CallbackInfo ci) {
      Minecraft client = Minecraft.getInstance();
      if (InventorySorter.canSortOpenContainer(client)) {
         if (client.player != null && client.player.containerMenu == this.menu) {
            if (SuiteConfig.INSTANCE.QolConfig != null && SuiteConfig.INSTANCE.QolConfig.inventoryManagementShowContainerButtons) {
               int right = Math.min(this.leftPos + this.imageWidth - 10, this.width - 10 - 4);
               HandledScreenMixin.IconButton sort = new HandledScreenMixin.IconButton(
                  right + -8, Math.max(4, this.topPos + 6), SORT_ICON, () -> InventorySorter.sortOpenContainer(Minecraft.getInstance())
               );
               sort.setTooltip(Tooltip.create(Component.literal("Sorts the open container.")));
               this.addRenderableWidget(sort);
               int depositY = Math.max(4, this.topPos + this.inventoryLabelY - 6 + 3);
               int depositMatchingX = right + -8;
               int depositAllX = depositMatchingX - 2 - 10;
               this.suitecore$depositAllButton = new HandledScreenMixin.IconButton(depositAllX, depositY, DEPOSIT_ALL_ICON, () -> {
                  if (net.minecraft.client.Minecraft.getInstance().hasShiftDown()) {
                     InventorySorter.withdrawAllFromOpenContainer(Minecraft.getInstance());
                  } else {
                     InventorySorter.depositAllToOpenContainer(Minecraft.getInstance());
                  }
               });
               this.suitecore$depositAllButton
                  .setTooltip(
                     Tooltip.create(
                        Component.literal("Click: move all player inventory items to the container.\nShift-click: move all container items to your inventory.")
                     )
                  );
               this.addRenderableWidget(this.suitecore$depositAllButton);
               this.suitecore$depositMatchingButton = new HandledScreenMixin.IconButton(depositMatchingX, depositY, DEPOSIT_MATCHING_ICON, () -> {
                  if (net.minecraft.client.Minecraft.getInstance().hasShiftDown()) {
                     InventorySorter.withdrawMatchingFromOpenContainer(Minecraft.getInstance());
                  } else {
                     InventorySorter.depositMatchingToOpenContainer(Minecraft.getInstance());
                  }
               });
               this.suitecore$depositMatchingButton
                  .setTooltip(
                     Tooltip.create(
                        Component.literal(
                           "Click: move player items matching this container into it.\nShift-click: move container items matching your inventory to you."
                        )
                     )
                  );
               this.addRenderableWidget(this.suitecore$depositMatchingButton);
            }
         }
      }
   }

   @Inject(method = "extractRenderState", at = @At("HEAD"))
   private void suitecore$updateContainerUtilityButtonIcons(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      boolean withdraw = net.minecraft.client.Minecraft.getInstance().hasShiftDown();
      if (this.suitecore$depositAllButton != null) {
         this.suitecore$depositAllButton.setMessage(withdraw ? WITHDRAW_ALL_ICON : DEPOSIT_ALL_ICON);
      }

      if (this.suitecore$depositMatchingButton != null) {
         this.suitecore$depositMatchingButton.setMessage(withdraw ? WITHDRAW_MATCHING_ICON : DEPOSIT_MATCHING_ICON);
      }
   }

   @Unique
   private static final class IconButton extends AbstractWidget {
      private final Runnable onPress;

      private IconButton(int x, int y, Component icon, Runnable onPress) {
         super(x, y, 10, 10, icon);
         this.onPress = onPress;
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
         int fill = this.isHovered() ? -1426063361 : 1711276032;
         int border = this.isHovered() ? -1 : -1711276033;
         context.fill(x, y, x + 10, y + 10, fill);
         context.outline(x, y, 10, 10, border);
         Minecraft client = Minecraft.getInstance();
         if (client != null) {
            Font textRenderer = client.font;
            Component icon = this.getMessage();
            int textX = x + (10 - textRenderer.width(icon)) / 2;
            int textY = y + (10 - 9) / 2;
            context.text(textRenderer, icon, textX, textY, this.isHovered() ? -15658735 : -1);
         }
      }

      @Override
      protected void updateWidgetNarration(NarrationElementOutput builder) {
      }
   }
}
