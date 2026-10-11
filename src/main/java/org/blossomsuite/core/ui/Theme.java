package org.blossomsuite.core.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
/**
 * Colours and drawing helpers for the BlossomBuddy look: a deep plum base with soft rounded panels and a
 * cherry-blossom pink accent. All colours are opaque ARGB.
 */
public final class Theme {
   /** Seasonal choices for {@code FeatureConfig.Render.theme}. */
   public static final String AUTO = "AUTO";
   public static final String HALLOWEEN = "HALLOWEEN";
   public static final String CLASSIC = "CLASSIC";

   // the colours are plain fields (not constants) so the seasonal theme can swap the whole set at once, see refresh()
   public static int BG_TOP;
   public static int BG_BOTTOM;
   public static int PANEL;
   public static int PANEL_EDGE;
   public static int PANEL_SOFT;

   public static int ACCENT;
   public static int ACCENT_DIM;
   public static int ACCENT_FILL;
   public static int LAVENDER;

   public static int TEXT;
   public static int TEXT_DIM;
   public static int TEXT_MUTED;

   public static int CONTROL;
   public static int CONTROL_HOVER;
   public static int CONTROL_OFF;
   public static int CONTROL_EDGE;

   public static int GOOD;
   public static int BAD;

   private static boolean halloween = false;

   static {
      apply(false);
   }

   private Theme() {
   }

   /** True while the Halloween colours are in use. */
   public static boolean halloween() {
      return halloween;
   }

   /** The mode to use: AUTO, HALLOWEEN or CLASSIC (anything else reads as AUTO). */
   public static String normalizeMode(String mode) {
      String m = mode == null ? "" : mode.trim().toUpperCase(java.util.Locale.ROOT);
      return m.equals(HALLOWEEN) || m.equals(CLASSIC) ? m : AUTO;
   }

   public static String nextMode(String mode) {
      return switch (normalizeMode(mode)) {
         case AUTO -> HALLOWEEN;
         case HALLOWEEN -> CLASSIC;
         default -> AUTO;
      };
   }

   public static String modeLabel(String mode) {
      return switch (normalizeMode(mode)) {
         case HALLOWEEN -> "Halloween";
         case CLASSIC -> "Classic";
         default -> "Auto";
      };
   }

   /** Whether Halloween is on for this mode on this day. Auto means 1 October to 2 November. */
   public static boolean halloweenOn(String mode, java.time.LocalDate today) {
      return switch (normalizeMode(mode)) {
         case HALLOWEEN -> true;
         case CLASSIC -> false;
         default -> today.getMonthValue() == 10 || today.getMonthValue() == 11 && today.getDayOfMonth() <= 2;
      };
   }

   /** Picks the colour set for today and the player's choice; cheap, so it can run every tick. */
   public static void refresh() {
      boolean want = halloweenOn(org.blossomsuite.core.config.FeatureConfig.INSTANCE.render.theme, java.time.LocalDate.now());
      if (want != halloween) {
         apply(want);
      }
   }

   private static void apply(boolean on) {
      halloween = on;
      if (on) {
         BG_TOP = 0xFF1D1020;
         BG_BOTTOM = 0xFF0B0709;
         PANEL = 0xFF241524;
         PANEL_EDGE = 0xFF5A3418;
         PANEL_SOFT = 0xFF2B1A26;
         ACCENT = 0xFFFF9A2E;
         ACCENT_DIM = 0xFFB5651C;
         ACCENT_FILL = 0xFF4A2810;
         LAVENDER = 0xFFB07CE8;
         TEXT = 0xFFF5EBDD;
         TEXT_DIM = 0xFFC9B39C;
         TEXT_MUTED = 0xFF8C7767;
         CONTROL = 0xFF2E1D2A;
         CONTROL_HOVER = 0xFF42293A;
         CONTROL_OFF = 0xFF231620;
         CONTROL_EDGE = 0xFF5A3A2C;
         GOOD = 0xFF9BE08A;
         BAD = 0xFFE8605A;
      } else {
         BG_TOP = 0xFF1B1324;
         BG_BOTTOM = 0xFF0E0A14;
         PANEL = 0xFF211A2C;
         PANEL_EDGE = 0xFF3A2D4A;
         PANEL_SOFT = 0xFF281F35;
         ACCENT = 0xFFF48FB1;
         ACCENT_DIM = 0xFFB0688A;
         ACCENT_FILL = 0xFF4A2B3F;
         LAVENDER = 0xFFB9A5E6;
         TEXT = 0xFFEFE8F7;
         TEXT_DIM = 0xFFB4A8C6;
         TEXT_MUTED = 0xFF7D7290;
         CONTROL = 0xFF2C2339;
         CONTROL_HOVER = 0xFF3A2E4A;
         CONTROL_OFF = 0xFF221B2D;
         CONTROL_EDGE = 0xFF43364F;
         GOOD = 0xFF8EE0A2;
         BAD = 0xFFE8848A;
      }
   }

   private static final String[] PUMPKIN = {
      "....GG....",
      "...GG.....",
      ".OOOOOOOO.",
      "OOOOOOOOOO",
      "OKKOOOOKKO",
      "OOKOOOOKOO",
      "OOOOOOOOOO",
      "OKOKKKKOKO",
      "OOKOKKOKOO",
      ".OOOOOOOO."
   };

   /** A small pumpkin (10x10 px), drawn from rectangles. Only when Halloween is on. */
   public static void pumpkin(GuiGraphicsExtractor c, int x, int y) {
      if (!halloween) {
         return;
      }

      for (int row = 0; row < PUMPKIN.length; row++) {
         for (int col = 0; col < PUMPKIN[row].length(); col++) {
            int color = switch (PUMPKIN[row].charAt(col)) {
               case 'O' -> 0xFFFF8A1F;
               case 'K' -> 0xFF2A1405;
               case 'G' -> 0xFF5DB04A;
               default -> 0;
            };
            if (color != 0) {
               c.fill(x + col, y + row, x + col + 1, y + row + 1, color);
            }
         }
      }
   }

   /** A cobweb in a corner of a panel, {@code size} px across. {@code right}/{@code bottom} say which corner (x, y is that corner). */
   public static void cobweb(GuiGraphicsExtractor c, int x, int y, int size, boolean right, boolean bottom) {
      if (!halloween) {
         return;
      }

      int line = 0x66E8E0F0;
      int dx = right ? -1 : 1;
      int dy = bottom ? -1 : 1;
      double[] angles = {0.0, 30.0, 60.0, 90.0};
      for (double deg : angles) {
         double rad = Math.toRadians(deg);
         for (int i = 0; i <= size; i++) {
            int px = x + dx * (int)Math.round(Math.cos(rad) * i);
            int py = y + dy * (int)Math.round(Math.sin(rad) * i);
            c.fill(px, py, px + 1, py + 1, line);
         }
      }

      for (int ring = 1; ring <= 3; ring++) {
         double r = size * ring / 3.0;
         for (int a = 0; a <= 90; a += 3) {
            double rad = Math.toRadians(a);
            int px = x + dx * (int)Math.round(Math.cos(rad) * r);
            int py = y + dy * (int)Math.round(Math.sin(rad) * r);
            c.fill(px, py, px + 1, py + 1, line);
         }
      }
   }

   /** Filled rounded rectangle. Rows never overlap, so translucent colours blend correctly. */
   public static void roundRect(GuiGraphicsExtractor c, int x1, int y1, int x2, int y2, int radius, int color) {
      int w = x2 - x1;
      int h = y2 - y1;
      if (w <= 0 || h <= 0) {
         return;
      }

      int r = Math.min(radius, Math.min(w, h) / 2);
      if (r <= 0) {
         c.fill(x1, y1, x2, y2, color);
         return;
      }

      c.fill(x1, y1 + r, x2, y2 - r, color);
      for (int i = 0; i < r; i++) {
         double dy = r - i - 0.5;
         int inset = r - (int)Math.round(Math.sqrt(r * r - dy * dy));
         c.fill(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, color);
         c.fill(x1 + inset, y2 - i - 1, x2 - inset, y2 - i, color);
      }
   }

   /** Rounded rectangle with a one pixel edge. */
   public static void roundBox(GuiGraphicsExtractor c, int x1, int y1, int x2, int y2, int radius, int edge, int fill) {
      roundRect(c, x1, y1, x2, y2, radius, edge);
      roundRect(c, x1 + 1, y1 + 1, x2 - 1, y2 - 1, Math.max(0, radius - 1), fill);
   }

   public static int lerp(int from, int to, float t) {
      float k = Math.max(0.0F, Math.min(1.0F, t));
      int a = (int)((from >>> 24) + ((to >>> 24) - (from >>> 24)) * k);
      int r = (int)((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * k);
      int g = (int)((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * k);
      int b = (int)((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * k);
      return a << 24 | r << 16 | g << 8 | b;
   }

   /** A small magnifying glass, drawn from rectangles because the default font has no such glyph. */
   public static void magnifier(GuiGraphicsExtractor c, int x, int y, int color) {
      roundBox(c, x, y, x + 8, y + 8, 3, color, PANEL);
      c.fill(x + 7, y + 7, x + 9, y + 9, color);
      c.fill(x + 8, y + 8, x + 11, y + 11, color);
   }
}
