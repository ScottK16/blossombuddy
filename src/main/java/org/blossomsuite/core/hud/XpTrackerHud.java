package org.blossomsuite.core.hud;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.blossomsuite.core.config.FeatureConfig;
import org.blossomsuite.core.util.HudStyleUtil;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.jobs.JobXpTracker;

/** Session XP per job: how much, how fast, and how long until the next level. */
public final class XpTrackerHud extends PanelHud {
   public static final XpTrackerHud INSTANCE = new XpTrackerHud();
   private static final int ROW_H = 10;
   private static final int HEADER_H = 14;

   private XpTrackerHud() {
   }

   @Override
   public String id() {
      return "xptracker";
   }

   @Override
   protected FeatureConfig.Panel panel() {
      return FeatureConfig.INSTANCE.xp.panel;
   }

   @Override
   protected boolean shown() {
      return FeatureConfig.INSTANCE.xp.show;
   }

   @Override
   protected int[] defaultTopLeft(int screenW, int screenH, int w, int h) {
      return new int[]{6, Math.max(6, screenH / 2 - h / 2)};
   }

   public void render(DrawContext ctx, MinecraftClient client) {
      FeatureConfig.Xp cfg = FeatureConfig.INSTANCE.xp;
      if (!cfg.show || client.player == null || !SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
         return;
      }

      List<JobXpTracker.Row> rows = JobXpTracker.INSTANCE.rows(System.currentTimeMillis(), cfg.maxRows);
      if (rows.isEmpty()) {
         if (!HudEditState.editMode) {
            return;
         }

         rows = new ArrayList<>(List.of(
            new JobXpTracker.Row("Miner", 145, 2000, 9000, 12_340, 98_200, 4_300_000L),
            new JobXpTracker.Row("Farmer", 210, 500, 3000, 4_100, 31_000, 2_900_000L)
         ));
      }

      TextRenderer tr = client.textRenderer;
      List<String> left = new ArrayList<>();
      List<String> right = new ArrayList<>();
      int leftW = 0;
      int rightW = 0;
      for (JobXpTracker.Row r : rows) {
         String l = r.job() + " " + r.level();
         StringBuilder sb = new StringBuilder("+").append(JobXpTracker.shortNumber(r.sessionXp()));
         if (cfg.showRates) {
            sb.append("  ").append(JobXpTracker.shortNumber(r.perHour())).append("/h");
         }

         if (cfg.showEta) {
            sb.append("  ").append(JobXpTracker.duration(r.etaMs()));
         }

         left.add(l);
         right.add(sb.toString());
         leftW = Math.max(leftW, tr.getWidth(l));
         rightW = Math.max(rightW, tr.getWidth(sb.toString()));
      }

      int baseW = Math.max(90, leftW + rightW + 22);
      int baseH = HEADER_H + rows.size() * ROW_H + 6;
      final List<String> lefts = left;
      final List<String> rights = right;
      final int width = baseW;
      this.draw(ctx, client, baseW, baseH, true, c -> {
         float opacity = cfg.panel.opacity;
         c.fill(0, 0, width, HEADER_H, HudStyleUtil.panelHeader(opacity));
         c.fill(0, HEADER_H, width, HEADER_H + 1, HudStyleUtil.panelDivider(opacity));
         c.drawTextWithShadow(tr, "XP", 6, 4, -1);
         int y = HEADER_H + 4;
         for (int i = 0; i < lefts.size(); i++) {
            c.drawTextWithShadow(tr, lefts.get(i), 6, y, -1);
            String s = rights.get(i);
            c.drawTextWithShadow(tr, s, width - 6 - tr.getWidth(s), y, 0xFF9EE0A8);
            y += ROW_H;
         }
      });
   }
}
