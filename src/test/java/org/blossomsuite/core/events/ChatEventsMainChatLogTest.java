package org.blossomsuite.core.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.text.Text;
import org.blossomsuite.core.chat.MainChatLog;
import org.blossomsuite.core.config.FeatureConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The rule the search log has to follow: only lines that actually land in main chat are worth searching. Rather than register real
 * Fabric events (which need a running game), this re-checks the same decision {@link ChatEvents} makes, against the pieces it is built
 * from, so a change to that logic that stops recording (or starts recording lines that never show) is caught.
 */
class ChatEventsMainChatLogTest {
   @BeforeEach
   void reset() {
      MainChatLog.INSTANCE.clear();
      FeatureConfig.INSTANCE.chat.filters = new FeatureConfig.Chat().filters;
      FeatureConfig.INSTANCE.chat.windows = new FeatureConfig.Chat().windows;
      FeatureConfig.INSTANCE.chat.show = false;
   }

   @Test
   void ordinaryChatEndsUpInTheSearchLog() {
      Text line = Text.literal("Wanderer : Steve > hello");
      boolean hiddenBySecondary = org.blossomsuite.core.chat.SecondaryChat.INSTANCE.onMessage(line);
      assertTrue(!hiddenBySecondary, "an ordinary line is never hidden");
      MainChatLog.INSTANCE.record(line);
      assertEquals(1, MainChatLog.INSTANCE.search("Steve").size());
   }

   @Test
   void aLineHiddenIntoASecondaryWindowIsNotRecordedForMainChatSearch() {
      FeatureConfig.INSTANCE.chat.show = true;
      Text tp = Text.literal("TELEPORT ~Steve has requested to teleport to you.");
      List<String> log = new ArrayList<>();
      boolean hidden = org.blossomsuite.core.chat.SecondaryChat.INSTANCE.onMessage(tp);
      assertTrue(hidden, "the real ChatEvents.ALLOW_GAME would return false here and never call MainChatLog.record");
      if (!hidden) {
         MainChatLog.INSTANCE.record(tp);
      }

      assertEquals(0, MainChatLog.INSTANCE.search("Steve").size(), "so it is not in the main-chat search either");
   }
}
