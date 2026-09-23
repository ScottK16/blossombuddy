package org.blossomsuite.core.xchat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GradientTextTest {
   @Test
   void needsTwoToFiveFullSixDigitColours() {
      assertFalse(GradientText.isValid(null));
      assertFalse(GradientText.isValid(new String[]{}));
      assertFalse(GradientText.isValid(new String[]{"#ff66aa"}), "only one stop");
      assertFalse(GradientText.isValid(new String[]{"#ff66aa", "#66ccff", "#000000", "#111111", "#222222", "#333333"}), "more than five");
      assertFalse(GradientText.isValid(new String[]{"#ff66aa", "not-a-colour"}));
      assertFalse(GradientText.isValid(new String[]{"#ff66aa", "#fff"}), "short form is not accepted, matching the relay");
      assertTrue(GradientText.isValid(new String[]{"#ff66aa", "#66ccff"}));
      assertTrue(GradientText.isValid(new String[]{"#000000", "#111111", "#222222", "#333333", "#444444"}), "five is the max, and allowed");
   }

   @Test
   void theFirstAndLastCharacterLandExactlyOnTheirStops() {
      String[] colors = {"#ff0000", "#00ff00"};
      assertEquals(0xff0000, GradientText.colorAt(colors, 0, 5));
      assertEquals(0x00ff00, GradientText.colorAt(colors, 4, 5));
   }

   @Test
   void aOneCharacterNameJustGetsTheFirstStop() {
      String[] colors = {"#ff0000", "#00ff00"};
      assertEquals(0xff0000, GradientText.colorAt(colors, 0, 1));
   }

   @Test
   void theMiddleBlendsBetweenTheTwoNearestStops() {
      String[] colors = {"#000000", "#ffffff"};
      int mid = GradientText.colorAt(colors, 2, 5); // halfway
      int r = (mid >> 16) & 0xFF;
      assertTrue(r > 100 && r < 160, "roughly halfway between black and white, was " + Integer.toHexString(mid));
   }

   @Test
   void threeOrMoreStopsAreWalkedInOrder() {
      String[] colors = {"#ff0000", "#00ff00", "#0000ff"};
      assertEquals(0xff0000, GradientText.colorAt(colors, 0, 5), "starts on red");
      assertEquals(0x00ff00, GradientText.colorAt(colors, 2, 5), "hits green exactly in the middle");
      assertEquals(0x0000ff, GradientText.colorAt(colors, 4, 5), "ends on blue");
   }
}
