package org.blossomsuite.core.cooldowns;

import org.blossomsuite.core.config.CooldownsConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.util.SuiteItemIdUtil;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

public final class CooldownModule {
   public static void init() {
      ClientPlayerBlockBreakEvents.AFTER.register((world, player, pos, state) -> {
         if (SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
            if (player != null) {
               ItemStack held = player.getMainHandStack();
               CooldownRules.CooldownRule rule = null;
               String id = SuiteItemIdUtil.getBestId(held);
               if (!held.isEmpty() && player.isSneaking()) {
                  rule = CooldownRules.resolveRule(id, CooldownRules.Trigger.SNEAK_BREAK, held);
                  if (!CooldownRules.matchesBreakBlock(rule, state)) {
                     rule = null;
                  }
               }

               if (!held.isEmpty() && rule == null) {
                  rule = CooldownRules.resolveRule(id, CooldownRules.Trigger.BREAK, held);
                  if (!CooldownRules.matchesBreakBlock(rule, state)) {
                     rule = null;
                  }
               }

               long now = System.currentTimeMillis();
               if (rule != null) {
                  CooldownState.pending.add(new CooldownState.PendingTrigger(rule, now, held.copy()));
               }

               Set<CooldownRules.CooldownRule> fired = new HashSet<>();
               Set<String> handledItems = new HashSet<>();
               int max = 8;
               if (player.isSneaking()) {
                  fireSlotRules(player, CooldownRules.Trigger.SNEAK_BREAK, state, now, fired, handledItems, max);
                  fireSlotRules(player, CooldownRules.Trigger.BREAK, state, now, fired, handledItems, max);
               } else {
                  fireSlotRules(player, CooldownRules.Trigger.BREAK, state, now, fired, handledItems, max);
               }
            }
         }
      });
   }

   private static void fireSlotRules(
      PlayerEntity player,
      CooldownRules.Trigger trigger,
      BlockState brokenState,
      long now,
      Set<CooldownRules.CooldownRule> fired,
      Set<String> handledItems,
      int max
   ) {
      if (player != null && trigger != null && fired.size() < max) {
         List<CooldownRules.CooldownRule> rules = CooldownRules.getSlotRules(trigger);
         if (!rules.isEmpty()) {
            CooldownsConfig cfg = SuiteConfig.INSTANCE.CooldownsConfig;
            PlayerInventory inv = player.getInventory();

            for (CooldownRules.CooldownRule r : rules) {
               if (fired.size() >= max) {
                  return;
               }

               if (r != null && CooldownRules.matchesBreakBlock(r, brokenState)) {
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
                           tryFireSlotRule(r, CooldownRules.SlotKind.INDEX, idx, CooldownRules.ArmorSlot.ANY, s, now, fired, handledItems);
                        }
                        break;
                     }
                     case HOTBAR: {
                        for (int idx = 0; idx <= 8 && fired.size() < max; idx++) {
                           ItemStack s = inv.getStack(idx);
                           tryFireSlotRule(r, CooldownRules.SlotKind.INDEX, idx, CooldownRules.ArmorSlot.ANY, s, now, fired, handledItems);
                        }
                        break;
                     }
                     case MAINHAND: {
                        ItemStack s = player.getMainHandStack();
                        int selected = -1;

                        try {
                           selected = inv.getSelectedSlot();
                        } catch (Throwable var17) {
                        }

                        tryFireSlotRule(r, CooldownRules.SlotKind.MAINHAND, selected, CooldownRules.ArmorSlot.ANY, s, now, fired, handledItems);
                        break;
                     }
                     case OFFHAND: {
                        if (cfg == null || cfg.trackOffhand) {
                           ItemStack s = player.getOffHandStack();
                           tryFireSlotRule(r, CooldownRules.SlotKind.OFFHAND, -1, CooldownRules.ArmorSlot.ANY, s, now, fired, handledItems);
                        }
                        break;
                     }
                     case ARMOR: {
                        if (cfg == null || cfg.trackArmor) {
                           fireArmor(player, r, now, fired, handledItems, max);
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
                              tryFireSlotRule(r, CooldownRules.SlotKind.INDEX, idx, CooldownRules.ArmorSlot.ANY, s, now, fired, handledItems);
                           }

                           if (cfg == null || cfg.trackOffhand) {
                              ItemStack off = player.getOffHandStack();
                              tryFireSlotRule(r, CooldownRules.SlotKind.OFFHAND, -1, CooldownRules.ArmorSlot.ANY, off, now, fired, handledItems);
                           }

                           if (cfg == null || cfg.trackArmor) {
                              fireArmor(player, r, now, fired, handledItems, max);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void fireArmor(
      PlayerEntity player, CooldownRules.CooldownRule r, long now, Set<CooldownRules.CooldownRule> fired, Set<String> handledItems, int max
   ) {
      if (player != null && fired.size() < max) {
         EquipmentSlot[] armorSlots = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
         CooldownRules.ArmorSlot[] armorKinds = new CooldownRules.ArmorSlot[]{
            CooldownRules.ArmorSlot.HEAD, CooldownRules.ArmorSlot.CHEST, CooldownRules.ArmorSlot.LEGS, CooldownRules.ArmorSlot.FEET
         };

         for (int i = 0; i < armorSlots.length && fired.size() < max; i++) {
            if (r.armorSlot() == null || r.armorSlot() == CooldownRules.ArmorSlot.ANY || r.armorSlot() == armorKinds[i]) {
               ItemStack s = player.getEquippedStack(armorSlots[i]);
               tryFireSlotRule(r, CooldownRules.SlotKind.ARMOR, -1, armorKinds[i], s, now, fired, handledItems);
            }
         }
      }
   }

   private static void tryFireSlotRule(
      CooldownRules.CooldownRule r,
      CooldownRules.SlotKind kind,
      int index,
      CooldownRules.ArmorSlot armorSlot,
      ItemStack stack,
      long now,
      Set<CooldownRules.CooldownRule> fired,
      Set<String> handledItems
   ) {
      if (r != null && stack != null && !stack.isEmpty()) {
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
