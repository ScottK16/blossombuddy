package org.blossomsuite.core.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.text.Text;
import org.blossomsuite.core.config.FeatureConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Reading back through the secondary chat: what a scrolled window shows, and that it stays put while new lines arrive. */
class SecondaryChatScrollTest {
   @BeforeEach
   void reset() {
      FeatureConfig.INSTANCE.chat.filters = new FeatureConfig.Chat().filters;
      FeatureConfig.INSTANCE.chat.show = true;
      FeatureConfig.INSTANCE.chat.selected = -1;
   }

   private static SecondaryChat withLines(int n) {
      SecondaryChat chat = new SecondaryChat();
      for (int i = 1; i <= n; i++) {
         chat.addExternal(Text.literal("line " + i));
      }

      return chat;
   }

   private static String texts(List<SecondaryChat.Line> lines) {
      return lines.stream().map(l -> l.text().getString()).collect(Collectors.joining(","));
   }

   @Test
   void aWindowEndsAtTheLineYouScrolledBackTo() {
      SecondaryChat chat = withLines(10);
      assertEquals("line 8,line 9,line 10", texts(chat.window(null, 0, 3)), "following the newest");
      assertEquals("line 6,line 7,line 8", texts(chat.window(null, 2, 3)), "two lines back");
      assertEquals("line 1,line 2", texts(chat.window(null, 8, 5)), "near the start only what is left");
      assertEquals("", texts(chat.window(null, 10, 3)));
   }

   @Test
   void scrollingIsHeldBetweenTheOldestAndTheNewestLine() {
      SecondaryChat chat = withLines(5);
      assertFalse(chat.scrollBy(-3), "already at the newest");
      assertTrue(chat.scrollBy(2));
      assertEquals(2, chat.scroll());
      assertTrue(chat.scrollBy(100));
      assertEquals(4, chat.scroll(), "never past the oldest line");
      assertTrue(chat.scrollBy(-100));
      assertEquals(0, chat.scroll());
   }

   @Test
   void theViewStaysWhereYouLeftItWhenNewLinesArrive() {
      SecondaryChat chat = withLines(10);
      chat.scrollBy(3);
      String before = texts(chat.window(null, chat.scroll(), 3));

      chat.addExternal(Text.literal("line 11"));
      chat.addExternal(Text.literal("line 12"));

      assertEquals(5, chat.scroll(), "moved back by the number of new lines");
      assertEquals(before, texts(chat.window(null, chat.scroll(), 3)), "so you are still reading the same lines");
   }

   @Test
   void aFollowingReaderKeepsFollowing() {
      SecondaryChat chat = withLines(3);
      chat.addExternal(Text.literal("line 4"));
      assertEquals(0, chat.scroll());
   }

   @Test
   void linesFromAnotherTabDoNotMoveTheViewOfThisTab() {
      SecondaryChat chat = withLines(10); // all under Realms
      FeatureConfig.INSTANCE.chat.selected = indexOf(SecondaryChat.REALMS);
      chat.scrollBy(3);
      assertEquals(3, chat.scroll());

      chat.onMessage(Text.literal("TELEPORT ~Marv has requested to teleport to you."));
      assertEquals(1, chat.count("Teleports"), "the line really was filed under another tab");
      assertEquals(3, chat.scroll(), "a teleport line is filed under Teleports, not the Realms tab being read");
   }

   @Test
   void changingTabAndClearingGoBackToTheNewest() {
      SecondaryChat chat = withLines(10);
      chat.scrollBy(4);
      chat.cycleFilter();
      assertEquals(0, chat.scroll());

      chat.scrollBy(2);
      chat.clear();
      assertEquals(0, chat.scroll());
   }

   @Test
   void theCountIsPerTab() {
      SecondaryChat chat = withLines(4);
      assertEquals(4, chat.count(null));
      assertEquals(4, chat.count(SecondaryChat.REALMS));
      assertEquals(0, chat.count("Teleports"));
   }

   private static int indexOf(String name) {
      List<FeatureConfig.Filter> filters = FeatureConfig.INSTANCE.chat.filters;
      for (int i = 0; i < filters.size(); i++) {
         if (name.equalsIgnoreCase(filters.get(i).name)) {
            return i;
         }
      }

      throw new IllegalStateException(name);
   }
}
