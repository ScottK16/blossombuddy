package org.blossomsuite.core.ui;

import net.minecraft.client.gui.DrawContext;

public interface SuiteSubTab {
   String titleKey();

   void build(SuiteSettingsScreen var1, int var2);

   void removed();

   default void renderText(SuiteSettingsScreen screen, DrawContext ctx, int mouseX, int mouseY, float delta, int contentTopOffset) {
   }

   default int contentHeight(SuiteSettingsScreen screen, int contentTopOffset) {
      return 0;
   }

   default boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, int contentTopOffset) {
      return false;
   }

   default boolean mouseClicked(double mouseX, double mouseY, int button, int contentTopOffset) {
      return false;
   }
}
