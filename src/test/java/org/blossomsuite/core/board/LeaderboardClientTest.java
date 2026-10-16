package org.blossomsuite.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.blossomsuite.core.util.JsonUtil;
import org.blossomsuite.core.xchat.XChatClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The leaderboard sharing loop, with a fake relay and a fake Mojang. */
class LeaderboardClientTest {
   private static final long T = 1_000_000L;

   static final class FakeEnv implements LeaderboardClient.Env {
      boolean enabled = true;
      boolean canRun = true;
      Map<String, double[]> totals = new TreeMap<>(Map.of("cherry", new double[]{1500.0, 40.0}, "tulip", new double[]{200.0, 0.0}));
      boolean noticeShown = true;
      Map<String, Object> progress = null;

      @Override public boolean enabled() { return this.enabled; }
      @Override public void setEnabled(boolean on) { this.enabled = on; }
      @Override public boolean canRun() { return this.canRun; }
      @Override public Map<String, double[]> totals() { return this.totals; }
      @Override public Map<String, Object> progress() { return this.progress; }
      @Override public boolean noticeShown() { return this.noticeShown; }
      @Override public void setNoticeShown(boolean shown) { this.noticeShown = shown; }
   }

   static final class FakeRelay implements XChatClient.Relay {
      final List<String[]> calls = new ArrayList<>();
      int reportStatus = 200;
      int profileStatus = 200;
      String profileBody = "{\"name\":\"Alice\",\"level\":12,\"title\":\"Bud\",\"xp\":7000,\"xpIntoLevel\":525,\"xpForNext\":1050,\"minutes\":90,\"style\":{\"gradientId\":\"sunset\",\"colors\":[\"#ff9a2e\",\"#ff4f81\"],\"symbolId\":\"flower\",\"glyph\":\"\u273F\"},\"catalog\":{\"gradients\":[{\"id\":\"sunset\",\"label\":\"Sunset\",\"level\":10,\"colors\":[\"#ff9a2e\",\"#ff4f81\"],\"unlocked\":true}],\"symbols\":[{\"id\":\"flower\",\"label\":\"Flower\",\"glyph\":\"\u273F\",\"level\":1,\"unlocked\":true}]}}";
      int cosmeticStatus = 200;
      String cosmeticBody = "{\"ok\":true}";
      int joinStatus = 200;
      boolean down = false;

      @Override
      public XChatClient.Reply post(String path, String json) throws XChatClient.ChatException {
         this.calls.add(new String[]{path, json});
         if (this.down) {
            throw new XChatClient.ChatException("Cannot reach the relay.", 30_000L);
         }

         return switch (path) {
            case "/v1/chat/challenge" -> new XChatClient.Reply(200, "{\"challengeId\":\"c1\",\"serverId\":\"s1\"}");
            case "/v1/lb/join" -> new XChatClient.Reply(this.joinStatus, "{\"token\":\"T1\",\"name\":\"Alice\",\"expiresInSeconds\":21600}");
            case "/v1/lb/report" -> new XChatClient.Reply(this.reportStatus, "{\"ok\":true}");
            case "/v1/lb/profile" -> new XChatClient.Reply(this.profileStatus, this.profileBody);
            case "/v1/lb/cosmetic" -> new XChatClient.Reply(this.cosmeticStatus, this.cosmeticBody);
            case "/v1/lb/me" -> new XChatClient.Reply(200, "{\"rank\":3,\"value\":1500,\"total\":12}");
            default -> new XChatClient.Reply(200, "{\"ok\":true}");
         };
      }

      List<String> paths() {
         return this.calls.stream().map(c -> c[0]).toList();
      }

      JsonObject bodyOf(String path) {
         return this.calls.stream().filter(c -> c[0].equals(path)).map(c -> JsonUtil.GSON.fromJson(c[1], JsonObject.class)).findFirst().orElseThrow();
      }
   }

   private FakeEnv env;
   private FakeRelay relay;
   private List<String> notices;
   private LeaderboardClient client;

   @BeforeEach
   void setUp() {
      this.env = new FakeEnv();
      this.relay = new FakeRelay();
      this.notices = new ArrayList<>();
      this.client = new LeaderboardClient(this.relay, serverId -> "Alice", this.env, this.notices::add, Runnable::run, () -> T);
   }

   @Test
   void itProvesTheAccountThenSendsTheTotalsPerRealmAndNothingElse() {
      this.client.step(T);
      assertEquals(List.of("/v1/chat/challenge", "/v1/lb/join", "/v1/lb/report"), this.relay.paths());

      JsonObject join = this.relay.bodyOf("/v1/lb/join");
      assertEquals(2, join.size(), "only the challenge and the name: " + join);
      assertEquals("Alice", join.get("name").getAsString());

      JsonObject report = this.relay.bodyOf("/v1/lb/report");
      assertEquals(2, report.size(), "only the token and the totals: " + report);
      assertEquals("T1", report.get("token").getAsString());
      JsonObject cherry = report.getAsJsonObject("realms").getAsJsonObject("cherry");
      assertEquals(1500.0, cherry.get("money").getAsDouble());
      assertEquals(40.0, cherry.get("overflow").getAsDouble());
      assertEquals(200.0, report.getAsJsonObject("realms").getAsJsonObject("tulip").get("money").getAsDouble());
      assertEquals(LeaderboardClient.State.READY, this.client.state());
   }

   @Test
   void itOnlySendsEveryFewMinutesAndReusesItsSignIn() {
      this.client.step(T);
      this.relay.calls.clear();
      this.client.step(T + 60_000L);
      assertTrue(this.relay.calls.isEmpty(), "too soon");
      this.client.step(T + LeaderboardClient.REPORT_EVERY_MS);
      assertEquals(List.of("/v1/lb/report"), this.relay.paths(), "no second sign-in");
   }

   @Test
   void nothingIsSentWhenOffOrOutOfAWorldOrWithNothingEarned() {
      this.env.enabled = false;
      this.client.step(T);
      this.env.enabled = true;
      this.env.canRun = false;
      this.client.step(T);
      this.env.canRun = true;
      this.env.totals = new TreeMap<>();
      this.client.step(T);
      assertTrue(this.relay.calls.isEmpty());
   }

   @Test
   void turningItOnSaysWhatIsSharedAndTurningItOffDeletesTheEntry() {
      this.env.enabled = false;
      this.client.setEnabled(true);
      assertEquals(List.of(LeaderboardClient.ON_NOTICE), this.notices);
      this.client.step(T);
      assertEquals("T1", this.client.sessionToken());

      this.relay.calls.clear();
      this.client.setEnabled(false);
      assertFalse(this.env.enabled);
      assertEquals(List.of("/v1/lb/leave"), this.relay.paths());
      assertEquals("T1", this.relay.bodyOf("/v1/lb/leave").get("token").getAsString());
      assertEquals(LeaderboardClient.OFF_NOTICE, this.notices.get(1));
      assertNull(this.client.sessionToken());
      assertEquals(LeaderboardClient.State.OFF, this.client.state());
   }

   @Test
   void anExpiredSessionSignsInAgainOnTheNextRound() {
      this.client.step(T);
      this.relay.reportStatus = 401;
      this.client.step(T + LeaderboardClient.REPORT_EVERY_MS);
      assertEquals(LeaderboardClient.State.FAILED, this.client.state());

      this.relay.calls.clear();
      this.relay.reportStatus = 200;
      this.client.step(T + LeaderboardClient.REPORT_EVERY_MS + 2_000L);
      assertEquals(List.of("/v1/chat/challenge", "/v1/lb/join", "/v1/lb/report"), this.relay.paths());
      assertEquals(LeaderboardClient.State.READY, this.client.state());
   }

   @Test
   void aBlockedPlayerOrAnUnreachableRelayIsReportedAndRetriedLater() {
      this.relay.joinStatus = 403;
      this.client.step(T);
      assertEquals(LeaderboardClient.State.FAILED, this.client.state());
      assertTrue(this.client.status().contains("blocked"));

      this.relay.joinStatus = 200;
      this.relay.calls.clear();
      this.client.step(T + 1_000L);
      assertTrue(this.relay.calls.isEmpty(), "waits before trying again");

      this.relay.down = true;
      this.client.step(T + 11 * 60_000L);
      assertEquals(LeaderboardClient.State.FAILED, this.client.state());
   }

   @Test
   void itCanSayWhereThePlayerStandsOnceConnected() {
      assertNull(this.client.fetchMe(LeaderboardModels.Choice.DEFAULT), "not connected yet");
      this.client.step(T);
      LeaderboardModels.Me me = this.client.fetchMe(new LeaderboardModels.Choice("money", "cherry", "week"));
      assertEquals(3, me.rank);
      assertEquals(12, me.total);
      JsonObject asked = this.relay.bodyOf("/v1/lb/me");
      assertEquals("cherry", asked.get("realm").getAsString());
      assertEquals("week", asked.get("period").getAsString());
   }

   @Test
   void aBoardFromTheRelayIsReadAndJunkIsRefused() {
      LeaderboardModels.Board board = LeaderboardClient.parseBoard("{\"metric\":\"money\",\"realm\":\"all\",\"period\":\"all\",\"total\":2,\"entries\":[{\"rank\":1,\"name\":\"Alice\",\"value\":1500},{\"rank\":2,\"name\":\"Bob\",\"value\":900}],\"realmsAvailable\":[{\"key\":\"cherry\",\"label\":\"Cherry\"}]}");
      assertEquals(2, board.rows().size());
      assertEquals("Alice", board.rows().get(0).name);
      assertEquals(900.0, board.rows().get(1).value);
      assertEquals("cherry", board.realmsAvailable.get(0).key);
      assertEquals(0, LeaderboardClient.parseBoard("{}").rows().size());
      try {
         LeaderboardClient.parseBoard("not json");
         throw new AssertionError("junk must be refused");
      } catch (IllegalStateException expected) {
         assertEquals("The relay sent something unexpected.", expected.getMessage());
      }
   }

   @Test
   void onByDefaultItSaysSoFirstAndSendsNothingUntilTheWaitIsOver() {
      this.env.noticeShown = false;
      this.client.step(T);
      assertEquals(List.of(LeaderboardClient.DEFAULT_NOTICE), this.notices);
      assertTrue(this.relay.calls.isEmpty(), "nothing is sent with the notice");
      assertTrue(this.env.noticeShown);

      this.client.step(T + 30_000L);
      assertTrue(this.relay.calls.isEmpty(), "still waiting, so there is time to switch it off");

      this.client.step(T + LeaderboardClient.NOTICE_GRACE_MS + 1_000L);
      assertEquals(List.of("/v1/chat/challenge", "/v1/lb/join", "/v1/lb/report"), this.relay.paths());
      assertEquals(1, this.notices.size(), "the notice is only ever shown once");
   }

   @Test
   void switchingItOffDuringTheWaitMeansNothingIsEverSent() {
      this.env.noticeShown = false;
      this.client.step(T);
      this.client.setEnabled(false);
      this.client.step(T + LeaderboardClient.NOTICE_GRACE_MS + 5_000L);
      assertFalse(this.env.enabled);
      assertTrue(this.relay.calls.stream().noneMatch(c -> c[0].equals("/v1/lb/join") || c[0].equals("/v1/lb/report")));
   }

   @Test
   void choosingItYourselfSkipsTheNoticeAndTheWait() {
      this.env.noticeShown = false;
      this.env.enabled = false;
      this.client.setEnabled(true);
      assertTrue(this.env.noticeShown);
      this.client.step(T);
      assertEquals(List.of("/v1/chat/challenge", "/v1/lb/join", "/v1/lb/report"), this.relay.paths());
   }

   @Test
   void theLevelCountersGoWithTheTotalsWhenThereAreSome() {
      this.env.progress = new java.util.LinkedHashMap<>(Map.of("minutes", 90L, "done", 2, "failed", 1, "firsts", List.of("emote")));
      this.client.step(T);
      JsonObject report = this.relay.bodyOf("/v1/lb/report");
      assertEquals(3, report.size(), "token, totals and progress: " + report);
      JsonObject progress = report.getAsJsonObject("progress");
      assertEquals(90L, progress.get("minutes").getAsLong());
      assertEquals("emote", progress.getAsJsonArray("firsts").get(0).getAsString());
   }

   @Test
   void yourProfileIsLoadedEvenBeforeTheFirstTotalsHaveGoneOut() throws Exception {
      this.env.totals = new TreeMap<>(); // nothing earned yet, so nothing has been reported
      ProfileModels.Profile p = this.client.loadProfile().get();
      assertEquals(12, p.level);
      assertEquals("Bud", p.title);
      assertEquals(0.5, p.progress(), 0.001);
      assertEquals("sunset", p.style.gradientId);
      assertEquals(1, p.gradients().size());
      assertTrue(p.symbols().get(0).unlocked);
      assertEquals(List.of("/v1/chat/challenge", "/v1/lb/join", "/v1/lb/profile"), this.relay.paths(), "it signs in on its own first");
   }

   @Test
   void anExpiredSessionSignsInAgainOnceForTheProfile() {
      this.client.step(T);
      this.relay.calls.clear();
      this.relay.profileStatus = 401;
      org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class, () -> this.client.loadProfile().get());
      assertEquals(2, this.relay.paths().stream().filter(p -> p.equals("/v1/lb/profile")).count(), "it retried once after signing in again");
   }

   @Test
   void youHaveNoProfileWhileTheLeaderboardIsOff() {
      this.env.enabled = false;
      java.util.concurrent.ExecutionException e = org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class, () -> this.client.loadProfile().get());
      assertTrue(e.getCause().getMessage().contains("not on the leaderboard"));
      assertTrue(this.relay.calls.isEmpty());
   }

   @Test
   void wearingSomethingSendsOnlyTheChoiceAndShowsTheRelaysReasonWhenRefused() throws Exception {
      this.client.step(T);
      this.relay.calls.clear();
      this.client.applyStyle("sunset", null, "flower").get();
      JsonObject sent = this.relay.bodyOf("/v1/lb/cosmetic");
      assertEquals("sunset", sent.get("gradient").getAsString());
      assertEquals("flower", sent.get("symbol").getAsString());
      assertEquals("T1", sent.get("token").getAsString());
      assertEquals(3, sent.size(), "no colours unless it is a custom gradient: " + sent);

      this.relay.cosmeticStatus = 403;
      this.relay.cosmeticBody = "{\"error\":\"unlocks at level 50\"}";
      java.util.concurrent.ExecutionException e = org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class, () -> this.client.applyStyle("gold", null, null).get());
      assertEquals("unlocks at level 50", e.getCause().getMessage());
   }

   @Test
   void aCustomGradientSendsItsColours() throws Exception {
      this.client.step(T);
      this.relay.calls.clear();
      this.client.applyStyle("custom", List.of("#ff8800", "#00ccff"), null).get();
      JsonObject sent = this.relay.bodyOf("/v1/lb/cosmetic");
      assertEquals("#ff8800", sent.getAsJsonArray("colors").get(0).getAsString());
      assertEquals("none", sent.get("symbol").getAsString());
   }
}
