package org.blossomsuite.core.hud;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Consumer;
import org.blossomsuite.core.config.FeatureConfig;
import org.junit.jupiter.api.Test;

class ScoreboardHudTest {
   @Test
   void theBoxMatchesVanillasSidebarLayout() {
      // vanilla: bottom = H/2 + lines*9/3, x from W-widest-5 to W-1, top = bottom - lines*9 - 10
      assertArrayEquals(new int[]{375, 89, 479, 153}, ScoreboardHud.box(100, 6, 480, 270));
      assertArrayEquals(new int[]{455, 125, 479, 135}, ScoreboardHud.box(20, 0, 480, 270), "an empty scoreboard is just the title bar");
   }

   @Test
   void theBoxIsAlwaysWidestPlusFourWide() {
      for (int widest : new int[]{10, 57, 133}) {
         int[] b = ScoreboardHud.box(widest, 5, 640, 360);
         assertEquals(widest + 4, b[2] - b[0]);
      }
   }

   @Test
   void theBoxHeightIsNineRowsPerLinePlusTheTitle() {
      for (int lines : new int[]{0, 1, 7, 15}) {
         int[] b = ScoreboardHud.box(80, lines, 640, 360);
         assertEquals(lines * 9 + 10, b[3] - b[1]);
      }
   }

   private static boolean customisedAfter(Consumer<FeatureConfig.Scoreboard> change) {
      FeatureConfig.Scoreboard sb = new FeatureConfig.Scoreboard();
      change.accept(sb);
      return sb.isCustomized();
   }

   @Test
   void aFreshScoreboardIsLeftToTheGameOrClient() {
      assertFalse(new FeatureConfig.Scoreboard().isCustomized());
   }

   @Test
   void anySingleChangeTakesOverTheDrawing() {
      assertTrue(customisedAfter(s -> s.hidden = true));
      assertTrue(customisedAfter(s -> s.showNumbers = false));
      assertTrue(customisedAfter(s -> s.textShadow = true));
      assertTrue(customisedAfter(s -> s.background = false));
      assertTrue(customisedAfter(s -> s.backgroundOpacity = 0.5F));
      assertTrue(customisedAfter(s -> s.border = true));
      assertTrue(customisedAfter(s -> s.rounded = true));
      assertTrue(customisedAfter(s -> s.panel.scale = 0.8F));
      assertTrue(customisedAfter(s -> s.panel.x = 0.4F));
      assertTrue(customisedAfter(s -> s.panel.y = 0.4F));
   }

   @Test
   void resettingHandsTheScoreboardBack() {
      FeatureConfig.Scoreboard sb = new FeatureConfig.Scoreboard();
      sb.hidden = true;
      sb.panel.x = 0.3F;
      assertTrue(sb.isCustomized());
      assertFalse(new FeatureConfig.Scoreboard().isCustomized());
   }

   @Test
   void anotherModOwnsTheScoreboardWhenASidebarExistsButOurHookWasNeverReached() {
      assertTrue(ScoreboardHud.isForeign(true, false), "sidebar present, hook skipped: someone else cancelled the draw");
      assertFalse(ScoreboardHud.isForeign(true, true), "we were called: the game (or we) draw it");
      assertFalse(ScoreboardHud.isForeign(false, false), "no sidebar at all is not a takeover");
      assertFalse(ScoreboardHud.isForeign(false, true));
   }
}
