package org.blossomsuite.core.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class JobXpTrackerTest {
   private static final double EPS = 1.0E-6;
   private static final long T0 = 10_000_000L;

   @Test
   void runningGainOnlyCountsTheIncrease() {
      JobXpTracker t = new JobXpTracker();
      t.record("Miner", 100, 500, 1000, 50, T0);
      t.record("Miner", 100, 620, 1000, 120, T0 + 1_000); // bar now says (+120): only 70 is new
      assertEquals(120.0, t.rows(T0 + 2_000, 5).get(0).sessionXp(), EPS);
      t.record("Miner", 100, 650, 1000, 30, T0 + 10_000); // quiet for > 5s: a fresh burst
      assertEquals(150.0, t.rows(T0 + 11_000, 5).get(0).sessionXp(), EPS);
   }

   @Test
   void jobsAreTrackedSeparatelyAndSortedByXp() {
      JobXpTracker t = new JobXpTracker();
      t.record("Farmer", 50, 10, 100, 40, T0);
      t.record("Miner", 100, 10, 100, 900, T0);
      t.record("Hunter", 20, 10, 100, 5, T0);
      List<JobXpTracker.Row> rows = t.rows(T0 + 1_000, 5);
      assertEquals(List.of("Miner", "Farmer", "Hunter"), rows.stream().map(JobXpTracker.Row::job).toList());
      assertEquals(2, t.rows(T0 + 1_000, 2).size(), "limited to the requested number of rows");
   }

   @Test
   void barUpdatesWithNoGainDoNotStartATracker() {
      JobXpTracker t = new JobXpTracker();
      t.record("Miner", 100, 10, 100, 0, T0);
      assertTrue(t.rows(T0 + 1_000, 5).isEmpty());
   }

   @Test
   void hourlyRateAndTimeToNextLevel() {
      JobXpTracker t = new JobXpTracker();
      t.record("Miner", 100, 500, 1000, 1000, T0);
      t.record("Miner", 100, 500, 1000, 2000, T0 + 1_800_000); // half an hour later, 2000 more in a new burst
      long now = T0 + 1_800_000 + 10_000;
      JobXpTracker.Row row = t.rows(now, 5).get(0);
      assertEquals(3000.0, row.sessionXp(), EPS);
      double hours = (now - T0) / 3_600_000.0;
      assertEquals(3000.0 / hours, row.perHour(), 1.0E-3);
      // 500 XP still to go at that rate
      assertEquals((long)(500.0 / row.perHour() * 3_600_000.0), row.etaMs());
   }

   @Test
   void theRateFreezesOnceAJobGoesIdle() {
      JobXpTracker t = new JobXpTracker();
      t.record("Miner", 100, 500, 1000, 1000, T0);
      t.record("Miner", 100, 500, 1000, 2000, T0 + 600_000);
      double soon = t.rows(T0 + 600_000 + 200_000, 5).get(0).perHour();
      double muchLater = t.rows(T0 + 600_000 + 6_000_000, 5).get(0).perHour();
      assertEquals(soon, muchLater, EPS);
   }

   @Test
   void aBrandNewJobDoesNotShowAWildRate() {
      JobXpTracker t = new JobXpTracker();
      t.record("Miner", 100, 500, 1000, 100, T0);
      // one hit, one second in: the rate is measured over at least 30 seconds, not extrapolated from 1s
      assertEquals(100.0 / (30_000 / 3_600_000.0), t.rows(T0 + 1_000, 5).get(0).perHour(), 1.0E-3);
   }

   @Test
   void resetClearsEverything() {
      JobXpTracker t = new JobXpTracker();
      t.record("Miner", 100, 500, 1000, 100, T0);
      t.reset();
      assertTrue(t.rows(T0, 5).isEmpty());
   }

   @Test
   void formatsNumbersAndDurations() {
      assertEquals("999", JobXpTracker.shortNumber(999));
      assertEquals("1.2k", JobXpTracker.shortNumber(1234));
      assertEquals("12k", JobXpTracker.shortNumber(12_000));
      assertEquals("1.5m", JobXpTracker.shortNumber(1_500_000));
      assertEquals("-", JobXpTracker.duration(-1));
      assertEquals("1m", JobXpTracker.duration(20_000));
      assertEquals("12m", JobXpTracker.duration(12 * 60_000L));
      assertEquals("1h5m", JobXpTracker.duration(65 * 60_000L));
      assertEquals("2d", JobXpTracker.duration(49 * 3_600_000L));
   }
}
