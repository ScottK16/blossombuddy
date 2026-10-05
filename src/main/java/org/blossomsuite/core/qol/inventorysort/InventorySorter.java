package org.blossomsuite.core.qol.inventorysort;

import org.blossomsuite.core.config.QolConfig;
import org.blossomsuite.core.config.SuiteConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerInput;
public final class InventorySorter {
   private static final int MAX_OPERATIONS_PER_TICK = 8;
   private static final Deque<InventorySorter.SlotOperation> PENDING_OPERATIONS = new ArrayDeque<>();
   private static int queuedSyncId = -1;

   private InventorySorter() {
   }

   public static void tick(Minecraft client) {
      if (!PENDING_OPERATIONS.isEmpty()) {
         if (client == null || client.player == null || client.gameMode == null) {
            clearQueue();
         } else if (client.player.containerMenu != null
            && client.player.containerMenu.containerId == queuedSyncId
            && client.player.containerMenu.getCarried().isEmpty()) {
            int operations = Math.min(8, PENDING_OPERATIONS.size());

            for (int i = 0; i < operations; i++) {
               PENDING_OPERATIONS.removeFirst().perform(client);
               if (client.player.containerMenu == null
                  || client.player.containerMenu.containerId != queuedSyncId
                  || !client.player.containerMenu.getCarried().isEmpty()) {
                  clearQueue();
                  return;
               }
            }

            if (PENDING_OPERATIONS.isEmpty()) {
               queuedSyncId = -1;
            }
         } else {
            clearQueue();
         }
      }
   }

   public static void sortPlayerInventory(Minecraft client) {
      if (canStartSort(client)) {
         QolConfig cfg = SuiteConfig.INSTANCE.QolConfig;
         if (cfg != null && cfg.inventorySortEnabled) {
            startQueue(client);
            sortSlots(playerInventoryEntries(client, cfg), cfg.inventorySortStackMatching);
            finishQueueIfEmpty();
         }
      }
   }

   public static void sortOpenContainer(Minecraft client) {
      if (canStartSort(client)) {
         QolConfig cfg = SuiteConfig.INSTANCE.QolConfig;
         if (cfg != null && cfg.inventorySortEnabled) {
            startQueue(client);
            sortSlots(openContainerEntries(client), cfg.inventorySortStackMatching);
            finishQueueIfEmpty();
         }
      }
   }

   public static void sortPlayerInventoryAndOpenContainer(Minecraft client) {
      if (canStartSort(client)) {
         QolConfig cfg = SuiteConfig.INSTANCE.QolConfig;
         if (cfg != null && cfg.inventorySortEnabled) {
            startQueue(client);
            sortSlots(playerInventoryEntries(client, cfg), cfg.inventorySortStackMatching);
            sortSlots(openContainerEntries(client), cfg.inventorySortStackMatching);
            finishQueueIfEmpty();
         }
      }
   }

   public static void depositAllToOpenContainer(Minecraft client) {
      if (canStartSort(client)) {
         QolConfig cfg = SuiteConfig.INSTANCE.QolConfig;
         if (cfg != null && cfg.inventorySortEnabled) {
            if (openContainerSlotCount(client) > 1) {
               startQueue(client);

               for (InventorySorter.Entry entry : allPlayerInventoryEntries(client, cfg.inventoryManagementDepositIgnoresLockedSlots)) {
                  if (!entry.stack.isEmpty()) {
                     queueQuickMove(entry.handlerSlot);
                  }
               }

               finishQueueIfEmpty();
            }
         }
      }
   }

   public static void depositMatchingToOpenContainer(Minecraft client) {
      if (canStartSort(client)) {
         QolConfig cfg = SuiteConfig.INSTANCE.QolConfig;
         if (cfg != null && cfg.inventorySortEnabled) {
            List<ItemStack> containerStacks = openContainerEntries(client)
               .stream()
               .map(entryx -> entryx.stack)
               .filter(stack -> stack != null && !stack.isEmpty())
               .toList();
            if (!containerStacks.isEmpty()) {
               startQueue(client);

               for (InventorySorter.Entry entry : allPlayerInventoryEntries(client, cfg.inventoryManagementDepositIgnoresLockedSlots)) {
                  if (!entry.stack.isEmpty() && matchesAnyExistingContainerStack(entry.stack, containerStacks)) {
                     queueQuickMove(entry.handlerSlot);
                  }
               }

               finishQueueIfEmpty();
            }
         }
      }
   }

   public static void withdrawAllFromOpenContainer(Minecraft client) {
      if (canStartSort(client)) {
         QolConfig cfg = SuiteConfig.INSTANCE.QolConfig;
         if (cfg != null && cfg.inventorySortEnabled) {
            if (openContainerSlotCount(client) > 1) {
               startQueue(client);

               for (InventorySorter.Entry entry : openContainerEntries(client)) {
                  if (!entry.stack.isEmpty()) {
                     queueQuickMove(entry.handlerSlot);
                  }
               }

               finishQueueIfEmpty();
            }
         }
      }
   }

   public static void withdrawMatchingFromOpenContainer(Minecraft client) {
      if (canStartSort(client)) {
         QolConfig cfg = SuiteConfig.INSTANCE.QolConfig;
         if (cfg != null && cfg.inventorySortEnabled) {
            List<ItemStack> playerStacks = allPlayerInventoryEntries(client, true)
               .stream()
               .map(entryx -> entryx.stack)
               .filter(stack -> stack != null && !stack.isEmpty())
               .toList();
            if (!playerStacks.isEmpty()) {
               startQueue(client);

               for (InventorySorter.Entry entry : openContainerEntries(client)) {
                  if (!entry.stack.isEmpty() && matchesAnyExistingContainerStack(entry.stack, playerStacks)) {
                     queueQuickMove(entry.handlerSlot);
                  }
               }

               finishQueueIfEmpty();
            }
         }
      }
   }

   public static boolean canSortOpenContainer(Minecraft client) {
      if (client != null && client.player != null) {
         QolConfig cfg = SuiteConfig.INSTANCE.QolConfig;
         return cfg != null && cfg.inventorySortEnabled && openContainerSlotCount(client) > 1;
      } else {
         return false;
      }
   }

   private static int openContainerSlotCount(Minecraft client) {
      if (client != null && client.player != null && client.player.containerMenu != null) {
         if (client.player.containerMenu instanceof ChestMenu handler) {
            return handler.getRowCount() * 9;
         } else {
            return client.player.containerMenu instanceof ShulkerBoxMenu ? 27 : 0;
         }
      } else {
         return 0;
      }
   }

   private static boolean canStartSort(Minecraft client) {
      return client != null
         && client.player != null
         && client.gameMode != null
         && client.player.containerMenu != null
         && client.player.containerMenu.getCarried().isEmpty();
   }

   private static void startQueue(Minecraft client) {
      PENDING_OPERATIONS.clear();
      queuedSyncId = client.player.containerMenu.containerId;
   }

   private static void finishQueueIfEmpty() {
      if (PENDING_OPERATIONS.isEmpty()) {
         queuedSyncId = -1;
      }
   }

   private static List<InventorySorter.Entry> playerInventoryEntries(Minecraft client, QolConfig cfg) {
      List<InventorySorter.Entry> entries = new ArrayList<>();

      for (int invIndex = 0; invIndex < 36; invIndex++) {
         if (!cfg.isInventorySortSlotLocked(invIndex)) {
            int handlerSlot = handlerSlotForInventoryIndex(client, invIndex);
            if (handlerSlot >= 0) {
               ItemStack stack = client.player.getInventory().getItem(invIndex);
               entries.add(new InventorySorter.Entry(handlerSlot, stack.copy()));
            }
         }
      }

      return entries;
   }

   private static List<InventorySorter.Entry> allPlayerInventoryEntries(Minecraft client, boolean includeLockedSlots) {
      List<InventorySorter.Entry> entries = new ArrayList<>();
      QolConfig cfg = SuiteConfig.INSTANCE.QolConfig;

      for (int invIndex = 0; invIndex < 36; invIndex++) {
         if (includeLockedSlots || cfg == null || !cfg.isInventorySortSlotLocked(invIndex)) {
            int handlerSlot = handlerSlotForInventoryIndex(client, invIndex);
            if (handlerSlot >= 0) {
               ItemStack stack = client.player.getInventory().getItem(invIndex);
               entries.add(new InventorySorter.Entry(handlerSlot, stack.copy()));
            }
         }
      }

      return entries;
   }

   private static List<InventorySorter.Entry> openContainerEntries(Minecraft client) {
      List<InventorySorter.Entry> entries = new ArrayList<>();
      int containerSlots = openContainerSlotCount(client);
      if (containerSlots <= 1) {
         return entries;
      }

      for (int slotId = 0; slotId < containerSlots && slotId < client.player.containerMenu.slots.size(); slotId++) {
         Slot slot = client.player.containerMenu.slots.get(slotId);
         entries.add(new InventorySorter.Entry(slot.index, slot.getItem().copy()));
      }

      return entries;
   }

   private static void sortSlots(List<InventorySorter.Entry> entries, boolean stackMatching) {
      if (entries.size() > 1) {
         if (stackMatching) {
            stackMatchingSlots(entries);
         }

         List<ItemStack> sortedStacks = entries.stream().map(entry -> entry.stack.copy()).sorted(stackComparator()).toList();
         List<ItemStack> simulated = entries.stream().map(entry -> entry.stack.copy()).collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

         for (int i = 0; i < entries.size(); i++) {
            ItemStack want = sortedStacks.get(i);
            if (!sameStack(simulated.get(i), want)) {
               int found = -1;

               for (int j = i + 1; j < simulated.size(); j++) {
                  if (sameStack(simulated.get(j), want)) {
                     found = j;
                     break;
                  }
               }

               if (found >= 0) {
                  queueMove(entries.get(i).handlerSlot, entries.get(found).handlerSlot);
                  ItemStack tmp = simulated.get(i);
                  simulated.set(i, simulated.get(found));
                  simulated.set(found, tmp);
               }
            }
         }
      }
   }

   private static int stackMatchingSlots(List<InventorySorter.Entry> entries) {
      int moves = 0;

      for (int targetIndex = 0; targetIndex < entries.size(); targetIndex++) {
         ItemStack target = entries.get(targetIndex).stack;
         if (canReceiveStack(target)) {
            for (int sourceIndex = targetIndex + 1; sourceIndex < entries.size() && target.getCount() < target.getMaxStackSize(); sourceIndex++) {
               ItemStack source = entries.get(sourceIndex).stack;
               if (canDonateStack(source) && ItemStack.isSameItemSameComponents(target, source)) {
                  int transferable = Math.min(source.getCount(), target.getMaxStackSize() - target.getCount());
                  if (transferable > 0) {
                     queueMove(entries.get(sourceIndex).handlerSlot, entries.get(targetIndex).handlerSlot);
                     target.grow(transferable);
                     source.shrink(transferable);
                     moves++;
                  }
               }
            }
         }
      }

      return moves;
   }

   private static boolean canReceiveStack(ItemStack stack) {
      return stack != null && !stack.isEmpty() && stack.isStackable() && stack.getCount() < stack.getMaxStackSize();
   }

   private static boolean canDonateStack(ItemStack stack) {
      return stack != null && !stack.isEmpty() && stack.isStackable();
   }

   private static int handlerSlotForInventoryIndex(Minecraft client, int inventoryIndex) {
      for (Slot slot : client.player.containerMenu.slots) {
         if (slot.container == client.player.getInventory() && slot.getContainerSlot() == inventoryIndex) {
            return slot.index;
         }
      }

      return -1;
   }

   private static void queueMove(int source, int target) {
      if (source != target) {
         PENDING_OPERATIONS.addLast(new InventorySorter.ClickOperation(source, target));
      }
   }

   private static void queueQuickMove(int slot) {
      PENDING_OPERATIONS.addLast(new InventorySorter.QuickMoveOperation(slot));
   }

   private static void clearQueue() {
      PENDING_OPERATIONS.clear();
      queuedSyncId = -1;
   }

   private static Comparator<ItemStack> stackComparator() {
      return (a, b) -> {
         boolean ae = a == null || a.isEmpty();
         boolean be = b == null || b.isEmpty();
         if (ae && be) {
            return 0;
         }

         if (ae) {
            return 1;
         }

         if (be) {
            return -1;
         }

         String aid = BuiltInRegistries.ITEM.getKey(a.getItem()).toString();
         String bid = BuiltInRegistries.ITEM.getKey(b.getItem()).toString();
         int id = aid.compareToIgnoreCase(bid);
         if (id != 0) {
            return id;
         }

         String an = a.getHoverName().getString();
         String bn = b.getHoverName().getString();
         int name = an.compareToIgnoreCase(bn);
         return name != 0 ? name : Integer.compare(b.getCount(), a.getCount());
      };
   }

   private static boolean sameStack(ItemStack a, ItemStack b) {
      if (a != null && !a.isEmpty() || b != null && !b.isEmpty()) {
         if (a == null || b == null) {
            return false;
         } else {
            return a.getCount() != b.getCount() ? false : ItemStack.isSameItemSameComponents(a, b);
         }
      } else {
         return true;
      }
   }

   private static boolean matchesAnyExistingContainerStack(ItemStack stack, List<ItemStack> containerStacks) {
      for (ItemStack existing : containerStacks) {
         if (ItemStack.isSameItemSameComponents(stack, existing)) {
            return true;
         }
      }

      return false;
   }

   private record ClickOperation(int source, int target) implements InventorySorter.SlotOperation {
      @Override
      public void perform(Minecraft client) {
         int syncId = client.player.containerMenu.containerId;
         client.gameMode.handleContainerInput(syncId, this.source, 0, ContainerInput.PICKUP, client.player);
         client.gameMode.handleContainerInput(syncId, this.target, 0, ContainerInput.PICKUP, client.player);
         client.gameMode.handleContainerInput(syncId, this.source, 0, ContainerInput.PICKUP, client.player);
      }
   }

   private record Entry(int handlerSlot, ItemStack stack) {
   }

   private record QuickMoveOperation(int slot) implements InventorySorter.SlotOperation {
      @Override
      public void perform(Minecraft client) {
         int syncId = client.player.containerMenu.containerId;
         client.gameMode.handleContainerInput(syncId, this.slot, 0, ContainerInput.QUICK_MOVE, client.player);
      }
   }

   private interface SlotOperation {
      void perform(Minecraft var1);
   }
}
