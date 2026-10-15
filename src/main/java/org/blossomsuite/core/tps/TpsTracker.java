package org.blossomsuite.core.tps;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * Estimates how fast the server is running. A server tells every player the world's tick count about once a second (every 20 ticks);
 * comparing how many ticks passed with how much real time passed gives ticks per second. A healthy server is 20.0.
 *
 * <p>It is an estimate: it is only as steady as your connection, so it averages the last few readings and ignores gaps (joining,
 * changing server) that would give nonsense.
 */
public final class TpsTracker {
   public static final TpsTracker INSTANCE = new TpsTracker();
   public static final double MAX_TPS = 20.0;
   static final int SAMPLES = 5;
   /** A gap longer than this between two updates is a pause or a change of server, not lag. */
   static final long MAX_GAP_MS = 15_000L;
   /** No update for this long and the number is no longer trusted. */
   public static final long STALE_MS = 6_000L;

   private final Deque<Double> samples = new ArrayDeque<>();
   private long lastGameTime = -1L;
   private long lastAtMs = 0L;

   /** Call when a time update arrives: the world's tick count and the clock now (milliseconds). */
   public synchronized void onTimeUpdate(long gameTime, long nowMs) {
      if (this.lastGameTime >= 0L) {
         long ticks = gameTime - this.lastGameTime;
         long elapsed = nowMs - this.lastAtMs;
         if (ticks <= 0L || elapsed <= 0L || elapsed > MAX_GAP_MS) {
            this.samples.clear(); // went backwards or paused: a different world or a long stall, start again
         } else {
            double tps = Math.max(0.0, Math.min(MAX_TPS, ticks * 1000.0 / elapsed));
            this.samples.addLast(tps);
            while (this.samples.size() > SAMPLES) {
               this.samples.removeFirst();
            }
         }
      }

      this.lastGameTime = gameTime;
      this.lastAtMs = nowMs;
   }

   /** The recent average, or -1 when there is not enough to say yet (or nothing has arrived for a while). */
   public synchronized double tps(long nowMs) {
      if (this.samples.isEmpty() || nowMs - this.lastAtMs > STALE_MS) {
         return -1.0;
      }

      double sum = 0.0;
      for (double s : this.samples) {
         sum += s;
      }

      return sum / this.samples.size();
   }

   public synchronized void reset() {
      this.samples.clear();
      this.lastGameTime = -1L;
      this.lastAtMs = 0L;
   }

   /** "19.8", or "--" when unknown. */
   public static String format(double tps) {
      return tps < 0.0 ? "--" : String.format(Locale.ROOT, "%.1f", Math.min(MAX_TPS, tps));
   }

   /** 0 = good (green), 1 = wobbling (yellow), 2 = bad (red), -1 = unknown. */
   public static int grade(double tps) {
      if (tps < 0.0) {
         return -1;
      }

      return tps >= 18.0 ? 0 : tps >= 15.0 ? 1 : 2;
   }
}
