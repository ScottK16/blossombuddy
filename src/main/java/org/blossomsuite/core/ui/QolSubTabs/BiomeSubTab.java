package org.blossomsuite.core.ui.QolSubTabs;

import org.blossomsuite.core.ui.StyledButton;

import org.blossomsuite.core.config.BiomeHudConfig;
import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.ui.HoverLabelWidget;
import org.blossomsuite.core.ui.SuiteSettingsScreen;
import org.blossomsuite.core.ui.SuiteSubTab;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class BiomeSubTab implements SuiteSubTab {
   @Override
   public String titleKey() {
      return "suitecore.tab.biome";
   }

   @Override
   public void build(SuiteSettingsScreen screen, int contentTopOffset) {
      BiomeHudConfig cfg = SuiteConfig.INSTANCE.BiomeHudConfig;
      int x = screen.contentX();
      int w = screen.contentW();
      int y = screen.bodyContentY() - screen.scrollOffset() + contentTopOffset;
      y = this.addToggleRow(screen, x, w, y, "Biome HUD", cfg.showHud, "Shows your current biome on-screen.\nUse HUD Edit Mode to move or resize it.", () -> {
         cfg.toggleShowHud();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      });
      this.addToggleRow(screen, x, w, y, "Biome Header", cfg.showHeader, "Shows or hides the Biome title bar on the biome HUD.", () -> {
         cfg.toggleShowHeader();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      });
   }

   @Override
   public void removed() {
   }

   @Override
   public int contentHeight(SuiteSettingsScreen screen, int contentTopOffset) {
      return contentTopOffset + 8 + 48 + 24;
   }

   private int addToggleRow(SuiteSettingsScreen screen, int x, int w, int y, String label, boolean enabled, String tooltip, Runnable onPress) {
      screen.addContentWidget(new HoverLabelWidget(x, y + 6, 180, 12, Text.literal(label), Tooltip.of(Text.literal(tooltip))));
      ButtonWidget button = StyledButton.of(Text.literal(enabled ? "ON" : "OFF"), b -> onPress.run()).dimensions(x + w - 80, y, 80, 20).build();
      button.setTooltip(Tooltip.of(Text.literal(tooltip)));
      screen.addContentWidget(button);
      return y + 24;
   }
}
