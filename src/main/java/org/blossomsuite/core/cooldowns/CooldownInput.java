package org.blossomsuite.core.cooldowns;

import org.blossomsuite.core.config.CooldownsConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.util.SuiteItemIdUtil;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

public final class CooldownInput {
   private CooldownInput() {
   }

   private static void processPendingTriggers() {
      if (!CooldownState.pending.isEmpty()) {
         for (CooldownState.PendingTrigger p : CooldownState.pending) {
            fireIfRuleExists(p.rule(), p.nowMs(), p.stack());
         }

         CooldownState.pending.clear();
      }
   }

   public static void tick(MinecraftClient client) {
      if (client.player != null) {
         long now = System.currentTimeMillis();
         if (client.currentScreen != null) {
            resetInputEdges();
            processPendingTriggers();
         } else {
            boolean sneakDown = client.player.isSneaking();
            boolean attackDown = client.options.attackKey.isPressed();
            boolean useDown = client.options.useKey.isPressed();
            boolean sneakPressed = sneakDown && !CooldownState.wasSneakDown;
            boolean attackPressed = attackDown && !CooldownState.wasAttackDown;
            if (useDown && !CooldownState.wasUseDown) {
               boolean usePressed = true;
            } else {
               boolean usePressed = false;
            }

            CooldownState.wasAttackDown = attackDown;
            CooldownState.wasUseDown = useDown;
            CooldownState.wasSneakDown = sneakDown;
            ItemStack held = client.player.getMainHandStack();
            if (!held.isEmpty()) {
               if (sneakPressed) {
                  String id = SuiteItemIdUtil.getBestId(held);
                  CooldownRules.CooldownRule rule = CooldownRules.resolveRule(id, CooldownRules.Trigger.SNEAK, held);
                  fireIfRuleExists(rule, now, held);
               }

               if (attackPressed) {
                  fireWithSneakFallback(CooldownRules.Trigger.LEFT_CLICK, CooldownRules.Trigger.SNEAK_LEFT_CLICK, sneakDown, now, held);
               }

               if (attackDown && now - CooldownState.lastAttackAttemptMs >= 150L) {
                  CooldownState.lastAttackAttemptMs = now;
                  fireWithSneakFallback(CooldownRules.Trigger.LEFT_CLICK, CooldownRules.Trigger.SNEAK_LEFT_CLICK, sneakDown, now, held);
               }
            }

            processPendingTriggers();
            float effectiveHp = client.player.getHealth() + client.player.getAbsorptionAmount();
            if (CooldownState.lastEffectiveHp < 0.0F) {
               CooldownState.lastEffectiveHp = effectiveHp;
            } else {
               if (effectiveHp < CooldownState.lastEffectiveHp - 0.001F && now - CooldownState.lastDamageTakenTriggerAtMs >= 100L) {
                  CooldownState.lastDamageTakenTriggerAtMs = now;
                  fireOnDamageTaken(client, now);
               }

               CooldownState.lastEffectiveHp = effectiveHp;
            }
         }
      }
   }

   public static void resetInputEdges() {
      CooldownState.wasAttackDown = false;
      CooldownState.wasUseDown = false;
      CooldownState.wasSneakDown = false;
      CooldownState.lastAttackAttemptMs = 0L;
      CooldownState.lastUseAttemptMs = 0L;
   }

   private static void fireOnDamageTaken(MinecraftClient client, long now) {
      if (client != null && client.player != null) {
         ItemStack held = client.player.getMainHandStack();
         if (held != null && !held.isEmpty()) {
            String id = SuiteItemIdUtil.getBestId(held);
            CooldownRules.CooldownRule rule = CooldownRules.resolveRule(id, CooldownRules.Trigger.ON_DAMAGE_TAKEN, held);
            fireIfRuleExists(rule, now, held);
         }

         fireSlotRules(CooldownRules.Trigger.ON_DAMAGE_TAKEN, client, now);
      }
   }

   private static void fireSlotRules(CooldownRules.Trigger trigger, MinecraftClient client, long now) {
      if (client != null && client.player != null) {
         CooldownsConfig cfg = SuiteConfig.INSTANCE.CooldownsConfig;
         int max = 8;
         int fired = 0;
         Set<CooldownRules.CooldownRule> seenRules = new HashSet<>();
         Set<String> handledItemKeys = new HashSet<>();
         List<CooldownRules.CooldownRule> rules = CooldownRules.getSlotRules(trigger);
         if (!rules.isEmpty()) {
            PlayerInventory inv = client.player.getInventory();

            for (CooldownRules.CooldownRule r : rules) {
               if (r != null) {
                  if (fired >= max) {
                     break;
                  }

                  if (seenRules.add(r)) {
                     switch (r.slotKind()) {
                        case INDEX: {
                           int idx = r.slotIndex();
                           if (idx < 0 || cfg != null && !cfg.trackInventory && idx >= 9) {
                              break;
                           }

                           int size;
                           try {
                              size = inv.size();
                           } catch (Throwable t) {
                              size = 0;
                           }

                           if (idx < size) {
                              ItemStack s = inv.getStack(idx);
                              fired += tryFireSlotRule(r, CooldownRules.SlotKind.INDEX, idx, CooldownRules.ArmorSlot.ANY, s, now, handledItemKeys) ? 1 : 0;
                           }
                           break;
                        }
                        case HOTBAR: {
                           for (int idx = 0; idx <= 8 && fired < max; idx++) {
                              ItemStack s = inv.getStack(idx);
                              if (tryFireSlotRule(r, CooldownRules.SlotKind.INDEX, idx, CooldownRules.ArmorSlot.ANY, s, now, handledItemKeys)) {
                                 fired++;
                              }
                           }
                           break;
                        }
                        case ARMOR: {
                           if (cfg != null && !cfg.trackArmor) {
                              break;
                           }

                           EquipmentSlot[] armorSlots = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
                           CooldownRules.ArmorSlot[] armorKinds = new CooldownRules.ArmorSlot[]{
                              CooldownRules.ArmorSlot.HEAD, CooldownRules.ArmorSlot.CHEST, CooldownRules.ArmorSlot.LEGS, CooldownRules.ArmorSlot.FEET
                           };
                           int i = 0;

                           for (; i < armorSlots.length && fired < max; i++) {
                              if (r.armorSlot() == null || r.armorSlot() == CooldownRules.ArmorSlot.ANY || r.armorSlot() == armorKinds[i]) {
                                 ItemStack s = client.player.getEquippedStack(armorSlots[i]);
                                 if (tryFireSlotRule(r, CooldownRules.SlotKind.ARMOR, -1, armorKinds[i], s, now, handledItemKeys)) {
                                    fired++;
                                 }
                              }
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

                              for (int idx = 0; idx < size && fired < max; idx++) {
                                 ItemStack s = inv.getStack(idx);
                                 if (tryFireSlotRule(r, CooldownRules.SlotKind.INDEX, idx, CooldownRules.ArmorSlot.ANY, s, now, handledItemKeys)) {
                                    fired++;
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
   }

   private static boolean tryFireSlotRule(
      CooldownRules.CooldownRule r,
      CooldownRules.SlotKind kind,
      int idx,
      CooldownRules.ArmorSlot armorSlot,
      ItemStack stack,
      long now,
      Set<String> handledItemKeys
   ) {
      if (r == null || stack == null || stack.isEmpty()) {
         return false;
      }

      if (!CooldownRules.slotMatches(r, kind, idx, armorSlot)) {
         return false;
      }

      if (!CooldownRules.matchesRule(r, stack)) {
         return false;
      }

      String itemKey = SuiteItemIdUtil.getBestId(stack);
      if (!handledItemKeys.add(itemKey)) {
         return false;
      }

      CooldownState.pending.add(new CooldownState.PendingTrigger(r, now, stack.copy()));
      return true;
   }

   private static void fireWithSneakFallback(CooldownRules.Trigger normal, CooldownRules.Trigger sneak, boolean isSneaking, long now, ItemStack stack) {
      String id = SuiteItemIdUtil.getBestId(stack);
      CooldownRules.CooldownRule sneakRule = CooldownRules.resolveRule(id, sneak, stack);
      CooldownRules.CooldownRule normalRule = CooldownRules.resolveRule(id, normal, stack);
      if (isSneaking) {
         if (sneakRule != null) {
            fireIfRuleExists(sneakRule, now, stack);
         } else {
            fireIfRuleExists(normalRule, now, stack);
         }
      } else {
         fireIfRuleExists(normalRule, now, stack);
      }
   }

   private static void fireIfRuleExists(CooldownRules.CooldownRule rule, long now, ItemStack stack) {
      if (rule != null) {
         long total = rule.ms();
         if (total > 0L) {
            long ends = CooldownState.endsAtByRuleKey.getOrDefault(rule, 0L);
            if (ends <= now) {
               CooldownState.endsAtByRuleKey.put(rule, now + total);
               CooldownState.totalMsByRuleKey.put(rule, total);
               if (stack != null && !stack.isEmpty()) {
                  CooldownState.lastStackByRuleKey.put(rule, stack.copy());
                  CooldownState.lastItemKeyByRuleKey.put(rule, SuiteItemIdUtil.getBestId(stack));
               }

               CooldownRuntime.publishCooldownSnapshot(CooldownRules.buildCooldownSnapshot());
            }
         }
      }
   }
}
