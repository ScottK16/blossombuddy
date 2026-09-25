package org.blossomsuite.core.emote;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** The emote movements: they start and end at rest, stay within sensible angles, and each one does something different. */
class EmoteAnimatorTest {
   private static final float[] RIGHT_REST = {0.0F, 0.0F, 0.05F};
   private static final float[] LEFT_REST = {0.0F, 0.0F, -0.05F};
   /** Emotes that hold a pose instead of moving all the time. */
   private static final Set<String> HOLDS = Set.of("salute", "feetup", "dab");

   private static void assertSane(float[] part, String what) {
      if (part == null) {
         return;
      }

      assertEquals(3, part.length, what);
      for (float v : part) {
         assertTrue(Float.isFinite(v), what + " is a real number");
         assertTrue(Math.abs(v) < 4.0F, what + " stays within a sensible angle: " + v);
      }
   }

   /** The biggest change in any single value between two poses (parts one has and the other lacks count as unchanged). */
   private static float biggestChange(EmotePose a, EmotePose b) {
      float best = Math.max(Math.abs(a.bodyYawDegrees - b.bodyYawDegrees), Math.abs(a.yOffset - b.yOffset));
      best = Math.max(best, Math.abs(a.prone - b.prone));
      float[][][] pairs = {{a.head, b.head}, {a.rightArm, b.rightArm}, {a.leftArm, b.leftArm}, {a.rightLeg, b.rightLeg}, {a.leftLeg, b.leftLeg}, {a.torso, b.torso}};
      for (float[][] pair : pairs) {
         if (pair[0] != null && pair[1] != null) {
            for (int i = 0; i < 3; i++) {
               best = Math.max(best, Math.abs(pair[0][i] - pair[1][i]));
            }
         }
      }

      return best;
   }

   @Test
   void aLoopingEmoteNeverEndsAndStaysSaneForAsLongAsItRuns() {
      for (Emote e : Emote.ALL) {
         for (float t = 0.0F; t < 600.0F; t += 7.3F) {
            EmotePose p = EmoteAnimator.pose(e, t, true);
            assertNotNull(p, e.id() + " at " + t);
            assertSane(p.head, e.id() + " head");
            assertSane(p.rightArm, e.id() + " right arm");
            assertSane(p.leftArm, e.id() + " left arm");
            assertSane(p.rightLeg, e.id() + " right leg");
            assertSane(p.leftLeg, e.id() + " left leg");
            assertTrue(p.prone >= 0.0F && p.prone <= 1.0F, e.id() + " prone " + p.prone);
         }

         assertNull(EmoteAnimator.pose(e, -0.1F, true), "not started");
      }
   }

   @Test
   void aLoopingSpinKeepsTurningInsteadOfStoppingAfterTwoTurns() {
      float early = EmoteAnimator.pose(Emote.SPIN, 10.0F, true).bodyYawDegrees;
      float later = EmoteAnimator.pose(Emote.SPIN, 20.0F, true).bodyYawDegrees;
      assertTrue(later > early + 100.0F, "still turning: " + early + " -> " + later);
   }

   @Test
   void everyEmoteHasAMovementForItsWholeDurationAndNoneBeyond() {
      for (Emote e : Emote.ALL) {
         for (float t = 0.0F; t < e.durationSeconds(); t += 0.1F) {
            EmotePose p = EmoteAnimator.pose(e, t);
            assertNotNull(p, e.id() + " at " + t);
            assertSane(p.head, e.id() + " head");
            assertSane(p.rightArm, e.id() + " right arm");
            assertSane(p.leftArm, e.id() + " left arm");
            assertSane(p.rightLeg, e.id() + " right leg");
            assertSane(p.leftLeg, e.id() + " left leg");
            assertSane(p.torso, e.id() + " torso");
            assertTrue(p.prone >= 0.0F && p.prone <= 1.0F, e.id() + " lies down between not at all and completely: " + p.prone);
            assertTrue(Math.abs(p.yOffset) < 1.0F, e.id() + " stays within a block of where it stands: " + p.yOffset);
            assertTrue(Math.abs(p.bodyYawDegrees) <= 720.5F, e.id() + " turns at most twice round");
         }

         assertNull(EmoteAnimator.pose(e, e.durationSeconds()), e.id() + " is over");
         assertNull(EmoteAnimator.pose(e, e.durationSeconds() + 5.0F));
         assertNull(EmoteAnimator.pose(e, -0.1F), "not started");
      }
   }

   @Test
   void theCharacterEasesInFromRestAndBackOut() {
      for (Emote e : Emote.ALL) {
         EmotePose start = EmoteAnimator.pose(e, 0.0F);
         if (start.rightArm != null) {
            assertArrayEquals(RIGHT_REST, start.rightArm, 1e-4F, e.id() + " starts with the right arm at rest");
         }

         if (start.leftArm != null) {
            assertArrayEquals(LEFT_REST, start.leftArm, 1e-4F, e.id() + " starts with the left arm at rest");
         }

         if (start.head != null) {
            assertArrayEquals(new float[]{0.0F, 0.0F, 0.0F}, start.head, 1e-4F, e.id() + " starts with no head movement");
         }

         if (start.torso != null) {
            assertArrayEquals(new float[]{0.0F, 0.0F, 0.0F}, start.torso, 1e-4F, e.id() + " starts with the torso straight");
         }

         assertEquals(0.0F, start.prone, 1e-4F, e.id() + " starts standing");
         assertEquals(0.0F, start.yOffset, 1e-4F, e.id() + " starts on the ground");
         assertEquals(0.0F, start.bodyYawDegrees, 1e-3F, e.id() + " starts facing the way they were");

         EmotePose almostEnd = EmoteAnimator.pose(e, e.durationSeconds() - 0.001F);
         if (almostEnd.rightArm != null) {
            assertArrayEquals(RIGHT_REST, almostEnd.rightArm, 0.02F, e.id() + " ends back at rest");
         }

         assertEquals(0.0F, almostEnd.yOffset, 0.02F, e.id() + " ends back on the ground");
         assertEquals(0.0F, almostEnd.prone, 0.02F, e.id() + " ends standing again");
      }
   }

   @Test
   void inTheMiddleTheMovementIsFullyAppliedAndDifferentFromRest() {
      float middle = 2.0F;
      EmotePose wave = EmoteAnimator.pose(Emote.WAVE, middle);
      assertTrue(wave.rightArm[2] > 2.0F, "the right arm is up and out");
      assertNull(wave.leftArm, "a wave leaves the other arm alone");
      assertNull(wave.rightLeg);

      EmotePose cheer = EmoteAnimator.pose(Emote.CHEER, middle);
      assertTrue(cheer.rightArm[0] < -2.5F && cheer.leftArm[0] < -2.5F, "both arms are up");

      EmotePose dance = EmoteAnimator.pose(Emote.DANCE, middle);
      assertNotNull(dance.rightLeg);
      assertNotNull(dance.leftLeg);
      assertEquals(-dance.rightLeg[0], dance.leftLeg[0], 1e-4F, "the feet step in opposite directions");

      EmotePose clap = EmoteAnimator.pose(Emote.CLAP, middle);
      assertEquals(-1.45F, clap.rightArm[0], 1e-3F, "arms held out in front");
      assertEquals(clap.rightArm[0], clap.leftArm[0], 1e-4F);
   }

   @Test
   void theNewEmotesDoWhatTheirNamesSay() {
      EmotePose spinStart = EmoteAnimator.pose(Emote.SPIN, 1.0F);
      EmotePose spinLater = EmoteAnimator.pose(Emote.SPIN, 2.5F);
      assertTrue(spinLater.bodyYawDegrees > spinStart.bodyYawDegrees, "the character turns round");
      assertEquals(720.0F, EmoteAnimator.pose(Emote.SPIN, Emote.SPIN.durationSeconds() - 0.0001F).bodyYawDegrees, 1.0F, "two full turns, ending facing the same way");
      assertTrue(spinLater.rightArm[2] > 1.4F && spinLater.leftArm[2] < -1.4F, "arms held out to the sides");

      EmotePose jacksOpen = EmoteAnimator.pose(Emote.JACKS, (float)(Math.PI / 6.0F));
      assertTrue(jacksOpen.rightArm[2] > 2.5F && jacksOpen.leftArm[2] < -2.5F, "arms overhead");
      assertTrue(jacksOpen.rightLeg[2] > 0.3F && jacksOpen.leftLeg[2] < -0.3F, "feet apart");
      assertTrue(jacksOpen.yOffset > 0.1F, "hopping");

      EmotePose headbang = EmoteAnimator.pose(Emote.HEADBANG, 1.0F);
      assertTrue(Math.abs(headbang.head[0]) > 0.1F, "the head moves up and down");

      EmotePose chicken = EmoteAnimator.pose(Emote.CHICKEN, 1.0F);
      assertTrue(chicken.rightArm[2] > 0.4F && chicken.leftArm[2] < -0.4F, "wings out either side");

      EmotePose salute = EmoteAnimator.pose(Emote.SALUTE, 1.5F);
      assertTrue(salute.rightArm[0] < -2.0F, "the right hand goes up to the head");
      assertNull(salute.leftArm);

      EmotePose shrug = EmoteAnimator.pose(Emote.SHRUG, 1.5F);
      assertTrue(shrug.rightArm[2] > 0.5F && shrug.leftArm[2] < -0.5F, "arms out");

      EmotePose robot = EmoteAnimator.pose(Emote.ROBOT, 1.2F);
      assertNotNull(robot.rightArm);
      assertNotNull(robot.leftArm);

      EmotePose disco = EmoteAnimator.pose(Emote.DISCO, 0.6F);
      assertTrue(disco.rightArm[0] < -1.5F || disco.leftArm[0] < -1.5F, "one arm points up");
   }

   @Test
   void feetUpSitsTheCharacterDownWithBothLegsStretchedOutInFront() {
      EmotePose p = EmoteAnimator.pose(Emote.FEETUP, 3.0F);
      assertTrue(p.rightLeg[0] < -1.9F && p.leftLeg[0] < -1.9F, "both legs raised up in the air in front");
      assertTrue(p.yOffset < -0.6F && p.yOffset > -0.75F, "lowered to sit near the ground: " + p.yOffset);
      assertTrue(p.rightArm[0] > 0.3F && p.leftArm[0] > 0.3F, "arms propped behind");
   }

   @Test
   void theFunnyOnesDoWhatTheirNamesSay() {
      EmotePose twerk = EmoteAnimator.pose(Emote.TWERK, 3.0F);
      assertTrue(twerk.yOffset < -0.2F, "squatting down: " + twerk.yOffset);
      assertTrue(twerk.rightLeg[2] > 0.25F && twerk.leftLeg[2] < -0.25F, "feet apart");
      assertTrue(biggestChange(twerk, EmoteAnimator.pose(Emote.TWERK, 3.03F)) > 0.3F, "the hips shake quickly");

      EmotePose dab = EmoteAnimator.pose(Emote.DAB, 2.0F);
      assertTrue(dab.rightArm[2] > 2.0F, "one arm out and up");
      assertTrue(dab.leftArm[1] > 0.8F, "the other across the face");
      assertTrue(dab.head[0] > 0.4F, "head dropped");

      EmotePose tpose = EmoteAnimator.pose(Emote.TPOSE, 2.5F);
      assertTrue(tpose.rightArm[2] > 1.5F && tpose.leftArm[2] < -1.5F, "arms straight out to the sides");
      assertNull(tpose.rightLeg);

      EmotePose zombie = EmoteAnimator.pose(Emote.ZOMBIE, 3.0F);
      assertEquals(-1.57F, zombie.rightArm[0], 1e-3F, "arms stretched out in front");
      assertEquals(-1.57F, zombie.leftArm[0], 1e-3F);

      EmotePose early = EmoteAnimator.pose(Emote.SPRINKLER, 1.0F);
      EmotePose later = EmoteAnimator.pose(Emote.SPRINKLER, 1.5F);
      assertTrue(Math.abs(early.rightArm[1] - later.rightArm[1]) > 0.2F, "the arm sweeps round in steps");
      assertTrue(later.leftArm[0] < -2.5F, "the other hand is up behind the head");

      float rightKneeUp = (float)((Math.PI / 2.0 + 4.0 * Math.PI) / 5.0); // where the march's sine peaks
      EmotePose rightUp = EmoteAnimator.pose(Emote.MARCH, rightKneeUp);
      assertTrue(rightUp.rightLeg[0] < -0.5F, "one knee high");
      assertTrue(Math.abs(rightUp.leftLeg[0]) < 0.05F, "the other foot down");
   }

   @Test
   void kickbackLiesFaceDownWithTheHandsUpAndTheFeetKickingBehind() {
      EmotePose p = EmoteAnimator.pose(Emote.KICKBACK, 4.0F);
      assertEquals(1.0F, p.prone, 0.01F, "lying flat once it has settled");
      assertTrue(p.rightLeg[0] > 0.0F && p.leftLeg[0] > 0.0F, "both feet up behind");
      assertEquals(1.8F, p.rightLeg[0] + p.leftLeg[0], 1e-3F, "the feet take turns: as one goes up the other comes down");
      assertTrue(p.rightArm[0] < -2.5F && p.leftArm[0] < -2.5F, "the hands up by the head");
      assertTrue(p.head[0] < -0.8F, "the head lifted to look forward");

      float early = EmoteAnimator.pose(Emote.KICKBACK, 0.4F).prone;
      assertTrue(early > 0.0F && early < 1.0F, "lowered gradually, not instantly: " + early);
      assertTrue(EmoteAnimator.pose(Emote.KICKBACK, 7.6F).prone < 1.0F, "and got back up gradually");
   }

   @Test
   void wiggleSwingsTheWaistWhileTheHeadStaysFacingFrontAndTheArmsStayOut() {
      EmotePose p = EmoteAnimator.pose(Emote.WIGGLE, 1.5F);
      assertNotNull(p.torso);
      assertTrue(Math.abs(p.torso[1]) > 0.05F || Math.abs(p.torso[2]) > 0.05F, "the torso twists or tilts");
      assertEquals(-p.torso[1], p.head[1], 1e-4F, "the head turns the other way, so it keeps facing front");
      assertTrue(p.rightArm[2] > 0.7F && p.leftArm[2] < -0.7F, "hands out to the sides");
      assertTrue(biggestChange(p, EmoteAnimator.pose(Emote.WIGGLE, 1.6F)) > 0.05F, "it keeps moving");
   }

   @Test
   void theMovementActuallyMovesOverTime() {
      for (Emote e : Emote.ALL) {
         if (HOLDS.contains(e.id())) {
            continue;
         }

         float difference = biggestChange(EmoteAnimator.pose(e, 2.0F), EmoteAnimator.pose(e, 2.1F));
         assertTrue(difference > 1e-3F, e.id() + " is not frozen");
      }
   }

   @Test
   void aRepeatedPoseIsTheSameAndDoesNotChangeTheRestValues() {
      EmotePose a = EmoteAnimator.pose(Emote.DANCE, 3.3F);
      EmotePose b = EmoteAnimator.pose(Emote.DANCE, 3.3F);
      assertArrayEquals(a.rightArm, b.rightArm);
      assertArrayEquals(RIGHT_REST, new float[]{0.0F, 0.0F, 0.05F}, "the shared rest pose was not modified");
   }

   @Test
   void unknownEmotesHaveNoMovement() {
      assertNull(EmoteAnimator.pose(null, 1.0F));
      assertNull(EmoteAnimator.pose(new Emote("moonwalk", "Moonwalk", 5.0F), 1.0F));
   }

   @Test
   void emotesAreFoundByNameAnyCase() {
      assertEquals(Emote.DANCE, Emote.byId("dance"));
      assertEquals(Emote.DANCE, Emote.byId("  DANCE "));
      assertEquals(Emote.FEETUP, Emote.byId("feetup"));
      assertNull(Emote.byId("nope"));
      assertNull(Emote.byId(null));
   }

   @Test
   void everyEmoteHasAUniqueIdAndAShortLabelThatFitsOnTheWheel() {
      java.util.Set<String> ids = new java.util.HashSet<>();
      for (Emote e : Emote.ALL) {
         assertTrue(ids.add(e.id()), "unique: " + e.id());
         assertTrue(e.id().matches("[a-z0-9_]{1,24}"), "valid for the relay: " + e.id());
         assertTrue(e.durationSeconds() > 1.0F);
         assertTrue(e.label().length() <= 9, "fits a slot: " + e.label());
      }

      assertEquals(23, Emote.ALL.size());
   }
}
