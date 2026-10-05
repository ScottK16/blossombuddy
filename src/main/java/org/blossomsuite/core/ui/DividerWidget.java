package org.blossomsuite.core.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratableEntry.NarrationPriority;
import net.minecraft.client.gui.narration.NarrationElementOutput;
public class DividerWidget implements Renderable, GuiEventListener, NarratableEntry {
   private final int x;
   private final int y;
   private final int length;
   private final int thickness;
   private final int color;
   private final DividerWidget.Orientation orientation;

   @Override
   public void updateNarration(NarrationElementOutput builder) {
   }

   public DividerWidget(int x, int y, int length) {
      this(x, y, length, 1, 872415231, DividerWidget.Orientation.HORIZONTAL);
   }

   public DividerWidget(int x, int y, int length, int thickness) {
      this(x, y, length, thickness, 872415231, DividerWidget.Orientation.HORIZONTAL);
   }

   public DividerWidget(int x, int y, int length, int thickness, int color) {
      this(x, y, length, thickness, color, DividerWidget.Orientation.HORIZONTAL);
   }

   public DividerWidget(int x, int y, int length, int thickness, int color, DividerWidget.Orientation orientation) {
      this.x = x;
      this.y = y;
      this.length = length;
      this.thickness = thickness;
      this.color = color;
      this.orientation = orientation;
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
      if (this.orientation == DividerWidget.Orientation.HORIZONTAL) {
         ctx.fill(this.x, this.y, this.x + this.length, this.y + this.thickness, this.color);
      } else {
         ctx.fill(this.x, this.y, this.x + this.thickness, this.y + this.length, this.color);
      }
   }

   @Override
   public boolean isMouseOver(double mouseX, double mouseY) {
      return false;
   }

   @Override
   public void setFocused(boolean focused) {
   }

   @Override
   public boolean isFocused() {
      return false;
   }

   @Override
   public NarrationPriority narrationPriority() {
      return NarrationPriority.NONE;
   }

   public enum Orientation {
      HORIZONTAL,
      VERTICAL;
   }
}
