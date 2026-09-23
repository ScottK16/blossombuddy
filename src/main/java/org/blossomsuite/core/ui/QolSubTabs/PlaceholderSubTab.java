package org.blossomsuite.core.ui.QolSubTabs;

import org.blossomsuite.core.ui.SuiteSettingsScreen;
import org.blossomsuite.core.ui.SuiteSubTab;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

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
   public void renderText(SuiteSettingsScreen screen, DrawContext ctx, int mouseX, int mouseY, float delta, int contentTopOffset) {
      int x = screen.contentX();
      int y = screen.bodyContentY() + contentTopOffset + 8;
      ctx.drawTextWithShadow(screen.getTextRenderer(), Text.literal(this.message), x, y, -3355444);
   }

   @Override
   public void removed() {
   }
}
