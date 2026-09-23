package org.blossomsuite.core.mapart;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

/**
 * Builds a vanilla Minecraft structure NBT (the same format a Structure Block saves - {@code DataVersion}, {@code size},
 * a block-state {@code palette} and a flat {@code blocks} list) from a map art design. This is deliberately the
 * official, stable, well-documented format rather than Litematica's own proprietary one: Litematica's file browser
 * auto-detects and loads vanilla structure NBT directly (its own "convert structures" support), so writing this one
 * real format gets us Litematica, a vanilla structure block, and WorldEdit all at once, with far less risk of getting
 * an undocumented binary format subtly wrong.
 *
 * <p>The whole design is one layer thick (Y stays 0 throughout) - map art is always flat.
 */
public final class SchematicWriter {
   /** Minecraft 1.21.8's data version, read straight from that version's own version.json rather than guessed. */
   public static final int DATA_VERSION = 4440;

   private SchematicWriter() {
   }

   /**
    * @param blocks width*height colour ids, row-major (index = z*width + x), matching the relay's project format
    * @param colorIdToBlockId a colour id's real block id (e.g. "minecraft:white_wool"); an id with no entry falls back to white wool
    * rather than failing the whole schematic over one bad lookup
    */
   public static NbtCompound build(int width, int height, int[] blocks, Map<Integer, String> colorIdToBlockId) {
      if (width <= 0 || height <= 0 || blocks == null || blocks.length != width * height) {
         throw new IllegalArgumentException("blocks must be a width*height grid");
      }

      NbtCompound root = new NbtCompound();
      root.putInt("DataVersion", DATA_VERSION);

      NbtList size = new NbtList();
      size.add(NbtInt.of(width));
      size.add(NbtInt.of(1));
      size.add(NbtInt.of(height));
      root.put("size", size);

      NbtList palette = new NbtList();
      NbtList blockList = new NbtList();
      Map<String, Integer> paletteIndex = new LinkedHashMap<>();

      for (int z = 0; z < height; z++) {
         for (int x = 0; x < width; x++) {
            String blockId = colorIdToBlockId.getOrDefault(blocks[z * width + x], "minecraft:white_wool");
            int index = paletteIndex.computeIfAbsent(blockId, id -> {
               NbtCompound entry = new NbtCompound();
               entry.putString("Name", id);
               palette.add(entry);
               return palette.size() - 1;
            });

            NbtCompound blockEntry = new NbtCompound();
            NbtList pos = new NbtList();
            pos.add(NbtInt.of(x));
            pos.add(NbtInt.of(0));
            pos.add(NbtInt.of(z));
            blockEntry.put("pos", pos);
            blockEntry.putInt("state", index);
            blockList.add(blockEntry);
         }
      }

      root.put("palette", palette);
      root.put("blocks", blockList);
      root.put("entities", new NbtList());
      return root;
   }

   /** Turns a project's per-colour material rows into the lookup {@link #build} needs. Colours with no blockId are skipped. */
   public static Map<Integer, String> paletteOf(java.util.List<MapArtModels.Material> materials) {
      Map<Integer, String> map = new LinkedHashMap<>();
      if (materials != null) {
         for (MapArtModels.Material m : materials) {
            if (m != null && m.blockId != null && !m.blockId.isBlank()) {
               map.put(m.colorId, m.blockId);
            }
         }
      }

      return map;
   }

   /** A filesystem-safe file name: letters, digits, spaces, dashes and underscores only, capped to a sane length. */
   public static String safeFileName(String name, String fallback) {
      String base = name == null ? "" : name.trim();
      String cleaned = base.replaceAll("[^A-Za-z0-9 _-]", "").trim();
      if (cleaned.isEmpty()) {
         cleaned = fallback;
      }

      return cleaned.length() > 48 ? cleaned.substring(0, 48).trim() : cleaned;
   }
}
