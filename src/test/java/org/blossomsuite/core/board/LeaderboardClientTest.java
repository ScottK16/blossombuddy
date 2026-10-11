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

      @Override public boolean enabled() { return this.enabled; }
      @Override public void setEnabled(boolean on) { this.enabled = on; }
      @Override public boolean canRun() { return this.canRun; }
      @Override public Map<String, double[]> totals() { return this.totals; }
   }

   static final class FakeRelay implements XChatClient.Relay {
      final List<String[]> calls = new ArrayList<>();
      int reportStatus = 200;
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
}
