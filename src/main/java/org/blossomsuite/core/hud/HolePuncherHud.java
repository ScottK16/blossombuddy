package org.blossomsuite.core.hud;

import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.QolConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.qol.holepuncher.HolePuncher;
import org.blossomsuite.core.util.HudStyleUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix3x2fStack;

public final class HolePuncherHud {
   private static final int GUIDE_STEP = 5;
   public static final DraggableHud DRAGGABLE = new DraggableHud() {
      @Override
      public String id() {
         return "holepuncher";
      }

      @Override
      public int x() {
         return HolePuncherHud.lastX;
      }

      @Override
      public int y() {
         return HolePuncherHud.lastY;
      }

      @Override
      public int w() {
         return HolePuncherHud.lastW;
      }

      @Override
      public int h() {
         return HolePuncherHud.lastH;
      }

      @Override
      public float posX() {
         return SuiteConfig.INSTANCE.HolePuncherHudConfig.positionX;
      }

      @Override
      public float posY() {
         return SuiteConfig.INSTANCE.HolePuncherHudConfig.positionY;
      }

      @Override
      public void setPos(float nx, float ny) {
         SuiteConfig.INSTANCE.HolePuncherHudConfig.setPosition(nx, ny);
         ConfigIO.saveIfDirty();
      }

      @Override
      public boolean enabled() {
         return SuiteConfig.INSTANCE.HolePuncherHudConfig.showHud;
      }

      @Override
      public boolean resizable() {
         return true;
      }

      @Override
      public float scale() {
         return SuiteConfig.INSTANCE.HolePuncherHudConfig.scale;
      }

      @Override
      public void setScale(float s) {
         SuiteConfig.INSTANCE.HolePuncherHudConfig.setScale(s);
         ConfigIO.saveIfDirty();
      }

      @Override
      public int baseW() {
         return HolePuncherHud.lastBaseW > 0 ? HolePuncherHud.lastBaseW : 130;
      }

      @Override
      public int baseH() {
         return HolePuncherHud.lastBaseH > 0 ? HolePuncherHud.lastBaseH : HolePuncherHud.fallbackBaseH();
      }

      @Override
      public float backgroundOpacity() {
         return SuiteConfig.INSTANCE.HolePuncherHudConfig.backgroundOpacity;
      }

      @Override
      public void setBackgroundOpacity(float opacity) {
         SuiteConfig.INSTANCE.HolePuncherHudConfig.backgroundOpacity = Math.max(0.0F, Math.min(1.0F, opacity));
         ConfigIO.saveIfDirty();
      }
   };
   public static int lastX;
   public static int lastY;
   public static int lastW;
   public static int lastH;
   private static int lastBaseW = 130;
   private static int lastBaseH = 0;

   private HolePuncherHud() {
   }

   private static int fallbackBaseH() {
      int rows = SuiteConfig.INSTANCE.HolePuncherHudConfig.showHeader ? 3 : 2;
      return 2 + (SuiteConfig.INSTANCE.HolePuncherHudConfig.showHeader ? 18 : 4) + rows * 9 + Math.max(0, rows - 1) * 4 + 4 + 2;
   }

   public static void render(DrawContext ctx, MinecraftClient client) {
      if (SuiteConfig.INSTANCE.HolePuncherHudConfig.showHud) {
         if (client != null && client.player != null) {
            TextRenderer tr = client.textRenderer;
            List<HolePuncherHud.Row> rows = buildRows(client);
            if (!rows.isEmpty()) {
               String header = "Hole Puncher";
               boolean showHeader = SuiteConfig.INSTANCE.HolePuncherHudConfig.showHeader;
               int headerH = 14;
               int pad = 4;
               int rowGap = 4;
               int rowH = 9;
               int baseH = showHeader
                  ? 2 + headerH + pad + rows.size() * rowH + Math.max(0, rows.size() - 1) * rowGap + pad + 2
                  : 2 + pad + rows.size() * rowH + Math.max(0, rows.size() - 1) * rowGap + pad + 2;
               int baseW = Math.max(120, tr.getWidth(header) + 12);

               for (HolePuncherHud.Row row : rows) {
                  baseW = Math.max(baseW, tr.getWidth(row.text) + 12);
               }

               if (baseW > 240) {
                  baseW = 240;
               }

               lastBaseW = baseW;
               lastBaseH = baseH;
               int screenH = client.getWindow().getScaledHeight();
               int screenW = client.getWindow().getScaledWidth();
               float scale = HudScaleUtil.scaleFor(SuiteConfig.INSTANCE.HolePuncherHudConfig.scale, 0.1F, 2.0F, baseW, baseH, screenW, screenH);
               int w = Math.round(baseW * scale);
               int h = Math.round(baseH * scale);
               int x;
               int y;
               if (!(SuiteConfig.INSTANCE.HolePuncherHudConfig.positionX < 0.0F) && !(SuiteConfig.INSTANCE.HolePuncherHudConfig.positionY < 0.0F)) {
                  int maxX = Math.max(0, screenW - w);
                  int maxY = Math.max(0, screenH - h);
                  x = Math.round(SuiteConfig.INSTANCE.HolePuncherHudConfig.positionX * maxX);
                  y = Math.round(SuiteConfig.INSTANCE.HolePuncherHudConfig.positionY * maxY);
               } else {
                  x = screenW - w - 6;
                  y = 74;
               }

               lastX = x;
               lastY = y;
               lastW = w;
               lastH = h;
               Matrix3x2fStack matrices = ctx.getMatrices();
               matrices.pushMatrix();
               matrices.translate(x, y);
               matrices.scale(scale, scale);

               try {
                  float opacity = SuiteConfig.INSTANCE.HolePuncherHudConfig.backgroundOpacity;
                  ctx.fill(0, 0, baseW, baseH, HudStyleUtil.panelBg(opacity));
                  if (showHeader) {
                     ctx.fill(0, 0, baseW, headerH, HudStyleUtil.panelHeader(opacity));
                     ctx.fill(0, headerH, baseW, headerH + 1, HudStyleUtil.panelDivider(opacity));
                     ctx.drawTextWithShadow(tr, header, 6, 4, -1);
                  }

                  int yy = showHeader ? headerH + pad : 2 + pad;

                  for (HolePuncherHud.Row row : rows) {
                     ctx.drawTextWithShadow(tr, row.text, 6, yy, row.color);
                     yy += rowH + rowGap;
                  }
               } finally {
                  matrices.popMatrix();
               }
            }
         }
      }
   }

   private static List<HolePuncherHud.Row> buildRows(MinecraftClient client) {
      QolConfig q = SuiteConfig.INSTANCE.QolConfig;
      List<HolePuncherHud.Row> rows = new ArrayList<>();
      rows.add(new HolePuncherHud.Row("Status: " + (q.holePuncherEnabled ? "ON" : "OFF"), q.holePuncherEnabled ? -8585348 : -5197648));
      rows.add(new HolePuncherHud.Row("Guided: " + (q.holePuncherGuided ? "ON" : "OFF"), q.holePuncherGuided ? -8585348 : -5197648));
      if (q.holePuncherEnabled && q.holePuncherGuided) {
         BlockPos anchor = HolePuncher.getGuideAnchor();
         if (anchor == null) {
            rows.add(new HolePuncherHud.Row("Grid: anchor not set", -5197648));
         } else if (client.crosshairTarget != null && client.crosshairTarget.getType() == Type.BLOCK) {
            BlockPos target = ((BlockHitResult)client.crosshairTarget).getBlockPos();
            int dx = target.getX() - anchor.getX();
            int dz = target.getZ() - anchor.getZ();
            boolean ok = Math.floorMod(dx, 5) == 0 && Math.floorMod(dz, 5) == 0;
            rows.add(new HolePuncherHud.Row("Grid: " + (ok ? "OK" : "off") + "  y=" + anchor.getY(), ok ? -8585348 : -11410));
         } else {
            rows.add(new HolePuncherHud.Row("Grid: aim at block", -5197648));
         }
      }

      return rows;
   }

   private record Row(String text, int color) {
   }
}
