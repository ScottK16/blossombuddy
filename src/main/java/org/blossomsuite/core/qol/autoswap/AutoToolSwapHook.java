package org.blossomsuite.core.qol.autoswap;

import org.blossomsuite.core.config.SuiteConfig;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Util;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

public final class AutoToolSwapHook {
   private static BlockPos lastSwapPos = null;
   private static long lastSwapMs = 0L;
   private static BooleanSupplier externalBlocker = () -> false;

   public static void setExternalBlocker(BooleanSupplier blocker) {
      externalBlocker = blocker != null ? blocker : () -> false;
   }

   public static void init() {
      AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
         if (!world.isClient()) {
            return ActionResult.PASS;
         }

         if (player.isSneaking()) {
            return ActionResult.PASS;
         }

         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc == null) {
            return ActionResult.PASS;
         }

         if (!SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
            return ActionResult.PASS;
         }

         if (externalBlocker.getAsBoolean()) {
            return ActionResult.PASS;
         }

         if (mc.crosshairTarget instanceof BlockHitResult bhr) {
            if (!bhr.getBlockPos().equals(pos)) {
               return ActionResult.PASS;
            }

            BlockState state = world.getBlockState(pos);
            if (state != null && !state.isAir()) {
               int desiredSlot = AutoToolSwapper.pickSlotFor(state);
               if (desiredSlot == -1) {
                  return ActionResult.PASS;
               }

               int current = player.getInventory().getSelectedSlot();
               if (desiredSlot == current) {
                  return ActionResult.PASS;
               }

               long now = Util.getMeasuringTimeMs();
               long delta = now - lastSwapMs;
               long debounceMs = SuiteConfig.INSTANCE.QolConfig.autoSwapperDebounceMs;
               if (lastSwapPos != null && lastSwapPos.equals(pos) && delta < debounceMs) {
                  return ActionResult.FAIL;
               }

               lastSwapPos = pos.toImmutable();
               lastSwapMs = now;
               player.getInventory().setSelectedSlot(desiredSlot);
               return ActionResult.FAIL;
            } else {
               return ActionResult.PASS;
            }
         } else {
            return ActionResult.PASS;
         }
      });
   }
}
