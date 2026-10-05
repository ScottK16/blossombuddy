package org.blossomsuite.core.qol.autoswap;

import org.blossomsuite.core.config.QolConfig;
import org.blossomsuite.core.config.SuiteConfig;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.Identifier;
public final class AutoToolSwapper {
   private static final TagKey<Block> C_ORES = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "ores"));

   public static int pickSlotFor(BlockState state) {
      QolConfig cfg = SuiteConfig.INSTANCE.QolConfig;
      if (!cfg.autoSwapperEnabled) {
         return -1;
      }

      QolConfig.AutoSwapperProfile profile = getActiveProfile(cfg);
      if (profile == null) {
         return -1;
      }

      if (state == null) {
         return fallbackSlot(profile);
      }

      for (QolConfig.AutoSwapRule r : profile.rules) {
         if (r != null) {
            if (r.type == QolConfig.AutoSwapTargetType.GROUP) {
               if (matchesGroup(state, r.group)) {
                  return toInvSlot(r.slot);
               }
            } else if (r.type == QolConfig.AutoSwapTargetType.CUSTOM) {
               if (matchesCustomGroup(state, cfg, r.customGroupId)) {
                  return toInvSlot(r.slot);
               }
            } else if (matchesBlockId(state, r.blockId)) {
               return toInvSlot(r.slot);
            }
         }
      }

      return fallbackSlot(profile);
   }

   private static QolConfig.AutoSwapperProfile getActiveProfile(QolConfig cfg) {
      if (cfg != null && !cfg.autoSwapperProfiles.isEmpty()) {
         int idx = cfg.autoSwapperActiveProfile;
         if (idx < 0) {
            idx = 0;
         }

         if (idx >= cfg.autoSwapperProfiles.size()) {
            idx = cfg.autoSwapperProfiles.size() - 1;
         }

         return cfg.autoSwapperProfiles.get(idx);
      } else {
         return null;
      }
   }

   private static int fallbackSlot(QolConfig.AutoSwapperProfile profile) {
      if (profile == null) {
         return -1;
      } else {
         return !profile.useDefaultTool ? -1 : toInvSlot(profile.defaultSlot);
      }
   }

   private static int toInvSlot(int slot1to9) {
      int s = slot1to9;
      if (s < 1) {
         s = 1;
      }

      if (s > 9) {
         s = 9;
      }

      return s - 1;
   }

   private static boolean matchesGroup(BlockState state, QolConfig.AutoSwapGrouping g) {
      return g == null ? false : AutoSwapBlockMatcher.matchesGroup(state, AutoSwapGrouping.valueOf(g.name()));
   }

   private static boolean isGlassLike(BlockState state) {
      Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
      String path = id.getPath();
      return path.endsWith("_glass") || path.endsWith("_glass_pane") || path.equals("glass") || path.equals("glass_pane") || path.equals("tinted_glass");
   }

   private static boolean isDecorativeLight(BlockState state) {
      Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
      String path = id.getPath();
      return state.getLightEmission() > 0
         && (
            path.contains("froglight")
               || path.equals("shroomlight")
               || path.contains("lantern")
               || path.contains("torch")
               || path.contains("candle")
               || path.equals("glowstone")
               || path.equals("sea_lantern")
               || path.equals("end_rod")
               || path.equals("jack_o_lantern")
               || path.equals("soul_fire")
               || path.equals("fire")
               || path.equals("campfire")
               || path.equals("soul_campfire")
               || path.equals("redstone_lamp")
               || path.equals("ochre_froglight")
               || path.equals("pearlescent_froglight")
               || path.equals("verdant_froglight")
         );
   }

   private static boolean isRedstoneComponent(BlockState state) {
      Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
      String path = id.getPath();
      return path.contains("redstone")
         || path.endsWith("_button")
         || path.endsWith("_pressure_plate")
         || path.equals("lever")
         || path.equals("repeater")
         || path.equals("comparator")
         || path.equals("observer")
         || path.equals("dispenser")
         || path.equals("dropper")
         || path.equals("hopper")
         || path.equals("target")
         || path.equals("tripwire_hook")
         || path.equals("daylight_detector")
         || path.equals("piston")
         || path.equals("sticky_piston");
   }

   private static boolean isPathSuffix(BlockState state, String suffix) {
      Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
      return id.getPath().endsWith(suffix);
   }

   private static boolean isShearsLike(BlockState state) {
      return state.is(BlockTags.LEAVES)
         || state.is(BlockTags.WOOL)
         || state.is(Blocks.VINE)
         || state.is(Blocks.GLOW_LICHEN)
         || state.is(Blocks.COBWEB)
         || state.is(Blocks.TRIPWIRE)
         || state.is(Blocks.DEAD_BUSH)
         || state.is(Blocks.FERN)
         || state.is(Blocks.SHORT_GRASS)
         || state.is(Blocks.TALL_GRASS)
         || state.is(Blocks.SEAGRASS)
         || state.is(Blocks.TALL_SEAGRASS)
         || state.is(Blocks.HANGING_ROOTS);
   }

   private static boolean isAmethystLike(BlockState state) {
      return state.is(Blocks.AMETHYST_BLOCK)
         || state.is(Blocks.BUDDING_AMETHYST)
         || state.is(Blocks.SMALL_AMETHYST_BUD)
         || state.is(Blocks.MEDIUM_AMETHYST_BUD)
         || state.is(Blocks.LARGE_AMETHYST_BUD)
         || state.is(Blocks.AMETHYST_CLUSTER);
   }

   private static boolean isOreLike(BlockState state) {
      if (state.is(C_ORES)) {
         return true;
      }

      Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
      String path = id.getPath();
      return path.endsWith("_ore") || path.contains("_ore_") || path.equals("ancient_debris");
   }

   private static boolean matchesBlockId(BlockState state, String blockId) {
      return blockId != null && !blockId.isBlank() ? AutoSwapBlockMatcher.matchesBlockId(state, blockId) : false;
   }

   private static boolean matchesCustomGroup(BlockState state, QolConfig cfg, String groupId) {
      if (cfg != null && groupId != null && !groupId.isBlank()) {
         Identifier blockIdentifier = BuiltInRegistries.BLOCK.getKey(state.getBlock());
         String blockId = blockIdentifier.toString();

         for (QolConfig.AutoSwapCustomGroup group : cfg.autoSwapperCustomGroups) {
            if (group != null && groupId.equals(group.id)) {
               return group.blockIds.contains(blockId);
            }
         }

         return false;
      } else {
         return false;
      }
   }
}
