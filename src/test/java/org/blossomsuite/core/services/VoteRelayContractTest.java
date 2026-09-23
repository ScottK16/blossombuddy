package org.blossomsuite.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.blossomsuite.core.services.models.VoteModels;
import org.blossomsuite.core.util.JsonUtil;
import org.junit.jupiter.api.Test;

/** The mod and the relay (relay/server.js) agree on the JSON they exchange. */
class VoteRelayContractTest {
   // exactly the shape relay/server.js produces
   private static final String RELAY_RESPONSE = "{\"serverNow\":\"2026-09-21T13:00:00.000Z\",\"updatedAt\":\"2026-09-21T13:00:00.000Z\",\"servers\":["
      + "{\"serverKey\":\"cherry\",\"displayName\":\"Cherry\",\"current\":115,\"max\":150,\"partyOngoing\":false,\"seenAt\":1758459600000,"
      + "\"countdownActive\":false,\"countdownSecondsRemaining\":null,\"expectedTriggerAt\":null},"
      + "{\"serverKey\":\"tulip\",\"displayName\":\"Tulip\",\"current\":150,\"max\":150,\"partyOngoing\":false,\"seenAt\":1758459601000,"
      + "\"countdownActive\":true,\"countdownSecondsRemaining\":28.5,\"expectedTriggerAt\":1758459628500}]}";

   @Test
   void theModUnderstandsWhatTheRelaySends() {
      VoteModels.VoteStateSyncResponse r = JsonUtil.GSON.fromJson(RELAY_RESPONSE, VoteModels.VoteStateSyncResponse.class);
      assertEquals(2, r.servers.size());

      VoteModels.VotePartySnapshotDto cherry = r.servers.get(0);
      assertEquals("cherry", cherry.serverKey);
      assertEquals("Cherry", cherry.displayName);
      assertEquals(115, cherry.current);
      assertEquals(150, cherry.max);
      assertFalse(cherry.partyOngoing);
      assertEquals(1758459600000L, cherry.seenAt);
      assertFalse(cherry.countdownActive);
      assertNull(cherry.countdownSecondsRemaining);
      assertNull(cherry.expectedTriggerAt);

      VoteModels.VotePartySnapshotDto tulip = r.servers.get(1);
      assertTrue(tulip.countdownActive);
      assertEquals(28.5F, tulip.countdownSecondsRemaining);
      assertEquals(1758459628500L, tulip.expectedTriggerAt);
   }

   @Test
   void whatTheModSendsHasTheFieldsTheRelayChecks() {
      VoteModels.VotePartySnapshotDto dto = new VoteModels.VotePartySnapshotDto();
      dto.serverKey = "cherry";
      dto.displayName = "Cherry";
      dto.current = 115;
      dto.max = 150;
      dto.partyOngoing = false;
      dto.seenAt = 1L;
      dto.countdownActive = false;
      VoteModels.VoteStateSyncRequest req = new VoteModels.VoteStateSyncRequest();
      req.snapshot = dto;
      req.nonce = "n";

      JsonObject snapshot = JsonParser.parseString(JsonUtil.GSON.toJson(req)).getAsJsonObject().getAsJsonObject("snapshot");
      assertEquals("cherry", snapshot.get("serverKey").getAsString());
      assertEquals(115, snapshot.get("current").getAsInt());
      assertEquals(150, snapshot.get("max").getAsInt());
      assertTrue(snapshot.has("partyOngoing"));
      assertTrue(snapshot.has("countdownActive"));
   }

   @Test
   void thePayloadCarriesNoPlayerIdentity() {
      String json = JsonUtil.GSON.toJson(new VoteModels.VoteStateSyncRequest());
      for (String forbidden : new String[]{"uuid", "playerName", "username", "account"}) {
         assertFalse(json.toLowerCase().contains(forbidden.toLowerCase()), forbidden);
      }

      for (java.lang.reflect.Field f : VoteModels.VotePartySnapshotDto.class.getFields()) {
         String n = f.getName().toLowerCase();
         assertFalse(n.contains("uuid") || n.contains("player") || n.contains("user") || n.contains("account"), f.getName());
      }
   }
}
