package org.blossomsuite.core.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;

public final class SuiteSubTabGroup implements SuiteSubTab {
   private final String titleKey;
   private final List<SuiteSubTab> children = new ArrayList<>();
   private SuiteSubTab current;

   public SuiteSubTabGroup(String titleKey, SuiteSubTab... tabs) {
      this.titleKey = titleKey;
      if (tabs != null) {
         for (SuiteSubTab tab : tabs) {
            if (tab != null) {
               this.children.add(tab);
            }
         }
      }

      if (!this.children.isEmpty()) {
         this.current = this.children.get(0);
      }
   }

   @Override
   public String titleKey() {
      return this.titleKey;
   }

   @Override
   public void build(SuiteSettingsScreen screen, int contentTopOffset) {
      if (this.current != null) {
         this.current.build(screen, contentTopOffset);
      }
   }

   @Override
   public void removed() {
      if (this.current != null) {
         this.current.removed();
      }
   }

   @Override
   public void renderText(SuiteSettingsScreen screen, DrawContext ctx, int mouseX, int mouseY, float delta, int contentTopOffset) {
      if (this.current != null) {
         this.current.renderText(screen, ctx, mouseX, mouseY, delta, contentTopOffset);
      }
   }

   @Override
   public int contentHeight(SuiteSettingsScreen screen, int contentTopOffset) {
      return this.current == null ? 0 : this.current.contentHeight(screen, contentTopOffset);
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, int contentTopOffset) {
      return this.current != null && this.current.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount, contentTopOffset);
   }

   public List<SuiteSubTab> children() {
      return List.copyOf(this.children);
   }

   public SuiteSubTab currentChild() {
      return this.current;
   }

   public void selectChild(SuiteSubTab child) {
      if (child != null && child != this.current && this.children.contains(child)) {
         if (this.current != null) {
            this.current.removed();
         }

         this.current = child;
      }
   }
}
