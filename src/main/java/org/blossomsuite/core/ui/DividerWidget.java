package org.blossomsuite.core.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.Selectable.SelectionType;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;

public class DividerWidget implements Drawable, Element, Selectable {
   private final int x;
   private final int y;
   private final int length;
   private final int thickness;
   private final int color;
   private final DividerWidget.Orientation orientation;

   @Override
   public void appendNarrations(NarrationMessageBuilder builder) {
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
   public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
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
   public SelectionType getType() {
      return SelectionType.NONE;
   }

   public enum Orientation {
      HORIZONTAL,
      VERTICAL;
   }
}
