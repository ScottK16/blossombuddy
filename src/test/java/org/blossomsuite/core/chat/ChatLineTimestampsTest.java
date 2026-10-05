package org.blossomsuite.core.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** When each visible chat line was really created, keyed by the object itself (not its text). */
class ChatLineTimestampsTest {
   private static GuiMessage.Line line(String text) {
      FormattedCharSequence ordered = FormattedCharSequence.forward(text, net.minecraft.network.chat.Style.EMPTY);
      return new GuiMessage.Line(null, ordered, true);
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
      GuiMessage.Line a = line("hello");
      ChatLineTimestamps.record(a, 1000L);
      assertEquals(1000L, ChatLineTimestamps.timeOf(a));
   }

   @Test
   void twoDifferentLinesWithTheSameTextAreKeptSeparate() {
      GuiMessage.Line a = line("Steve says hi");
      GuiMessage.Line b = line("Steve says hi"); // identical content, a different object (a different message)
      ChatLineTimestamps.record(a, 1000L);
      ChatLineTimestamps.record(b, 2000L);
      assertEquals(1000L, ChatLineTimestamps.timeOf(a), "each object keeps its own time, not merged by matching text");
      assertEquals(2000L, ChatLineTimestamps.timeOf(b));
   }

   @Test
   void recordingTheSameLineTwiceKeepsTheFirstTime() {
      GuiMessage.Line a = line("hello");
      ChatLineTimestamps.record(a, 1000L);
      ChatLineTimestamps.record(a, 5000L);
      assertEquals(1000L, ChatLineTimestamps.timeOf(a));
   }

   @Test
   void oldLinesAreForgottenOnceTheStoreIsFull() {
      GuiMessage.Line first = line("line 0");
      ChatLineTimestamps.record(first, 0L);
      for (int i = 1; i < 500; i++) {
         ChatLineTimestamps.record(line("line " + i), i);
      }

      assertNull(ChatLineTimestamps.timeOf(first), "aged out once the store is well past vanilla's own 100-line history");
      assertTrue(ChatLineTimestamps.size() <= 400);
   }

   @Test
   void clearForgetsEverything() {
      GuiMessage.Line a = line("hello");
      ChatLineTimestamps.record(a, 1000L);
      ChatLineTimestamps.clear();
      assertNull(ChatLineTimestamps.timeOf(a));
      assertEquals(0, ChatLineTimestamps.size());
   }
}
