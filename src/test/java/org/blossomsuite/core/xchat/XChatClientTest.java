package org.blossomsuite.core.xchat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class XChatClientTest {
   private static final String CHALLENGE = "{\"challengeId\":\"c1\",\"serverId\":\"s1\"}";
   private static final String AUTH = "{\"token\":\"T1\",\"name\":\"Alice\",\"uuid\":\"aaaaaaaabbbbccccddddeeeeeeeeeeee\",\"expiresInSeconds\":21600}";
   private static final String SYNC = "{\"messages\":[],\"latestId\":5}";
   private static final String BOB_SAYS_HI = "{\"messages\":[{\"id\":6,\"ts\":1,\"uuid\":\"11111111222233334444555555555555\",\"name\":\"Bob\",\"realm\":\"tulip\",\"text\":\"hi from Tulip\"}],\"latestId\":6}";

   static final class FakeRelay implements XChatClient.Relay {
      final List<String[]> calls = new ArrayList<>();
      final Map<String, Deque<XChatClient.Reply>> script = new HashMap<>();

      void on(String path, int status, String body) {
         this.script.computeIfAbsent(path, k -> new ArrayDeque<>()).add(new XChatClient.Reply(status, body));
      }

      @Override
      public XChatClient.Reply post(String path, String json) throws XChatClient.ChatException {
         this.calls.add(new String[]{path, json});
         Deque<XChatClient.Reply> queue = this.script.get(path);
         if (queue == null || queue.isEmpty()) {
            throw new XChatClient.ChatException("nothing scripted for " + path, 1000L);
         }

         return queue.size() > 1 ? queue.poll() : queue.peek(); // the last answer repeats
      }

      long count(String path) {
         return this.calls.stream().filter(c -> c[0].equals(path)).count();
      }

      String lastBody(String path) {
         String body = null;
         for (String[] c : this.calls) {
            if (c[0].equals(path)) {
               body = c[1];
            }
         }

         return body;
      }
   }

   static final class FakeEnv implements XChatClient.Env {
      boolean enabled = true;
      boolean canRun = true;
      String realm = "cherry";
      final List<String> muted = new ArrayList<>();

      @Override
      public boolean enabled() {
         return this.enabled;
      }

      @Override
      public boolean canRun() {
         return this.canRun;
      }

      @Override
      public String realm() {
         return this.realm;
      }

      @Override
      public Collection<String> muted() {
         return this.muted;
      }
   }

   static final class FakeSink implements XChatClient.Sink {
      final List<XChatModels.Message> messages = new ArrayList<>();
      final List<Boolean> own = new ArrayList<>();
      final List<String> notices = new ArrayList<>();

      @Override
      public void message(XChatModels.Message message, boolean isOwn) {
         this.messages.add(message);
         this.own.add(isOwn);
      }

      @Override
      public void notice(String text) {
         this.notices.add(text);
      }
   }

   FakeRelay relay;
   FakeEnv env;
   FakeSink sink;
   List<String> joinedWith;
   XChatClient client;

   @BeforeEach
   void setUp() {
      this.relay = new FakeRelay();
      this.env = new FakeEnv();
      this.sink = new FakeSink();
      this.joinedWith = new ArrayList<>();
      this.relay.on("/v1/chat/challenge", 200, CHALLENGE);
      this.relay.on("/v1/chat/auth", 200, AUTH);
      this.client = new XChatClient(this.relay, serverId -> {
         this.joinedWith.add(serverId);
         return "Alice";
      }, this.env, this.sink, () -> 0L);
   }

   @Test
   void signsInThroughMojangAndTheRelayNeverSeesAnythingSecret() {
      this.relay.on("/v1/chat/poll", 200, SYNC);
      this.client.step(1000);

      assertEquals(List.of("s1"), this.joinedWith, "the account joins with the relay's code, on Mojang's side");
      assertEquals(XChatClient.State.READY, this.client.state());
      assertEquals("Connected as Alice.", this.client.status());
      String auth = this.relay.lastBody("/v1/chat/auth");
      assertTrue(auth.contains("\"challengeId\":\"c1\"") && auth.contains("\"name\":\"Alice\""), auth);
      for (String[] call : this.relay.calls) {
         assertFalse(call[1].toLowerCase().contains("accesstoken") || call[1].toLowerCase().contains("secret"), call[0] + " " + call[1]);
      }
   }

   @Test
   void theFirstPollOnlyCatchesUpAndLaterMessagesAreShown() {
      this.relay.on("/v1/chat/poll", 200, SYNC);
      this.client.step(1000);
      assertTrue(this.sink.messages.isEmpty(), "joining does not replay old chat");
      assertFalse(this.relay.lastBody("/v1/chat/poll").contains("since"), "the first poll asks only where the chat is");

      this.relay.script.get("/v1/chat/poll").clear();
      this.relay.on("/v1/chat/poll", 200, BOB_SAYS_HI);
      this.client.step(4000);
      assertEquals(1, this.sink.messages.size());
      assertEquals("Bob", this.sink.messages.get(0).name);
      assertEquals("hi from Tulip", this.sink.messages.get(0).text);
      assertFalse(this.sink.own.get(0));
      assertTrue(this.relay.lastBody("/v1/chat/poll").contains("\"since\":5"), "then it asks for what is newer than what it has");
   }

   @Test
   void yourOwnMessageIsShownOnceNotTwice() {
      this.relay.on("/v1/chat/poll", 200, SYNC);
      this.client.step(1000);
      this.relay.on("/v1/chat/send", 200, "{\"id\":6,\"ts\":2,\"uuid\":\"aaaaaaaabbbbccccddddeeeeeeeeeeee\",\"name\":\"Alice\",\"realm\":\"cherry\",\"text\":\"hello\"}");
      this.client.send("hello", 2000);
      assertEquals(1, this.sink.messages.size());
      assertTrue(this.sink.own.get(0));
      assertTrue(this.relay.lastBody("/v1/chat/send").contains("\"realm\":\"cherry\""), "the message says which realm it came from");

      this.relay.script.get("/v1/chat/poll").clear();
      this.relay.on("/v1/chat/poll", 200, "{\"messages\":[{\"id\":6,\"ts\":2,\"uuid\":\"aaaaaaaabbbbccccddddeeeeeeeeeeee\",\"name\":\"Alice\",\"realm\":\"cherry\",\"text\":\"hello\"}],\"latestId\":6}");
      this.client.step(5000);
      assertEquals(1, this.sink.messages.size(), "the relay's copy of your own message is not shown again");
   }

   @Test
   void mutedPlayersAreHiddenByNameOrUuid() {
      this.relay.on("/v1/chat/poll", 200, SYNC);
      this.client.step(1000);
      this.env.muted.add("BOB");
      this.relay.script.get("/v1/chat/poll").clear();
      this.relay.on("/v1/chat/poll", 200, BOB_SAYS_HI);
      this.client.step(4000);
      assertTrue(this.sink.messages.isEmpty());
   }

   @Test
   void aRefusedTokenMeansSigningInAgain() {
      this.relay.on("/v1/chat/poll", 200, SYNC);
      this.client.step(1000);
      assertEquals(1, this.relay.count("/v1/chat/challenge"));

      this.relay.script.get("/v1/chat/poll").clear();
      this.relay.on("/v1/chat/poll", 401, "{\"error\":\"sign in again\"}");
      this.client.step(4000); // the relay no longer knows this token
      this.relay.script.get("/v1/chat/poll").clear();
      this.relay.on("/v1/chat/poll", 200, SYNC);
      this.client.step(7000);
      assertEquals(2, this.relay.count("/v1/chat/challenge"), "a second sign-in happened");
      assertEquals(XChatClient.State.READY, this.client.state());
   }

   @Test
   void failingToVerifyBacksOffAndTellsThePlayerOnce() {
      this.relay.script.get("/v1/chat/auth").clear();
      this.relay.on("/v1/chat/auth", 401, "{\"error\":\"not verified\"}");
      this.client.step(1000);
      assertEquals(XChatClient.State.FAILED, this.client.state());
      assertTrue(this.client.status().contains("verify"), this.client.status());
      assertEquals(1, this.sink.notices.size());

      long callsBefore = this.relay.calls.size();
      this.client.step(30_000);
      assertEquals(callsBefore, this.relay.calls.size(), "it leaves the relay alone while backing off");

      this.client.step(70_000);
      assertTrue(this.relay.calls.size() > callsBefore, "and tries again afterwards");
      assertEquals(1, this.sink.notices.size(), "without repeating the same complaint");
   }

   @Test
   void nothingHappensWhileTheFeatureIsOffOrNoWorldIsOpen() {
      this.env.enabled = false;
      this.client.step(1000);
      assertEquals(0, this.relay.calls.size());
      assertEquals(XChatClient.State.OFF, this.client.state());

      this.env.enabled = true;
      this.env.canRun = false;
      this.client.step(2000);
      assertEquals(0, this.relay.calls.size());
   }

   @Test
   void sendingWhileOffExplainsHowToTurnItOn() {
      this.env.enabled = false;
      this.client.send("hello", 1000);
      assertEquals(0, this.relay.calls.size());
      assertTrue(this.sink.notices.get(0).contains("/buddy xchat on"), this.sink.notices.get(0));
   }

   @Test
   void whatYouSendIsCleanedAndEmptyIsRefused() {
      this.relay.on("/v1/chat/send", 200, "{\"id\":1,\"ts\":1,\"uuid\":\"u\",\"name\":\"Alice\",\"realm\":\"cherry\",\"text\":\"cred text\"}");
      this.client.send("§cred  \n text", 1000);
      assertTrue(this.relay.lastBody("/v1/chat/send").contains("\"message\":\"cred text\""), this.relay.lastBody("/v1/chat/send"));

      long before = this.relay.calls.size();
      this.client.send("   §  ", 2000);
      assertEquals(before, this.relay.calls.size());
      assertTrue(this.sink.notices.get(0).contains("Nothing to send"));
   }

   @Test
   void theRelaysReasonIsShownWhenASendIsRefused() {
      this.relay.on("/v1/chat/send", 429, "{\"error\":\"slow down\"}");
      this.client.send("hello", 1000);
      assertEquals("slow down", this.sink.notices.get(0));
   }

   @Test
   void anExpiredTokenIsRenewedOnceAndTheSendGoesThrough() {
      this.relay.on("/v1/chat/poll", 200, SYNC);
      this.client.step(1000);
      this.relay.on("/v1/chat/send", 401, "{\"error\":\"sign in again\"}");
      this.relay.on("/v1/chat/send", 200, "{\"id\":9,\"ts\":1,\"uuid\":\"u\",\"name\":\"Alice\",\"realm\":\"cherry\",\"text\":\"hello\"}");
      this.client.send("hello", 3000);
      assertEquals(2, this.relay.count("/v1/chat/challenge"), "signed in again");
      assertEquals(1, this.sink.messages.size());
      assertTrue(this.sink.notices.isEmpty());
   }

   @Test
   void aSendThatCannotSignInIsReportedOnce() {
      this.relay.script.get("/v1/chat/auth").clear();
      this.relay.on("/v1/chat/auth", 403, "{\"error\":\"blocked\"}");
      this.client.send("hello", 1000);
      assertEquals(1, this.sink.notices.size(), this.sink.notices.toString());
      assertTrue(this.sink.notices.get(0).contains("blocked"), this.sink.notices.get(0));
   }
}
