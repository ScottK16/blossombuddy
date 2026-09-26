package org.blossomsuite.core.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.text.Text;
import org.blossomsuite.core.config.FeatureConfig;
import org.junit.jupiter.api.Test;

class SecondaryChatVersionTest {
   @Test
   void theVersionMovesOnWhenALineArrivesOrTheListIsClearedAndNotOtherwise() {
      SecondaryChat chat = new SecondaryChat();
      long start = chat.version();
      assertEquals(start, chat.version(), "looking doesn't change it");
      chat.addExternal(Text.literal("hello"));
      long afterAdd = chat.version();
      assertTrue(afterAdd > start);
      chat.clear();
      assertTrue(chat.version() > afterAdd);
   }

   @Test
   void aRegexFilterKeepsWorkingOnEveryLineNowThatItIsCompiledOnce() {
      FeatureConfig.Filter f = new FeatureConfig.Filter();
      f.regex = true;
      f.pattern = "^\\[Cherry\\] \\w+: hi";
      for (int i = 0; i < 5; i++) {
         assertTrue(SecondaryChat.matches(f, "[Cherry] Alice: hi there"));
         assertFalse(SecondaryChat.matches(f, "[Tulip] Alice: hi there"));
      }

      f.pattern = "([";
      assertFalse(SecondaryChat.matches(f, "anything"));
      assertFalse(SecondaryChat.matches(f, "anything"), "and stays false when asked again");
   }
}
