package org.blossomsuite.core.ui.QolSubTabs;

import org.blossomsuite.core.ui.StyledButton;

import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.QolConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.ui.HoverLabelWidget;
import org.blossomsuite.core.ui.SuiteSettingsScreen;
import org.blossomsuite.core.ui.SuiteSubTab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
public final class ToolLockSubTab implements SuiteSubTab {
   private int hotbarX = -1;
   private int hotbarY = -1;

   @Override
   public String titleKey() {
      return "suitecore.tab.toollock";
   }

   @Override
   public void build(SuiteSettingsScreen screen, int contentTopOffset) {
      SuiteConfig cfg = SuiteConfig.INSTANCE;
      if (cfg != null && cfg.QolConfig != null) {
         int x = screen.contentX();
         int w = screen.contentW();
         int y = screen.bodyContentY() - screen.scrollOffset() + contentTopOffset;
         int rowH = 20;
         int gapY = 28;
         int gapX = 6;
         screen.addContentWidget(
            new HoverLabelWidget(
               x,
               y + 6,
               220,
               12,
               Component.literal("Tool Lock"),
               Tooltip.create(Component.literal("Blocks sneak + right-click tool mode swaps (ex: Silk Touch/Fortune) for locked hotbar slots."))
            )
         );
         Button enabledBtn = StyledButton.of(Component.literal(cfg.QolConfig.toolLockEnabled ? "ON" : "OFF"), b -> {
            cfg.QolConfig.toggleToolLockEnabled();
            cfg.markDirty();
            ConfigIO.saveIfDirty();
            screen.rebuildPreserveScroll();
         }).dimensions(x + w - 80, y, 80, 20).build();
         enabledBtn.setTooltip(Tooltip.create(Component.literal("Enable/disable Tool Lock.")));
         screen.addContentWidget(enabledBtn);
         y += 28;
         QolConfig q = cfg.QolConfig;
         q.ensureToolLockProfiles();
         screen.addContentWidget(
            new HoverLabelWidget(
               x, y + 6, 220, 12, Component.literal("Profile"), Tooltip.create(Component.literal("Tool Lock profiles let you switch between saved hotbar lock setups."))
            )
         );
         int deleteW = 70;
         int addW = 70;
         int profileW = w - deleteW - addW - 12;
         Button profileBtn = StyledButton.of(Component.literal(currentProfileTitle(q)), b -> {
            if (!q.toolLockProfiles.isEmpty()) {
               q.syncActiveToolLockProfile();
               int next = q.toolLockActiveProfile + 1;
               if (next >= q.toolLockProfiles.size()) {
                  next = 0;
               }

               q.applyToolLockProfile(next);
               ConfigIO.saveIfDirty();
               screen.rebuildPreserveScroll();
            }
         }).dimensions(x, y + 20, profileW, 20).build();
         profileBtn.setTooltip(Tooltip.create(Component.literal("Cycles through Tool Lock profiles.")));
         screen.addContentWidget(profileBtn);
         Button addProfileBtn = StyledButton.of(Component.literal("+ Add"), b -> {
            q.addToolLockProfile(nextProfileName(q));
            ConfigIO.saveIfDirty();
            screen.rebuildPreserveScroll();
         }).dimensions(x + profileW + 6, y + 20, addW, 20).build();
         addProfileBtn.setTooltip(Tooltip.create(Component.literal("Adds a new empty Tool Lock profile.")));
         screen.addContentWidget(addProfileBtn);
         Button deleteProfileBtn = StyledButton.of(Component.literal("Delete"), b -> {
            q.deleteActiveToolLockProfile();
            ConfigIO.saveIfDirty();
            screen.rebuildPreserveScroll();
         }).dimensions(x + profileW + 6 + addW + 6, y + 20, deleteW, 20).build();
         deleteProfileBtn.active = q.toolLockProfiles.size() > 1;
         deleteProfileBtn.setTooltip(Tooltip.create(Component.literal("Deletes the current Tool Lock profile.")));
         screen.addContentWidget(deleteProfileBtn);
         EditBox profileNameField = new EditBox(screen.getFont(), x, y + 46, w, 20, Component.empty());
         profileNameField.setMaxLength(32);
         profileNameField.setValue(currentProfileTitle(q));
         profileNameField.setTooltip(Tooltip.create(Component.literal("Rename the current Tool Lock profile.")));
         profileNameField.setResponder(newText -> q.renameActiveToolLockProfile(newText));
         screen.addContentWidget(profileNameField);
         y += 76;
         screen.addContentWidget(
            new HoverLabelWidget(
               x,
               y + 6,
               Math.min(w, 340),
               12,
               Component.literal("Block On Interact Blocks"),
               Tooltip.create(
                  Component.literal(
                     "When OFF, Tool Lock will not block sneak-right-clicking on containers/interactive blocks.\nWhen ON, Tool Lock blocks on blocks too (more aggressive)."
                  )
               )
            )
         );
         Button blockOnBlockBtn = StyledButton.of(Component.literal(q.toolLockBlockOnInteractBlock ? "ON" : "OFF"), b -> {
            q.toggleToolLockBlockOnInteractBlock();
            cfg.markDirty();
            ConfigIO.saveIfDirty();
            screen.rebuildPreserveScroll();
         }).dimensions(x + w - 80, y, 80, 20).build();
         blockOnBlockBtn.setTooltip(Tooltip.create(Component.literal("Toggle whether Tool Lock blocks interactions with blocks.")));
         screen.addContentWidget(blockOnBlockBtn);
         y += 28;
         screen.addContentWidget(
            new HoverLabelWidget(
               x,
               y + 6,
               Math.min(w, 340),
               12,
               Component.literal("Blocked Input Reporting"),
               Tooltip.create(Component.literal("Choose where Tool Lock reports blocked inputs."))
            )
         );
         Button reportBtn = StyledButton.of(Component.literal(reportModeLabel(q.toolLockReportMode)), b -> {
            q.cycleToolLockReportMode();
            cfg.markDirty();
            ConfigIO.saveIfDirty();
            screen.rebuildPreserveScroll();
         }).dimensions(x + w - 100, y, 100, 20).build();
         reportBtn.setTooltip(Tooltip.create(Component.literal("Off, Chat, or Notice. Message: Slot is locked.")));
         screen.addContentWidget(reportBtn);
         y += 28;
         screen.addContentWidget(
            new HoverLabelWidget(
               x,
               y + 6,
               Math.min(w, 340),
               12,
               Component.literal("Locked Hotbar Slots"),
               Tooltip.create(Component.literal("Click a hotbar slot to lock mode swaps. Shift+Click toggles left-click locking."))
            )
         );
         int gridW = 180;
         this.hotbarX = x + Math.max(0, (w - gridW) / 2);
         this.hotbarY = y + 24;
      }
   }

   @Override
   public void renderText(SuiteSettingsScreen screen, GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta, int contentTopOffset) {
      this.drawHotbar(ctx, mouseX, mouseY);
   }

   @Override
   public int contentHeight(SuiteSettingsScreen screen, int contentTopOffset) {
      return contentTopOffset + 232;
   }

   @Override
   public void removed() {
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button, int contentTopOffset) {
      if (button != 0) {
         return false;
      }

      int slot = this.hotbarSlotAt(mouseX, mouseY);
      if (slot < 0) {
         return false;
      }

      QolConfig q = SuiteConfig.INSTANCE.QolConfig;
      if (net.minecraft.client.Minecraft.getInstance().hasShiftDown()) {
         q.toggleToolLockLeftClickSlot(slot);
      } else {
         q.toggleToolLockSlot(slot);
      }

      ConfigIO.saveIfDirty();
      return true;
   }

   private void drawHotbar(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
      if (this.hotbarX >= 0 && this.hotbarY >= 0) {
         Minecraft client = Minecraft.getInstance();
         QolConfig q = SuiteConfig.INSTANCE.QolConfig;
         int selected = -1;
         if (client != null && client.player != null) {
            try {
               selected = client.player.getInventory().getSelectedSlot();
            } catch (Throwable var17) {
            }
         }

         for (int slot = 0; slot < 9; slot++) {
            int x = this.hotbarX + slot * 20;
            int y = this.hotbarY;
            boolean hover = mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18;
            boolean swapLocked = q.isToolLockSlotLocked(slot);
            boolean leftLocked = q.isToolLockLeftClickSlotLocked(slot);
            boolean locked = swapLocked || leftLocked;
            int fill = locked ? -14273498 : (hover ? -14013910 : -15263977);
            int border = slot == selected ? -3722 : (locked ? -8585317 : (hover ? -5592406 : -11184811));
            ctx.fill(x, y, x + 18, y + 18, fill);
            ctx.outline(x, y, 18, 18, border);
            if (client != null && client.player != null && slot < client.player.getInventory().getContainerSize()) {
               ItemStack stack = client.player.getInventory().getItem(slot);
               if (stack != null && !stack.isEmpty()) {
                  ctx.item(stack, x + 1, y + 1);
                  ctx.itemDecorations(client.font, stack, x + 1, y + 1);
               }
            }

            if (swapLocked) {
               ctx.fill(x + 1, y + 1, x + 17, y + 17, 1434255259);
               drawCentered(ctx, client, Component.literal("S"), x + 5, y + 4, -1);
            }

            if (leftLocked) {
               ctx.fill(x + 1, y + 1, x + 17, y + 17, 1442801254);
               drawCentered(ctx, client, Component.literal("L"), x + 13, y + 4, -1);
            }
         }

         if (client != null) {
            ctx.text(
               client.font, Component.literal("Click: swap lock  Shift+Click: left-click lock"), this.hotbarX, this.hotbarY + 26, -4671304
            );
         }
      }
   }

   private static void drawCentered(GuiGraphicsExtractor ctx, Minecraft client, Component text, int centerX, int y, int color) {
      if (client != null && client.font != null) {
         ctx.text(client.font, text, centerX - client.font.width(text) / 2, y, color);
      }
   }

   private int hotbarSlotAt(double mouseX, double mouseY) {
      if (this.hotbarX < 0 || this.hotbarY < 0) {
         return -1;
      }

      if (mouseX < this.hotbarX || mouseX >= this.hotbarX + 180) {
         return -1;
      }

      if (!(mouseY < this.hotbarY) && !(mouseY >= this.hotbarY + 20)) {
         int col = (int)((mouseX - this.hotbarX) / 20.0);
         double localX = mouseX - (this.hotbarX + col * 20);
         if (localX >= 18.0) {
            return -1;
         } else {
            return col >= 0 && col < 9 ? col : -1;
         }
      } else {
         return -1;
      }
   }

   private static String reportModeLabel(QolConfig.ToolLockReportMode mode) {
      return switch (mode == null ? QolConfig.ToolLockReportMode.NOTICE : mode) {
         case OFF -> "Report: OFF";
         case CHAT -> "Report: Chat";
         case NOTICE -> "Report: Notice";
      };
   }

   private static String currentProfileTitle(QolConfig q) {
      if (q == null) {
         return "Default";
      }

      QolConfig.ToolLockProfile profile = q.getActiveToolLockProfile();
      return profile != null && profile.name != null && !profile.name.isBlank() ? profile.name : "Profile";
   }

   private static String nextProfileName(QolConfig q) {
      int n = q == null ? 1 : q.toolLockProfiles.size() + 1;
      return "Profile " + n;
   }
}
