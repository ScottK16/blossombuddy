package org.blossomsuite.core.stats;

import com.google.gson.JsonParseException;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import org.blossomsuite.core.chat.ChatOutput;
import org.blossomsuite.core.config.FeatureConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.net.SuiteHttp;
import org.blossomsuite.core.state.SuiteScheduler;
import org.blossomsuite.core.state.SuiteState;
import org.blossomsuite.core.util.JsonUtil;
import org.blossomsuite.core.util.SuiteLog;
import org.blossomsuite.core.xchat.XChatClient;
import org.blossomsuite.core.xchat.XChatModels;

/**
 * Usage counts for the developer, as light as possible.
 *
 * <p>By default the mod only says hello now and then with a random ID it made for itself and its own version: no name,
 * and the relay stores no address. The player can turn that off (and the relay then forgets the ID). Separately, the
 * player can choose to share their Minecraft name; it is proven with Mojang's login check, exactly like cross-realm chat,
 * so the access token only ever goes to Mojang.
 */
public final class StatsClient {
   public static final StatsClient INSTANCE = new StatsClient(new XChatClient.HttpRelay(), new XChatClient.MojangJoiner(), new GameEnv(), new GameSink(), SuiteScheduler.IO, System::currentTimeMillis);

   /** How often the mod says hello. */
   static final long PING_EVERY_MS = 6L * 60L * 60L * 1000L;
   /** After telling the player about the counting, wait this long before the first hello so they can switch it off. */
   static final long NOTICE_GRACE_MS = 120_000L;

   static final String NOTICE = "BlossomBuddy counts how many people use it: a random ID and the mod version are sent now and then. No name, and no IP address is stored. "
      + "Turn it off with /buddy stats off. You can also choose to share your username with the developer: /buddy stats name on.";
   static final String NAME_ON = "Your Minecraft name is now stored with your random ID, so the developer can see who uses the mod. Turn it off any time with /buddy stats name off.";

   public interface Env {
      boolean enabled();

      void setEnabled(boolean on);

      boolean shareName();

      void setShareName(boolean on);

      /** In a world the mod is active in, with a relay address set. */
      boolean canRun();

      String installId();

      void setInstallId(String id);

      long lastPingMs();

      void setLastPingMs(long ms);

      /** The name the relay currently has for this install, or "". */
      String namedAs();

      void setNamedAs(String name);

      boolean noticeShown();

      void setNoticeShown(boolean shown);

      /** The player's current Minecraft name. */
      String accountName();

      String version();
   }

   public interface Sink {
      void notice(String text);
   }

   private final XChatClient.Relay relay;
   private final XChatClient.Joiner joiner;
   private final Env env;
   private final Sink sink;
   private final Executor async;
   private final java.util.function.LongSupplier clock;
   private volatile long retryAt = 0L;

   public StatsClient(XChatClient.Relay relay, XChatClient.Joiner joiner, Env env, Sink sink, Executor async, java.util.function.LongSupplier clock) {
      this.relay = relay;
      this.joiner = joiner;
      this.env = env;
      this.sink = sink;
      this.async = async;
      this.clock = clock;
   }

   /** Starts the background loop (once a minute; the actual hello is only every few hours). */
   public void init() {
      SuiteScheduler.IO.scheduleWithFixedDelay(() -> {
         try {
            this.step(this.clock.getAsLong());
         } catch (Throwable t) {
            SuiteLog.logger().debug("[stats] step failed: {}", t.toString());
         }
      }, 20L, 60L, TimeUnit.SECONDS);
   }

   /** One line for the options screen and /buddy stats. */
   public String status() {
      if (!this.env.enabled()) {
         return "Usage counting is OFF. Nothing is sent.";
      }

      return "Usage counting is ON (random ID only). Sharing your username: " + (this.env.shareName() ? "ON" : "OFF") + ".";
   }

   /** Counting on or off. Turning it off also asks the relay to forget this install, and stops sharing the name. */
   public void setEnabled(boolean on) {
      this.env.setEnabled(on);
      if (on) {
         this.wake();
         return;
      }

      String id = this.env.installId();
      this.env.setShareName(false);
      this.env.setNamedAs("");
      this.env.setLastPingMs(0L);
      this.env.setInstallId("");
      if (id.isEmpty()) {
         return;
      }

      this.async.execute(() -> {
         try {
            this.post("/v1/stats/forget", Map.of("installId", id));
            this.sink.notice("Usage counting is off, and this install was removed from the relay's counts.");
         } catch (XChatClient.ChatException e) {
            this.sink.notice("Usage counting is off. The relay couldn't be reached to remove your entry (" + e.getMessage() + "); it only holds a random ID.");
         }
      });
   }

   /** Sharing the Minecraft name on or off (turning it on also turns counting on). */
   public void setShareName(boolean on) {
      if (on) {
         if (!this.env.enabled()) {
            this.env.setEnabled(true);
         }

         this.env.setShareName(true);
         this.wake();
         this.sink.notice(NAME_ON);
         return;
      }

      this.env.setShareName(false);
      String id = this.env.installId();
      if (this.env.namedAs().isEmpty() || id.isEmpty()) {
         this.env.setNamedAs("");
         return;
      }

      this.env.setNamedAs("");
      this.async.execute(() -> {
         try {
            this.post("/v1/stats/unname", Map.of("installId", id));
            this.sink.notice("Your username was removed from the user list. You are still counted anonymously.");
         } catch (XChatClient.ChatException e) {
            this.sink.notice("Username sharing is off, but the relay couldn't be reached to remove it (" + e.getMessage() + ").");
         }
      });
   }

   /** So a past failure doesn't delay the next attempt after the player changed a setting. */
   public void wake() {
      this.retryAt = 0L;
   }

   // ------------------------------------------------------------------ the loop

   void step(long now) {
      if (!this.env.enabled() || !this.env.canRun() || now < this.retryAt) {
         return;
      }

      try {
         if (!this.env.noticeShown()) {
            // say what is counted before the first hello, and leave time to switch it off
            this.env.setNoticeShown(true);
            this.sink.notice(NOTICE);
            this.retryAt = now + NOTICE_GRACE_MS;
            return;
         }

         if (this.env.installId().isEmpty()) {
            this.env.setInstallId(newId());
         }

         if (now - this.env.lastPingMs() >= PING_EVERY_MS) {
            this.post("/v1/stats/ping", Map.of("installId", this.env.installId(), "version", this.env.version()));
            this.env.setLastPingMs(now);
         }

         if (this.env.shareName() && !this.env.accountName().equals(this.env.namedAs())) {
            this.identify();
         }
      } catch (XChatClient.ChatException e) {
         this.retryAt = now + e.retryAfterMs();
      }
   }

   private void identify() throws XChatClient.ChatException {
      XChatClient.Reply challengeReply = this.relay.post("/v1/chat/challenge", "{}");
      XChatModels.Challenge challenge = parse(challengeReply, XChatModels.Challenge.class);
      if (challenge.challengeId == null || challenge.serverId == null) {
         throw new XChatClient.ChatException("The relay sent something unexpected.", 60L * 60_000L);
      }

      String name = this.joiner.join(challenge.serverId);
      XChatClient.Reply reply = this.relay.post("/v1/stats/identify", JsonUtil.GSON.toJson(Map.of("installId", this.env.installId(), "challengeId", challenge.challengeId, "name", name)));
      if (reply.status() == 200) {
         this.env.setNamedAs(name);
         return;
      }

      if (reply.status() == 401) {
         throw new XChatClient.ChatException("Couldn't verify your Minecraft account.", 60L * 60_000L);
      }

      throw new XChatClient.ChatException("The relay said " + reply.status() + ".", 60L * 60_000L);
   }

   // ------------------------------------------------------------------ helpers

   private void post(String path, Map<String, String> body) throws XChatClient.ChatException {
      XChatClient.Reply reply = this.relay.post(path, JsonUtil.GSON.toJson(body));
      if (reply.status() == 429) {
         throw new XChatClient.ChatException("The relay is busy.", 5L * 60_000L);
      }

      if (reply.status() != 200) {
         throw new XChatClient.ChatException("The relay said " + reply.status() + ".", 30L * 60_000L);
      }
   }

   private static <T> T parse(XChatClient.Reply reply, Class<T> type) throws XChatClient.ChatException {
      if (reply.status() != 200) {
         throw new XChatClient.ChatException("The relay said " + reply.status() + ".", 30L * 60_000L);
      }

      try {
         T parsed = JsonUtil.GSON.fromJson(reply.body(), type);
         if (parsed == null) {
            throw new XChatClient.ChatException("The relay sent something unexpected.", 60L * 60_000L);
         }

         return parsed;
      } catch (JsonParseException e) {
         throw new XChatClient.ChatException("The relay sent something unexpected.", 60L * 60_000L);
      }
   }

   /** 32 random hex characters: an ID the mod makes for itself, unrelated to the player. */
   static String newId() {
      return java.util.UUID.randomUUID().toString().replace("-", "");
   }

   // ------------------------------------------------------------------ real wiring

   private static final class GameEnv implements Env {
      private static FeatureConfig.Stats s() {
         return FeatureConfig.INSTANCE.stats;
      }

      @Override
      public boolean enabled() {
         return s().enabled;
      }

      @Override
      public void setEnabled(boolean on) {
         s().enabled = on;
         FeatureConfig.markDirty();
      }

      @Override
      public boolean shareName() {
         return s().shareName;
      }

      @Override
      public void setShareName(boolean on) {
         s().shareName = on;
         FeatureConfig.markDirty();
      }

      @Override
      public boolean canRun() {
         MinecraftClient mc = MinecraftClient.getInstance();
         SuiteHttp http = SuiteState.INSTANCE.http;
         return mc.player != null && SuiteConfig.INSTANCE.isEnabledForCurrentWorld() && http != null && http.enabled();
      }

      @Override
      public String installId() {
         return s().installId == null ? "" : s().installId;
      }

      @Override
      public void setInstallId(String id) {
         s().installId = id;
         FeatureConfig.markDirty();
      }

      @Override
      public long lastPingMs() {
         return s().lastPingMs;
      }

      @Override
      public void setLastPingMs(long ms) {
         s().lastPingMs = ms;
         FeatureConfig.markDirty();
      }

      @Override
      public String namedAs() {
         return s().namedAs == null ? "" : s().namedAs;
      }

      @Override
      public void setNamedAs(String name) {
         s().namedAs = name == null ? "" : name;
         FeatureConfig.markDirty();
      }

      @Override
      public boolean noticeShown() {
         return s().noticeShown;
      }

      @Override
      public void setNoticeShown(boolean shown) {
         s().noticeShown = shown;
         FeatureConfig.markDirty();
      }

      @Override
      public String accountName() {
         Session session = MinecraftClient.getInstance().getSession();
         return session == null ? "" : session.getUsername();
      }

      @Override
      public String version() {
         return FabricLoader.getInstance().getModContainer("blossombuddy").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
      }
   }

   private static final class GameSink implements Sink {
      @Override
      public void notice(String text) {
         MinecraftClient.getInstance().execute(() -> ChatOutput.info(text));
      }
   }
}
