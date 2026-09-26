package org.blossomsuite.core.visibility;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.blossomsuite.core.vote.VotePartySnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PlayerVisibilityTest {
   private static final long NOW = 1_000_000L;

   @AfterEach
   void reset() {
      PlayerVisibility.set(false);
   }

   @Test
   void aRecentOngoingPartyCountsAsRunning() {
      assertTrue(PlayerVisibility.partyRunning(new VotePartySnapshot("cherry", "Cherry", 150, 150, true, NOW - 5_000L), NOW));
   }

   @Test
   void noReadingAPartyThatIsNotOnOrAStaleReadingDoesNotCount() {
      assertFalse(PlayerVisibility.partyRunning(null, NOW));
      assertFalse(PlayerVisibility.partyRunning(new VotePartySnapshot("cherry", "Cherry", 120, 150, false, NOW - 5_000L), NOW));
      assertFalse(PlayerVisibility.partyRunning(new VotePartySnapshot("cherry", "Cherry", 150, 150, true, NOW - 5 * 60_000L), NOW), "a reading from minutes ago must not keep everyone hidden");
   }

   @Test
   void theSwitchFlipsAndClears() {
      assertFalse(PlayerVisibility.manual());
      assertTrue(PlayerVisibility.toggle());
      assertTrue(PlayerVisibility.manual());
      assertTrue(PlayerVisibility.hidden());
      PlayerVisibility.set(false);
      assertFalse(PlayerVisibility.manual());
   }
}
