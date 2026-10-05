package org.blossomsuite.core.mapart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import org.junit.jupiter.api.Test;

class SchematicWriterTest {
   private static Map<Integer, String> palette() {
      return Map.of(1, "minecraft:grass_block", 2, "minecraft:sand", 3, "minecraft:white_wool");
   }

   @Test
   void theHeaderDescribesAOneBlockTallStructureOfTheRightFootprint() {
      CompoundTag nbt = SchematicWriter.build(2, 3, new int[]{1, 1, 1, 1, 1, 1}, palette());
      assertEquals(SchematicWriter.DATA_VERSION, nbt.getIntOr("DataVersion", -1));
      ListTag size = nbt.getListOrEmpty("size");
      assertEquals(2, size.getIntOr(0, -1), "width");
      assertEquals(1, size.getIntOr(1, -1), "map art is always one block tall");
      assertEquals(3, size.getIntOr(2, -1), "height, stored as the Z size");
   }

   @Test
   void everyCellBecomesExactlyOneBlockEntryAtY0() {
      int w = 4;
      int h = 3;
      int[] blocks = new int[w * h];
      java.util.Arrays.fill(blocks, 1);
      CompoundTag nbt = SchematicWriter.build(w, h, blocks, palette());
      ListTag blockList = nbt.getListOrEmpty("blocks");
      assertEquals(w * h, blockList.size());
      for (int i = 0; i < blockList.size(); i++) {
         CompoundTag entry = blockList.getCompoundOrEmpty(i);
         assertEquals(0, entry.getListOrEmpty("pos").getIntOr(1, -1), "Y is always 0");
      }
   }

   @Test
   void repeatedColoursShareOnePaletteEntryInsteadOfOnePerBlock() {
      int[] blocks = {1, 1, 1, 1, 1, 1, 1, 1, 1}; // 3x3, all grass
      CompoundTag nbt = SchematicWriter.build(3, 3, blocks, palette());
      ListTag palette = nbt.getListOrEmpty("palette");
      assertEquals(1, palette.size());
      assertEquals("minecraft:grass_block", palette.getCompoundOrEmpty(0).getStringOr("Name", ""));
   }

   @Test
   void differentColoursEachGetTheirOwnPaletteEntryAndTheRightIndexPerCell() {
      // a 2x1 strip: grass then sand
      CompoundTag nbt = SchematicWriter.build(2, 1, new int[]{1, 2}, palette());
      ListTag palette = nbt.getListOrEmpty("palette");
      assertEquals(2, palette.size());

      ListTag blockList = nbt.getListOrEmpty("blocks");
      int grassState = blockList.getCompoundOrEmpty(0).getIntOr("state", -1);
      int sandState = blockList.getCompoundOrEmpty(1).getIntOr("state", -1);
      assertEquals("minecraft:grass_block", palette.getCompoundOrEmpty(grassState).getStringOr("Name", ""));
      assertEquals("minecraft:sand", palette.getCompoundOrEmpty(sandState).getStringOr("Name", ""));
   }

   @Test
   void aColourIdMissingFromThePaletteMapFallsBackToWhiteWoolRatherThanFailing() {
      CompoundTag nbt = SchematicWriter.build(1, 1, new int[]{999}, palette());
      ListTag palette = nbt.getListOrEmpty("palette");
      assertEquals("minecraft:white_wool", palette.getCompoundOrEmpty(0).getStringOr("Name", ""));
   }

   @Test
   void badDimensionsAreRefusedRatherThanBuildingSomethingCorrupt() {
      assertThrows(IllegalArgumentException.class, () -> SchematicWriter.build(0, 1, new int[0], palette()));
      assertThrows(IllegalArgumentException.class, () -> SchematicWriter.build(2, 2, new int[]{1, 1, 1}, palette()));
   }

   @Test
   void theRealNbtWriterCanActuallyWriteAndReadThisBackGzippedAndAllNotJustBuildItInMemory() throws IOException {
      CompoundTag nbt = SchematicWriter.build(2, 2, new int[]{1, 2, 3, 1}, palette());
      Path tmp = Files.createTempFile("blossombuddy-schematic-test", ".nbt");
      try {
         NbtIo.writeCompressed(nbt, tmp);
         assertTrue(Files.size(tmp) > 0);
         CompoundTag readBack = NbtIo.readCompressed(tmp, NbtAccounter.unlimitedHeap());
         assertEquals(SchematicWriter.DATA_VERSION, readBack.getIntOr("DataVersion", -1));
         assertEquals(4, readBack.getListOrEmpty("blocks").size());
         assertEquals(3, readBack.getListOrEmpty("palette").size());
      } finally {
         Files.deleteIfExists(tmp);
      }
   }

   @Test
   void paletteOfBuildsTheLookupFromAProjectsMaterialsAndSkipsAnyWithNoRealBlockId() {
      MapArtModels.Material withId = new MapArtModels.Material();
      withId.colorId = 1;
      withId.blockId = "minecraft:grass_block";

      MapArtModels.Material blank = new MapArtModels.Material();
      blank.colorId = 2;
      blank.blockId = "";

      MapArtModels.Material missing = new MapArtModels.Material();
      missing.colorId = 3;

      Map<Integer, String> map = SchematicWriter.paletteOf(List.of(withId, blank, missing));
      assertEquals(Map.of(1, "minecraft:grass_block"), map);
   }

   @Test
   void safeFileNameStripsAnythingThatIsNotLetterDigitSpaceDashOrUnderscore() {
      assertEquals("My House", SchematicWriter.safeFileName("My House", "fallback"));
      assertEquals("weirdstuff", SchematicWriter.safeFileName("weird/../stuff:*?", "fallback"), "path-breaking characters are just dropped, not replaced with a space");
      assertEquals("fallback", SchematicWriter.safeFileName("", "fallback"));
      assertEquals("fallback", SchematicWriter.safeFileName(null, "fallback"));
      assertEquals("fallback", SchematicWriter.safeFileName("///***", "fallback"));
      assertTrue(SchematicWriter.safeFileName("x".repeat(100), "fallback").length() <= 48);
   }
}
