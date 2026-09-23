package org.blossomsuite.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

/** Usage counting is on by default, sharing the username is off, including for config files written before the feature existed. */
class FeatureConfigStatsTest {
   private static final Gson GSON = new GsonBuilder().create();

   @Test
   void aBrandNewConfigCountsAnonymouslyAndSharesNoName() {
      FeatureConfig.Stats s = new FeatureConfig.Stats();
      assertTrue(s.enabled);
      assertFalse(s.shareName);
      assertEquals("", s.installId);
      assertFalse(s.noticeShown);
      assertEquals("", s.namedAs);
   }

   @Test
   void aConfigFileFromBeforeTheFeatureExistedGetsTheSameDefaults() {
      FeatureConfig loaded = GSON.fromJson("{\"relay\":{\"url\":\"\",\"share\":true},\"xchat\":{\"enabled\":true}}", FeatureConfig.class);
      assertTrue(loaded.stats.enabled, "counting is on for people who already had a config");
      assertFalse(loaded.stats.shareName);
   }

   @Test
   void aFileWithOnlySomeStatsFieldsKeepsTheOthersAtTheirDefaults() {
      FeatureConfig loaded = GSON.fromJson("{\"stats\":{\"installId\":\"" + "a".repeat(32) + "\"}}", FeatureConfig.class);
      assertTrue(loaded.stats.enabled);
      assertFalse(loaded.stats.shareName);
      assertEquals("a".repeat(32), loaded.stats.installId);
   }

   @Test
   void aPlayersChoiceToTurnItOffIsKept() {
      FeatureConfig loaded = GSON.fromJson("{\"stats\":{\"enabled\":false}}", FeatureConfig.class);
      assertFalse(loaded.stats.enabled);
   }
}
