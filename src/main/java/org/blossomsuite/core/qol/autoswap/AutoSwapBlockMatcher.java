package org.blossomsuite.core.qol.autoswap;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

public final class AutoSwapBlockMatcher {
   private static final TagKey<Block> C_ORES = TagKey.of(RegistryKeys.BLOCK, Identifier.of("c", "ores"));

   private AutoSwapBlockMatcher() {
   }

   public static boolean matchesGroup(BlockState state, AutoSwapGrouping group) {
      if (state != null && group != null) {
         return switch (group) {
            case ORES -> isOreLike(state);
            case LOGS -> state.isIn(BlockTags.LOGS);
            case PLANKS -> isPathSuffix(state, "_planks");
            case SHOVEL_MINEABLE -> state.isIn(BlockTags.SHOVEL_MINEABLE);
            case PICKAXE_MINEABLE -> state.isIn(BlockTags.PICKAXE_MINEABLE);
            case AXE_MINEABLE -> state.isIn(BlockTags.AXE_MINEABLE);
            case HOE_MINEABLE -> state.isIn(BlockTags.HOE_MINEABLE);
            case SHEARS_MINEABLE -> isShearsLike(state);
            case SWORD_EFFICIENT -> state.isIn(BlockTags.SWORD_EFFICIENT);
            case LEAVES -> state.isIn(BlockTags.LEAVES);
            case MUSHROOMS -> state.isOf(Blocks.BROWN_MUSHROOM_BLOCK) || state.isOf(Blocks.RED_MUSHROOM_BLOCK) || state.isOf(Blocks.MUSHROOM_STEM);
            case SPAWNERS -> state.isOf(Blocks.SPAWNER);
            case DIRT_LIKE -> state.isIn(BlockTags.DIRT);
            case SAND_LIKE -> state.isIn(BlockTags.SAND);
            case GLASS -> isGlassLike(state);
            case WOOL -> state.isIn(BlockTags.WOOL);
            case SAPLINGS -> state.isIn(BlockTags.SAPLINGS);
            case CROPS -> state.isIn(BlockTags.CROPS);
            case FLOWERS -> state.isIn(BlockTags.FLOWERS);
            case SMALL_FLOWERS -> state.isIn(BlockTags.SMALL_FLOWERS);
            case AMETHYST -> isAmethystLike(state);
            case LIGHT_EMITTING -> state.getLuminance() > 0;
            case DECORATIVE_LIGHTS -> isDecorativeLight(state);
            case TERRACOTTA -> isPathSuffix(state, "terracotta") || isPathSuffix(state, "_terracotta");
            case CONCRETE -> isPathSuffix(state, "_concrete") || isPathSuffix(state, "_concrete_powder");
            case ICE -> state.isOf(Blocks.ICE) || state.isOf(Blocks.PACKED_ICE) || state.isOf(Blocks.BLUE_ICE) || state.isOf(Blocks.FROSTED_ICE);
            case RAILS -> state.isIn(BlockTags.RAILS);
            case REDSTONE_COMPONENTS -> isRedstoneComponent(state);
         };
      } else {
         return false;
      }
   }

   public static boolean matchesBlockId(BlockState state, String blockId) {
      if (state != null && blockId != null && !blockId.isBlank()) {
         Identifier id = Identifier.tryParse(blockId);
         if (id == null) {
            return false;
         }

         Block block = Registries.BLOCK.get(id);
         return state.isOf(block);
      } else {
         return false;
      }
   }

   private static boolean isGlassLike(BlockState state) {
      Identifier id = Registries.BLOCK.getId(state.getBlock());
      String path = id.getPath();
      return path.endsWith("_glass") || path.endsWith("_glass_pane") || path.equals("glass") || path.equals("glass_pane") || path.equals("tinted_glass");
   }

   private static boolean isDecorativeLight(BlockState state) {
      Identifier id = Registries.BLOCK.getId(state.getBlock());
      String path = id.getPath();
      return state.getLuminance() > 0
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
      Identifier id = Registries.BLOCK.getId(state.getBlock());
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
      Identifier id = Registries.BLOCK.getId(state.getBlock());
      return id.getPath().endsWith(suffix);
   }

   private static boolean isShearsLike(BlockState state) {
      return state.isIn(BlockTags.LEAVES)
         || state.isIn(BlockTags.WOOL)
         || state.isOf(Blocks.VINE)
         || state.isOf(Blocks.GLOW_LICHEN)
         || state.isOf(Blocks.COBWEB)
         || state.isOf(Blocks.TRIPWIRE)
         || state.isOf(Blocks.DEAD_BUSH)
         || state.isOf(Blocks.FERN)
         || state.isOf(Blocks.SHORT_GRASS)
         || state.isOf(Blocks.TALL_GRASS)
         || state.isOf(Blocks.SEAGRASS)
         || state.isOf(Blocks.TALL_SEAGRASS)
         || state.isOf(Blocks.HANGING_ROOTS);
   }

   private static boolean isAmethystLike(BlockState state) {
      return state.isOf(Blocks.AMETHYST_BLOCK)
         || state.isOf(Blocks.BUDDING_AMETHYST)
         || state.isOf(Blocks.SMALL_AMETHYST_BUD)
         || state.isOf(Blocks.MEDIUM_AMETHYST_BUD)
         || state.isOf(Blocks.LARGE_AMETHYST_BUD)
         || state.isOf(Blocks.AMETHYST_CLUSTER);
   }

   public static boolean isOreLike(BlockState state) {
      if (state == null) {
         return false;
      }

      if (state.isOf(Blocks.ANCIENT_DEBRIS)) {
         return false;
      }

      if (state.isIn(C_ORES)) {
         return true;
      }

      Identifier id = Registries.BLOCK.getId(state.getBlock());
      String path = id.getPath();
      return path.endsWith("_ore") || path.contains("_ore_") || !path.equals("ancient_debris") && (path.endsWith("_debris") || path.contains("_debris_"));
   }
}
