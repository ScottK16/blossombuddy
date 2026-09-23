package org.blossomsuite.core.net;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SuiteHttpTest {
   @Test
   void theOnlyAllowedRequestsAreVotePartyChatAndUsageCounts() {
      assertTrue(SuiteHttp.isAllowedPath("/v1/vote/state"));
      assertTrue(SuiteHttp.isAllowedPath("/v1/chat/send"));
      assertTrue(SuiteHttp.isAllowedPath("/v1/stats/ping"));
      assertTrue(SuiteHttp.isAllowedPath("/v1/presence/update"));
      assertTrue(SuiteHttp.isAllowedPath("/v1/emote/play"));
      assertTrue(SuiteHttp.isAllowedPath("/v1/mapart/get"));
      for (String path : new String[]{"/v1/donations", "/v1/dungeons/runs/start", "/v1/relay/cooldowns", "/v1/manifest", "/v1/mod/info", "/", "", null}) {
         assertFalse(SuiteHttp.isAllowedPath(path), String.valueOf(path));
      }
   }

   @Test
   void withNoAddressNothingIsEnabled() {
      assertFalse(new SuiteHttp("", "", "").enabled());
      assertFalse(new SuiteHttp("   ", "", "").enabled(), "an address of only spaces is no address");
   }

   @Test
   void theAddressCanBeChangedAtRuntime() {
      SuiteHttp http = new SuiteHttp("", "", "");
      http.setBaseUrl("https://relay.example.org/");
      assertTrue(http.enabled());
      http.setBaseUrl("");
      assertFalse(http.enabled());
      http.setBaseUrl(null);
      assertFalse(http.enabled());
   }

   @Test
   void otherServicesAreRefusedWithoutTouchingTheNetworkEvenWhenAnAddressIsSet() {
      SuiteHttp http = new SuiteHttp("https://relay.example.invalid", "", "");
      assertTrue(http.postJson("/v1/donations", "{}").isCompletedExceptionally(), "donations");
      assertTrue(http.postJson("/v1/dungeons/runs/start", "{}").isCompletedExceptionally(), "dungeon runs");
      assertTrue(http.get("/v1/manifest", null).isCompletedExceptionally(), "manifest");
   }
}
