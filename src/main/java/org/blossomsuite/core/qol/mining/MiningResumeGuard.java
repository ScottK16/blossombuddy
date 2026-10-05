package org.blossomsuite.core.qol.mining;

import org.blossomsuite.core.config.SuiteConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

   public static void onAutoDropLikeInventoryAction(Minecraft client) {
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

   public static void onBlockBreakingInterrupted(Minecraft client) {
      onAutoDropLikeInventoryAction(client);
   }

   public static void tick(Minecraft client) {
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

   public static boolean shouldKeepBreaking(Minecraft client) {
      if (client != null && client.player != null && client.level != null) {
         SuiteConfig cfg = SuiteConfig.INSTANCE;
         if (cfg == null || cfg.QolConfig == null || !cfg.QolConfig.miningResumeAfterDrops) {
            return false;
         }

         if (client.screen != null) {
            return false;
         }

         if (client.hitResult != null && client.hitResult.getType() == Type.BLOCK) {
            boolean attackKeyPressed = false;

            try {
               attackKeyPressed = client.options != null && client.options.keyAttack != null && client.options.keyAttack.isDown();
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

   private static void rememberCurrentTarget(Minecraft client) {
      if (client != null) {
         if (client.hitResult instanceof BlockHitResult hit) {
            BlockPos var4 = hit.getBlockPos();
            Direction side = hit.getDirection();
            if (var4 != null && side != null) {
               lastTargetPos = var4.immutable();
               lastTargetSide = side;
            }
         }
      }
   }

   private static void cancelCurrentBreaking(Minecraft client) {
      if (client != null && client.gameMode != null) {
         try {
            client.gameMode.stopDestroyBlock();
         } catch (Throwable var2) {
         }
      }
   }

   private static void restartCurrentTargetBreaking(Minecraft client) {
      if (restartSent) {
         clearRecovery();
      } else if (client != null && client.gameMode != null) {
         if (!isAttackInputPressed(client)) {
            clearRecovery();
         } else {
            BlockPos pos = lastTargetPos;
            Direction side = lastTargetSide;
            if (pos != null) {
               if (side != null) {
                  try {
                     boolean accepted = client.gameMode.startDestroyBlock(pos, side);
                     if (!accepted) {
                        clearRecovery();
                        return;
                     }

                     client.gameMode.continueDestroyBlock(pos, side);
                     restartSent = true;
                  } catch (Throwable ignored) {
                     clearRecovery();
                  }
               }
            }
         }
      }
   }

   private static boolean isAttackInputPressed(Minecraft client) {
      if (client != null && client.options != null && client.options.keyAttack != null) {
         try {
            return client.options.keyAttack.isDown();
         } catch (Throwable ignored) {
            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean currentTargetMatchesRemembered(Minecraft client) {
      if (client == null) {
         return false;
      } else if (!(client.hitResult instanceof BlockHitResult hit)) {
         return false;
      } else {
         return lastTargetPos != null && lastTargetSide != null ? lastTargetPos.equals(hit.getBlockPos()) && lastTargetSide == hit.getDirection() : false;
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
