package org.blossomsuite.core.ui;

import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.SuiteServer;
import org.blossomsuite.core.config.JobsConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.jobs.JobsActionBarMode;
import org.blossomsuite.core.jobs.JobsAutoSegmentMode;
import org.blossomsuite.core.jobs.JobsMode;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class JobsTab extends NestedSuiteTab {
   private static final DecimalFormat MONEY_FMT = new DecimalFormat("#,##0.00");
   private TextFieldWidget autoSegmentMoneyField;
   private TextFieldWidget autoSegmentExpField;
   private TextFieldWidget pauseAutoUnpauseMoneyField;

   @Override
   public String titleKey() {
      return "suitecore.tab.jobs";
   }

   @Override
   public boolean isEnabled() {
      return true;
   }

   @Override
   public void setEnabled(boolean enabled) {
   }

   @Override
   protected void initializeSubTabs() {
      this.subTabs.add(new JobsTab.DisplaySubTab());
      this.subTabs.add(new JobsTab.SessionSubTab());
      this.subTabs.add(new JobsTab.PauseSubTab());
      this.subTabs.add(new JobsTab.AutoSegmentSubTab());
      this.subTabs.add(new JobsTab.LifetimeSubTab());
      this.subTabs.add(new JobsTab.OverflowSubTab());
   }

   @Override
   public void buildHeaderControls(SuiteSettingsScreen screen) {
      JobsConfig jobs = SuiteConfig.INSTANCE.JobsConfig;
      int x = screen.contentX();
      int w = screen.contentW();
      int y = screen.headerControlY();
      screen.addHeaderWidget(new HoverLabelWidget(x, y + 6, 120, 12, Text.literal("Capture"), Tooltip.of(Text.literal("Master toggle for Jobs tracking."))));
      ButtonWidget captureButton = StyledButton.of(Text.literal(jobs.capture ? "ON" : "OFF"), b -> {
         jobs.toggleCapture();
         screen.rebuildFromTab();
      }).dimensions(x + w - 80, y, 80, 20).build();
      captureButton.setTooltip(Tooltip.of(Text.literal("When disabled, Jobs data will not be captured.")));
      screen.addHeaderWidget(captureButton);
   }

   @Override
   public void removed() {
      super.removed();
      this.clearTextFields();
   }

   private void clearTextFields() {
      this.autoSegmentMoneyField = null;
      this.autoSegmentExpField = null;
      this.pauseAutoUnpauseMoneyField = null;
   }

   private void addCaptureDisabledMessage(SuiteSettingsScreen screen, int x, int w, int y) {
      screen.addContentWidget(
         new HoverLabelWidget(
            x,
            y,
            Math.min(w, 360),
            24,
            Text.literal("Enable Capture to show the rest of the Jobs settings."),
            Tooltip.of(Text.literal("HUD, session, pause, segment, and lifetime options are hidden while Capture is off."))
         )
      );
   }

   private int disabledHeight(int contentTopOffset) {
      return contentTopOffset + 48;
   }

   private int addToggleRow(SuiteSettingsScreen screen, int x, int w, int y, int rowH, String label, boolean enabled, String tooltip, Runnable onPress) {
      screen.addContentWidget(new HoverLabelWidget(x, y + 6, 220, 12, Text.literal(label), Tooltip.of(Text.literal(tooltip))));
      ButtonWidget button = StyledButton.of(Text.literal(enabled ? "ON" : "OFF"), b -> onPress.run()).dimensions(x + w - 80, y, 80, rowH).build();
      button.setTooltip(Tooltip.of(Text.literal(tooltip)));
      screen.addContentWidget(button);
      return y + 24;
   }

   private int addButtonRow(SuiteSettingsScreen screen, int x, int w, int y, int rowH, String label, String buttonText, String tooltip, Runnable onPress) {
      screen.addContentWidget(new HoverLabelWidget(x, y + 6, 180, 12, Text.literal(label), Tooltip.of(Text.literal(tooltip))));
      ButtonWidget button = StyledButton.of(Text.literal(buttonText), b -> onPress.run()).dimensions(x + w - 120, y, 120, rowH).build();
      button.setTooltip(Tooltip.of(Text.literal(tooltip)));
      screen.addContentWidget(button);
      return y + 24;
   }

   private int addLifetimeRow(SuiteSettingsScreen screen, int tableX, int tableW, int y, int rowH, String label, String value) {
      int labelW = 90;
      screen.addContentWidget(new HoverLabelWidget(tableX, y + 6, labelW, 12, Text.literal(label), Tooltip.of(Text.literal(label + " lifetime earnings"))));
      Text valueText = Text.literal(value != null && !value.isBlank() ? value : "-");
      int textWidth = screen.getTextRenderer().getWidth(valueText);
      screen.addContentWidget(new HoverLabelWidget(tableX + tableW - textWidth, y + 6, textWidth, 12, valueText, null));
      return y + rowH + 2;
   }

   private static JobsMode nextMode(JobsMode current) {
      JobsMode[] all = JobsMode.values();
      return all[(current.ordinal() + 1) % all.length];
   }

   private static String prettyMode(JobsMode mode) {
      String raw = mode.name().toLowerCase(Locale.ROOT).replace('_', ' ');
      String[] parts = raw.split(" ");
      StringBuilder sb = new StringBuilder();

      for (String p : parts) {
         if (!p.isEmpty()) {
            if (sb.length() > 0) {
               sb.append(' ');
            }

            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) {
               sb.append(p.substring(1));
            }
         }
      }

      return sb.toString();
   }

   private static String prettyActionBarMode(JobsActionBarMode mode) {
      if (mode == null) {
         return "Hide";
      }

      return switch (mode) {
         case HIDE -> "Hide";
         case RUNNING_TOTAL -> "Running Amt";
         case SESSION_TOTAL -> "Session Total";
         case SEGMENT_TOTAL -> "Segment Total";
         case ORIGINAL -> "Original";
      };
   }

   private static double secsToSlider(int secs) {
      int min = 300;
      int max = 10800;
      secs = Math.max(min, Math.min(max, secs));
      return (double)(secs - min) / (max - min);
   }

   private static int sliderToSecs(double value) {
      int min = 300;
      int max = 10800;
      value = Math.max(0.0, Math.min(1.0, value));
      int secs = (int)Math.round(min + value * (max - min));
      secs = Math.max(min, Math.min(max, secs));
      return secs / 60 * 60;
   }

   private static double pauseSecsToSlider(int secs) {
      int min = 60;
      int max = 3600;
      secs = Math.max(min, Math.min(max, secs));
      return (double)(secs - min) / (max - min);
   }

   private static int sliderToPauseSecs(double value) {
      int min = 60;
      int max = 3600;
      value = Math.max(0.0, Math.min(1.0, value));
      int secs = (int)Math.round(min + value * (max - min));
      secs = Math.max(min, Math.min(max, secs));
      return secs / 60 * 60;
   }

   private static String prettyAutoSegmentMode(JobsAutoSegmentMode mode) {
      if (mode == null) {
         return "Time";
      }

      return switch (mode) {
         case TIME_ACTIVE -> "Time";
         case SEGMENT_MONEY -> "Money";
         case SEGMENT_EXP -> "XP";
      };
   }

   private static String formatShortAmount(double val) {
      if (val <= 0.0) {
         return "0";
      }

      long v = (long)Math.floor(val);
      return String.valueOf(v);
   }

   private static Double parseShortAmount(String raw) {
      if (raw == null) {
         return 0.0;
      }

      String s = raw.trim().toLowerCase(Locale.ROOT);
      if (s.isEmpty()) {
         return 0.0;
      }

      s = s.replace("$", "");
      s = s.replace(",", "");
      s = s.replace("_", "");
      double mult = 1.0;
      if (s.endsWith("k")) {
         mult = 1000.0;
         s = s.substring(0, s.length() - 1).trim();
      } else if (s.endsWith("m")) {
         mult = 1000000.0;
         s = s.substring(0, s.length() - 1).trim();
      } else if (s.endsWith("b")) {
         mult = 1.0E9;
         s = s.substring(0, s.length() - 1).trim();
      }

      if (s.isEmpty()) {
         return null;
      }

      try {
         double base = Double.parseDouble(s);
         if (base < 0.0) {
            base = 0.0;
         }

         return base * mult;
      } catch (Exception ignored) {
         return null;
      }
   }

   private final class AutoSegmentSubTab implements SuiteSubTab {
      @Override
      public String titleKey() {
         return "suitecore.tab.jobs.auto_segment";
      }

      @Override
      public void build(SuiteSettingsScreen screen, int contentTopOffset) {
         JobsTab.this.pauseAutoUnpauseMoneyField = null;
         final JobsConfig jobs = SuiteConfig.INSTANCE.JobsConfig;
         int x = screen.contentX();
         int w = screen.contentW();
         int y = screen.bodyContentY() - screen.scrollOffset() + contentTopOffset;
         if (!jobs.capture) {
            JobsTab.this.autoSegmentMoneyField = null;
            JobsTab.this.autoSegmentExpField = null;
            JobsTab.this.addCaptureDisabledMessage(screen, x, w, y);
         } else {
            y = JobsTab.this.addToggleRow(
               screen,
               x,
               w,
               y,
               20,
               "Auto Segment",
               jobs.autoSegment,
               "Automatically segments Jobs sessions on a timer, money threshold, or XP threshold.",
               () -> {
                  jobs.toggleAutoSegment();
                  screen.rebuildPreserveScroll();
               }
            );
            if (!jobs.autoSegment) {
               JobsTab.this.autoSegmentMoneyField = null;
               JobsTab.this.autoSegmentExpField = null;
            } else {
               y = JobsTab.this.addButtonRow(
                  screen,
                  x,
                  w,
                  y,
                  20,
                  "Auto Segment By",
                  JobsTab.prettyAutoSegmentMode(jobs.autoSegmentMode),
                  "Cycles the auto-segment trigger:\nTime: segments after active time.\nMoney: segments when segment $ reaches a threshold.\nXP: segments when segment XP reaches a threshold.",
                  () -> {
                     jobs.cycleAutoSegmentMode();
                     screen.rebuildPreserveScroll();
                  }
               );
               JobsAutoSegmentMode mode = jobs.autoSegmentMode == null ? JobsAutoSegmentMode.TIME_ACTIVE : jobs.autoSegmentMode;
               if (mode == JobsAutoSegmentMode.TIME_ACTIVE) {
                  int labelY = y + 2;
                  int sliderY = y + 16;
                  screen.addContentWidget(
                     new HoverLabelWidget(
                        x,
                        labelY,
                        180,
                        12,
                        Text.literal("Segment Interval"),
                        Tooltip.of(Text.literal("How often Jobs should automatically segment. Range: 5 to 180 minutes."))
                     )
                  );
                  SliderWidget autoSegmentSlider = new StyledSlider(x, sliderY, w, 20, Text.empty(), JobsTab.secsToSlider(jobs.autoSegmentInSecs)) {
                     {
                        this.updateMessage();
                        this.setTooltip(Tooltip.of(Text.literal("Current auto-segment interval.")));
                     }

                     @Override
                     protected void updateMessage() {
                        int secs = JobsTab.sliderToSecs(this.value);
                        int mins = secs / 60;
                        this.setMessage(Text.literal(mins + " min"));
                     }

                     @Override
                     protected void applyValue() {
                        jobs.setJobsAutoSegmentInSecs(JobsTab.sliderToSecs(this.value));
                     }
                  };
                  screen.addContentWidget(autoSegmentSlider);
                  JobsTab.this.autoSegmentMoneyField = null;
                  JobsTab.this.autoSegmentExpField = null;
               } else if (mode == JobsAutoSegmentMode.SEGMENT_MONEY) {
                  screen.addContentWidget(
                     new HoverLabelWidget(
                        x,
                        y + 2,
                        220,
                        12,
                        Text.literal("Money Threshold"),
                        Tooltip.of(Text.literal("Segments when segment money reaches this amount (e.g. 100k, 1m)."))
                     )
                  );
                  y += 16;
                  JobsTab.this.autoSegmentMoneyField = new TextFieldWidget(screen.getTextRenderer(), x, y, w, 20, Text.empty());
                  JobsTab.this.autoSegmentMoneyField.setMaxLength(32);
                  JobsTab.this.autoSegmentMoneyField.setText(JobsTab.formatShortAmount(jobs.autoSegmentMoneyThreshold));
                  JobsTab.this.autoSegmentMoneyField.setTooltip(Tooltip.of(Text.literal("Accepts numbers like 100000, 100k, 1m.")));
                  JobsTab.this.autoSegmentMoneyField.setChangedListener(s -> {
                     Double v = JobsTab.parseShortAmount(s);
                     if (v != null) {
                        jobs.setAutoSegmentMoneyThreshold(v);
                     }
                  });
                  screen.addContentWidget(JobsTab.this.autoSegmentMoneyField);
                  JobsTab.this.autoSegmentExpField = null;
               } else if (mode == JobsAutoSegmentMode.SEGMENT_EXP) {
                  screen.addContentWidget(
                     new HoverLabelWidget(
                        x,
                        y + 2,
                        220,
                        12,
                        Text.literal("XP Threshold"),
                        Tooltip.of(Text.literal("Segments when segment XP reaches this amount (e.g. 100k, 1m)."))
                     )
                  );
                  y += 16;
                  JobsTab.this.autoSegmentExpField = new TextFieldWidget(screen.getTextRenderer(), x, y, w, 20, Text.empty());
                  JobsTab.this.autoSegmentExpField.setMaxLength(32);
                  JobsTab.this.autoSegmentExpField.setText(JobsTab.formatShortAmount(jobs.autoSegmentExpThreshold));
                  JobsTab.this.autoSegmentExpField.setTooltip(Tooltip.of(Text.literal("Accepts numbers like 100000, 100k, 1m.")));
                  JobsTab.this.autoSegmentExpField.setChangedListener(s -> {
                     Double v = JobsTab.parseShortAmount(s);
                     if (v != null) {
                        jobs.setAutoSegmentExpThreshold(v);
                     }
                  });
                  screen.addContentWidget(JobsTab.this.autoSegmentExpField);
                  JobsTab.this.autoSegmentMoneyField = null;
               }
            }
         }
      }

      @Override
      public void removed() {
      }

      @Override
      public int contentHeight(SuiteSettingsScreen screen, int contentTopOffset) {
         JobsConfig jobs = SuiteConfig.INSTANCE.JobsConfig;
         if (!jobs.capture) {
            return JobsTab.this.disabledHeight(contentTopOffset);
         }

         int h = contentTopOffset + 8 + 24;
         if (jobs.autoSegment) {
            h += 24;
            h += 44;
         }

         return h + 24;
      }
   }

   private final class DisplaySubTab implements SuiteSubTab {
      @Override
      public String titleKey() {
         return "suitecore.tab.jobs.hud";
      }

      @Override
      public void build(SuiteSettingsScreen screen, int contentTopOffset) {
         JobsTab.this.clearTextFields();
         JobsConfig jobs = SuiteConfig.INSTANCE.JobsConfig;
         int x = screen.contentX();
         int w = screen.contentW();
         int y = screen.bodyContentY() - screen.scrollOffset() + contentTopOffset;
         if (!jobs.capture) {
            JobsTab.this.addCaptureDisabledMessage(screen, x, w, y);
         } else {
            y = JobsTab.this.addToggleRow(screen, x, w, y, 20, "Show HUD", jobs.showHud, "Shows or hides the Jobs HUD overlay.", () -> {
               jobs.toggleShowHud();
               screen.rebuildPreserveScroll();
            });
            y = JobsTab.this.addToggleRow(screen, x, w, y, 20, "Show Stopwatch", jobs.showStopwatch, "Shows elapsed active session time on the HUD.", () -> {
               jobs.toggleShowStopwatch();
               screen.rebuildPreserveScroll();
            });
            y = JobsTab.this.addToggleRow(
               screen, x, w, y, 20, "Show Segment", jobs.showSegmentLines, "Shows segment money/XP and rates on the Jobs HUD.", () -> {
                  jobs.toggleShowSegmentLines();
                  screen.rebuildPreserveScroll();
               }
            );
            y = JobsTab.this.addToggleRow(
               screen, x, w, y, 20, "Show Session", jobs.showSessionLines, "Shows session money/XP and rates on the Jobs HUD.", () -> {
                  jobs.toggleShowSessionLines();
                  screen.rebuildPreserveScroll();
               }
            );
            y = JobsTab.this.addToggleRow(
               screen, x, w, y, 20, "Show In Chat", jobs.showInChat, "Prints each Jobs reward into chat (\"You got: $..., and ... exp\").", () -> {
                  jobs.toggleShowInChat();
                  screen.rebuildPreserveScroll();
               }
            );
            y = JobsTab.this.addButtonRow(
               screen,
               x,
               w,
               y,
               20,
               "ActionBar",
               JobsTab.prettyActionBarMode(jobs.actionBarMode),
               "Controls the jobs actionbar message.\nHide: captures but shows nothing.\nRunning Total: shows the running +$ / +XP total.\nSession Total: shows the current session $ / XP total.\nSegment Total: shows the current segment $ / XP total.\nOriginal: shows the server's original message.",
               () -> {
                  jobs.cycleActionBarMode();
                  screen.rebuildPreserveScroll();
               }
            );
            y = JobsTab.this.addToggleRow(screen, x, w, y, 20, "Show Lifetime", jobs.showLifetime, "Shows lifetime totals on the HUD when available.", () -> {
               jobs.toggleShowLifetime();
               screen.rebuildPreserveScroll();
            });
            y = JobsTab.this.addToggleRow(
               screen,
               x,
               w,
               y,
               20,
               "Pause Crosshair Icon",
               jobs.showPauseIconNearCrosshair,
               "Shows a pause icon near the crosshair while Jobs tracking is paused.",
               () -> {
                  jobs.toggleShowPauseIconNearCrosshair();
                  screen.rebuildPreserveScroll();
               }
            );
            JobsTab.this.addButtonRow(screen, x, w, y, 20, "Current Mode", JobsTab.prettyMode(jobs.mode), "Cycles through available Jobs modes.", () -> {
               jobs.setMode(JobsTab.nextMode(jobs.mode));
               screen.rebuildPreserveScroll();
            });
         }
      }

      @Override
      public void removed() {
      }

      @Override
      public int contentHeight(SuiteSettingsScreen screen, int contentTopOffset) {
         return !SuiteConfig.INSTANCE.JobsConfig.capture ? JobsTab.this.disabledHeight(contentTopOffset) : contentTopOffset + 8 + 216 + 24;
      }
   }

   private final class LifetimeSubTab implements SuiteSubTab {
      @Override
      public String titleKey() {
         return "suitecore.tab.jobs.lifetime";
      }

      @Override
      public void build(SuiteSettingsScreen screen, int contentTopOffset) {
         JobsTab.this.clearTextFields();
         JobsConfig jobs = SuiteConfig.INSTANCE.JobsConfig;
         int x = screen.contentX();
         int w = screen.contentW();
         int y = screen.bodyContentY() - screen.scrollOffset() + contentTopOffset;
         if (!jobs.capture) {
            JobsTab.this.addCaptureDisabledMessage(screen, x, w, y);
         } else {
            int tableW = Math.min(320, w);
            int tableX = x + (w - tableW) / 2;
            List<SuiteServer> servers = SuiteRuntime.profile().servers();
            if (servers.isEmpty()) {
               screen.addContentWidget(
                  new HoverLabelWidget(x, y, Math.min(w, 360), 24, Text.literal("No tracked servers are configured for this build."), null)
               );
            } else {
               for (SuiteServer server : servers) {
                  y = JobsTab.this.addLifetimeRow(
                     screen, tableX, tableW, y, 20, server.displayName(), "$" + JobsTab.MONEY_FMT.format(jobs.lifetimeForServer(server.key()))
                  );
               }
            }
         }
      }

      @Override
      public void removed() {
      }

      @Override
      public int contentHeight(SuiteSettingsScreen screen, int contentTopOffset) {
         if (!SuiteConfig.INSTANCE.JobsConfig.capture) {
            return JobsTab.this.disabledHeight(contentTopOffset);
         }

         int serverRows = Math.max(1, SuiteRuntime.profile().servers().size());
         return contentTopOffset + 8 + serverRows * 22 + 30;
      }
   }

   private final class OverflowSubTab implements SuiteSubTab {
      @Override
      public String titleKey() {
         return "suitecore.tab.jobs.overflow";
      }

      @Override
      public void build(SuiteSettingsScreen screen, int contentTopOffset) {
         JobsTab.this.clearTextFields();
         final JobsConfig jobs = SuiteConfig.INSTANCE.JobsConfig;
         int x = screen.contentX();
         int w = screen.contentW();
         int y = screen.bodyContentY() - screen.scrollOffset() + contentTopOffset;
         if (!jobs.capture) {
            JobsTab.this.addCaptureDisabledMessage(screen, x, w, y);
            return;
         }

         y = JobsTab.this.addToggleRow(
            screen, x, w, y, 20, "Track overflow XP", jobs.overflowEnabled, "Counts XP earned after a job's max level by reading the Jobs boss bar.", () -> {
               jobs.toggleOverflowEnabled();
               screen.rebuildPreserveScroll();
            }
         );
         if (jobs.overflowEnabled) {
            y = JobsTab.this.addButtonRow(
               screen,
               x,
               w,
               y,
               20,
               "Boss bar display",
               jobs.overflowDisplay.name(),
               "XP: show the overflow total. LEVELS: show cosmetic levels past max. OFF: track only, leave the boss bar alone.",
               () -> {
                  jobs.cycleOverflowDisplay();
                  screen.rebuildPreserveScroll();
               }
            );
            y = JobsTab.this.addToggleRow(
               screen, x, w, y, 20, "Level-up message", jobs.overflowLevelUpMessage, "Chat message when your cosmetic overflow level goes up.", () -> {
                  jobs.toggleOverflowLevelUpMessage();
                  screen.rebuildPreserveScroll();
               }
            );
         }

         screen.addContentWidget(
            new HoverLabelWidget(
               x,
               y + 4,
               Math.min(w, 360),
               24,
               Text.literal("Totals: /jobsoverflow  (all, reset, resetjob <job>)"),
               Tooltip.of(Text.literal("Needs the Jobs Reborn boss bar enabled on the server. Max level defaults to 200 (jobs.overflow.maxLevel in the config)."))
            )
         );
      }

      @Override
      public void removed() {
      }

      @Override
      public int contentHeight(SuiteSettingsScreen screen, int contentTopOffset) {
         if (!SuiteConfig.INSTANCE.JobsConfig.capture) {
            return JobsTab.this.disabledHeight(contentTopOffset);
         }

         return contentTopOffset + 8 + (SuiteConfig.INSTANCE.JobsConfig.overflowEnabled ? 3 : 1) * 24 + 36;
      }
   }

   private final class PauseSubTab implements SuiteSubTab {
      @Override
      public String titleKey() {
         return "suitecore.tab.jobs.pause";
      }

      @Override
      public void build(SuiteSettingsScreen screen, int contentTopOffset) {
         JobsTab.this.autoSegmentMoneyField = null;
         JobsTab.this.autoSegmentExpField = null;
         final JobsConfig jobs = SuiteConfig.INSTANCE.JobsConfig;
         int x = screen.contentX();
         int w = screen.contentW();
         int y = screen.bodyContentY() - screen.scrollOffset() + contentTopOffset;
         if (!jobs.capture) {
            JobsTab.this.pauseAutoUnpauseMoneyField = null;
            JobsTab.this.addCaptureDisabledMessage(screen, x, w, y);
         } else {
            y = JobsTab.this.addToggleRow(
               screen, x, w, y, 20, "Reminder", jobs.pauseReminder, "Sends a pause reminder after the reminder timer is reached.", () -> {
                  jobs.togglePauseReminder();
                  screen.rebuildPreserveScroll();
               }
            );
            if (jobs.pauseReminder) {
               int labelY = y + 2;
               int sliderY = y + 16;
               screen.addContentWidget(
                  new HoverLabelWidget(
                     x,
                     labelY,
                     220,
                     12,
                     Text.literal("Reminder Timer"),
                     Tooltip.of(Text.literal("How long Jobs can stay paused before sending another reminder. Range: 1 to 60 minutes."))
                  )
               );
               SliderWidget pauseThresholdSlider = new StyledSlider(x, sliderY, w, 20, Text.empty(), JobsTab.pauseSecsToSlider(jobs.pauseReminderThresholdSecs)) {
                  {
                     this.updateMessage();
                     this.setTooltip(Tooltip.of(Text.literal("Current pause reminder timer.")));
                  }

                  @Override
                  protected void updateMessage() {
                     int secs = JobsTab.sliderToPauseSecs(this.value);
                     int mins = Math.max(1, secs / 60);
                     this.setMessage(Text.literal(mins + " min"));
                  }

                  @Override
                  protected void applyValue() {
                     jobs.setPauseReminderThresholdSecs(JobsTab.sliderToPauseSecs(this.value));
                  }
               };
               screen.addContentWidget(pauseThresholdSlider);
               y = sliderY + 28;
            }

            y = JobsTab.this.addToggleRow(
               screen,
               x,
               w,
               y,
               20,
               "Auto Unpause",
               jobs.pauseAutoUnpause,
               "Automatically resumes Jobs tracking after enough money is earned while paused.",
               () -> {
                  jobs.togglePauseAutoUnpause();
                  screen.rebuildPreserveScroll();
               }
            );
            if (jobs.pauseAutoUnpause) {
               screen.addContentWidget(
                  new HoverLabelWidget(
                     x,
                     y + 2,
                     240,
                     12,
                     Text.literal("Auto Unpause $"),
                     Tooltip.of(Text.literal("Auto-resumes after this much money is earned while paused. Set 0 to disable auto-unpause."))
                  )
               );
               y += 16;
               JobsTab.this.pauseAutoUnpauseMoneyField = new TextFieldWidget(screen.getTextRenderer(), x, y, w, 20, Text.empty());
               JobsTab.this.pauseAutoUnpauseMoneyField.setMaxLength(32);
               JobsTab.this.pauseAutoUnpauseMoneyField.setText(JobsTab.formatShortAmount(jobs.pauseAutoUnpauseMoneyThreshold));
               JobsTab.this.pauseAutoUnpauseMoneyField
                  .setTooltip(Tooltip.of(Text.literal("Accepts numbers like 100000, 100k, 1m. Set 0 to disable auto-unpause.")));
               JobsTab.this.pauseAutoUnpauseMoneyField.setChangedListener(s -> {
                  Double v = JobsTab.parseShortAmount(s);
                  if (v != null) {
                     jobs.setPauseAutoUnpauseMoneyThreshold(v);
                  }
               });
               screen.addContentWidget(JobsTab.this.pauseAutoUnpauseMoneyField);
            } else {
               JobsTab.this.pauseAutoUnpauseMoneyField = null;
            }
         }
      }

      @Override
      public void removed() {
      }

      @Override
      public int contentHeight(SuiteSettingsScreen screen, int contentTopOffset) {
         JobsConfig jobs = SuiteConfig.INSTANCE.JobsConfig;
         if (!jobs.capture) {
            return JobsTab.this.disabledHeight(contentTopOffset);
         }

         int h = contentTopOffset + 8 + 24;
         if (jobs.pauseReminder) {
            h += 44;
         }

         h += 24;
         if (jobs.pauseAutoUnpause) {
            h += 44;
         }

         return h + 24;
      }
   }

   private final class SessionSubTab implements SuiteSubTab {
      @Override
      public String titleKey() {
         return "suitecore.tab.jobs.session";
      }

      @Override
      public void build(SuiteSettingsScreen screen, int contentTopOffset) {
         JobsTab.this.clearTextFields();
         JobsConfig jobs = SuiteConfig.INSTANCE.JobsConfig;
         int x = screen.contentX();
         int w = screen.contentW();
         int y = screen.bodyContentY() - screen.scrollOffset() + contentTopOffset;
         if (!jobs.capture) {
            JobsTab.this.addCaptureDisabledMessage(screen, x, w, y);
         } else {
            JobsTab.this.addToggleRow(
               screen, x, w, y, 20, "Session Rollover", jobs.sessionRollover, "Carries the previous session into the next one when supported.", () -> {
                  jobs.toggleSessionRollover();
                  screen.rebuildPreserveScroll();
               }
            );
         }
      }

      @Override
      public void removed() {
      }

      @Override
      public int contentHeight(SuiteSettingsScreen screen, int contentTopOffset) {
         return !SuiteConfig.INSTANCE.JobsConfig.capture ? JobsTab.this.disabledHeight(contentTopOffset) : contentTopOffset + 8 + 24 + 24;
      }
   }
}
