package org.blossomsuite.core.emote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmoteStateTest {
   private static final UUID ALICE = UUID.randomUUID();
   private static final UUID BOB = UUID.randomUUID();

   @Test
   void aPlayerIsPosedWhileTheirEmotePlaysAndOnlyThen() {
      EmoteState s = new EmoteState();
      assertNull(s.poseFor(ALICE, 1000L));
      s.start(ALICE, Emote.WAVE, 1000L);
      assertNotNull(s.poseFor(ALICE, 3000L));
      assertTrue(s.isActive(ALICE, 3000L));
      assertNull(s.poseFor(BOB, 3000L), "other players are not affected");
   }

   @Test
   void anEmoteEndsByItselfAndIsForgotten() {
      EmoteState s = new EmoteState();
      s.start(ALICE, Emote.WAVE, 0L);
      long over = (long)(Emote.WAVE.durationSeconds() * 1000L);
      assertNotNull(s.poseFor(ALICE, over - 1L));
      assertNull(s.poseFor(ALICE, over));
      assertFalse(s.isActive(ALICE, over));
      assertNull(s.activeFor(ALICE, over - 1L), "and it does not come back");
   }

   @Test
   void stoppingEndsItAtOnce() {
      EmoteState s = new EmoteState();
      s.start(ALICE, Emote.DANCE, 0L);
      s.stop(ALICE);
      assertNull(s.poseFor(ALICE, 500L));
      s.stop(BOB); // nothing to stop: fine
   }

   @Test
   void startingANewEmoteReplacesTheOldOne() {
      EmoteState s = new EmoteState();
      s.start(ALICE, Emote.WAVE, 0L);
      s.start(ALICE, Emote.CHEER, 2000L);
      assertEquals(Emote.CHEER, s.activeFor(ALICE, 2500L).emote());
      assertEquals(2000L, s.activeFor(ALICE, 2500L).startMs());
   }

   @Test
   void badInputIsIgnored() {
      EmoteState s = new EmoteState();
      s.start(null, Emote.WAVE, 0L);
      s.start(ALICE, null, 0L);
      assertNull(s.poseFor(null, 0L));
      assertNull(s.poseFor(ALICE, 0L));
   }

   @Test
   void clearForgetsEveryone() {
      EmoteState s = new EmoteState();
      s.start(ALICE, Emote.WAVE, 0L);
      s.start(BOB, Emote.CLAP, 0L);
      s.clear();
      assertFalse(s.isActive(ALICE, 100L));
      assertFalse(s.isActive(BOB, 100L));
   }
}
