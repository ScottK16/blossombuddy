package org.blossomsuite.core.mapart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.blossomsuite.core.util.JsonUtil;
import org.blossomsuite.core.xchat.XChatClient;
import org.junit.jupiter.api.Test;

class MapArtClientTest {
   private static final String PROJECT_JSON =
      "{\"code\":\"7K4P9M\",\"name\":\"My House\",\"width\":2,\"height\":2,\"blocks\":[1,2,3,29],"
         + "\"materials\":[{\"colorId\":1,\"block\":\"Grass Block\",\"count\":1},{\"colorId\":29,\"block\":\"Black Wool\",\"count\":1}],"
         + "\"thumbnail\":\"data:image/png;base64,AA==\",\"createdAt\":1000}";

   static final class FakeRelay implements XChatClient.Relay {
      final List<String[]> calls = new ArrayList<>();
      int status = 200;
      String body = PROJECT_JSON;
      XChatClient.ChatException throwOn = null;

      @Override
      public XChatClient.Reply post(String path, String json) throws XChatClient.ChatException {
         this.calls.add(new String[]{path, json});
         if (this.throwOn != null) {
            throw this.throwOn;
         }

         return new XChatClient.Reply(this.status, this.body);
      }

      JsonObject lastBody() {
         return JsonUtil.GSON.fromJson(this.calls.get(this.calls.size() - 1)[1], JsonObject.class);
      }
   }

   @Test
   void aBlankOrEmptyCodeIsRefusedWithoutTouchingTheNetwork() {
      FakeRelay relay = new FakeRelay();
      MapArtClient client = new MapArtClient(relay);
      MapArtClient.Result result = client.fetch("   ");
      assertFalse(result.ok());
      assertTrue(result.error().contains("Type a code"), result.error());
      assertTrue(relay.calls.isEmpty());
   }

   @Test
   void aFoundProjectComesBackWithEverythingTheScreenNeeds() {
      FakeRelay relay = new FakeRelay();
      MapArtClient client = new MapArtClient(relay);
      MapArtClient.Result result = client.fetch("7k4p9m");
      assertTrue(result.ok(), result.error());
      assertEquals("My House", result.project().name);
      assertEquals(2, result.project().width);
      assertEquals(4, result.project().blocks.length);
      assertEquals(2, result.project().materials.size());
      assertEquals("data:image/png;base64,AA==", result.project().thumbnail);
   }

   @Test
   void theCodeIsNormalizedBeforeItIsSent() {
      FakeRelay relay = new FakeRelay();
      MapArtClient client = new MapArtClient(relay);
      client.fetch("  7k4p9m  ");
      assertEquals("7K4P9M", relay.lastBody().get("code").getAsString());
   }

   @Test
   void aMissingCodeGetsAFriendlyNotFoundMessageNotARawStatusNumber() {
      FakeRelay relay = new FakeRelay();
      relay.status = 404;
      relay.body = "{\"error\":\"no map art with that code\"}";
      MapArtClient.Result result = new MapArtClient(relay).fetch("ZZZZZZ");
      assertFalse(result.ok());
      assertEquals("No map art found with that code.", result.error());
   }

   @Test
   void aServerErrorSurfacesTheRelaysOwnMessage() {
      FakeRelay relay = new FakeRelay();
      relay.status = 429;
      relay.body = "{\"error\":\"slow down\"}";
      MapArtClient.Result result = new MapArtClient(relay).fetch("7K4P9M");
      assertFalse(result.ok());
      assertEquals("slow down", result.error());
   }

   @Test
   void aServerErrorWithNoUsableBodyFallsBackToTheStatusCode() {
      FakeRelay relay = new FakeRelay();
      relay.status = 500;
      relay.body = "not json";
      MapArtClient.Result result = new MapArtClient(relay).fetch("7K4P9M");
      assertFalse(result.ok());
      assertEquals("The relay said 500.", result.error());
   }

   @Test
   void garbageOnA200IsTreatedAsAFailureNotACrash() {
      FakeRelay relay = new FakeRelay();
      relay.body = "not json";
      MapArtClient.Result result = new MapArtClient(relay).fetch("7K4P9M");
      assertFalse(result.ok());
      assertEquals("The relay sent something unexpected.", result.error());
   }

   @Test
   void aProjectMissingItsBlockGridIsTreatedAsUnexpectedNotShownHalfBroken() {
      FakeRelay relay = new FakeRelay();
      relay.body = "{\"code\":\"7K4P9M\",\"width\":2,\"height\":2}"; // no "blocks" at all
      MapArtClient.Result result = new MapArtClient(relay).fetch("7K4P9M");
      assertFalse(result.ok());
      assertNull(result.project());
   }

   @Test
   void anUnreachableRelayReportsWhyNotJustThatItFailed() {
      FakeRelay relay = new FakeRelay();
      relay.throwOn = new XChatClient.ChatException("Can't reach the relay.", 30_000L);
      MapArtClient.Result result = new MapArtClient(relay).fetch("7K4P9M");
      assertFalse(result.ok());
      assertEquals("Can't reach the relay.", result.error());
   }

   @Test
   void normalizeTrimsAndUppercasesButLeavesABlankCodeBlank() {
      assertEquals("7K4P9M", MapArtClient.normalize(" 7k4p9m "));
      assertEquals("", MapArtClient.normalize(null));
      assertEquals("", MapArtClient.normalize("   "));
   }
}
