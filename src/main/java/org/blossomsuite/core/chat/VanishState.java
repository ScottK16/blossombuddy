package org.blossomsuite.core.chat;

import org.blossomsuite.core.util.TextStrings;
import java.util.Locale;

public final class VanishState {
   public static volatile boolean vanished = false;

   private VanishState() {
   }

   public static void resetForJoin() {
      vanished = false;
   }

   public static void setVanished(boolean value) {
      vanished = value;
   }

   /**
    * "Vanish for BaraGC: ENABLED" / "Vanish for ~Bara: DISABLED" - the server sometimes shows a nicknamed,
    * truncated form of the name (the tilde folds away with the rest of the punctuation), so the name in the
    * line is matched as an exact match OR a prefix either way against the local player's real name.
    */
   public static Boolean parseServerLine(String text, String selfName) {
      if (text == null || text.isBlank() || selfName == null || selfName.isBlank()) {
         return null;
      }

      String folded = TextStrings.foldToLettersDigitsSpace(text).toLowerCase(Locale.ROOT);
      if (!folded.startsWith("vanish for ")) {
         return null;
      }

      boolean enabled;
      String rest;
      if (folded.endsWith(" enabled")) {
         enabled = true;
         rest = folded.substring("vanish for ".length(), folded.length() - " enabled".length());
      } else if (folded.endsWith(" disabled")) {
         enabled = false;
         rest = folded.substring("vanish for ".length(), folded.length() - " disabled".length());
      } else {
         return null;
      }

      String name = rest.trim();
      String self = selfName.trim().toLowerCase(Locale.ROOT);
      if (name.isEmpty() || self.isEmpty()) {
         return null;
      }

      return name.equals(self) || self.startsWith(name) || name.startsWith(self) ? enabled : null;
   }
}
