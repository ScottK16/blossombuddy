package org.blossomsuite.core.xchat;

import com.google.gson.JsonParseException;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import java.net.http.HttpResponse;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import org.blossomsuite.core.chat.ChatOutput;
import org.blossomsuite.core.chat.SecondaryChat;
import org.blossomsuite.core.config.FeatureConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.net.SuiteHttp;
import org.blossomsuite.core.state.SuiteScheduler;
import org.blossomsuite.core.state.SuiteState;
import org.blossomsuite.core.util.JsonUtil;
import org.blossomsuite.core.util.SuiteLog;
import org.blossomsuite.core.util.WorldGate;

/**
 * Cross-realm chat: signs in to the relay, sends what the player types with {@code /xc}, and fetches what others said.
 *
 * <p>Sign-in proves who the player is without ever giving the relay anything secret: the relay hands out a one-time
 * code, the mod tells <em>Mojang</em> "this account is joining a server with that code", and the relay asks Mojang
 * whether that happened. The Minecraft access token only ever goes to Mojang.
 *
 * <p>Everything runs on the mod's single I/O thread, so the fields below need no locking beyond what the UI reads.
 */
public final class XChatClient {
   public static final XChatClient INSTANCE = new XChatClient(new HttpRelay(), new MojangJoiner(), new GameEnv(), new GameSink(), System::currentTimeMillis);

   private static final long RENEW_BEFORE_EXPIRY_MS = 60_000L;

   public enum State {
      OFF,
      CONNECTING,
      READY,
      FAILED
   }

   public record Reply(int status, String body) {
   }

   /** One POST to the relay. */
   public interface Relay {
      Reply post(String path, String json) throws ChatException;
   }

   /** Tells Mojang the account is joining a server with this id, and returns the account name. */
   public interface Joiner {
      String join(String serverId) throws ChatException;
   }

   public interface Env {
      boolean enabled();

      /** In a world the mod is active in, with a relay address set. */
      boolean canRun();

      String realm();

      Collection<String> muted();
   }

   public interface Sink {
      void message(XChatModels.Message message, boolean own);

      void notice(String text);
   }

   /** A failure the player can be told about. {@code retryAfterMs} is how long to leave the relay alone. */
   public static final class ChatException extends Exception {
      final long retryAfterMs;

      public ChatException(String message, long retryAfterMs) {
         super(message);
         this.retryAfterMs = retryAfterMs;
      }

      public long retryAfterMs() {
         return this.retryAfterMs;
      }
   }

   private final Relay relay;
   private final Joiner joiner;
   private final Env env;
   private final Sink sink;
   private final java.util.function.LongSupplier clock;
   private final Set<Long> echoed = ConcurrentHashMap.newKeySet();

   private volatile State state = State.OFF;
   private volatile String detail = "";
   private volatile long retryAt = 0L;
   private volatile String accountName = "";
   private String token;
   private long tokenExpiresAt;
   private String myUuid = "";
   private long lastId = -1L;
   private String lastNotified = "";

   public XChatClient(Relay relay, Joiner joiner, Env env, Sink sink, java.util.function.LongSupplier clock) {
      this.relay = relay;
      this.joiner = joiner;
      this.env = env;
      this.sink = sink;
      this.clock = clock;
   }

   /** Starts the background loop (every 3 seconds). */
   public void init() {
      SuiteScheduler.IO.scheduleWithFixedDelay(() -> {
         try {
            this.step(this.clock.getAsLong());
         } catch (Throwable t) {
            SuiteLog.logger().debug("[xchat] step failed: {}", t.toString());
         }
      }, 3L, 3L, TimeUnit.SECONDS);
   }

   public State state() {
      return this.state;
   }

   /** One line for the options screen. */
   public String status() {
      return switch (this.state) {
         case OFF -> "Off.";
         case CONNECTING -> "Connecting...";
         case READY -> "Connected as " + this.accountName + ".";
         case FAILED -> "Not connected: " + this.detail;
      };
   }

   /** Called when the player turns chat on, so a past failure doesn't delay the first attempt. */
   public void wake() {
      this.retryAt = 0L;
   }

   /** Queues a message to send (from any thread). */
   public void sendAsync(String text) {
      SuiteScheduler.IO.execute(() -> {
         try {
            this.send(text, this.clock.getAsLong());
         } catch (Throwable t) {
            SuiteLog.logger().debug("[xchat] send failed: {}", t.toString());
         }
      });
   }

   // ------------------------------------------------------------------ the loop

   /** One round of work: sign in if needed, then fetch anything new. */
   void step(long now) {
      if (!this.env.enabled()) {
         this.state = State.OFF;
         this.detail = "";
         return;
      }

      if (!this.env.canRun() || now < this.retryAt) {
         return;
      }

      try {
         if (this.token == null || now >= this.tokenExpiresAt - RENEW_BEFORE_EXPIRY_MS) {
            this.signIn(now);
         }

         this.poll();
      } catch (ChatException e) {
         this.fail(e, now, true);
      }
   }

   private void signIn(long now) throws ChatException {
      this.state = State.CONNECTING;
      XChatModels.Challenge challenge = expect(this.relay.post("/v1/chat/challenge", "{}"), XChatModels.Challenge.class);
      if (challenge.challengeId == null || challenge.serverId == null) {
         throw new ChatException("The relay sent something unexpected.", 60_000L);
      }

      String name = this.joiner.join(challenge.serverId);
      Reply reply = this.relay.post("/v1/chat/auth", JsonUtil.GSON.toJson(new XChatModels.AuthRequest(challenge.challengeId, name)));
      if (reply.status() == 401) {
         throw new ChatException("Couldn't verify your Minecraft account.", 60_000L);
      }

      if (reply.status() == 403) {
         throw new ChatException("You're blocked from cross-realm chat.", 10 * 60_000L);
      }

      XChatModels.Auth auth = expect(reply, XChatModels.Auth.class);
      if (auth.token == null || auth.token.isBlank()) {
         throw new ChatException("The relay sent something unexpected.", 60_000L);
      }

      this.token = auth.token;
      this.tokenExpiresAt = now + (auth.expiresInSeconds == null ? 3600 : auth.expiresInSeconds) * 1000L;
      this.myUuid = auth.uuid == null ? "" : auth.uuid.toLowerCase();
      this.accountName = auth.name == null ? name : auth.name;
      this.lastId = -1L; // a new session starts from "now", without replaying old chat
      this.state = State.READY;
      this.detail = "";
      this.lastNotified = "";
   }

   private void poll() throws ChatException {
      Reply reply = this.relay.post("/v1/chat/poll", JsonUtil.GSON.toJson(new XChatModels.PollRequest(this.token, this.lastId >= 0 ? this.lastId : null)));
      if (reply.status() == 401) {
         this.token = null; // signed out on the relay's side: sign in again next round
         return;
      }

      if (reply.status() == 429) {
         return;
      }

      XChatModels.Poll poll = expect(reply, XChatModels.Poll.class);
      boolean firstSync = this.lastId < 0;
      long newest = this.lastId;
      List<XChatModels.Message> messages = poll.messages == null ? List.of() : poll.messages;
      for (XChatModels.Message m : messages) {
         newest = Math.max(newest, m.id);
         if (!firstSync) {
            this.deliver(m);
         }
      }

      this.lastId = Math.max(newest, poll.latestId);
      this.state = State.READY;
   }

   private void deliver(XChatModels.Message m) {
      if (this.echoed.remove(m.id) || XChatText.isMuted(m, this.env.muted())) {
         return;
      }

      this.sink.message(m, m.uuid != null && m.uuid.equalsIgnoreCase(this.myUuid));
   }

   // ------------------------------------------------------------------ sending

   /** Sends one message. Answers (including errors) go to the player through the sink. */
   void send(String rawText, long now) {
      if (!this.env.enabled()) {
         this.sink.notice("Cross-realm chat is off. Turn it on with /buddy xchat on.");
         return;
      }

      String text = XChatText.clean(rawText, XChatText.MAX_LENGTH);
      if (text.isEmpty()) {
         this.sink.notice("Nothing to send. Use /xc <message>.");
         return;
      }

      try {
         for (int attempt = 0; attempt < 2; attempt++) {
            if (this.token == null || now >= this.tokenExpiresAt - RENEW_BEFORE_EXPIRY_MS) {
               this.signIn(now);
            }

            Reply reply = this.relay.post("/v1/chat/send", JsonUtil.GSON.toJson(new XChatModels.SendRequest(this.token, text, this.env.realm())));
            if (reply.status() == 401 && attempt == 0) {
               this.token = null; // expired on the relay's side: sign in and try once more
               continue;
            }

            if (reply.status() == 200) {
               XChatModels.Message sent = expect(reply, XChatModels.Message.class);
               this.echoed.add(sent.id);
               this.sink.message(sent, true);
            } else {
               this.sink.notice(errorOf(reply));
            }

            return;
         }
      } catch (ChatException e) {
         this.fail(e, now, false);
         this.sink.notice("Couldn't send: " + e.getMessage());
      }
   }

   /** @param tell whether to tell the player now (background problems are mentioned once per kind, not every retry) */
   private void fail(ChatException e, long now, boolean tell) {
      this.state = State.FAILED;
      this.detail = e.getMessage();
      this.retryAt = now + e.retryAfterMs;
      if (tell && !e.getMessage().equals(this.lastNotified)) {
         this.lastNotified = e.getMessage(); // tell the player once per kind of problem, not every retry
         this.sink.notice("Cross-realm chat: " + e.getMessage());
      }
   }

   // ------------------------------------------------------------------ helpers

   private static <T> T expect(Reply reply, Class<T> type) throws ChatException {
      if (reply.status() != 200) {
         throw new ChatException(errorOf(reply), 30_000L);
      }

      try {
         T parsed = JsonUtil.GSON.fromJson(reply.body(), type);
         if (parsed == null) {
            throw new ChatException("The relay sent something unexpected.", 60_000L);
         }

         return parsed;
      } catch (JsonParseException e) {
         throw new ChatException("The relay sent something unexpected.", 60_000L);
      }
   }

   /** The relay's own explanation when it gave one. */
   static String errorOf(Reply reply) {
      try {
         XChatModels.Error e = JsonUtil.GSON.fromJson(reply.body(), XChatModels.Error.class);
         if (e != null && e.error != null && !e.error.isBlank()) {
            return XChatText.clean(e.error, 80);
         }
      } catch (JsonParseException ignored) {
      }

      return "The relay said " + reply.status() + ".";
   }

   // ------------------------------------------------------------------ real wiring

   public static final class HttpRelay implements Relay {
      @Override
      public Reply post(String path, String json) throws ChatException {
         SuiteHttp http = SuiteState.INSTANCE.http;
         if (http == null || !http.enabled()) {
            throw new ChatException("No relay address is set.", 60_000L);
         }

         try {
            HttpResponse<String> response = http.postJson(path, json).get(8, TimeUnit.SECONDS);
            return new Reply(response.statusCode(), response.body());
         } catch (ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new ChatException("Can't reach the relay.", 30_000L);
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ChatException("Interrupted.", 5_000L);
         }
      }
   }

   public static final class MojangJoiner implements Joiner {
      @Override
      public String join(String serverId) throws ChatException {
         MinecraftClient mc = MinecraftClient.getInstance();
         Session session = mc.getSession();
         UUID id = session == null ? null : session.getUuidOrNull();
         if (id == null) {
            throw new ChatException("This isn't a Minecraft account that can sign in.", 10 * 60_000L);
         }

         try {
            MinecraftSessionService service = mc.getSessionService();
            service.joinServer(id, session.getAccessToken(), serverId); // talks to Mojang only
            return session.getUsername();
         } catch (Exception e) {
            throw new ChatException("Mojang's login check didn't accept this session.", 60_000L);
         }
      }
   }

   private static final class GameEnv implements Env {
      @Override
      public boolean enabled() {
         return FeatureConfig.INSTANCE.xchat.enabled;
      }

      @Override
      public boolean canRun() {
         MinecraftClient mc = MinecraftClient.getInstance();
         SuiteHttp http = SuiteState.INSTANCE.http;
         return mc.player != null && SuiteConfig.INSTANCE.isEnabledForCurrentWorld() && http != null && http.enabled();
      }

      @Override
      public String realm() {
         String realm = WorldGate.Server;
         return realm == null ? "" : realm;
      }

      @Override
      public Collection<String> muted() {
         return FeatureConfig.INSTANCE.xchat.muted;
      }
   }

   /** Shows messages in the secondary chat's Realms tab, or in the main chat when that window is off. */
   private static final class GameSink implements Sink {
      @Override
      public void message(XChatModels.Message message, boolean own) {
         MinecraftClient.getInstance().execute(() -> {
            net.minecraft.text.Text line = XChatText.format(message);
            SecondaryChat.INSTANCE.addExternal(line);
            if (!FeatureConfig.INSTANCE.chat.show) {
               ChatOutput.raw(line);
            }
         });
      }

      @Override
      public void notice(String text) {
         MinecraftClient.getInstance().execute(() -> ChatOutput.info(text));
      }
   }
}
