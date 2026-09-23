package org.blossomsuite.core.jobs.overflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.blossomsuite.core.jobs.overflow.OverflowTracker.Accrual;
import org.blossomsuite.core.jobs.overflow.OverflowTracker.ParsedJob;
import org.blossomsuite.core.jobs.overflow.OverflowTracker.VirtualLevel;
import org.junit.jupiter.api.Test;

class OverflowTrackerTest {
   private static final double EPS = 1.0E-6;

   // ---- parsing the Jobs Reborn boss bar

   @Test
   void parsesStandardBar() {
      ParsedJob job = OverflowTracker.parse("Lvl 200 Miner: 5000/5000 xp (+125) $100");
      assertNotNull(job);
      assertEquals("Miner", job.job());
      assertEquals(200, job.level());
      assertEquals(5000.0, job.xp(), EPS);
      assertEquals(5000.0, job.maxXp(), EPS);
      assertEquals(125.0, job.gain(), EPS);
   }

   @Test
   void parsesThousandsSeparatorsDecimalsAndNonBreakingSpaces() {
      ParsedJob job = OverflowTracker.parse("Lvl 213 Wood Cutter: 1,234.5/90,852 xp (+1,000.25)");
      assertNotNull(job);
      assertEquals("Wood Cutter", job.job());
      assertEquals(213, job.level());
      assertEquals(1234.5, job.xp(), EPS);
      assertEquals(90852.0, job.maxXp(), EPS);
      assertEquals(1000.25, job.gain(), EPS);
   }

   @Test
   void barWithoutGainHasZeroGain() {
      ParsedJob job = OverflowTracker.parse("Lvl 12 Farmer: 10/300 xp");
      assertNotNull(job);
      assertEquals(0.0, job.gain(), EPS);
   }

   @Test
   void ignoresOtherBossBars() {
      assertNull(OverflowTracker.parse("Vote party: 12/30"));
      assertNull(OverflowTracker.parse("Ender Dragon"));
      assertNull(OverflowTracker.parse(""));
      assertNull(OverflowTracker.parse(null));
   }

   @Test
   void onlyMaxedBarsCountAsOverflow() {
      assertTrue(OverflowTracker.parse("Lvl 200 Miner: 5000/5000 xp (+1)").maxed(200));
      assertFalse(OverflowTracker.parse("Lvl 200 Miner: 4999/5000 xp (+1)").maxed(200), "not full yet");
      assertFalse(OverflowTracker.parse("Lvl 199 Miner: 5000/5000 xp (+1)").maxed(200), "below max level");
      assertTrue(OverflowTracker.parse("Lvl 250 Miner: 5000/5000 xp (+1)").maxed(200));
   }

   // ---- turning the running "(+N)" into real gains

   @Test
   void runningGainOnlyCountsTheIncrease() {
      OverflowTracker t = new OverflowTracker();
      Accrual a = t.accrue("cherry", "miner", 125.0, 1_000L);
      assertEquals(125.0, a.delta(), EPS);
      assertEquals(125.0, a.total(), EPS);

      Accrual b = t.accrue("cherry", "miner", 250.0, 2_000L); // bar now says (+250), only 125 is new
      assertEquals(125.0, b.delta(), EPS);
      assertEquals(250.0, b.total(), EPS);
   }

   @Test
   void unchangedGainAddsNothing() {
      OverflowTracker t = new OverflowTracker();
      t.accrue("cherry", "miner", 100.0, 1_000L);
      assertNull(t.accrue("cherry", "miner", 100.0, 1_500L));
      assertEquals(100.0, t.snapshotAll().get("cherry").get("miner"), EPS);
   }

   @Test
   void quietBarStartsAFreshBurst() {
      OverflowTracker t = new OverflowTracker();
      t.accrue("cherry", "miner", 500.0, 1_000L);
      Accrual next = t.accrue("cherry", "miner", 80.0, 1_000L + 6_000L); // > 5s later: (+80) is a new burst
      assertEquals(80.0, next.delta(), EPS);
      assertEquals(580.0, next.total(), EPS);
   }

   @Test
   void droppingGainWithinTheWindowCountsAsNewBurst() {
      OverflowTracker t = new OverflowTracker();
      t.accrue("cherry", "miner", 300.0, 1_000L);
      Accrual next = t.accrue("cherry", "miner", 50.0, 2_000L);
      assertEquals(50.0, next.delta(), EPS);
      assertEquals(350.0, next.total(), EPS);
   }

   @Test
   void totalsAreKeptPerRealmAndPerJob() {
      OverflowTracker t = new OverflowTracker();
      t.accrue("cherry", "miner", 100.0, 1_000L);
      t.accrue("cherry", "farmer", 40.0, 1_000L);
      t.accrue("tulip", "miner", 7.0, 1_000L);
      assertEquals(100.0, t.snapshotAll().get("cherry").get("miner"), EPS);
      assertEquals(40.0, t.snapshotAll().get("cherry").get("farmer"), EPS);
      assertEquals(7.0, t.snapshotAll().get("tulip").get("miner"), EPS);
   }

   @Test
   void resetAllClearsEverything() {
      OverflowTracker t = new OverflowTracker();
      t.accrue("cherry", "miner", 100.0, 1_000L);
      t.resetAll();
      assertTrue(t.snapshotAll().isEmpty());
   }

   // ---- cosmetic levels past max

   @Test
   void noOverflowIsTheFirstLevelPastMax() {
      VirtualLevel v = OverflowTracker.computeVirtualLevel(90_000.0, 0.0, 200);
      assertEquals(201, v.level());
      assertEquals(0.0, v.xpInto(), EPS);
      assertEquals(2.0 * 201 * 201 + 50.0 * 201, v.xpForLevel(), EPS); // scale 1: 200 needs 90,000
   }

   @Test
   void overflowExactlyFillingALevelRollsOver() {
      double level201 = 2.0 * 201 * 201 + 50.0 * 201;
      VirtualLevel v = OverflowTracker.computeVirtualLevel(90_000.0, level201, 200);
      assertEquals(202, v.level());
      assertEquals(0.0, v.xpInto(), EPS);
   }

   @Test
   void curveScalesWithTheXpShownAtMaxLevel() {
      // the server's curve is twice as steep as the reference one, so every level costs twice as much
      VirtualLevel v = OverflowTracker.computeVirtualLevel(180_000.0, 0.0, 200);
      assertEquals(2.0 * (2.0 * 201 * 201 + 50.0 * 201), v.xpForLevel(), EPS);
   }

   @Test
   void partialProgressStaysInLevel() {
      VirtualLevel v = OverflowTracker.computeVirtualLevel(90_000.0, 1_000.0, 200);
      assertEquals(201, v.level());
      assertEquals(1_000.0, v.xpInto(), EPS);
   }

   // ---- small helpers

   @Test
   void formatsNumbers() {
      assertEquals("12,345", OverflowTracker.formatNumber(12345.0));
      assertEquals("12.50", OverflowTracker.formatNumber(12.5));
      assertEquals("0", OverflowTracker.formatNumber(0.0));
   }

   @Test
   void normalisesJobNames() {
      assertEquals("wood cutter", OverflowTracker.normalise("  Wood Cutter "));
      assertEquals("", OverflowTracker.normalise(null));
   }
}
