package org.blossomsuite.core.qol.toollock;

import org.blossomsuite.core.chat.ChatOutput;
import org.blossomsuite.core.config.QolConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.hud.ScreenNoticeOverlay;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
public final class ToolLock {
   private static final long REPORT_DEBOUNCE_MS = 650L;
   private static long lastReportAtMs = 0L;

   private ToolLock() {
   }

   public static boolean shouldBlockRightClick(Player player, InteractionHand hand, ItemStack stack, BlockHitResult hitResult) {
      if (player == null || hand == null) {
         return false;
      }

      if (stack == null || stack.isEmpty()) {
         return false;
      }

      if (!player.isShiftKeyDown()) {
         return false;
      }

      if (hand != InteractionHand.MAIN_HAND) {
         return false;
      }

      if (stack.getItem() instanceof BlockItem) {
         return false;
      }

      QolConfig q = qol();
      if (q != null && q.toolLockEnabled) {
         int selected = selectedHotbarSlot(player);
         if (!q.isToolLockSlotLocked(selected)) {
            return false;
         }

         if (hitResult != null) {
            boolean interactive = isProbablyInteractiveBlock(player, hitResult);
            if (interactive && !q.toolLockBlockOnInteractBlock) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   public static boolean shouldBlockLeftClick(Player player) {
      if (player == null) {
         return false;
      } else {
         QolConfig q = qol();
         if (q != null && q.toolLockEnabled) {
            int selected = selectedHotbarSlot(player);
            return q.isToolLockLeftClickSlotLocked(selected);
         } else {
            return false;
         }
      }
   }

   public static void reportBlocked() {
      QolConfig q = qol();
      if (q != null) {
         QolConfig.ToolLockReportMode mode = q.toolLockReportMode == null ? QolConfig.ToolLockReportMode.NOTICE : q.toolLockReportMode;
         if (mode != QolConfig.ToolLockReportMode.OFF) {
            long now = System.currentTimeMillis();
            if (now - lastReportAtMs >= 650L) {
               lastReportAtMs = now;
               String message = "Slot is locked";
               if (mode == QolConfig.ToolLockReportMode.CHAT) {
                  ChatOutput.info(message);
               } else {
                  ScreenNoticeOverlay.show(message, 16737894, 1200L);
               }
            }
         }
      }
   }

   private static QolConfig qol() {
      SuiteConfig cfg = SuiteConfig.INSTANCE;
      return cfg == null ? null : cfg.QolConfig;
   }

   private static int selectedHotbarSlot(Player player) {
      try {
         int selected = player.getInventory().getSelectedSlot();
         return selected >= 0 && selected <= 8 ? selected : -1;
      } catch (Throwable ignored) {
         return -1;
      }
   }

   private static boolean isProbablyInteractiveBlock(Player player, BlockHitResult hitResult) {
      if (player == null || hitResult == null) {
         return false;
      }

      if (player.level() == null) {
         return false;
      }

      BlockPos pos = hitResult.getBlockPos();
      if (pos == null) {
         return false;
      }

      try {
         if (player.level().getBlockEntity(pos) != null) {
            return true;
         }
      } catch (Throwable var5) {
      }

      try {
         return player.level().getBlockState(pos).hasBlockEntity();
      } catch (Throwable ignored) {
         return false;
      }
   }
}
