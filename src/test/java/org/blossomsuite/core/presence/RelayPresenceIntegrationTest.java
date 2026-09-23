package org.blossomsuite.core.presence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.blossomsuite.core.net.SuiteHttp;
import org.blossomsuite.core.xchat.XChatClient;
import org.junit.jupiter.api.Test;

/**
 * Two real player list clients talking through a real relay over HTTP. Skipped unless RELAY_PRESENCE_URL is set. The relay needs
 * a stand-in for Mojang's login check that accepts the names "Alice" and "Bob" (this machine cannot sign in to real accounts).
 */
class RelayPresenceIntegrationTest {
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

   @Test
   void aPlayerOnCherryAndAPlayerOnTulipSeeEachOtherAndDisappearWhenTheyLeave() throws Exception {
      String url = System.getenv("RELAY_PRESENCE_URL");
      assumeTrue(url != null && !url.isBlank(), "set RELAY_PRESENCE_URL to run this against a relay");

      SuiteHttp http = new SuiteHttp(url, "", "");
      PresenceClientTest.FakeEnv aliceEnv = new PresenceClientTest.FakeEnv();
      aliceEnv.realm = "cherry";
      PresenceClientTest.FakeEnv bobEnv = new PresenceClientTest.FakeEnv();
      bobEnv.realm = "tulip";
      List<String> notices = new ArrayList<>();
      PresenceClient alice = new PresenceClient(overHttp(http), s -> "Alice", aliceEnv, notices::add, Runnable::run, System::currentTimeMillis);
      PresenceClient bob = new PresenceClient(overHttp(http), s -> "Bob", bobEnv, notices::add, Runnable::run, System::currentTimeMillis);

      long t = System.currentTimeMillis();
      alice.step(t);
      bob.step(t);
      assertEquals(PresenceClient.State.READY, alice.state(), alice.status());
      assertEquals(PresenceClient.State.READY, bob.state(), bob.status());
      assertEquals(2, bob.players().size(), "Bob sees both of them: " + bob.players());

      Thread.sleep(5500); // the relay lets one player send a heartbeat about every five seconds
      alice.step(t + PresenceClient.UPDATE_EVERY_MS + 1);
      List<String> seen = alice.players().stream().map(p -> p.realm() + ":" + p.name()).toList();
      assertEquals(List.of("cherry:Alice", "tulip:Bob"), seen);
      assertTrue(alice.marks(bob.players().get(1).uuid(), "Bob"));

      bob.setEnabled(false); // leaves at once
      Thread.sleep(5500);
      alice.step(t + PresenceClient.UPDATE_EVERY_MS * 2 + 2);
      assertEquals(List.of("cherry:Alice"), alice.players().stream().map(p -> p.realm() + ":" + p.name()).toList());
   }
}
