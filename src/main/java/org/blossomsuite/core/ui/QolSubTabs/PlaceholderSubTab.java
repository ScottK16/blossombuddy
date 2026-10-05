package org.blossomsuite.core.ui.QolSubTabs;

import org.blossomsuite.core.ui.SuiteSettingsScreen;
import org.blossomsuite.core.ui.SuiteSubTab;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
public class PlaceholderSubTab implements SuiteSubTab {
   private final String titleKey;
   private final String message;

   public PlaceholderSubTab(String titleKey, String message) {
      this.titleKey = titleKey;
      this.message = message;
   }

   @Override
   public String titleKey() {
      return this.titleKey;
   }

   @Override
   public void build(SuiteSettingsScreen screen, int contentTopOffset) {
   }

   @Override
   public void renderText(SuiteSettingsScreen screen, GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta, int contentTopOffset) {
      int x = screen.contentX();
      int y = screen.bodyContentY() + contentTopOffset + 8;
      ctx.text(screen.getFont(), Component.literal(this.message), x, y, -3355444);
   }

   @Override
   public void removed() {
   }
}
