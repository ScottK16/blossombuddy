package org.blossomsuite.core.finder;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.chat.ChatOutput;
import org.blossomsuite.core.config.FeatureConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.session.SessionStats;
import org.blossomsuite.core.state.SuiteScheduler;
import org.blossomsuite.core.util.WorldGate;

/**
 * The chest finder: notices which container you open, remembers what was in it when you closed it, and answers
 * {@code /buddy find diamond} with where those items are (and lights those chests up for a minute). Local to your computer.
 */
public final class ContainerFinder {
   public static final ContainerFinder INSTANCE = new ContainerFinder();
   /** A container screen counts as "that block" only if it opens this soon after you used the block. */
   static final long OPEN_WINDOW_MS = 3_000L;
   static final long MARK_MS = 60_000L;
   private static final int MAX_LINES = 8;

   private final ContainerIndex index = new ContainerIndex();
   private Path file;
   private BlockPos lastUsePos;
   private long lastUseAtMs;
   private final List<Mark> marks = new ArrayList<>();

   private record Mark(BlockPos pos, String dimension, long untilMs) {
   }

   private ContainerFinder() {
   }

   public ContainerIndex index() {
      return this.index;
   }

   public void init() {
      this.file = FabricLoader.getInstance().getConfigDir().resolve(SuiteRuntime.profile().modId()).resolve("containers.json");
      this.index.load(this.file);
      UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
         if (level.isClientSide()) {
            this.lastUsePos = hit.getBlockPos();
            this.lastUseAtMs = System.currentTimeMillis();
         }

         return InteractionResult.PASS;
      });
      ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
         if (screen instanceof AbstractContainerScreen<?> container) {
            // the block that opened this screen: only one used a moment ago counts (a shop menu opened by a command has none)
            BlockPos opener = this.lastUsePos != null && System.currentTimeMillis() - this.lastUseAtMs <= OPEN_WINDOW_MS ? this.lastUsePos : null;
            this.lastUsePos = null;
            if (opener != null) {
               ScreenEvents.remove(screen).register(closed -> this.onClosed(client, container, opener));
            }
         }
      });
   }

   private boolean enabled() {
      return FeatureConfig.INSTANCE.finder.enabled && SuiteConfig.INSTANCE.isEnabledForCurrentWorld();
   }

   private static String currentRealm() {
      String realm = WorldGate.Server;
      return realm == null || realm.isBlank() ? "unknown" : realm.toLowerCase(Locale.ROOT);
   }

   private void onClosed(Minecraft client, AbstractContainerScreen<?> screen, BlockPos pos) {
      if (!this.enabled() || client.player == null || client.level == null) {
         return;
      }

      long now = System.currentTimeMillis();
      // the block that opened it has to be a real container too
      Level level = client.level;
      BlockState state = level.getBlockState(pos);
      boolean ender = state.is(Blocks.ENDER_CHEST);
      if (!ender && !(level.getBlockEntity(pos) instanceof net.minecraft.world.Container)) {
         return;
      }

      AbstractContainerMenu menu = screen.getMenu();
      Inventory mine = client.player.getInventory();
      int containerSlots = 0;
      List<ContainerIndex.Stack> items = new ArrayList<>();
      for (Slot slot : menu.slots) {
         if (slot.container == mine) {
            continue;
         }

         containerSlots++;
         ItemStack stack = slot.getItem();
         if (!stack.isEmpty()) {
            add(items, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getHoverName().getString(), stack.getCount());
         }
      }

      if (containerSlots == 0) {
         return;
      }

      String dimension = level.dimension().identifier().toString();
      String realm = currentRealm();
      ContainerIndex.Container c = new ContainerIndex.Container();
      c.realm = realm;
      c.dimension = dimension;
      c.portable = ender;
      c.x = pos.getX();
      c.y = pos.getY();
      c.z = pos.getZ();
      c.label = state.getBlock().getName().getString();
      c.key = ContainerIndex.keyFor(realm, dimension, c.x, c.y, c.z, ender);
      c.updatedMs = now;
      c.items = items;
      this.index.record(c);
      SuiteScheduler.IO.execute(() -> this.index.save(this.file));
   }

   private static void add(List<ContainerIndex.Stack> items, String id, String name, int count) {
      for (ContainerIndex.Stack s : items) {
         if (s.id.equals(id) && s.name.equals(name)) {
            s.count += count;
            return;
         }
      }

      items.add(new ContainerIndex.Stack(id, name, count));
   }

   // ------------------------------------------------------------------ asking

   /** {@code /buddy find <words>}: lists where it is and lights up the nearest few for a minute. */
   public void report(String query) {
      Minecraft mc = Minecraft.getInstance();
      org.blossomsuite.core.progress.Progress.first("find");
      List<ContainerIndex.Hit> hits = this.index.search(query, null);
      if (hits.isEmpty()) {
         ChatOutput.info(this.index.size() == 0
            ? "Nothing remembered yet. Open a chest, barrel or other container and I'll remember what's in it."
            : "No container you've opened has '" + query.trim() + "' in it (" + this.index.size() + " remembered).");
         return;
      }

      long now = System.currentTimeMillis();
      String realm = currentRealm();
      String dimension = mc.level == null ? "" : mc.level.dimension().identifier().toString();
      int total = 0;
      for (ContainerIndex.Hit h : hits) {
         total += h.count();
      }

      ChatOutput.info(Component.literal("Found " + total + " in " + hits.size() + (hits.size() == 1 ? " container" : " containers") + " (as of when you last looked):").withStyle(ChatFormatting.GRAY));
      synchronized (this.marks) {
         this.marks.clear();
         int shown = 0;
         for (ContainerIndex.Hit h : hits) {
            if (shown++ >= MAX_LINES) {
               break;
            }

            ContainerIndex.Container c = h.container();
            boolean here = !c.portable && c.realm.equalsIgnoreCase(realm) && c.dimension.equals(dimension);
            if (here && this.marks.size() < 6) {
               this.marks.add(new Mark(new BlockPos(c.x, c.y, c.z), c.dimension, now + MARK_MS));
            }

            ChatOutput.raw(describe(h, now, realm, dimension));
         }

         if (hits.size() > MAX_LINES) {
            ChatOutput.raw(Component.literal("  ...and " + (hits.size() - MAX_LINES) + " more").withStyle(ChatFormatting.DARK_GRAY));
         }

         if (!this.marks.isEmpty()) {
            ChatOutput.raw(Component.literal("  The ones near you are lit up for a minute.").withStyle(ChatFormatting.DARK_GRAY));
         }
      }
   }

   static Component describe(ContainerIndex.Hit h, long nowMs, String realm, String dimension) {
      ContainerIndex.Container c = h.container();
      String where = c.portable
         ? c.label + " (yours on every realm)"
         : c.label + " at " + c.x + ", " + c.y + ", " + c.z + (c.dimension.equals(dimension) ? "" : " [" + shortDimension(c.dimension) + "]");
      String far = c.realm.equalsIgnoreCase(realm) ? "" : " on " + SessionStats.realmLabel(c.realm);
      return Component.literal("  " + h.count() + "x ").withStyle(ChatFormatting.GREEN)
         .append(Component.literal(h.itemName()).withStyle(ChatFormatting.WHITE))
         .append(Component.literal(" - " + where + far + ", " + SessionStats.duration(nowMs - c.updatedMs) + " ago").withStyle(ChatFormatting.GRAY));
   }

   static String shortDimension(String dimension) {
      int colon = dimension.indexOf(':');
      return colon >= 0 ? dimension.substring(colon + 1) : dimension;
   }

   public void forgetEverything() {
      this.index.clear();
      synchronized (this.marks) {
         this.marks.clear();
      }

      SuiteScheduler.IO.execute(() -> this.index.save(this.file));
   }

   /** Called every tick from the world overlays: draws the boxes around found containers, through walls. */
   public void drawMarks(Minecraft client) {
      if (client.level == null) {
         return;
      }

      long now = System.currentTimeMillis();
      String dimension = client.level.dimension().identifier().toString();
      synchronized (this.marks) {
         this.marks.removeIf(m -> m.untilMs() < now);
         for (Mark m : this.marks) {
            if (m.dimension().equals(dimension)) {
               Gizmos.cuboid(new AABB(m.pos()).inflate(0.01), GizmoStyle.strokeAndFill(0xFFFFC107, 2.0F, 0x40FFC107)).setAlwaysOnTop();
            }
         }
      }
   }
}
