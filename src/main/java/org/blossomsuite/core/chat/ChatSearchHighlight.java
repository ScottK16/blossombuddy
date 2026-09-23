package org.blossomsuite.core.chat;

import java.util.Locale;

/** Splits a line into the part before, the matched part, and the part after the first case-insensitive hit of a search term. */
public final class ChatSearchHighlight {
   public record Parts(String before, String match, String after) {
   }

   private ChatSearchHighlight() {
   }

   /** No match (including a blank query) comes back as everything in {@code before} and empty match/after. */
   public static Parts split(String plain, String query) {
      String text = plain == null ? "" : plain;
      if (query == null || query.isBlank()) {
         return new Parts(text, "", "");
      }

      String needle = query.trim().toLowerCase(Locale.ROOT);
      int idx = text.toLowerCase(Locale.ROOT).indexOf(needle);
      if (idx < 0) {
         return new Parts(text, "", "");
      }

      return new Parts(text.substring(0, idx), text.substring(idx, idx + needle.length()), text.substring(idx + needle.length()));
   }
}
