package org.blossomsuite.core.chat;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class CrateMessageFormatter {
   private static final String CRATES_PREFIX = "^Crates\\s*(?:\\u00BB|\\u00C2\\u00BB|\\u00C3\\u0082\\u00C2\\u00BB)\\s*";
   private static final Pattern CRATE_PATTERN = Pattern.compile(
      "^Crates\\s*(?:\\u00BB|\\u00C2\\u00BB|\\u00C3\\u0082\\u00C2\\u00BB)\\s*Player\\s+(?<player>.+?)\\s+just got the\\s+(?<item>.+?)\\s+reward from the\\s+(?<crate>.+?)(?:\\s*-\\s*(?<suffix>.+?))?!?$",
      2
   );
   private static final Pattern YOU_PATTERN = Pattern.compile(
      "^Crates\\s*(?:\\u00BB|\\u00C2\\u00BB|\\u00C3\\u0082\\u00C2\\u00BB)\\s*You got the\\s+(?<item>.+?)\\s+reward from the\\s+(?<crate>.+?)(?:\\s*-\\s*(?<suffix>.+?))?!?$",
      2
   );

   private CrateMessageFormatter() {
   }

   public static Text tryRewrite(Text original) {
      if (original == null) {
         return null;
      }

      String plain = original.getString();
      if (plain != null && !plain.isBlank()) {
         String trimmed = plain.trim();
         Matcher playerMatch = CRATE_PATTERN.matcher(trimmed);
         Matcher youMatch = YOU_PATTERN.matcher(trimmed);
         boolean isPlayer = playerMatch.matches();
         boolean isYou = youMatch.matches();
         if (!isPlayer && !isYou) {
            return null;
         }

         Matcher m = isPlayer ? playerMatch : youMatch;
         MutableText result = Text.empty();
         Text item = sliceStyled(original, m.start("item"), m.end("item"));
         Text source = sourceText(original, trimmed, m);
         if (isPlayer) {
            Text player = sliceStyled(original, m.start("player"), m.end("player"));
            result.append(player);
         } else {
            int youStart = trimmed.indexOf("You");
            if (youStart < 0) {
               return null;
            }

            Text you = sliceStyled(original, youStart, youStart + 3);
            result.append(you);
         }

         result.append(Text.literal(" got ").formatted(Formatting.WHITE));
         result.append(item);
         result.append(Text.literal(" from ").formatted(Formatting.WHITE));
         result.append(source);
         result.append(Text.literal("!").formatted(Formatting.WHITE));
         return result;
      } else {
         return null;
      }
   }

   public static boolean isCrateMessage(Text original) {
      if (original == null) {
         return false;
      } else {
         String plain = original.getString();
         if (plain != null && !plain.isBlank()) {
            String trimmed = plain.trim();
            return CRATE_PATTERN.matcher(trimmed).matches() || YOU_PATTERN.matcher(trimmed).matches();
         } else {
            return false;
         }
      }
   }

   private static Text sourceText(Text original, String trimmed, Matcher m) {
      if (hasGroup(m, "suffix")) {
         int suffixStart = m.start("suffix");
         int suffixEnd = m.end("suffix");

         while (suffixEnd > suffixStart && trimmed.charAt(suffixEnd - 1) == '!') {
            suffixEnd--;
         }

         return sliceStyled(original, suffixStart, suffixEnd);
      } else {
         int crateStart = m.start("crate");
         int crateEnd = m.end("crate");
         String cratePlain = trimmed.substring(crateStart, crateEnd);

         while (crateEnd > crateStart && trimmed.charAt(crateEnd - 1) == '!') {
            cratePlain = trimmed.substring(crateStart, --crateEnd);
         }

         String lower = cratePlain.toLowerCase();
         boolean keepFullName = lower.equals("vote crate") || lower.equals("spawner crate");
         if (!keepFullName && lower.endsWith(" crate")) {
            crateEnd -= 6;
         }

         return sliceStyled(original, crateStart, crateEnd);
      }
   }

   private static boolean hasGroup(Matcher m, String name) {
      try {
         return m.start(name) >= 0 && m.end(name) >= 0;
      } catch (IllegalArgumentException | IllegalStateException ex) {
         return false;
      }
   }

   private static Text sliceStyled(Text original, int start, int end) {
      MutableText out = Text.empty();
      if (start >= end) {
         return out;
      }

      int[] cursor = new int[]{0};
      original.visit((style, segment) -> {
         if (segment != null && !segment.isEmpty()) {
            int segStart = cursor[0];
            int segEnd = segStart + segment.length();
            int from = Math.max(start, segStart);
            int to = Math.min(end, segEnd);
            if (from < to) {
               String part = segment.substring(from - segStart, to - segStart);
               out.append(Text.literal(part).setStyle(style));
            }

            cursor[0] = segEnd;
            return Optional.empty();
         } else {
            return Optional.empty();
         }
      }, Style.EMPTY);
      return out;
   }
}
