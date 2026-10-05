package org.blossomsuite.core.qol.locks;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.inventory.ContainerInput;
import org.junit.jupiter.api.Test;

class SlotProtectionTest {
   /** clicked slot locked, swap target locked, blockMoves, blockDrop */
   private static boolean block(ContainerInput type, boolean clicked, boolean target, boolean moves, boolean drop) {
      return SlotProtection.shouldBlock(type, clicked, target, moves, drop);
   }

   @Test
   void theDropClickIsBlockedOnlyOnALockedSlotWhenDropBlockingIsOn() {
      assertTrue(block(ContainerInput.THROW, true, false, true, true));
      assertFalse(block(ContainerInput.THROW, false, false, true, true), "an unlocked slot can still be dropped");
      assertFalse(block(ContainerInput.THROW, true, false, true, false), "drop blocking switched off");
      assertTrue(block(ContainerInput.THROW, true, false, false, true), "moves being allowed doesn't allow drops");
   }

   @Test
   void pickingUpOrShiftClickingALockedItemIsBlocked() {
      for (ContainerInput t : new ContainerInput[]{ContainerInput.PICKUP, ContainerInput.QUICK_MOVE}) {
         assertTrue(block(t, true, false, true, true), t.name());
         assertFalse(block(t, false, false, true, true), t.name() + " on an unlocked slot");
         assertFalse(block(t, true, false, false, true), t.name() + " with move blocking off");
      }
   }

   @Test
   void aNumberKeySwapIsBlockedIfEitherSideIsLocked() {
      assertTrue(block(ContainerInput.SWAP, true, false, true, true), "the clicked slot is locked");
      assertTrue(block(ContainerInput.SWAP, false, true, true, true), "the hotbar slot it would swap with is locked");
      assertFalse(block(ContainerInput.SWAP, false, false, true, true));
      assertFalse(block(ContainerInput.SWAP, true, true, false, true), "move blocking off");
   }

   @Test
   void otherClickTypesAreNeverBlocked() {
      for (ContainerInput t : new ContainerInput[]{ContainerInput.QUICK_CRAFT, ContainerInput.CLONE, ContainerInput.PICKUP_ALL}) {
         assertFalse(block(t, true, true, true, true), t.name());
      }
   }

   @Test
   void everyRuleIsFalseWhenNothingIsLocked() {
      for (ContainerInput t : ContainerInput.values()) {
         assertFalse(block(t, false, false, true, true), t.name());
      }
   }
}
