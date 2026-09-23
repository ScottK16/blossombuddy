package org.blossomsuite.core.xchat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.blossomsuite.core.net.SuiteHttp;
import org.junit.jupiter.api.Test;

/**
 * Two real mod clients talking through a real relay over HTTP. Skipped unless RELAY_CHAT_URL is set. The relay has to be
 * started with a stand-in for Mojang's login check (this machine can't sign in to real accounts), for example:
 * <pre>
 * cd relay &amp;&amp; node -e "const {createApp}=require('./server');const {parseRealms}=require('./vote-store');
 *   const ids={Alice:'a'.repeat(32),Bob:'b'.repeat(32)};
 *   createApp({realms:parseRealms('cherry:Cherry,tulip:Tulip'),ttlMs:9e4,minReporters:1,ratePerMinute:30,chatEnabled:true,
 *   chatRatePerMinute:1000,banned:'',bannedFile:'/none'},()=>Date.now(),{verifier:async n=>ids[n]?{id:ids[n],name:n}:null})
 *   .server.listen(8792,'127.0.0.1')"
 * </pre>
 * then {@code RELAY_CHAT_URL=http://127.0.0.1:8792 ./gradlew test}.
 */
class RelayChatIntegrationTest {
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

   private static XChatClient.Env env(String realm) {
      return new XChatClient.Env() {
         @Override
         public boolean enabled() {
            return true;
         }

         @Override
         public boolean canRun() {
            return true;
         }

         @Override
         public String realm() {
            return realm;
         }

         @Override
         public Collection<String> muted() {
            return List.of();
         }
      };
   }

   private static final class Inbox implements XChatClient.Sink {
      final List<XChatModels.Message> messages = new ArrayList<>();
      final List<String> notices = new ArrayList<>();

      @Override
      public void message(XChatModels.Message message, boolean own) {
         this.messages.add(message);
      }

      @Override
      public void notice(String text) {
         this.notices.add(text);
      }
   }

   @Test
   void aPlayerOnCherryAndAPlayerOnTulipTalkThroughTheRelay() throws Exception {
      String url = System.getenv("RELAY_CHAT_URL");
      assumeTrue(url != null && !url.isBlank(), "set RELAY_CHAT_URL to run this against a relay");

      SuiteHttp http = new SuiteHttp(url, "", "");
      Inbox aliceInbox = new Inbox();
      Inbox bobInbox = new Inbox();
      XChatClient alice = new XChatClient(overHttp(http), serverId -> "Alice", env("cherry"), aliceInbox, System::currentTimeMillis);
      XChatClient bob = new XChatClient(overHttp(http), serverId -> "Bob", env("tulip"), bobInbox, System::currentTimeMillis);

      alice.step(System.currentTimeMillis());
      bob.step(System.currentTimeMillis());
      assertEquals(XChatClient.State.READY, alice.state(), alice.status());
      assertEquals(XChatClient.State.READY, bob.state(), bob.status());

      alice.send("hello from Cherry", System.currentTimeMillis());
      assertEquals(1, aliceInbox.messages.size(), "the sender sees their own line straight away: " + aliceInbox.notices);

      Thread.sleep(1000); // the relay lets one player poll about once a second
      bob.step(System.currentTimeMillis());
      assertEquals(1, bobInbox.messages.size(), bobInbox.notices.toString());
      XChatModels.Message m = bobInbox.messages.get(0);
      assertEquals("Alice", m.name);
      assertEquals("cherry", m.realm);
      assertEquals("hello from Cherry", m.text);
      assertTrue(XChatText.format(m).getString().startsWith("[Cherry] Alice:"));

      Thread.sleep(1000);
      alice.step(System.currentTimeMillis());
      assertEquals(1, aliceInbox.messages.size(), "and does not get their own message a second time");

      bob.send("hi Cherry, it's Tulip", System.currentTimeMillis());
      Thread.sleep(1000);
      alice.step(System.currentTimeMillis());
      assertEquals(2, aliceInbox.messages.size());
      assertEquals("[Tulip] Bob: hi Cherry, it's Tulip", XChatText.format(aliceInbox.messages.get(1)).getString());
   }
}
