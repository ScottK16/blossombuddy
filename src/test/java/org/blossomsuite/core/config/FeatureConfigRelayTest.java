package org.blossomsuite.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FeatureConfigRelayTest {
   @Test
   void sharingIsOffByDefaultUntilThePlayerOptsIn() {
      FeatureConfig.Relay r = new FeatureConfig.Relay();
      assertFalse(r.share);
      assertFalse(r.noticeShown);
      assertEquals("", r.url);
   }

   @Test
   void thePlayersOwnAddressWinsOtherwiseTheBuiltInOneIsUsed() {
      assertEquals("https://built.in", FeatureConfig.effectiveRelayUrl("", "https://built.in"));
      assertEquals("https://built.in", FeatureConfig.effectiveRelayUrl("   ", "  https://built.in "));
      assertEquals("https://mine.example", FeatureConfig.effectiveRelayUrl("  https://mine.example ", "https://built.in"));
      assertEquals("", FeatureConfig.effectiveRelayUrl(null, null), "nothing configured anywhere means no relay");
   }

   @Test
   void theBuiltInRelayIsSecureAndDoesNotSwitchOnTheOldRemoteConfig() {
      assertEquals("https://relay.blossombuddy.site", FeatureConfig.BUILT_IN_RELAY_URL);
      assertTrue(FeatureConfig.BUILT_IN_RELAY_URL.startsWith("https://"));
      assertEquals("", org.blossomsuite.BlossomBuddyProfile.INSTANCE.apiBaseUrl(), "the profile keeps no backend, so remote config stays off");
      assertFalse(org.blossomsuite.BlossomBuddyProfile.INSTANCE.hasBackend());
   }
}
