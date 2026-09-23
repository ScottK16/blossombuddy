package org.blossomsuite.core.services.models;

import java.util.ArrayList;
import java.util.List;

public final class RelayModels {
   private RelayModels() {
   }

   public static final class AltState {
      public String altName;
      public String realmName;
      public String updatedAt;
      public List<RelayModels.RelayCooldownEntry> cooldowns = new ArrayList<>();
   }

   public static final class RelayCooldownEntry {
      public String id;
      public String expiresAt;

      public RelayCooldownEntry(String id, String expiresAt) {
         this.id = id;
         this.expiresAt = expiresAt;
      }
   }

   public static final class RelayFetchResponse {
      public String serverNow;
      public String updatedAt;
      public List<RelayModels.AltState> alts = new ArrayList<>();
   }

   public static final class RelayPublishDto {
      public String linkId;
      public String realmName;
      public String altName;
      public List<RelayModels.RelayCooldownEntry> cooldowns = new ArrayList<>();
      public String nonce;
   }
}
