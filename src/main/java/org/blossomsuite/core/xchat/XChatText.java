package org.blossomsuite.core.xchat;

import java.util.Collection;
import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.ChatFormatting;
/** Cleaning and showing cross-realm chat lines. */
public final class XChatText {
   public static final int MAX_LENGTH = 200;
   /** The relay marks messages that come from the Discord channel with this realm. */
   public static final String DISCORD_REALM = "discord";
   private static final int MAX_NAME_LENGTH = 24;
   private static final Pattern UNWANTED = Pattern.compile("[\\p{Cc}\\p{Cf}\\p{Co}\\p{Cs}§]");
   private static final Pattern SPACES = Pattern.compile("\\s+");

   private XChatText() {
   }

   /**
    * Same rules as the relay: no control or invisible characters, no colour codes, no private-use glyphs (servers draw
    * rank badges and icons with those, so allowing them would let someone fake a staff tag), single spaces. The mod
    * applies this to what it receives too, so it never has to trust the relay to have done it.
    */
   public static String clean(String input, int max) {
      if (input == null) {
         return "";
      }

      String s = SPACES.matcher(UNWANTED.matcher(input).replaceAll(" ")).replaceAll(" ").trim();
      if (s.codePointCount(0, s.length()) > max) {
         s = s.substring(0, s.offsetByCodePoints(0, max)).trim();
      }

      return s;
   }

   /** "cherry" -> "Cherry"; an empty realm stays empty. */
   public static String realmLabel(String realmKey) {
      String k = realmKey == null ? "" : clean(realmKey, 16);
      return k.isEmpty() ? "" : k.substring(0, 1).toUpperCase(Locale.ROOT) + k.substring(1).toLowerCase(Locale.ROOT);
   }

   /** Spirit's light blue: lighter than the Discord tag's blue, so the two can't be mixed up. */
   private static final TextColor LIGHT_BLUE = TextColor.fromRgb(0x5AB4FF);

   /**
    * The colour of a realm's name in chat: Cherry red, Spirit light blue, Lotus green, Tulip yellow. The Discord tag is blue.
    * Anything else is plain gray.
    */
   public static TextColor realmColor(String realmKey) {
      String k = realmKey == null ? "" : clean(realmKey, 16).toLowerCase(Locale.ROOT);
      return switch (k) {
         case "cherry" -> TextColor.fromLegacyFormat(ChatFormatting.RED);
         case "spirit" -> LIGHT_BLUE;
         case "lotus" -> TextColor.fromLegacyFormat(ChatFormatting.GREEN);
         case "tulip" -> TextColor.fromLegacyFormat(ChatFormatting.YELLOW);
         case DISCORD_REALM -> TextColor.fromLegacyFormat(ChatFormatting.BLUE);
         default -> TextColor.fromLegacyFormat(ChatFormatting.GRAY);
      };
   }

   /** How a message looks in chat: {@code [Cherry] Alice: hello}. */
   public static Component format(XChatModels.Message m) {
      MutableComponent line = Component.empty();
      String realm = realmLabel(m.realm);
      if (!realm.isEmpty()) {
         line.append(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal(realm).withStyle(style -> style.withColor(realmColor(m.realm))))
            .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY));
      }

      String name = clean(m.name, MAX_NAME_LENGTH);
      String shown = name.isEmpty() ? "?" : name;
      line.append(GradientText.isValid(m.gradient) ? gradientName(shown, m.gradient) : Component.literal(shown).withStyle(ChatFormatting.WHITE))
         .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
         .append(Component.literal(clean(m.text, MAX_LENGTH)).withStyle(ChatFormatting.WHITE));
      return line;
   }

   /** The name with one colour per character, blended across {@code colors} left to right. */
   private static Component gradientName(String name, String[] colors) {
      MutableComponent result = Component.empty();
      int length = name.codePointCount(0, name.length());
      int i = 0;
      for (int idx = 0; idx < name.length(); ) {
         int cp = name.codePointAt(idx);
         int rgb = GradientText.colorAt(colors, i, length);
         result.append(Component.literal(new String(Character.toChars(cp))).withStyle(style -> style.withColor(TextColor.fromRgb(rgb))));
         idx += Character.charCount(cp);
         i++;
      }
      return result;
   }

   /** Muted by name (any case) or by UUID (with or without dashes). */
   public static boolean isMuted(XChatModels.Message m, Collection<String> muted) {
      if (muted == null || muted.isEmpty()) {
         return false;
      }

      String name = m.name == null ? "" : m.name.toLowerCase(Locale.ROOT);
      String uuid = m.uuid == null ? "" : m.uuid.toLowerCase(Locale.ROOT).replace("-", "");
      for (String entry : muted) {
         String e = entry == null ? "" : entry.trim().toLowerCase(Locale.ROOT).replace("-", "");
         if (!e.isEmpty() && (e.equals(name.replace("-", "")) || e.equals(uuid))) {
            return true;
         }
      }

      return false;
   }
}
