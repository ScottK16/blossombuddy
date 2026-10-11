package org.blossomsuite.core.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.blossomsuite.core.config.FeatureConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatNotifyTest {
   static final class FakeEnv implements ChatNotify.Env {
      boolean enabled = true;
      List<String> words = List.of("Claude");
      boolean mentionName = true;
      String self = "Alice";
      long now = 1_000_000L;
      int dings = 0;

      @Override public boolean enabled() { return this.enabled; }
      @Override public List<String> words() { return this.words; }
      @Override public boolean mentionName() { return this.mentionName; }
      @Override public String selfName() { return this.self; }
      @Override public long now() { return this.now; }
      @Override public boolean isOurOwnLine(String text) { return text.contains("[BLOSSOMBUDDY]"); }
      @Override public void ding() { this.dings++; }
   }

   private FakeEnv env;
   private ChatNotify notify;

   @BeforeEach
   void setUp() {
      this.env = new FakeEnv();
      this.notify = new ChatNotify(this.env);
   }

   @Test
   void aKeywordInAnyoneElsesChatDings() {
      this.notify.onLine("[Member] Bob: does anyone know Claude?");
      assertEquals(1, this.env.dings);
   }

   @Test
   void itMatchesWholeWordsInAnyCaseOnly() {
      this.notify.onLine("Bob: CLAUDE is here");
      assertEquals(1, this.env.dings);
      this.env.now += 10_000L;
      this.notify.onLine("Bob: Claudette is here");
      this.notify.onLine("Bob: superclaude is here");
      assertEquals(1, this.env.dings, "part of another word is not a match");
   }

   @Test
   void aPhraseWorksToo() {
      this.env.words = List.of("diamond key");
      this.notify.onLine("Server: Bob found a Diamond Key!");
      assertEquals(1, this.env.dings);
   }

   @Test
   void yourOwnMessagesNeverDing() {
      this.notify.onLine("[Member] Alice: I said Claude");
      this.notify.onLine("Alice » Claude is great");
      this.notify.onLine("<Alice> Claude");
      assertEquals(0, this.env.dings);
   }

   @Test
   void aBroadcastWithNoSpeakerStillDings() {
      this.notify.onLine("Claude has joined the server");
      assertEquals(1, this.env.dings);
   }

   @Test
   void signedPlayerChatSkipsYourOwnNameAndRingsForOthers() {
      this.notify.onPlayerChat("hello Claude", "Alice");
      assertEquals(0, this.env.dings);
      this.notify.onPlayerChat("hello Claude", "Bob");
      assertEquals(1, this.env.dings);
   }

   @Test
   void yourNameDingsWhenSomeoneElseSaysItButNotWhenYouAreTheSpeaker() {
      this.env.words = List.of();
      this.notify.onLine("[Member] Bob: hey Alice, got a minute?");
      assertEquals(1, this.env.dings);
      this.env.now += 10_000L;
      this.notify.onLine("[Member] Alice: hi me");
      this.notify.onLine("Alice joined the game");
      assertEquals(1, this.env.dings, "your own name as speaker, or in a join line, is not a mention");
      this.env.mentionName = false;
      this.notify.onLine("[Member] Bob: hey Alice again");
      assertEquals(1, this.env.dings);
   }

   @Test
   void itRingsAtMostOnceEveryMoment() {
      this.notify.onLine("Bob: Claude");
      this.env.now += 500L;
      this.notify.onLine("Bob: Claude again");
      assertEquals(1, this.env.dings);
      this.env.now += ChatNotify.DEBOUNCE_MS;
      this.notify.onLine("Bob: Claude once more");
      assertEquals(2, this.env.dings);
   }

   @Test
   void nothingHappensWhenOffOrOnTheModsOwnLines() {
      this.env.enabled = false;
      this.notify.onLine("Bob: Claude");
      this.env.enabled = true;
      this.notify.onLine("[BLOSSOMBUDDY] Keywords: Claude");
      this.notify.onLine("");
      this.notify.onLine(null);
      assertEquals(0, this.env.dings);
   }

   @Test
   void theKeywordListIsReadCleanly() {
      FeatureConfig.ChatNotify cfg = new FeatureConfig.ChatNotify();
      cfg.keywords = " Claude | diamond key,, claude |";
      assertEquals(List.of("Claude", "diamond key"), cfg.words());
      cfg.setWords(List.of("a", "b"));
      assertEquals("a|b", cfg.keywords);
      cfg.keywords = null;
      assertTrue(cfg.words().isEmpty());
      assertFalse(cfg.enabled, "off by default");
   }

   @Test
   void firstMatchReportsTheWordThatHit() {
      assertEquals("b", ChatNotify.firstMatch("say b now", List.of("a", "b")));
      assertNull(ChatNotify.firstMatch("nothing", List.of("a", "b")));
   }
}
