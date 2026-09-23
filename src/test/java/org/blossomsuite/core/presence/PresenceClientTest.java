package org.blossomsuite.core.presence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.blossomsuite.core.util.JsonUtil;
import org.blossomsuite.core.xchat.XChatClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The player list client, with a fake relay and a fake Mojang. */
class PresenceClientTest {
   private static final long T = 1_000_000L;
   private static final String ALICE = "a".repeat(32);
   private static final String BOB = "b".repeat(32);
   private static final UUID ALICE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
   private static final UUID BOB_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

   static final class FakeEnv implements PresenceClient.Env {
      boolean enabled = true;
      boolean tabSymbol = true;
      boolean canRun = true;
      String realm = "cherry";
      boolean noticeShown = true;
      boolean vanished = false;

      @Override public boolean enabled() { return this.enabled; }
      @Override public void setEnabled(boolean on) { this.enabled = on; }
      @Override public boolean tabSymbol() { return this.tabSymbol; }
      @Override public void setTabSymbol(boolean on) { this.tabSymbol = on; }
      @Override public boolean canRun() { return this.canRun; }
      @Override public String realm() { return this.realm; }
      @Override public boolean noticeShown() { return this.noticeShown; }
      @Override public void setNoticeShown(boolean shown) { this.noticeShown = shown; }
      @Override public boolean vanished() { return this.vanished; }
   }

   static final class FakeRelay implements XChatClient.Relay {
      final List<String[]> calls = new ArrayList<>();
      int joinStatus = 200;
      int updateStatus = 200;
      boolean down = false;
      String players = "{\"players\":[{\"name\":\"Alice\",\"uuid\":\"" + ALICE + "\",\"realm\":\"cherry\"},{\"name\":\"Bob\",\"uuid\":\"" + BOB + "\",\"realm\":\"tulip\"}]}";

      @Override
      public XChatClient.Reply post(String path, String json) throws XChatClient.ChatException {
         this.calls.add(new String[]{path, json});
         if (this.down) {
            throw new XChatClient.ChatException("Can't reach the relay.", 30_000L);
         }

         return switch (path) {
            case "/v1/chat/challenge" -> new XChatClient.Reply(200, "{\"challengeId\":\"c1\",\"serverId\":\"s1\"}");
            case "/v1/presence/join" -> new XChatClient.Reply(this.joinStatus, "{\"token\":\"T1\",\"name\":\"Alice\",\"uuid\":\"" + ALICE + "\",\"expiresInSeconds\":21600}");
            case "/v1/presence/update" -> new XChatClient.Reply(this.updateStatus, this.players);
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
   private PresenceClient client;

   @BeforeEach
   void setUp() {
      this.env = new FakeEnv();
      this.relay = new FakeRelay();
      this.notices = new ArrayList<>();
      this.joined = new ArrayList<>();
      XChatClient.Joiner joiner = serverId -> {
         this.joined.add(serverId);
         return "Alice";
      };
      this.client = new PresenceClient(this.relay, joiner, this.env, this.notices::add, Runnable::run, () -> T);
   }

   @Test
   void itProvesTheAccountThenJoinsThenSaysWhereYouAreAndReadsTheList() {
      this.client.step(T);
      assertEquals(List.of("/v1/chat/challenge", "/v1/presence/join", "/v1/presence/update"), this.relay.paths());
      assertEquals(List.of("s1"), this.joined, "Mojang is told the account is joining with the relay's code");

      JsonObject join = this.relay.bodyOf("/v1/presence/join");
      assertEquals("c1", join.get("challengeId").getAsString());
      assertEquals("Alice", join.get("name").getAsString());
      assertEquals("cherry", join.get("realm").getAsString());
      assertEquals(3, join.size(), "nothing else is sent: " + join);

      JsonObject update = this.relay.bodyOf("/v1/presence/update");
      assertEquals("T1", update.get("token").getAsString());
      assertEquals("cherry", update.get("realm").getAsString());
      assertEquals(2, update.size());

      assertEquals(PresenceClient.State.READY, this.client.state());
      assertEquals(List.of(new PresenceClient.Player("Alice", ALICE_ID, "cherry"), new PresenceClient.Player("Bob", BOB_ID, "tulip")), this.client.players());
      assertTrue(this.client.status().contains("2 players"));
   }

   @Test
   void theHeartbeatIsSentEveryHalfMinuteAndOnlyThat() {
      this.client.step(T);
      this.client.step(T + PresenceClient.UPDATE_EVERY_MS - 1000L);
      assertEquals(3, this.relay.calls.size(), "not yet");

      this.client.step(T + PresenceClient.UPDATE_EVERY_MS);
      assertEquals(List.of("/v1/chat/challenge", "/v1/presence/join", "/v1/presence/update", "/v1/presence/update"), this.relay.paths(), "no second sign-in");
   }

   @Test
   void theRealmIsSentInLowerCase() {
      this.env.realm = "Cherry";
      this.client.step(T);
      assertEquals("cherry", this.relay.bodyOf("/v1/presence/update").get("realm").getAsString());
   }

   @Test
   void nothingIsSentWhileItIsOffOrTheGameIsNotInTheRightPlace() {
      this.env.enabled = false;
      this.client.step(T);
      assertTrue(this.relay.calls.isEmpty());
      assertTrue(this.joined.isEmpty(), "Mojang is not contacted either");
      assertTrue(this.client.status().contains("Off"));

      this.env.enabled = true;
      this.env.canRun = false;
      this.client.step(T + 100_000L);
      assertTrue(this.relay.calls.isEmpty());
   }

   @Test
   void turningItOnExplainsWhatIsShown() {
      this.env.enabled = false;
      this.client.setEnabled(true);
      assertTrue(this.env.enabled);
      assertEquals(1, this.notices.size());
      assertTrue(this.notices.get(0).contains("Minecraft name"), this.notices.get(0));
      assertTrue(this.notices.get(0).contains("/buddy who off"));
   }

   @Test
   void turningItOffLeavesTheListAtOnceAndForgetsEveryone() {
      this.client.step(T);
      this.relay.calls.clear();
      this.client.setEnabled(false);

      assertFalse(this.env.enabled);
      assertEquals(List.of("/v1/presence/leave"), this.relay.paths());
      assertEquals("T1", this.relay.bodyOf("/v1/presence/leave").get("token").getAsString());
      assertTrue(this.client.players().isEmpty(), "the list is gone from the screen too");
      assertFalse(this.client.marks(ALICE_ID, "Alice"));
      assertEquals(PresenceClient.State.OFF, this.client.state());
      assertTrue(this.notices.get(0).contains("no longer appear"));

      this.client.step(T + 500_000L);
      assertEquals(1, this.relay.calls.size(), "and nothing more is sent");
   }

   @Test
   void turningItOffWithoutEverHavingJoinedContactsNoOne() {
      this.client.setEnabled(false);
      assertTrue(this.relay.calls.isEmpty());
   }

   @Test
   void ifTheRelayForgotUsItJoinsAgain() {
      this.client.step(T);
      this.relay.updateStatus = 401;
      this.client.step(T + PresenceClient.UPDATE_EVERY_MS);
      this.relay.updateStatus = 200;
      this.client.step(T + PresenceClient.UPDATE_EVERY_MS + 1000L);
      assertEquals(2, this.relay.paths().stream().filter("/v1/presence/join"::equals).count(), "a new sign-in");
      assertEquals(PresenceClient.State.READY, this.client.state());
   }

   @Test
   void aBlockedPlayerIsToldAndLeftAlone() {
      this.relay.joinStatus = 403;
      this.client.step(T);
      assertEquals(PresenceClient.State.FAILED, this.client.state());
      assertTrue(this.client.status().contains("blocked"));

      this.client.step(T + 5 * 60_000L);
      assertEquals(2, this.relay.calls.size(), "no retry for ten minutes");
   }

   @Test
   void anUnreachableRelayBacksOffThenRecovers() {
      this.relay.down = true;
      this.client.step(T);
      assertEquals(PresenceClient.State.FAILED, this.client.state());
      assertEquals(1, this.relay.calls.size());

      this.client.step(T + 10_000L);
      assertEquals(1, this.relay.calls.size(), "waits");

      this.relay.down = false;
      this.client.step(T + 31_000L);
      assertEquals(PresenceClient.State.READY, this.client.state());
   }

   @Test
   void malformedEntriesFromTheRelayAreIgnored() {
      this.relay.players = "{\"players\":[{\"name\":\"Good\",\"uuid\":\"" + ALICE + "\",\"realm\":\"Cherry\"},"
         + "{\"name\":\"bad name!\",\"uuid\":\"" + BOB + "\",\"realm\":\"tulip\"},"
         + "{\"name\":\"NoUuid\",\"uuid\":\"xyz\",\"realm\":\"tulip\"},"
         + "{\"name\":\"NoRealm\",\"uuid\":\"" + BOB + "\",\"realm\":\"\"},"
         + "{\"name\":\"" + "x".repeat(17) + "\",\"uuid\":\"" + BOB + "\",\"realm\":\"lotus\"},"
         + "null,{}]}";
      this.client.step(T);
      assertEquals(List.of(new PresenceClient.Player("Good", ALICE_ID, "cherry")), this.client.players());
   }

   @Test
   void tabListPlayersAreMarkedByUuidOrByNameOnTheSameRealm() {
      this.client.step(T);
      assertTrue(this.client.marks(ALICE_ID, "SomeOtherName"), "by UUID");
      assertTrue(this.client.marks(UUID.randomUUID(), "alice"), "by name, any case, on the realm we are on");
      assertFalse(this.client.marks(UUID.randomUUID(), "bob"), "Bob is on Tulip: a name match only counts on our own realm");
      assertTrue(this.client.marks(BOB_ID, "Bob"), "but his UUID is enough");
      assertFalse(this.client.marks(UUID.randomUUID(), "Stranger"));
      assertFalse(this.client.marks(null, null));
   }

   @Test
   void theSymbolCanBeTurnedOffAndOnlyShowsWhileConnected() {
      assertFalse(this.client.marks(ALICE_ID, "Alice"), "not connected yet");
      this.client.step(T);
      assertTrue(this.client.marks(ALICE_ID, "Alice"));
      this.client.setTabSymbol(false);
      assertFalse(this.client.marks(ALICE_ID, "Alice"));
      assertEquals(1, this.relay.calls.stream().filter(c -> c[0].equals("/v1/presence/update")).count(), "changing the symbol sends nothing");
   }

   @Test
   void uuidsAreReadFromTheRelaysCompactForm() {
      assertEquals(ALICE_ID, PresenceClient.uuidOf(ALICE));
      assertEquals(UUID.fromString("01234567-89ab-cdef-0123-456789abcdef"), PresenceClient.uuidOf("0123456789ABCDEF0123456789abcdef"));
      for (String bad : new String[]{null, "", "abc", "g".repeat(32), ALICE + "0"}) {
         assertEquals(null, PresenceClient.uuidOf(bad), String.valueOf(bad));
      }
   }

   @Test
   void beingOnByDefaultIsAnnouncedBeforeAnythingIsSent() {
      this.env.noticeShown = false;
      this.client.step(T);
      assertEquals(1, this.notices.size());
      assertTrue(this.notices.get(0).contains("/buddy who off"), "says how to leave: " + this.notices.get(0));
      assertTrue(this.notices.get(0).contains("Minecraft name"), "says what is shown");
      assertTrue(this.env.noticeShown);
      assertTrue(this.relay.calls.isEmpty(), "nothing is sent before they could react");
      assertTrue(this.joined.isEmpty(), "Mojang is not contacted either");

      this.client.step(T + 1000L);
      assertTrue(this.relay.calls.isEmpty(), "and not straight after");

      this.client.step(T + PresenceClient.NOTICE_GRACE_MS + 1L);
      assertEquals(3, this.relay.calls.size(), "after the grace period it joins");
      assertEquals(1, this.notices.size(), "and the notice is said only once");
   }

   @Test
   void openingThePlayerListScreenEndsTheWaitAndConnectsAtOnce() {
      this.env.noticeShown = false;
      this.client.step(T);
      assertTrue(this.client.status().contains("Starting in a moment"), this.client.status());

      this.client.refreshSoon(); // what opening /buddy who does
      this.client.step(T + 1000L);
      assertEquals(3, this.relay.calls.size(), "joined without waiting out the minute");
      assertEquals(PresenceClient.State.READY, this.client.state());
      assertTrue(this.client.status().contains("players"), this.client.status());
   }

   @Test
   void theStatusSaysWhatIsHappeningDuringAndAfterTheWait() {
      this.env.noticeShown = false;
      this.client.step(T);
      assertTrue(this.client.status().contains("Starting in a moment"));
      this.client.step(T + PresenceClient.NOTICE_GRACE_MS + 1L);
      assertTrue(this.client.status().contains("2 players"));
   }

   @Test
   void switchingItOffDuringTheGracePeriodMeansNothingIsEverSent() {
      this.env.noticeShown = false;
      this.client.step(T);
      this.client.setEnabled(false);
      this.client.step(T + PresenceClient.NOTICE_GRACE_MS + 1L);
      assertTrue(this.relay.calls.isEmpty());
      assertTrue(this.joined.isEmpty());
   }

   @Test
   void turningItOnByHandSkipsTheFirstRunNotice() {
      this.env.enabled = false;
      this.env.noticeShown = false;
      this.client.setEnabled(true);
      assertTrue(this.env.noticeShown);
      this.client.step(T);
      assertEquals(1, this.notices.size(), "only the message about switching it on");
      assertEquals(3, this.relay.calls.size(), "and it joins straight away");
   }
}
