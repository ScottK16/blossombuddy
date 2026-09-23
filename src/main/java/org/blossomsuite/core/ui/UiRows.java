package org.blossomsuite.core.ui;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;

/** Small builders for the settings rows the BlossomBuddy tabs share. Each returns the y of the next row. */
final class UiRows {
   static final int ROW = 24;
   static final int SLIDER_ROW = 40;

   private UiRows() {
   }

   static int toggle(SuiteSettingsScreen screen, int x, int w, int y, String label, boolean on, String tip, Runnable press) {
      screen.addContentWidget(new HoverLabelWidget(x, y + 6, Math.max(60, w - 96), 12, Text.literal(label), Tooltip.of(Text.literal(tip))));
      StyledButton button = StyledButton.of(Text.literal(on ? "ON" : "OFF"), b -> press.run()).dimensions(x + w - 80, y, 80, 20).build();
      button.setTooltip(Tooltip.of(Text.literal(tip)));
      screen.addContentWidget(button);
      return y + ROW;
   }

   static int button(SuiteSettingsScreen screen, int x, int w, int y, String label, String text, String tip, Runnable press) {
      screen.addContentWidget(new HoverLabelWidget(x, y + 6, Math.max(60, w - 136), 12, Text.literal(label), Tooltip.of(Text.literal(tip))));
      StyledButton button = StyledButton.of(Text.literal(text), b -> press.run()).dimensions(x + w - 120, y, 120, 20).build();
      button.setTooltip(Tooltip.of(Text.literal(tip)));
      screen.addContentWidget(button);
      return y + ROW;
   }

   /** A label with a slider under it. {@code value01} is 0..1; {@code format} turns it into the text on the slider. */
   static int slider(SuiteSettingsScreen screen, int x, int w, int y, String label, String tip, double value01, DoubleFunction<String> format, DoubleConsumer apply) {
      screen.addContentWidget(new HoverLabelWidget(x, y + 2, w, 12, Text.literal(label), Tooltip.of(Text.literal(tip))));
      StyledSlider slider = new StyledSlider(x, y + 16, w, 20, Text.empty(), Math.max(0.0, Math.min(1.0, value01))) {
         {
            this.updateMessage();
         }

         @Override
         protected void updateMessage() {
            this.setMessage(Text.literal(format.apply(this.value)));
         }

         @Override
         protected void applyValue() {
            apply.accept(this.value);
         }
      };
      screen.addContentWidget(slider);
      return y + SLIDER_ROW;
   }

   static int note(SuiteSettingsScreen screen, int x, int w, int y, String text) {
      screen.addContentWidget(new LabelWidget(x, y + 2, w, 12, Text.literal(text), Theme.TEXT_MUTED));
      return y + 18;
   }

   /** Two side-by-side buttons on one row. */
   static int pair(SuiteSettingsScreen screen, int x, int w, int y, String leftText, Runnable left, String rightText, Runnable right) {
      int half = (w - 8) / 2;
      screen.addContentWidget(StyledButton.of(Text.literal(leftText), b -> left.run()).dimensions(x, y, half, 20).build());
      screen.addContentWidget(StyledButton.of(Text.literal(rightText), b -> right.run()).dimensions(x + half + 8, y, half, 20).build());
      return y + ROW;
   }
}
