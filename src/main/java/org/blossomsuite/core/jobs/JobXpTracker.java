package org.blossomsuite.core.jobs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Per-job XP earned this session, with an hourly rate and time to the next level. Fed from the Jobs Reborn
 * boss bar (see {@code OverflowTracker}), so it knows which job each bit of XP belongs to.
 */
public final class JobXpTracker {
   public static final JobXpTracker INSTANCE = new JobXpTracker();
   private static final long BURST_GAP_MS = 5000L;
   private static final long IDLE_MS = 120_000L;
   private static final long MIN_RATE_WINDOW_MS = 30_000L;

   public record Row(String job, int level, double xp, double maxXp, double sessionXp, double perHour, long etaMs) {
   }

   private static final class Entry {
      String display;
      int level;
      double xp;
      double maxXp;
      double session;
      long firstAtMs;
      long lastAtMs;
      double lastShownGain;
      long lastGainAtMs = Long.MIN_VALUE / 2;
   }

   private final Map<String, Entry> jobs = new HashMap<>();

   JobXpTracker() {
   }

   /**
    * @param shownGain the running "(+N)" the boss bar shows for the current burst of XP; only the increase since
    *                  the last update is new
    */
   public synchronized void record(String job, int level, double xp, double maxXp, double shownGain, long nowMs) {
      String key = job == null ? "" : job.trim().toLowerCase(Locale.ROOT);
      if (key.isEmpty()) {
         return;
      }

      Entry e = this.jobs.computeIfAbsent(key, k -> new Entry());
      e.display = job.trim();
      e.level = level;
      e.xp = xp;
      e.maxXp = maxXp;
      if (shownGain <= 0.0) {
         return;
      }

      double previous = nowMs - e.lastGainAtMs > BURST_GAP_MS ? 0.0 : e.lastShownGain;
      double delta = shownGain >= previous ? shownGain - previous : shownGain;
      e.lastShownGain = shownGain;
      e.lastGainAtMs = nowMs;
      if (delta > 0.0) {
         if (e.session <= 0.0) {
            e.firstAtMs = nowMs;
         }

         e.session += delta;
         e.lastAtMs = nowMs;
      }
   }

   public synchronized List<Row> rows(long nowMs, int max) {
      List<Row> out = new ArrayList<>();
      for (Entry e : this.jobs.values()) {
         if (e.session <= 0.0) {
            continue;
         }

         // once a job goes quiet, freeze its rate at the moment it stopped instead of letting it decay forever
         long end = nowMs - e.lastAtMs > IDLE_MS ? e.lastAtMs : nowMs;
         long window = Math.max(MIN_RATE_WINDOW_MS, end - e.firstAtMs);
         double perHour = e.session / (window / 3_600_000.0);
         double remaining = e.maxXp - e.xp;
         long eta = perHour > 0.0 && remaining > 0.0 && e.maxXp > 0.0 ? (long)(remaining / perHour * 3_600_000.0) : -1L;
         out.add(new Row(e.display, e.level, e.xp, e.maxXp, e.session, perHour, eta));
      }

      out.sort((a, b) -> Double.compare(b.sessionXp(), a.sessionXp()));
      return out.size() > max ? new ArrayList<>(out.subList(0, Math.max(0, max))) : out;
   }

   public synchronized void reset() {
      this.jobs.clear();
   }

   // ------------------------------------------------------------------ formatting

   public static String shortNumber(double v) {
      double a = Math.abs(v);
      if (a >= 1_000_000_000.0) {
         return trim(v / 1_000_000_000.0) + "b";
      } else if (a >= 1_000_000.0) {
         return trim(v / 1_000_000.0) + "m";
      } else if (a >= 1_000.0) {
         return trim(v / 1_000.0) + "k";
      }

      return String.valueOf(Math.round(v));
   }

   private static String trim(double v) {
      String s = String.format(Locale.ROOT, "%.1f", v);
      return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
   }

   public static String duration(long ms) {
      if (ms < 0L) {
         return "-";
      }

      long minutes = ms / 60_000L;
      if (minutes >= 60L * 24L) {
         return (minutes / (60L * 24L)) + "d";
      } else if (minutes >= 60L) {
         return (minutes / 60L) + "h" + (minutes % 60L) + "m";
      }

      return Math.max(1L, minutes) + "m";
   }
}
