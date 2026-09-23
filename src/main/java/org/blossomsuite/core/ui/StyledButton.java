package org.blossomsuite.core.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Drop-in for {@link ButtonWidget} with the BlossomBuddy look. Same builder calls as the vanilla one,
 * so a call site only needs {@code ButtonWidget.builder(} changed to {@code StyledButton.of(}.
 */
public class StyledButton extends ButtonWidget implements TooltipHolder {
   private boolean accent = false;
   private Tooltip heldTooltip;

   protected StyledButton(int x, int y, int width, int height, Text message, PressAction onPress, NarrationSupplier narration) {
      super(x, y, width, height, message, onPress, narration);
   }

   public static Builder of(Text message, PressAction onPress) {
      return new Builder(message, onPress);
   }

   @Override
   public void setTooltip(Tooltip tooltip) {
      this.heldTooltip = tooltip;
      super.setTooltip(tooltip);
   }

   @Override
   public Tooltip heldTooltip() {
      return this.heldTooltip;
   }

   /** Primary-action styling (filled pink). */
   public StyledButton accent() {
      this.accent = true;
      return this;
   }

   @Override
   public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
      boolean hover = this.isHovered() && this.active;
      boolean focus = this.isFocused() && this.active;
      int fill;
      int edge;
      if (!this.active) {
         fill = Theme.CONTROL_OFF;
         edge = Theme.PANEL_EDGE;
      } else if (this.accent) {
         fill = hover ? Theme.ACCENT : Theme.ACCENT_DIM;
         edge = hover ? Theme.TEXT : Theme.ACCENT;
      } else {
         fill = hover ? Theme.CONTROL_HOVER : Theme.CONTROL;
         edge = hover || focus ? Theme.ACCENT_DIM : Theme.CONTROL_EDGE;
      }

      int x1 = this.getX();
      int y1 = this.getY();
      Theme.roundBox(context, x1, y1, x1 + this.getWidth(), y1 + this.getHeight(), 3, edge, fill);

      String label = this.getMessage().getString();
      int color;
      if (!this.active) {
         color = Theme.TEXT_MUTED;
      } else if (this.accent) {
         color = 0xFF2A1420;
      } else if ("ON".equals(label)) {
         color = Theme.GOOD;
      } else if ("OFF".equals(label)) {
         color = Theme.BAD;
      } else {
         color = Theme.TEXT;
      }

      TextRenderer tr = MinecraftClient.getInstance().textRenderer;
      int textY = y1 + (this.getHeight() - 8) / 2;
      context.drawCenteredTextWithShadow(tr, this.getMessage(), x1 + this.getWidth() / 2, textY, color);
   }

   public static final class Builder {
      private final Text message;
      private final PressAction onPress;
      private Tooltip tooltip;
      private int x;
      private int y;
      private int width = 150;
      private int height = 20;
      private NarrationSupplier narration = DEFAULT_NARRATION_SUPPLIER;

      private Builder(Text message, PressAction onPress) {
         this.message = message;
         this.onPress = onPress;
      }

      public Builder position(int x, int y) {
         this.x = x;
         this.y = y;
         return this;
      }

      public Builder width(int width) {
         this.width = width;
         return this;
      }

      public Builder size(int width, int height) {
         this.width = width;
         this.height = height;
         return this;
      }

      public Builder dimensions(int x, int y, int width, int height) {
         return this.position(x, y).size(width, height);
      }

      public Builder tooltip(Tooltip tooltip) {
         this.tooltip = tooltip;
         return this;
      }

      public Builder narrationSupplier(NarrationSupplier narration) {
         this.narration = narration;
         return this;
      }

      public StyledButton build() {
         StyledButton button = new StyledButton(this.x, this.y, this.width, this.height, this.message, this.onPress, this.narration);
         if (this.tooltip != null) {
            button.setTooltip(this.tooltip);
         }

         return button;
      }
   }
}
