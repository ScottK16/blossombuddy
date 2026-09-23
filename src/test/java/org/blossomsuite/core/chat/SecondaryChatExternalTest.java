package org.blossomsuite.core.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.text.Text;
import org.blossomsuite.core.config.FeatureConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Cross-realm chat lines are put in the secondary chat by the mod, not matched from game chat. */
class SecondaryChatExternalTest {
   @BeforeEach
   void reset() {
      FeatureConfig.INSTANCE.chat.filters = new FeatureConfig.Chat().filters;
      FeatureConfig.INSTANCE.chat.show = true;
   }

   private static FeatureConfig.Filter realms(List<FeatureConfig.Filter> filters) {
      return filters.stream().filter(f -> f.external).findFirst().orElseThrow();
   }

   @Test
   void theRealmsTabExistsByDefault() {
      FeatureConfig.Filter r = realms(new FeatureConfig.Chat().filters);
      assertEquals(SecondaryChat.REALMS, r.name);
      assertTrue(r.enabled);
      assertFalse(r.hideFromMain);
   }

   @Test
   void externalLinesAppearUnderTheRealmsTab() {
      SecondaryChat chat = new SecondaryChat();
      chat.addExternal(Text.literal("[Cherry] Alice: hi"));
      List<SecondaryChat.Line> lines = chat.recent(SecondaryChat.REALMS, 10);
      assertEquals(1, lines.size());
      assertEquals("[Cherry] Alice: hi", lines.get(0).text().getString());
      assertEquals(1, chat.recent(null, 10).size(), "and in 'All'");
      assertTrue(chat.recent("Teleports", 10).isEmpty());
   }

   @Test
   void gameChatIsNeverFiledUnderRealms() {
      SecondaryChat chat = new SecondaryChat();
      assertFalse(chat.onMessage(Text.literal("anything at all, even the word Realms")));
      assertTrue(chat.recent(SecondaryChat.REALMS, 10).isEmpty());
   }

   @Test
   void anExternalFilterNeverMatchesText() {
      FeatureConfig.Filter r = realms(new FeatureConfig.Chat().filters);
      r.pattern = "hello"; // even if someone types a pattern into it
      assertFalse(SecondaryChat.matches(r, "hello world"));
   }

   @Test
   void theTabIsAddedToSavedFiltersThatLackIt() {
      List<FeatureConfig.Filter> saved = new ArrayList<>();
      saved.add(new FeatureConfig.Filter("Marry", "marry"));
      assertTrue(FeatureConfig.Chat.ensureRealmsFilter(saved));
      assertEquals(2, saved.size());
      assertFalse(FeatureConfig.Chat.ensureRealmsFilter(saved), "added once, not every load");
      assertEquals(2, saved.size());
   }

   @Test
   void nullEntriesInASavedListDoNotBreakIt() {
      List<FeatureConfig.Filter> saved = new ArrayList<>();
      saved.add(null);
      assertTrue(FeatureConfig.Chat.ensureRealmsFilter(saved));
   }

   @Test
   void cyclingTheTabsIncludesRealms() {
      SecondaryChat chat = new SecondaryChat();
      FeatureConfig.INSTANCE.chat.selected = -1;
      List<String> seen = new ArrayList<>();
      for (int i = 0; i < FeatureConfig.INSTANCE.chat.filters.size(); i++) {
         seen.add(chat.cycleFilter());
      }

      assertTrue(seen.contains(SecondaryChat.REALMS), seen.toString());
   }

   @Test
   void crossRealmChatIsOffUntilTheOwnerTurnsItOn() {
      FeatureConfig.XChat x = new FeatureConfig.XChat();
      assertFalse(x.enabled);
      assertTrue(x.muted.isEmpty());
   }
}
