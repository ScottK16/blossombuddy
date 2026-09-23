package org.blossomsuite.core.ui;

import net.minecraft.client.gui.DrawContext;

/**
 * Colours and drawing helpers for the BlossomBuddy look: a deep plum base with soft rounded panels and a
 * cherry-blossom pink accent. All colours are opaque ARGB.
 */
public final class Theme {
   public static final int BG_TOP = 0xFF1B1324;
   public static final int BG_BOTTOM = 0xFF0E0A14;
   public static final int PANEL = 0xFF211A2C;
   public static final int PANEL_EDGE = 0xFF3A2D4A;
   public static final int PANEL_SOFT = 0xFF281F35;

   public static final int ACCENT = 0xFFF48FB1;
   public static final int ACCENT_DIM = 0xFFB0688A;
   public static final int ACCENT_FILL = 0xFF4A2B3F;
   public static final int LAVENDER = 0xFFB9A5E6;

   public static final int TEXT = 0xFFEFE8F7;
   public static final int TEXT_DIM = 0xFFB4A8C6;
   public static final int TEXT_MUTED = 0xFF7D7290;

   public static final int CONTROL = 0xFF2C2339;
   public static final int CONTROL_HOVER = 0xFF3A2E4A;
   public static final int CONTROL_OFF = 0xFF221B2D;
   public static final int CONTROL_EDGE = 0xFF43364F;

   public static final int GOOD = 0xFF8EE0A2;
   public static final int BAD = 0xFFE8848A;

   private Theme() {
   }

   /** Filled rounded rectangle. Rows never overlap, so translucent colours blend correctly. */
   public static void roundRect(DrawContext c, int x1, int y1, int x2, int y2, int radius, int color) {
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
   public static void roundBox(DrawContext c, int x1, int y1, int x2, int y2, int radius, int edge, int fill) {
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
   public static void magnifier(DrawContext c, int x, int y, int color) {
      roundBox(c, x, y, x + 8, y + 8, 3, color, PANEL);
      c.fill(x + 7, y + 7, x + 9, y + 9, color);
      c.fill(x + 8, y + 8, x + 11, y + 11, color);
   }
}
