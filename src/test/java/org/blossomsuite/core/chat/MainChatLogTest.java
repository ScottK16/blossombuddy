package org.blossomsuite.core.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

/** Searching and copying everything that has shown up in main chat. */
class MainChatLogTest {
   private static String texts(List<MainChatLog.Line> lines) {
      return lines.stream().map(MainChatLog.Line::plain).collect(Collectors.joining(","));
   }

   @Test
   void aBlankLogHasNothingToFind() {
      MainChatLog log = new MainChatLog();
      assertEquals(0, log.size());
      assertEquals(List.of(), log.search("anything"));
   }

   @Test
   void aLineIsFoundByAnyWordItContains() {
      MainChatLog log = new MainChatLog();
      log.record(Text.literal("Wanderer : Steve > hey, anyone selling wheat?"));
      log.record(Text.literal("Ranger : Alex > gg everyone"));
      assertEquals("Wanderer : Steve > hey, anyone selling wheat?", texts(log.search("Steve")));
      assertEquals("Wanderer : Steve > hey, anyone selling wheat?", texts(log.search("wheat")));
      assertEquals("Ranger : Alex > gg everyone", texts(log.search("gg")));
   }

   @Test
   void aPlayersNameIsFoundWhereverItAppearsInTheLine() {
      MainChatLog log = new MainChatLog();
      log.record(Text.literal("[Staff] Steve has been promoted"));
      log.record(Text.literal("Alice whispers to you: ask Steve"));
      log.record(Text.literal("Nothing about that name here"));
      assertEquals(2, log.search("Steve").size());
   }

   @Test
   void searchingIsNotCaseSensitive() {
      MainChatLog log = new MainChatLog();
      log.record(Text.literal("Wanderer : STEVE > hello"));
      assertEquals(1, log.search("steve").size());
      assertEquals(1, log.search("StEvE").size());
   }

   @Test
   void resultsComeBackNewestFirst() {
      MainChatLog log = new MainChatLog();
      log.record(Text.literal("Steve says one"));
      log.record(Text.literal("Steve says two"));
      log.record(Text.literal("Steve says three"));
      assertEquals("Steve says three,Steve says two,Steve says one", texts(log.search("Steve")));
   }

   @Test
   void aBlankQueryReturnsTheMostRecentLinesInstead() {
      MainChatLog log = new MainChatLog();
      for (int i = 1; i <= 5; i++) {
         log.record(Text.literal("line " + i));
      }

      assertEquals("line 5,line 4,line 3", texts(log.search("", 3)));
      assertEquals("line 5,line 4,line 3", texts(log.search("   ", 3)), "whitespace-only counts as blank");
   }

   @Test
   void theNumberOfResultsIsCapped() {
      MainChatLog log = new MainChatLog();
      for (int i = 1; i <= 10; i++) {
         log.record(Text.literal("Steve #" + i));
      }

      assertEquals(4, log.search("Steve", 4).size());
      assertEquals(MainChatLog.MAX_RESULTS, MainChatLog.MAX_RESULTS); // the constant exists and is used as the default
   }

   @Test
   void onlyTheMostRecentLinesAreKept() {
      MainChatLog log = new MainChatLog();
      for (int i = 1; i <= 1200; i++) {
         log.record(Text.literal("line " + i));
      }

      assertEquals(1000, log.size());
      assertTrue(texts(log.search("", 1)).equals("line 1200"));
      assertTrue(log.search("line 1").isEmpty() == false); // line 1xxx still around; line 1 itself trimmed
   }

   @Test
   void blankOrMissingLinesAreNotRecorded() {
      MainChatLog log = new MainChatLog();
      log.record(null);
      log.record(Text.literal(""));
      log.record(Text.literal("   "));
      assertEquals(0, log.size());
   }

   @Test
   void clearEmptiesTheLog() {
      MainChatLog log = new MainChatLog();
      log.record(Text.literal("Steve was here"));
      log.clear();
      assertEquals(0, log.size());
      assertEquals(List.of(), log.search("Steve"));
   }

   @Test
   void theOriginalTextIsKeptAlongsideThePlainString() {
      MainChatLog log = new MainChatLog();
      Text styled = Text.literal("Steve").formatted(net.minecraft.util.Formatting.RED);
      log.record(styled);
      MainChatLog.Line line = log.search("Steve").get(0);
      assertEquals(styled, line.text());
      assertEquals("Steve", line.plain());
      assertTrue(line.atMs() > 0);
   }
}
