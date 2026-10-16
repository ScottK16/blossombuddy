package org.blossomsuite.core.session;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Adds sessions up: today, this week, all of them. */
public final class SessionStats {
   private SessionStats() {
   }

   public record Totals(int sessions, long timeMs, double money, double overflowXp, int dungeonRuns, int dungeonDone) {
      public static final Totals NONE = new Totals(0, 0L, 0.0, 0.0, 0, 0);

      public double moneyPerHour() {
         return this.timeMs < 60_000L ? 0.0 : this.money / (this.timeMs / 3_600_000.0);
      }

      public double overflowPerHour() {
         return this.timeMs < 60_000L ? 0.0 : this.overflowXp / (this.timeMs / 3_600_000.0);
      }
   }

   public record Day(LocalDate date, double money, long timeMs) {
   }

   public static Totals of(SessionRecord r) {
      return new Totals(1, r.durationMs(), r.money(), r.overflowXp(), r.dungeonRuns(), r.dungeonDone());
   }

   public static Totals plus(Totals a, Totals b) {
      return new Totals(a.sessions() + b.sessions(), a.timeMs() + b.timeMs(), a.money() + b.money(), a.overflowXp() + b.overflowXp(), a.dungeonRuns() + b.dungeonRuns(), a.dungeonDone() + b.dungeonDone());
   }

   /** Sessions that ended at or after {@code fromMs}. */
   public static Totals since(List<SessionRecord> records, long fromMs) {
      Totals sum = Totals.NONE;
      for (SessionRecord r : records) {
         if (r.endMs() >= fromMs) {
            sum = plus(sum, of(r));
         }
      }

      return sum;
   }

   public static long startOfDay(long nowMs, ZoneId zone) {
      return Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli();
   }

   /** The last {@code days} days, oldest first, today last. A session counts on the day it ended. */
   public static List<Day> lastDays(List<SessionRecord> records, long nowMs, ZoneId zone, int days) {
      LocalDate today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate();
      List<Day> out = new ArrayList<>();
      for (int i = days - 1; i >= 0; i--) {
         LocalDate date = today.minusDays(i);
         double money = 0.0;
         long time = 0L;
         for (SessionRecord r : records) {
            if (Instant.ofEpochMilli(r.endMs()).atZone(zone).toLocalDate().equals(date)) {
               money += r.money();
               time += r.durationMs();
            }
         }

         out.add(new Day(date, money, time));
      }

      return out;
   }

   /** "1h 23m", "42m", "35s". */
   public static String duration(long ms) {
      long s = Math.max(0L, ms / 1000L);
      long h = s / 3600L;
      long m = s % 3600L / 60L;
      if (h > 0L) {
         return h + "h " + m + "m";
      }

      return m > 0L ? m + "m" : s + "s";
   }

   public static String realmLabel(String key) {
      if (key == null || key.isBlank()) {
         return "Unknown";
      }

      return key.substring(0, 1).toUpperCase(Locale.ROOT) + key.substring(1);
   }
}
