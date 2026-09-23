package org.blossomsuite.core.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ChatTimestampFormatTest {
   private static final long T0 = 1_700_000_000_000L; // an arbitrary fixed instant

   @Test
   void showsTheClockTimeAndHowLongAgo() {
      String s = ChatTimestampFormat.format(T0, T0 + 90_000L, ZoneOffset.UTC);
      assertTrue(s.contains(" - "), s);
      assertTrue(s.endsWith("2m ago"), s);
   }

   @Test
   void justSentReadsAsJustNow() {
      assertEquals("just now", ChatTimestampFormat.ago(0L));
      assertEquals("just now", ChatTimestampFormat.ago(20_000L), "rounds down under a minute");
   }

   @Test
   void minutesHoursAndDaysAreAllReadable() {
      assertEquals("1m ago", ChatTimestampFormat.ago(45_000L), "rounds to the nearest minute");
      assertEquals("5m ago", ChatTimestampFormat.ago(5 * 60_000L));
      assertEquals("2h 5m ago", ChatTimestampFormat.ago(2 * 3_600_000L + 5 * 60_000L));
      assertEquals("3d 1h ago", ChatTimestampFormat.ago(3L * 24 * 3_600_000L + 3_600_000L));
   }

   @Test
   void aMessageFromTheFutureIsNeverNegative() {
      assertEquals("just now", ChatTimestampFormat.ago(-5000L));
   }

   @Test
   void theClockPortionUsesTheGivenTimeZone() {
      // an instant that is 12:00:00 UTC
      long noonUtc = ZoneOffset.UTC.getRules().isFixedOffset() ? 0L : 0L;
      long anInstant = java.time.Instant.parse("2024-01-01T12:00:00Z").toEpochMilli();
      assertTrue(ChatTimestampFormat.format(anInstant, anInstant, ZoneOffset.UTC).startsWith("12:00:00"));
      assertTrue(ChatTimestampFormat.format(anInstant, anInstant, ZoneId.of("America/New_York")).startsWith("07:00:00"));
   }
}
