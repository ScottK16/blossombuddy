package org.blossomsuite.core.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Matcher;
import org.junit.jupiter.api.Test;

/** The scoreboard lines BlossomCraft shows, in the plain and small-capital forms its labels can take. */
class SidebarParserTest {
   private static Matcher vote(String line) {
      return SidebarParser.VOTE_PARTY_PATTERN.matcher(SidebarParser.normalizeSidebarText(line));
   }

   private static Matcher server(String line) {
      return SidebarParser.SERVER_PATTERN.matcher(SidebarParser.normalizeSidebarText(line));
   }

   @Test
   void theVotePartyLineIsReadFromPlainText() {
      Matcher m = vote("VOTE PARTY: 84/150");
      assertTrue(m.find());
      assertEquals(84, Integer.parseInt(m.group(1)));
      assertEquals(150, Integer.parseInt(m.group(2)));
   }

   @Test
   void theVotePartyLineIsReadFromSmallCapitals() {
      // ᴠᴏᴛᴇ ᴘᴀʀᴛʏ = small-caps "VOTE PARTY"
      Matcher m = vote("ᴠᴏᴛᴇ ᴘᴀʀᴛʏ: 115/150");
      assertTrue(m.find());
      assertEquals(115, Integer.parseInt(m.group(1)));
      assertEquals(150, Integer.parseInt(m.group(2)));
   }

   @Test
   void theRealmNameIsReadInBothForms() {
      Matcher plain = server("SERVER: Cherry");
      assertTrue(plain.find());
      assertEquals("cherry", plain.group(1));

      // ꜱᴇʀᴠᴇʀ = small-caps "SERVER"
      Matcher caps = server("ꜱᴇʀᴠᴇʀ: Tulip");
      assertTrue(caps.find());
      assertEquals("tulip", caps.group(1));
   }

   @Test
   void otherScoreboardLinesAreNotMistakenForTheVoteParty() {
      for (String line : new String[]{"BALANCE: $17,390", "CLAIM BLOCKS: 144", "ONLINE: 43", "WORLD: Overworld", "PLAY.BLOSSOMCRAFT.ORG"}) {
         assertFalse(vote(line).find(), line);
      }
   }

   @Test
   void theWorldLineDoesNotLookLikeARealm() {
      assertFalse(server("WORLD: Overworld").find());
   }
}
