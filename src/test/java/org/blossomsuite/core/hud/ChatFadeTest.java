package org.blossomsuite.core.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChatFadeTest {
   @Test
   void fullyShownWhileRecentAndWhenFadingIsOff() {
      assertEquals(1.0F, ChatFade.alpha(true, 10_000L, 0L));
      assertEquals(1.0F, ChatFade.alpha(true, 10_000L, 10_000L));
      assertEquals(1.0F, ChatFade.alpha(false, 10_000L, 999_999L), "with fading switched off it never fades");
   }

   @Test
   void fadesOverTheFadeTimeThenIsGone() {
      float half = ChatFade.alpha(true, 10_000L, 10_000L + ChatFade.FADE_MS / 2);
      assertTrue(half > 0.4F && half < 0.6F, "halfway through the fade: " + half);
      assertEquals(0.0F, ChatFade.alpha(true, 10_000L, 10_000L + ChatFade.FADE_MS));
      assertEquals(0.0F, ChatFade.alpha(true, 10_000L, 1_000_000L));
   }

   @Test
   void tintScalesOnlyTheAlpha() {
      assertEquals(0x80F48FB1, ChatFade.tint(0xFFF48FB1, 0.5F) | 0, "alpha halved, colour kept");
      assertEquals(0x00F48FB1, ChatFade.tint(0xFFF48FB1, 0.0F));
      assertEquals(0xFFF48FB1, ChatFade.tint(0xFFF48FB1, 2.0F), "never brighter than the original");
   }
}
