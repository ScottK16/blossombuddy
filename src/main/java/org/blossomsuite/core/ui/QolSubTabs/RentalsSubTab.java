package org.blossomsuite.core.ui.QolSubTabs;

import org.blossomsuite.core.ui.StyledButton;

import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.RentalsConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.hud.RentalsHud;
import org.blossomsuite.core.ui.HoverLabelWidget;
import org.blossomsuite.core.ui.SuiteSettingsScreen;
import org.blossomsuite.core.ui.SuiteSubTab;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
public final class RentalsSubTab implements SuiteSubTab {
   private String itemDraft = "";
   private String locationDraft = "";
   private String minutesDraft = "60";
   private EditBox itemField;
   private EditBox locationField;
   private EditBox minutesField;

   @Override
   public String titleKey() {
      return "suitecore.tab.qol.rentals";
   }

   @Override
   public void build(SuiteSettingsScreen screen, int contentTopOffset) {
      RentalsConfig cfg = SuiteConfig.INSTANCE.RentalsConfig;
      int x = screen.contentX();
      int w = screen.contentW();
      int y = screen.bodyContentY() - screen.scrollOffset() + contentTopOffset;
      int rowH = 20;
      int gap = 6;
      y = this.addToggleRow(screen, x, w, y, "Rental HUD", cfg.showHud, "Shows the draggable rental countdown HUD.", () -> {
         cfg.showHud = !cfg.showHud;
         SuiteConfig.INSTANCE.markDirty();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      });
      screen.addContentWidget(
         new HoverLabelWidget(x, y + 2, 220, 12, Component.literal("Item Name"), Tooltip.create(Component.literal("What you rented. This can be any text.")))
      );
      y += 16;
      int helperW = 96;
      this.itemField = new EditBox(screen.getFont(), x, y, w - helperW - 6, 20, Component.empty());
      this.itemField.setMaxLength(64);
      this.itemField.setValue(this.itemDraft);
      this.itemField.setHint(Component.literal("Item name (blank = item in hand)"));
      this.itemField.setResponder(s -> this.itemDraft = s == null ? "" : s);
      screen.addContentWidget(this.itemField);
      Button useHeld = StyledButton.of(Component.literal("Use held item"), b -> {
         String held = heldItemName();
         if (held != null && this.itemField != null) {
            this.itemField.setValue(held);
         }
      }).dimensions(x + w - helperW, y, helperW, 20).build();
      useHeld.setTooltip(Tooltip.create(Component.literal("Fills in the name of the item you are holding, the same as /buddy rent does.")));
      screen.addContentWidget(useHeld);
      y += 28;
      screen.addContentWidget(
         new HoverLabelWidget(x, y + 2, 220, 12, Component.literal("Where Rented"), Tooltip.create(Component.literal("Where you rented it from. This can be any text.")))
      );
      y += 16;
      this.locationField = new EditBox(screen.getFont(), x, y, w - helperW - 6, 20, Component.empty());
      this.locationField.setMaxLength(64);
      this.locationField.setValue(this.locationDraft);
      this.locationField.setHint(Component.literal("Pw name (blank = this realm)"));
      this.locationField.setResponder(s -> this.locationDraft = s == null ? "" : s);
      screen.addContentWidget(this.locationField);
      Button useRealm = StyledButton.of(Component.literal("This realm"), b -> {
         String realm = org.blossomsuite.core.util.WorldGate.Server;
         if (realm != null && !realm.isBlank() && this.locationField != null) {
            this.locationField.setValue(realm);
         }
      }).dimensions(x + w - helperW, y, helperW, 20).build();
      useRealm.setTooltip(Tooltip.create(Component.literal("Fills in the realm you are on.")));
      screen.addContentWidget(useRealm);
      y += 28;
      screen.addContentWidget(
         new HoverLabelWidget(
            x, y + 2, 220, 12, Component.literal("Minutes"), Tooltip.create(Component.literal("How long the rental should count down. Range: 1 to 10080 minutes."))
         )
      );
      y += 16;
      int addW = 90;
      this.minutesField = new EditBox(screen.getFont(), x, y, w - addW - 6, 20, Component.empty());
      this.minutesField.setMaxLength(8);
      this.minutesField.setValue(this.minutesDraft);
      this.minutesField.setHint(Component.literal("60"));
      this.minutesField.setResponder(s -> this.minutesDraft = s == null ? "" : s);
      screen.addContentWidget(this.minutesField);
      Button addButton = StyledButton.of(Component.literal("Add"), b -> {
         int minutes = parseMinutes(this.minutesDraft);
         // the same defaults as /buddy rent: no name typed means the item in your hand, no place typed means this realm
         String heldName = heldItemName();
         String item = org.blossomsuite.core.util.RentalDefaults.itemOrHeld(this.itemDraft, heldName, null, heldName != null);
         String place = org.blossomsuite.core.util.RentalDefaults.place(this.locationDraft, org.blossomsuite.core.util.WorldGate.Server);
         cfg.addRental(item, place, minutes);
         this.itemDraft = "";
         this.locationDraft = "";
         this.minutesDraft = "60";
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(x + w - addW, y, addW, 20).build();
      addButton.setTooltip(Tooltip.create(Component.literal("Starts tracking this rental. Leave the name blank to use the item in your hand and the place blank to use this realm, like /buddy rent <minutes>.")));
      screen.addContentWidget(addButton);
      y += 30;
      screen.addContentWidget(
         new HoverLabelWidget(x, y, w, 12, Component.literal("In chat: /buddy rent <minutes> [place] with the rented item in your hand does the same."), null)
      );
      y += 18;
      int halfW = (w - 6) / 2;
      Button pauseAll = StyledButton.of(Component.literal("Pause All"), b -> {
         cfg.pauseAll();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(x, y, halfW, 20).build();
      pauseAll.setTooltip(Tooltip.create(Component.literal("Pauses every active rental timer.")));
      screen.addContentWidget(pauseAll);
      Button resumeAll = StyledButton.of(Component.literal("Resume All"), b -> {
         cfg.resumeAll();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(x + halfW + 6, y, halfW, 20).build();
      resumeAll.setTooltip(Tooltip.create(Component.literal("Resumes every paused rental timer.")));
      screen.addContentWidget(resumeAll);
      y += 28;
      Button clearFinished = StyledButton.of(Component.literal("Clear Finished"), b -> {
         cfg.clearFinished();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(x, y, halfW, 20).build();
      clearFinished.setTooltip(Tooltip.create(Component.literal("Removes rentals whose timers are done.")));
      screen.addContentWidget(clearFinished);
      Button clearAll = StyledButton.of(Component.literal("Clear All"), b -> {
         cfg.entries.clear();
         SuiteConfig.INSTANCE.markDirty();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(x + halfW + 6, y, halfW, 20).build();
      clearAll.setTooltip(Tooltip.create(Component.literal("Removes every rental entry.")));
      screen.addContentWidget(clearAll);
      y += 34;
      long now = System.currentTimeMillis();
      if (cfg.entries.isEmpty()) {
         screen.addContentWidget(
            new HoverLabelWidget(
               x,
               y,
               Math.min(w, 320),
               12,
               Component.literal("No rentals are being tracked."),
               Tooltip.create(Component.literal("Add a rental above to start tracking countdown time."))
            )
         );
      } else {
         screen.addContentWidget(
            new HoverLabelWidget(x, y, 180, 12, Component.literal("Tracked Rentals"), Tooltip.create(Component.literal("Finished rentals stay here until you clear them.")))
         );
         y += 16;

         for (RentalsConfig.RentalEntry entry : cfg.entries) {
            long remaining = entry.remainingMs(now);
            String title = safe(entry.itemName) + " - " + RentalsHud.formatRemaining(remaining);
            screen.addContentWidget(
               new HoverLabelWidget(x, y + 2, Math.min(w - 158, 360), 12, Component.literal(title), Tooltip.create(Component.literal("Where: " + safe(entry.location))))
            );
            Button pauseResume = StyledButton.of(Component.literal(entry.paused ? "Resume" : "Pause"), b -> {
               long clickNow = System.currentTimeMillis();
               if (entry.paused) {
                  entry.resume(clickNow);
               } else {
                  entry.pause(clickNow);
               }

               ConfigIO.saveIfDirty();
               screen.rebuildPreserveScroll();
            }).dimensions(x + w - 150, y, 70, 20).build();
            pauseResume.active = remaining > 0L || entry.paused;
            screen.addContentWidget(pauseResume);
            Button clear = StyledButton.of(Component.literal("Clear"), b -> {
               cfg.entries.remove(entry);
               SuiteConfig.INSTANCE.markDirty();
               ConfigIO.saveIfDirty();
               screen.rebuildPreserveScroll();
            }).dimensions(x + w - 74, y, 74, 20).build();
            screen.addContentWidget(clear);
            y += 22;
            screen.addContentWidget(
               new HoverLabelWidget(
                  x + 8,
                  y,
                  Math.min(w - 8, 420),
                  12,
                  Component.literal("Where: " + safe(entry.location) + "  Time: " + Math.max(1, entry.totalMinutes) + " min"),
                  null
               )
            );
            y += 20;
         }
      }
   }

   @Override
   public void removed() {
      this.itemField = null;
      this.locationField = null;
      this.minutesField = null;
   }

   @Override
   public int contentHeight(SuiteSettingsScreen screen, int contentTopOffset) {
      int rows = SuiteConfig.INSTANCE.RentalsConfig.entries.size();
      return contentTopOffset + 8 + 24 + 44 + 44 + 50 + 28 + 34 + 16 + 14 + (rows == 0 ? 32 : rows * 42) + 24;
   }

   private int addToggleRow(SuiteSettingsScreen screen, int x, int w, int y, String label, boolean enabled, String tooltip, Runnable onPress) {
      screen.addContentWidget(new HoverLabelWidget(x, y + 6, 180, 12, Component.literal(label), Tooltip.create(Component.literal(tooltip))));
      Button button = StyledButton.of(Component.literal(enabled ? "ON" : "OFF"), b -> onPress.run()).dimensions(x + w - 80, y, 80, 20).build();
      button.setTooltip(Tooltip.create(Component.literal(tooltip)));
      screen.addContentWidget(button);
      return y + 28;
   }

   /** The name of the item in the player's main hand, or null if they hold nothing. */
   private static String heldItemName() {
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      if (mc == null || mc.player == null) {
         return null;
      }

      net.minecraft.world.item.ItemStack held = mc.player.getMainHandItem();
      if (held == null || held.isEmpty()) {
         return null;
      }

      return org.blossomsuite.core.util.RentalDefaults.itemName(held.getHoverName().getString(), held.getItemName().getString());
   }

   private static int parseMinutes(String raw) {
      try {
         int minutes = Integer.parseInt(raw == null ? "" : raw.trim());
         return Math.max(1, Math.min(10080, minutes));
      } catch (Exception ignored) {
         return 60;
      }
   }

   private static String safe(String s) {
      return s != null && !s.isBlank() ? s.trim() : "-";
   }
}
