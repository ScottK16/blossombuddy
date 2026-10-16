package org.blossomsuite.core.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.blossomsuite.core.board.BoardFormat;
import org.blossomsuite.core.board.LeaderboardClient;
import org.blossomsuite.core.board.LeaderboardModels;

/**
 * The BlossomBuddy leaderboard: top earners and top overflow XP, for everyone or one realm, all time, this week or today.
 * Anyone can read it; to be on it a player turns on "Share my stats" (see {@link LeaderboardClient}).
 */
public final class LeaderboardScreen extends Screen {
   private static final String[] DEFAULT_REALMS = {"cherry", "spirit", "lotus", "tulip"};
   private static final int MAX_ROWS = 15;
   private static final long REFRESH_MS = 60_000L;
   private static final int PANEL_MAX_W = 340;
   private static final int ROW_H = 13;

   private static LeaderboardModels.Choice remembered = LeaderboardModels.Choice.DEFAULT;

   private LeaderboardModels.Choice choice = remembered;
   private volatile LeaderboardModels.Board board;
   private volatile LeaderboardModels.Me me;
   private volatile String error = "";
   private volatile boolean loading = false;
   private long lastLoadAt = 0L;
   private int loadToken = 0;

   public LeaderboardScreen() {
      super(Component.literal("BlossomBuddy leaderboard"));
   }

   @Override
   protected void init() {
      int w = this.panelW();
      int x = (this.width - w) / 2;
      int third = (w - 8) / 3;
      int y = 26;
      this.addRenderableWidget(StyledButton.of(Component.literal(BoardFormat.metricLabel(this.choice.metric())), b -> this.choose(new LeaderboardModels.Choice(BoardFormat.nextMetric(this.choice.metric()), this.choice.realm(), this.choice.period())))
         .dimensions(x, y, third, 18).build());
      this.addRenderableWidget(StyledButton.of(Component.literal(this.realmLabel(this.choice.realm())), b -> this.choose(new LeaderboardModels.Choice(this.choice.metric(), this.nextRealm(), this.choice.period())))
         .dimensions(x + third + 4, y, third, 18).build());
      this.addRenderableWidget(StyledButton.of(Component.literal(BoardFormat.periodLabel(this.choice.period())), b -> this.choose(new LeaderboardModels.Choice(this.choice.metric(), this.choice.realm(), BoardFormat.nextPeriod(this.choice.period()))))
         .dimensions(x + 2 * (third + 4), y, third, 18).build());

      boolean on = LeaderboardClient.INSTANCE.enabled();
      int footY = this.height - 26;
      this.addRenderableWidget(StyledButton.of(Component.literal(on ? "Share my stats: ON" : "Share my stats: OFF"), b -> {
         LeaderboardClient.INSTANCE.setEnabled(!LeaderboardClient.INSTANCE.enabled());
         this.me = null;
         this.rebuildWidgets();
      }).dimensions(this.width / 2 - 154, footY, 150, 20).build());
      this.addRenderableWidget(StyledButton.of(Component.literal("Done"), b -> this.onClose()).dimensions(this.width / 2 + 4, footY, 150, 20).build());
      this.reload();
   }

   private int panelW() {
      return Math.min(PANEL_MAX_W, this.width - 24);
   }

   private void choose(LeaderboardModels.Choice next) {
      this.choice = next;
      remembered = next;
      this.board = null;
      this.me = null;
      this.rebuildWidgets();
   }

   /** The realm keys to cycle through: All, then the ones the relay lists (or the four known ones). */
   private List<String> realms() {
      List<String> out = new ArrayList<>();
      out.add("all");
      LeaderboardModels.Board b = this.board;
      if (b != null && b.realmsAvailable != null && !b.realmsAvailable.isEmpty()) {
         for (LeaderboardModels.RealmInfo r : b.realmsAvailable) {
            if (r != null && r.key != null) {
               out.add(r.key);
            }
         }
      } else {
         out.addAll(List.of(DEFAULT_REALMS));
      }

      return out;
   }

   private String nextRealm() {
      List<String> all = this.realms();
      int at = all.indexOf(this.choice.realm());
      return all.get((at + 1) % all.size());
   }

   private String realmLabel(String key) {
      if (key == null || key.equals("all")) {
         return "All realms";
      }

      return Character.toUpperCase(key.charAt(0)) + key.substring(1);
   }

   private void reload() {
      this.lastLoadAt = System.currentTimeMillis();
      this.loading = true;
      this.error = "";
      final int mine = ++this.loadToken;
      final LeaderboardModels.Choice asked = this.choice;
      LeaderboardClient.fetchTop(asked, MAX_ROWS).whenComplete((result, failure) -> {
         LeaderboardModels.Me place = failure == null ? LeaderboardClient.INSTANCE.fetchMe(asked) : null;
         if (this.minecraft != null) {
            this.minecraft.execute(() -> {
               if (mine != this.loadToken) {
                  return; // a newer request replaced this one
               }

               this.loading = false;
               if (failure != null) {
                  this.error = "The leaderboard can't be reached right now.";
                  return;
               }

               this.board = result;
               this.me = place;
            });
         }
      });
   }

   @Override
   public void tick() {
      if (!this.loading && System.currentTimeMillis() - this.lastLoadAt > REFRESH_MS) {
         this.reload();
      }
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
      super.extractRenderState(ctx, mouseX, mouseY, delta);
      Font tr = this.font;
      ctx.centeredText(tr, Component.literal("BlossomBuddy leaderboard").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), this.width / 2, 10, -1);

      int w = this.panelW();
      int x = (this.width - w) / 2;
      int top = 50;
      int bottom = this.height - 62;
      ctx.fill(x, top - 4, x + w, bottom + 4, 0x66000000);

      LeaderboardModels.Board b = this.board;
      int y = top;
      if (b == null) {
         String text = this.error.isEmpty() ? "Loading..." : this.error;
         ctx.centeredText(tr, Component.literal(text).withStyle(ChatFormatting.GRAY), this.width / 2, top + 20, -1);
      } else if (b.rows().isEmpty()) {
         ctx.centeredText(tr, Component.literal("Nobody is on this board yet.").withStyle(ChatFormatting.GRAY), this.width / 2, top + 20, -1);
         ctx.centeredText(tr, Component.literal(LeaderboardClient.INSTANCE.enabled() ? "Your totals appear a few minutes after you earn something." : "Turn on \"Share my stats\" below to be the first.").withStyle(ChatFormatting.DARK_GRAY), this.width / 2, top + 34, -1);
      } else {
         String mineName = this.minecraft == null || this.minecraft.getUser() == null ? "" : this.minecraft.getUser().getName();
         int rows = Math.max(1, Math.min(MAX_ROWS, (bottom - top) / ROW_H));
         int shown = 0;
         for (LeaderboardModels.Entry e : b.rows()) {
            if (shown++ >= rows) {
               break;
            }

            boolean mine = e.name != null && e.name.equalsIgnoreCase(mineName);
            if (mine) {
               ctx.fill(x + 2, y - 1, x + w - 2, y + ROW_H - 2, 0x33F48FB1);
            }

            ChatFormatting medal = e.rank == 1 ? ChatFormatting.GOLD : e.rank == 2 ? ChatFormatting.WHITE : e.rank == 3 ? ChatFormatting.RED : ChatFormatting.GRAY;
            ctx.text(tr, Component.literal("#" + e.rank).withStyle(medal), x + 6, y + 1, -1);
            ctx.text(tr, Component.literal(String.valueOf(e.name)).withStyle(mine ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.WHITE), x + 34, y + 1, -1);
            String value = BoardFormat.value(b.metric, e.value);
            ctx.text(tr, Component.literal(value).withStyle(ChatFormatting.GREEN), x + w - 6 - tr.width(value), y + 1, -1);
            y += ROW_H;
         }
      }

      ctx.centeredText(tr, Component.literal(this.youLine(b)).withStyle(ChatFormatting.GRAY), this.width / 2, this.height - 54, -1);
      String status = LeaderboardClient.INSTANCE.enabled() ? LeaderboardClient.INSTANCE.status() : "Not sharing. Your stats stay on your computer.";
      ctx.centeredText(tr, Component.literal(status).withStyle(ChatFormatting.DARK_GRAY), this.width / 2, this.height - 42, -1);
   }

   private String youLine(LeaderboardModels.Board b) {
      if (!LeaderboardClient.INSTANCE.enabled()) {
         return "Turn on \"Share my stats\" to appear here (your name and totals are shown).";
      }

      LeaderboardModels.Me m = this.me;
      if (m == null) {
         return "";
      }

      if (m.rank <= 0) {
         return "You're not on this board yet - it fills in once your totals are sent.";
      }

      String metric = b == null ? this.choice.metric() : b.metric;
      return String.format(Locale.ROOT, "You: #%d of %d  (%s)", m.rank, m.total, BoardFormat.value(metric, m.value));
   }
}
