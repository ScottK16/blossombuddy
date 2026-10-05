package org.blossomsuite.core.qol.autoswap;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.Identifier;
public final class AutoSwapBlockMatcher {
   private static final TagKey<Block> C_ORES = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "ores"));

   private AutoSwapBlockMatcher() {
   }

   public static boolean matchesGroup(BlockState state, AutoSwapGrouping group) {
      if (state != null && group != null) {
         return switch (group) {
            case ORES -> isOreLike(state);
            case LOGS -> state.is(BlockTags.LOGS);
            case PLANKS -> isPathSuffix(state, "_planks");
            case SHOVEL_MINEABLE -> state.is(BlockTags.MINEABLE_WITH_SHOVEL);
            case PICKAXE_MINEABLE -> state.is(BlockTags.MINEABLE_WITH_PICKAXE);
            case AXE_MINEABLE -> state.is(BlockTags.MINEABLE_WITH_AXE);
            case HOE_MINEABLE -> state.is(BlockTags.MINEABLE_WITH_HOE);
            case SHEARS_MINEABLE -> isShearsLike(state);
            case SWORD_EFFICIENT -> state.is(BlockTags.SWORD_EFFICIENT);
            case LEAVES -> state.is(BlockTags.LEAVES);
            case MUSHROOMS -> state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.RED_MUSHROOM_BLOCK) || state.is(Blocks.MUSHROOM_STEM);
            case SPAWNERS -> state.is(Blocks.SPAWNER);
            case DIRT_LIKE -> state.is(BlockTags.DIRT);
            case SAND_LIKE -> state.is(BlockTags.SAND);
            case GLASS -> isGlassLike(state);
            case WOOL -> state.is(BlockTags.WOOL);
            case SAPLINGS -> state.is(BlockTags.SAPLINGS);
            case CROPS -> state.is(BlockTags.CROPS);
            case FLOWERS -> state.is(BlockTags.FLOWERS);
            case SMALL_FLOWERS -> state.is(BlockTags.SMALL_FLOWERS);
            case AMETHYST -> isAmethystLike(state);
            case LIGHT_EMITTING -> state.getLightEmission() > 0;
            case DECORATIVE_LIGHTS -> isDecorativeLight(state);
            case TERRACOTTA -> isPathSuffix(state, "terracotta") || isPathSuffix(state, "_terracotta");
            case CONCRETE -> isPathSuffix(state, "_concrete") || isPathSuffix(state, "_concrete_powder");
            case ICE -> state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE) || state.is(Blocks.FROSTED_ICE);
            case RAILS -> state.is(BlockTags.RAILS);
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

         Block block = BuiltInRegistries.BLOCK.getValue(id);
         return state.is(block);
      } else {
         return false;
      }
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

   public static boolean isOreLike(BlockState state) {
      if (state == null) {
         return false;
      }

      if (state.is(Blocks.ANCIENT_DEBRIS)) {
         return false;
      }

      if (state.is(C_ORES)) {
         return true;
      }

      Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
      String path = id.getPath();
      return path.endsWith("_ore") || path.contains("_ore_") || !path.equals("ancient_debris") && (path.endsWith("_debris") || path.contains("_debris_"));
   }
}
