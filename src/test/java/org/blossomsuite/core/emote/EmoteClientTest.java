package org.blossomsuite.core.emote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.blossomsuite.core.util.JsonUtil;
import org.blossomsuite.core.xchat.XChatClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The emote client, with a fake relay and a fake player list. */
class EmoteClientTest {
   private static final long T = 5_000_000L;
   private static final UUID ME = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
   private static final UUID BOB = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
   private static final String BOB_HEX = "b".repeat(32);
   private static final String ME_HEX = "a".repeat(32);

   static final class FakeEnv implements EmoteClient.Env {
      boolean enabled = true;
      boolean showOthers = true;
      boolean share = true;
      boolean canRun = true;
      boolean moving = false;

      @Override public boolean enabled() { return this.enabled; }
      @Override public void setEnabled(boolean on) { this.enabled = on; }
      @Override public boolean showOthers() { return this.showOthers; }
      @Override public void setShowOthers(boolean on) { this.showOthers = on; }
      @Override public boolean share() { return this.share; }
      @Override public void setShare(boolean on) { this.share = on; }
      @Override public boolean canRun() { return this.canRun; }
      @Override public UUID selfId() { return ME; }
      @Override public boolean wantsToMove() { return this.moving; }
   }

   static final class FakePresence implements EmoteClient.Presence {
      String token = "T1";
      int others = 1;

      @Override public String token() { return this.token; }
      @Override public int othersOnMyRealm() { return this.others; }
   }

   static final class FakeRelay implements XChatClient.Relay {
      final List<String[]> calls = new ArrayList<>();
      int playStatus = 200;
      int pollStatus = 200;
      boolean down = false;
      String events = "[]";
      long latestId = 0;

      @Override
      public XChatClient.Reply post(String path, String json) throws XChatClient.ChatException {
         this.calls.add(new String[]{path, json});
         if (this.down) {
            throw new XChatClient.ChatException("Can't reach the relay.", 30_000L);
         }

         return switch (path) {
            case "/v1/emote/play" -> new XChatClient.Reply(this.playStatus, "{\"id\":1}");
            case "/v1/emote/poll" -> new XChatClient.Reply(this.pollStatus, "{\"events\":" + this.events + ",\"latestId\":" + this.latestId + "}");
            default -> new XChatClient.Reply(404, "{}");
         };
      }

      JsonObject lastBody(String path) {
         for (int i = this.calls.size() - 1; i >= 0; i--) {
            if (this.calls.get(i)[0].equals(path)) {
               return JsonUtil.GSON.fromJson(this.calls.get(i)[1], JsonObject.class);
            }
         }

         throw new IllegalStateException("no call to " + path);
      }

      long count(String path) {
         return this.calls.stream().filter(c -> c[0].equals(path)).count();
      }
   }

   private FakeEnv env;
   private FakePresence presence;
   private FakeRelay relay;
   private EmoteState state;
   private List<String> notices;
   private long clock;
   private EmoteClient client;

   @BeforeEach
   void setUp() {
      this.env = new FakeEnv();
      this.presence = new FakePresence();
      this.relay = new FakeRelay();
      this.state = new EmoteState();
      this.notices = new ArrayList<>();
      this.clock = T;
      this.client = new EmoteClient(this.relay, this.presence, this.env, this.notices::add, this.state, Runnable::run, () -> this.clock);
   }

   private static String event(long id, String uuid, String type, String emote) {
      return "{\"id\":" + id + ",\"uuid\":\"" + uuid + "\",\"type\":\"" + type + "\",\"emote\":\"" + emote + "\"}";
   }

   // ------------------------------------------------------------------ playing your own

   @Test
   void yourOwnEmoteStartsAtOnceAndIsSentToTheRelayWithYourSession() {
      assertTrue(this.client.play("dance"));
      assertTrue(this.state.isActive(ME, T), "no waiting for the network");
      assertEquals("T1", this.relay.lastBody("/v1/emote/play").get("token").getAsString());
      assertEquals("dance", this.relay.lastBody("/v1/emote/play").get("emote").getAsString());
      assertEquals(2, this.relay.lastBody("/v1/emote/play").size(), "only the session and the emote name are sent");
   }

   @Test
   void unknownEmotesAreRefusedWithAHelpfulMessage() {
      assertFalse(this.client.play("moonwalk"));
      assertTrue(this.notices.get(0).contains("wave, dance, cheer, clap"), this.notices.get(0));
      assertTrue(this.relay.calls.isEmpty());
      assertFalse(this.state.isActive(ME, T));
   }

   @Test
   void withEmotesOffNothingHappens() {
      this.env.enabled = false;
      assertFalse(this.client.play("wave"));
      assertTrue(this.notices.get(0).contains("Emotes are off"));
      assertTrue(this.relay.calls.isEmpty());
      assertFalse(this.state.isActive(ME, T));
   }

   @Test
   void ifYouDontShareYouStillDanceButNobodyIsTold() {
      this.env.share = false;
      assertTrue(this.client.play("wave"));
      assertTrue(this.state.isActive(ME, T));
      assertTrue(this.relay.calls.isEmpty());
   }

   @Test
   void withoutAConnectedPlayerListYouDanceAloneAndAreToldWhyOnlyOnce() {
      this.presence.token = null;
      this.client.play("wave");
      assertTrue(this.state.isActive(ME, T));
      assertTrue(this.relay.calls.isEmpty());
      assertTrue(this.notices.get(0).contains("player list"), this.notices.get(0));

      this.clock += 5_000L;
      this.client.play("dance");
      assertEquals(1, this.notices.size(), "not nagged again straight away");
      this.clock += 60_000L;
      this.client.play("cheer");
      assertEquals(2, this.notices.size(), "but reminded after a while");
   }

   @Test
   void aRefusedOrFailedSendTellsYouOthersDidNotSeeIt() {
      this.relay.playStatus = 429;
      this.client.play("wave");
      assertTrue(this.notices.get(0).contains("Slow down"));

      this.notices.clear();
      this.clock += 60_000L;
      this.relay.playStatus = 200;
      this.relay.down = true;
      this.client.play("dance");
      assertTrue(this.notices.get(0).contains("can't see your emotes right now"), this.notices.get(0));
      assertTrue(this.state.isActive(ME, this.clock), "you still dance");
   }

   @Test
   void aRefusalBecauseYouDoNotOwnItStopsItForYouTooNotJustOthers() {
      this.relay.playStatus = 403;
      this.client.play("dance");
      assertFalse(this.state.isActive(ME, T), "a 403 is a real refusal, not a network hiccup, so it stops for us too, not just others");
      assertTrue(this.notices.get(0).contains("don't have that emote"), this.notices.get(0));
   }

   @Test
   void stoppingEndsItForYouAndTellsTheRelay() {
      this.client.play("dance");
      this.relay.calls.clear();
      this.client.stop();
      assertFalse(this.state.isActive(ME, T));
      assertEquals("stop", this.relay.lastBody("/v1/emote/play").get("emote").getAsString());

      this.relay.calls.clear();
      this.client.stop();
      assertTrue(this.relay.calls.isEmpty(), "nothing to stop, nothing sent");
   }

   @Test
   void walkingDoesNotEndAnEmoteYouCanDoOnTheMove() {
      this.client.play("dance");
      this.env.moving = true;
      this.client.clientTick(T + EmoteClient.MOVE_GRACE_MS + 50L);
      assertTrue(this.state.isActive(ME, T + 1000L));
      this.client.clientTick(T + 60_000L);
      assertTrue(this.state.isActive(ME, T + 60_000L), "and it is still going a minute later, well past its own length");
   }

   @Test
   void yourEmoteLoopsUntilYouStopIt() {
      this.client.play("wave");
      long wellPastItsLength = T + (long)(Emote.WAVE.durationSeconds() * 1000L) * 10L;
      assertTrue(this.state.isActive(ME, wellPastItsLength));
      this.client.stop();
      assertFalse(this.state.isActive(ME, wellPastItsLength));
   }

   @Test
   void pickingTheEmoteThatIsPlayingTurnsItOffButPickingAnotherSwitchesToIt() {
      this.client.toggle(Emote.DANCE);
      assertTrue(this.state.isActive(ME, T));
      this.client.toggle(Emote.WAVE);
      assertEquals(Emote.WAVE, this.state.activeFor(ME, T).emote());
      this.relay.calls.clear();
      this.client.toggle(Emote.WAVE);
      assertFalse(this.state.isActive(ME, T));
      assertEquals("stop", this.relay.lastBody("/v1/emote/play").get("emote").getAsString());
   }

   @Test
   void walkingEndsTheSittingAndLyingEmotesButNotInTheFirstMoments() {
      this.client.play("feetup");
      this.env.moving = true;
      this.client.clientTick(T + 100L);
      assertTrue(this.state.isActive(ME, T + 100L), "the grace period, so opening the wheel with a key held doesn't cancel it");

      this.relay.calls.clear();
      this.client.clientTick(T + EmoteClient.MOVE_GRACE_MS + 50L);
      assertFalse(this.state.isActive(ME, T + 1000L));
      assertEquals("stop", this.relay.lastBody("/v1/emote/play").get("emote").getAsString());
   }

   @Test
   void standingStillKeepsTheEmoteGoing() {
      this.client.play("dance");
      this.client.clientTick(T + 3000L);
      assertTrue(this.state.isActive(ME, T + 3000L));
   }

   // ------------------------------------------------------------------ seeing others

   @Test
   void theFirstLookOnlyFindsOutWhereTheFeedIsNowSoOldEmotesAreNotReplayed() {
      this.relay.latestId = 7;
      this.relay.events = "[" + event(6, BOB_HEX, "play", "dance") + "]";
      this.client.step(T);
      assertFalse(this.state.isActive(BOB, T));
      assertEquals(0, this.relay.lastBody("/v1/emote/poll").keySet().stream().filter("since"::equals).count(), "no 'since' on the first poll");

      this.clock += 2000L;
      this.relay.events = "[]";
      this.client.step(T + 2000L);
      assertEquals(7, this.relay.lastBody("/v1/emote/poll").get("since").getAsLong());
   }

   @Test
   void anotherPlayersEmotePlaysOnTheirCharacterAndStopsWhenTheyStop() {
      this.client.step(T); // first look
      this.relay.latestId = 8;
      this.relay.events = "[" + event(8, BOB_HEX, "play", "clap") + "]";
      this.client.step(T + 1000L);
      assertEquals(Emote.CLAP, this.state.activeFor(BOB, T + 1500L).emote());
      assertEquals(T + 1000L, this.state.activeFor(BOB, T + 1500L).startMs(), "timed from when we heard about it");

      this.relay.latestId = 9;
      this.relay.events = "[" + event(9, BOB_HEX, "stop", "") + "]";
      this.client.step(T + 2000L);
      assertFalse(this.state.isActive(BOB, T + 2100L));
   }

   @Test
   void yourOwnEventsComingBackAreIgnored() {
      this.client.step(T);
      this.relay.latestId = 3;
      this.relay.events = "[" + event(3, ME_HEX, "stop", "") + "]";
      this.client.play("wave");
      this.client.step(T + 1000L);
      assertTrue(this.state.isActive(ME, T + 1100L), "a stop for us from the relay does not cancel our own emote");
   }

   @Test
   void garbageEventsAreIgnoredWithoutBreakingTheOthers() {
      this.client.step(T);
      this.relay.latestId = 5;
      this.relay.events = "[null," + event(2, "nonsense", "play", "wave") + "," + event(3, BOB_HEX, "play", "moonwalk") + "," + event(4, BOB_HEX, "explode", "wave") + "," + event(5, BOB_HEX, "play", "cheer") + "]";
      this.client.step(T + 1000L);
      assertEquals(Emote.CHEER, this.state.activeFor(BOB, T + 1100L).emote());
   }

   @Test
   void itOnlyLooksForEmotesWhenThereIsAReasonToAndAsOftenAsTheSituationNeeds() {
      this.presence.others = 1;
      this.client.step(T);
      this.client.step(T + 500L);
      assertEquals(1, this.relay.count("/v1/emote/poll"), "not more than once a second");
      this.client.step(T + EmoteClient.POLL_BUSY_MS);
      assertEquals(2, this.relay.count("/v1/emote/poll"));

      this.presence.others = 0;
      this.client.step(T + 2000L); // this poll sees nobody else around, so the next one is a long way off
      this.client.step(T + 5000L);
      assertEquals(3, this.relay.count("/v1/emote/poll"), "quiet: every ten seconds");
      this.client.step(T + 2000L + EmoteClient.POLL_QUIET_MS);
      assertEquals(4, this.relay.count("/v1/emote/poll"));
   }

   @Test
   void nothingIsFetchedWhenSwitchedOffOrNotConnectedOrNotInTheRightPlace() {
      this.env.showOthers = false;
      this.client.step(T);
      this.env.showOthers = true;
      this.env.enabled = false;
      this.client.step(T + 1000L);
      this.env.enabled = true;
      this.env.canRun = false;
      this.client.step(T + 2000L);
      this.env.canRun = true;
      this.presence.token = null;
      this.client.step(T + 3000L);
      assertTrue(this.relay.calls.isEmpty());
   }

   @Test
   void aRelayThatForgotUsIsGivenTimeToSignInAgainAndTheFeedRestarts() {
      this.relay.latestId = 4;
      this.client.step(T);
      this.relay.pollStatus = 401;
      this.client.step(T + 1000L);
      long callsBefore = this.relay.count("/v1/emote/poll");
      this.client.step(T + 2000L);
      assertEquals(callsBefore, this.relay.count("/v1/emote/poll"), "waits before asking again");

      this.relay.pollStatus = 200;
      this.client.step(T + 7000L);
      assertEquals(0, this.relay.lastBody("/v1/emote/poll").keySet().stream().filter("since"::equals).count(), "starts from 'now' again");
   }

   @Test
   void anUnreachableRelayBacksOff() {
      this.relay.down = true;
      this.client.step(T);
      this.client.step(T + 1000L);
      assertEquals(1, this.relay.calls.size());
      this.relay.down = false;
      this.client.step(T + 31_000L);
      assertEquals(2, this.relay.calls.size());
   }

   @Test
   void switchingOffEmotesOrHidingOthersClearsWhatIsPlaying() {
      this.state.start(BOB, Emote.WAVE, T);
      this.state.start(ME, Emote.WAVE, T);
      this.client.setShowOthers(false);
      assertFalse(this.env.showOthers);
      assertFalse(this.state.isActive(BOB, T + 100L));

      this.client.setEnabled(false);
      assertFalse(this.env.enabled);
      assertFalse(this.state.isActive(ME, T + 100L));

      this.client.setShare(false);
      assertFalse(this.env.share);
   }

   @Test
   void theEmoteNamesAreListedForHelp() {
      assertTrue(EmoteClient.emoteNames().startsWith("wave, dance, cheer, clap"), EmoteClient.emoteNames());
      assertTrue(EmoteClient.emoteNames().contains("feetup"));
   }
}
