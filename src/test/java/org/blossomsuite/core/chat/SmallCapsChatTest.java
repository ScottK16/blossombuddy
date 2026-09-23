package org.blossomsuite.core.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.blossomsuite.core.config.FeatureConfig;
import org.junit.jupiter.api.Test;

/**
 * Real lines copied from the game log of a BlossomCraft session. Its labels (MESSAGE, TELEPORT, SYSTEM) are
 * small-capital Unicode rather than plain letters, which is why plain-text filters missed them.
 */
class SmallCapsChatTest {
   // ᴍᴇꜱꜱᴀɢᴇ = small-caps "MESSAGE", ᴍᴇ = "ME"
   private static final String DM_SENT = "✉ ᴍᴇꜱꜱᴀɢᴇ ᴍᴇ ✈ ~Marv ✎ test";
   private static final String DM_RECEIVED = "✉ ᴍᴇꜱꜱᴀɢᴇ ~Marv ✈ ᴍᴇ ✎ hello";
   // small-caps "TELEPORT"
   private static final String TP_SENT = "☄ ᴛᴇʟᴇᴘᴏʀᴛ Request sent to ~Marv.";
   // small-caps "SYSTEM" ... "ENABLED"
   private static final String SYSTEM_LINE = "☁ ꜱʏꜱᴛᴇᴍ Set fly mode ᴇɴᴀʙʟᴇᴅ for ~IrishScotty.";

   private static FeatureConfig.Filter named(String name) {
      return new FeatureConfig.Chat().filters.stream().filter(f -> name.equals(f.name)).findFirst().orElseThrow();
   }

   @Test
   void smallCapitalsFoldToPlainCapitals() {
      assertEquals("MESSAGE", SecondaryChat.foldSmallCaps("ᴍᴇꜱꜱᴀɢᴇ"));
      assertEquals("TELEPORT", SecondaryChat.foldSmallCaps("ᴛᴇʟᴇᴘᴏʀᴛ"));
      assertEquals("SYSTEM", SecondaryChat.foldSmallCaps("ꜱʏꜱᴛᴇᴍ"));
      assertEquals("ENABLED", SecondaryChat.foldSmallCaps("ᴇɴᴀʙʟᴇᴅ"));
   }

   @Test
   void everySmallCapitalHasAPlainLetter() {
      // the two lookup strings must line up one to one, or letters would fold to the wrong thing
      String from = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘꞯǫʀꜱᴛᴜᴠᴡʏᴢ";
      String as = "ABCDEFGHIJKLMNOPQQRSTUVWYZ";
      assertEquals(from.length(), as.length());
      assertEquals("ABCDEFGHIJKLMNOPQRSTUVWYZ", SecondaryChat.foldSmallCaps(from.substring(0, 17) + from.substring(18)));
   }

   @Test
   void plainAndOtherTextIsLeftExactlyAsItWas() {
      String plain = "Wanderer : NicholasB1 > How do you show what item you have?";
      assertSame(plain, SecondaryChat.foldSmallCaps(plain), "nothing to fold: same string back");
      assertEquals("MESSAGE hello ✉", SecondaryChat.foldSmallCaps("ᴍᴇꜱꜱᴀɢᴇ hello ✉"));
   }

   @Test
   void privateMessagesFromTheRealLogGoToTheMessagesFilter() {
      FeatureConfig.Filter dm = named("Messages");
      assertTrue(SecondaryChat.matches(dm, DM_SENT), "one you sent");
      assertTrue(SecondaryChat.matches(dm, DM_RECEIVED), "one you received");
   }

   @Test
   void theOutgoingTeleportRequestGoesToTheTeleportsFilter() {
      assertTrue(SecondaryChat.matches(named("Teleports"), TP_SENT));
   }

   @Test
   void ordinaryLinesFromTheRealLogAreNotCaught() {
      List<String> ordinary = List.of(
         SYSTEM_LINE,
         "■ Fontaines D.C : ~IrishScotty > .",
         "■ Guardian : randomguis > hey kaled how to get money fast",
         "■ Wanderer : NicholasB1 > How do you show what item you have?",
         "■ Scout : 77Spire77 > hey i got you the cobwebs",
         "Following partners are online: Yulz, ~Marv");
      for (FeatureConfig.Filter f : new FeatureConfig.Chat().filters) {
         for (String line : ordinary) {
            // "Following partners are online" is partner-related on purpose; everything else must stay out
            if (f.name.equals("Marry") && line.startsWith("Following partners")) {
               continue;
            }

            assertFalse(SecondaryChat.matches(f, line), f.name + " must not match: " + line);
         }
      }
   }

   @Test
   void aFilterWrittenInPlainLettersNowSeesSmallCapitalText() {
      FeatureConfig.Filter mine = new FeatureConfig.Filter("Mine", "teleport");
      assertTrue(SecondaryChat.matches(mine, TP_SENT));
      FeatureConfig.Filter regex = new FeatureConfig.Filter("Mine", "^\\W*system\\b");
      regex.regex = true;
      assertTrue(SecondaryChat.matches(regex, SYSTEM_LINE));
   }
}
