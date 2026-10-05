package org.blossomsuite.core.ui.QolSubTabs;

import org.blossomsuite.core.ui.StyledButton;

import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.ui.HoverLabelWidget;
import org.blossomsuite.core.ui.SuiteSettingsScreen;
import org.blossomsuite.core.ui.SuiteSubTab;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
public final class AutoFlySubTab implements SuiteSubTab {
   @Override
   public String titleKey() {
      return "suitecore.tab.auto_fly";
   }

   @Override
   public void build(SuiteSettingsScreen screen, int contentTopOffset) {
      SuiteConfig cfg = SuiteConfig.INSTANCE;
      int x = screen.contentX();
      int w = screen.contentW();
      int y = screen.bodyContentY() - screen.scrollOffset() + contentTopOffset;
      int rowH = 20;
      screen.addContentWidget(
         new HoverLabelWidget(
            x,
            y + 6,
            260,
            12,
            Component.literal("Auto Fly"),
            Tooltip.create(
               Component.literal("Automatically runs /fly enable after first login or a scoreboard world change. Teleport commands arm faster scoreboard watching.")
            )
         )
      );
      Button button = StyledButton.of(Component.literal(cfg.QolConfig.autoFlyOnRwWorldLoad ? "ON" : "OFF"), b -> {
         cfg.QolConfig.autoFlyOnRwWorldLoad = !cfg.QolConfig.autoFlyOnRwWorldLoad;
         cfg.markDirty();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(x + w - 80, y, 80, rowH).build();
      button.setTooltip(Tooltip.create(Component.literal("Normal chat is ignored. Excluded worlds are skipped and AutoFly never sends /fly disable.")));
      screen.addContentWidget(button);
   }

   @Override
   public int contentHeight(SuiteSettingsScreen screen, int contentTopOffset) {
      return contentTopOffset + 48;
   }

   @Override
   public void removed() {
      ConfigIO.saveIfDirty();
   }
}
