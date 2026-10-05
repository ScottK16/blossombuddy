package org.blossomsuite.core.alts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.blossomsuite.core.config.FeatureConfig;
import org.junit.jupiter.api.Test;

class AltGuardTest {
   @Test
   void leavesAtTheLimitAndAboveButNotBelow() {
      assertFalse(AltGuard.shouldLeave(true, 60, 59));
      assertTrue(AltGuard.shouldLeave(true, 60, 60));
      assertTrue(AltGuard.shouldLeave(true, 60, 85));
   }

   @Test
   void neverLeavesWhenSwitchedOff() {
      assertFalse(AltGuard.shouldLeave(false, 60, 200));
   }

   @Test
   void offByDefaultWithTheLimitAtSixty() {
      FeatureConfig.AltAccount fresh = new FeatureConfig.AltAccount();
      assertFalse(fresh.leaveWhenCrowded, "must be off until the player turns it on");
      assertEquals(60, fresh.playerLimit);
   }

   @Test
   void aBadSavedLimitIsPulledIntoRange() {
      assertEquals(AltGuard.MIN_LIMIT, AltGuard.clampLimit(-5));
      assertEquals(AltGuard.MAX_LIMIT, AltGuard.clampLimit(9999));
      assertEquals(60, AltGuard.clampLimit(60));
   }
}
