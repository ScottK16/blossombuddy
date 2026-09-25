package org.blossomsuite.core.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.blossomsuite.core.util.JsonUtil;
import org.blossomsuite.core.xchat.XChatClient;
import org.junit.jupiter.api.Test;

class VerifyClientTest {
   static final class FakeRelay implements XChatClient.Relay {
      final List<String[]> calls = new ArrayList<>();
      int verifyStatus = 200;
      String verifyBody = "{\"code\":\"K7M2-9QX4\",\"expiresInSeconds\":600}";

      @Override
      public XChatClient.Reply post(String path, String json) {
         this.calls.add(new String[]{path, json});
         if (path.equals("/v1/chat/challenge")) {
            return new XChatClient.Reply(200, "{\"challengeId\":\"c1\",\"serverId\":\"s1\"}");
         }

         return new XChatClient.Reply(this.verifyStatus, this.verifyBody);
      }

      JsonObject lastBody() {
         return JsonUtil.GSON.fromJson(this.calls.get(this.calls.size() - 1)[1], JsonObject.class);
      }
   }

   private static XChatClient.Joiner joiner() {
      return serverId -> "Alice";
   }

   @Test
   void aCodeComesBackWithHowToUseIt() throws Exception {
      FakeRelay relay = new FakeRelay();
      VerifyClient.Result r = new VerifyClient(relay, joiner()).start();
      assertTrue(r.ok());
      assertTrue(r.message().contains("K7M2-9QX4"), r.message());
      assertTrue(r.message().contains("10 minutes"), r.message());
      assertEquals("/v1/verify/start", relay.calls.get(1)[0]);
   }

   @Test
   void theRequestCarriesTheChallengeAndTheAccountNameAndNothingElse() throws Exception {
      FakeRelay relay = new FakeRelay();
      new VerifyClient(relay, joiner()).start();
      JsonObject body = relay.lastBody();
      assertEquals("c1", body.get("challengeId").getAsString());
      assertEquals("Alice", body.get("name").getAsString());
      assertEquals(2, body.size());
   }

   @Test
   void anUnconfirmedAccountGetsAFriendlyMessage() throws Exception {
      FakeRelay relay = new FakeRelay();
      relay.verifyStatus = 401;
      VerifyClient.Result r = new VerifyClient(relay, joiner()).start();
      assertFalse(r.ok());
      assertEquals("Couldn't verify your Minecraft account.", r.message());
   }

   @Test
   void garbageOnA200IsAFailureNotACrash() throws Exception {
      FakeRelay relay = new FakeRelay();
      relay.verifyBody = "not json";
      assertFalse(new VerifyClient(relay, joiner()).start().ok());
   }

   @Test
   void removingSaysWhetherThereWasAnythingToRemove() throws Exception {
      FakeRelay relay = new FakeRelay();
      relay.verifyBody = "{\"ok\":true,\"removed\":true}";
      assertEquals("Your Discord link is removed.", new VerifyClient(relay, joiner()).remove().message());
      relay.verifyBody = "{\"ok\":true,\"removed\":false}";
      assertEquals("You didn't have a Discord link.", new VerifyClient(relay, joiner()).remove().message());
      assertEquals("/v1/verify/remove", relay.calls.get(relay.calls.size() - 1)[0]);
   }
}
