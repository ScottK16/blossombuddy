package org.blossomsuite.core.qol.locks;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.screen.slot.SlotActionType;
import org.junit.jupiter.api.Test;

class SlotProtectionTest {
   /** clicked slot locked, swap target locked, blockMoves, blockDrop */
   private static boolean block(SlotActionType type, boolean clicked, boolean target, boolean moves, boolean drop) {
      return SlotProtection.shouldBlock(type, clicked, target, moves, drop);
   }

   @Test
   void theDropClickIsBlockedOnlyOnALockedSlotWhenDropBlockingIsOn() {
      assertTrue(block(SlotActionType.THROW, true, false, true, true));
      assertFalse(block(SlotActionType.THROW, false, false, true, true), "an unlocked slot can still be dropped");
      assertFalse(block(SlotActionType.THROW, true, false, true, false), "drop blocking switched off");
      assertTrue(block(SlotActionType.THROW, true, false, false, true), "moves being allowed doesn't allow drops");
   }

   @Test
   void pickingUpOrShiftClickingALockedItemIsBlocked() {
      for (SlotActionType t : new SlotActionType[]{SlotActionType.PICKUP, SlotActionType.QUICK_MOVE}) {
         assertTrue(block(t, true, false, true, true), t.name());
         assertFalse(block(t, false, false, true, true), t.name() + " on an unlocked slot");
         assertFalse(block(t, true, false, false, true), t.name() + " with move blocking off");
      }
   }

   @Test
   void aNumberKeySwapIsBlockedIfEitherSideIsLocked() {
      assertTrue(block(SlotActionType.SWAP, true, false, true, true), "the clicked slot is locked");
      assertTrue(block(SlotActionType.SWAP, false, true, true, true), "the hotbar slot it would swap with is locked");
      assertFalse(block(SlotActionType.SWAP, false, false, true, true));
      assertFalse(block(SlotActionType.SWAP, true, true, false, true), "move blocking off");
   }

   @Test
   void otherClickTypesAreNeverBlocked() {
      for (SlotActionType t : new SlotActionType[]{SlotActionType.QUICK_CRAFT, SlotActionType.CLONE, SlotActionType.PICKUP_ALL}) {
         assertFalse(block(t, true, true, true, true), t.name());
      }
   }

   @Test
   void everyRuleIsFalseWhenNothingIsLocked() {
      for (SlotActionType t : SlotActionType.values()) {
         assertFalse(block(t, false, false, true, true), t.name());
      }
   }
}
