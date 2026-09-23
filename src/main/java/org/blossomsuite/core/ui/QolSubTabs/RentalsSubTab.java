package org.blossomsuite.core.ui.QolSubTabs;

import org.blossomsuite.core.ui.StyledButton;

import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.RentalsConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.hud.RentalsHud;
import org.blossomsuite.core.ui.HoverLabelWidget;
import org.blossomsuite.core.ui.SuiteSettingsScreen;
import org.blossomsuite.core.ui.SuiteSubTab;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class RentalsSubTab implements SuiteSubTab {
   private String itemDraft = "";
   private String locationDraft = "";
   private String minutesDraft = "60";
   private TextFieldWidget itemField;
   private TextFieldWidget locationField;
   private TextFieldWidget minutesField;

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
         new HoverLabelWidget(x, y + 2, 220, 12, Text.literal("Item Name"), Tooltip.of(Text.literal("What you rented. This can be any text.")))
      );
      y += 16;
      this.itemField = new TextFieldWidget(screen.getTextRenderer(), x, y, w, 20, Text.empty());
      this.itemField.setMaxLength(64);
      this.itemField.setText(this.itemDraft);
      this.itemField.setPlaceholder(Text.literal("Item name"));
      this.itemField.setChangedListener(s -> this.itemDraft = s == null ? "" : s);
      screen.addContentWidget(this.itemField);
      y += 28;
      screen.addContentWidget(
         new HoverLabelWidget(x, y + 2, 220, 12, Text.literal("Where Rented"), Tooltip.of(Text.literal("Where you rented it from. This can be any text.")))
      );
      y += 16;
      this.locationField = new TextFieldWidget(screen.getTextRenderer(), x, y, w, 20, Text.empty());
      this.locationField.setMaxLength(64);
      this.locationField.setText(this.locationDraft);
      this.locationField.setPlaceholder(Text.literal("Pw name"));
      this.locationField.setChangedListener(s -> this.locationDraft = s == null ? "" : s);
      screen.addContentWidget(this.locationField);
      y += 28;
      screen.addContentWidget(
         new HoverLabelWidget(
            x, y + 2, 220, 12, Text.literal("Minutes"), Tooltip.of(Text.literal("How long the rental should count down. Range: 1 to 10080 minutes."))
         )
      );
      y += 16;
      int addW = 90;
      this.minutesField = new TextFieldWidget(screen.getTextRenderer(), x, y, w - addW - 6, 20, Text.empty());
      this.minutesField.setMaxLength(8);
      this.minutesField.setText(this.minutesDraft);
      this.minutesField.setPlaceholder(Text.literal("60"));
      this.minutesField.setChangedListener(s -> this.minutesDraft = s == null ? "" : s);
      screen.addContentWidget(this.minutesField);
      ButtonWidget addButton = StyledButton.of(Text.literal("Add"), b -> {
         int minutes = parseMinutes(this.minutesDraft);
         cfg.addRental(this.itemDraft, this.locationDraft, minutes);
         this.itemDraft = "";
         this.locationDraft = "";
         this.minutesDraft = "60";
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(x + w - addW, y, addW, 20).build();
      addButton.setTooltip(Tooltip.of(Text.literal("Starts tracking this rental.")));
      screen.addContentWidget(addButton);
      y += 34;
      int halfW = (w - 6) / 2;
      ButtonWidget pauseAll = StyledButton.of(Text.literal("Pause All"), b -> {
         cfg.pauseAll();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(x, y, halfW, 20).build();
      pauseAll.setTooltip(Tooltip.of(Text.literal("Pauses every active rental timer.")));
      screen.addContentWidget(pauseAll);
      ButtonWidget resumeAll = StyledButton.of(Text.literal("Resume All"), b -> {
         cfg.resumeAll();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(x + halfW + 6, y, halfW, 20).build();
      resumeAll.setTooltip(Tooltip.of(Text.literal("Resumes every paused rental timer.")));
      screen.addContentWidget(resumeAll);
      y += 28;
      ButtonWidget clearFinished = StyledButton.of(Text.literal("Clear Finished"), b -> {
         cfg.clearFinished();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(x, y, halfW, 20).build();
      clearFinished.setTooltip(Tooltip.of(Text.literal("Removes rentals whose timers are done.")));
      screen.addContentWidget(clearFinished);
      ButtonWidget clearAll = StyledButton.of(Text.literal("Clear All"), b -> {
         cfg.entries.clear();
         SuiteConfig.INSTANCE.markDirty();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(x + halfW + 6, y, halfW, 20).build();
      clearAll.setTooltip(Tooltip.of(Text.literal("Removes every rental entry.")));
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
               Text.literal("No rentals are being tracked."),
               Tooltip.of(Text.literal("Add a rental above to start tracking countdown time."))
            )
         );
      } else {
         screen.addContentWidget(
            new HoverLabelWidget(x, y, 180, 12, Text.literal("Tracked Rentals"), Tooltip.of(Text.literal("Finished rentals stay here until you clear them.")))
         );
         y += 16;

         for (RentalsConfig.RentalEntry entry : cfg.entries) {
            long remaining = entry.remainingMs(now);
            String title = safe(entry.itemName) + " - " + RentalsHud.formatRemaining(remaining);
            screen.addContentWidget(
               new HoverLabelWidget(x, y + 2, Math.min(w - 158, 360), 12, Text.literal(title), Tooltip.of(Text.literal("Where: " + safe(entry.location))))
            );
            ButtonWidget pauseResume = StyledButton.of(Text.literal(entry.paused ? "Resume" : "Pause"), b -> {
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
            ButtonWidget clear = StyledButton.of(Text.literal("Clear"), b -> {
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
                  Text.literal("Where: " + safe(entry.location) + "  Time: " + Math.max(1, entry.totalMinutes) + " min"),
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
      return contentTopOffset + 8 + 24 + 44 + 44 + 50 + 28 + 34 + 16 + (rows == 0 ? 32 : rows * 42) + 24;
   }

   private int addToggleRow(SuiteSettingsScreen screen, int x, int w, int y, String label, boolean enabled, String tooltip, Runnable onPress) {
      screen.addContentWidget(new HoverLabelWidget(x, y + 6, 180, 12, Text.literal(label), Tooltip.of(Text.literal(tooltip))));
      ButtonWidget button = StyledButton.of(Text.literal(enabled ? "ON" : "OFF"), b -> onPress.run()).dimensions(x + w - 80, y, 80, 20).build();
      button.setTooltip(Tooltip.of(Text.literal(tooltip)));
      screen.addContentWidget(button);
      return y + 28;
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
