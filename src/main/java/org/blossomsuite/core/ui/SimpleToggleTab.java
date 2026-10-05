package org.blossomsuite.core.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
public class SimpleToggleTab implements SuiteTab {
   private final String titleKey;
   private boolean enabled = false;

   public SimpleToggleTab(String titleKey) {
      this.titleKey = titleKey;
   }

   @Override
   public String titleKey() {
      return this.titleKey;
   }

   @Override
   public boolean isEnabled() {
      return this.enabled;
   }

   @Override
   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   @Override
   public void build(SuiteSettingsScreen screen) {
      int x = screen.contentX();
      int w = screen.contentW();
      int y = screen.contentY();
      int rowH = 20;
      int toggleW = 80;
      int toggleX = x + w - toggleW;
      screen.addPanelWidget(StyledButton.of(Component.literal(this.enabled ? "ON" : "OFF"), b -> {
         this.enabled = !this.enabled;
         screen.rebuildFromTab();
      }).dimensions(toggleX, y, toggleW, rowH).build());
   }

   @Override
   public void renderText(SuiteSettingsScreen screen, GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
      int x = screen.contentX();
      int y = screen.contentY();
      ctx.text(screen.getFont(), Component.translatable("suitecore.option.enabled"), x, y + 6, -1);
      if (!this.enabled) {
         ctx.text(screen.getFont(), Component.translatable("suitecore.option.disabled_hint"), x, y + 28, -3355444);
      }
   }

   @Override
   public void removed() {
   }
}
