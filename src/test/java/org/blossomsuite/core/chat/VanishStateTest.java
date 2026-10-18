package org.blossomsuite.core.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.blossomsuite.core.config.FeatureConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VanishStateTest {
   @BeforeEach
   @AfterEach
   void clean() {
      FeatureConfig.INSTANCE.staff = new FeatureConfig.Staff();
      VanishState.vanished = false;
      VanishState.staffSidebar = false;
   }

   @Test
   void theServersVanishLinesAreRecognisedForTheLocalPlayerOnly() {
      assertEquals(Boolean.TRUE, VanishState.parseServerLine("Vanish for BaraGC: ENABLED", "BaraGC"));
      assertEquals(Boolean.FALSE, VanishState.parseServerLine("Vanish for ~Bara: DISABLED", "BaraGC"));
      assertNull(VanishState.parseServerLine("Vanish for SomeoneElse: ENABLED", "BaraGC"));
      assertNull(VanishState.parseServerLine("hello there", "BaraGC"));
   }

   @Test
   void joiningOrChangingRealmDoesNotMakeAVanishedPlayerVisibleAgain() {
      VanishState.setVanished(true);
      VanishState.resetForJoin(); // what happens on every join and every realm change
      assertTrue(VanishState.vanished, "still vanished on the new realm");
   }

   @Test
   void itIsRememberedInTheSettingsAndComesBackAfterARestart() {
      VanishState.setVanished(true);
      assertTrue(FeatureConfig.INSTANCE.staff.vanished);

      VanishState.vanished = false; // a fresh start of the game
      VanishState.loadFromConfig();
      assertTrue(VanishState.vanished);
   }

   @Test
   void onlyTheServerOrThePlayerEndsIt() {
      VanishState.setVanished(true);
      VanishState.setVanished(VanishState.parseServerLine("Vanish for BaraGC: DISABLED", "BaraGC"));
      assertFalse(VanishState.vanished);
      assertFalse(FeatureConfig.INSTANCE.staff.vanished);
   }

   @Test
   void staffWhoAreVanishedOnEveryJoinAreHiddenFromTheMomentTheyJoin() {
      FeatureConfig.INSTANCE.staff.autoVanish = true;
      assertFalse(VanishState.vanished);
      VanishState.resetForJoin(); // joining a realm
      assertTrue(VanishState.vanished, "hidden before any message from the server");

      VanishState.setVanished(VanishState.parseServerLine("Vanish for BaraGC: DISABLED", "BaraGC"));
      assertFalse(VanishState.vanished, "the server saying vanish is off still wins");
      VanishState.resetForJoin(); // and next realm they are vanished again
      assertTrue(VanishState.vanished);
   }

   @Test
   void withoutTheSettingJoiningNeverHidesAnyone() {
      FeatureConfig.INSTANCE.staff.autoVanish = false;
      VanishState.resetForJoin();
      assertFalse(VanishState.vanished);
   }

   @Test
   void staffAreKeptOffTheListsWhetherVanishedOrNotSoTogglingGivesNothingAway() {
      assertFalse(VanishState.hiddenFromLists(), "an ordinary player is shown");

      VanishState.staffSidebar = true; // their sidebar has a Visibility line
      VanishState.vanished = false;
      assertTrue(VanishState.hiddenFromLists(), "staff are hidden even while visible");
      VanishState.vanished = true;
      assertTrue(VanishState.hiddenFromLists());
      VanishState.vanished = false;
      assertTrue(VanishState.hiddenFromLists(), "going visible changes nothing that others can see");
   }

   @Test
   void aStaffMemberCanChooseToShowWhileVisibleButNeverWhileVanished() {
      VanishState.staffSidebar = true;
      FeatureConfig.INSTANCE.staff.showWhenStaff = true;
      assertFalse(VanishState.hiddenFromLists());
      VanishState.vanished = true;
      assertTrue(VanishState.hiddenFromLists(), "vanish always wins");
   }

   @Test
   void whetherSomeoneIsStaffIsWorkedOutAgainOnEveryJoin() {
      VanishState.staffSidebar = true;
      VanishState.resetForJoin();
      assertFalse(VanishState.staffSidebar, "a player who stopped being staff is not hidden forever");
   }
}
