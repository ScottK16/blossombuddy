package org.blossomsuite.core.emote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmoteElapsedTest {
   @Test
   void timesReadAsMinutesAndSecondsAndOnlyShowHoursWhenThereAreSome() {
      assertEquals("0:00", EmoteState.formatElapsed(0L));
      assertEquals("0:09", EmoteState.formatElapsed(9_400L));
      assertEquals("1:05", EmoteState.formatElapsed(65_000L));
      assertEquals("59:59", EmoteState.formatElapsed(3_599_000L));
      assertEquals("1:02:03", EmoteState.formatElapsed(3_723_000L));
      assertEquals("0:00", EmoteState.formatElapsed(-5_000L), "a clock that jumped back never shows a negative time");
   }

   @Test
   void everyoneEmotingIsListedLongestFirstAndFinishedOnesAreLeftOut() {
      EmoteState s = new EmoteState();
      UUID early = UUID.randomUUID();
      UUID late = UUID.randomUUID();
      UUID done = UUID.randomUUID();
      s.startLooping(late, Emote.DANCE, 5_000L);
      s.startLooping(early, Emote.WAVE, 1_000L);
      s.start(done, Emote.WAVE, 0L); // plays once, and is over by the time we look
      List<Map.Entry<UUID, EmoteState.Active>> all = s.allActive(10_000L + (long)(Emote.WAVE.durationSeconds() * 1000L));
      assertEquals(2, all.size());
      assertEquals(early, all.get(0).getKey());
      assertEquals(late, all.get(1).getKey());
      assertTrue(all.get(0).getValue().loop());
   }

   @Test
   void theMoneySwipeIsAnEmoteLikeAnyOtherAndKeepsGoing() {
      assertEquals(Emote.MONEY, Emote.byId("MONEY"));
      assertTrue(EmoteAnimator.pose(Emote.MONEY, 300.0F, true) != null);
      assertTrue(Emote.ALL.contains(Emote.MONEY));
   }
}
