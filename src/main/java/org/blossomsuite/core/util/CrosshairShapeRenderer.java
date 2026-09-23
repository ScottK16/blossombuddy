package org.blossomsuite.core.util;

import org.blossomsuite.core.config.QolConfig;
import net.minecraft.client.gui.DrawContext;

public final class CrosshairShapeRenderer {
   private CrosshairShapeRenderer() {
   }

   public static void draw(DrawContext ctx, int cx, int cy, QolConfig q, int argb) {
      if (ctx != null && q != null) {
         QolConfig.CrosshairShape shape = q.crosshairShape == null ? QolConfig.CrosshairShape.VANILLA : q.crosshairShape;
         int size = clamp(q.crosshairSize, 1, maxSize(shape));
         int gap = supportsGap(shape) ? clamp(q.crosshairGap, 0, maxGap(shape)) : 0;
         int thick = normalizeThickness(shape, clamp(q.crosshairThickness, 1, maxThickness(shape)));
         switch (shape) {
            case PLUS:
               drawPlus(ctx, cx, cy, size, gap, thick, argb);
               break;
            case DOT:
               drawDot(ctx, cx, cy, thick, argb);
               break;
            case CIRCLE:
               drawCircle(ctx, cx, cy, Math.max(2, size), thick, argb);
               break;
            case SQUARE:
               drawSquare(ctx, cx, cy, Math.max(2, size), thick, argb);
               break;
            case X:
               drawX(ctx, cx, cy, Math.max(2, size), thick, argb);
               break;
            case T:
               drawT(ctx, cx, cy, size, thick, argb);
               break;
            case VANILLA:
               drawPlus(ctx, cx, cy, size, gap, thick, argb);
         }

         if (shapeHasCenterDot(shape)) {
            drawCenterDot(ctx, cx, cy, thick, argb);
         }
      }
   }

   private static void drawPlus(DrawContext ctx, int cx, int cy, int size, int gap, int thick, int argb) {
      int h = thick / 2;
      int x1 = cx - h;
      int x2 = x1 + thick;
      int y1 = cy - h;
      int y2 = y1 + thick;
      int topEnd = cy - gap;
      int bottomStart = cy + gap + 1;
      int leftEnd = cx - gap;
      int rightStart = cx + gap + 1;
      ctx.fill(x1, topEnd - size, x2, topEnd, argb);
      ctx.fill(x1, bottomStart, x2, bottomStart + size, argb);
      ctx.fill(leftEnd - size, y1, leftEnd, y2, argb);
      ctx.fill(rightStart, y1, rightStart + size, y2, argb);
   }

   private static void drawT(DrawContext ctx, int cx, int cy, int size, int thick, int argb) {
      int x1 = centeredStart(cx, thick);
      int y1 = centeredStart(cy, thick);
      ctx.fill(cx - size, y1, cx + size + 1, y1 + thick, argb);
      ctx.fill(x1, cy, x1 + thick, cy + size + 1, argb);
   }

   private static void drawDot(DrawContext ctx, int cx, int cy, int thick, int argb) {
      int size = Math.max(2, thick + 1);
      drawCenteredSquare(ctx, cx, cy, makeOdd(size), argb);
   }

   private static void drawCenterDot(DrawContext ctx, int cx, int cy, int thick, int argb) {
      int size = thick >= 3 ? 3 : 1;
      drawCenteredSquare(ctx, cx, cy, size, argb);
   }

   private static boolean shapeHasCenterDot(QolConfig.CrosshairShape shape) {
      return switch (shape) {
         case PLUS, DOT, T, VANILLA -> false;
         case CIRCLE, SQUARE, X -> true;
      };
   }

   private static void drawCenteredSquare(DrawContext ctx, int cx, int cy, int size, int argb) {
      int h = size / 2;
      ctx.fill(cx - h, cy - h, cx + h + 1, cy + h + 1, argb);
   }

   private static int centeredStart(int center, int width) {
      return center - width / 2;
   }

   private static int makeOdd(int value) {
      return value % 2 == 0 ? value + 1 : value;
   }

   private static void drawSquare(DrawContext ctx, int cx, int cy, int radius, int thick, int argb) {
      ctx.fill(cx - radius, cy - radius, cx + radius + 1, cy - radius + thick, argb);
      ctx.fill(cx - radius, cy + radius - thick + 1, cx + radius + 1, cy + radius + 1, argb);
      ctx.fill(cx - radius, cy - radius, cx - radius + thick, cy + radius + 1, argb);
      ctx.fill(cx + radius - thick + 1, cy - radius, cx + radius + 1, cy + radius + 1, argb);
   }

   private static void drawCircle(DrawContext ctx, int cx, int cy, int radius, int thick, int argb) {
      int r2 = radius * radius;
      int inner = Math.max(0, radius - thick);
      int inner2 = inner * inner;

      for (int y = -radius; y <= radius; y++) {
         for (int x = -radius; x <= radius; x++) {
            int d = x * x + y * y;
            if (d <= r2 && d >= inner2) {
               ctx.fill(cx + x, cy + y, cx + x + 1, cy + y + 1, argb);
            }
         }
      }
   }

   private static void drawX(DrawContext ctx, int cx, int cy, int radius, int thick, int argb) {
      int h = Math.max(0, (thick - 1) / 2);

      for (int i = -radius; i <= radius; i++) {
         for (int t = -h; t <= h; t++) {
            ctx.fill(cx + i, cy + i + t, cx + i + 1, cy + i + t + 1, argb);
            ctx.fill(cx + i, cy - i + t, cx + i + 1, cy - i + t + 1, argb);
         }
      }
   }

   public static boolean supportsSize(QolConfig.CrosshairShape shape) {
      return switch (shape) {
         case PLUS, CIRCLE, SQUARE, X, T -> true;
         case DOT, VANILLA -> false;
      };
   }

   public static boolean supportsGap(QolConfig.CrosshairShape shape) {
      return switch (shape) {
         case PLUS -> true;
         case DOT, CIRCLE, SQUARE, X, T, VANILLA -> false;
      };
   }

   public static int maxSize(QolConfig.CrosshairShape shape) {
      return switch (shape) {
         case PLUS, CIRCLE, SQUARE, T, VANILLA -> 24;
         case DOT -> 1;
         case X -> 16;
      };
   }

   public static int maxGap(QolConfig.CrosshairShape shape) {
      return supportsGap(shape) ? 16 : 0;
   }

   public static int maxThickness(QolConfig.CrosshairShape shape) {
      return switch (shape) {
         case PLUS, CIRCLE, SQUARE, VANILLA -> 8;
         case DOT -> 8;
         case X -> 3;
         case T -> 3;
      };
   }

   public static int normalizeThickness(QolConfig.CrosshairShape shape, int thickness) {
      int clamped = clamp(thickness, 1, maxThickness(shape));
      return (shape == QolConfig.CrosshairShape.T || shape == QolConfig.CrosshairShape.PLUS || shape == QolConfig.CrosshairShape.VANILLA) && clamped % 2 == 0
         ? Math.max(1, clamped - 1)
         : clamped;
   }

   private static int clamp(int v, int min, int max) {
      return Math.max(min, Math.min(max, v));
   }
}
