package org.blossomsuite.core.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;
import org.blossomsuite.core.util.JsonUtil;

/**
 * Discord "Rich Presence": the line under your name in Discord ("Playing BlossomBuddy - On Cherry Realm"). It talks to the Discord app
 * running on your own computer over a local connection; the mod sends nothing to any BlossomBuddy server. Off unless you turn it on.
 *
 * <p>This class holds the protocol and the pacing (Discord allows an update about every 15 seconds); how the connection is opened and
 * what to show are handed in, so it can be tried without Discord.
 */
public final class DiscordPresence {
   static final int OP_HANDSHAKE = 0;
   static final int OP_FRAME = 1;
   static final int OP_CLOSE = 2;
   /** Discord rate-limits presence updates; one every 15 seconds is what it documents. */
   static final long MIN_SEND_GAP_MS = 15_000L;
   /** How often to look for a Discord app that was not running. */
   static final long RETRY_MS = 30_000L;

   /** What to show. {@code state} may be empty; {@code startSeconds} (epoch seconds) makes Discord count "elapsed" from then. */
   public record Activity(String details, String state, long startSeconds, String largeImage, String largeText, String buttonLabel, String buttonUrl) {
      JsonObject toJson() {
         JsonObject o = new JsonObject();
         if (this.details != null && !this.details.isBlank()) {
            o.addProperty("details", clip(this.details));
         }

         if (this.state != null && !this.state.isBlank()) {
            o.addProperty("state", clip(this.state));
         }

         if (this.startSeconds > 0L) {
            JsonObject t = new JsonObject();
            t.addProperty("start", this.startSeconds);
            o.add("timestamps", t);
         }

         if (this.largeImage != null && !this.largeImage.isBlank()) {
            JsonObject a = new JsonObject();
            a.addProperty("large_image", this.largeImage);
            if (this.largeText != null && !this.largeText.isBlank()) {
               a.addProperty("large_text", clip(this.largeText));
            }

            o.add("assets", a);
         }

         if (this.buttonLabel != null && !this.buttonLabel.isBlank() && this.buttonUrl != null && this.buttonUrl.startsWith("https://")) {
            JsonArray buttons = new JsonArray();
            JsonObject b = new JsonObject();
            b.addProperty("label", clip(this.buttonLabel, 32));
            b.addProperty("url", this.buttonUrl);
            buttons.add(b);
            o.add("buttons", buttons);
         }

         return o;
      }
   }

   /** An open line to Discord. */
   interface Conn extends Closeable {
      void write(byte[] frame) throws IOException;

      /** Reads one whole frame (blocking): {opcode, JSON text}. */
      Frame readFrame() throws IOException;
   }

   record Frame(int opcode, String json) {
   }

   /** Opens a line to Discord; throws when no Discord app is running. */
   interface Transport {
      Conn open() throws IOException;
   }

   private final Transport transport;
   private final Executor io;
   private final LongSupplier clock;
   private final long pid;
   private Conn conn;
   private String connClientId = "";
   private boolean busy = false;
   private Activity lastSent = null;
   private long lastSentAtMs = Long.MIN_VALUE / 2;
   private long lastAttemptAtMs = Long.MIN_VALUE / 2;
   private String status = "Off";

   public DiscordPresence(Transport transport, Executor io, LongSupplier clock, long pid) {
      this.transport = transport;
      this.io = io;
      this.clock = clock;
      this.pid = pid;
   }

   public synchronized String status() {
      return this.status;
   }

   /**
    * Call about once a second with what should be showing (null for nothing, e.g. you are not on a realm). Cheap; the connecting and
    * writing happens on the executor.
    */
   public synchronized void tick(String clientId, Activity desired) {
      if (clientId == null || clientId.isBlank()) {
         this.status = "No Discord application is set up yet";
         return;
      }

      if (this.conn != null && !this.connClientId.equals(clientId)) {
         this.closeQuietly(); // the application changed: start again with the new one
      }

      long now = this.clock.getAsLong();
      if (this.busy) {
         return;
      }

      if (this.conn == null) {
         if (desired == null || now - this.lastAttemptAtMs < RETRY_MS) {
            return; // nothing to show, or tried a moment ago
         }

         this.lastAttemptAtMs = now;
         this.busy = true;
         this.io.execute(() -> this.connect(clientId));
         return;
      }

      if (Objects.equals(desired, this.lastSent) || now - this.lastSentAtMs < MIN_SEND_GAP_MS) {
         return;
      }

      this.busy = true;
      Activity send = desired;
      this.io.execute(() -> this.send(send));
   }

   /** Takes the presence down (leaving the game, or switching it off) and hangs up. */
   public synchronized void stop() {
      Conn c = this.conn;
      if (c == null) {
         this.status = "Off";
         return;
      }

      this.busy = true;
      this.io.execute(() -> {
         try {
            this.writeActivity(c, null);
         } catch (IOException ignored) {
            // hanging up anyway
         }

         synchronized (this) {
            this.closeQuietly();
            this.lastSent = null;
            this.busy = false;
            this.status = "Off";
         }
      });
   }

   // ------------------------------------------------------------------ on the executor

   private void connect(String clientId) {
      Conn opened = null;
      try {
         opened = this.transport.open();
         JsonObject hello = new JsonObject();
         hello.addProperty("v", 1);
         hello.addProperty("client_id", clientId);
         opened.write(encode(OP_HANDSHAKE, JsonUtil.GSON.toJson(hello)));
         Frame reply = opened.readFrame();
         if (reply.opcode() == OP_CLOSE || !reply.json().contains("READY")) {
            throw new Refused(reply.json());
         }

         synchronized (this) {
            this.conn = opened;
            this.connClientId = clientId;
            this.lastSent = null;
            this.lastSentAtMs = Long.MIN_VALUE / 2; // connected just now: the first update can go straight away
            this.status = "Connected to Discord";
            this.busy = false;
         }
      } catch (IOException | RuntimeException e) {
         if (opened != null) {
            try {
               opened.close();
            } catch (IOException ignored) {
               // already failed
            }
         }

         synchronized (this) {
            this.conn = null;
            this.status = e instanceof Refused
               ? "Discord refused the application id (" + clip(e.getMessage(), 80) + ")"
               : "Discord is not running, or can't be reached (will keep looking): " + clip(String.valueOf(e.getMessage()), 80);
            this.busy = false;
         }
      }
   }

   /** Discord answered, but said no (for example an application id it does not know). */
   private static final class Refused extends IOException {
      Refused(String reply) {
         super(reply);
      }
   }

   private void send(Activity activity) {
      Conn c;
      synchronized (this) {
         c = this.conn;
      }

      if (c == null) {
         synchronized (this) {
            this.busy = false;
         }

         return;
      }

      try {
         this.writeActivity(c, activity);
         synchronized (this) {
            this.lastSent = activity;
            this.lastSentAtMs = this.clock.getAsLong();
            this.status = activity == null ? "Connected to Discord" : "Showing on Discord";
            this.busy = false;
         }
      } catch (Rejected e) {
         synchronized (this) {
            this.lastSent = activity; // do not hammer Discord with the same thing; it is tried again when something changes
            this.lastSentAtMs = this.clock.getAsLong();
            this.status = "Discord rejected the update (" + e.getMessage() + ")";
            this.busy = false;
         }
      } catch (IOException e) {
         synchronized (this) {
            this.closeQuietly();
            this.status = "Lost Discord (will reconnect)";
            this.busy = false;
         }
      }
   }

   private void writeActivity(Conn c, Activity activity) throws IOException {
      JsonObject args = new JsonObject();
      args.addProperty("pid", this.pid);
      if (activity != null) {
         args.add("activity", activity.toJson());
      }

      JsonObject cmd = new JsonObject();
      cmd.addProperty("cmd", "SET_ACTIVITY");
      cmd.add("args", args);
      cmd.addProperty("nonce", UUID.randomUUID().toString());
      c.write(encode(OP_FRAME, JsonUtil.GSON.toJson(cmd)));
      Frame answer = c.readFrame(); // Discord answers every command; reading it keeps the line clear
      String problem = errorIn(answer);
      if (problem != null) {
         throw new Rejected(problem);
      }
   }

   /** Discord says "ERROR" (with a message) when it does not like what it was sent; null when the answer is fine. */
   static String errorIn(Frame answer) {
      if (answer == null) {
         return null;
      }

      if (answer.opcode() == OP_CLOSE) {
         return "Discord closed the connection: " + clip(answer.json(), 100);
      }

      try {
         JsonObject o = JsonUtil.GSON.fromJson(answer.json(), JsonObject.class);
         if (o != null && o.has("evt") && !o.get("evt").isJsonNull() && "ERROR".equalsIgnoreCase(o.get("evt").getAsString())) {
            String message = o.has("data") && o.get("data").isJsonObject() && o.getAsJsonObject("data").has("message")
               ? o.getAsJsonObject("data").get("message").getAsString()
               : answer.json();
            return clip(message, 120);
         }
      } catch (RuntimeException notJson) {
         // not something we understand: assume it was fine
      }

      return null;
   }

   /** Discord understood the update but refused it (for example a badly formed field). The line itself is fine. */
   private static final class Rejected extends IOException {
      Rejected(String message) {
         super(message);
      }
   }

   private void closeQuietly() {
      Conn c = this.conn;
      this.conn = null;
      this.connClientId = "";
      if (c != null) {
         try {
            c.close();
         } catch (IOException ignored) {
            // nothing more to do
         }
      }
   }

   // ------------------------------------------------------------------ the wire format

   /** A frame is: opcode (4 bytes, little-endian), length (4 bytes, little-endian), then that many bytes of JSON. */
   static byte[] encode(int opcode, String json) {
      byte[] body = json.getBytes(StandardCharsets.UTF_8);
      ByteBuffer buf = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
      buf.putInt(opcode);
      buf.putInt(body.length);
      buf.put(body);
      return buf.array();
   }

   static int bodyLength(byte[] header) throws IOException {
      int len = ByteBuffer.wrap(header, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
      if (len < 0 || len > 1_048_576) {
         throw new IOException("Discord sent a frame that is too big");
      }

      return len;
   }

   static int opcodeOf(byte[] header) {
      return ByteBuffer.wrap(header, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
   }

   private static String clip(String s) {
      return clip(s, 128);
   }

   private static String clip(String s, int max) {
      return s.length() <= max ? s : s.substring(0, max);
   }
}
