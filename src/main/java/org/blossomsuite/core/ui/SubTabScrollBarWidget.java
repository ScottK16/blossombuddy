package org.blossomsuite.core.ui;

import java.util.function.IntConsumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

public final class SubTabScrollBarWidget extends ClickableWidget {
   private final int totalTabs;
   private final int visibleTabs;
   private int startIndex;
   private final IntConsumer onChange;
   private boolean dragging = false;
   private double dragOffsetX = 0.0;

   public SubTabScrollBarWidget(int x, int y, int w, int h, int totalTabs, int visibleTabs, int startIndex, IntConsumer onChange) {
      super(x, y, w, h, Text.empty());
      this.totalTabs = Math.max(0, totalTabs);
      this.visibleTabs = Math.max(1, visibleTabs);
      this.startIndex = Math.max(0, startIndex);
      this.onChange = onChange;
      this.active = true;
      this.visible = true;
   }

   public void setStartIndex(int startIndex) {
      this.startIndex = Math.max(0, startIndex);
   }

   private int maxStart() {
      return Math.max(0, this.totalTabs - this.visibleTabs);
   }

   private int thumbW() {
      int max = this.maxStart();
      if (max <= 0) {
         return this.getWidth();
      }

      double frac = (double)this.visibleTabs / this.totalTabs;
      int w = (int)Math.round(frac * this.getWidth());
      return Math.max(18, Math.min(this.getWidth(), w));
   }

   private int thumbX() {
      int max = this.maxStart();
      int tw = this.thumbW();
      if (max <= 0) {
         return this.getX();
      }

      int track = this.getWidth() - tw;
      if (track <= 0) {
         return this.getX();
      }

      double t = (double)this.startIndex / max;
      return this.getX() + (int)Math.round(t * track);
   }

   private void setFromThumbLeft(int thumbLeft) {
      int max = this.maxStart();
      if (max > 0) {
         int tw = this.thumbW();
         int track = this.getWidth() - tw;
         if (track > 0) {
            int rel = Math.max(0, Math.min(track, thumbLeft - this.getX()));
            int idx = (int)Math.round((double)rel / track * max);
            idx = Math.max(0, Math.min(max, idx));
            if (idx != this.startIndex) {
               this.startIndex = idx;
               if (this.onChange != null) {
                  this.onChange.accept(idx);
               }
            }
         }
      }
   }

   @Override
   public void onClick(double mouseX, double mouseY) {
      int tx = this.thumbX();
      int tw = this.thumbW();
      if (mouseX >= tx && mouseX <= tx + tw) {
         this.dragging = true;
         this.dragOffsetX = mouseX - tx;
      } else {
         if (mouseX < tx) {
            this.startIndex = Math.max(0, this.startIndex - 1);
            if (this.onChange != null) {
               this.onChange.accept(this.startIndex);
            }
         } else {
            this.startIndex = Math.min(this.maxStart(), this.startIndex + 1);
            if (this.onChange != null) {
               this.onChange.accept(this.startIndex);
            }
         }
      }
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
      if (!this.dragging) {
         return false;
      }

      if (button != 0) {
         return false;
      }

      this.setFromThumbLeft((int)Math.round(mouseX - this.dragOffsetX));
      return true;
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (this.dragging && button == 0) {
         this.dragging = false;
         return true;
      } else {
         return super.mouseReleased(mouseX, mouseY, button);
      }
   }

   @Override
   protected void appendClickableNarrations(NarrationMessageBuilder builder) {
   }

   @Override
   protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
      int x1 = this.getX();
      int y1 = this.getY();
      int x2 = x1 + this.getWidth();
      int y2 = y1 + this.getHeight();
      context.fill(x1, y1, x2, y2, -15461356);
      context.drawBorder(x1, y1, this.getWidth(), this.getHeight(), -12961222);
      int tx = this.thumbX();
      int tw = this.thumbW();
      boolean hover = mouseX >= tx && mouseX <= tx + tw && mouseY >= y1 && mouseY <= y2;
      int fill = this.dragging ? -5197648 : (hover ? -6645094 : -8750470);
      context.fill(tx, y1 + 1, tx + tw, y2 - 1, fill);
      context.fill(tx + 1, y1 + 2, tx + tw - 1, y1 + 3, 587202559);
      MinecraftClient.getInstance();
   }
}
