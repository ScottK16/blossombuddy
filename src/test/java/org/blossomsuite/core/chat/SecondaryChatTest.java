package org.blossomsuite.core.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import net.minecraft.text.Text;
import org.blossomsuite.core.config.FeatureConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SecondaryChatTest {
   private static FeatureConfig.Filter filter(String name, String pattern, boolean regex, boolean hide) {
      FeatureConfig.Filter f = new FeatureConfig.Filter(name, pattern);
      f.regex = regex;
      f.hideFromMain = hide;
      return f;
   }

   @BeforeEach
   void freshFilters() {
      FeatureConfig.INSTANCE.chat.filters = new ArrayList<>();
      FeatureConfig.INSTANCE.chat.selected = -1;
      FeatureConfig.INSTANCE.chat.show = true;
   }

   @Test
   void nothingIsHiddenFromMainChatWhileTheSecondaryWindowIsOff() {
      FeatureConfig.INSTANCE.chat.filters.add(filter("Hide", "beta", false, true));
      FeatureConfig.INSTANCE.chat.show = false;
      SecondaryChat chat = new SecondaryChat();
      assertFalse(chat.onMessage(Text.literal("beta only")), "no window to read it in, so leave it in main chat");
      assertEquals(1, chat.recent("Hide", 10).size(), "it is still filed, for /buddy chat");
   }

   @Test
   void plainFiltersMatchAnyWordCaseInsensitively() {
      FeatureConfig.Filter f = filter("Marry", "marry|divorce", false, false);
      assertTrue(SecondaryChat.matches(f, "Steve wants to MARRY you"));
      assertTrue(SecondaryChat.matches(f, "They got a divorce"));
      assertFalse(SecondaryChat.matches(f, "Just chatting"));
   }

   @Test
   void blankPatternsAndBlankWordsNeverMatch() {
      assertFalse(SecondaryChat.matches(filter("x", "", false, false), "anything"));
      assertFalse(SecondaryChat.matches(filter("x", "  ", false, false), "anything"));
      assertFalse(SecondaryChat.matches(filter("x", "|", false, false), "anything"), "empty words between | must not match everything");
   }

   @Test
   void regexFiltersWorkAndBadRegexIsIgnored() {
      FeatureConfig.Filter f = filter("Whisper", "^\\[.* -> me\\]", true, false);
      assertTrue(SecondaryChat.matches(f, "[Alex -> me] hi"));
      assertFalse(SecondaryChat.matches(f, "Alex said hi"));
      assertFalse(SecondaryChat.matches(filter("bad", "([", true, false), "anything"), "an invalid regex must not throw");
   }

   @Test
   void aLineIsFiledUnderEveryFilterItMatches() {
      FeatureConfig.INSTANCE.chat.filters.add(filter("TP", "teleport", false, false));
      FeatureConfig.INSTANCE.chat.filters.add(filter("Requests", "request", false, false));
      SecondaryChat chat = new SecondaryChat();
      chat.onMessage(Text.literal("Alex sent you a teleport request"));
      assertEquals(1, chat.recent("TP", 10).size());
      assertEquals(1, chat.recent("Requests", 10).size());
      assertEquals(2, chat.recent(null, 10).size(), "'all' lists both copies");
   }

   @Test
   void onlyFiltersThatAskToHideRemoveTheLineFromMainChat() {
      FeatureConfig.INSTANCE.chat.filters.add(filter("Keep", "alpha", false, false));
      FeatureConfig.INSTANCE.chat.filters.add(filter("Hide", "beta", false, true));
      SecondaryChat chat = new SecondaryChat();
      assertFalse(chat.onMessage(Text.literal("alpha only")));
      assertTrue(chat.onMessage(Text.literal("beta only")));
      assertFalse(chat.onMessage(Text.literal("unrelated")));
   }

   @Test
   void disabledFiltersAreSkipped() {
      FeatureConfig.Filter f = filter("Off", "anything", false, true);
      f.enabled = false;
      FeatureConfig.INSTANCE.chat.filters.add(f);
      SecondaryChat chat = new SecondaryChat();
      assertFalse(chat.onMessage(Text.literal("anything at all")));
      assertTrue(chat.recent(null, 10).isEmpty());
   }

   @Test
   void recentReturnsTheNewestLinesOldestFirst() {
      FeatureConfig.INSTANCE.chat.filters.add(filter("All", "msg", false, false));
      SecondaryChat chat = new SecondaryChat();
      for (int i = 1; i <= 5; i++) {
         chat.onMessage(Text.literal("msg " + i));
      }

      var last3 = chat.recent(null, 3);
      assertEquals(3, last3.size());
      assertEquals("msg 3", last3.get(0).text().getString());
      assertEquals("msg 5", last3.get(2).text().getString());
   }

   @Test
   void cyclingVisitsEachFilterThenAll() {
      FeatureConfig.INSTANCE.chat.filters.add(filter("A", "a", false, false));
      FeatureConfig.INSTANCE.chat.filters.add(filter("B", "b", false, false));
      SecondaryChat chat = new SecondaryChat();
      assertEquals("A", chat.cycleFilter());
      assertEquals("B", chat.cycleFilter());
      assertEquals("All", chat.cycleFilter());
      assertEquals("A", chat.cycleFilter());
   }
}
