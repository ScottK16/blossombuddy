package org.blossomsuite.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.blossomsuite.core.net.SuiteHttp;
import org.blossomsuite.core.services.models.VoteModels;
import org.blossomsuite.core.vote.VotePartySnapshot;
import org.junit.jupiter.api.Test;

/**
 * Runs the mod's real vote-sync class against a real relay over HTTP. Skipped unless RELAY_URL is set, e.g.
 * <pre>cd relay &amp;&amp; PORT=8791 node server.js     (then)     RELAY_URL=http://127.0.0.1:8791 ./gradlew test</pre>
 */
class RelayIntegrationTest {
   @Test
   void theModsVoteSyncTalksToTheRelayAndReadsTheReply() throws Exception {
      String url = System.getenv("RELAY_URL");
      assumeTrue(url != null && !url.isBlank(), "set RELAY_URL to run this against a relay");

      SuiteHttp http = new SuiteHttp(url, "", "");
      CompletableFuture<VoteModels.VoteStateSyncResponse> received = new CompletableFuture<>();
      VoteStateService service = new VoteStateService(http, () -> true, null, Runnable::run, received::complete, (a, b) -> {}, () -> 0L);

      VotePartySnapshot cherry = new VotePartySnapshot("cherry", "Cherry", 115, 150, false, System.currentTimeMillis());
      service.syncCurrentServerSnapshotIfAllowed("cherry", cherry);

      VoteModels.VoteStateSyncResponse response = received.get(10, TimeUnit.SECONDS);
      assertNotNull(response.servers);
      VoteModels.VotePartySnapshotDto found = response.servers.stream().filter(s -> "cherry".equals(s.serverKey)).findFirst().orElseThrow();
      assertEquals(115, found.current);
      assertEquals(150, found.max);
      assertEquals("Cherry", found.displayName);
      assertTrue(found.seenAt != null && found.seenAt > 0);
   }
}
