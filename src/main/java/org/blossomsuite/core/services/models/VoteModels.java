package org.blossomsuite.core.services.models;

import java.util.ArrayList;
import java.util.List;

public final class VoteModels {
   private VoteModels() {
   }

   public static final class VotePartySnapshotDto {
      public String serverKey;
      public String displayName;
      public Integer current;
      public Integer max;
      public Boolean partyOngoing;
      public Long seenAt;
      public Boolean countdownActive;
      public Float countdownSecondsRemaining;
      public Long expectedTriggerAt;
   }

   public static final class VoteStateSyncRequest {
      public VoteModels.VotePartySnapshotDto snapshot;
      public String nonce;
   }

   public static final class VoteStateSyncResponse {
      public String serverNow;
      public String updatedAt;
      public List<VoteModels.VotePartySnapshotDto> servers = new ArrayList<>();
   }
}
