package org.blossomsuite.core.qol.autoswap;

import org.blossomsuite.core.config.SuiteConfig;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.util.Util;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
public final class AutoToolSwapHook {
   private static BlockPos lastSwapPos = null;
   private static long lastSwapMs = 0L;
   private static BooleanSupplier externalBlocker = () -> false;

   public static void setExternalBlocker(BooleanSupplier blocker) {
      externalBlocker = blocker != null ? blocker : () -> false;
   }

   public static void init() {
      AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
         if (!world.isClientSide()) {
            return InteractionResult.PASS;
         }

         if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
         }

         Minecraft mc = Minecraft.getInstance();
         if (mc == null) {
            return InteractionResult.PASS;
         }

         if (!SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
            return InteractionResult.PASS;
         }

         if (externalBlocker.getAsBoolean()) {
            return InteractionResult.PASS;
         }

         if (mc.hitResult instanceof BlockHitResult bhr) {
            if (!bhr.getBlockPos().equals(pos)) {
               return InteractionResult.PASS;
            }

            BlockState state = world.getBlockState(pos);
            if (state != null && !state.isAir()) {
               int desiredSlot = AutoToolSwapper.pickSlotFor(state);
               if (desiredSlot == -1) {
                  return InteractionResult.PASS;
               }

               int current = player.getInventory().getSelectedSlot();
               if (desiredSlot == current) {
                  return InteractionResult.PASS;
               }

               long now = Util.getMillis();
               long delta = now - lastSwapMs;
               long debounceMs = SuiteConfig.INSTANCE.QolConfig.autoSwapperDebounceMs;
               if (lastSwapPos != null && lastSwapPos.equals(pos) && delta < debounceMs) {
                  return InteractionResult.FAIL;
               }

               lastSwapPos = pos.immutable();
               lastSwapMs = now;
               player.getInventory().setSelectedSlot(desiredSlot);
               return InteractionResult.FAIL;
            } else {
               return InteractionResult.PASS;
            }
         } else {
            return InteractionResult.PASS;
         }
      });
   }
}
