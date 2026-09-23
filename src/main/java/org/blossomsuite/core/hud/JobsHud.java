package org.blossomsuite.core.hud;

import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.jobs.JobsMode;
import org.blossomsuite.core.jobs.JobsRuntime;
import org.blossomsuite.core.jobs.JobsTracker;
import org.blossomsuite.core.util.HudStyleUtil;
import org.blossomsuite.core.util.RateColors;
import org.blossomsuite.core.util.TextUtil;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.joml.Matrix3x2fStack;

public final class JobsHud {
   private static final int BASE_W = 170;
   private static final int FALLBACK_FONT_HEIGHT = 9;
   public static final DraggableHud DRAGGABLE = new DraggableHud() {
      @Override
      public String id() {
         return "jobs";
      }

      @Override
      public int x() {
         return JobsHud.lastX;
      }

      @Override
      public int y() {
         return JobsHud.lastY;
      }

      @Override
      public int w() {
         return JobsHud.lastW;
      }

      @Override
      public int h() {
         return JobsHud.lastH;
      }

      @Override
      public float posX() {
         return SuiteConfig.INSTANCE.JobsConfig.positionX;
      }

      @Override
      public float posY() {
         return SuiteConfig.INSTANCE.JobsConfig.positionY;
      }

      @Override
      public void setPos(float nx, float ny) {
         SuiteConfig.INSTANCE.JobsConfig.setPosition(nx, ny);
         ConfigIO.saveIfDirty();
      }

      @Override
      public boolean enabled() {
         return SuiteConfig.INSTANCE.JobsConfig.showHud;
      }

      @Override
      public boolean resizable() {
         return true;
      }

      @Override
      public float scale() {
         return SuiteConfig.INSTANCE.JobsConfig.scale;
      }

      @Override
      public void setScale(float s) {
         SuiteConfig.INSTANCE.JobsConfig.setScale(s);
         ConfigIO.saveIfDirty();
      }

      @Override
      public int baseW() {
         return JobsHud.lastBaseW > 0 ? JobsHud.lastBaseW : 170;
      }

      @Override
      public int baseH() {
         return JobsHud.lastBaseH > 0 ? JobsHud.lastBaseH : JobsHud.computeBaseH(9);
      }

      @Override
      public float backgroundOpacity() {
         return SuiteConfig.INSTANCE.JobsConfig.backgroundOpacity;
      }

      @Override
      public void setBackgroundOpacity(float opacity) {
         SuiteConfig.INSTANCE.JobsConfig.backgroundOpacity = Math.max(0.0F, Math.min(1.0F, opacity));
         ConfigIO.saveIfDirty();
      }
   };
   public static boolean dragging = false;
   private static JobsTracker tracker;
   public static int lastX;
   public static int lastY;
   public static int lastW;
   public static int lastH;
   private static int lastBaseW = 170;
   private static int lastBaseH = computeBaseH(9);

   private JobsHud() {
   }

   private static Text headerText() {
      return TextUtil.gradient(SuiteRuntime.profile().displayName().toUpperCase(Locale.ROOT), -11672879, -6591489, true);
   }

   public static void init(JobsTracker jobsTracker) {
      tracker = jobsTracker;
   }

   public static void render(DrawContext ctx, MinecraftClient client) {
      if (SuiteConfig.INSTANCE.JobsConfig.showHud) {
         if (tracker != null && client.player != null) {
            JobsTracker.LiveStats s = tracker.getLiveStats();
            renderPauseCrosshairIcon(ctx, client, s);
            String segmentTag = "Seg: ";
            String sessionTag = "Sess: ";
            boolean showSegment = SuiteConfig.INSTANCE.JobsConfig.showSegmentLines;
            boolean showSession = SuiteConfig.INSTANCE.JobsConfig.showSessionLines;
            boolean showAnyStats = showSegment || showSession;
            int screenH = client.getWindow().getScaledHeight();
            int screenW = client.getWindow().getScaledWidth();
            int lineH = 9;
            int lineGap = 4;
            int lineOffset = lineH + lineGap;
            int titleH = 14;
            int timerH = SuiteConfig.INSTANCE.JobsConfig.showStopwatch && showAnyStats ? lineH + 6 : 0;
            int lifetimeH = SuiteConfig.INSTANCE.JobsConfig.showLifetime ? lineH + 6 : 0;
            int baseH = computeBaseH(lineH);
            int baseW = 170;
            lastBaseW = baseW;
            lastBaseH = baseH;
            float scale = HudScaleUtil.scaleFor(SuiteConfig.INSTANCE.JobsConfig.scale, 0.1F, 2.0F, baseW, baseH, screenW, screenH);
            int w = Math.round(baseW * scale);
            int h = Math.round(baseH * scale);
            int defaultX = screenW - w - 6;
            int defaultY = screenH / 2 + 80;
            int x;
            int y;
            if (!(SuiteConfig.INSTANCE.JobsConfig.positionX < 0.0F) && !(SuiteConfig.INSTANCE.JobsConfig.positionY < 0.0F)) {
               int maxX = Math.max(0, screenW - w);
               int maxY = Math.max(0, screenH - h);
               x = Math.round(SuiteConfig.INSTANCE.JobsConfig.positionX * maxX);
               y = Math.round(SuiteConfig.INSTANCE.JobsConfig.positionY * maxY);
            } else {
               x = defaultX;
               y = defaultY;
            }

            lastX = x;
            lastY = y;
            lastW = w;
            lastH = h;
            Matrix3x2fStack matrices = ctx.getMatrices();
            matrices.pushMatrix();
            matrices.translate(x, y);
            matrices.scale(scale, scale);

            try {
               int panelX1 = 2;
               int panelY1 = 2;
               int panelX2 = baseW - 2;
               int panelY2 = baseH - 2;
               float bg = SuiteConfig.INSTANCE.JobsConfig.backgroundOpacity;
               ctx.fill(panelX1, panelY1, panelX2, panelY2, HudStyleUtil.panelBg(bg));
               ctx.fill(panelX1, panelY1, panelX2, panelY1 + titleH, HudStyleUtil.panelHeader(bg));
               ctx.fill(panelX1 + 4, panelY1 + titleH, panelX2 - 4, panelY1 + titleH + 1, HudStyleUtil.panelDivider(bg));
               Text headerText = headerText();
               int headerW = client.textRenderer.getWidth(headerText);
               int headerX = (baseW - headerW) / 2;
               int headerY = 6;
               ctx.drawTextWithShadow(client.textRenderer, headerText, headerX, headerY, -1);
               if (SuiteConfig.INSTANCE.JobsConfig.showLifetime) {
                  int lifeY1 = panelY1 + titleH + 1;
                  int lifeY2 = lifeY1 + lifetimeH;
                  ctx.fill(panelX1, lifeY1, panelX2, lifeY2, HudStyleUtil.panelSection(bg));
                  ctx.fill(panelX1 + 4, lifeY2, panelX2 - 4, lifeY2 + 1, HudStyleUtil.panelDivider(bg));
                  double lifeMoney = SuiteConfig.INSTANCE.JobsConfig.lifetimeForServer(JobsRuntime.currentRealm());
                  String left = "LIFETIME";
                  String right = "$" + TextUtil.fmtMoney(lifeMoney);
                  int textY = lifeY1 + (lifetimeH - 9) / 2;
                  int leftX2 = panelX1 + 6;
                  ctx.drawTextWithShadow(client.textRenderer, left, leftX2, textY, -4208683);
                  int rightW = client.textRenderer.getWidth(right);
                  int rightX2 = panelX2 - 6 - rightW;
                  ctx.drawTextWithShadow(client.textRenderer, right, rightX2, textY, -8585317);
               }

               int leftX = 6;
               int padTop = 4;
               int rowY = panelY1 + titleH + lifetimeH + timerH + padTop + (timerH > 0 ? 1 : 0) + (SuiteConfig.INSTANCE.JobsConfig.showLifetime ? 1 : 0);
               int rightPad = 6;
               if (timerH > 0) {
                  int timerY1 = panelY1 + titleH + lifetimeH + 1;
                  int timerY2 = timerY1 + timerH;
                  ctx.fill(panelX1, timerY1, panelX2, timerY2, HudStyleUtil.panelSection(bg));
                  ctx.fill(panelX1 + 4, timerY2, panelX2 - 4, timerY2 + 1, HudStyleUtil.panelDivider(bg));
                  String segText = segmentTag + TextUtil.fmtStopwatch(s.segmentActiveMs());
                  String sessText = sessionTag + TextUtil.fmtStopwatch(s.sessionActiveMs());
                  int textY = timerY1 + (timerH - 9) / 2;
                  if (showSegment) {
                     int segX = panelX1 + 6;
                     ctx.drawTextWithShadow(client.textRenderer, segText, segX, textY, -4208683);
                  }

                  if (showSession) {
                     int sessW = client.textRenderer.getWidth(sessText);
                     int sessX = showSegment ? panelX2 - 6 - sessW : panelX1 + 6;
                     ctx.drawTextWithShadow(client.textRenderer, sessText, sessX, textY, -4208683);
                  }
               }

               if (SuiteConfig.INSTANCE.JobsConfig.mode == JobsMode.MONEY) {
                  if (showSegment) {
                     drawSegmentLine(
                        ctx,
                        client,
                        segmentTag + " $" + TextUtil.fmtMoney(s.segmentMoney()),
                        TextUtil.fmtRate(s.segmentMoneyPerHr()),
                        leftX,
                        rowY,
                        0,
                        baseW,
                        rightPad,
                        s.segmentMoneyPerHr(),
                        s.captureEnabled(),
                        s.paused()
                     );
                     rowY += lineOffset;
                  }

                  if (showSession) {
                     drawSessionLine(
                        ctx,
                        client,
                        sessionTag + " $" + TextUtil.fmtMoney(s.sessionMoney()),
                        TextUtil.fmtRate(s.sessionMoneyPerHr()),
                        leftX,
                        rowY,
                        0,
                        baseW,
                        rightPad,
                        s.sessionMoneyPerHr(),
                        s.captureEnabled(),
                        s.paused()
                     );
                  }
               } else if (SuiteConfig.INSTANCE.JobsConfig.mode == JobsMode.XP) {
                  if (showSegment) {
                     drawSegmentLine(
                        ctx, client, segmentTag + TextUtil.fmtExp(s.segmentExp()), TextUtil.fmtRate(s.segmentExpPerHr()), leftX, rowY, 0, baseW, rightPad
                     );
                     rowY += lineOffset;
                  }

                  if (showSession) {
                     drawSessionLine(
                        ctx, client, sessionTag + TextUtil.fmtExp(s.sessionExp()), TextUtil.fmtRate(s.sessionExpPerHr()), leftX, rowY, 0, baseW, rightPad
                     );
                  }
               } else if (SuiteConfig.INSTANCE.JobsConfig.mode == JobsMode.BOTH) {
                  if (showSegment) {
                     drawSegmentLine(
                        ctx,
                        client,
                        segmentTag + " $" + TextUtil.fmtMoney(s.segmentMoney()),
                        TextUtil.fmtRate(s.segmentMoneyPerHr()),
                        leftX,
                        rowY,
                        0,
                        baseW,
                        rightPad,
                        s.segmentMoneyPerHr(),
                        s.captureEnabled(),
                        s.paused()
                     );
                     rowY += lineOffset;
                     drawSegmentLine(ctx, client, TextUtil.fmtExp(s.segmentExp()), TextUtil.fmtRate(s.segmentExpPerHr()), leftX, rowY, 0, baseW, rightPad);
                     rowY += lineOffset;
                  }

                  if (showSession) {
                     drawSessionLine(
                        ctx,
                        client,
                        sessionTag + " $" + TextUtil.fmtMoney(s.sessionMoney()),
                        TextUtil.fmtRate(s.sessionMoneyPerHr()),
                        leftX,
                        rowY,
                        0,
                        baseW,
                        rightPad,
                        s.sessionMoneyPerHr(),
                        s.captureEnabled(),
                        s.paused()
                     );
                     rowY += lineOffset;
                     drawSessionLine(ctx, client, TextUtil.fmtExp(s.sessionExp()), TextUtil.fmtRate(s.sessionExpPerHr()), leftX, rowY, 0, baseW, rightPad);
                  }
               }

               if (HudEditState.editMode) {
                  ctx.fill(0, 0, baseW, 1, -1996488705);
                  ctx.fill(0, baseH - 1, baseW, baseH, -1996488705);
                  ctx.fill(0, 0, 1, baseH, -1996488705);
                  ctx.fill(baseW - 1, 0, baseW, baseH, -1996488705);
               }
            } finally {
               matrices.popMatrix();
            }
         }
      }
   }

   private static int computeBaseH(int lineH) {
      boolean showSegment = SuiteConfig.INSTANCE.JobsConfig.showSegmentLines;
      boolean showSession = SuiteConfig.INSTANCE.JobsConfig.showSessionLines;
      boolean showAnyStats = showSegment || showSession;
      int titleH = 14;
      int timerH = SuiteConfig.INSTANCE.JobsConfig.showStopwatch && showAnyStats ? lineH + 6 : 0;
      int lifetimeH = SuiteConfig.INSTANCE.JobsConfig.showLifetime ? lineH + 6 : 0;
      int padTop = 4;
      int padBot = 4;
      int lineGap = 4;
      int rowsPerGroup = SuiteConfig.INSTANCE.JobsConfig.mode == JobsMode.BOTH ? 2 : 1;
      int rows = (showSegment ? rowsPerGroup : 0) + (showSession ? rowsPerGroup : 0);
      return 2 + titleH + lifetimeH + timerH + padTop + rows * lineH + Math.max(0, rows - 1) * lineGap + padBot + 2;
   }

   private static void renderPauseCrosshairIcon(DrawContext ctx, MinecraftClient client, JobsTracker.LiveStats stats) {
      if (SuiteConfig.INSTANCE.JobsConfig.showPauseIconNearCrosshair) {
         if (stats != null && stats.paused()) {
            if (client != null && client.getWindow() != null) {
               int screenW = client.getWindow().getScaledWidth();
               int screenH = client.getWindow().getScaledHeight();
               String icon = "||";
               int iconW = client.textRenderer.getWidth(icon);
               int x = screenW / 2 - iconW / 2;
               int y = screenH / 2 + 10;
               ctx.drawTextWithShadow(client.textRenderer, icon, x, y, -11410);
            }
         }
      }
   }

   private static void drawSegmentLine(
      DrawContext ctx,
      MinecraftClient client,
      String leftText,
      String rateText,
      int leftX,
      int rowY,
      int x,
      int w,
      int rightPad,
      double rateValue,
      boolean captureEnabled,
      boolean paused
   ) {
      ctx.drawTextWithShadow(client.textRenderer, leftText, leftX, rowY, -1);
      int rateW = client.textRenderer.getWidth(rateText);
      int rateX = x + w - rightPad - rateW;
      int segmentColor = RateColors.rateColor(captureEnabled, paused, rateValue);
      ctx.drawTextWithShadow(client.textRenderer, rateText, rateX, rowY, segmentColor);
   }

   private static void drawSegmentLine(
      DrawContext ctx, MinecraftClient client, String leftText, String rateText, int leftX, int rowY, int x, int w, int rightPad
   ) {
      ctx.drawTextWithShadow(client.textRenderer, leftText, leftX, rowY, -8519782);
      int rateW = client.textRenderer.getWidth(rateText);
      int rateX = x + w - rightPad - rateW;
      ctx.drawTextWithShadow(client.textRenderer, rateText, rateX, rowY, -8519782);
   }

   private static void drawSessionLine(
      DrawContext ctx,
      MinecraftClient client,
      String leftText,
      String rateText,
      int leftX,
      int rowY,
      int x,
      int w,
      int rightPad,
      double rateValue,
      boolean captureEnabled,
      boolean paused
   ) {
      ctx.drawTextWithShadow(client.textRenderer, leftText, leftX, rowY, -4208683);
      int rateW = client.textRenderer.getWidth(rateText);
      int rateX = x + w - rightPad - rateW;
      int segmentColor = RateColors.rateColor(captureEnabled, paused, rateValue);
      ctx.drawTextWithShadow(client.textRenderer, rateText, rateX, rowY, segmentColor);
   }

   private static void drawSessionLine(
      DrawContext ctx, MinecraftClient client, String leftText, String rateText, int leftX, int rowY, int x, int w, int rightPad
   ) {
      ctx.drawTextWithShadow(client.textRenderer, leftText, leftX, rowY, -8519782);
      int rateW = client.textRenderer.getWidth(rateText);
      int rateX = x + w - rightPad - rateW;
      ctx.drawTextWithShadow(client.textRenderer, rateText, rateX, rowY, -8519782);
   }
}
