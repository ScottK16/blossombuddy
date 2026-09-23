package org.blossomsuite.core.services.models;

import java.util.ArrayList;
import java.util.List;

public final class DungeonModels {
   private DungeonModels() {
   }

   public static final class DungeonCooldownRequest {
      public String uid;
      public String clientRunId;
      public String partyRunKey;
      public String runMergeKey;
      public String playerUuid;
      public String playerName;
      public String reporterUuid;
      public String reporterName;
      public String serverKey;
      public String dungeonName;
      public Long startedAtMs;
      public Long cooldownTriggeredAtMs;
      public Long cooldownEndsAtMs;
      public Integer cooldownHours;
      public List<String> partyNames = new ArrayList<>();
      public List<String> lostLivesNames = new ArrayList<>();
      public Integer expectedPartySize;
      public String reason;
      public String nonce;
   }

   public static final class DungeonLostLivesRequest {
      public String uid;
      public String clientRunId;
      public String partyRunKey;
      public String runMergeKey;
      public String playerUuid;
      public String playerName;
      public String reporterUuid;
      public String reporterName;
      public String serverKey;
      public String dungeonName;
      public Long startedAtMs;
      public Long observedAtMs;
      public List<String> partyNames = new ArrayList<>();
      public List<String> lostLivesNames = new ArrayList<>();
      public Integer expectedPartySize;
      public String nonce;
   }

   public static final class DungeonRunFinishRequest {
      public String uid;
      public String clientRunId;
      public String partyRunKey;
      public String playerUuid;
      public String playerName;
      public String reporterUuid;
      public String reporterName;
      public String serverKey;
      public String dungeonName;
      public Long startedAtMs;
      public Long endedAtMs;
      public Long durationMs;
      public String status;
      public Boolean completed;
      public String runMergeKey;
      public List<String> partyNames = new ArrayList<>();
      public List<String> lostLivesNames = new ArrayList<>();
      public Integer expectedPartySize;
      public String nonce;
   }

   public static final class DungeonRunStartRequest {
      public String clientRunId;
      public String partyRunKey;
      public String playerUuid;
      public String playerName;
      public String reporterUuid;
      public String reporterName;
      public String serverKey;
      public String dungeonName;
      public Long startedAtMs;
      public String runMergeKey;
      public List<String> partyNames = new ArrayList<>();
      public Integer expectedPartySize;
      public String nonce;
   }

   public static final class DungeonRunStartResponse {
      public String uid;
      public String runUid;
      public String id;
   }
}
