package org.blossomsuite.core.mapart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MapArtFileStoreTest {
   private Path dir;

   private static final String PROJECT_JSON =
      "{\"name\":\"My House\",\"width\":2,\"height\":2,\"blocks\":[1,2,3,29],"
         + "\"materials\":[{\"colorId\":1,\"block\":\"Grass Block\",\"count\":1},{\"colorId\":29,\"block\":\"Black Wool\",\"count\":1}],"
         + "\"thumbnail\":\"data:image/png;base64,AA==\"}";

   @BeforeEach
   void makeTempDir() throws IOException {
      this.dir = Files.createTempDirectory("blossombuddy-mapart-test");
   }

   @AfterEach
   void cleanUp() throws IOException {
      try (var stream = Files.list(this.dir)) {
         for (Path p : stream.toList()) {
            Files.deleteIfExists(p);
         }
      }
      Files.deleteIfExists(this.dir);
   }

   @Test
   void anEmptyFolderListsNothing() {
      assertTrue(MapArtFileStore.list(this.dir).isEmpty());
   }

   @Test
   void aMissingFolderListsNothingRatherThanThrowing() {
      assertTrue(MapArtFileStore.list(this.dir.resolve("does-not-exist")).isEmpty());
   }

   @Test
   void onlyDotJsonFilesAreListedAndTheExtensionIsStripped() throws IOException {
      Files.writeString(this.dir.resolve("a design.json"), PROJECT_JSON);
      Files.writeString(this.dir.resolve("notes.txt"), "not a design");
      List<String> names = MapArtFileStore.list(this.dir);
      assertEquals(List.of("a design"), names);
   }

   @Test
   void newestFileComesFirst() throws IOException {
      Path older = this.dir.resolve("older.json");
      Path newer = this.dir.resolve("newer.json");
      Files.writeString(older, PROJECT_JSON);
      Files.writeString(newer, PROJECT_JSON);
      Files.setLastModifiedTime(older, FileTime.fromMillis(1000));
      Files.setLastModifiedTime(newer, FileTime.fromMillis(2000));

      assertEquals(List.of("newer", "older"), MapArtFileStore.list(this.dir));
   }

   @Test
   void aValidFileLoadsWithEverythingTheScreenNeeds() throws IOException {
      Files.writeString(this.dir.resolve("a design.json"), PROJECT_JSON);
      MapArtFileStore.Result result = MapArtFileStore.load(this.dir, "a design");
      assertTrue(result.ok(), result.error());
      assertEquals("My House", result.project().name);
      assertEquals(2, result.project().width);
      assertEquals(4, result.project().blocks.length);
      assertEquals(2, result.project().materials.size());
      assertEquals("data:image/png;base64,AA==", result.project().thumbnail);
   }

   @Test
   void aMissingFileGetsAFriendlyMessageNotAStackTrace() {
      MapArtFileStore.Result result = MapArtFileStore.load(this.dir, "nope");
      assertFalse(result.ok());
      assertEquals("Could not read that file.", result.error());
   }

   @Test
   void garbageJsonIsTreatedAsAFailureNotACrash() throws IOException {
      Files.writeString(this.dir.resolve("broken.json"), "not json");
      MapArtFileStore.Result result = MapArtFileStore.load(this.dir, "broken");
      assertFalse(result.ok());
      assertEquals("That file isn't a valid map art design.", result.error());
   }

   @Test
   void aFileMissingItsBlockGridIsTreatedAsInvalidNotShownHalfBroken() throws IOException {
      Files.writeString(this.dir.resolve("incomplete.json"), "{\"width\":2,\"height\":2}"); // no "blocks" at all
      MapArtFileStore.Result result = MapArtFileStore.load(this.dir, "incomplete");
      assertFalse(result.ok());
      assertEquals("That file isn't a valid map art design.", result.error());
   }
}
