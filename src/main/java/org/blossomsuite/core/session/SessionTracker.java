package org.blossomsuite.core.session;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.board.LeaderboardClient;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.storage.DungeonStore;
import org.blossomsuite.core.util.WorldGate;

/**
 * Watches the game session you are in: how long you have been on, how much you have earned and which realm you were on. Earnings are
 * read as "your running totals now, minus your totals when you joined", so it needs nothing from the chat. When you leave, the
 * session is saved on your computer; {@code /buddy session} adds the saved ones up. Nothing is sent anywhere.
 */
public final class SessionTracker {
   public static final SessionTracker INSTANCE = new SessionTracker(new GameEnv());
   /** A session shorter than this that earned nothing is not worth keeping. */
   static final long MIN_KEEP_MS = 120_000L;
   /** A gap between two ticks longer than this (a freeze, a suspended laptop) is not counted as time on a realm. */
   static final long MAX_TICK_GAP_MS = 10_000L;

   /** What the tracker needs from the game, so it can be tried without one. */
   interface Env {
      long now();

      /** Running totals per realm: {money, overflow XP}. */
      Map<String, double[]> totals();

      /** The realm key you are on, or empty when the mod is not active here. */
      String realm();

      /** {runs, completed} of the dungeon attempts that ended at or after {@code sinceMs}. */
      int[] dungeons(long sinceMs);

      void save(SessionRecord record);

      List<SessionRecord> history();

      void clearHistory();
   }

   private final Env env;
   private boolean running = false;
   private boolean sawActive = false;
   private long startMs;
   private long lastTickMs;
   private Map<String, double[]> baseline = Map.of();
   private Map<String, double[]> lastTotals = Map.of();
   private final Map<String, Long> realmMs = new LinkedHashMap<>();

   SessionTracker(Env env) {
      this.env = env;
   }

   public synchronized boolean running() {
      return this.running;
   }

   public synchronized void begin() {
      if (this.running) {
         return;
      }

      long now = this.env.now();
      this.running = true;
      this.sawActive = false;
      this.startMs = now;
      this.lastTickMs = now;
      this.baseline = copy(this.env.totals());
      this.lastTotals = this.baseline;
      this.realmMs.clear();
   }

   /** Call about once a second while connected. */
   public synchronized void tick() {
      if (!this.running) {
         return;
      }

      long now = this.env.now();
      long gap = now - this.lastTickMs;
      this.lastTickMs = now;
      String realm = this.env.realm();
      if (!realm.isEmpty()) {
         this.sawActive = true;
         if (gap > 0L && gap <= MAX_TICK_GAP_MS) {
            this.realmMs.merge(realm, gap, Long::sum);
         }
      }
   }

   /** The session so far, as if it ended now; null when none is running. */
   public synchronized SessionRecord live() {
      return this.running ? this.snapshot(this.env.now()) : null;
   }

   /** Ends the session and saves it if it was worth keeping. Returns the saved record, or null. */
   public synchronized SessionRecord end() {
      if (!this.running) {
         return null;
      }

      SessionRecord record = this.snapshot(this.env.now());
      this.running = false;
      boolean worthKeeping = this.sawActive && (record.durationMs() >= MIN_KEEP_MS || record.money() > 0.0 || record.overflowXp() > 0.0);
      if (!worthKeeping) {
         return null;
      }

      this.env.save(record);
      return record;
   }

   public List<SessionRecord> history() {
      return this.env.history();
   }

   public void clearHistory() {
      this.env.clearHistory();
   }

   private SessionRecord snapshot(long now) {
      double money = 0.0;
      double overflow = 0.0;
      Map<String, double[]> latest = this.env.totals();
      if (latest.isEmpty() && !this.baseline.isEmpty()) {
         latest = this.lastTotals; // the totals were busy for a moment: use the last good reading rather than showing nothing earned
      } else {
         this.lastTotals = copy(latest);
      }

      for (Map.Entry<String, double[]> e : latest.entrySet()) {
         double[] before = this.baseline.getOrDefault(e.getKey(), new double[2]);
         money += Math.max(0.0, e.getValue()[0] - before[0]);
         overflow += Math.max(0.0, e.getValue()[1] - before[1]);
      }

      int[] runs = this.env.dungeons(this.startMs);
      return new SessionRecord(this.startMs, now, money, overflow, runs[0], runs[1], new LinkedHashMap<>(this.realmMs));
   }

   private static Map<String, double[]> copy(Map<String, double[]> totals) {
      Map<String, double[]> out = new TreeMap<>();
      for (Map.Entry<String, double[]> e : totals.entrySet()) {
         out.put(e.getKey(), new double[]{e.getValue()[0], e.getValue()[1]});
      }

      return out;
   }

   // ------------------------------------------------------------------ in the game

   private static final class GameEnv implements Env {
      private volatile SessionStore store;

      private SessionStore store() {
         SessionStore s = this.store;
         if (s == null) {
            Path dir = FabricLoader.getInstance().getConfigDir().resolve(SuiteRuntime.profile().modId());
            s = new SessionStore(dir.resolve("sessions.jsonl"));
            this.store = s;
         }

         return s;
      }

      @Override
      public long now() {
         return System.currentTimeMillis();
      }

      @Override
      public Map<String, double[]> totals() {
         return LeaderboardClient.readGameTotals();
      }

      @Override
      public String realm() {
         Minecraft mc = Minecraft.getInstance();
         if (mc == null || mc.player == null || !SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
            return "";
         }

         String server = WorldGate.Server;
         return server == null || server.isBlank() ? "" : server.trim().toLowerCase(Locale.ROOT);
      }

      @Override
      public int[] dungeons(long sinceMs) {
         int runs = 0;
         int done = 0;
         for (List<DungeonStore.DungeonAttempt> list : DungeonStore.getRecent12ByServer().values()) {
            for (DungeonStore.DungeonAttempt a : list) {
               if (a.endedAtMs() >= sinceMs) {
                  runs++;
                  if (a.completed()) {
                     done++;
                  }
               }
            }
         }

         return new int[]{runs, done};
      }

      @Override
      public void save(SessionRecord record) {
         this.store().append(record);
      }

      @Override
      public List<SessionRecord> history() {
         return this.store().load(System.currentTimeMillis());
      }

      @Override
      public void clearHistory() {
         this.store().clear();
      }
   }
}
