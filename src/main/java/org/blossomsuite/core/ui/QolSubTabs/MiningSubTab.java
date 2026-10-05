package org.blossomsuite.core.ui.QolSubTabs;

import org.blossomsuite.core.ui.StyledButton;
import org.blossomsuite.core.ui.StyledSlider;

import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.QolConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.ui.HoverLabelWidget;
import org.blossomsuite.core.ui.SuiteSettingsScreen;
import org.blossomsuite.core.ui.SuiteSubTab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Direction;
public final class MiningSubTab implements SuiteSubTab {
   @Override
   public String titleKey() {
      return "suitecore.tab.mining";
   }

   @Override
   public void build(SuiteSettingsScreen screen, int contentTopOffset) {
      SuiteConfig cfg = SuiteConfig.INSTANCE;
      int x = screen.contentX();
      int w = screen.contentW();
      int y = screen.bodyContentY() - screen.scrollOffset() + contentTopOffset;
      int rowH = 20;
      screen.addContentWidget(
         new HoverLabelWidget(
            x,
            y + 6,
            260,
            12,
            Component.literal("Resume Mining After Drops"),
            Tooltip.create(Component.literal("Prevents auto-drop/inventory actions from falsely releasing held-click mining."))
         )
      );
      Button resumeBtn = StyledButton.of(Component.literal(cfg.QolConfig.miningResumeAfterDrops ? "ON" : "OFF"), b -> {
         cfg.QolConfig.miningResumeAfterDrops = !cfg.QolConfig.miningResumeAfterDrops;
         cfg.markDirty();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(x + w - 80, y, 80, 20).build();
      screen.addContentWidget(resumeBtn);
      y += 28;
      screen.addContentWidget(
         new HoverLabelWidget(
            x,
            y + 6,
            220,
            12,
            Component.literal("Mining Track Overlay"),
            Tooltip.create(
               Component.literal(
                  "Draws block outlines along your locked mining line.\nGreen = on track. Red = drifted off.\nUse Set Track while facing N/E/S/W to lock direction + axis."
               )
            )
         )
      );
      Button indicatorBtn = StyledButton.of(Component.literal(cfg.QolConfig.miningTrackIndicator ? "ON" : "OFF"), b -> {
         cfg.QolConfig.miningTrackIndicator = !cfg.QolConfig.miningTrackIndicator;
         cfg.markDirty();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(x + w - 80, y, 80, 20).build();
      screen.addContentWidget(indicatorBtn);
      y += 28;
      String trackText = trackSummary(cfg);
      screen.addContentWidget(
         new HoverLabelWidget(
            x,
            y + 6,
            Math.min(w, 360),
            12,
            Component.literal(trackText),
            Tooltip.create(Component.literal("Current locked track. Clear to disable the lock.\nSet Track uses your current facing and current X/Z."))
         )
      );
      int btnW = 110;
      Button setBtn = StyledButton.of(Component.literal("Set Track"), b -> {
         Minecraft mc = Minecraft.getInstance();
         if (mc != null && mc.player != null) {
            Direction d = mc.player.getDirection();
            cfg.QolConfig.miningTrackDir = d.getSerializedName();
            if (d != Direction.EAST && d != Direction.WEST) {
               cfg.QolConfig.miningTrackCoord = mc.player.blockPosition().getX();
            } else {
               cfg.QolConfig.miningTrackCoord = mc.player.blockPosition().getZ();
            }

            cfg.markDirty();
            ConfigIO.saveIfDirty();
            screen.rebuildPreserveScroll();
         }
      }).dimensions(x + w - (btnW * 2 + 8), y, btnW, 20).build();
      setBtn.setTooltip(Tooltip.create(Component.literal("Locks to your current facing direction and current X/Z track coordinate.")));
      screen.addContentWidget(setBtn);
      Button clearBtn = StyledButton.of(Component.literal("Clear"), b -> {
         cfg.QolConfig.miningTrackDir = "";
         cfg.QolConfig.miningTrackCoord = 0;
         cfg.markDirty();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(x + w - btnW, y, btnW, 20).build();
      clearBtn.setTooltip(Tooltip.create(Component.literal("Clears the locked track (indicator can stay ON).")));
      screen.addContentWidget(clearBtn);
      y += 28;
      String rangeLabel = cfg.QolConfig.miningTrackRangeBlocks <= 0 ? "Auto (view distance)" : cfg.QolConfig.miningTrackRangeBlocks + " blocks";
      screen.addContentWidget(
         new HoverLabelWidget(
            x,
            y + 6,
            Math.min(w, 260),
            12,
            Component.literal("Track Range: " + rangeLabel),
            Tooltip.create(Component.literal("How far to render the mining track outlines.\nAuto uses your current view distance."))
         )
      );
      int small = 28;
      int gap = 6;
      Button minusBtn = StyledButton.of(Component.literal("-"), b -> {
         int v = cfg.QolConfig.miningTrackRangeBlocks;
         if (v <= 0) {
            v = 128;
         }

         v = Math.max(16, v - 16);
         cfg.QolConfig.miningTrackRangeBlocks = v;
         cfg.markDirty();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(x + w - (small * 3 + gap * 2), y, small, 20).build();
      minusBtn.setTooltip(Tooltip.create(Component.literal("Decrease range")));
      screen.addContentWidget(minusBtn);
      Button plusBtn = StyledButton.of(Component.literal("+"), b -> {
         int v = cfg.QolConfig.miningTrackRangeBlocks;
         if (v <= 0) {
            v = 128;
         }

         v = Math.min(2048, v + 16);
         cfg.QolConfig.miningTrackRangeBlocks = v;
         cfg.markDirty();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(x + w - (small * 2 + gap), y, small, 20).build();
      plusBtn.setTooltip(Tooltip.create(Component.literal("Increase range")));
      screen.addContentWidget(plusBtn);
      Button autoBtn = StyledButton.of(Component.literal("Auto"), b -> {
         cfg.QolConfig.miningTrackRangeBlocks = 0;
         cfg.markDirty();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(x + w - small, y, small, 20).build();
      autoBtn.setTooltip(Tooltip.create(Component.literal("Use view distance")));
      screen.addContentWidget(autoBtn);
      y += 28;
      int t = cfg.QolConfig.miningTrackLineThickness;
      if (t < 1) {
         t = 1;
      }

      if (t > 4) {
         t = 4;
      }

      int showT = t;
      screen.addContentWidget(
         new HoverLabelWidget(
            x,
            y + 6,
            Math.min(w, 240),
            12,
            Component.literal("Line Thickness: " + showT),
            Tooltip.create(Component.literal("Adjusts how bold the mining track outline appears."))
         )
      );
      Button tMinus = StyledButton.of(Component.literal("-"), b -> {
         int v = cfg.QolConfig.miningTrackLineThickness;
         if (v < 1) {
            v = 1;
         }

         v = Math.max(1, v - 1);
         cfg.QolConfig.miningTrackLineThickness = v;
         cfg.markDirty();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(x + w - (small * 2 + gap), y, small, 20).build();
      tMinus.setTooltip(Tooltip.create(Component.literal("Thinner")));
      screen.addContentWidget(tMinus);
      Button tPlus = StyledButton.of(Component.literal("+"), b -> {
         int v = cfg.QolConfig.miningTrackLineThickness;
         if (v < 1) {
            v = 1;
         }

         v = Math.min(4, v + 1);
         cfg.QolConfig.miningTrackLineThickness = v;
         cfg.markDirty();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(x + w - small, y, small, 20).build();
      tPlus.setTooltip(Tooltip.create(Component.literal("Thicker")));
      screen.addContentWidget(tPlus);
      y += 32;
      y = addColorSection(screen, cfg, "On Track Color", "Color used when you are on the locked mining track.", true, x, w, y, 20);
      y += 12;
      addColorSection(screen, cfg, "Off Track Color", "Color used when you drift off the locked mining track.", false, x, w, y, 20);
   }

   private static int addColorSection(SuiteSettingsScreen screen, SuiteConfig cfg, String label, String tooltip, boolean onTrack, int x, int w, int y, int rowH) {
      QolConfig q = cfg.QolConfig;
      QolConfig.MiningTrackColorMode mode = onTrack ? q.miningTrackOnTrackColorMode : q.miningTrackOffTrackColorMode;
      screen.addContentWidget(
         new HoverLabelWidget(
            x,
            y + 6,
            Math.min(w, 240),
            12,
            Component.literal(label + ": " + colorModeLabel(mode)),
            Tooltip.create(Component.literal(tooltip + "\nSolid uses the RGB sliders. Rainbow cycles colors along the track."))
         )
      );
      Button modeBtn = StyledButton.of(Component.literal("Mode"), b -> {
         QolConfig.MiningTrackColorMode next = nextColorMode(mode);
         if (onTrack) {
            q.miningTrackOnTrackColorMode = next;
         } else {
            q.miningTrackOffTrackColorMode = next;
         }

         cfg.markDirty();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(x + w - 80, y, 80, rowH).build();
      modeBtn.setTooltip(Tooltip.create(Component.literal("Cycle Solid and Rainbow")));
      screen.addContentWidget(modeBtn);
      y += 30;
      if (mode != QolConfig.MiningTrackColorMode.RAINBOW) {
         y = addColorSlider(screen, cfg, "Primary Red", onTrack ? q.miningTrackOnTrackR : q.miningTrackOffTrackR, x, w, y, rowH, v -> {
            if (onTrack) {
               q.miningTrackOnTrackR = v;
            } else {
               q.miningTrackOffTrackR = v;
            }
         });
         y = addColorSlider(screen, cfg, "Primary Green", onTrack ? q.miningTrackOnTrackG : q.miningTrackOffTrackG, x, w, y, rowH, v -> {
            if (onTrack) {
               q.miningTrackOnTrackG = v;
            } else {
               q.miningTrackOffTrackG = v;
            }
         });
         y = addColorSlider(screen, cfg, "Primary Blue", onTrack ? q.miningTrackOnTrackB : q.miningTrackOffTrackB, x, w, y, rowH, v -> {
            if (onTrack) {
               q.miningTrackOnTrackB = v;
            } else {
               q.miningTrackOffTrackB = v;
            }
         });
      }

      return y;
   }

   private static int addColorSlider(
      SuiteSettingsScreen screen, SuiteConfig cfg, String label, int current, int x, int w, int y, int rowH, MiningSubTab.IntSetter setter
   ) {
      screen.addContentWidget(new HoverLabelWidget(x, y, 130, 12, Component.literal(label), Tooltip.create(Component.literal(label + " channel (0 to 255)."))));
      AbstractSliderButton slider = new StyledSlider(x, y + 14, w, rowH, Component.empty(), colorToSlider(current)) {
         {
            this.updateMessage();
         }

         @Override
         protected void updateMessage() {
            this.setMessage(Component.literal(String.valueOf(MiningSubTab.sliderToColor(this.value))));
         }

         @Override
         protected void applyValue() {
            setter.set(MiningSubTab.sliderToColor(this.value));
            cfg.markDirty();
         }
      };
      screen.addContentWidget(slider);
      return y + 44;
   }

   private static QolConfig.MiningTrackColorMode nextColorMode(QolConfig.MiningTrackColorMode mode) {
      return mode == QolConfig.MiningTrackColorMode.SOLID ? QolConfig.MiningTrackColorMode.RAINBOW : QolConfig.MiningTrackColorMode.SOLID;
   }

   private static String colorModeLabel(QolConfig.MiningTrackColorMode mode) {
      return mode == QolConfig.MiningTrackColorMode.RAINBOW ? "Rainbow" : "Solid";
   }

   private static String trackSummary(SuiteConfig cfg) {
      if (cfg != null && cfg.QolConfig != null) {
         String dir = cfg.QolConfig.miningTrackDir == null ? "" : cfg.QolConfig.miningTrackDir.trim();
         if (dir.isBlank()) {
            return "Track: (not set)";
         }

         String d = dir.toLowerCase();
         boolean alongZ = d.equals("north") || d.equals("south");
         String axisLocked = alongZ ? "X" : "Z";
         String label = alongZ ? "N/S" : "E/W";
         return "Track: " + label + " (" + axisLocked + "=" + cfg.QolConfig.miningTrackCoord + ")";
      } else {
         return "Track: (unknown)";
      }
   }

   @Override
   public void renderText(SuiteSettingsScreen screen, GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta, int contentTopOffset) {
   }

   @Override
   public int contentHeight(SuiteSettingsScreen screen, int contentTopOffset) {
      QolConfig q = SuiteConfig.INSTANCE.QolConfig;
      int h = contentTopOffset + 224;
      h += colorSectionHeight(q.miningTrackOnTrackColorMode);
      h += 12;
      h += colorSectionHeight(q.miningTrackOffTrackColorMode);
      return h + 24;
   }

   @Override
   public void removed() {
      ConfigIO.saveIfDirty();
   }

   private static int colorSectionHeight(QolConfig.MiningTrackColorMode mode) {
      int h = 30;
      if (mode != QolConfig.MiningTrackColorMode.RAINBOW) {
         h += 132;
      }

      return h;
   }

   private static double colorToSlider(int v) {
      v = Math.max(0, Math.min(255, v));
      return v / 255.0;
   }

   private static int sliderToColor(double value) {
      if (value < 0.0) {
         value = 0.0;
      }

      if (value > 1.0) {
         value = 1.0;
      }

      return (int)Math.round(value * 255.0);
   }

   private interface IntSetter {
      void set(int var1);
   }
}
