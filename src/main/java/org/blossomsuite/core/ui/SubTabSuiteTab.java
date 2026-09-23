package org.blossomsuite.core.ui;

import net.minecraft.client.gui.DrawContext;

public final class SubTabSuiteTab implements SuiteTab {
   private final SuiteSubTab subTab;

   public SubTabSuiteTab(SuiteSubTab subTab) {
      this.subTab = subTab;
   }

   public SuiteSubTab subTab() {
      return this.subTab;
   }

   @Override
   public String titleKey() {
      return this.subTab.titleKey();
   }

   @Override
   public boolean isEnabled() {
      return true;
   }

   @Override
   public void setEnabled(boolean enabled) {
   }

   @Override
   public void build(SuiteSettingsScreen screen) {
      this.subTab.build(screen, 0);
   }

   @Override
   public void removed() {
      this.subTab.removed();
   }

   @Override
   public void renderText(SuiteSettingsScreen screen, DrawContext ctx, int mouseX, int mouseY, float delta) {
      this.subTab.renderText(screen, ctx, mouseX, mouseY, delta, 0);
   }

   @Override
   public int contentHeight(SuiteSettingsScreen screen) {
      return this.subTab.contentHeight(screen, 0);
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      return this.subTab.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount, 0);
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      return this.subTab.mouseClicked(mouseX, mouseY, button, 0);
   }
}
