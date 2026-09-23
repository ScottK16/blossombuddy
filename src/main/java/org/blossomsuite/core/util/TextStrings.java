package org.blossomsuite.core.util;

public final class TextStrings {
   private TextStrings() {
   }

   public static String stripLegacySectionCodes(String s) {
      if (s != null && !s.isEmpty()) {
         StringBuilder out = new StringBuilder(s.length());

         for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == 167) {
               if (i + 1 < s.length()) {
                  i++;
               }
            } else {
               out.append(c);
            }
         }

         return out.toString();
      } else {
         return "";
      }
   }

   public static String stripSectionSignsOnly(String s) {
      if (s != null && !s.isEmpty()) {
         boolean has = false;

         for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == 167 || c == 194) {
               has = true;
               break;
            }
         }

         if (!has) {
            return s;
         }

         StringBuilder out = new StringBuilder(s.length());

         for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != 167 && c != 194) {
               out.append(c);
            }
         }

         return out.toString();
      } else {
         return "";
      }
   }

   public static String fmtMoney(double v) {
      return String.format("%,.2f", v);
   }

   public static String fmtExp(double v) {
      return String.format("%,.2f", v);
   }

   public static String fmtRate(double perHr) {
      double a = Math.abs(perHr);
      if (a >= 1000000.0) {
         return String.format("%.2fM/hr", perHr / 1000000.0);
      } else {
         return a >= 1000.0 ? String.format("%.1fk/hr", perHr / 1000.0) : String.format("%,.2f/hr", perHr);
      }
   }

   public static String fmtStopwatch(long ms) {
      long totalSec = Math.max(0L, ms / 1000L);
      long h = totalSec / 3600L;
      long m = totalSec % 3600L / 60L;
      long s = totalSec % 60L;
      return h > 0L ? String.format("%d:%02d:%02d", h, m, s) : String.format("%02d:%02d", m, s);
   }

   public static String simplify(String s) {
      if (s == null) {
         return "";
      }

      s = stripSectionSignsOnly(s);
      StringBuilder out = new StringBuilder(s.length());
      boolean lastWasSpace = false;

      for (int i = 0; i < s.length(); i++) {
         char c = s.charAt(i);
         boolean keep = c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9';
         if (keep) {
            out.append(Character.toLowerCase(c));
            lastWasSpace = false;
         } else if (!lastWasSpace) {
            out.append(' ');
            lastWasSpace = true;
         }
      }

      int len = out.length();
      if (len > 0 && out.charAt(len - 1) == ' ') {
         out.setLength(len - 1);
      }

      return out.toString();
   }

   public static String foldName(String s) {
      if (s == null) {
         return "";
      }

      s = stripSectionSignsOnly(s);
      StringBuilder out = new StringBuilder(s.length());
      boolean lastSpace = false;

      for (int i = 0; i < s.length(); i++) {
         int cp = s.codePointAt(i);
         if (Character.charCount(cp) == 2) {
            i++;
         }

         if (Character.isLetterOrDigit(cp)) {
            out.appendCodePoint(Character.toLowerCase(cp));
            lastSpace = false;
         } else if (!lastSpace) {
            out.append(' ');
            lastSpace = true;
         }
      }

      int n = out.length();
      if (n > 0 && out.charAt(n - 1) == ' ') {
         out.setLength(n - 1);
      }

      return out.toString();
   }

   public static String foldToLettersDigitsSpace(String s) {
      if (s == null) {
         return "";
      }

      s = stripSectionSignsOnly(s);
      StringBuilder out = new StringBuilder(s.length());
      boolean lastSpace = false;

      for (int i = 0; i < s.length(); i++) {
         int cp = s.codePointAt(i);
         if (Character.charCount(cp) == 2) {
            i++;
         }

         if (Character.isLetterOrDigit(cp)) {
            out.appendCodePoint(Character.toLowerCase(cp));
            lastSpace = false;
         } else if (!lastSpace) {
            out.append(' ');
            lastSpace = true;
         }
      }

      int n = out.length();
      if (n > 0 && out.charAt(n - 1) == ' ') {
         out.setLength(n - 1);
      }

      return out.toString();
   }
}
