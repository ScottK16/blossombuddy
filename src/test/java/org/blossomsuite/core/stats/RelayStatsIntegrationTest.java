package org.blossomsuite.core.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.blossomsuite.core.net.SuiteHttp;
import org.blossomsuite.core.util.JsonUtil;
import org.blossomsuite.core.xchat.XChatClient;
import org.junit.jupiter.api.Test;

/**
 * The real usage client talking to a real relay over HTTP. Skipped unless RELAY_STATS_URL and RELAY_BRIDGE_KEY are set. The
 * relay needs BRIDGE_KEY set to the same key, STATS_FILE somewhere disposable, and a stand-in for Mojang's login check
 * (this machine cannot sign in to a real account); see relay/README.md and the helper used when this test was written:
 * a relay whose verifier accepts the name "Alice".
 */
class RelayStatsIntegrationTest {
   private static final HttpClient RAW = HttpClient.newHttpClient();

   private static String admin(String base, String key, String path) throws Exception {
      HttpResponse<String> r = RAW.send(HttpRequest.newBuilder().uri(URI.create(base + path)).header("x-bridge-key", key).header("content-type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
      assertEquals(200, r.statusCode(), path + " " + r.body());
      return r.body();
   }

   @Test
   void theClientCountsItselfNamesItselfAndForgetsItselfThroughARealRelay() throws Exception {
      String url = System.getenv("RELAY_STATS_URL");
      String key = System.getenv("RELAY_BRIDGE_KEY");
      assumeTrue(url != null && !url.isBlank() && key != null && !key.isBlank(), "set RELAY_STATS_URL and RELAY_BRIDGE_KEY to run this against a relay");

      SuiteHttp http = new SuiteHttp(url, "", "");
      XChatClient.Relay overHttp = (path, json) -> {
         try {
            var response = http.postJson(path, json).get(8, TimeUnit.SECONDS);
            return new XChatClient.Reply(response.statusCode(), response.body());
         } catch (Exception e) {
            throw new XChatClient.ChatException(e.toString(), 1000L);
         }
      };

      StatsClientTest.FakeEnv env = new StatsClientTest.FakeEnv();
      env.shareName = true;
      List<String> notices = new ArrayList<>();
      StatsClient client = new StatsClient(overHttp, serverId -> "Alice", env, notices::add, Runnable::run, System::currentTimeMillis);

      long t = System.currentTimeMillis();
      client.step(t); // only the notice
      client.step(t + StatsClient.NOTICE_GRACE_MS + 1);
      assertTrue(env.installId.matches("[0-9a-f]{32}"), env.installId);
      assertEquals("Alice", env.namedAs, "the relay accepted the verified name: " + notices);

      JsonObject summary = JsonUtil.GSON.fromJson(admin(url, key, "/v1/stats/summary"), JsonObject.class);
      assertEquals(1, summary.get("total").getAsInt());
      assertEquals(1, summary.getAsJsonObject("active").get("day").getAsInt());
      assertEquals(1, summary.get("named").getAsInt());
      assertTrue(admin(url, key, "/v1/stats/names").contains("\"Alice\""));

      client.setShareName(false);
      assertTrue(!admin(url, key, "/v1/stats/names").contains("Alice"), "the name is gone");
      assertEquals(1, JsonUtil.GSON.fromJson(admin(url, key, "/v1/stats/summary"), JsonObject.class).get("total").getAsInt(), "still counted");

      client.setEnabled(false);
      assertEquals(0, JsonUtil.GSON.fromJson(admin(url, key, "/v1/stats/summary"), JsonObject.class).get("total").getAsInt(), "and now forgotten");
   }
}
