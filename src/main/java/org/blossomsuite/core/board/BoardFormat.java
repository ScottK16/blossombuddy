package org.blossomsuite.core.board;

import java.util.Locale;

/** How the leaderboard writes numbers and choices. */
public final class BoardFormat {
   private BoardFormat() {
   }

   /** 1234567 -> "$1,234,567" */
   public static String money(double value) {
      return "$" + String.format(Locale.ROOT, "%,d", Math.round(value));
   }

   /** 1234567 -> "1.23M" (overflow XP gets big quickly, so it is shortened). */
   public static String xp(double value) {
      double v = Math.abs(value);
      if (v >= 1.0E12) {
         return trim(value / 1.0E12) + "T";
      }

      if (v >= 1.0E9) {
         return trim(value / 1.0E9) + "B";
      }

      if (v >= 1.0E6) {
         return trim(value / 1.0E6) + "M";
      }

      if (v >= 1.0E4) {
         return trim(value / 1.0E3) + "K";
      }

      return String.format(Locale.ROOT, "%,d", Math.round(value));
   }

   private static String trim(double v) {
      String s = String.format(Locale.ROOT, "%.2f", v);
      if (s.contains(".")) {
         s = s.replaceAll("0+$", "");
         if (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1);
         }
      }

      return s;
   }

   public static String value(String metric, double value) {
      return "overflow".equals(metric) ? xp(value) : money(value);
   }

   public static String metricLabel(String metric) {
      return "overflow".equals(metric) ? "Overflow XP" : "Earnings";
   }

   public static String periodLabel(String period) {
      return switch (period == null ? "" : period) {
         case "week" -> "This week";
         case "day" -> "Today";
         default -> "All time";
      };
   }

   /** The next metric in the cycle. */
   public static String nextMetric(String metric) {
      return "overflow".equals(metric) ? "money" : "overflow";
   }

   public static String nextPeriod(String period) {
      return switch (period == null ? "" : period) {
         case "all" -> "week";
         case "week" -> "day";
         default -> "all";
      };
   }
}
