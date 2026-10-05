package org.blossomsuite.core.chat;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
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

   public static Component tryRewrite(Component original) {
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
         MutableComponent result = Component.empty();
         Component item = sliceStyled(original, m.start("item"), m.end("item"));
         Component source = sourceText(original, trimmed, m);
         if (isPlayer) {
            Component player = sliceStyled(original, m.start("player"), m.end("player"));
            result.append(player);
         } else {
            int youStart = trimmed.indexOf("You");
            if (youStart < 0) {
               return null;
            }

            Component you = sliceStyled(original, youStart, youStart + 3);
            result.append(you);
         }

         result.append(Component.literal(" got ").withStyle(ChatFormatting.WHITE));
         result.append(item);
         result.append(Component.literal(" from ").withStyle(ChatFormatting.WHITE));
         result.append(source);
         result.append(Component.literal("!").withStyle(ChatFormatting.WHITE));
         return result;
      } else {
         return null;
      }
   }

   public static boolean isCrateMessage(Component original) {
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

   private static Component sourceText(Component original, String trimmed, Matcher m) {
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

   private static Component sliceStyled(Component original, int start, int end) {
      MutableComponent out = Component.empty();
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
               out.append(Component.literal(part).setStyle(style));
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
