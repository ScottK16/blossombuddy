package org.blossomsuite.core.ui;

import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.DungeonConfig;
import org.blossomsuite.core.config.SuiteConfig;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class DungeonTab implements SuiteTab {
   private TextFieldWidget cooldownHoursField;

   @Override
   public String titleKey() {
      return "suitecore.tab.dungeon";
   }

   @Override
   public boolean isEnabled() {
      return SuiteConfig.INSTANCE.DungeonConfig.enabled;
   }

   @Override
   public void setEnabled(boolean enabled) {
      SuiteConfig.INSTANCE.DungeonConfig.enabled = enabled;
      SuiteConfig.INSTANCE.markDirty();
   }

   @Override
   public void build(SuiteSettingsScreen screen) {
      DungeonConfig cfg = SuiteConfig.INSTANCE.DungeonConfig;
      int x = screen.contentX();
      int w = screen.contentW();
      int y = screen.bodyContentY() - screen.scrollOffset();
      y = this.addToggleRow(screen, x, w, y, "Dungeon Tracking", cfg.enabled, "Watches dungeon chat messages to track runs and the 8 hour cooldown.", () -> {
         cfg.toggleEnabled();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      });
      y = this.addToggleRow(screen, x, w, y, "Dungeon HUD", cfg.showHud, "Shows dungeon cooldown status on its own draggable HUD.", () -> {
         cfg.toggleShowHud();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      });
      y = this.addToggleRow(screen, x, w, y, "HUD Header", cfg.showHeader, "Shows or hides the Dungeon title bar on the Dungeon HUD.", () -> {
         cfg.toggleShowHeader();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      });
      y = this.addCycleRow(
         screen,
         x,
         w,
         y,
         "HUD Servers",
         modeLabel(cfg.hudServerMode),
         "Current Server shows only the realm you are on. All Servers shows tracked cooldowns for every realm.",
         () -> {
            cfg.cycleHudServerMode();
            ConfigIO.saveIfDirty();
            screen.rebuildPreserveScroll();
         }
      );
      y = this.addToggleRow(screen, x, w, y, "Run Reports In Chat", cfg.reportRunsInChat, "Prints a local summary when a dungeon completes or fails.", () -> {
         cfg.toggleReportRunsInChat();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      });
      this.addIntRow(
         screen,
         x,
         w,
         y,
         "Cooldown Hours",
         cfg.cooldownHours,
         "Dungeon cooldown length. Akuma dungeons are normally 8 hours.",
         value -> cfg.setCooldownHours(value)
      );
   }

   @Override
   public void removed() {
      if (this.cooldownHoursField != null) {
         try {
            SuiteConfig.INSTANCE.DungeonConfig.setCooldownHours(Integer.parseInt(this.cooldownHoursField.getText().trim()));
            ConfigIO.saveIfDirty();
         } catch (NumberFormatException var2) {
         }
      }
   }

   @Override
   public int contentHeight(SuiteSettingsScreen screen) {
      return 176;
   }

   private int addToggleRow(SuiteSettingsScreen screen, int x, int w, int y, String label, boolean enabled, String tooltip, Runnable onPress) {
      screen.addContentWidget(new HoverLabelWidget(x, y + 6, 220, 12, Text.literal(label), Tooltip.of(Text.literal(tooltip))));
      ButtonWidget button = StyledButton.of(Text.literal(enabled ? "ON" : "OFF"), b -> onPress.run()).dimensions(x + w - 80, y, 80, 20).build();
      button.setTooltip(Tooltip.of(Text.literal(tooltip)));
      screen.addContentWidget(button);
      return y + 24;
   }

   private int addCycleRow(SuiteSettingsScreen screen, int x, int w, int y, String label, String value, String tooltip, Runnable onPress) {
      screen.addContentWidget(new HoverLabelWidget(x, y + 6, 220, 12, Text.literal(label), Tooltip.of(Text.literal(tooltip))));
      ButtonWidget button = StyledButton.of(Text.literal(value), b -> onPress.run()).dimensions(x + w - 120, y, 120, 20).build();
      button.setTooltip(Tooltip.of(Text.literal(tooltip)));
      screen.addContentWidget(button);
      return y + 24;
   }

   private void addIntRow(SuiteSettingsScreen screen, int x, int w, int y, String label, int value, String tooltip, DungeonTab.IntSetter setter) {
      screen.addContentWidget(new HoverLabelWidget(x, y + 6, 220, 12, Text.literal(label), Tooltip.of(Text.literal(tooltip))));
      this.cooldownHoursField = new TextFieldWidget(screen.getTextRenderer(), x + w - 80, y, 80, 20, Text.empty());
      this.cooldownHoursField.setMaxLength(2);
      this.cooldownHoursField.setText(String.valueOf(value));
      this.cooldownHoursField.setTooltip(Tooltip.of(Text.literal(tooltip)));
      this.cooldownHoursField.setChangedListener(s -> {
         try {
            setter.set(Integer.parseInt(s != null && !s.isBlank() ? s.trim() : "8"));
            ConfigIO.saveIfDirty();
         } catch (NumberFormatException var3) {
         }
      });
      screen.addContentWidget(this.cooldownHoursField);
   }

   private static String modeLabel(DungeonConfig.HudServerMode mode) {
      return mode == DungeonConfig.HudServerMode.ALL_SERVERS ? "All Servers" : "Current Server";
   }

   private interface IntSetter {
      void set(int var1);
   }
}
