package org.blossomsuite.core.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.blossomsuite.core.config.FeatureConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProgressTest {
   @BeforeEach
   void reset() {
      FeatureConfig.INSTANCE.progress = new FeatureConfig.Progress();
   }

   @Test
   void timeAndDungeonRunsAreCounted() {
      Progress.addPlayMs(90_000L);
      Progress.addPlayMs(-5L);
      Progress.addPlayMs(30_000L);
      Progress.dungeon(true);
      Progress.dungeon(true);
      Progress.dungeon(false);

      Map<String, Object> out = Progress.snapshot();
      assertEquals(2L, out.get("minutes"));
      assertEquals(2, out.get("done"));
      assertEquals(1, out.get("failed"));
   }

   @Test
   void eachFirstIsRecordedOnceAndUnknownOnesAreIgnored() {
      Progress.first("emote");
      Progress.first("emote");
      Progress.first("find");
      Progress.first("made-up");
      assertEquals(List.of("emote", "find"), Progress.snapshot().get("firsts"));
   }

   @Test
   void everyFirstTheModKnowsIsOneTheRelayKnows() throws Exception {
      // relay/progress.js keeps the same list; this guards against the two drifting apart
      java.nio.file.Path js = java.nio.file.Path.of("relay", "progress.js");
      if (java.nio.file.Files.isRegularFile(js)) {
         String text = java.nio.file.Files.readString(js);
         for (String id : Progress.FIRSTS) {
            assertTrue(text.contains("'" + id + "'"), id);
         }
      }
   }
}
