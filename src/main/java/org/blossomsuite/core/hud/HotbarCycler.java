package org.blossomsuite.core.hud;

import java.util.function.IntPredicate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.slot.SlotActionType;
import org.blossomsuite.core.chat.ChatOutput;
import org.blossomsuite.core.config.FeatureConfig;
import org.blossomsuite.core.qol.locks.SlotProtection;

/**
 * Swaps the hotbar with the inventory rows above it. Everything is done with hotbar-swap clicks on the player
 * inventory (the same clicks the number keys make inside the inventory screen). A column with any locked slot in
 * it is left exactly as it is.
 */
public final class HotbarCycler {
   private HotbarCycler() {
   }

   private static boolean ready(MinecraftClient client) {
      return client != null && client.player != null && client.interactionManager != null && client.currentScreen == null;
   }

   private static int rows() {
      return Math.max(1, Math.min(2, FeatureConfig.INSTANCE.hotbar.extraRows));
   }

   /**
    * Rotates through all the rows.
    *
    * @param up true brings the row above the hotbar down into it (row1 -> hotbar); false sends the hotbar up
    */
   public static void cycle(MinecraftClient client, boolean up) {
      if (!ready(client)) {
         return;
      }

      int rows = rows();
      int syncId = client.player.playerScreenHandler.syncId;
      for (int col = 0; col < 9; col++) {
         if (!columnFree(rows, col, SlotProtection::isLocked)) {
            continue;
         }

         for (int slot : order(rows, up, col)) {
            client.interactionManager.clickSlot(syncId, slot, col, SlotActionType.SWAP, client.player);
         }
      }
   }

   /**
    * Flips the hotbar with one row: press once to go to that hotbar, press again to come back.
    *
    * @param row 1 = the row directly above the hotbar ("hotbar 2"), 2 = the row above that ("hotbar 3")
    */
   public static void swapWithRow(MinecraftClient client, int row) {
      if (!ready(client)) {
         return;
      }

      if (row > rows()) {
         ChatOutput.info("Turn on the triple hotbar in options to use row " + row + ".");
         return;
      }

      int syncId = client.player.playerScreenHandler.syncId;
      for (int col = 0; col < 9; col++) {
         int slot = ExtraHotbarHud.inventoryIndex(row, col);
         if (SlotProtection.isLocked(col) || SlotProtection.isLocked(slot)) {
            continue;
         }

         client.interactionManager.clickSlot(syncId, slot, col, SlotActionType.SWAP, client.player);
      }
   }

   /** True when no slot in this column (the hotbar slot and each extra row) is locked. */
   static boolean columnFree(int rows, int col, IntPredicate locked) {
      if (locked.test(col)) {
         return false;
      }

      for (int row = 1; row <= rows; row++) {
         if (locked.test(ExtraHotbarHud.inventoryIndex(row, col))) {
            return false;
         }
      }

      return true;
   }

   /**
    * The inventory slot ids to swap with hotbar slot {@code col}, in order.
    * Double: one swap either way. Triple, up: R0<-R1<-R2<-R0 needs row 2 first, then row 1; down is the reverse.
    */
   static int[] order(int rows, boolean up, int col) {
      int row1 = ExtraHotbarHud.inventoryIndex(1, col);
      if (rows == 1) {
         return new int[]{row1};
      }

      int row2 = ExtraHotbarHud.inventoryIndex(2, col);
      return up ? new int[]{row2, row1} : new int[]{row1, row2};
   }
}
