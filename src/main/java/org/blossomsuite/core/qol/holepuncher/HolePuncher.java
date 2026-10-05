package org.blossomsuite.core.qol.holepuncher;

import org.blossomsuite.core.config.QolConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.hud.ScreenNoticeOverlay;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
public final class HolePuncher {
   private static final int GUIDE_STEP = 5;
   private static final int INVENTORY_RECOVERY_TICKS = 2;
   private static BlockPos guideAnchor = null;
   private static Object guideWorldRef = null;
   private static BlockPos miningPos = null;
   private static boolean sentAttackForThisBlock = false;
   private static int inventoryRecoveryTicks = 0;
   private static boolean running = false;
   private static BlockPos firstPos = null;
   private static BlockPos secondPos = null;
   private static Direction hitFace = Direction.UP;
   private static int stage = 0;

   private HolePuncher() {
   }

   public static void onKeyPressed(Minecraft client) {
      if (client != null) {
         if (SuiteConfig.INSTANCE == null || SuiteConfig.INSTANCE.QolConfig == null || SuiteConfig.INSTANCE.QolConfig.holePuncherEnabled) {
            if (!running) {
               tryStartFromCrosshair(client);
            }
         }
      }
   }

   public static void tick(Minecraft client) {
      if (client.player != null && client.level != null && client.gameMode != null) {
         if (SuiteConfig.INSTANCE != null && SuiteConfig.INSTANCE.QolConfig != null && !SuiteConfig.INSTANCE.QolConfig.holePuncherEnabled) {
            if (running) {
               stop(client);
            }
         } else {
            if (guideWorldRef != client.level) {
               guideWorldRef = client.level;
               guideAnchor = null;
            }

            if (running) {
               tickSequence(client);
            }
         }
      }
   }

   private static void tryStartFromCrosshair(Minecraft client) {
      if (client != null && client.player != null && client.level != null) {
         HitResult hr = client.hitResult;
         if (hr != null && hr.getType() == Type.BLOCK) {
            BlockHitResult bhr = (BlockHitResult)hr;
            BlockPos target = bhr.getBlockPos();
            Direction face = bhr.getDirection();
            BlockPos behind = target.relative(face.getOpposite());
            if (!isValid(client, target, behind)) {
               reportFailure(client, HolePuncher.FailureReason.INVALID_BLOCKS);
            } else {
               boolean guided = SuiteConfig.INSTANCE != null && SuiteConfig.INSTANCE.QolConfig != null && SuiteConfig.INSTANCE.QolConfig.holePuncherGuided;
               if (guided) {
                  if (guideAnchor == null) {
                     guideAnchor = target;
                  } else if (!isOnGuideGrid(guideAnchor, target)) {
                     reportFailure(client, HolePuncher.FailureReason.NOT_ON_GUIDED_GRID);
                     return;
                  }
               }

               firstPos = target;
               secondPos = behind;
               hitFace = face;
               running = true;
               stage = 0;
               startOrContinueBreak(client, firstPos, hitFace);
            }
         } else {
            reportFailure(client, HolePuncher.FailureReason.NO_BLOCK_TARGETED);
         }
      }
   }

   private static boolean isAllowedBlock(BlockState s) {
      return s.is(Blocks.STONE) || s.is(Blocks.DEEPSLATE) || s.is(Blocks.NETHERRACK);
   }

   private static boolean isValid(Minecraft client, BlockPos a, BlockPos b) {
      if (a.equals(b)) {
         return false;
      }

      BlockState wa = client.level.getBlockState(a);
      BlockState wb = client.level.getBlockState(b);
      if (!wa.isAir() && !wb.isAir()) {
         if (wa.getDestroySpeed(client.level, a) < 0.0F) {
            return false;
         } else {
            return wb.getDestroySpeed(client.level, b) < 0.0F ? false : isAllowedBlock(wa) && isAllowedBlock(wb);
         }
      } else {
         return false;
      }
   }

   private static boolean isOnGuideGrid(BlockPos anchor, BlockPos target) {
      if (anchor != null && target != null) {
         int dx = target.getX() - anchor.getX();
         int dz = target.getZ() - anchor.getZ();
         return Math.floorMod(dx, 5) == 0 && Math.floorMod(dz, 5) == 0;
      } else {
         return false;
      }
   }

   public static BlockPos getGuideAnchor() {
      return guideAnchor;
   }

   public static void clearGuideAnchor() {
      guideAnchor = null;
   }

   public static boolean isRunning() {
      return running;
   }

   public static void onInventoryInterrupted(Minecraft client) {
      if (running) {
         sentAttackForThisBlock = false;
         inventoryRecoveryTicks = Math.max(inventoryRecoveryTicks, 2);
         if (client != null && client.gameMode != null) {
            client.gameMode.stopDestroyBlock();
         }
      }
   }

   public static void stopNow(Minecraft client) {
      if (client != null) {
         if (client.gameMode != null) {
            if (running) {
               stop(client);
            }

            guideAnchor = null;
         }
      }
   }

   private static String fmtPos(BlockPos p) {
      return p == null ? "(none)" : "(" + p.getX() + ", " + p.getY() + ", " + p.getZ() + ")";
   }

   private static void tickSequence(Minecraft client) {
      if (inventoryRecoveryTicks > 0) {
         inventoryRecoveryTicks--;
      } else {
         BlockPos current = stage == 0 ? firstPos : secondPos;
         if (client.level.isEmptyBlock(current)) {
            if (stage == 0) {
               stage = 1;
               startOrContinueBreak(client, secondPos, hitFace);
            } else {
               stop(client);
            }
         } else {
            startOrContinueBreak(client, current, hitFace);
         }
      }
   }

   private static void startOrContinueBreak(Minecraft client, BlockPos pos, Direction face) {
      if (client != null && client.player != null && client.gameMode != null) {
         if (ensureAutoSwapSlot(client)) {
            if (!hasPickaxeInMainHand(client)) {
               reportFailure(client, HolePuncher.FailureReason.NO_PICKAXE);
               stop(client);
            } else {
               if (miningPos == null || !miningPos.equals(pos)) {
                  miningPos = pos;
                  sentAttackForThisBlock = false;
                  client.gameMode.stopDestroyBlock();
               }

               if (!sentAttackForThisBlock) {
                  client.gameMode.startDestroyBlock(pos, face);
                  sentAttackForThisBlock = true;
               }

               client.gameMode.continueDestroyBlock(pos, face);
            }
         }
      }
   }

   private static boolean hasPickaxeInMainHand(Minecraft client) {
      if (client != null && client.player != null) {
         ItemStack stack = client.player.getMainHandItem();
         return stack != null && stack.is(ItemTags.PICKAXES);
      } else {
         return false;
      }
   }

   private static boolean ensureAutoSwapSlot(Minecraft client) {
      if (client == null || client.player == null) {
         return false;
      }

      if (SuiteConfig.INSTANCE != null && SuiteConfig.INSTANCE.QolConfig != null) {
         QolConfig q = SuiteConfig.INSTANCE.QolConfig;
         if (!q.autoSwapperEnabled) {
            return true;
         }

         int slot1to9 = q.holePuncherAutoSwapSlot;
         if (slot1to9 < 1) {
            slot1to9 = 1;
         }

         if (slot1to9 > 9) {
            slot1to9 = 9;
         }

         int desired = slot1to9 - 1;
         if (client.player.getInventory().getSelectedSlot() != desired) {
            client.player.getInventory().setSelectedSlot(desired);
            if (client.getConnection() != null) {
               client.getConnection().send(new ServerboundSetCarriedItemPacket(desired));
            }

            return false;
         } else {
            return client.player.getInventory().getSelectedSlot() == desired;
         }
      } else {
         return true;
      }
   }

   private static void reportFailure(Minecraft client, HolePuncher.FailureReason reason) {
      if (client != null && client.player != null && reason != null) {
         if (SuiteConfig.INSTANCE != null && SuiteConfig.INSTANCE.QolConfig != null) {
            QolConfig q = SuiteConfig.INSTANCE.QolConfig;
            Component msg = Component.literal("Hole Puncher: " + reason.message);
            if (q.holePuncherFailureScreenMessage) {
               client.player.sendOverlayMessage(msg);
            }

            if (q.holePuncherFailureChatMessage) {
               client.player.sendSystemMessage(msg);
            }

            if (q.holePuncherFailureNoticeMessage) {
               ScreenNoticeOverlay.show(msg.getString());
            }
         }
      }
   }

   private static void stop(Minecraft client) {
      running = false;
      stage = 0;
      firstPos = null;
      secondPos = null;
      miningPos = null;
      sentAttackForThisBlock = false;
      inventoryRecoveryTicks = 0;
      client.gameMode.stopDestroyBlock();
   }

   private enum FailureReason {
      NO_BLOCK_TARGETED("No block targeted"),
      INVALID_BLOCKS("Not valid blocks to break"),
      NOT_ON_GUIDED_GRID("Not on guided grid"),
      NO_PICKAXE("Don't have pickaxe equipped");

      private final String message;

      FailureReason(String message) {
         this.message = message;
      }
   }
}
