package org.blossomsuite.core.util;

import java.util.Locale;
import java.util.Optional;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

public final class TextUtil {
   private TextUtil() {
   }

   public static String stripLegacySectionCodes(String s) {
      return TextStrings.stripLegacySectionCodes(s);
   }

   public static Text stripLegacySectionCodes(Text t) {
      if (t == null) {
         return Text.empty();
      }

      MutableText out = Text.empty();
      t.visit((style, s) -> {
         String clean = stripLegacySectionCodes(s);
         if (!clean.isEmpty()) {
            out.append(Text.literal(clean).setStyle(style));
         }

         return Optional.empty();
      }, Style.EMPTY);
      return out;
   }

   public static String stripSectionSignsOnly(String s) {
      return TextStrings.stripSectionSignsOnly(s);
   }

   public static String normalizeSmallCaps(String s) {
      return s != null && !s.isBlank()
         ? s.trim()
            .toLowerCase(Locale.ROOT)
            .replace('\u1d00', 'a')
            .replace('\u0299', 'b')
            .replace('\u1d04', 'c')
            .replace('\u1d05', 'd')
            .replace('\u1d07', 'e')
            .replace('\u026a', 'i')
            .replace('\u1d0b', 'k')
            .replace('\u029f', 'l')
            .replace('\u1d0d', 'm')
            .replace('\u0274', 'n')
            .replace('\u1d0f', 'o')
            .replace('\u1d18', 'p')
            .replace('\u0280', 'r')
            .replace('\ua731', 's')
            .replace('\u1d1b', 't')
            .replace('\u1d1c', 'u')
            .replace('\u1d20', 'v')
            .replace('\u1d21', 'w')
            .replace('\u028f', 'y')
         : "";
   }

   public static Text gradient(String s, int startArgb, int endArgb, boolean bold) {
      int len = s.length();
      int sr = startArgb >> 16 & 0xFF;
      int sg = startArgb >> 8 & 0xFF;
      int sb = startArgb & 0xFF;
      int er = endArgb >> 16 & 0xFF;
      int eg = endArgb >> 8 & 0xFF;
      int eb = endArgb & 0xFF;
      MutableText out = Text.empty();

      for (int i = 0; i < len; i++) {
         float t = len == 1 ? 0.0F : (float)i / (len - 1);
         int r = (int)(sr + t * (er - sr));
         int g = (int)(sg + t * (eg - sg));
         int b = (int)(sb + t * (eb - sb));
         int rgb = r << 16 | g << 8 | b;
         Style style = Style.EMPTY.withColor(rgb);
         if (bold) {
            style = style.withBold(true);
         }

         out.append(Text.literal(String.valueOf(s.charAt(i))).setStyle(style));
      }

      return out;
   }

   public static String fmtMoney(double v) {
      return TextStrings.fmtMoney(v);
   }

   public static String fmtExp(double v) {
      return TextStrings.fmtExp(v);
   }

   public static String fmtRate(double perHr) {
      return TextStrings.fmtRate(perHr);
   }

   public static String fmtStopwatch(long ms) {
      return TextStrings.fmtStopwatch(ms);
   }

   public static String simplify(String s) {
      return TextStrings.simplify(s);
   }

   public static String foldName(String s) {
      return TextStrings.foldName(s);
   }

   public static String foldToLettersDigitsSpace(String s) {
      return TextStrings.foldToLettersDigitsSpace(s);
   }
}
