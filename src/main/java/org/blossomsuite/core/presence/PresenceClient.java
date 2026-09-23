package org.blossomsuite.core.presence;

import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import net.minecraft.client.MinecraftClient;
import org.blossomsuite.core.chat.ChatOutput;
import org.blossomsuite.core.chat.VanishState;
import org.blossomsuite.core.config.FeatureConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.net.SuiteHttp;
import org.blossomsuite.core.state.SuiteScheduler;
import org.blossomsuite.core.state.SuiteState;
import org.blossomsuite.core.util.JsonUtil;
import org.blossomsuite.core.util.SuiteLog;
import org.blossomsuite.core.util.WorldGate;
import org.blossomsuite.core.xchat.XChatClient;
import org.blossomsuite.core.xchat.XChatModels;

/**
 * The player list: who else is using the mod, and on which realm. Off until the player chooses to appear.
 *
 * <p>Appearing means the relay learns the player's Minecraft name (proven with Mojang's login check, like cross-realm chat, so
 * the access token only ever goes to Mojang) and which realm they are on, and shows both to everyone else who chose to appear.
 * The mod sends a heartbeat every half minute and gets the list back, so you can only see the list while you are on it.
 */
public final class PresenceClient {
   public static final PresenceClient INSTANCE = new PresenceClient(new XChatClient.HttpRelay(), new XChatClient.MojangJoiner(), new GameEnv(), new GameSink(), SuiteScheduler.IO, System::currentTimeMillis);

   static final long UPDATE_EVERY_MS = 25_000L;
   private static final long RENEW_BEFORE_EXPIRY_MS = 60_000L;
   private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

   public static final String ON_NOTICE = "You now appear in the BlossomBuddy player list: your Minecraft name and the realm you are on are shown to other players who also chose to appear (and you can see them). Turn it off any time with /buddy who off.";
   public static final String OFF_NOTICE = "You no longer appear in the BlossomBuddy player list.";
   /** Said once, before anything is sent, because the list is on by default. */
   public static final String DEFAULT_NOTICE = "You appear in the BlossomBuddy player list: your Minecraft name and the realm you are on are shown to other players who are also in it (and you can see them). "
      + "Turn it off with /buddy who off, or the button in /buddy who.";
   /** After that notice, wait this long before the first heartbeat so it can be switched off first. */
   static final long NOTICE_GRACE_MS = 60_000L;

   public enum State {
      OFF,
      CONNECTING,
      READY,
      FAILED
   }

   /** One player who chose to appear. {@code realm} is a lower-case key such as "cherry". */
   public record Player(String name, UUID uuid, String realm) {
   }

   public interface Env {
      boolean enabled();

      void setEnabled(boolean on);

      /** Whether players who appear get a symbol next to their name in the tab list. */
      boolean tabSymbol();

      void setTabSymbol(boolean on);

      /** The one-time "you are in the list" notice has been shown (or the player switched it on by hand). */
      boolean noticeShown();

      void setNoticeShown(boolean shown);

      /** In a world the mod is active in, with a relay address set. */
      boolean canRun();

      /** The realm the player is on now (lower or any case), or "". */
      String realm();

      /** True while the local player is /vanish-ed, so they drop off the list without switching it off. */
      boolean vanished();
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

   private volatile State state = State.OFF;
   private volatile String detail = "";
   private volatile long retryAt = 0L;
   private volatile long nextUpdateAt = 0L;
   /** After the first-run notice, nothing is sent until this time, so the player can switch it off first. */
   private volatile long graceUntil = 0L;
   private volatile String token;
   private long tokenExpiresAt;
   private volatile List<Player> players = List.of();
   private volatile Set<UUID> ids = Set.of();
   private volatile Map<String, Set<String>> namesByRealm = Map.of();

   public PresenceClient(XChatClient.Relay relay, XChatClient.Joiner joiner, Env env, Sink sink, Executor async, java.util.function.LongSupplier clock) {
      this.relay = relay;
      this.joiner = joiner;
      this.env = env;
      this.sink = sink;
      this.async = async;
      this.clock = clock;
   }

   /** Starts the background loop (checks every 5 seconds; a heartbeat goes out every 25). */
   public void init() {
      SuiteScheduler.IO.scheduleWithFixedDelay(() -> {
         try {
            this.step(this.clock.getAsLong());
         } catch (Throwable t) {
            SuiteLog.logger().debug("[presence] step failed: {}", t.toString());
         }
      }, 5L, 5L, TimeUnit.SECONDS);
   }

   // ------------------------------------------------------------------ what the rest of the mod reads

   public boolean enabled() {
      return this.env.enabled();
   }

   public State state() {
      return this.state;
   }

   public List<Player> players() {
      return this.players;
   }

   /** The relay session while connected, else null. Emotes use it: they only work for players who are in the list. */
   public String sessionToken() {
      return this.state == State.READY ? this.token : null;
   }

   /** How many other players listed on the realm we are on (not counting us). */
   public int othersOnMyRealm() {
      String realm = normalizeRealm(this.env.realm());
      if (realm.isEmpty()) {
         return 0;
      }

      int n = 0;
      for (Player p : this.players) {
         if (p.realm().equals(realm)) {
            n++;
         }
      }

      return Math.max(0, n - 1);
   }

   /** One line for the screen and the options page. */
   public String status() {
      return switch (this.state) {
         case OFF -> !this.env.enabled() ? "Off. You don't appear, and you can't see who else is using the mod."
            : this.clock.getAsLong() < this.graceUntil ? "Starting in a moment..." : "Waiting to connect...";
         case CONNECTING -> "Connecting...";
         case READY -> this.players.size() + (this.players.size() == 1 ? " player" : " players") + " using BlossomBuddy right now.";
         case FAILED -> "Not connected: " + this.detail;
      };
   }

   /**
    * True when this tab-list entry is a player who chose to appear (and both this player and the symbol are switched on).
    * By UUID, or by name for players on the realm we are on, since some servers show made-up UUIDs in the tab list.
    */
   public boolean marks(UUID id, String name) {
      if (!this.env.enabled() || !this.env.tabSymbol() || this.state != State.READY) {
         return false;
      }

      if (id != null && this.ids.contains(id)) {
         return true;
      }

      Set<String> here = this.namesByRealm.get(normalizeRealm(this.env.realm()));
      return name != null && here != null && here.contains(name.toLowerCase(Locale.ROOT));
   }

   // ------------------------------------------------------------------ switches

   /** Appear in the list (and see it), or stop. Turning it off removes the player from the relay's list at once. */
   public void setEnabled(boolean on) {
      this.env.setEnabled(on);
      if (on) {
         this.env.setNoticeShown(true); // they know: they just asked for it
         this.wake();
         this.sink.notice(ON_NOTICE);
         return;
      }

      String t = this.token;
      this.token = null;
      this.state = State.OFF;
      this.detail = "";
      this.clearPlayers();
      this.sink.notice(OFF_NOTICE);
      if (t != null) {
         this.leaveRelay(t);
      }
   }

   public void setTabSymbol(boolean on) {
      this.env.setTabSymbol(on);
   }

   /** So a past failure doesn't delay the next attempt after the player changed a setting. */
   public void wake() {
      this.retryAt = 0L;
      this.nextUpdateAt = 0L;
      this.graceUntil = 0L;
   }

   /**
    * Asks for a fresh list soon (used when the player list screen is opened). Opening the screen is the player asking to see
    * the list, so it also ends the short wait after the first-run notice.
    */
   public void refreshSoon() {
      this.nextUpdateAt = 0L;
      this.graceUntil = 0L;
   }

   private void leaveRelay(String token) {
      this.async.execute(() -> {
         try {
            this.relay.post("/v1/presence/leave", JsonUtil.GSON.toJson(Map.of("token", token)));
         } catch (XChatClient.ChatException ignored) {
            // the relay drops a player 90 seconds after their last heartbeat anyway
         }
      });
   }

   // ------------------------------------------------------------------ the loop

   /** One round of work. Public so tests in other packages can drive the loop without waiting on the scheduler. */
   public void step(long now) {
      if (!this.env.enabled()) {
         if (this.state != State.OFF) {
            this.state = State.OFF;
            this.clearPlayers();
         }

         return;
      }

      if (this.env.vanished()) {
         // dropped off the list at once rather than waiting out the relay's 90-second heartbeat timeout;
         // un-vanishing needs no special handling since a null token naturally rejoins on the next round
         if (this.state != State.OFF) {
            this.state = State.OFF;
            this.clearPlayers();
         }

         String t = this.token;
         if (t != null) {
            this.token = null;
            this.leaveRelay(t);
         }

         return;
      }

      if (!this.env.canRun() || now < this.retryAt || now < this.nextUpdateAt || now < this.graceUntil) {
         return;
      }

      if (!this.env.noticeShown()) {
         // on by default, so say so before anything is sent, and leave time to switch it off
         this.env.setNoticeShown(true);
         this.sink.notice(DEFAULT_NOTICE);
         this.graceUntil = now + NOTICE_GRACE_MS;
         return;
      }

      try {
         if (this.token == null || now >= this.tokenExpiresAt - RENEW_BEFORE_EXPIRY_MS) {
            this.join(now);
         }

         this.update(now);
         if (this.token != null) {
            this.nextUpdateAt = now + UPDATE_EVERY_MS; // (a forgotten token is cleared in update(), and rejoins on the next round)
         }
      } catch (XChatClient.ChatException e) {
         this.state = State.FAILED;
         this.detail = e.getMessage();
         this.retryAt = now + e.retryAfterMs();
      }
   }

   private void join(long now) throws XChatClient.ChatException {
      this.state = State.CONNECTING;
      XChatModels.Challenge challenge = parse(this.relay.post("/v1/chat/challenge", "{}"), XChatModels.Challenge.class);
      if (challenge.challengeId == null || challenge.serverId == null) {
         throw new XChatClient.ChatException("The relay sent something unexpected.", 60_000L);
      }

      String name = this.joiner.join(challenge.serverId);
      XChatClient.Reply reply = this.relay.post("/v1/presence/join", JsonUtil.GSON.toJson(Map.of("challengeId", challenge.challengeId, "name", name, "realm", normalizeRealm(this.env.realm()))));
      if (reply.status() == 401) {
         throw new XChatClient.ChatException("Couldn't verify your Minecraft account.", 60_000L);
      }

      if (reply.status() == 403) {
         throw new XChatClient.ChatException("You're blocked from the player list.", 10L * 60_000L);
      }

      XChatModels.Auth auth = parse(reply, XChatModels.Auth.class);
      if (auth.token == null || auth.token.isBlank()) {
         throw new XChatClient.ChatException("The relay sent something unexpected.", 60_000L);
      }

      this.token = auth.token;
      this.tokenExpiresAt = now + (auth.expiresInSeconds == null ? 3600 : auth.expiresInSeconds) * 1000L;
   }

   private void update(long now) throws XChatClient.ChatException {
      XChatClient.Reply reply = this.relay.post("/v1/presence/update", JsonUtil.GSON.toJson(Map.of("token", this.token, "realm", normalizeRealm(this.env.realm()))));
      if (reply.status() == 401) {
         this.token = null; // the relay forgot us: join again on the next round
         this.nextUpdateAt = 0L;
         return;
      }

      if (reply.status() == 429) {
         return;
      }

      if (reply.status() == 403) {
         throw new XChatClient.ChatException("You're blocked from the player list.", 10L * 60_000L);
      }

      Wire wire = parse(reply, Wire.class);
      this.accept(wire.players == null ? List.of() : wire.players);
      this.state = State.READY;
      this.detail = "";
   }

   // ------------------------------------------------------------------ helpers

   /** The shape of the relay's answer. */
   static final class Wire {
      List<WirePlayer> players;
   }

   static final class WirePlayer {
      String name;
      String uuid;
      String realm;
   }

   /** Keeps only well-formed entries: the mod never trusts what the relay sends more than it has to. */
   private void accept(List<WirePlayer> incoming) {
      List<Player> out = new ArrayList<>();
      Set<UUID> idSet = new HashSet<>();
      Map<String, Set<String>> byRealm = new HashMap<>();
      for (WirePlayer w : incoming) {
         UUID id = uuidOf(w == null ? null : w.uuid);
         String realm = normalizeRealm(w == null ? null : w.realm);
         if (w == null || id == null || realm.isEmpty() || w.name == null || !NAME.matcher(w.name).matches()) {
            continue;
         }

         out.add(new Player(w.name, id, realm));
         idSet.add(id);
         byRealm.computeIfAbsent(realm, k -> new HashSet<>()).add(w.name.toLowerCase(Locale.ROOT));
      }

      this.players = List.copyOf(out);
      this.ids = Set.copyOf(idSet);
      this.namesByRealm = Map.copyOf(byRealm);
   }

   private void clearPlayers() {
      this.players = List.of();
      this.ids = Set.of();
      this.namesByRealm = Map.of();
   }

   /** "cherry" from "Cherry " and so on; null-safe. */
   static String normalizeRealm(String realm) {
      return realm == null ? "" : realm.trim().toLowerCase(Locale.ROOT);
   }

   /** The relay sends UUIDs as 32 hex characters; null when it is not one. */
   public static UUID uuidOf(String hex) {
      if (hex == null || !hex.matches("[0-9a-fA-F]{32}")) {
         return null;
      }

      return UUID.fromString(hex.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"));
   }

   private static <T> T parse(XChatClient.Reply reply, Class<T> type) throws XChatClient.ChatException {
      if (reply.status() != 200) {
         throw new XChatClient.ChatException("The relay said " + reply.status() + ".", 30_000L);
      }

      try {
         T parsed = JsonUtil.GSON.fromJson(reply.body(), type);
         if (parsed == null) {
            throw new XChatClient.ChatException("The relay sent something unexpected.", 60_000L);
         }

         return parsed;
      } catch (JsonParseException e) {
         throw new XChatClient.ChatException("The relay sent something unexpected.", 60_000L);
      }
   }

   // ------------------------------------------------------------------ real wiring

   private static final class GameEnv implements Env {
      private static FeatureConfig.Presence p() {
         return FeatureConfig.INSTANCE.presence;
      }

      @Override
      public boolean enabled() {
         return p().enabled;
      }

      @Override
      public void setEnabled(boolean on) {
         p().enabled = on;
         FeatureConfig.markDirty();
      }

      @Override
      public boolean tabSymbol() {
         return p().tabSymbol;
      }

      @Override
      public void setTabSymbol(boolean on) {
         p().tabSymbol = on;
         FeatureConfig.markDirty();
      }

      @Override
      public boolean noticeShown() {
         return p().noticeShown;
      }

      @Override
      public void setNoticeShown(boolean shown) {
         p().noticeShown = shown;
         FeatureConfig.markDirty();
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
      public boolean vanished() {
         return VanishState.vanished;
      }
   }

   private static final class GameSink implements Sink {
      @Override
      public void notice(String text) {
         MinecraftClient.getInstance().execute(() -> ChatOutput.info(text));
      }
   }
}
