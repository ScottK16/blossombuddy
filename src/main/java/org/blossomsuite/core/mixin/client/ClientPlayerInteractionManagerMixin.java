package org.blossomsuite.core.mixin.client;

import org.blossomsuite.core.config.CooldownsConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.cooldowns.CooldownInput;
import org.blossomsuite.core.cooldowns.CooldownRules;
import org.blossomsuite.core.cooldowns.CooldownState;
import org.blossomsuite.core.qol.autodrop.AutoDropper;
import org.blossomsuite.core.qol.holepuncher.HolePuncher;
import org.blossomsuite.core.qol.mining.MiningResumeGuard;
import org.blossomsuite.core.qol.toollock.ToolLock;
import org.blossomsuite.core.util.SuiteItemIdUtil;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
   @Unique
   private ItemStack suitecore$placeStack = ItemStack.EMPTY;
   @Unique
   private int suitecore$placeCount = 0;
   @Unique
   private boolean suitecore$placeIsBlockItem = false;
   @Unique
   private Hand suitecore$placeHand = null;
   @Unique
   private ItemStack suitecore$rightClickStack = ItemStack.EMPTY;
   @Unique
   private Hand suitecore$rightClickHand = null;
   @Unique
   private boolean suitecore$rightClickSneaking = false;

   @Unique
   private static void suitecore$maybeTriggerRightClick(PlayerEntity player, Hand hand, ItemStack stackSnapshot, boolean sneak) {
      if (player != null && hand != null) {
         if (stackSnapshot != null && !stackSnapshot.isEmpty()) {
            CooldownsConfig cfg = SuiteConfig.INSTANCE.CooldownsConfig;
            if (cfg == null || cfg.trackOffhand || hand != Hand.OFF_HAND) {
               long now = System.currentTimeMillis();
               String id = SuiteItemIdUtil.getBestId(stackSnapshot);
               CooldownRules.CooldownRule sneakRule = CooldownRules.resolveRule(id, CooldownRules.Trigger.SNEAK_RIGHT_CLICK, stackSnapshot);
               CooldownRules.CooldownRule normalRule = CooldownRules.resolveRule(id, CooldownRules.Trigger.RIGHT_CLICK, stackSnapshot);
               CooldownRules.CooldownRule chosen;
               if (sneak) {
                  chosen = sneakRule != null ? sneakRule : normalRule;
               } else {
                  chosen = normalRule;
               }

               if (chosen != null) {
                  int selected = -1;

                  try {
                     selected = player.getInventory().getSelectedSlot();
                  } catch (Throwable var13) {
                  }

                  CooldownRules.SlotKind kind = hand == Hand.OFF_HAND ? CooldownRules.SlotKind.OFFHAND : CooldownRules.SlotKind.MAINHAND;
                  if (CooldownRules.slotMatches(chosen, kind, selected, CooldownRules.ArmorSlot.ANY)) {
                     CooldownState.pending.add(new CooldownState.PendingTrigger(chosen, now, stackSnapshot));
                  }
               }
            }
         }
      }
   }

   @Unique
   private static void suitecore$maybeTriggerSlotRightClick(PlayerEntity player, boolean sneak) {
      if (player != null) {
         long now = System.currentTimeMillis();
         if (now - CooldownState.lastInventoryUseTriggerAtMs >= 25L) {
            CooldownState.lastInventoryUseTriggerAtMs = now;
            Set<CooldownRules.CooldownRule> fired = new HashSet<>();
            Set<String> handledItems = new HashSet<>();
            int max = 8;
            if (sneak) {
               suitecore$fireSlotRules(player, CooldownRules.Trigger.SNEAK_RIGHT_CLICK, now, fired, handledItems, 8);
               suitecore$fireSlotRules(player, CooldownRules.Trigger.RIGHT_CLICK, now, fired, handledItems, 8);
            } else {
               suitecore$fireSlotRules(player, CooldownRules.Trigger.RIGHT_CLICK, now, fired, handledItems, 8);
            }
         }
      }
   }

   @Unique
   private static void suitecore$fireSlotRules(
      PlayerEntity player, CooldownRules.Trigger trigger, long now, Set<CooldownRules.CooldownRule> fired, Set<String> handledItems, int max
   ) {
      if (player != null) {
         if (fired.size() < max) {
            List<CooldownRules.CooldownRule> rules = CooldownRules.getSlotRules(trigger);
            if (!rules.isEmpty()) {
               CooldownsConfig cfg = SuiteConfig.INSTANCE.CooldownsConfig;
               PlayerInventory inv = player.getInventory();

               for (CooldownRules.CooldownRule r : rules) {
                  if (fired.size() >= max) {
                     return;
                  }

                  if (r != null) {
                     switch (r.slotKind()) {
                        case INDEX: {
                           int idx = r.slotIndex();
                           if (cfg != null && !cfg.trackInventory && idx >= 9) {
                              break;
                           }

                           int size;
                           try {
                              size = inv.size();
                           } catch (Throwable t) {
                              size = 0;
                           }

                           if (idx >= 0 && idx < size) {
                              ItemStack s = inv.getStack(idx);
                              suitecore$tryFireSlotRule(player, r, CooldownRules.SlotKind.INDEX, idx, CooldownRules.ArmorSlot.ANY, s, now, fired, handledItems);
                           }
                           break;
                        }
                        case HOTBAR: {
                           for (int idx = 0; idx <= 8 && fired.size() < max; idx++) {
                              ItemStack s = inv.getStack(idx);
                              suitecore$tryFireSlotRule(player, r, CooldownRules.SlotKind.INDEX, idx, CooldownRules.ArmorSlot.ANY, s, now, fired, handledItems);
                           }
                           break;
                        }
                        case MAINHAND: {
                           ItemStack s = player.getMainHandStack();
                           int selected = -1;

                           try {
                              selected = inv.getSelectedSlot();
                           } catch (Throwable var16) {
                           }

                           suitecore$tryFireSlotRule(
                              player, r, CooldownRules.SlotKind.MAINHAND, selected, CooldownRules.ArmorSlot.ANY, s, now, fired, handledItems
                           );
                           break;
                        }
                        case OFFHAND: {
                           if (cfg == null || cfg.trackOffhand) {
                              ItemStack s = player.getOffHandStack();
                              suitecore$tryFireSlotRule(player, r, CooldownRules.SlotKind.OFFHAND, -1, CooldownRules.ArmorSlot.ANY, s, now, fired, handledItems);
                           }
                           break;
                        }
                        case ARMOR: {
                           if (cfg == null || cfg.trackArmor) {
                              suitecore$fireArmor(player, r, now, fired, handledItems, max);
                           }
                           break;
                        }
                        case ANY: {
                           if (cfg == null || cfg.trackInventory) {
                              int size;
                              try {
                                 size = inv.size();
                              } catch (Throwable t) {
                                 size = 0;
                              }

                              for (int idx = 0; idx < size && fired.size() < max; idx++) {
                                 ItemStack s = inv.getStack(idx);
                                 suitecore$tryFireSlotRule(
                                    player, r, CooldownRules.SlotKind.INDEX, idx, CooldownRules.ArmorSlot.ANY, s, now, fired, handledItems
                                 );
                              }

                              if (cfg == null || cfg.trackOffhand) {
                                 ItemStack off = player.getOffHandStack();
                                 suitecore$tryFireSlotRule(
                                    player, r, CooldownRules.SlotKind.OFFHAND, -1, CooldownRules.ArmorSlot.ANY, off, now, fired, handledItems
                                 );
                              }

                              if (cfg == null || cfg.trackArmor) {
                                 suitecore$fireArmor(player, r, now, fired, handledItems, max);
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @Unique
   private static void suitecore$fireArmor(
      PlayerEntity player, CooldownRules.CooldownRule r, long now, Set<CooldownRules.CooldownRule> fired, Set<String> handledItems, int max
   ) {
      if (player != null) {
         if (fired.size() < max) {
            EquipmentSlot[] armorSlots = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            CooldownRules.ArmorSlot[] armorKinds = new CooldownRules.ArmorSlot[]{
               CooldownRules.ArmorSlot.HEAD, CooldownRules.ArmorSlot.CHEST, CooldownRules.ArmorSlot.LEGS, CooldownRules.ArmorSlot.FEET
            };

            for (int i = 0; i < armorSlots.length && fired.size() < max; i++) {
               if (r.armorSlot() == null || r.armorSlot() == CooldownRules.ArmorSlot.ANY || r.armorSlot() == armorKinds[i]) {
                  ItemStack s = player.getEquippedStack(armorSlots[i]);
                  suitecore$tryFireSlotRule(player, r, CooldownRules.SlotKind.ARMOR, -1, armorKinds[i], s, now, fired, handledItems);
               }
            }
         }
      }
   }

   @Unique
   private static void suitecore$tryFireSlotRule(
      PlayerEntity player,
      CooldownRules.CooldownRule r,
      CooldownRules.SlotKind kind,
      int index,
      CooldownRules.ArmorSlot armorSlot,
      ItemStack stack,
      long now,
      Set<CooldownRules.CooldownRule> fired,
      Set<String> handledItems
   ) {
      if (player != null && r != null) {
         if (stack != null && !stack.isEmpty()) {
            if (CooldownRules.slotMatches(r, kind, index, armorSlot)) {
               if (CooldownRules.matchesRule(r, stack)) {
                  String itemKey = SuiteItemIdUtil.getBestId(stack);
                  if (!handledItems.contains(itemKey)) {
                     if (fired.add(r)) {
                        handledItems.add(itemKey);
                        CooldownState.pending.add(new CooldownState.PendingTrigger(r, now, stack.copy()));
                     }
                  }
               }
            }
         }
      }
   }

   @Unique
   private static boolean suitecore$shouldBlockToolLock(PlayerEntity player, Hand hand, ItemStack stack, BlockHitResult hitResult) {
      return ToolLock.shouldBlockRightClick(player, hand, stack, hitResult);
   }

   @Inject(method = "clickSlot", at = @At("HEAD"))
   private void suitecore$noticeAutoDropLikeInventoryAction(int syncId, int slotId, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
      if (SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
         CooldownInput.resetInputEdges();
         if (!AutoDropper.isRunningAutoDropBurst()) {
            if (actionType == SlotActionType.THROW || actionType == SlotActionType.PICKUP) {
               MinecraftClient client = MinecraftClient.getInstance();
               if (HolePuncher.isRunning()) {
                  HolePuncher.onInventoryInterrupted(client);
               } else {
                  MiningResumeGuard.onAutoDropLikeInventoryAction(client);
               }
            }
         }
      }
   }

   @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
   private void suitecore$cdPlaceHead(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
      if (SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
         if (player != null && hand != null) {
            ItemStack stack = player.getStackInHand(hand);
            if (stack != null && !stack.isEmpty() && suitecore$shouldBlockToolLock(player, hand, stack, hitResult)) {
               ToolLock.reportBlocked();
               cir.setReturnValue(ActionResult.PASS);
               cir.cancel();
            } else {
               this.suitecore$placeHand = hand;
               this.suitecore$placeStack = stack == null ? ItemStack.EMPTY : stack.copy();
               this.suitecore$placeCount = stack == null ? 0 : stack.getCount();
               this.suitecore$placeIsBlockItem = stack != null && !stack.isEmpty() && stack.getItem() instanceof BlockItem;
               this.suitecore$rightClickStack = stack == null ? ItemStack.EMPTY : stack.copy();
               this.suitecore$rightClickHand = hand;
               this.suitecore$rightClickSneaking = player.isSneaking();
            }
         } else {
            this.suitecore$placeStack = ItemStack.EMPTY;
            this.suitecore$placeCount = 0;
            this.suitecore$placeIsBlockItem = false;
            this.suitecore$placeHand = null;
            this.suitecore$rightClickStack = ItemStack.EMPTY;
            this.suitecore$rightClickHand = null;
            this.suitecore$rightClickSneaking = false;
         }
      }
   }

   @Inject(method = "interactBlock", at = @At("RETURN"))
   private void suitecore$cdPlaceReturn(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
      if (SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
         if (player != null) {
            ActionResult result = (ActionResult)cir.getReturnValue();
            if (result != null && result != ActionResult.FAIL) {
               if (!this.suitecore$placeIsBlockItem && result.isAccepted()) {
                  this.suitecore$fireAcceptedRightClick(player, hand);
               } else if (this.suitecore$placeIsBlockItem) {
                  if (this.suitecore$placeStack != null && !this.suitecore$placeStack.isEmpty()) {
                     if (this.suitecore$placeHand == hand) {
                        ItemStack after = player.getStackInHand(hand);
                        int afterCount = after == null ? 0 : after.getCount();
                        if (afterCount < this.suitecore$placeCount) {
                           long now = System.currentTimeMillis();
                           String id = SuiteItemIdUtil.getBestId(this.suitecore$placeStack);
                           CooldownRules.CooldownRule rule = CooldownRules.resolveRule(id, CooldownRules.Trigger.PLACE, this.suitecore$placeStack);
                           CooldownState.pending.add(new CooldownState.PendingTrigger(rule, now, this.suitecore$placeStack));
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @Inject(method = "interactItem", at = @At("HEAD"), cancellable = true)
   private void suitecore$cdRightClickItemHead(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
      if (SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
         if (player != null && hand != null) {
            ItemStack stack = player.getStackInHand(hand);
            if (stack != null && !stack.isEmpty()) {
               if (suitecore$shouldBlockToolLock(player, hand, stack, null)) {
                  ToolLock.reportBlocked();
                  cir.setReturnValue(ActionResult.PASS);
                  cir.cancel();
               } else {
                  this.suitecore$rightClickStack = stack.copy();
                  this.suitecore$rightClickHand = hand;
                  this.suitecore$rightClickSneaking = player.isSneaking();
               }
            }
         }
      }
   }

   @Inject(method = "interactItem", at = @At("RETURN"))
   private void suitecore$cdRightClickItemReturn(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
      if (SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
         if (player != null && hand != null) {
            ActionResult result = (ActionResult)cir.getReturnValue();
            if (result != null && result != ActionResult.FAIL) {
               this.suitecore$fireAcceptedRightClick(player, hand);
            }
         }
      }
   }

   @Unique
   private void suitecore$fireAcceptedRightClick(PlayerEntity player, Hand hand) {
      if (player != null && hand != null) {
         if (this.suitecore$rightClickHand == hand) {
            if (this.suitecore$rightClickStack != null && !this.suitecore$rightClickStack.isEmpty()) {
               ItemStack snapshot = this.suitecore$rightClickStack.copy();
               boolean wasSneaking = this.suitecore$rightClickSneaking;
               this.suitecore$rightClickStack = ItemStack.EMPTY;
               this.suitecore$rightClickHand = null;
               this.suitecore$rightClickSneaking = false;
               suitecore$maybeTriggerRightClick(player, hand, snapshot, wasSneaking);
               suitecore$maybeTriggerSlotRightClick(player, wasSneaking);
            }
         }
      }
   }

   @Unique
   private static void suitecore$maybeTriggerOnAttack(PlayerEntity player) {
      if (player != null) {
         ItemStack held = player.getMainHandStack();
         if (held != null && !held.isEmpty()) {
            String id = SuiteItemIdUtil.getBestId(held);
            CooldownRules.CooldownRule rule = CooldownRules.resolveRule(id, CooldownRules.Trigger.ON_ATTACK, held);
            if (rule != null) {
               long now = System.currentTimeMillis();
               CooldownState.pending.add(new CooldownState.PendingTrigger(rule, now, held.copy()));
            }
         }
      }
   }

   @Unique
   private static void suitecore$maybeTriggerSlotEvent(PlayerEntity player, CooldownRules.Trigger trigger) {
      if (player != null && trigger != null) {
         long now = System.currentTimeMillis();
         Set<CooldownRules.CooldownRule> fired = new HashSet<>();
         Set<String> handledItems = new HashSet<>();
         int max = 8;
         suitecore$fireSlotRules(player, trigger, now, fired, handledItems, max);
      }
   }

   @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
   private void suitecore$toolLockAttackBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
      if (SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client != null && client.player != null) {
            if (ToolLock.shouldBlockLeftClick(client.player)) {
               ((ClientPlayerInteractionManager)(Object)this).cancelBlockBreaking();
               ToolLock.reportBlocked();
               cir.setReturnValue(false);
               cir.cancel();
            }
         }
      }
   }

   @Inject(method = "updateBlockBreakingProgress", at = @At("HEAD"), cancellable = true)
   private void suitecore$toolLockUpdateBlockBreakingProgress(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
      if (SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client != null && client.player != null) {
            if (ToolLock.shouldBlockLeftClick(client.player)) {
               ((ClientPlayerInteractionManager)(Object)this).cancelBlockBreaking();
               ToolLock.reportBlocked();
               cir.setReturnValue(false);
               cir.cancel();
            }
         }
      }
   }

   @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
   private void suitecore$cdOnAttackEntityPlayer(PlayerEntity player, Entity target, CallbackInfo ci) {
      if (SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
         if (ToolLock.shouldBlockLeftClick(player)) {
            ToolLock.reportBlocked();
            ci.cancel();
         } else {
            AutoDropper.noteAttackTarget(target);
            suitecore$maybeTriggerOnAttack(player);
            suitecore$maybeTriggerSlotEvent(player, CooldownRules.Trigger.ON_ATTACK);
         }
      }
   }
}
