package org.blossomsuite.core.ui;

import net.minecraft.client.gui.DrawContext;

public interface SuiteTab {
   String titleKey();

   boolean isEnabled();

   void setEnabled(boolean var1);

   void build(SuiteSettingsScreen var1);

   void removed();

   default void renderText(SuiteSettingsScreen screen, DrawContext ctx, int mouseX, int mouseY, float delta) {
   }

   default int contentHeight(SuiteSettingsScreen screen) {
      return 0;
   }

   default void buildHeaderControls(SuiteSettingsScreen screen) {
   }

   default boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      return false;
   }

   default boolean keyReleased(int keyCode, int scanCode, int modifiers) {
      return false;
   }

   default boolean mouseClicked(double mouseX, double mouseY, int button) {
      return false;
   }

   default boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      return false;
   }

   default int bodyTopInset() {
      return 0;
   }
}
