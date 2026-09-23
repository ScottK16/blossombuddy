package org.blossomsuite.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The extra chat windows, the Staff filter, and the player list being on by default. */
class FeatureConfigChatWindowsTest {
   private static final Gson GSON = new GsonBuilder().create();

   @Test
   void thereAreFourExtraWindowsAllOffAndPointingAtSensibleFilters() {
      FeatureConfig.Chat chat = new FeatureConfig.Chat();
      assertEquals(4, chat.windows.size());
      assertEquals(List.of("Marry", "Staff", "Teleports", "Messages"), chat.windows.stream().map(w -> w.filter).toList());
      for (FeatureConfig.ChatWindow w : chat.windows) {
         assertFalse(w.enabled);
         assertTrue(w.exclusive);
         assertEquals(6, w.lines);
         assertNotNull(w.panel);
      }
   }

   @Test
   void aStaffFilterExistsButIsOffAndTheRealmsTabStaysLast() {
      List<FeatureConfig.Filter> filters = new FeatureConfig.Chat().filters;
      FeatureConfig.Filter staff = filters.stream().filter(f -> "Staff".equals(f.name)).findFirst().orElseThrow();
      assertFalse(staff.enabled, "off until a staff member turns it on");
      assertTrue(staff.hideFromMain);
      assertTrue(filters.get(filters.size() - 1).external, "Realms is still the last tab");
   }

   @Test
   void theStaffFilterUsesTheRealFormatAndOnlyMatchesAtTheStartOfALine() {
      FeatureConfig.Filter staff = new FeatureConfig.Chat().filters.stream().filter(f -> "Staff".equals(f.name)).findFirst().orElseThrow();
      assertTrue(staff.regex);
      assertEquals(FeatureConfig.Chat.STAFF_PATTERN, staff.pattern);
      java.util.regex.Pattern p = java.util.regex.Pattern.compile(staff.pattern, java.util.regex.Pattern.CASE_INSENSITIVE);
      assertTrue(p.matcher("[Staff] Theonlymxrvin > test").find());
      assertTrue(p.matcher("[22:59:03] [Staff] Theonlymxrvin > tst").find());
      assertFalse(p.matcher("Someone > [Staff] hi").find());
   }

   @Test
   void aStaffFilterLeftAtTheFirstGuessIsUpgraded() {
      List<FeatureConfig.Filter> saved = new ArrayList<>();
      FeatureConfig.Filter staff = new FeatureConfig.Filter("Staff", FeatureConfig.Chat.OLD_STAFF_PATTERN);
      saved.add(staff);
      assertTrue(FeatureConfig.Chat.upgradeOldDefaults(saved));
      assertEquals(FeatureConfig.Chat.STAFF_PATTERN, staff.pattern);
      assertTrue(staff.regex);
      assertFalse(FeatureConfig.Chat.upgradeOldDefaults(saved), "upgrading twice changes nothing");

      FeatureConfig.Filter mine = new FeatureConfig.Filter("Staff", "my own words");
      assertFalse(FeatureConfig.Chat.upgradeOldDefaults(new ArrayList<>(List.of(mine))));
      assertEquals("my own words", mine.pattern);
   }

   @Test
   void theStaffFilterIsAddedToAnOlderSavedListOnceBeforeRealms() {
      List<FeatureConfig.Filter> saved = new ArrayList<>();
      saved.add(new FeatureConfig.Filter("Marry", "marry"));
      FeatureConfig.Filter realms = new FeatureConfig.Filter("Realms", "");
      realms.external = true;
      saved.add(realms);

      assertTrue(FeatureConfig.Chat.ensureStaffFilter(saved));
      assertEquals(List.of("Marry", "Staff", "Realms"), saved.stream().map(f -> f.name).toList());
      assertFalse(saved.get(1).enabled);
      assertFalse(FeatureConfig.Chat.ensureStaffFilter(saved), "not added twice");
   }

   @Test
   void aStaffFilterThePlayerMadeThemselvesIsLeftAlone() {
      List<FeatureConfig.Filter> saved = new ArrayList<>(List.of(new FeatureConfig.Filter("STAFF", "my own words")));
      assertFalse(FeatureConfig.Chat.ensureStaffFilter(saved));
      assertEquals("my own words", saved.get(0).pattern);
   }

   @Test
   void windowListsAreRepairedToExactlyFourUsableEntries() {
      List<FeatureConfig.ChatWindow> list = new ArrayList<>();
      assertTrue(FeatureConfig.Chat.normalizeWindows(list));
      assertEquals(4, list.size());
      assertFalse(FeatureConfig.Chat.normalizeWindows(list), "a good list is left alone");

      List<FeatureConfig.ChatWindow> messy = new ArrayList<>();
      messy.add(null);
      FeatureConfig.ChatWindow bad = new FeatureConfig.ChatWindow();
      bad.panel = null;
      bad.filter = null;
      bad.lines = 500;
      messy.add(bad);
      for (int i = 0; i < 5; i++) {
         messy.add(new FeatureConfig.ChatWindow("x"));
      }

      assertTrue(FeatureConfig.Chat.normalizeWindows(messy));
      assertEquals(4, messy.size(), "too many are trimmed");
      assertEquals("Marry", messy.get(0).filter, "a missing entry gets its preset");
      assertEquals("", messy.get(1).filter);
      assertNotNull(messy.get(1).panel);
      assertEquals(20, messy.get(1).lines);
   }

   @Test
   void aConfigFromBeforeTheWindowsExistedStillGetsFourOffWindows() {
      FeatureConfig loaded = GSON.fromJson("{\"chat\":{\"show\":true,\"lines\":8}}", FeatureConfig.class);
      assertEquals(4, loaded.chat.windows.size());
      assertFalse(loaded.chat.windows.get(0).enabled);
   }

   @Test
   void thePlayerListIsOnByDefaultAndTheNoticeHasNotBeenShown() {
      FeatureConfig.Presence p = new FeatureConfig.Presence();
      assertTrue(p.enabled);
      assertTrue(p.tabSymbol);
      assertFalse(p.noticeShown);
   }

   @Test
   void aConfigFromBeforeThePlayerListGetsItOnWithTheNoticePending() {
      FeatureConfig loaded = GSON.fromJson("{\"relay\":{\"share\":true},\"xchat\":{\"enabled\":true}}", FeatureConfig.class);
      assertTrue(loaded.presence.enabled);
      assertFalse(loaded.presence.noticeShown);
   }

   @Test
   void aPlayerWhoSwitchedTheListOffStaysOff() {
      FeatureConfig loaded = GSON.fromJson("{\"presence\":{\"enabled\":false,\"noticeShown\":true}}", FeatureConfig.class);
      assertFalse(loaded.presence.enabled);
      assertTrue(loaded.presence.noticeShown);
   }
}
