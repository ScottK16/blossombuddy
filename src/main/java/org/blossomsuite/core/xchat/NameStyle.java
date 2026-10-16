package org.blossomsuite.core.xchat;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

/**
 * A player's chosen look: their name blended across a gradient, and a small symbol beside it. Used in cross-realm chat, the player list
 * and over players' heads. Everything that comes from the relay is checked here, so a misbehaving relay can't put anything odd on screen.
 */
public final class NameStyle {
   private NameStyle() {
   }

   /** The symbols the relay can send are single characters from the dingbat and symbol blocks; anything else is dropped. */
   public static String cleanGlyph(String glyph) {
      if (glyph == null || glyph.isEmpty() || glyph.length() > 2) {
         return "";
      }

      for (int i = 0; i < glyph.length(); ) {
         int cp = glyph.codePointAt(i);
         boolean symbol = (cp >= 0x2190 && cp <= 0x27BF) || (cp >= 0x2B00 && cp <= 0x2BFF);
         if (!symbol) {
            return "";
         }

         i += Character.charCount(cp);
      }

      return glyph;
   }

   /** The colours as an array when they are a usable gradient, else null. */
   public static String[] colorsOf(List<String> colors) {
      if (colors == null) {
         return null;
      }

      String[] array = colors.toArray(new String[0]);
      return GradientText.isValid(array) ? array : null;
   }

   /** The name with one colour per character, blended across {@code colors} left to right. */
   public static MutableComponent gradient(String name, String[] colors) {
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

   /** The symbol in the gradient's last colour, or in {@code fallbackRgb} when there is no gradient; empty when there is no symbol. */
   public static Component symbol(String glyph, String[] colors, int fallbackRgb) {
      String g = cleanGlyph(glyph);
      if (g.isEmpty()) {
         return Component.empty();
      }

      int rgb = colors == null ? fallbackRgb : GradientText.colorAt(colors, 0, 2);
      return Component.literal(g).withStyle(style -> style.withColor(TextColor.fromRgb(rgb)));
   }

   /** The name (gradient if there is one, else {@code plain}) followed by the symbol, if any. */
   public static Component styled(String name, MutableComponent plain, String[] colors, String glyph) {
      MutableComponent out = colors != null ? gradient(name, colors) : plain;
      String g = cleanGlyph(glyph);
      if (!g.isEmpty()) {
         out = out.copy().append(Component.literal(" ")).append(symbol(g, colors, 0xFFFFFF));
      }

      return out;
   }
}
