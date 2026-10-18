package org.blossomsuite.core.tps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TpsTrackerTest {
   private TpsTracker tracker;

   @BeforeEach
   void setUp() {
      this.tracker = new TpsTracker();
   }

   @Test
   void aHealthyServerIsTwenty() {
      for (int i = 0; i < 6; i++) {
         this.tracker.onTimeUpdate(1000L + i * 20L, 10_000L + i * 1000L);
      }

      assertEquals(20.0, this.tracker.tps(15_100L), 0.01);
   }

   @Test
   void aSlowServerShowsHowSlow() {
      // 20 ticks took 1.25 seconds each time: 16 ticks per second
      for (int i = 0; i < 6; i++) {
         this.tracker.onTimeUpdate(1000L + i * 20L, 10_000L + i * 1250L);
      }

      assertEquals(16.0, this.tracker.tps(16_300L), 0.01);
   }

   @Test
   void itNeedsTwoUpdatesBeforeItSaysAnything() {
      assertEquals(-1.0, this.tracker.tps(1_000L));
      this.tracker.onTimeUpdate(100L, 1_000L);
      assertEquals(-1.0, this.tracker.tps(1_500L));
      this.tracker.onTimeUpdate(120L, 2_000L);
      assertEquals(20.0, this.tracker.tps(2_100L), 0.01);
   }

   @Test
   void bunchedUpPacketsCannotShowMoreThanTwenty() {
      this.tracker.onTimeUpdate(0L, 0L);
      this.tracker.onTimeUpdate(20L, 100L); // arrived early: would be 200 on its own
      assertEquals(20.0, this.tracker.tps(150L), 0.01);
   }

   @Test
   void aChangeOfWorldOrALongPauseStartsAgainInsteadOfShowingNonsense() {
      this.tracker.onTimeUpdate(5000L, 1_000L);
      this.tracker.onTimeUpdate(5020L, 2_000L);
      this.tracker.onTimeUpdate(30L, 3_000L); // the tick count went backwards: another world
      assertEquals(-1.0, this.tracker.tps(3_100L));
      this.tracker.onTimeUpdate(50L, 4_000L);
      this.tracker.onTimeUpdate(70L, 60_000L); // a minute gap: not lag
      assertEquals(-1.0, this.tracker.tps(60_100L));
   }

   @Test
   void theNumberIsDroppedWhenUpdatesStopArriving() {
      this.tracker.onTimeUpdate(0L, 0L);
      this.tracker.onTimeUpdate(20L, 1_000L);
      assertTrue(this.tracker.tps(2_000L) > 0);
      assertEquals(-1.0, this.tracker.tps(1_000L + TpsTracker.STALE_MS + 1L));
   }

   @Test
   void onlyTheLastFewReadingsCount() {
      long t = 0L;
      long ticks = 0L;
      for (int i = 0; i < 10; i++) { // ten slow seconds (10 tps)
         t += 2_000L;
         ticks += 20L;
         this.tracker.onTimeUpdate(ticks, t);
      }

      for (int i = 0; i < TpsTracker.SAMPLES; i++) { // then recovered
         t += 1_000L;
         ticks += 20L;
         this.tracker.onTimeUpdate(ticks, t);
      }

      assertEquals(20.0, this.tracker.tps(t + 100L), 0.01);
   }

   @Test
   void formattingAndGrading() {
      assertEquals("19.8", TpsTracker.format(19.8));
      assertEquals("20.0", TpsTracker.format(20.4));
      assertEquals("--", TpsTracker.format(-1.0));
      assertEquals(0, TpsTracker.grade(19.9));
      assertEquals(1, TpsTracker.grade(16.0));
      assertEquals(2, TpsTracker.grade(9.0));
      assertEquals(-1, TpsTracker.grade(-1.0));
   }

   @Test
   void theServersOwnFigureIsUsedWhileFreshAndTheEstimateTakesOverAfter() {
      for (int i = 0; i < 4; i++) {
         this.tracker.onTimeUpdate(i * 20L, 1_000L + i * 1000L); // an estimate of 20
      }

      this.tracker.noteReported(15.68, 5_000L);
      assertEquals(15.68, this.tracker.tps(6_000L), 0.0001);
      // 25 seconds later the figure is stale; the estimate has gone stale too, so there is nothing to show
      assertEquals(-1.0, this.tracker.tps(5_000L + TpsTracker.REPORTED_FRESH_MS + 1L), 0.0001);
   }

   @Test
   void aReportedFigureAloneIsEnoughAndIsClampedToTwenty() {
      this.tracker.noteReported(22.0, 1_000L);
      assertEquals(20.0, this.tracker.tps(2_000L), 0.0001);
      this.tracker.noteReported(-3.0, 3_000L); // nonsense is ignored
      assertEquals(20.0, this.tracker.tps(4_000L), 0.0001);
      assertEquals(-1.0, this.tracker.tps(1_000L + TpsTracker.REPORTED_FRESH_MS + 5_000L), "stale, and no estimate");
   }

   @Test
   void leavingAServerForgetsTheReportedFigure() {
      this.tracker.noteReported(17.0, 1_000L);
      this.tracker.reset();
      assertEquals(-1.0, this.tracker.tps(2_000L));
   }
}
