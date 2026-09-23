package org.blossomsuite.core.chat;

import org.blossomsuite.core.util.TextStrings;
import java.util.Locale;

public final class PartyChatState {
   public static volatile boolean partyChatEnabled = false;

   private PartyChatState() {
   }

   public static void resetForJoin() {
      partyChatEnabled = false;
   }

   public static void setPartyChatEnabled(boolean enabled) {
      partyChatEnabled = enabled;
   }

   public static Boolean parseOutgoingCommand(String command) {
      String[] parts = commandParts(command);
      if (parts.length < 3) {
         return null;
      } else if (!"party".equals(parts[0]) && !"p".equals(parts[0])) {
         return null;
      } else if (!"chat".equals(parts[1])) {
         return null;
      } else if ("on".equals(parts[2]) || "enable".equals(parts[2]) || "enabled".equals(parts[2])) {
         return true;
      } else {
         return !"off".equals(parts[2]) && !"disable".equals(parts[2]) && !"disabled".equals(parts[2]) ? null : false;
      }
   }

   public static boolean isOutgoingPartyExitCommand(String command) {
      String[] parts = commandParts(command);
      if (parts.length < 2) {
         return false;
      }

      if (!"party".equals(parts[0]) && !"p".equals(parts[0])) {
         return false;
      }

      return switch (parts[1]) {
         case "leave", "quit", "disband" -> true;
         default -> false;
      };
   }

   public static Boolean parseServerLine(String text) {
      if (text != null && !text.isBlank()) {
         String folded = TextStrings.foldToLettersDigitsSpace(text).toLowerCase(Locale.ROOT);
         if (!folded.contains("party chat")) {
            return null;
         } else if (folded.contains(" turned on") || folded.contains(" set to on") || folded.contains(" chat on") || folded.contains(" enabled")) {
            return true;
         } else {
            return !folded.contains(" turned off") && !folded.contains(" set to off") && !folded.contains(" chat off") && !folded.contains(" disabled")
               ? null
               : false;
         }
      } else {
         return null;
      }
   }

   private static String[] commandParts(String command) {
      if (command != null && !command.isBlank()) {
         String s = command.trim();
         if (s.startsWith("/")) {
            s = s.substring(1).trim();
         }

         String folded = TextStrings.foldToLettersDigitsSpace(s);
         return folded.isBlank() ? new String[0] : folded.toLowerCase(Locale.ROOT).split("\\s+");
      } else {
         return new String[0];
      }
   }
}
