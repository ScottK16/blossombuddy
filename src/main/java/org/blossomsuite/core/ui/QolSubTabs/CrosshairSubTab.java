package org.blossomsuite.core.ui.QolSubTabs;

import org.blossomsuite.core.ui.StyledButton;
import org.blossomsuite.core.ui.StyledSlider;

import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.QolConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.ui.CrosshairPreviewWidget;
import org.blossomsuite.core.ui.HoverLabelWidget;
import org.blossomsuite.core.ui.SuiteSettingsScreen;
import org.blossomsuite.core.ui.SuiteSubTab;
import org.blossomsuite.core.util.CrosshairShapeRenderer;
import java.util.Locale;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

public class CrosshairSubTab implements SuiteSubTab {
   @Override
   public String titleKey() {
      return "suitecore.tab.crosshair";
   }

   @Override
   public void build(SuiteSettingsScreen screen, int contentTopOffset) {
      final SuiteConfig cfg = SuiteConfig.INSTANCE;
      final QolConfig q = cfg.QolConfig;
      int x = screen.contentX();
      int w = screen.contentW();
      int y = screen.bodyContentY() - screen.scrollOffset() + contentTopOffset;
      int rowH = 20;
      screen.addContentWidget(new HoverLabelWidget(x, y + 6, 160, 12, Text.literal("Enabled"), Tooltip.of(Text.literal("Enables crosshair tinting."))));
      ButtonWidget enabledBtn = StyledButton.of(Text.literal(q.crosshairTintEnabled ? "ON" : "OFF"), b -> {
         q.crosshairTintEnabled = !q.crosshairTintEnabled;
         cfg.markDirty();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(x + w - 80, y, 80, 20).build();
      screen.addContentWidget(enabledBtn);
      y += 28;
      screen.addContentWidget(
         new HoverLabelWidget(
            x, y + 6, 160, 12, Text.literal("Rainbow"), Tooltip.of(Text.literal("When enabled, RGB sliders are ignored and the crosshair cycles colors."))
         )
      );
      ButtonWidget rainbowBtn = StyledButton.of(Text.literal(q.crosshairRainbow ? "ON" : "OFF"), b -> {
         q.crosshairRainbow = !q.crosshairRainbow;
         cfg.markDirty();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(x + w - 80, y, 80, 20).build();
      screen.addContentWidget(rainbowBtn);
      y += 32;
      screen.addContentWidget(
         new HoverLabelWidget(
            x,
            y + 6,
            160,
            12,
            Text.literal("Shape"),
            Tooltip.of(Text.literal("Vanilla keeps Minecraft's crosshair texture. Other shapes are drawn by " + SuiteRuntime.profile().displayName() + "."))
         )
      );
      ButtonWidget shapeBtn = StyledButton.of(Text.literal(prettyShape(q.crosshairShape)), b -> {
         q.crosshairShape = nextShape(q.crosshairShape);
         clampShapeOptions(q);
         cfg.markDirty();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(x + w - 120, y, 120, 20).build();
      screen.addContentWidget(shapeBtn);
      y += 28;
      int previewH = 72;
      screen.addContentWidget(new CrosshairPreviewWidget(x, y, w, previewH, () -> SuiteConfig.INSTANCE.QolConfig));
      y += previewH + 18;
      if (q.crosshairShape != QolConfig.CrosshairShape.VANILLA) {
         QolConfig.CrosshairShape shape = normalizedShape(q.crosshairShape);
         if (CrosshairShapeRenderer.supportsSize(shape)) {
            y = addIntSlider(screen, cfg, "Size", q.crosshairSize, 1, CrosshairShapeRenderer.maxSize(shape), x, w, y, 20, v -> q.crosshairSize = v);
         }

         if (CrosshairShapeRenderer.supportsGap(shape)) {
            y = addIntSlider(screen, cfg, "Gap", q.crosshairGap, 0, CrosshairShapeRenderer.maxGap(shape), x, w, y, 20, v -> q.crosshairGap = v);
         }

         y = addIntSlider(
            screen,
            cfg,
            "Thickness",
            q.crosshairThickness,
            1,
            CrosshairShapeRenderer.maxThickness(shape),
            x,
            w,
            y,
            20,
            v -> q.crosshairThickness = CrosshairShapeRenderer.normalizeThickness(shape, v)
         );
      }

      if (!q.crosshairRainbow) {
         y = addColorSlider(screen, cfg, "Red", q.crosshairR, x, w, y, 20, v -> q.crosshairR = v);
         y = addColorSlider(screen, cfg, "Green", q.crosshairG, x, w, y, 20, v -> q.crosshairG = v);
         y = addColorSlider(screen, cfg, "Blue", q.crosshairB, x, w, y, 20, v -> q.crosshairB = v);
      }

      screen.addContentWidget(new HoverLabelWidget(x, y, 120, 12, Text.literal("Alpha"), Tooltip.of(Text.literal("Crosshair opacity (0% to 100%)."))));
      SliderWidget alphaSlider = new StyledSlider(x, y + 14, w, 20, Text.empty(), clamp01(q.crosshairA)) {
         {
            this.updateMessage();
         }

         @Override
         protected void updateMessage() {
            int pct = (int)Math.round(this.value * 100.0);
            this.setMessage(Text.literal(pct + "%"));
         }

         @Override
         protected void applyValue() {
            q.crosshairA = (float)CrosshairSubTab.clamp01(this.value);
            cfg.markDirty();
         }
      };
      screen.addContentWidget(alphaSlider);
      y += 44;
      if (q.crosshairRainbow) {
         screen.addContentWidget(
            new HoverLabelWidget(x, y, 160, 12, Text.literal("Rainbow Speed"), Tooltip.of(Text.literal("Rainbow cycle period. Lower = faster.")))
         );
         SliderWidget speedSlider = new StyledSlider(x, y + 14, w, 20, Text.empty(), periodToSlider(q.crosshairRainbowPeriodMs)) {
            {
               this.updateMessage();
            }

            @Override
            protected void updateMessage() {
               int ms = CrosshairSubTab.sliderToPeriod(this.value);
               this.setMessage(Text.literal(ms / 1000.0 + "s"));
            }

            @Override
            protected void applyValue() {
               q.crosshairRainbowPeriodMs = CrosshairSubTab.sliderToPeriod(this.value);
               cfg.markDirty();
            }
         };
         screen.addContentWidget(speedSlider);
         y += 44;
      }
   }

   private static int addIntSlider(
      SuiteSettingsScreen screen, SuiteConfig cfg, String label, int current, int min, int max, int x, int w, int y, int rowH, CrosshairSubTab.IntSetter setter
   ) {
      screen.addContentWidget(new HoverLabelWidget(x, y, 120, 12, Text.literal(label), Tooltip.of(Text.literal(label + " for custom crosshair shapes."))));
      SliderWidget slider = new StyledSlider(x, y + 14, w, rowH, Text.empty(), intToSlider(current, min, max)) {
         {
            this.updateMessage();
         }

         @Override
         protected void updateMessage() {
            this.setMessage(Text.literal(String.valueOf(CrosshairSubTab.sliderToInt(this.value, min, max))));
         }

         @Override
         protected void applyValue() {
            setter.set(CrosshairSubTab.sliderToInt(this.value, min, max));
            cfg.markDirty();
         }
      };
      screen.addContentWidget(slider);
      return y + 44;
   }

   private static int addColorSlider(
      SuiteSettingsScreen screen, SuiteConfig cfg, String label, int current, int x, int w, int y, int rowH, CrosshairSubTab.IntSetter setter
   ) {
      screen.addContentWidget(new HoverLabelWidget(x, y, 120, 12, Text.literal(label), Tooltip.of(Text.literal(label + " channel (0 to 255)."))));
      SliderWidget slider = new StyledSlider(x, y + 14, w, rowH, Text.empty(), colorToSlider(current)) {
         {
            this.updateMessage();
         }

         @Override
         protected void updateMessage() {
            int v = CrosshairSubTab.sliderToColor(this.value);
            this.setMessage(Text.literal(String.valueOf(v)));
         }

         @Override
         protected void applyValue() {
            setter.set(CrosshairSubTab.sliderToColor(this.value));
            cfg.markDirty();
         }
      };
      screen.addContentWidget(slider);
      return y + 44;
   }

   @Override
   public void renderText(SuiteSettingsScreen screen, DrawContext ctx, int mouseX, int mouseY, float delta, int contentTopOffset) {
   }

   @Override
   public int contentHeight(SuiteSettingsScreen screen, int contentTopOffset) {
      QolConfig q = SuiteConfig.INSTANCE.QolConfig;
      int h = contentTopOffset;
      h += 2;
      h += 28;
      h += 32;
      h += 90;
      h += 28;
      if (q != null && q.crosshairShape != QolConfig.CrosshairShape.VANILLA) {
         QolConfig.CrosshairShape shape = normalizedShape(q.crosshairShape);
         if (CrosshairShapeRenderer.supportsSize(shape)) {
            h += 44;
         }

         if (CrosshairShapeRenderer.supportsGap(shape)) {
            h += 44;
         }

         h += 44;
      }

      if (q != null && !q.crosshairRainbow) {
         h += 132;
      }

      h += 44;
      if (q != null && q.crosshairRainbow) {
         h += 44;
      }

      return h + 24;
   }

   @Override
   public void removed() {
      ConfigIO.saveIfDirty();
   }

   private static double colorToSlider(int v) {
      v = Math.max(0, Math.min(255, v));
      return v / 255.0;
   }

   private static int sliderToColor(double value) {
      value = clamp01(value);
      return (int)Math.round(value * 255.0);
   }

   private static double intToSlider(int current, int min, int max) {
      current = Math.max(min, Math.min(max, current));
      return (double)(current - min) / (max - min);
   }

   private static int sliderToInt(double value, int min, int max) {
      value = clamp01(value);
      return (int)Math.round(min + value * (max - min));
   }

   private static QolConfig.CrosshairShape nextShape(QolConfig.CrosshairShape current) {
      QolConfig.CrosshairShape[] all = QolConfig.CrosshairShape.values();
      int idx = current == null ? 0 : current.ordinal();
      return all[(idx + 1) % all.length];
   }

   private static QolConfig.CrosshairShape normalizedShape(QolConfig.CrosshairShape shape) {
      return shape == null ? QolConfig.CrosshairShape.VANILLA : shape;
   }

   private static void clampShapeOptions(QolConfig q) {
      if (q != null) {
         QolConfig.CrosshairShape shape = normalizedShape(q.crosshairShape);
         q.crosshairSize = Math.max(1, Math.min(CrosshairShapeRenderer.maxSize(shape), q.crosshairSize));
         q.crosshairGap = CrosshairShapeRenderer.supportsGap(shape) ? Math.max(0, Math.min(CrosshairShapeRenderer.maxGap(shape), q.crosshairGap)) : 0;
         q.crosshairThickness = CrosshairShapeRenderer.normalizeThickness(shape, q.crosshairThickness);
      }
   }

   private static String prettyShape(QolConfig.CrosshairShape shape) {
      if (shape == null) {
         return "Vanilla";
      }

      String raw = shape.name().toLowerCase(Locale.ROOT).replace('_', ' ');
      return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
   }

   private static double periodToSlider(int ms) {
      int min = 250;
      int max = 30000;
      ms = Math.max(min, Math.min(max, ms));
      return (double)(ms - min) / (max - min);
   }

   private static int sliderToPeriod(double value) {
      value = clamp01(value);
      int min = 250;
      int max = 30000;
      return (int)Math.round(min + value * (max - min));
   }

   private static double clamp01(double v) {
      if (v < 0.0) {
         return 0.0;
      } else {
         return v > 1.0 ? 1.0 : v;
      }
   }

   private interface IntSetter {
      void set(int var1);
   }
}
