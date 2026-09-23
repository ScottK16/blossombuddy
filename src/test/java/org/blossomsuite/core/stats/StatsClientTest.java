package org.blossomsuite.core.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.blossomsuite.core.util.JsonUtil;
import org.blossomsuite.core.xchat.XChatClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The usage counting client, with a fake relay and a fake Mojang. */
class StatsClientTest {
   private static final long START = 1_000_000_000L;

   static final class FakeEnv implements StatsClient.Env {
      boolean enabled = true;
      boolean shareName = false;
      boolean canRun = true;
      String installId = "";
      long lastPing = 0L;
      String namedAs = "";
      boolean noticeShown = false;
      String account = "Alice";
      String version = "2.0.0-beta.1";

      @Override public boolean enabled() { return this.enabled; }
      @Override public void setEnabled(boolean on) { this.enabled = on; }
      @Override public boolean shareName() { return this.shareName; }
      @Override public void setShareName(boolean on) { this.shareName = on; }
      @Override public boolean canRun() { return this.canRun; }
      @Override public String installId() { return this.installId; }
      @Override public void setInstallId(String id) { this.installId = id; }
      @Override public long lastPingMs() { return this.lastPing; }
      @Override public void setLastPingMs(long ms) { this.lastPing = ms; }
      @Override public String namedAs() { return this.namedAs; }
      @Override public void setNamedAs(String name) { this.namedAs = name; }
      @Override public boolean noticeShown() { return this.noticeShown; }
      @Override public void setNoticeShown(boolean shown) { this.noticeShown = shown; }
      @Override public String accountName() { return this.account; }
      @Override public String version() { return this.version; }
   }

   static final class FakeRelay implements XChatClient.Relay {
      final List<String[]> calls = new ArrayList<>();
      int identifyStatus = 200;
      boolean down = false;

      @Override
      public XChatClient.Reply post(String path, String json) throws XChatClient.ChatException {
         this.calls.add(new String[]{path, json});
         if (this.down) {
            throw new XChatClient.ChatException("Can't reach the relay.", 30_000L);
         }

         return switch (path) {
            case "/v1/chat/challenge" -> new XChatClient.Reply(200, "{\"challengeId\":\"c1\",\"serverId\":\"s1\"}");
            case "/v1/stats/identify" -> new XChatClient.Reply(this.identifyStatus, "{\"ok\":true}");
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
   private List<String> joined;
   private StatsClient client;

   @BeforeEach
   void setUp() {
      this.env = new FakeEnv();
      this.relay = new FakeRelay();
      this.notices = new ArrayList<>();
      this.joined = new ArrayList<>();
      XChatClient.Joiner joiner = serverId -> {
         this.joined.add(serverId);
         return this.env.account;
      };
      this.client = new StatsClient(this.relay, joiner, this.env, this.notices::add, Runnable::run, () -> START);
   }

   /** Gets past the first-time notice so the next step can send. */
   private long pastNotice() {
      this.client.step(START);
      return START + StatsClient.NOTICE_GRACE_MS + 1;
   }

   @Test
   void theFirstStepOnlyTellsThePlayerAndSendsNothing() {
      this.client.step(START);
      assertEquals(1, this.notices.size());
      assertTrue(this.notices.get(0).contains("/buddy stats off"), "says how to turn it off");
      assertTrue(this.notices.get(0).contains("No name"), "says no name is sent");
      assertTrue(this.env.noticeShown);
      assertTrue(this.relay.calls.isEmpty(), "nothing is sent before they could react");

      this.client.step(START + 1000L);
      assertTrue(this.relay.calls.isEmpty(), "and not straight after either");
   }

   @Test
   void afterTheGracePeriodItSaysHelloWithARandomIdAndTheVersionOnly() {
      long t = this.pastNotice();
      this.client.step(t);

      assertEquals(List.of("/v1/stats/ping"), this.relay.paths());
      JsonObject body = this.relay.bodyOf("/v1/stats/ping");
      assertEquals(2, body.size(), "only the id and the version: " + body);
      assertTrue(body.get("installId").getAsString().matches("[0-9a-f]{32}"));
      assertEquals("2.0.0-beta.1", body.get("version").getAsString());
      assertEquals(this.env.installId, body.get("installId").getAsString());
      assertEquals(t, this.env.lastPing);
   }

   @Test
   void theIdStaysTheSameAndHelloIsOnlySentEverySixHours() {
      long t = this.pastNotice();
      this.client.step(t);
      String id = this.env.installId;

      this.client.step(t + StatsClient.PING_EVERY_MS - 1000L);
      assertEquals(1, this.relay.calls.size(), "not again yet");

      this.client.step(t + StatsClient.PING_EVERY_MS);
      assertEquals(2, this.relay.calls.size());
      assertEquals(id, this.env.installId, "same install, same id");
   }

   @Test
   void nothingIsSentWhenCountingIsOffOrThePlayerIsNotInTheRightPlace() {
      this.env.enabled = false;
      this.client.step(START);
      this.client.step(START + 10_000_000L);
      assertTrue(this.relay.calls.isEmpty());
      assertTrue(this.notices.isEmpty(), "and no notice about something that is off");

      this.env.enabled = true;
      this.env.canRun = false; // singleplayer, another server, or no relay address
      this.client.step(START + 20_000_000L);
      assertTrue(this.relay.calls.isEmpty());
   }

   @Test
   void theNameIsNeverSentUnlessThePlayerAsked() {
      long t = this.pastNotice();
      this.client.step(t);
      this.client.step(t + StatsClient.PING_EVERY_MS * 3);
      assertFalse(this.relay.paths().contains("/v1/stats/identify"));
      assertFalse(this.relay.paths().contains("/v1/chat/challenge"));
      assertTrue(this.joined.isEmpty(), "Mojang is not even contacted");
      for (String[] call : this.relay.calls) {
         assertFalse(call[1].contains("Alice"), "no name anywhere in " + call[0]);
      }
   }

   @Test
   void sharingTheNameProvesTheAccountWithMojangAndStoresIt() {
      this.env.shareName = true;
      long t = this.pastNotice();
      this.client.step(t);

      assertEquals(List.of("/v1/stats/ping", "/v1/chat/challenge", "/v1/stats/identify"), this.relay.paths());
      assertEquals(List.of("s1"), this.joined, "the mod tells Mojang it is joining with the relay's code");
      JsonObject identify = this.relay.bodyOf("/v1/stats/identify");
      assertEquals(this.env.installId, identify.get("installId").getAsString());
      assertEquals("c1", identify.get("challengeId").getAsString());
      assertEquals("Alice", identify.get("name").getAsString());
      assertEquals("Alice", this.env.namedAs);

      this.client.step(t + 60_000L);
      assertEquals(3, this.relay.calls.size(), "already identified: not repeated");
   }

   @Test
   void aChangedMinecraftNameIsSentAgain() {
      this.env.shareName = true;
      long t = this.pastNotice();
      this.client.step(t);
      this.env.account = "AliceNewName";
      this.client.step(t + 60_000L);
      assertEquals("AliceNewName", this.env.namedAs);
      assertEquals(2, this.relay.paths().stream().filter("/v1/stats/identify"::equals).count());
   }

   @Test
   void turningOnNameSharingExplainsWhatIsStoredAndTurnsCountingOn() {
      this.env.enabled = false;
      this.client.setShareName(true);
      assertTrue(this.env.enabled);
      assertTrue(this.env.shareName);
      assertTrue(this.notices.get(0).contains("Minecraft name is now stored"));
      assertTrue(this.notices.get(0).contains("/buddy stats name off"));
   }

   @Test
   void turningOffNameSharingAsksTheRelayToRemoveIt() {
      this.env.shareName = true;
      this.env.namedAs = "Alice";
      this.env.installId = "a".repeat(32);
      this.client.setShareName(false);

      assertFalse(this.env.shareName);
      assertEquals("", this.env.namedAs);
      assertEquals(List.of("/v1/stats/unname"), this.relay.paths());
      assertEquals("a".repeat(32), this.relay.bodyOf("/v1/stats/unname").get("installId").getAsString());
      assertTrue(this.env.enabled, "still counted anonymously");
   }

   @Test
   void turningOffCountingForgetsTheInstallOnTheRelayAndOnTheClient() {
      this.env.shareName = true;
      this.env.namedAs = "Alice";
      this.env.installId = "b".repeat(32);
      this.env.lastPing = 5L;
      this.client.setEnabled(false);

      assertFalse(this.env.enabled);
      assertFalse(this.env.shareName, "no name is shared without counting");
      assertEquals("", this.env.namedAs);
      assertEquals("", this.env.installId, "the id is thrown away");
      assertEquals(0L, this.env.lastPing);
      assertEquals(List.of("/v1/stats/forget"), this.relay.paths());
      assertEquals("b".repeat(32), this.relay.bodyOf("/v1/stats/forget").get("installId").getAsString());
      assertTrue(this.notices.get(0).contains("removed from the relay"));

      this.client.step(START + 99_000_000L);
      assertEquals(1, this.relay.calls.size(), "and nothing more is ever sent");
   }

   @Test
   void ifTheRelayIsUnreachableWhenTurningOffThePlayerIsToldAndCountingStillStops() {
      this.env.installId = "c".repeat(32);
      this.relay.down = true;
      this.client.setEnabled(false);
      assertFalse(this.env.enabled);
      assertEquals("", this.env.installId);
      assertTrue(this.notices.get(0).contains("couldn't be reached"));
   }

   @Test
   void aFailedHelloBacksOffInsteadOfHammeringTheRelay() {
      long t = this.pastNotice();
      this.relay.down = true;
      this.client.step(t);
      assertEquals(1, this.relay.calls.size());
      assertEquals(0L, this.env.lastPing, "not counted as sent");

      this.client.step(t + 10_000L);
      assertEquals(1, this.relay.calls.size(), "waits before trying again");

      this.relay.down = false;
      this.client.step(t + 31_000L);
      assertEquals(2, this.relay.calls.size());
      assertEquals(t + 31_000L, this.env.lastPing);
   }

   @Test
   void aRefusedNameCheckDoesNotStoreANameAndRetriesMuchLater() {
      this.env.shareName = true;
      this.relay.identifyStatus = 401;
      long t = this.pastNotice();
      this.client.step(t);
      assertEquals("", this.env.namedAs);

      this.client.step(t + 120_000L);
      assertEquals(3, this.relay.calls.size(), "not retried within the hour");
   }

   @Test
   void theStatusLineSaysWhatIsHappening() {
      assertTrue(this.client.status().contains("ON"));
      assertTrue(this.client.status().contains("username: OFF"));
      this.env.shareName = true;
      assertTrue(this.client.status().contains("username: ON"));
      this.env.enabled = false;
      assertTrue(this.client.status().contains("OFF. Nothing is sent"));
   }
}
