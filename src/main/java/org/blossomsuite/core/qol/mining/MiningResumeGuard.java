package org.blossomsuite.core.qol.mining;

import org.blossomsuite.core.config.SuiteConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class MiningResumeGuard {
   private static final int RECOVERY_TICKS = 10;
   private static final int RESTART_DELAY_TICKS = 6;
   private static boolean leftMouseDown = false;
   private static int recoveryTicks = 0;
   private static int restartDelayTicks = 0;
   private static boolean restartSent = false;
   private static BlockPos lastTargetPos = null;
   private static Direction lastTargetSide = null;

   private MiningResumeGuard() {
   }

   public static void onMouseButton(int button, int action) {
      if (button == 0) {
         if (action == 1) {
            leftMouseDown = true;
         } else if (action == 0) {
            leftMouseDown = false;
         }
      }
   }

   public static void onAutoDropLikeInventoryAction(MinecraftClient client) {
      if (shouldKeepBreaking(client)) {
         if (isAttackInputPressed(client)) {
            rememberCurrentTarget(client);
            cancelCurrentBreaking(client);
            recoveryTicks = Math.max(recoveryTicks, 10);
            restartDelayTicks = Math.max(restartDelayTicks, 6);
            restartSent = false;
         }
      }
   }

   public static void onBlockBreakingInterrupted(MinecraftClient client) {
      onAutoDropLikeInventoryAction(client);
   }

   public static void tick(MinecraftClient client) {
      if (!shouldKeepBreaking(client)) {
         clearRecovery();
      } else if (recoveryTicks > 0) {
         if (!currentTargetMatchesRemembered(client)) {
            clearRecovery();
         } else if (!isAttackInputPressed(client)) {
            clearRecovery();
         } else {
            recoveryTicks--;
            if (restartDelayTicks > 0) {
               restartDelayTicks--;
            } else {
               restartCurrentTargetBreaking(client);
            }
         }
      }
   }

   public static boolean shouldKeepBreaking(MinecraftClient client) {
      if (client != null && client.player != null && client.world != null) {
         SuiteConfig cfg = SuiteConfig.INSTANCE;
         if (cfg == null || cfg.QolConfig == null || !cfg.QolConfig.miningResumeAfterDrops) {
            return false;
         }

         if (client.currentScreen != null) {
            return false;
         }

         if (client.crosshairTarget != null && client.crosshairTarget.getType() == Type.BLOCK) {
            boolean attackKeyPressed = false;

            try {
               attackKeyPressed = client.options != null && client.options.attackKey != null && client.options.attackKey.isPressed();
            } catch (Throwable var4) {
            }

            return leftMouseDown || attackKeyPressed;
         } else {
            return false;
         }
      } else {
         leftMouseDown = false;
         clearRecovery();
         return false;
      }
   }

   private static void rememberCurrentTarget(MinecraftClient client) {
      if (client != null) {
         if (client.crosshairTarget instanceof BlockHitResult hit) {
            BlockPos var4 = hit.getBlockPos();
            Direction side = hit.getSide();
            if (var4 != null && side != null) {
               lastTargetPos = var4.toImmutable();
               lastTargetSide = side;
            }
         }
      }
   }

   private static void cancelCurrentBreaking(MinecraftClient client) {
      if (client != null && client.interactionManager != null) {
         try {
            client.interactionManager.cancelBlockBreaking();
         } catch (Throwable var2) {
         }
      }
   }

   private static void restartCurrentTargetBreaking(MinecraftClient client) {
      if (restartSent) {
         clearRecovery();
      } else if (client != null && client.interactionManager != null) {
         if (!isAttackInputPressed(client)) {
            clearRecovery();
         } else {
            BlockPos pos = lastTargetPos;
            Direction side = lastTargetSide;
            if (pos != null) {
               if (side != null) {
                  try {
                     boolean accepted = client.interactionManager.attackBlock(pos, side);
                     if (!accepted) {
                        clearRecovery();
                        return;
                     }

                     client.interactionManager.updateBlockBreakingProgress(pos, side);
                     restartSent = true;
                  } catch (Throwable ignored) {
                     clearRecovery();
                  }
               }
            }
         }
      }
   }

   private static boolean isAttackInputPressed(MinecraftClient client) {
      if (client != null && client.options != null && client.options.attackKey != null) {
         try {
            return client.options.attackKey.isPressed();
         } catch (Throwable ignored) {
            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean currentTargetMatchesRemembered(MinecraftClient client) {
      if (client == null) {
         return false;
      } else if (!(client.crosshairTarget instanceof BlockHitResult hit)) {
         return false;
      } else {
         return lastTargetPos != null && lastTargetSide != null ? lastTargetPos.equals(hit.getBlockPos()) && lastTargetSide == hit.getSide() : false;
      }
   }

   private static void clearRecovery() {
      recoveryTicks = 0;
      restartDelayTicks = 0;
      restartSent = false;
      lastTargetPos = null;
      lastTargetSide = null;
   }
}
