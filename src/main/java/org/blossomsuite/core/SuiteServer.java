package org.blossomsuite.core;

import java.util.List;
import java.util.Locale;

public record SuiteServer(String key, String displayName, List<String> aliases) {
   public SuiteServer(String key, String displayName) {
      this(key, displayName, List.of());
   }

   public SuiteServer {
      key = normalizeKey(key);
      if (key.isBlank()) {
         throw new IllegalArgumentException("key must not be blank");
      }

      if (displayName == null || displayName.isBlank()) {
         displayName = defaultDisplayName(key);
      }

      aliases = aliases == null ? List.of() : List.copyOf(aliases);
   }

   public static String normalizeKey(String raw) {
      return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
   }

   public boolean matches(String raw) {
      String normalized = normalizeKey(raw);
      if (normalized.isBlank()) {
         return false;
      }

      if (normalized.equals(this.key)) {
         return true;
      }

      if (normalized.equals(normalizeKey(this.displayName))) {
         return true;
      }

      for (String alias : this.aliases) {
         if (normalized.equals(normalizeKey(alias))) {
            return true;
         }
      }

      return false;
   }

   public static String defaultDisplayName(String raw) {
      String key = normalizeKey(raw);
      if (key.isBlank()) {
         return "Unknown";
      } else {
         return key.length() == 1 ? key.toUpperCase(Locale.ROOT) : key.substring(0, 1).toUpperCase(Locale.ROOT) + key.substring(1);
      }
   }
}
