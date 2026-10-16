package org.blossomsuite.core.board;

import com.google.gson.JsonParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import org.blossomsuite.core.chat.ChatOutput;
import org.blossomsuite.core.config.FeatureConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.jobs.overflow.OverflowTracker;
import org.blossomsuite.core.net.SuiteHttp;
import org.blossomsuite.core.state.SuiteScheduler;
import org.blossomsuite.core.state.SuiteState;
import org.blossomsuite.core.util.JsonUtil;
import org.blossomsuite.core.util.SuiteLog;
import org.blossomsuite.core.xchat.XChatClient;
import org.blossomsuite.core.xchat.XChatModels;

/**
 * The leaderboard. Reading it needs nothing from the player. Being on it is on by default, after a one-time notice and a short wait so
 * the player can switch it off first (and it stays off once they have): the mod
 * proves the account with Mojang's login check (like cross-realm chat, so the access token only ever goes to Mojang), then every
 * few minutes tells the relay the player's running totals per realm - job money and overflow XP - which the relay shows next to
 * the Minecraft name. Turning it off tells the relay to delete the record.
 */
public final class LeaderboardClient {
   public static final LeaderboardClient INSTANCE = new LeaderboardClient(new XChatClient.HttpRelay(), new XChatClient.MojangJoiner(), new GameEnv(), new GameSink(), SuiteScheduler.IO, System::currentTimeMillis);

   static final long REPORT_EVERY_MS = 5L * 60_000L;
   private static final long RENEW_BEFORE_EXPIRY_MS = 60_000L;

   public static final String ON_NOTICE = "You are now on the BlossomBuddy leaderboard: your Minecraft name and your job money and overflow XP totals per realm are shown to everyone (in game and on blossombuddy.site). Turn it off any time with /buddy top off - that deletes what the relay holds.";
   /** Shown once, before anything is sent, to players who did not choose it themselves. */
   public static final String DEFAULT_NOTICE = "You are on the BlossomBuddy leaderboard (on by default): your Minecraft name and your job money and overflow XP totals per realm are shown to everyone, in game and on blossombuddy.site. Nothing is sent for a minute, so you can switch it off first with /buddy top off (that also deletes your entry).";
   /** After that notice, wait this long before the first report. */
   static final long NOTICE_GRACE_MS = 60_000L;
   public static final String OFF_NOTICE = "You are no longer on the BlossomBuddy leaderboard, and your entry has been removed.";

   public enum State {
      OFF,
      CONNECTING,
      READY,
      FAILED
   }

   public interface Env {
      boolean enabled();

      void setEnabled(boolean on);

      /** In a world the mod is active in, with a relay address set. */
      boolean canRun();

      /** The running totals per realm key (lower case): {money, overflow xp}. Realms with nothing yet may be left out. */
      Map<String, double[]> totals();

      /** The one-time "you are on the leaderboard" notice has been shown (or the player chose it themselves). */
      default boolean noticeShown() {
         return true;
      }

      default void setNoticeShown(boolean shown) {
      }
   }

   public interface Sink {
      void notice(String text);
   }

   private final XChatClient.Relay relay;
   private final XChatClient.Joiner joiner;
   private final Env env;
   private final Sink sink;
   private final Executor async;
   private final java.util.function.LongSupplier clock;

   private volatile State state = State.OFF;
   private volatile String detail = "";
   private volatile long retryAt = 0L;
   private volatile long nextReportAt = 0L;
   private volatile long graceUntil = 0L;
   private volatile String token;
   private long tokenExpiresAt;

   public LeaderboardClient(XChatClient.Relay relay, XChatClient.Joiner joiner, Env env, Sink sink, Executor async, java.util.function.LongSupplier clock) {
      this.relay = relay;
      this.joiner = joiner;
      this.env = env;
      this.sink = sink;
      this.async = async;
      this.clock = clock;
   }

   /** Starts the background loop (checks every 10 seconds; totals go out every five minutes). */
   public void init() {
      SuiteScheduler.IO.scheduleWithFixedDelay(() -> {
         try {
            this.step(this.clock.getAsLong());
         } catch (Throwable t) {
            SuiteLog.logger().debug("[leaderboard] step failed: {}", t.toString());
         }
      }, 10L, 10L, TimeUnit.SECONDS);
   }

   public boolean enabled() {
      return this.env.enabled();
   }

   public State state() {
      return this.state;
   }

   /** One line for the screen and the options page. */
   public String status() {
      return switch (this.state) {
         case OFF -> this.env.enabled() ? "Waiting to connect..." : "Off. You are not on the leaderboard.";
         case CONNECTING -> "Connecting...";
         case READY -> "On the leaderboard. Your totals are sent every few minutes.";
         case FAILED -> "Not connected: " + this.detail;
      };
   }

   /** Go on the leaderboard (a first report goes out at once), or come off it (the relay deletes the entry). */
   public void setEnabled(boolean on) {
      this.env.setEnabled(on);
      this.env.setNoticeShown(true); // they just chose, so they know
      if (on) {
         this.retryAt = 0L;
         this.nextReportAt = 0L;
         this.graceUntil = 0L;
         this.sink.notice(ON_NOTICE);
         return;
      }

      String t = this.token;
      this.token = null;
      this.state = State.OFF;
      this.detail = "";
      this.sink.notice(OFF_NOTICE);
      if (t != null) {
         this.leaveRelay(t);
      }
   }

   /** The relay session while connected, else null. */
   public String sessionToken() {
      return this.state == State.READY ? this.token : null;
   }

   private void leaveRelay(String token) {
      this.async.execute(() -> {
         try {
            this.relay.post("/v1/lb/leave", JsonUtil.GSON.toJson(Map.of("token", token)));
         } catch (XChatClient.ChatException ignored) {
            // nothing more to do: the operator can remove an entry from the admin dashboard
         }
      });
   }

   /** One round of work. Public so tests in other packages can drive the loop without waiting on the scheduler. */
   public void step(long now) {
      if (!this.env.enabled()) {
         this.state = State.OFF;
         return;
      }

      if (!this.env.canRun() || now < this.retryAt || now < this.nextReportAt) {
         return;
      }

      if (!this.env.noticeShown()) {
         // on by default, so say so before anything is sent, and leave time to switch it off
         this.env.setNoticeShown(true);
         this.sink.notice(DEFAULT_NOTICE);
         this.graceUntil = now + NOTICE_GRACE_MS;
         return;
      }

      if (now < this.graceUntil) {
         return;
      }

      Map<String, double[]> totals = this.env.totals();
      if (totals.isEmpty()) {
         return; // nothing earned yet, so nothing worth sending
      }

      try {
         if (this.token == null || now >= this.tokenExpiresAt - RENEW_BEFORE_EXPIRY_MS) {
            this.join(now);
         }

         this.report(totals);
         this.state = State.READY;
         this.nextReportAt = now + REPORT_EVERY_MS;
      } catch (XChatClient.ChatException e) {
         this.state = State.FAILED;
         this.detail = e.getMessage();
         this.retryAt = now + e.retryAfterMs();
      }
   }

   private void join(long now) throws XChatClient.ChatException {
      this.state = State.CONNECTING;
      XChatModels.Challenge challenge = parse(this.relay.post("/v1/chat/challenge", "{}"), XChatModels.Challenge.class);
      if (challenge.challengeId == null || challenge.serverId == null) {
         throw new XChatClient.ChatException("The relay sent something unexpected.", 60_000L);
      }

      String name = this.joiner.join(challenge.serverId);
      XChatClient.Reply reply = this.relay.post("/v1/lb/join", JsonUtil.GSON.toJson(Map.of("challengeId", challenge.challengeId, "name", name)));
      if (reply.status() == 401) {
         throw new XChatClient.ChatException("Couldn't verify your Minecraft account.", 60_000L);
      }

      if (reply.status() == 403) {
         throw new XChatClient.ChatException("You're blocked from the leaderboard.", 10L * 60_000L);
      }

      XChatModels.Auth auth = parse(reply, XChatModels.Auth.class);
      if (auth.token == null || auth.token.isBlank()) {
         throw new XChatClient.ChatException("The relay sent something unexpected.", 60_000L);
      }

      this.token = auth.token;
      this.tokenExpiresAt = now + (auth.expiresInSeconds == null ? 3600 : auth.expiresInSeconds) * 1000L;
   }

   private void report(Map<String, double[]> totals) throws XChatClient.ChatException {
      Map<String, Map<String, Double>> realms = new TreeMap<>();
      for (Map.Entry<String, double[]> e : totals.entrySet()) {
         Map<String, Double> one = new LinkedHashMap<>();
         one.put("money", Math.max(0.0, e.getValue()[0]));
         one.put("overflow", Math.max(0.0, e.getValue()[1]));
         realms.put(e.getKey(), one);
      }

      XChatClient.Reply reply = this.relay.post("/v1/lb/report", JsonUtil.GSON.toJson(Map.of("token", this.token, "realms", realms)));
      if (reply.status() == 401) {
         this.token = null; // expired on the relay's side: sign in again on the next round
         throw new XChatClient.ChatException("Signing in again...", 1_000L);
      }

      if (reply.status() == 403) {
         throw new XChatClient.ChatException("You're blocked from the leaderboard.", 10L * 60_000L);
      }

      if (reply.status() == 429) {
         return; // too soon after the last report: harmless
      }

      if (reply.status() != 200) {
         throw new XChatClient.ChatException("The relay said " + reply.status() + ".", 60_000L);
      }
   }

   // ------------------------------------------------------------------ reading a board

   /** Fetches one board. Needs no sign-in. The future fails if the relay can't be reached. */
   public static CompletableFuture<LeaderboardModels.Board> fetchTop(LeaderboardModels.Choice choice, int limit) {
      SuiteHttp http = SuiteState.INSTANCE.http;
      if (http == null || !http.enabled()) {
         return CompletableFuture.failedFuture(new IllegalStateException("No relay address is set."));
      }

      String query = "?metric=" + safe(choice.metric()) + "&realm=" + safe(choice.realm()) + "&period=" + safe(choice.period()) + "&limit=" + Math.max(1, Math.min(100, limit));
      return http.get("/v1/lb/top" + query, null).thenApply(r -> {
         if (r.statusCode() != 200) {
            throw new IllegalStateException("The relay said " + r.statusCode() + ".");
         }

         return parseBoard(r.body());
      });
   }

   /** Where the signed-in player stands on a board, or null when they are not connected. */
   public LeaderboardModels.Me fetchMe(LeaderboardModels.Choice choice) {
      String t = this.sessionToken();
      if (t == null) {
         return null;
      }

      try {
         Map<String, String> body = Map.of("token", t, "metric", safe(choice.metric()), "realm", safe(choice.realm()), "period", safe(choice.period()));
         XChatClient.Reply reply = this.relay.post("/v1/lb/me", JsonUtil.GSON.toJson(body));
         return reply.status() == 200 ? JsonUtil.GSON.fromJson(reply.body(), LeaderboardModels.Me.class) : null;
      } catch (XChatClient.ChatException | JsonParseException e) {
         return null;
      }
   }

   static LeaderboardModels.Board parseBoard(String json) {
      try {
         LeaderboardModels.Board board = JsonUtil.GSON.fromJson(json, LeaderboardModels.Board.class);
         if (board == null) {
            throw new IllegalStateException("The relay sent something unexpected.");
         }

         return board;
      } catch (JsonParseException e) {
         throw new IllegalStateException("The relay sent something unexpected.");
      }
   }

   /** Only letters, digits and underscores go into a request. */
   private static String safe(String s) {
      return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
   }

   private static <T> T parse(XChatClient.Reply reply, Class<T> type) throws XChatClient.ChatException {
      if (reply.status() != 200) {
         throw new XChatClient.ChatException("The relay said " + reply.status() + ".", 30_000L);
      }

      try {
         T parsed = JsonUtil.GSON.fromJson(reply.body(), type);
         if (parsed == null) {
            throw new XChatClient.ChatException("The relay sent something unexpected.", 60_000L);
         }

         return parsed;
      } catch (JsonParseException e) {
         throw new XChatClient.ChatException("The relay sent something unexpected.", 60_000L);
      }
   }

   // ------------------------------------------------------------------ real wiring

   private static final class GameEnv implements Env {
      @Override
      public boolean enabled() {
         return FeatureConfig.INSTANCE.leaderboard.share;
      }

      @Override
      public void setEnabled(boolean on) {
         FeatureConfig.INSTANCE.leaderboard.share = on;
         FeatureConfig.INSTANCE.leaderboard.chosen = true;
         FeatureConfig.markDirty();
      }

      @Override
      public boolean noticeShown() {
         return FeatureConfig.INSTANCE.leaderboard.noticeShown;
      }

      @Override
      public void setNoticeShown(boolean shown) {
         FeatureConfig.INSTANCE.leaderboard.noticeShown = shown;
         FeatureConfig.markDirty();
      }

      @Override
      public boolean canRun() {
         Minecraft mc = Minecraft.getInstance();
         SuiteHttp http = SuiteState.INSTANCE.http;
         return mc.player != null && SuiteConfig.INSTANCE.isEnabledForCurrentWorld() && http != null && http.enabled();
      }

      @Override
      public Map<String, double[]> totals() {
         return readGameTotals();
      }

      static void add(Map<String, double[]> out, String realm, int slot, Double value) {
         if (realm != null && value != null && value > 0.0) {
            out.computeIfAbsent(realm.trim().toLowerCase(Locale.ROOT), k -> new double[2])[slot] += value;
         }
      }
   }

   /** Your running totals per realm, {money, overflow XP}, as the mod has them now (also used by the session tracker). */
   public static Map<String, double[]> readGameTotals() {
      for (int attempt = 0; attempt < 3; attempt++) {
         Map<String, double[]> out = new TreeMap<>();
         try {
            for (Map.Entry<String, Double> e : SuiteConfig.INSTANCE.JobsConfig.lifetimeByServer().entrySet()) {
               GameEnv.add(out, e.getKey(), 0, e.getValue());
            }

            for (Map.Entry<String, Map<String, Double>> realm : OverflowTracker.INSTANCE.snapshotAll().entrySet()) {
               for (double xp : realm.getValue().values()) {
                  GameEnv.add(out, realm.getKey(), 1, xp);
               }
            }
         } catch (java.util.ConcurrentModificationException busy) {
            continue; // the totals were being updated just then; look again
         }

         out.keySet().removeIf(k -> k.isEmpty() || k.equals(OverflowTracker.UNKNOWN_REALM));
         out.values().removeIf(v -> v[0] <= 0.0 && v[1] <= 0.0);
         return out;
      }

      return Map.of();
   }

   private static final class GameSink implements Sink {
      @Override
      public void notice(String text) {
         Minecraft.getInstance().execute(() -> ChatOutput.info(text));
      }
   }
}
