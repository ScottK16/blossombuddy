package org.blossomsuite.core.mixin.client;

import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.qol.inventorysort.InventorySorter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin extends Screen {
   private static final int BUTTON_SIZE = 10;
   private static final int BUTTON_GAP = 2;
   private static final int SORT_BUTTON_X_OFFSET = -8;
   private static final int TRANSFER_BUTTON_X_OFFSET = -8;
   private static final int TRANSFER_BUTTON_Y_OFFSET = 3;
   private static final Text SORT_ICON = Text.literal("\u21c5");
   private static final Text DEPOSIT_ALL_ICON = Text.literal("\u21e7");
   private static final Text WITHDRAW_ALL_ICON = Text.literal("\u21e9");
   private static final Text DEPOSIT_MATCHING_ICON = Text.literal("\u21e5");
   private static final Text WITHDRAW_MATCHING_ICON = Text.literal("\u21e4");
   @Unique
   private HandledScreenMixin.IconButton suitecore$depositAllButton;
   @Unique
   private HandledScreenMixin.IconButton suitecore$depositMatchingButton;
   @Shadow
   protected int x;
   @Shadow
   protected int y;
   @Shadow
   protected int backgroundWidth;
   @Shadow
   protected int playerInventoryTitleY;
   @Shadow
   protected ScreenHandler handler;

   protected HandledScreenMixin(Text title) {
      super(title);
   }

   @Inject(method = "init", at = @At("TAIL"))
   private void suitecore$addContainerUtilityButtons(CallbackInfo ci) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (InventorySorter.canSortOpenContainer(client)) {
         if (client.player != null && client.player.currentScreenHandler == this.handler) {
            if (SuiteConfig.INSTANCE.QolConfig != null && SuiteConfig.INSTANCE.QolConfig.inventoryManagementShowContainerButtons) {
               int right = Math.min(this.x + this.backgroundWidth - 10, this.width - 10 - 4);
               HandledScreenMixin.IconButton sort = new HandledScreenMixin.IconButton(
                  right + -8, Math.max(4, this.y + 6), SORT_ICON, () -> InventorySorter.sortOpenContainer(MinecraftClient.getInstance())
               );
               sort.setTooltip(Tooltip.of(Text.literal("Sorts the open container.")));
               this.addDrawableChild(sort);
               int depositY = Math.max(4, this.y + this.playerInventoryTitleY - 6 + 3);
               int depositMatchingX = right + -8;
               int depositAllX = depositMatchingX - 2 - 10;
               this.suitecore$depositAllButton = new HandledScreenMixin.IconButton(depositAllX, depositY, DEPOSIT_ALL_ICON, () -> {
                  if (Screen.hasShiftDown()) {
                     InventorySorter.withdrawAllFromOpenContainer(MinecraftClient.getInstance());
                  } else {
                     InventorySorter.depositAllToOpenContainer(MinecraftClient.getInstance());
                  }
               });
               this.suitecore$depositAllButton
                  .setTooltip(
                     Tooltip.of(
                        Text.literal("Click: move all player inventory items to the container.\nShift-click: move all container items to your inventory.")
                     )
                  );
               this.addDrawableChild(this.suitecore$depositAllButton);
               this.suitecore$depositMatchingButton = new HandledScreenMixin.IconButton(depositMatchingX, depositY, DEPOSIT_MATCHING_ICON, () -> {
                  if (Screen.hasShiftDown()) {
                     InventorySorter.withdrawMatchingFromOpenContainer(MinecraftClient.getInstance());
                  } else {
                     InventorySorter.depositMatchingToOpenContainer(MinecraftClient.getInstance());
                  }
               });
               this.suitecore$depositMatchingButton
                  .setTooltip(
                     Tooltip.of(
                        Text.literal(
                           "Click: move player items matching this container into it.\nShift-click: move container items matching your inventory to you."
                        )
                     )
                  );
               this.addDrawableChild(this.suitecore$depositMatchingButton);
            }
         }
      }
   }

   @Inject(method = "render", at = @At("HEAD"))
   private void suitecore$updateContainerUtilityButtonIcons(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      boolean withdraw = Screen.hasShiftDown();
      if (this.suitecore$depositAllButton != null) {
         this.suitecore$depositAllButton.setMessage(withdraw ? WITHDRAW_ALL_ICON : DEPOSIT_ALL_ICON);
      }

      if (this.suitecore$depositMatchingButton != null) {
         this.suitecore$depositMatchingButton.setMessage(withdraw ? WITHDRAW_MATCHING_ICON : DEPOSIT_MATCHING_ICON);
      }
   }

   @Unique
   private static final class IconButton extends ClickableWidget {
      private final Runnable onPress;

      private IconButton(int x, int y, Text icon, Runnable onPress) {
         super(x, y, 10, 10, icon);
         this.onPress = onPress;
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
         int fill = this.isHovered() ? -1426063361 : 1711276032;
         int border = this.isHovered() ? -1 : -1711276033;
         context.fill(x, y, x + 10, y + 10, fill);
         context.drawBorder(x, y, 10, 10, border);
         MinecraftClient client = MinecraftClient.getInstance();
         if (client != null) {
            TextRenderer textRenderer = client.textRenderer;
            Text icon = this.getMessage();
            int textX = x + (10 - textRenderer.getWidth(icon)) / 2;
            int textY = y + (10 - 9) / 2;
            context.drawTextWithShadow(textRenderer, icon, textX, textY, this.isHovered() ? -15658735 : -1);
         }
      }

      @Override
      protected void appendClickableNarrations(NarrationMessageBuilder builder) {
      }
   }
}
