package org.blossomsuite.core.qol.locks;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.blossomsuite.core.config.FeatureConfig;
import org.blossomsuite.core.config.QolConfig;
import org.blossomsuite.core.config.SuiteConfig;

/**
 * Makes locked inventory slots actually protect their item: no dropping with Q, and no moving it with clicks.
 *
 * <p>"Locked" is the mask set in the Inventory Slot Locks screen (inventory indices 0-8 hotbar, 9-35 main).
 * Locked slots used to be skipped only by the mod's own sort and deposit; now the game's own drop key and slot
 * clicks are stopped too, before anything is sent to the server.
 */
public final class SlotProtection {
   private static final long NOTICE_GAP_MS = 800L;
   private static long lastNoticeMs = 0L;

   private SlotProtection() {
   }

   private static boolean active() {
      return SuiteConfig.INSTANCE.isEnabledForCurrentWorld();
   }

   /** Is this inventory index locked (hotbar 0-8, main 9-35)? */
   public static boolean isLocked(int inventoryIndex) {
      QolConfig q = SuiteConfig.INSTANCE.QolConfig;
      return q != null && q.isInventorySortSlotLocked(inventoryIndex);
   }

   /** A locked slot that actually holds something: the case worth protecting. */
   private static boolean lockedWithItem(PlayerInventory inv, int inventoryIndex) {
      return inventoryIndex >= 0 && inventoryIndex <= 35 && isLocked(inventoryIndex) && !inv.getStack(inventoryIndex).isEmpty();
   }

   /** The Q key: blocked when the selected hotbar slot is locked and holds an item. */
   public static boolean blocksDrop(PlayerEntity player) {
      if (!active() || !FeatureConfig.INSTANCE.slotLocks.blockDrop) {
         return false;
      }

      PlayerInventory inv = player.getInventory();
      return lockedWithItem(inv, inv.getSelectedSlot());
   }

   /** A click in any inventory screen. {@code slotId} is the screen handler's slot id, {@code button} as vanilla. */
   public static boolean blocksClick(PlayerEntity player, int slotId, int button, SlotActionType type) {
      if (!active()) {
         return false;
      }

      FeatureConfig.SlotLocks cfg = FeatureConfig.INSTANCE.slotLocks;
      PlayerInventory inv = player.getInventory();
      ScreenHandler handler = player.currentScreenHandler;
      boolean clicked = false;
      if (slotId >= 0 && slotId < handler.slots.size()) {
         Slot slot = handler.getSlot(slotId);
         clicked = slot.inventory == inv && lockedWithItem(inv, slot.getIndex());
      }

      boolean swapTarget = type == SlotActionType.SWAP && button >= 0 && button <= 8 && lockedWithItem(inv, button);
      return shouldBlock(type, clicked, swapTarget, cfg.blockMoves, cfg.blockDrop);
   }

   /**
    * The rule, kept free of game objects so it can be tested.
    *
    * @param clickedLocked    the clicked slot is locked and holds an item
    * @param swapTargetLocked for a number-key swap: the hotbar slot it would swap with is locked and holds an item
    */
   public static boolean shouldBlock(SlotActionType type, boolean clickedLocked, boolean swapTargetLocked, boolean blockMoves, boolean blockDrop) {
      return switch (type) {
         case THROW -> blockDrop && clickedLocked;
         case PICKUP, QUICK_MOVE -> blockMoves && clickedLocked;
         case SWAP -> blockMoves && (clickedLocked || swapTargetLocked);
         default -> false;
      };
   }

   /** Tells the player why nothing happened, without spamming while a key is held. */
   public static void notifyBlocked(PlayerEntity player) {
      long now = System.currentTimeMillis();
      if (now - lastNoticeMs >= NOTICE_GAP_MS) {
         lastNoticeMs = now;
         player.sendMessage(Text.literal("That slot is locked.").formatted(Formatting.RED), true);
      }
   }
}
