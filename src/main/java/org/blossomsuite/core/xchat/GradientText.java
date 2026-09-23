package org.blossomsuite.core.xchat;

/**
 * Blends an admin-granted 2-5 stop colour gradient across the characters of a player's name in cross-realm chat
 * (see relay/cosmetics.js, which is the only place a gradient is ever assigned). Pure colour math, no game types,
 * so it can be unit tested directly.
 */
public final class GradientText {
   private GradientText() {
   }

   /** True if this looks like a gradient the relay could actually have sent: 2-5 colours, each a full #rrggbb. */
   public static boolean isValid(String[] colors) {
      if (colors == null || colors.length < 2 || colors.length > 5) {
         return false;
      }

      for (String c : colors) {
         if (parseColor(c) < 0) {
            return false;
         }
      }

      return true;
   }

   /** The colour (packed 0xRRGGBB) for the character at {@code index} of a name {@code length} characters long. */
   public static int colorAt(String[] colors, int index, int length) {
      if (length <= 1) {
         return parseColor(colors[0]);
      }

      double t = Math.max(0.0, Math.min(1.0, (double)index / (length - 1)));
      double scaled = t * (colors.length - 1);
      int lower = (int)scaled;
      int upper = Math.min(lower + 1, colors.length - 1);
      return lerp(parseColor(colors[lower]), parseColor(colors[upper]), scaled - lower);
   }

   private static int lerp(int from, int to, double frac) {
      int fr = (from >> 16) & 0xFF;
      int fg = (from >> 8) & 0xFF;
      int fb = from & 0xFF;
      int tr = (to >> 16) & 0xFF;
      int tg = (to >> 8) & 0xFF;
      int tb = to & 0xFF;
      int r = (int)Math.round(fr + (tr - fr) * frac);
      int g = (int)Math.round(fg + (tg - fg) * frac);
      int b = (int)Math.round(fb + (tb - fb) * frac);
      return (r << 16) | (g << 8) | b;
   }

   /** -1 for anything that is not a full #rrggbb colour, so a malformed gradient from a misbehaving relay never crashes chat. */
   private static int parseColor(String hex) {
      if (hex == null || !hex.matches("(?i)#[0-9a-f]{6}")) {
         return -1;
      }

      try {
         return Integer.parseInt(hex.substring(1), 16);
      } catch (NumberFormatException e) {
         return -1;
      }
   }
}
