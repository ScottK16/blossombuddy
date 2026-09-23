package org.blossomsuite.core.ui.QolSubTabs;

import org.blossomsuite.core.ui.StyledButton;
import org.blossomsuite.core.ui.StyledSlider;

import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.QolConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.ui.HoverLabelWidget;
import org.blossomsuite.core.ui.SuiteSettingsScreen;
import org.blossomsuite.core.ui.SuiteSubTab;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

public class BlockHighlightSubTab implements SuiteSubTab {
   @Override
   public String titleKey() {
      return "suitecore.tab.visuals.block_highlight";
   }

   @Override
   public void build(SuiteSettingsScreen screen, int contentTopOffset) {
      final SuiteConfig cfg = SuiteConfig.INSTANCE;
      final QolConfig q = cfg.QolConfig;
      int x = screen.contentX();
      int w = screen.contentW();
      int y = screen.bodyContentY() - screen.scrollOffset() + contentTopOffset;
      int rowH = 20;
      y = addToggleRow(
         screen,
         cfg,
         x,
         w,
         y,
         rowH,
         "Enabled",
         q.targetBlockOutlineEnabled,
         "When ON, " + SuiteRuntime.profile().displayName() + " recolors Minecraft's target block outline.",
         () -> q.targetBlockOutlineEnabled = !q.targetBlockOutlineEnabled
      );
      if (q.targetBlockOutlineEnabled) {
         screen.addContentWidget(
            new HoverLabelWidget(
               x, y + 6, 160, 12, Text.literal("Color Mode"), Tooltip.of(Text.literal("Solid uses the RGB sliders. Rainbow cycles the outline color."))
            )
         );
         ButtonWidget mode = StyledButton.of(
               Text.literal(q.targetBlockOutlineColorMode == QolConfig.TargetBlockOutlineColorMode.RAINBOW ? "Rainbow" : "Solid"),
               b -> {
                  q.targetBlockOutlineColorMode = q.targetBlockOutlineColorMode == QolConfig.TargetBlockOutlineColorMode.RAINBOW
                     ? QolConfig.TargetBlockOutlineColorMode.SOLID
                     : QolConfig.TargetBlockOutlineColorMode.RAINBOW;
                  cfg.markDirty();
                  ConfigIO.saveIfDirty();
                  screen.rebuildPreserveScroll();
               }
            )
            .dimensions(x + w - 120, y, 120, rowH)
            .build();
         screen.addContentWidget(mode);
         y += 28;
         if (q.targetBlockOutlineColorMode != QolConfig.TargetBlockOutlineColorMode.RAINBOW) {
            y = addColorSlider(screen, cfg, "Red", q.targetBlockOutlineR, x, w, y, rowH, v -> q.targetBlockOutlineR = v);
            y = addColorSlider(screen, cfg, "Green", q.targetBlockOutlineG, x, w, y, rowH, v -> q.targetBlockOutlineG = v);
            y = addColorSlider(screen, cfg, "Blue", q.targetBlockOutlineB, x, w, y, rowH, v -> q.targetBlockOutlineB = v);
         }

         screen.addContentWidget(new HoverLabelWidget(x, y, 120, 12, Text.literal("Alpha"), Tooltip.of(Text.literal("Target block outline opacity."))));
         SliderWidget alpha = new StyledSlider(x, y + 14, w, rowH, Text.empty(), clamp01(q.targetBlockOutlineA)) {
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
               q.targetBlockOutlineA = (float)BlockHighlightSubTab.clamp01(this.value);
               cfg.markDirty();
            }
         };
         screen.addContentWidget(alpha);
      }
   }

   @Override
   public void renderText(SuiteSettingsScreen screen, DrawContext ctx, int mouseX, int mouseY, float delta, int contentTopOffset) {
   }

   @Override
   public int contentHeight(SuiteSettingsScreen screen, int contentTopOffset) {
      QolConfig q = SuiteConfig.INSTANCE.QolConfig;
      int h = contentTopOffset + 28;
      if (q != null && q.targetBlockOutlineEnabled) {
         h += 28;
         if (q.targetBlockOutlineColorMode != QolConfig.TargetBlockOutlineColorMode.RAINBOW) {
            h += 132;
         }

         h += 44;
      }

      return h + 24;
   }

   @Override
   public void removed() {
      ConfigIO.saveIfDirty();
   }

   private static int addToggleRow(
      SuiteSettingsScreen screen, SuiteConfig cfg, int x, int w, int y, int rowH, String label, boolean enabled, String tooltip, Runnable onPress
   ) {
      screen.addContentWidget(new HoverLabelWidget(x, y + 6, 180, 12, Text.literal(label), Tooltip.of(Text.literal(tooltip))));
      ButtonWidget button = StyledButton.of(Text.literal(enabled ? "ON" : "OFF"), b -> {
         onPress.run();
         cfg.markDirty();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(x + w - 80, y, 80, rowH).build();
      screen.addContentWidget(button);
      return y + 28;
   }

   private static int addColorSlider(
      SuiteSettingsScreen screen, SuiteConfig cfg, String label, int current, int x, int w, int y, int rowH, BlockHighlightSubTab.IntSetter setter
   ) {
      screen.addContentWidget(new HoverLabelWidget(x, y, 120, 12, Text.literal(label), Tooltip.of(Text.literal(label + " channel (0 to 255)."))));
      SliderWidget slider = new StyledSlider(x, y + 14, w, rowH, Text.empty(), colorToSlider(current)) {
         {
            this.updateMessage();
         }

         @Override
         protected void updateMessage() {
            this.setMessage(Text.literal(String.valueOf(BlockHighlightSubTab.sliderToColor(this.value))));
         }

         @Override
         protected void applyValue() {
            setter.set(BlockHighlightSubTab.sliderToColor(this.value));
            cfg.markDirty();
         }
      };
      screen.addContentWidget(slider);
      return y + 44;
   }

   private static double colorToSlider(int v) {
      v = Math.max(0, Math.min(255, v));
      return v / 255.0;
   }

   private static int sliderToColor(double value) {
      value = clamp01(value);
      return (int)Math.round(value * 255.0);
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
