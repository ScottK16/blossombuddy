package org.blossomsuite.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BoardFormatTest {
   @Test
   void moneyHasADollarSignAndThousandsSeparators() {
      assertEquals("$0", BoardFormat.money(0));
      assertEquals("$1,234,567", BoardFormat.money(1_234_567));
      assertEquals("$1,000", BoardFormat.money(999.6));
   }

   @Test
   void overflowXpIsShortenedOnceItGetsBig() {
      assertEquals("950", BoardFormat.xp(950));
      assertEquals("9,999", BoardFormat.xp(9_999));
      assertEquals("12.35K", BoardFormat.xp(12_345));
      assertEquals("1.5M", BoardFormat.xp(1_500_000));
      assertEquals("2B", BoardFormat.xp(2_000_000_000));
      assertEquals("3.2T", BoardFormat.xp(3_200_000_000_000.0));
   }

   @Test
   void theMetricPicksTheFormat() {
      assertEquals("$5", BoardFormat.value("money", 5));
      assertEquals("1.5M", BoardFormat.value("overflow", 1_500_000));
      assertEquals("$5", BoardFormat.value(null, 5), "anything unknown is read as earnings");
   }

   @Test
   void choicesCycleThroughEveryOptionAndComeBackRound() {
      assertEquals("overflow", BoardFormat.nextMetric("money"));
      assertEquals("money", BoardFormat.nextMetric("overflow"));
      assertEquals("week", BoardFormat.nextPeriod("all"));
      assertEquals("day", BoardFormat.nextPeriod("week"));
      assertEquals("all", BoardFormat.nextPeriod("day"));
      assertEquals("Today", BoardFormat.periodLabel("day"));
      assertEquals("Overflow XP", BoardFormat.metricLabel("overflow"));
   }
}
