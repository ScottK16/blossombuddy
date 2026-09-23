package org.blossomsuite.core.emote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.blossomsuite.core.net.SuiteHttp;
import org.blossomsuite.core.presence.PresenceClient;
import org.blossomsuite.core.xchat.XChatClient;
import org.junit.jupiter.api.Test;

/**
 * Two real players on the same realm: one plays an emote and the other's client starts it on the right character, through a real relay
 * over HTTP. Skipped unless RELAY_EMOTE_URL is set. The relay needs a stand-in for Mojang that accepts "Alice" and "Bob".
 */
class RelayEmoteIntegrationTest {
   private static XChatClient.Relay overHttp(SuiteHttp http) {
      return (path, json) -> {
         try {
            var response = http.postJson(path, json).get(8, TimeUnit.SECONDS);
            return new XChatClient.Reply(response.statusCode(), response.body());
         } catch (Exception e) {
            throw new XChatClient.ChatException(e.toString(), 1000L);
         }
      };
   }

   private static EmoteClient.Presence link(PresenceClient p) {
      return new EmoteClient.Presence() {
         @Override
         public String token() {
            return p.sessionToken();
         }

         @Override
         public int othersOnMyRealm() {
            return p.othersOnMyRealm();
         }
      };
   }

   private static EmoteClient.Env env(UUID self) {
      return new EmoteClient.Env() {
         @Override public boolean enabled() { return true; }
         @Override public void setEnabled(boolean on) { }
         @Override public boolean showOthers() { return true; }
         @Override public void setShowOthers(boolean on) { }
         @Override public boolean share() { return true; }
         @Override public void setShare(boolean on) { }
         @Override public boolean canRun() { return true; }
         @Override public UUID selfId() { return self; }
         @Override public boolean wantsToMove() { return false; }
      };
   }

   /** A player list setup for a player on the given realm. */
   private static PresenceClient.Env presenceEnv(String realm) {
      return new PresenceClient.Env() {
         @Override public boolean enabled() { return true; }
         @Override public void setEnabled(boolean on) { }
         @Override public boolean tabSymbol() { return true; }
         @Override public void setTabSymbol(boolean on) { }
         @Override public boolean noticeShown() { return true; }
         @Override public void setNoticeShown(boolean shown) { }
         @Override public boolean canRun() { return true; }
         @Override public String realm() { return realm; }
         @Override public boolean vanished() { return false; }
      };
   }

   @Test
   void oneClientsEmoteStartsOnTheirCharacterInTheOtherClient() throws Exception {
      String url = System.getenv("RELAY_EMOTE_URL");
      assumeTrue(url != null && !url.isBlank(), "set RELAY_EMOTE_URL to run this against a relay");

      SuiteHttp http = new SuiteHttp(url, "", "");
      XChatClient.Relay relay = overHttp(http);
      PresenceClient alice = new PresenceClient(relay, s -> "Alice", presenceEnv("cherry"), t -> { }, Runnable::run, System::currentTimeMillis);
      PresenceClient bob = new PresenceClient(relay, s -> "Bob", presenceEnv("cherry"), t -> { }, Runnable::run, System::currentTimeMillis);
      long t0 = System.currentTimeMillis();
      alice.step(t0);
      bob.step(t0);
      assertEquals(PresenceClient.State.READY, alice.state(), alice.status());
      assertEquals(PresenceClient.State.READY, bob.state(), bob.status());

      UUID aliceId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
      UUID bobId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
      EmoteState aliceState = new EmoteState();
      EmoteState bobState = new EmoteState();
      ArrayList<String> notices = new ArrayList<>();
      EmoteClient aliceEmotes = new EmoteClient(relay, link(alice), env(aliceId), notices::add, aliceState, Runnable::run, System::currentTimeMillis);
      EmoteClient bobEmotes = new EmoteClient(relay, link(bob), env(bobId), notices::add, bobState, Runnable::run, System::currentTimeMillis);

      bobEmotes.step(System.currentTimeMillis()); // Bob's first look: only finds where the feed is
      assertTrue(aliceEmotes.play("dance"), notices.toString());
      assertTrue(aliceState.isActive(aliceId, System.currentTimeMillis()), "Alice dances at once");

      Thread.sleep(1100);
      bobEmotes.step(System.currentTimeMillis());
      assertNotNull(bobState.activeFor(aliceId, System.currentTimeMillis()), "Bob sees Alice dance: " + notices);
      assertEquals(Emote.DANCE, bobState.activeFor(aliceId, System.currentTimeMillis()).emote());
      assertNotNull(bobState.poseFor(aliceId, System.currentTimeMillis()));
      assertFalse(bobState.isActive(bobId, System.currentTimeMillis()), "and Bob is not dancing");

      aliceEmotes.stop();
      Thread.sleep(1100);
      bobEmotes.step(System.currentTimeMillis());
      assertFalse(bobState.isActive(aliceId, System.currentTimeMillis()), "Alice's stop reaches Bob");
      assertTrue(notices.isEmpty(), "and nobody was told about a problem: " + notices);
   }
}
