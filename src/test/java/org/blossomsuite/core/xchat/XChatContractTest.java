package org.blossomsuite.core.xchat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.blossomsuite.core.util.JsonUtil;
import org.junit.jupiter.api.Test;

/** The mod and relay/chat.js agree on the JSON they exchange. */
class XChatContractTest {
   // exactly the shapes relay/server.js produces
   private static final String CHALLENGE = "{\"challengeId\":\"0a1b2c3d4e5f6a7b8c9d0e1f\",\"serverId\":\"a4f0c1d2e3b4a5968778695a4b3c2d1e0f9e8d7c\"}";
   private static final String AUTH = "{\"token\":\"tKn_-abc123\",\"name\":\"Alice\",\"uuid\":\"aaaaaaaabbbbccccddddeeeeeeeeeeee\",\"expiresInSeconds\":21600}";
   private static final String MESSAGE = "{\"id\":3,\"ts\":1789990000000,\"uuid\":\"aaaaaaaabbbbccccddddeeeeeeeeeeee\",\"name\":\"Alice\",\"realm\":\"cherry\",\"text\":\"hello\"}";
   private static final String POLL = "{\"messages\":[" + MESSAGE + "],\"latestId\":3}";

   @Test
   void theModReadsWhatTheRelaySends() {
      XChatModels.Challenge c = JsonUtil.GSON.fromJson(CHALLENGE, XChatModels.Challenge.class);
      assertEquals("0a1b2c3d4e5f6a7b8c9d0e1f", c.challengeId);
      assertEquals(40, c.serverId.length());

      XChatModels.Auth a = JsonUtil.GSON.fromJson(AUTH, XChatModels.Auth.class);
      assertEquals("tKn_-abc123", a.token);
      assertEquals("Alice", a.name);
      assertEquals(21600, a.expiresInSeconds);

      XChatModels.Poll p = JsonUtil.GSON.fromJson(POLL, XChatModels.Poll.class);
      assertEquals(3L, p.latestId);
      XChatModels.Message m = p.messages.get(0);
      assertEquals(3L, m.id);
      assertEquals(1789990000000L, m.ts);
      assertEquals("cherry", m.realm);
      assertEquals("hello", m.text);
      assertEquals(m.id, JsonUtil.GSON.fromJson(MESSAGE, XChatModels.Message.class).id, "a sent message comes back in the same shape");
   }

   @Test
   void theModSendsWhatTheRelayChecks() {
      JsonObject auth = JsonParser.parseString(JsonUtil.GSON.toJson(new XChatModels.AuthRequest("c1", "Alice"))).getAsJsonObject();
      assertEquals(java.util.Set.of("challengeId", "name"), auth.keySet(), "nothing else, in particular no token");

      JsonObject send = JsonParser.parseString(JsonUtil.GSON.toJson(new XChatModels.SendRequest("T", "hi", "cherry"))).getAsJsonObject();
      assertEquals(java.util.Set.of("token", "message", "realm"), send.keySet());

      JsonObject first = JsonParser.parseString(JsonUtil.GSON.toJson(new XChatModels.PollRequest("T", null))).getAsJsonObject();
      assertFalse(first.has("since"), "no 'since' on the first poll means 'just tell me where the chat is'");
      JsonObject later = JsonParser.parseString(JsonUtil.GSON.toJson(new XChatModels.PollRequest("T", 5L))).getAsJsonObject();
      assertEquals(5, later.get("since").getAsInt());
   }

   @Test
   void errorsAreReadWhenTheRelayGivesOne() {
      assertEquals("slow down", XChatClient.errorOf(new XChatClient.Reply(429, "{\"error\":\"slow down\"}")));
      assertTrue(XChatClient.errorOf(new XChatClient.Reply(502, "<html>bad gateway</html>")).contains("502"));
      assertTrue(XChatClient.errorOf(new XChatClient.Reply(500, "")).contains("500"));
   }
}
