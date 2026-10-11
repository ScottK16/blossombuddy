package org.blossomsuite.core.dungeons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.blossomsuite.BlossomBuddyProfile;
import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.config.DungeonConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.storage.DungeonStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The dungeon timer, fed the same chat lines the server sends. The timer is yours alone: teammates never move it. */
class DungeonStateTest {
   private static final String SERVER = "cherry";
   private static final long EIGHT_HOURS = 8L * 60L * 60L * 1000L;

   private List<String> chat;

   @BeforeEach
   void setUp() {
      SuiteRuntime.initialize(BlossomBuddyProfile.INSTANCE);
      DungeonConfig cfg = SuiteConfig.INSTANCE.DungeonConfig;
      cfg.enabled = true;
      cfg.cooldownHours = 8;
      cfg.reportRunsInChat = true;
      cfg.cooldownEndsByServer.clear();
      DungeonState.resetForTests("Alice");
      DungeonRuntime.setServerSupplier(() -> SERVER);
      this.chat = new ArrayList<>();
      DungeonRuntime.setChatReporter(text -> this.chat.add(text.getString()));
   }

   private static void hear(String... lines) {
      for (String line : lines) {
         DungeonState.observeChatLine(line);
      }
   }

   private static void startAsAParty() {
      hear(
         "Bob joined the party.",
         "[Dungeons] Alice is ready to enter the dungeon! (1/2)",
         "[Dungeons] Bob is ready to enter the dungeon! (2/2)",
         "[Dungeons] All players are ready! Entering the dungeon!"
      );
   }

   private static long cooldownLeft() {
      return SuiteConfig.INSTANCE.DungeonConfig.cooldownEndsAt(SERVER) - System.currentTimeMillis();
   }

   private static DungeonStore.DungeonAttempt lastAttempt() {
      List<DungeonStore.DungeonAttempt> attempts = DungeonStore.getRecent12ByServer().get(SERVER);
      assertNotNull(attempts, "an attempt should have been saved");
      return attempts.get(attempts.size() - 1);
   }

   @Test
   void theTimerStartsWhenThePartyEntersTheDungeon() {
      assertNull(DungeonState.activeRunLine(System.currentTimeMillis()));
      startAsAParty();
      assertTrue(DungeonState.inRun());
      String line = DungeonState.activeRunLine(System.currentTimeMillis());
      assertNotNull(line);
      assertTrue(line.startsWith("Akuma: "), line);
      assertFalse(DungeonState.cooldownActive(System.currentTimeMillis()), "no cooldown until the run is over");
   }

   @Test
   void aTeammateRunningOutOfLivesDoesNotTouchYourTimerOrCooldown() {
      startAsAParty();
      String before = DungeonState.activeRunLine(System.currentTimeMillis());

      hear("[Dungeons] Bob lost all their lives!");

      assertTrue(DungeonState.inRun(), "your run carries on");
      assertNotNull(DungeonState.activeRunLine(System.currentTimeMillis()));
      assertEquals(before.substring(0, 6), DungeonState.activeRunLine(System.currentTimeMillis()).substring(0, 6));
      assertFalse(DungeonState.cooldownActive(System.currentTimeMillis()), "their death must not start your cooldown");
      assertTrue(this.chat.isEmpty(), "nothing is announced for somebody else's death: " + this.chat);
   }

   @Test
   void youCanStillFinishAfterATeammateDiedAndYourCooldownStartsThen() {
      startAsAParty();
      hear("[Dungeons] Bob lost all their lives!");
      hear("AKUMA HAS FALLEN!");

      assertFalse(DungeonState.inRun());
      assertTrue(DungeonState.cooldownActive(System.currentTimeMillis()));
      long left = cooldownLeft();
      assertTrue(left > EIGHT_HOURS - 5_000L && left <= EIGHT_HOURS, "eight hours from now: " + left);
      assertTrue(lastAttempt().completed());
      assertTrue(this.chat.get(0).startsWith("Dungeon completed"), this.chat.toString());
   }

   @Test
   void yourOwnDeathEndsYourRunAndStartsYourCooldown() {
      startAsAParty();
      hear("[Dungeons] You lost all your lives!");

      assertFalse(DungeonState.inRun());
      assertTrue(DungeonState.cooldownActive(System.currentTimeMillis()));
      assertFalse(lastAttempt().completed());
   }

   @Test
   void yourOwnDeathIsAlsoRecognisedWhenTheServerNamesYou() {
      startAsAParty();
      hear("[Dungeons] Alice lost all their lives!");

      assertFalse(DungeonState.inRun());
      assertTrue(DungeonState.cooldownActive(System.currentTimeMillis()));
      assertFalse(lastAttempt().completed());
   }

   @Test
   void theLastTeammateDyingAfterYouAlreadyFinishedChangesNothing() {
      startAsAParty();
      hear("AKUMA HAS FALLEN!");
      long endsAt = SuiteConfig.INSTANCE.DungeonConfig.cooldownEndsAt(SERVER);
      int messages = this.chat.size();

      hear("[Dungeons] Bob lost all their lives!", "Bob left the party.");

      assertEquals(endsAt, SuiteConfig.INSTANCE.DungeonConfig.cooldownEndsAt(SERVER));
      assertEquals(messages, this.chat.size());
   }

   @Test
   void partyComingsAndGoingsNeverStopTheTimer() {
      startAsAParty();
      hear("Bob left the party.", "Carol joined the party.", "Carol was kicked from the party.", "The party has been disbanded.");

      assertTrue(DungeonState.inRun());
      assertFalse(DungeonState.cooldownActive(System.currentTimeMillis()));
   }

   @Test
   void theCooldownLengthFollowsTheSetting() {
      SuiteConfig.INSTANCE.DungeonConfig.setCooldownHours(3);
      startAsAParty();
      hear("AKUMA HAS FALLEN!");

      long left = cooldownLeft();
      assertTrue(left > 3L * 3_600_000L - 5_000L && left <= 3L * 3_600_000L, "three hours: " + left);
   }

   @Test
   void nothingIsTrackedWhileSwitchedOff() {
      SuiteConfig.INSTANCE.DungeonConfig.enabled = false;
      startAsAParty();

      assertFalse(DungeonState.inRun());
      assertNull(DungeonState.activeRunLine(System.currentTimeMillis()));
   }

   @Test
   void leavingTheGameDropsTheRunButKeepsACooldownThatAlreadyStarted() {
      startAsAParty();
      hear("[Dungeons] You lost all your lives!");
      long endsAt = SuiteConfig.INSTANCE.DungeonConfig.cooldownEndsAt(SERVER);

      DungeonState.resetForDisconnect();

      assertEquals(endsAt, SuiteConfig.INSTANCE.DungeonConfig.cooldownEndsAt(SERVER));
      startAsAParty();
      DungeonState.resetForDisconnect();
      assertFalse(DungeonState.inRun());
   }

   @Test
   void otherChatIsIgnored() {
      hear("<Bob> Akuma HAS FALLEN lol", "just some chat", "");
      assertFalse(DungeonState.inRun());
   }

   @Test
   void theHudRowsCountDownForTheCurrentRealm() {
      startAsAParty();
      hear("AKUMA HAS FALLEN!");

      List<DungeonState.CooldownRow> rows = DungeonState.hudCooldownRows(System.currentTimeMillis());
      assertEquals(1, rows.size());
      assertEquals(SERVER, rows.get(0).server());
      assertTrue(rows.get(0).active());
      String left = DungeonState.formatCooldownDuration(rows.get(0).remainingMs());
      assertTrue(left.equals("8h 0m") || left.equals("7h 59m"), left);
   }
}
