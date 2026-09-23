package org.blossomsuite.core.chat;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** How a chat line's timestamp is shown on hover: the clock time, and how long ago that was. */
public final class ChatTimestampFormat {
   private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);

   private ChatTimestampFormat() {
   }

   /** "14:32:07 - 2m ago" (system time zone), or "14:32:07 - just now" / "... - 3h ago" etc. */
   public static String format(long atMs, long nowMs) {
      return format(atMs, nowMs, ZoneId.systemDefault());
   }

   public static String format(long atMs, long nowMs, ZoneId zone) {
      String clock = CLOCK.format(Instant.ofEpochMilli(atMs).atZone(zone));
      return clock + " - " + ago(nowMs - atMs);
   }

   /** Just the "how long ago", e.g. for a shorter label. */
   public static String ago(long elapsedMs) {
      long m = Math.round(Math.max(0L, elapsedMs) / 60_000.0);
      if (m < 1) {
         return "just now";
      } else if (m < 60) {
         return m + "m ago";
      }

      long h = m / 60;
      return h < 24 ? h + "h " + (m % 60) + "m ago" : (h / 24) + "d " + (h % 24) + "h ago";
   }
}
