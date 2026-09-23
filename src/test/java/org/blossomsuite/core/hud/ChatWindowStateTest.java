package org.blossomsuite.core.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChatWindowStateTest {
   @Test
   void scrollingIsHeldBetweenTheOldestAndTheNewestLine() {
      ChatWindowState s = new ChatWindowState();
      assertFalse(s.scrollBy(-3, 5), "already at the newest");
      assertTrue(s.scrollBy(2, 5));
      assertEquals(2, s.scroll());
      assertTrue(s.scrollBy(100, 5));
      assertEquals(4, s.scroll(), "never past the oldest line");
      assertTrue(s.scrollBy(-100, 5));
      assertEquals(0, s.scroll());
   }

   @Test
   void aReaderScrolledBackKeepsTheirPlaceWhileNewLinesArrive() {
      ChatWindowState s = new ChatWindowState();
      s.update(10);
      s.scrollBy(3, 10);
      s.update(12);
      assertEquals(5, s.scroll(), "moved back by the two new lines");
   }

   @Test
   void aFollowingReaderKeepsFollowing() {
      ChatWindowState s = new ChatWindowState();
      s.update(3);
      s.update(4);
      s.update(9);
      assertEquals(0, s.scroll());
   }

   @Test
   void theFirstLookNeverJumpsAnywhere() {
      ChatWindowState s = new ChatWindowState();
      s.update(50);
      assertEquals(0, s.scroll());
   }

   @Test
   void ifLinesDisappearThePositionStaysInRange() {
      ChatWindowState s = new ChatWindowState();
      s.update(10);
      s.scrollBy(8, 10);
      s.update(3);
      assertEquals(2, s.scroll());
      s.update(0);
      assertEquals(0, s.scroll());
   }

   @Test
   void resetGoesBackToTheNewest() {
      ChatWindowState s = new ChatWindowState();
      s.update(10);
      s.scrollBy(4, 10);
      s.reset();
      assertEquals(0, s.scroll());
   }
}
