package org.blossomsuite.core.qol.autodrop;

import org.blossomsuite.core.config.QolConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.qol.mining.MiningResumeGuard;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult.Type;
public final class AutoDropper {
   private static long lastInventoryHash = 0L;
   private static boolean hadInventoryHash = false;
   private static boolean wasSneaking = false;
   private static long queuedAtMs = 0L;
   private static long lastDropAtMs = 0L;
   private static long lastHeldMiningDropAtMs = 0L;
   private static long lastPlayerAttackAtMs = 0L;
   private static long lastAttackingPlayerAtMs = 0L;
   private static boolean runningAutoDropBurst = false;
   private static final long HELD_MINING_BURST_COOLDOWN_MS = 300L;
   private static final long PLAYER_COMBAT_PAUSE_MS = 3000L;
   private static Supplier<Boolean> externalMiningRunner = () -> false;
   private static Consumer<Minecraft> externalMiningInterruptHandler = client -> {};
   private static Consumer<String> chatReporter = message -> {};

   private AutoDropper() {
   }

   public static void setExternalMiningHooks(Supplier<Boolean> runningSupplier, Consumer<Minecraft> interruptHandler) {
      externalMiningRunner = runningSupplier != null ? runningSupplier : () -> false;
      externalMiningInterruptHandler = interruptHandler != null ? interruptHandler : client -> {};
   }

   public static void setChatReporter(Consumer<String> reporter) {
      chatReporter = reporter != null ? reporter : message -> {};
   }

   public static boolean isRunningAutoDropBurst() {
      return runningAutoDropBurst;
   }

   public static void noteAttackTarget(Entity target) {
      QolConfig cfg = SuiteConfig.INSTANCE.QolConfig;
      if (cfg != null && cfg.autoDropperPauseOnAttackingPlayer && target instanceof Player) {
         lastAttackingPlayerAtMs = System.currentTimeMillis();
      }
   }

   public static void notePlayerAttack() {
      lastPlayerAttackAtMs = System.currentTimeMillis();
   }

   public static void tick(Minecraft client, long now) {
      QolConfig cfg = SuiteConfig.INSTANCE.QolConfig;
      if (client != null && client.player != null && cfg != null) {
         pollInboundPlayerCombat(client);
         if (!cfg.autoDropperEnabled) {
            resetTransient();
         } else if (!cfg.autoDropperPauseWhileScreenOpen || client.screen == null) {
            if (!cfg.autoDropperPauseWhileSneaking || !client.player.isShiftKeyDown()) {
               if (shouldPauseForPlayerCombat(client, now)) {
                  queuedAtMs = 0L;
               } else {
                  AutoDropper.InventoryScan scan = scanInventory(client, cfg);
                  boolean inventoryFull = scan.droppableInventoryFull();
                  long hash = scan.hash();
                  if (!hadInventoryHash) {
                     lastInventoryHash = hash;
                     hadInventoryHash = true;
                  } else if (hash != lastInventoryHash) {
                     lastInventoryHash = hash;
                     if (cfg.autoDropperOnPickup) {
                        queue(now);
                     }
                  }

                  if (cfg.autoDropperOnPickup && inventoryFull) {
                     queue(now);
                  }

                  boolean sneaking = client.player.isShiftKeyDown();
                  if (cfg.autoDropperOnSneak && sneaking && !wasSneaking) {
                     queue(now);
                  }

                  wasSneaking = sneaking;
                  if (queuedAtMs > 0L && (inventoryFull || now - queuedAtMs >= Math.max(0, cfg.autoDropperDelayMs))) {
                     if (shouldDeferForHeldMining(client, now)) {
                        return;
                     }

                     runDrop(client, now, false);
                  }
               }
            }
         }
      }
   }

   public static void triggerHotkey(Minecraft client) {
      if (client != null && client.player != null) {
         QolConfig cfg = SuiteConfig.INSTANCE.QolConfig;
         if (cfg == null || !cfg.autoDropperPauseWhileSneaking || !client.player.isShiftKeyDown()) {
            if (shouldPauseForPlayerCombat(client, System.currentTimeMillis())) {
               chatReporter.accept("AutoDropper paused during player combat.");
            } else {
               long now = System.currentTimeMillis();
               runDrop(client, now, true);
            }
         }
      }
   }

   public static void queue(long now) {
      if (queuedAtMs <= 0L) {
         queuedAtMs = now;
      }
   }

   private static void runDrop(Minecraft client, long now, boolean manual) {
      QolConfig cfg = SuiteConfig.INSTANCE.QolConfig;
      if (cfg != null && cfg.autoDropperEnabled) {
         if (client.gameMode != null && client.player != null) {
            if (!cfg.autoDropperPauseWhileSneaking || !client.player.isShiftKeyDown()) {
               if (!shouldPauseForPlayerCombat(client, now)) {
                  int dropped = 0;
                  int max = Math.max(1, Math.min(64, cfg.autoDropperMaxStacksPerTick));
                  AutoDropper.DropPlanIndex plans = compilePlans(cfg);
                  if (plans.isEmpty()) {
                     queuedAtMs = 0L;
                  } else {
                     Map<AutoDropper.DropPlan, Integer> matchingCounts = countMatchingItems(client, plans);
                     Map<String, Integer> keptItems = new HashMap<>();
                     Map<String, Integer> keptStacks = new HashMap<>();
                     int selected = client.player.getInventory().getSelectedSlot();
                     boolean burstStarted = false;

                     try {
                        for (int invSlot = 0; invSlot < client.player.getInventory().getContainerSize() && dropped < max; invSlot++) {
                           if ((cfg.autoDropperIncludeHotbar || invSlot < 0 || invSlot > 8) && (!cfg.autoDropperProtectSelectedSlot || invSlot != selected)) {
                              ItemStack stack = client.player.getInventory().getItem(invSlot);
                              if (stack != null && !stack.isEmpty()) {
                                 AutoDropper.DropPlan plan = plans.match(stack);
                                 if (plan != null && (plan.minimumAmount <= 0 || matchingCounts.getOrDefault(plan, 0) >= plan.minimumAmount)) {
                                    String itemId = plan.key();
                                    int keepItems = Math.max(0, plan.keepItems);
                                    int keepStacks = Math.max(0, plan.keepStacks);
                                    int seenItems = keptItems.getOrDefault(itemId, 0);
                                    int seenStacks = keptStacks.getOrDefault(itemId, 0);
                                    if (seenItems >= keepItems && seenStacks >= keepStacks) {
                                       int screenSlot = inventorySlotToScreenSlot(invSlot);
                                       if (screenSlot >= 0) {
                                          if (!burstStarted) {
                                             runningAutoDropBurst = true;
                                             notifyMiningInterrupted(client);
                                             burstStarted = true;
                                          }

                                          client.gameMode
                                             .handleContainerInput(client.player.containerMenu.containerId, screenSlot, 1, ContainerInput.THROW, client.player);
                                          dropped++;
                                       }
                                    } else {
                                       keptItems.put(itemId, seenItems + stack.getCount());
                                       keptStacks.put(itemId, seenStacks + 1);
                                    }
                                 }
                              }
                           }
                        }
                     } finally {
                        runningAutoDropBurst = false;
                     }

                     lastDropAtMs = now;
                     if (dropped > 0 && isHeldMining(client)) {
                        lastHeldMiningDropAtMs = now;
                     }

                     queuedAtMs = dropped >= max ? now - Math.max(0, cfg.autoDropperDelayMs) : 0L;
                     if (manual) {
                        chatReporter.accept("AutoDropper dropped " + dropped + " stack" + (dropped == 1 ? "." : "s."));
                     }
                  }
               }
            }
         }
      }
   }

   private static AutoDropper.DropPlanIndex compilePlans(QolConfig cfg) {
      AutoDropper.DropPlanIndex index = new AutoDropper.DropPlanIndex();

      for (QolConfig.AutoDropCustomGroup group : cfg.autoDropperCustomGroups) {
         if (group != null && group.enabled) {
            addGroupPlans(index, group);
         }
      }

      return index;
   }

   private static boolean shouldDeferForHeldMining(Minecraft client, long now) {
      return !isHeldMining(client) ? false : lastHeldMiningDropAtMs > 0L && now - lastHeldMiningDropAtMs < 300L;
   }

   private static boolean shouldPauseForPlayerCombat(Minecraft client, long now) {
      QolConfig cfg = SuiteConfig.INSTANCE.QolConfig;
      if (cfg == null) {
         return false;
      } else if (cfg.autoDropperPauseOnPlayerAttack && now - lastPlayerAttackAtMs < 3000L) {
         return true;
      } else {
         return cfg.autoDropperPauseOnAttackingPlayer && now - lastAttackingPlayerAtMs < 3000L
            ? true
            : cfg.autoDropperPauseOnTargetingPlayer && isCrosshairOnPlayer(client);
      }
   }

   private static void pollInboundPlayerCombat(Minecraft client) {
      if (client != null && client.player != null) {
         QolConfig cfg = SuiteConfig.INSTANCE.QolConfig;
         if (cfg != null && cfg.autoDropperPauseOnPlayerAttack) {
            if (client.player.hurtTime > 0) {
               if (client.player.getLastHurtByMob() instanceof Player attacker && attacker != client.player) {
                  notePlayerAttack();
               }
            }
         }
      }
   }

   private static boolean isCrosshairOnPlayer(Minecraft client) {
      if (client != null && client.hitResult != null) {
         if (client.hitResult.getType() != Type.ENTITY) {
            return false;
         } else {
            return client.hitResult instanceof EntityHitResult entityHit ? entityHit.getEntity() instanceof Player : false;
         }
      } else {
         return false;
      }
   }

   private static boolean isHeldMining(Minecraft client) {
      return externalMiningRunner.get() || MiningResumeGuard.shouldKeepBreaking(client);
   }

   private static void notifyMiningInterrupted(Minecraft client) {
      if (externalMiningRunner.get()) {
         externalMiningInterruptHandler.accept(client);
      } else {
         MiningResumeGuard.onAutoDropLikeInventoryAction(client);
      }
   }

   private static void addGroupPlans(AutoDropper.DropPlanIndex index, QolConfig.AutoDropCustomGroup group) {
      if (group != null && group.enabled) {
         for (QolConfig.AutoDropGroupItem item : group.items) {
            if (item != null && item.itemId != null && !item.itemId.isBlank()) {
               index.add(new AutoDropper.DropPlan(item.itemId, item.minimumAmount, item.keepItems, item.keepStacks, item.componentFilter));
            }
         }
      }
   }

   private static Map<AutoDropper.DropPlan, Integer> countMatchingItems(Minecraft client, AutoDropper.DropPlanIndex plans) {
      Map<AutoDropper.DropPlan, Integer> counts = new HashMap<>();

      for (int invSlot = 0; invSlot < client.player.getInventory().getContainerSize(); invSlot++) {
         ItemStack stack = client.player.getInventory().getItem(invSlot);
         if (stack != null && !stack.isEmpty()) {
            AutoDropper.DropPlan plan = plans.match(stack);
            if (plan != null) {
               counts.put(plan, counts.getOrDefault(plan, 0) + stack.getCount());
            }
         }
      }

      return counts;
   }

   private static String itemId(ItemStack stack) {
      Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
      return id == null ? "" : id.toString();
   }

   private static int inventorySlotToScreenSlot(int invSlot) {
      if (invSlot >= 0 && invSlot <= 8) {
         return invSlot + 36;
      } else {
         return invSlot >= 9 && invSlot <= 35 ? invSlot : -1;
      }
   }

   private static AutoDropper.InventoryScan scanInventory(Minecraft client, QolConfig cfg) {
      if (client != null && client.player != null && cfg != null) {
         long h = 1125899906842597L;
         boolean full = true;
         int selected = client.player.getInventory().getSelectedSlot();
         int size = client.player.getInventory().getContainerSize();

         for (int i = 0; i < size; i++) {
            boolean droppableSlot = i <= 35 && (cfg.autoDropperIncludeHotbar || i > 8) && (!cfg.autoDropperProtectSelectedSlot || i != selected);
            ItemStack stack = client.player.getInventory().getItem(i);
            if (stack != null && !stack.isEmpty()) {
               h = h * 31L + itemId(stack).hashCode();
               h = h * 31L + stack.getCount();
            } else {
               h = h * 31L + i;
               if (droppableSlot) {
                  full = false;
               }
            }
         }

         return new AutoDropper.InventoryScan(full, h);
      } else {
         return new AutoDropper.InventoryScan(false, 0L);
      }
   }

   private static void resetTransient() {
      queuedAtMs = 0L;
      hadInventoryHash = false;
      wasSneaking = false;
      lastDropAtMs = 0L;
      lastHeldMiningDropAtMs = 0L;
      lastPlayerAttackAtMs = 0L;
      lastAttackingPlayerAtMs = 0L;
   }

   private record DropPlan(String itemId, int minimumAmount, int keepItems, int keepStacks, String componentFilter) {
      private String key() {
         return this.itemId == null ? "" : this.itemId;
      }

      private boolean matches(ItemStack stack) {
         if (stack != null && !stack.isEmpty()) {
            if (!AutoDropper.itemId(stack).equals(this.key())) {
               return false;
            }

            String filter = this.componentFilter == null ? "" : this.componentFilter.trim();
            return filter.isEmpty() || stack.getComponents().toString().contains(filter);
         } else {
            return false;
         }
      }
   }

   private static final class DropPlanIndex {
      private final Map<String, List<AutoDropper.DropPlan>> byItemId = new HashMap<>();

      private void add(AutoDropper.DropPlan plan) {
         if (plan != null && !plan.key().isBlank()) {
            List<AutoDropper.DropPlan> plans = this.byItemId.computeIfAbsent(plan.key(), ignored -> new ArrayList<>());

            for (AutoDropper.DropPlan existing : plans) {
               if (existing.key().equals(plan.key()) && safe(existing.componentFilter).equals(safe(plan.componentFilter))) {
                  return;
               }
            }

            plans.add(plan);
         }
      }

      private boolean isEmpty() {
         return this.byItemId.isEmpty();
      }

      private AutoDropper.DropPlan match(ItemStack stack) {
         List<AutoDropper.DropPlan> plans = this.byItemId.get(AutoDropper.itemId(stack));
         if (plans != null && !plans.isEmpty()) {
            for (AutoDropper.DropPlan plan : plans) {
               if (plan.matches(stack)) {
                  return plan;
               }
            }

            return null;
         } else {
            return null;
         }
      }

      private static String safe(String value) {
         return value == null ? "" : value.trim();
      }
   }

   private record InventoryScan(boolean droppableInventoryFull, long hash) {
   }
}
