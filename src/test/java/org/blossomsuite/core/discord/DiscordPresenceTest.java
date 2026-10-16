package org.blossomsuite.core.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.blossomsuite.core.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DiscordPresenceTest {
   static final class FakeDiscord implements DiscordPresence.Transport {
      boolean running = true;
      boolean acceptApp = true;
      final List<byte[]> written = new ArrayList<>();
      int opens = 0;
      boolean closed = false;
      boolean failWrites = false;

      @Override
      public DiscordPresence.Conn open() throws IOException {
         this.opens++;
         if (!this.running) {
            throw new IOException("no discord");
         }

         return new DiscordPresence.Conn() {
            private int reads = 0;

            @Override
            public void write(byte[] frame) throws IOException {
               if (FakeDiscord.this.failWrites) {
                  throw new IOException("broken pipe");
               }

               FakeDiscord.this.written.add(frame);
            }

            @Override
            public DiscordPresence.Frame readFrame() {
               this.reads++;
               if (this.reads == 1) {
                  return FakeDiscord.this.acceptApp
                     ? new DiscordPresence.Frame(DiscordPresence.OP_FRAME, "{\"cmd\":\"DISPATCH\",\"evt\":\"READY\"}")
                     : new DiscordPresence.Frame(DiscordPresence.OP_CLOSE, "{\"code\":4000,\"message\":\"Invalid Client ID\"}");
               }

               return new DiscordPresence.Frame(DiscordPresence.OP_FRAME, "{\"cmd\":\"SET_ACTIVITY\"}");
            }

            @Override
            public void close() {
               FakeDiscord.this.closed = true;
            }
         };
      }

      JsonObject json(int index) {
         byte[] frame = this.written.get(index);
         return JsonUtil.GSON.fromJson(new String(Arrays.copyOfRange(frame, 8, frame.length), StandardCharsets.UTF_8), JsonObject.class);
      }
   }

   private FakeDiscord discord;
   private long now;
   private DiscordPresence presence;

   private static DiscordPresence.Activity on(String realm) {
      return new DiscordPresence.Activity("On " + realm + " Realm", "", 1_700_000_000L, "logo", "BlossomBuddy", "Get BlossomBuddy", "https://blossombuddy.site");
   }

   @BeforeEach
   void setUp() {
      this.discord = new FakeDiscord();
      this.now = 1_000_000L;
      this.presence = new DiscordPresence(this.discord, Runnable::run, () -> this.now, 4242L);
   }

   @Test
   void itConnectsThenShowsWhereYouAre() {
      this.presence.tick("12345", on("Cherry"));
      assertEquals("Connected to Discord", this.presence.status());
      JsonObject hello = this.discord.json(0);
      assertEquals(1, hello.get("v").getAsInt());
      assertEquals("12345", hello.get("client_id").getAsString());
      assertEquals(DiscordPresence.OP_HANDSHAKE, ByteBuffer.wrap(this.discord.written.get(0)).order(ByteOrder.LITTLE_ENDIAN).getInt());

      this.presence.tick("12345", on("Cherry"));
      assertEquals("Showing on Discord", this.presence.status());
      JsonObject set = this.discord.json(1);
      assertEquals("SET_ACTIVITY", set.get("cmd").getAsString());
      assertEquals(4242L, set.getAsJsonObject("args").get("pid").getAsLong());
      JsonObject activity = set.getAsJsonObject("args").getAsJsonObject("activity");
      assertEquals("On Cherry Realm", activity.get("details").getAsString());
      assertEquals(1_700_000_000L, activity.getAsJsonObject("timestamps").get("start").getAsLong());
      assertEquals("logo", activity.getAsJsonObject("assets").get("large_image").getAsString());
      assertEquals("https://blossombuddy.site", activity.getAsJsonArray("buttons").get(0).getAsJsonObject().get("url").getAsString());
      assertFalse(activity.has("state"), "no state line when there is nothing to say");
      assertNotNull(set.get("nonce"));
   }

   @Test
   void theLengthPrefixMatchesTheBody() {
      this.presence.tick("12345", on("Cherry"));
      byte[] frame = this.discord.written.get(0);
      assertEquals(frame.length - 8, ByteBuffer.wrap(frame, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
   }

   @Test
   void updatesAreNotSentMoreOftenThanDiscordAllows() {
      this.presence.tick("12345", on("Cherry"));
      this.presence.tick("12345", on("Cherry")); // first update
      int sent = this.discord.written.size();
      this.now += 2_000L;
      this.presence.tick("12345", on("Spirit")); // changed, but too soon
      assertEquals(sent, this.discord.written.size());
      this.now += DiscordPresence.MIN_SEND_GAP_MS;
      this.presence.tick("12345", on("Spirit"));
      assertEquals(sent + 1, this.discord.written.size());
      assertEquals("On Spirit Realm", this.discord.json(sent).getAsJsonObject("args").getAsJsonObject("activity").get("details").getAsString());
   }

   @Test
   void anUnchangedActivityIsNotSentAgain() {
      this.presence.tick("12345", on("Cherry"));
      this.presence.tick("12345", on("Cherry"));
      int sent = this.discord.written.size();
      this.now += 60_000L;
      this.presence.tick("12345", on("Cherry"));
      assertEquals(sent, this.discord.written.size());
   }

   @Test
   void noActivityTakesTheStatusDown() {
      this.presence.tick("12345", on("Cherry"));
      this.presence.tick("12345", on("Cherry"));
      this.now += DiscordPresence.MIN_SEND_GAP_MS;
      this.presence.tick("12345", null);
      JsonObject last = this.discord.json(this.discord.written.size() - 1);
      assertFalse(last.getAsJsonObject("args").has("activity"), "an update with no activity clears it");
   }

   @Test
   void stopClearsAndHangsUp() {
      this.presence.tick("12345", on("Cherry"));
      this.presence.tick("12345", on("Cherry"));
      this.presence.stop();
      assertTrue(this.discord.closed);
      assertEquals("Off", this.presence.status());
      assertFalse(this.discord.json(this.discord.written.size() - 1).getAsJsonObject("args").has("activity"));
   }

   @Test
   void withoutDiscordRunningItKeepsLookingButNotEveryTick() {
      this.discord.running = false;
      this.presence.tick("12345", on("Cherry"));
      assertTrue(this.presence.status().startsWith("Discord is not running"), this.presence.status());
      this.now += 1_000L;
      this.presence.tick("12345", on("Cherry"));
      assertEquals(1, this.discord.opens, "waits before looking again");
      this.now += DiscordPresence.RETRY_MS;
      this.discord.running = true;
      this.presence.tick("12345", on("Cherry"));
      assertEquals(2, this.discord.opens);
      assertEquals("Connected to Discord", this.presence.status());
   }

   @Test
   void nothingHappensWithoutAnApplicationIdOrAnythingToShow() {
      this.presence.tick("", on("Cherry"));
      assertEquals(0, this.discord.opens);
      assertTrue(this.presence.status().contains("No Discord application"));
      this.presence.tick("12345", null);
      assertEquals(0, this.discord.opens, "no point connecting with nothing to show");
   }

   @Test
   void anApplicationDiscordRefusesIsReportedAndRetriedLater() {
      this.discord.acceptApp = false;
      this.presence.tick("bad", on("Cherry"));
      assertTrue(this.presence.status().startsWith("Discord refused the application id"), this.presence.status());
      assertEquals(1, this.discord.opens);
   }

   @Test
   void aBrokenLineIsDroppedAndReopenedLater() {
      this.presence.tick("12345", on("Cherry"));
      this.presence.tick("12345", on("Cherry"));
      this.discord.failWrites = true;
      this.now += DiscordPresence.MIN_SEND_GAP_MS;
      this.presence.tick("12345", on("Spirit"));
      assertTrue(this.presence.status().startsWith("Lost Discord"));
      assertTrue(this.discord.closed);
      this.discord.failWrites = false;
      this.now += DiscordPresence.RETRY_MS;
      this.presence.tick("12345", on("Spirit"));
      assertEquals(2, this.discord.opens);
   }

   @Test
   void longTextIsClippedAndOnlyHttpsButtonsAreSent() {
      DiscordPresence.Activity a = new DiscordPresence.Activity("x".repeat(500), "", 0L, "", "", "Click", "http://insecure.example");
      JsonObject o = a.toJson();
      assertEquals(128, o.get("details").getAsString().length());
      assertFalse(o.has("buttons"));
      assertFalse(o.has("timestamps"));
   }
}
