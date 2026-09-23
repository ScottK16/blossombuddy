package org.blossomsuite.core;

import java.util.List;

public record SuiteDungeon(String key, String displayName, List<String> aliases) {
   public SuiteDungeon {
      if (key != null && !key.isBlank()) {
         if (displayName == null || displayName.isBlank()) {
            displayName = key;
         }

         aliases = aliases == null ? List.of() : List.copyOf(aliases);
      } else {
         throw new IllegalArgumentException("key must not be blank");
      }
   }
}
