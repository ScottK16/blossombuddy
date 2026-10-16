package org.blossomsuite.core.ui;

import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.blossomsuite.core.board.BoardFormat;
import org.blossomsuite.core.session.SessionRecord;
import org.blossomsuite.core.session.SessionStats;
import org.blossomsuite.core.session.SessionTracker;

/** {@code /buddy session}: this session, today, this week and so on, from what is saved on your computer. */
public final class SessionScreen extends Screen {
   private static final int PANEL_MAX_W = 360;
   private static final int ROW_H = 14;
   private static final long DAY_MS = 86_400_000L;

   private List<SessionRecord> history = List.of();
   private final ZoneId zone = ZoneId.systemDefault();

   public SessionScreen() {
      super(Component.literal("Your sessions"));
   }

   @Override
   protected void init() {
      this.history = SessionTracker.INSTANCE.history();
      int footY = this.height - 26;
      this.addRenderableWidget(StyledButton.of(Component.literal("Clear history"), b -> {
         SessionTracker.INSTANCE.clearHistory();
         this.history = List.of();
      }).dimensions(this.width / 2 - 154, footY, 150, 20).build());
      this.addRenderableWidget(StyledButton.of(Component.literal("Done"), b -> this.onClose()).dimensions(this.width / 2 + 4, footY, 150, 20).build());
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   private int panelW() {
      return Math.min(PANEL_MAX_W, this.width - 24);
   }

   private static String money(double v) {
      return "$" + BoardFormat.xp(v);
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
      super.extractRenderState(ctx, mouseX, mouseY, delta);
      Font tr = this.font;
      ctx.centeredText(tr, Component.literal("Your sessions").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), this.width / 2, 10, -1);

      long now = System.currentTimeMillis();
      SessionRecord live = SessionTracker.INSTANCE.live();
      SessionStats.Totals liveTotals = live == null ? SessionStats.Totals.NONE : SessionStats.of(live);

      int w = this.panelW();
      int x = (this.width - w) / 2;
      int top = 28;
      int tableH = 16 + ROW_H * 5 + 6;
      ctx.fill(x, top, x + w, top + tableH, 0x66000000);

      int labelW = 84;
      int colW = (w - labelW - 6) / 5;
      String[] heads = {"Online", "Earned", "Per hour", "Overflow", "Dungeons"};
      for (int i = 0; i < heads.length; i++) {
         ctx.text(tr, Component.literal(heads[i]).withStyle(ChatFormatting.GRAY), x + labelW + i * colW, top + 4, -1);
      }

      long dayStart = SessionStats.startOfDay(now, this.zone);
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[]{"This session", live == null ? null : liveTotals});
      rows.add(new Object[]{"Today", plus(SessionStats.since(this.history, dayStart), liveTotals)});
      rows.add(new Object[]{"Last 7 days", plus(SessionStats.since(this.history, dayStart - 6 * DAY_MS), liveTotals)});
      rows.add(new Object[]{"Last 30 days", plus(SessionStats.since(this.history, dayStart - 29 * DAY_MS), liveTotals)});
      rows.add(new Object[]{"All saved", plus(SessionStats.since(this.history, 0L), liveTotals)});
      int y = top + 16;
      for (Object[] row : rows) {
         ctx.text(tr, Component.literal((String)row[0]).withStyle(ChatFormatting.WHITE), x + 6, y, -1);
         SessionStats.Totals t = (SessionStats.Totals)row[1];
         if (t == null) {
            ctx.text(tr, Component.literal("not on a session").withStyle(ChatFormatting.DARK_GRAY), x + labelW, y, -1);
         } else {
            String[] cells = {
               SessionStats.duration(t.timeMs()),
               money(t.money()),
               money(t.moneyPerHour()),
               BoardFormat.xp(t.overflowXp()),
               t.dungeonRuns() == 0 ? "-" : t.dungeonDone() + "/" + t.dungeonRuns()
            };
            ChatFormatting[] colors = {ChatFormatting.AQUA, ChatFormatting.GREEN, ChatFormatting.GREEN, ChatFormatting.LIGHT_PURPLE, ChatFormatting.YELLOW};
            for (int i = 0; i < cells.length; i++) {
               ctx.text(tr, Component.literal(cells[i]).withStyle(colors[i]), x + labelW + i * colW, y, -1);
            }
         }

         y += ROW_H;
      }

      // earnings over the last seven days
      int chartTop = top + tableH + 10;
      int chartH = Math.max(40, this.height - chartTop - 74);
      ctx.fill(x, chartTop, x + w, chartTop + chartH + 22, 0x66000000);
      ctx.text(tr, Component.literal("Earned per day").withStyle(ChatFormatting.GRAY), x + 6, chartTop + 4, -1);
      List<SessionStats.Day> days = SessionStats.lastDays(this.history, now, this.zone, 7);
      double most = 1.0;
      for (SessionStats.Day d : days) {
         most = Math.max(most, d.money());
      }

      int barArea = chartH - 22;
      int slot = (w - 12) / days.size();
      for (int i = 0; i < days.size(); i++) {
         SessionStats.Day d = days.get(i);
         double money = d.money() + (i == days.size() - 1 ? liveTotals.money() : 0.0);
         most = Math.max(most, money);
         int barH = money <= 0.0 ? 1 : Math.max(2, (int)Math.round(barArea * money / most));
         int bx = x + 6 + i * slot + 4;
         int by = chartTop + 16 + (barArea - barH);
         ctx.fill(bx, by, bx + slot - 8, chartTop + 16 + barArea, i == days.size() - 1 ? Theme.ACCENT : Theme.ACCENT_DIM);
         if (money > 0.0) {
            String label = money(money);
            ctx.text(tr, Component.literal(label).withStyle(ChatFormatting.GRAY), bx + (slot - 8 - tr.width(label)) / 2, Math.max(chartTop + 14, by - 10), -1);
         }

         String day = d.date().getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
         ctx.text(tr, Component.literal(day).withStyle(ChatFormatting.DARK_GRAY), bx + (slot - 8 - tr.width(day)) / 2, chartTop + 18 + barArea, -1);
      }

      ctx.centeredText(tr, Component.literal(this.realmLine(live)).withStyle(ChatFormatting.GRAY), this.width / 2, this.height - 54, -1);
      ctx.centeredText(tr, Component.literal("Saved on your computer only. Nothing is sent anywhere.").withStyle(ChatFormatting.DARK_GRAY), this.width / 2, this.height - 42, -1);
   }

   private static SessionStats.Totals plus(SessionStats.Totals saved, SessionStats.Totals live) {
      return SessionStats.plus(saved, live);
   }

   private String realmLine(SessionRecord live) {
      if (live == null || live.realmMs().isEmpty()) {
         return "Join a realm to start a session.";
      }

      List<String> parts = new ArrayList<>();
      for (Map.Entry<String, Long> e : live.realmMs().entrySet()) {
         parts.add(SessionStats.realmLabel(e.getKey()) + " " + SessionStats.duration(e.getValue()));
      }

      return "This session: " + String.join("  -  ", parts);
   }
}
