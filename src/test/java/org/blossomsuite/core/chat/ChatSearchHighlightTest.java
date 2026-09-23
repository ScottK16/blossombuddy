package org.blossomsuite.core.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChatSearchHighlightTest {
   @Test
   void theMatchIsSplitOutWhereverItAppears() {
      ChatSearchHighlight.Parts p = ChatSearchHighlight.split("Wanderer : Steve > hi", "Steve");
      assertEquals("Wanderer : ", p.before());
      assertEquals("Steve", p.match());
      assertEquals(" > hi", p.after());
   }

   @Test
   void theMatchIsCaseInsensitiveButKeepsTheLinesOwnCasing() {
      ChatSearchHighlight.Parts p = ChatSearchHighlight.split("Wanderer : STEVE > hi", "steve");
      assertEquals("STEVE", p.match(), "the original casing from the line, not the search term's");
   }

   @Test
   void onlyTheFirstMatchIsSplitOut() {
      ChatSearchHighlight.Parts p = ChatSearchHighlight.split("Steve says hi to Steve", "Steve");
      assertEquals("", p.before());
      assertEquals("Steve", p.match());
      assertEquals(" says hi to Steve", p.after(), "the second occurrence stays in 'after' unsplit");
   }

   @Test
   void aBlankQueryOrNoMatchLeavesTheWholeLineInBefore() {
      assertEquals("hello", ChatSearchHighlight.split("hello", "").before());
      assertEquals("", ChatSearchHighlight.split("hello", "").match());
      assertEquals("hello", ChatSearchHighlight.split("hello", null).before());
      assertEquals("hello", ChatSearchHighlight.split("hello", "zzz").before());
      assertEquals("", ChatSearchHighlight.split("hello", "zzz").match());
   }

   @Test
   void aNullLineIsTreatedAsEmpty() {
      ChatSearchHighlight.Parts p = ChatSearchHighlight.split(null, "x");
      assertEquals("", p.before());
      assertEquals("", p.match());
   }
}
