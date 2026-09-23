package org.blossomsuite.core.hud;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Plays the swap clicks the cycler would send against a model of one inventory column, to prove the rotation ends
 * up where it says and nothing is lost or duplicated.
 */
class HotbarCyclerTest {
   /** hotbar slot, row 1 (inventory slot 27+col), row 2 (18+col) for a single column. */
   private static String[] column(String hotbar, String row1, String row2) {
      return new String[]{hotbar, row1, row2};
   }

   private static String[] play(String[] col, int rows, boolean up) {
      String[] c = col.clone();
      for (int slot : HotbarCycler.order(rows, up, 0)) {
         int index = slot == ExtraHotbarHud.inventoryIndex(1, 0) ? 1 : 2;
         String tmp = c[0]; // SWAP click: hotbar slot <-> clicked slot
         c[0] = c[index];
         c[index] = tmp;
      }

      return c;
   }

   @Test
   void slotIdsMatchThePlayerInventoryLayout() {
      assertEquals(27, ExtraHotbarHud.inventoryIndex(1, 0), "row above the hotbar starts at slot 27");
      assertEquals(35, ExtraHotbarHud.inventoryIndex(1, 8));
      assertEquals(18, ExtraHotbarHud.inventoryIndex(2, 0), "next row up starts at slot 18");
   }

   @Test
   void doubleHotbarSwapsTheTwoRowsEitherWay() {
      assertArrayEquals(new String[]{"B", "A", "C"}, play(column("A", "B", "C"), 1, true));
      assertArrayEquals(new String[]{"B", "A", "C"}, play(column("A", "B", "C"), 1, false));
   }

   @Test
   void tripleHotbarRotatesUp() {
      // the row above comes down into the hotbar, the third row moves up one, the old hotbar goes to the top
      assertArrayEquals(new String[]{"B", "C", "A"}, play(column("A", "B", "C"), 2, true));
   }

   @Test
   void tripleHotbarRotatesDown() {
      assertArrayEquals(new String[]{"C", "A", "B"}, play(column("A", "B", "C"), 2, false));
   }

   @Test
   void goingUpThenDownRestoresEverything() {
      String[] start = column("A", "B", "C");
      assertArrayEquals(start, play(play(start, 2, true), 2, false));
      assertArrayEquals(start, play(play(start, 1, true), 1, false));
   }

   @Test
   void threeCyclesInOneDirectionComeFullCircle() {
      String[] c = column("A", "B", "C");
      for (int i = 0; i < 3; i++) {
         c = play(c, 2, true);
      }

      assertArrayEquals(column("A", "B", "C"), c);
   }

   @Test
   void aColumnWithAnyLockedSlotIsLeftAlone() {
      assertFalse(HotbarCycler.columnFree(2, 3, i -> i == 3), "the hotbar slot itself is locked");
      assertFalse(HotbarCycler.columnFree(2, 3, i -> i == 30), "the slot above it (row 1) is locked");
      assertFalse(HotbarCycler.columnFree(2, 3, i -> i == 21), "row 2 matters for the triple hotbar");
      assertTrue(HotbarCycler.columnFree(1, 3, i -> i == 21), "but not for the double hotbar");
      assertTrue(HotbarCycler.columnFree(2, 3, i -> i == 4 || i == 31), "locks in other columns don't matter");
   }
}
