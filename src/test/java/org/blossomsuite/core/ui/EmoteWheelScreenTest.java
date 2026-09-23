package org.blossomsuite.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EmoteWheelScreenTest {
   @Test
   void theWheelHasEightSlotsAndMoreEmotesGoOnFurtherPages() {
      assertEquals(8, EmoteWheelScreen.PAGE_SIZE);
      assertEquals(1, EmoteWheelScreen.pageCount(0), "always at least one page");
      assertEquals(1, EmoteWheelScreen.pageCount(1));
      assertEquals(1, EmoteWheelScreen.pageCount(8));
      assertEquals(2, EmoteWheelScreen.pageCount(9));
      assertEquals(2, EmoteWheelScreen.pageCount(14));
      assertEquals(2, EmoteWheelScreen.pageCount(16));
      assertEquals(3, EmoteWheelScreen.pageCount(17));
   }
}
