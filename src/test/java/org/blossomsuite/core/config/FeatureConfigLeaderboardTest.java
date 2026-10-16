package org.blossomsuite.core.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FeatureConfigLeaderboardTest {
   @Test
   void theLeaderboardIsOnByDefaultWithTheNoticePending() {
      FeatureConfig.Leaderboard lb = new FeatureConfig().leaderboard;
      assertTrue(lb.share);
      assertFalse(lb.noticeShown);
      assertFalse(lb.chosen);
   }

   @Test
   void anOffThatWasOnlyTheOldDefaultBecomesOn() {
      FeatureConfig saved = new FeatureConfig();
      saved.leaderboard.share = false; // what builds before this one wrote for everyone
      saved.repair();
      assertTrue(saved.leaderboard.share);
   }

   @Test
   void anOffThePlayerChoseThemselvesIsKept() {
      FeatureConfig saved = new FeatureConfig();
      saved.leaderboard.share = false;
      saved.leaderboard.chosen = true;
      saved.repair();
      assertFalse(saved.leaderboard.share);
   }
}
