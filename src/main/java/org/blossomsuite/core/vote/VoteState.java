package org.blossomsuite.core.vote;

import org.blossomsuite.core.SuiteRuntime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class VoteState {
   public static final Map<String, VotePartySnapshot> byServer = new ConcurrentHashMap<>();

   private VoteState() {
   }

   public static String normalize(String server) {
      return server == null ? "" : server.trim().toLowerCase();
   }

   public static VotePartySnapshot get(String serverNameOrKey) {
      String key = normalize(serverNameOrKey);
      return key.isBlank() ? null : byServer.get(key);
   }

   public static VotePartySnapshot getOrCreate(String serverNameOrKey, String displayName, long now) {
      String key = normalize(serverNameOrKey);
      if (key.isBlank()) {
         return null;
      }

      String name = displayNameFor(serverNameOrKey, displayName);
      return byServer.computeIfAbsent(key, k -> new VotePartySnapshot(k, name, 0, 0, false, now));
   }

   public static String displayNameFor(String serverNameOrKey, String displayName) {
      String source = displayName != null && !displayName.isBlank() ? displayName : serverNameOrKey;
      if (source != null && !source.isBlank()) {
         try {
            return SuiteRuntime.profile().serverDisplayName(source);
         } catch (IllegalStateException ignored) {
            return source.trim();
         }
      } else {
         return "Unknown";
      }
   }
}
