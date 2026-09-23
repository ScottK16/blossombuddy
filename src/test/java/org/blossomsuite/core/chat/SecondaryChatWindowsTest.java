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

/** Extra chat windows: one filter per window, and what that does to the main window and to hiding lines from main chat. */
class SecondaryChatWindowsTest {
   private static final String MARRY_LINE = "266♥ ~IrishScotty => All Partners: hello";
   private static final String TP_LINE = "TELEPORT ~Marv has requested to teleport to you.";

   @BeforeEach
   void reset() {
      FeatureConfig.Chat fresh = new FeatureConfig.Chat();
      FeatureConfig.INSTANCE.chat.filters = fresh.filters;
      FeatureConfig.INSTANCE.chat.windows = fresh.windows;
      FeatureConfig.INSTANCE.chat.show = false;
      FeatureConfig.INSTANCE.chat.selected = -1;
   }

   private static FeatureConfig.ChatWindow window(int i, String filter, boolean enabled, boolean exclusive) {
      FeatureConfig.ChatWindow w = FeatureConfig.INSTANCE.chat.windows.get(i);
      w.filter = filter;
      w.enabled = enabled;
      w.exclusive = exclusive;
      return w;
   }

   private static String texts(List<SecondaryChat.Line> lines) {
      return lines.stream().map(l -> l.filter()).collect(Collectors.joining(","));
   }

   private static SecondaryChat withMarryAndTeleport() {
      SecondaryChat chat = new SecondaryChat();
      chat.onMessage(Text.literal(MARRY_LINE));
      chat.onMessage(Text.literal(TP_LINE));
      return chat;
   }

   @Test
   void withNoExtraWindowsTheAllViewShowsEverything() {
      SecondaryChat chat = withMarryAndTeleport();
      assertEquals("Marry,Teleports", texts(chat.recent(null, 10)));
      assertEquals(2, chat.count(null));
   }

   @Test
   void aWindowOfItsOwnTakesItsLinesOutOfTheAllView() {
      window(0, "Marry", true, true);
      SecondaryChat chat = withMarryAndTeleport();
      assertEquals("Teleports", texts(chat.recent(null, 10)), "Marry has its own window now");
      assertEquals(1, chat.count(null));
      assertEquals(1, chat.count("Marry"), "the window itself still sees them");
      assertEquals("Marry", texts(chat.recent("Marry", 10)));
   }

   @Test
   void theLinesReturnToTheAllViewWhenTheWindowIsOffOrNotExclusive() {
      SecondaryChat chat = withMarryAndTeleport();
      window(0, "Marry", false, true);
      assertEquals(2, chat.count(null), "window switched off");
      window(0, "Marry", true, false);
      assertEquals(2, chat.count(null), "window kept, but not exclusive");
   }

   @Test
   void theFilterNameIsMatchedWithoutRegardToCase() {
      window(0, "marry", true, true);
      assertEquals("Teleports", texts(withMarryAndTeleport().recent(null, 10)));
   }

   @Test
   void aSpecificTabStillShowsItsLinesWhateverWindowsExist() {
      window(0, "Marry", true, true);
      assertEquals("Marry", texts(withMarryAndTeleport().recent("Marry", 10)));
   }

   @Test
   void aLineIsHiddenFromMainChatWhenItsOwnWindowIsShowingEvenIfTheMainWindowIsOff() {
      FeatureConfig.INSTANCE.chat.show = false;
      SecondaryChat chat = new SecondaryChat();
      assertFalse(chat.onMessage(Text.literal(MARRY_LINE)), "no window to read it in: it stays in main chat");

      window(0, "Marry", true, true);
      assertTrue(chat.onMessage(Text.literal(MARRY_LINE)), "the Marry window shows it, so main chat doesn't need to");
      assertFalse(chat.onMessage(Text.literal(TP_LINE)), "Teleports has no window: still in main chat");

      window(0, "Marry", false, true);
      assertFalse(chat.onMessage(Text.literal(MARRY_LINE)), "window off again");
   }

   @Test
   void aFilterThatDoesNotHideFromMainIsNeverHidden() {
      window(0, "Marry", true, true);
      FeatureConfig.INSTANCE.chat.filters.stream().filter(f -> "Marry".equals(f.name)).findFirst().orElseThrow().hideFromMain = false;
      assertFalse(new SecondaryChat().onMessage(Text.literal(MARRY_LINE)));
   }

   @Test
   void dedicatedWindowsAreFoundByFilterName() {
      assertFalse(SecondaryChat.dedicatedWindowShows("Marry"), "all windows start off");
      window(1, "Staff", true, true);
      assertTrue(SecondaryChat.dedicatedWindowShows("staff"));
      assertFalse(SecondaryChat.dedicatedWindowShows("Marry"));
      assertFalse(SecondaryChat.dedicatedWindowShows(null));
      window(1, "", true, true);
      assertFalse(SecondaryChat.dedicatedWindowShows("Staff"), "a window with no filter shows nothing");
   }

   @Test
   void theStaffFilterIsOffByDefaultSoItCatchesNothingUntilAStaffMemberTurnsItOn() {
      SecondaryChat chat = new SecondaryChat();
      chat.onMessage(Text.literal("[Staff] Admin: meeting at 5"));
      assertEquals(0, chat.count("Staff"));

      FeatureConfig.INSTANCE.chat.filters.stream().filter(f -> "Staff".equals(f.name)).findFirst().orElseThrow().enabled = true;
      chat.onMessage(Text.literal("[Staff] Admin: meeting at 5"));
      assertEquals(1, chat.count("Staff"));
   }

   @Test
   void theRealStaffChatFormatIsCaughtWithOrWithoutATimeInFront() {
      FeatureConfig.INSTANCE.chat.filters.stream().filter(f -> "Staff".equals(f.name)).findFirst().orElseThrow().enabled = true;
      SecondaryChat chat = new SecondaryChat();
      for (String line : new String[]{"[Staff] updated staff chat toggle to on.", "[Staff] Theonlymxrvin > tst", "[22:59:05] [Staff] Theonlymxrvin > test", "  [Staff] Name > indented"}) {
         chat.onMessage(Text.literal(line));
      }

      assertEquals(4, chat.count("Staff"));
   }

   @Test
   void publicChatThatMerelyMentionsStaffIsNotStaffChat() {
      FeatureConfig.INSTANCE.chat.filters.stream().filter(f -> "Staff".equals(f.name)).findFirst().orElseThrow().enabled = true;
      SecondaryChat chat = new SecondaryChat();
      for (String line : new String[]{"Wanderer : SimpatiX > [Staff] hello everyone", "Ranger : Bob > can someone in staff chat help me?", "BR_Fishyboy12389 died", "[Party] Alice > staff"}) {
         chat.onMessage(Text.literal(line));
      }

      assertEquals(0, chat.count("Staff"), "a player typing [Staff] mid-line can't fake a staff message");
   }

   @Test
   void aStaffWindowCatchesStaffChatEvenThoughTheStaffFilterItselfIsOff() {
      SecondaryChat chat = new SecondaryChat();
      chat.onMessage(Text.literal("[Staff] Theonlymxrvin > before the window"));
      assertEquals(0, chat.count("Staff"), "no window, filter off: nothing is kept");

      window(1, "Staff", true, true);
      chat.onMessage(Text.literal("[Staff] Theonlymxrvin > after the window"));
      assertEquals(1, chat.count("Staff"));

      window(1, "Staff", false, true);
      chat.onMessage(Text.literal("[Staff] Theonlymxrvin > window off again"));
      assertEquals(1, chat.count("Staff"), "and not again once the window is off");
   }

   @Test
   void aScrolledMainWindowKeepsItsPlaceIgnoringLinesThatBelongToAnotherWindow() {
      window(0, "Marry", true, true);
      SecondaryChat chat = new SecondaryChat();
      for (int i = 0; i < 6; i++) {
         chat.onMessage(Text.literal(TP_LINE + " " + i));
      }

      chat.scrollBy(3);
      assertEquals(3, chat.scroll());
      chat.onMessage(Text.literal(MARRY_LINE));
      assertEquals(3, chat.scroll(), "a Marry line doesn't appear in All, so it doesn't push the view");
      chat.onMessage(Text.literal(TP_LINE + " new"));
      assertEquals(4, chat.scroll(), "a Teleports line does");
   }
}
