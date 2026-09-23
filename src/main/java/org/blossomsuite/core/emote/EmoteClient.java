package org.blossomsuite.core.emote;

import com.google.gson.JsonParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import org.blossomsuite.core.chat.ChatOutput;
import org.blossomsuite.core.config.FeatureConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.net.SuiteHttp;
import org.blossomsuite.core.presence.PresenceClient;
import org.blossomsuite.core.state.SuiteScheduler;
import org.blossomsuite.core.state.SuiteState;
import org.blossomsuite.core.util.JsonUtil;
import org.blossomsuite.core.util.SuiteLog;
import org.blossomsuite.core.xchat.XChatClient;

/**
 * Emotes: plays your own emote at once, tells the relay so other BlossomBuddy players on your realm see it, and plays theirs on
 * their characters.
 *
 * <p>It rides on the player list: the relay knows who you are (proven with Mojang's login check) and which realm you are on from
 * that, so emotes only work while you are connected to it. Others only see an emote if they use the mod and are in the list too.
 */
public final class EmoteClient {
   public static final EmoteClient INSTANCE = new EmoteClient(new XChatClient.HttpRelay(), new PresenceLink(), new GameEnv(), new GameSink(), EmoteState.INSTANCE, SuiteScheduler.IO, System::currentTimeMillis);

   /** How often to look for new emotes while another mod user is on your realm, and while nobody is. */
   static final long POLL_BUSY_MS = 1_000L;
   static final long POLL_QUIET_MS = 10_000L;
   /** A moving player stops their emote, but not in the first moments, so pressing a key while opening the wheel doesn't cancel it. */
   static final long MOVE_GRACE_MS = 400L;
   private static final long WARN_GAP_MS = 30_000L;

   /** The relay session the player list already has. */
   public interface Presence {
      /** Null unless the player list is connected. */
      String token();

      /** Other mod users listed on the realm we are on. */
      int othersOnMyRealm();
   }

   public interface Env {
      boolean enabled();

      void setEnabled(boolean on);

      boolean showOthers();

      void setShowOthers(boolean on);

      boolean share();

      void setShare(boolean on);

      /** In a world the mod is active in, with a relay address set. */
      boolean canRun();

      UUID selfId();

      /** Any movement, jump, sneak or attack key is held. */
      boolean wantsToMove();
   }

   public interface Sink {
      void notice(String text);
   }

   private final XChatClient.Relay relay;
   private final Presence presence;
   private final Env env;
   private final Sink sink;
   private final EmoteState state;
   private final Executor async;
   private final java.util.function.LongSupplier clock;
   private volatile long nextPollAt = 0L;
   private volatile long lastWarnAt = -WARN_GAP_MS;
   private long lastId = -1L;

   public EmoteClient(XChatClient.Relay relay, Presence presence, Env env, Sink sink, EmoteState state, Executor async, java.util.function.LongSupplier clock) {
      this.relay = relay;
      this.presence = presence;
      this.env = env;
      this.sink = sink;
      this.state = state;
      this.async = async;
      this.clock = clock;
   }

   /** Starts the background loop that fetches other players' emotes (it only asks the relay when there is a reason to). */
   public void init() {
      SuiteScheduler.IO.scheduleWithFixedDelay(() -> {
         try {
            this.step(this.clock.getAsLong());
         } catch (Throwable t) {
            SuiteLog.logger().debug("[emote] step failed: {}", t.toString());
         }
      }, 3L, 1L, TimeUnit.SECONDS);
   }

   // ------------------------------------------------------------------ playing your own

   /** Plays an emote by name. Says why when it can't. */
   public boolean play(String id) {
      Emote emote = Emote.byId(id);
      if (emote == null) {
         this.sink.notice("Unknown emote. Try: " + names() + ".");
         return false;
      }

      return this.play(emote);
   }

   public boolean play(Emote emote) {
      if (!this.env.enabled()) {
         this.sink.notice("Emotes are off. Turn them on in Options > Cross-Realm Chat.");
         return false;
      }

      UUID me = this.env.selfId();
      if (me == null) {
         return false;
      }

      this.state.start(me, emote, this.clock.getAsLong());
      if (this.env.share()) {
         this.send(emote.id(), me);
      }

      return true;
   }

   /** Ends your emote, for you and for everyone who was watching. */
   public void stop() {
      UUID me = this.env.selfId();
      if (me != null && this.state.isActive(me, this.clock.getAsLong())) {
         this.state.stop(me);
         if (this.env.share()) {
            this.send("stop", me);
         }
      }
   }

   /** Each game tick: moving, jumping or attacking ends your own emote. */
   public void clientTick() {
      this.clientTick(this.clock.getAsLong());
   }

   void clientTick(long now) {
      UUID me = this.env.selfId();
      EmoteState.Active mine = me == null ? null : this.state.activeFor(me, now);
      if (mine != null && now - mine.startMs() > MOVE_GRACE_MS && this.env.wantsToMove()) {
         this.stop();
      }
   }

   private void send(String emote, UUID me) {
      this.async.execute(() -> {
         String token = this.presence.token();
         if (token == null) {
            this.warn("Others can't see your emotes until the player list is connected (/buddy who).");
            return;
         }

         try {
            XChatClient.Reply reply = this.relay.post("/v1/emote/play", JsonUtil.GSON.toJson(Map.of("token", token, "emote", emote)));
            if (reply.status() == 403) {
               // not a broadcast hiccup: the relay is refusing this one specifically, so it should not keep playing for us either
               this.state.stop(me);
               this.warn("You don't have that emote.");
            } else if (reply.status() == 429) {
               this.warn("Slow down a little: others didn't see that emote.");
            } else if (reply.status() != 200) {
               this.warn("Others didn't see that emote (the relay said " + reply.status() + ").");
            }
         } catch (XChatClient.ChatException e) {
            this.warn("Others can't see your emotes right now: " + e.getMessage());
         }
      });
   }

   /** At most one complaint every half minute, so a relay outage doesn't fill the chat. */
   private void warn(String text) {
      long now = this.clock.getAsLong();
      if (now - this.lastWarnAt >= WARN_GAP_MS) {
         this.lastWarnAt = now;
         this.sink.notice(text);
      }
   }

   // ------------------------------------------------------------------ seeing other players'

   void step(long now) {
      if (!this.env.enabled() || !this.env.showOthers() || !this.env.canRun() || now < this.nextPollAt) {
         return;
      }

      String token = this.presence.token();
      if (token == null) {
         this.lastId = -1L; // when we reconnect, start from "now" again
         return;
      }

      try {
         this.poll(token, now);
      } catch (XChatClient.ChatException e) {
         this.nextPollAt = now + Math.max(5_000L, e.retryAfterMs());
      }
   }

   /** The shape of the relay's answer. */
   static final class Poll {
      List<Wire> events;
      Long latestId;
   }

   static final class Wire {
      long id;
      String uuid;
      String type;
      String emote;
   }

   private void poll(String token, long now) throws XChatClient.ChatException {
      Map<String, Object> body = new HashMap<>();
      body.put("token", token);
      if (this.lastId >= 0) {
         body.put("since", this.lastId);
      }

      XChatClient.Reply reply = this.relay.post("/v1/emote/poll", JsonUtil.GSON.toJson(body));
      if (reply.status() == 401) {
         this.nextPollAt = now + 5_000L; // the player list will sign in again
         this.lastId = -1L;
         return;
      }

      if (reply.status() != 200) {
         throw new XChatClient.ChatException("The relay said " + reply.status() + ".", 30_000L);
      }

      Poll poll;
      try {
         poll = JsonUtil.GSON.fromJson(reply.body(), Poll.class);
      } catch (JsonParseException e) {
         throw new XChatClient.ChatException("The relay sent something unexpected.", 60_000L);
      }

      if (poll == null) {
         throw new XChatClient.ChatException("The relay sent something unexpected.", 60_000L);
      }

      boolean firstLook = this.lastId < 0;
      long newest = this.lastId;
      UUID me = this.env.selfId();
      if (poll.events != null && !firstLook) {
         for (Wire w : poll.events) {
            if (w == null) {
               continue;
            }

            newest = Math.max(newest, w.id);
            this.apply(w, me, now);
         }
      }

      this.lastId = Math.max(newest, poll.latestId == null ? newest : poll.latestId);
      this.nextPollAt = now + (this.presence.othersOnMyRealm() >= 1 ? POLL_BUSY_MS : POLL_QUIET_MS);
   }

   private void apply(Wire w, UUID me, long now) {
      UUID who = PresenceClient.uuidOf(w.uuid);
      if (who == null || who.equals(me)) {
         return; // malformed, or our own (we already play those at once)
      }

      if ("stop".equals(w.type)) {
         this.state.stop(who);
      } else if ("play".equals(w.type)) {
         Emote emote = Emote.byId(w.emote);
         if (emote != null) {
            this.state.start(who, emote, now);
         }
      }
   }

   // ------------------------------------------------------------------ switches

   public void setEnabled(boolean on) {
      this.env.setEnabled(on);
      if (!on) {
         this.state.clear();
      }
   }

   public void setShowOthers(boolean on) {
      this.env.setShowOthers(on);
      this.nextPollAt = 0L;
      if (!on) {
         this.state.clear();
      }
   }

   public void setShare(boolean on) {
      this.env.setShare(on);
   }

   static String names() {
      StringBuilder sb = new StringBuilder();
      for (Emote e : Emote.ALL) {
         if (sb.length() > 0) {
            sb.append(", ");
         }

         sb.append(e.id());
      }

      return sb.toString();
   }

   public static String emoteNames() {
      return names();
   }

   // ------------------------------------------------------------------ real wiring

   private static final class PresenceLink implements Presence {
      @Override
      public String token() {
         return PresenceClient.INSTANCE.sessionToken();
      }

      @Override
      public int othersOnMyRealm() {
         return PresenceClient.INSTANCE.othersOnMyRealm();
      }
   }

   private static final class GameEnv implements Env {
      private static FeatureConfig.Emotes e() {
         return FeatureConfig.INSTANCE.emotes;
      }

      @Override
      public boolean enabled() {
         return e().enabled;
      }

      @Override
      public void setEnabled(boolean on) {
         e().enabled = on;
         FeatureConfig.markDirty();
      }

      @Override
      public boolean showOthers() {
         return e().showOthers;
      }

      @Override
      public void setShowOthers(boolean on) {
         e().showOthers = on;
         FeatureConfig.markDirty();
      }

      @Override
      public boolean share() {
         return e().share;
      }

      @Override
      public void setShare(boolean on) {
         e().share = on;
         FeatureConfig.markDirty();
      }

      @Override
      public boolean canRun() {
         MinecraftClient mc = MinecraftClient.getInstance();
         SuiteHttp http = SuiteState.INSTANCE.http;
         return mc.player != null && SuiteConfig.INSTANCE.isEnabledForCurrentWorld() && http != null && http.enabled();
      }

      @Override
      public UUID selfId() {
         MinecraftClient mc = MinecraftClient.getInstance();
         return mc.player == null ? null : mc.player.getUuid();
      }

      @Override
      public boolean wantsToMove() {
         GameOptions o = MinecraftClient.getInstance().options;
         return o.forwardKey.isPressed() || o.backKey.isPressed() || o.leftKey.isPressed() || o.rightKey.isPressed()
            || o.jumpKey.isPressed() || o.sneakKey.isPressed() || o.attackKey.isPressed();
      }
   }

   private static final class GameSink implements Sink {
      @Override
      public void notice(String text) {
         MinecraftClient.getInstance().execute(() -> ChatOutput.info(text));
      }
   }
}
