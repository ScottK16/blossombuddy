package org.blossomsuite.core;

import java.util.List;
import java.util.Locale;

public record SuiteResource(String key, String displayName, List<String> itemNameAliases, List<String> itemIdAliases) {
   public SuiteResource(String key, String displayName, List<String> itemNameAliases) {
      this(key, displayName, itemNameAliases, List.of());
   }

   public SuiteResource {
      if (key != null && !key.isBlank()) {
         if (displayName == null || displayName.isBlank()) {
            displayName = key;
         }

         itemNameAliases = itemNameAliases == null ? List.of() : List.copyOf(itemNameAliases);
         itemIdAliases = itemIdAliases == null ? List.of() : List.copyOf(itemIdAliases);
      } else {
         throw new IllegalArgumentException("key must not be blank");
      }
   }

   public boolean matchesItem(String itemId, String foldedName, String compactName) {
      return this.matchesItemId(itemId) ? true : this.matchesItemName(foldedName, compactName);
   }

   private boolean matchesItemId(String itemId) {
      String normalizedId = normalizeId(itemId);
      if (normalizedId.isBlank()) {
         return false;
      }

      for (String alias : this.itemIdAliases) {
         String normalizedAlias = normalizeId(alias);
         if (!normalizedAlias.isBlank() && normalizedId.equals(normalizedAlias)) {
            return true;
         }
      }

      return false;
   }

   private boolean matchesItemName(String foldedName, String compactName) {
      String folded = foldedName == null ? "" : foldedName.toLowerCase(Locale.ROOT);
      String compact = compactName == null ? "" : compactName.toLowerCase(Locale.ROOT);

      for (String alias : this.itemNameAliases) {
         if (alias != null && !alias.isBlank()) {
            String foldedAlias = alias.trim().toLowerCase(Locale.ROOT);
            String compactAlias = foldedAlias.replace(" ", "");
            if (folded.contains(foldedAlias) || compact.contains(compactAlias)) {
               return true;
            }
         }
      }

      return false;
   }

   private static String normalizeId(String value) {
      return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
   }
}
