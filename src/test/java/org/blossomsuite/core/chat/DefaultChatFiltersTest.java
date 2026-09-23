package org.blossomsuite.core.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.blossomsuite.core.config.FeatureConfig;
import org.junit.jupiter.api.Test;

/** The default filters, checked against real lines from BlossomCraft chat. */
class DefaultChatFiltersTest {
   private static FeatureConfig.Filter named(List<FeatureConfig.Filter> filters, String name) {
      return filters.stream().filter(f -> name.equals(f.name)).findFirst().orElseThrow();
   }

   private static List<FeatureConfig.Filter> defaults() {
      return new FeatureConfig.Chat().filters;
   }

   /** Lines that are just ordinary chat or server noise: no default filter should touch them. */
   private static final String[] ORDINARY = {
      "Wanderer : SimpatiX > ok thanks",
      "Shinobi : Auremys > yeah they are",
      "BR_BubbyLeeC tried to swim in lava",
      "bat from s1",
      "IMMORTAL : ~chapo > oh shit I got dizy",
      "Ranger : C0d3Br34ker > u need all that space?-",
      "JURASSIC Crate Released!",
      "You can view the crate at /warp Crates",
      "» shop.blossomcraft.org"};

   @Test
   void partnerChatLinesGoToTheMarryFilter() {
      FeatureConfig.Filter marry = named(defaults(), "Marry");
      assertTrue(SecondaryChat.matches(marry, "266♥ ~IrishScotty => All Partners: test"));
      assertTrue(SecondaryChat.matches(marry, "266♥ Yulz => All Partners: grr"));
      assertTrue(SecondaryChat.matches(marry, "You have set your chat to the private marry chat."));
   }

   @Test
   void everyLineOfATeleportRequestIsCaught() {
      FeatureConfig.Filter tp = named(defaults(), "Teleports");
      assertTrue(SecondaryChat.matches(tp, "TELEPORT ~Marv has requested to teleport to you."));
      assertTrue(SecondaryChat.matches(tp, "To teleport, type /tpaccept."));
      assertTrue(SecondaryChat.matches(tp, "To deny this request, type /tpdeny."), "the /tpdeny line used to slip through");
      assertTrue(SecondaryChat.matches(tp, "This request will timeout after 120 seconds."), "and so did the timeout line");
      assertTrue(SecondaryChat.matches(tp, "/tpa <player> - Requests to teleport to the specified player"));
   }

   @Test
   void playersTalkingAboutTeleportsOrMarriageInPublicChatAreNotCaught() {
      FeatureConfig.Filter tp = named(defaults(), "Teleports");
      FeatureConfig.Filter marry = named(defaults(), "Marry");
      // the line from a real report: a Bedrock player asking about a teleport request
      assertFalse(SecondaryChat.matches(tp, "Guardian : BR_Savs614 > why the tp request? can you fly?"));
      assertFalse(SecondaryChat.matches(tp, "Wanderer : Bob > can someone /tpa me please, my teleport is broken"));
      assertFalse(SecondaryChat.matches(tp, "IMMORTAL : ~chapo > tpdeny me again and I quit"));
      assertFalse(SecondaryChat.matches(marry, "Ranger : Alice > will you marry me?"));
      assertFalse(SecondaryChat.matches(marry, "Shinobi : ~Sam > i got married yesterday"));
   }

   @Test
   void theServersOwnTeleportNoticesAreStillCaught() {
      FeatureConfig.Filter tp = named(defaults(), "Teleports");
      for (String line : new String[]{"TELEPORT ~Marv has requested to teleport to you.", "To teleport, type /tpaccept.", "To deny this request, type /tpdeny.",
         "This request will timeout after 120 seconds.", "/tpa <player> - Requests to teleport to the specified player"}) {
         assertTrue(SecondaryChat.matches(tp, line), line);
      }
   }

   @Test
   void aWordInTheMiddleOfAnotherWordIsNotATeleportRequest() {
      FeatureConfig.Filter tp = named(defaults(), "Teleports");
      assertFalse(SecondaryChat.matches(tp, "Someone joined the game: Ctpalace99"));
   }

   @Test
   void privateMessagesInBothDirectionsGoToTheMessagesFilter() {
      FeatureConfig.Filter dm = named(defaults(), "Messages");
      assertTrue(SecondaryChat.matches(dm, "✉ MESSAGE ME ✈ ~Marv ✎ test"), "one you sent");
      assertTrue(SecondaryChat.matches(dm, "✉ MESSAGE ~Marv ✈ ME ✎ test"), "one you received");
      assertTrue(SecondaryChat.matches(dm, "MESSAGE Steve ✈ ME ✎ hello"), "a player without a nickname prefix");
   }

   @Test
   void playersTalkingAboutMessagesAreNotCaughtByTheMessagesFilter() {
      FeatureConfig.Filter dm = named(defaults(), "Messages");
      assertFalse(SecondaryChat.matches(dm, "266♥ ~IrishScotty => All Partners: can you try /message me"));
      assertFalse(SecondaryChat.matches(dm, "266♥ ~IrishScotty => All Partners: someone mind sending a message in here"));
   }

   @Test
   void ordinaryChatIsNeverCaught() {
      for (FeatureConfig.Filter f : defaults()) {
         for (String line : ORDINARY) {
            assertFalse(SecondaryChat.matches(f, line), f.name + " must not match: " + line);
         }
      }
   }

   @Test
   void everyDefaultFilterMovesItsLinesOutOfTheMainChat() {
      for (FeatureConfig.Filter f : defaults()) {
         if (!f.external) {
            assertTrue(f.hideFromMain, f.name);
         }
      }
   }

   @Test
   void filtersLeftAtAnyOlderDefaultAreUpgraded() {
      List<FeatureConfig.Filter> saved = new ArrayList<>();
      saved.add(new FeatureConfig.Filter("Teleports", FeatureConfig.Chat.OLD_TELEPORTS_PATTERN));
      saved.add(new FeatureConfig.Filter("Marry", FeatureConfig.Chat.OLD_MARRY_PATTERN));
      saved.add(new FeatureConfig.Filter("Messages", FeatureConfig.Chat.OLD_MESSAGES_PATTERN_2));

      assertTrue(FeatureConfig.Chat.upgradeOldDefaults(saved));
      assertEquals(FeatureConfig.Chat.TELEPORTS_PATTERN, saved.get(0).pattern);
      assertEquals(FeatureConfig.Chat.MARRY_PATTERN, saved.get(1).pattern);
      assertEquals(FeatureConfig.Chat.MESSAGES_PATTERN, saved.get(2).pattern);
      assertTrue(saved.get(2).regex, "the new Messages pattern is a regular expression");
      for (FeatureConfig.Filter f : saved) {
         assertTrue(f.hideFromMain, f.name);
      }

      assertFalse(FeatureConfig.Chat.upgradeOldDefaults(saved), "upgrading twice changes nothing");
   }

   @Test
   void theFirstVersionOfTheMessagesFilterIsUpgradedToo() {
      List<FeatureConfig.Filter> saved = new ArrayList<>(List.of(new FeatureConfig.Filter("Messages", FeatureConfig.Chat.OLD_MESSAGES_PATTERN)));
      assertTrue(FeatureConfig.Chat.upgradeOldDefaults(saved));
      assertEquals(FeatureConfig.Chat.MESSAGES_PATTERN, saved.get(0).pattern);
   }

   @Test
   void filtersThePlayerEditedAreLeftAlone() {
      FeatureConfig.Filter mine = new FeatureConfig.Filter("Marry", "my own words");
      mine.hideFromMain = false;
      List<FeatureConfig.Filter> saved = new ArrayList<>(List.of(mine));

      assertFalse(FeatureConfig.Chat.upgradeOldDefaults(saved));
      assertEquals("my own words", mine.pattern);
      assertFalse(mine.hideFromMain);
   }
}
