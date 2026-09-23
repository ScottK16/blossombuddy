package org.blossomsuite.core.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.OrderedText;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** When each visible chat line was really created, keyed by the object itself (not its text). */
class ChatLineTimestampsTest {
   private static ChatHudLine.Visible line(String text) {
      OrderedText ordered = OrderedText.styledForwardsVisitedString(text, net.minecraft.text.Style.EMPTY);
      return new ChatHudLine.Visible(0, ordered, null, true);
   }

   @BeforeEach
   void reset() {
      ChatLineTimestamps.clear();
   }

   @Test
   void anUnknownLineHasNoRecordedTime() {
      assertNull(ChatLineTimestamps.timeOf(line("hello")));
      assertNull(ChatLineTimestamps.timeOf(null));
   }

   @Test
   void aRecordedLineReturnsExactlyWhenItWasRecorded() {
      ChatHudLine.Visible a = line("hello");
      ChatLineTimestamps.record(a, 1000L);
      assertEquals(1000L, ChatLineTimestamps.timeOf(a));
   }

   @Test
   void twoDifferentLinesWithTheSameTextAreKeptSeparate() {
      ChatHudLine.Visible a = line("Steve says hi");
      ChatHudLine.Visible b = line("Steve says hi"); // identical content, a different object (a different message)
      ChatLineTimestamps.record(a, 1000L);
      ChatLineTimestamps.record(b, 2000L);
      assertEquals(1000L, ChatLineTimestamps.timeOf(a), "each object keeps its own time, not merged by matching text");
      assertEquals(2000L, ChatLineTimestamps.timeOf(b));
   }

   @Test
   void recordingTheSameLineTwiceKeepsTheFirstTime() {
      ChatHudLine.Visible a = line("hello");
      ChatLineTimestamps.record(a, 1000L);
      ChatLineTimestamps.record(a, 5000L);
      assertEquals(1000L, ChatLineTimestamps.timeOf(a));
   }

   @Test
   void oldLinesAreForgottenOnceTheStoreIsFull() {
      ChatHudLine.Visible first = line("line 0");
      ChatLineTimestamps.record(first, 0L);
      for (int i = 1; i < 500; i++) {
         ChatLineTimestamps.record(line("line " + i), i);
      }

      assertNull(ChatLineTimestamps.timeOf(first), "aged out once the store is well past vanilla's own 100-line history");
      assertTrue(ChatLineTimestamps.size() <= 400);
   }

   @Test
   void clearForgetsEverything() {
      ChatHudLine.Visible a = line("hello");
      ChatLineTimestamps.record(a, 1000L);
      ChatLineTimestamps.clear();
      assertNull(ChatLineTimestamps.timeOf(a));
      assertEquals(0, ChatLineTimestamps.size());
   }
}
