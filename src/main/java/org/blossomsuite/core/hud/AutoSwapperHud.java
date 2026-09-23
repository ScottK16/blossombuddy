package org.blossomsuite.core.hud;

import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.util.HudStyleUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;

public final class AutoSwapperHud {
   public static final DraggableHud DRAGGABLE = new DraggableHud() {
      @Override
      public String id() {
         return "autoswapper";
      }

      @Override
      public int x() {
         return AutoSwapperHud.lastX;
      }

      @Override
      public int y() {
         return AutoSwapperHud.lastY;
      }

      @Override
      public int w() {
         return AutoSwapperHud.lastW;
      }

      @Override
      public int h() {
         return AutoSwapperHud.lastH;
      }

      @Override
      public float posX() {
         return SuiteConfig.INSTANCE.AutoSwapperHudConfig.positionX;
      }

      @Override
      public float posY() {
         return SuiteConfig.INSTANCE.AutoSwapperHudConfig.positionY;
      }

      @Override
      public void setPos(float nx, float ny) {
         SuiteConfig.INSTANCE.AutoSwapperHudConfig.setPosition(nx, ny);
         ConfigIO.saveIfDirty();
      }

      @Override
      public boolean enabled() {
         return SuiteConfig.INSTANCE.AutoSwapperHudConfig.showHud;
      }

      @Override
      public boolean resizable() {
         return true;
      }

      @Override
      public float scale() {
         return SuiteConfig.INSTANCE.AutoSwapperHudConfig.scale;
      }

      @Override
      public void setScale(float s) {
         SuiteConfig.INSTANCE.AutoSwapperHudConfig.setScale(s);
         ConfigIO.saveIfDirty();
      }

      @Override
      public int baseW() {
         return AutoSwapperHud.lastBaseW > 0 ? AutoSwapperHud.lastBaseW : 120;
      }

      @Override
      public int baseH() {
         return AutoSwapperHud.lastBaseH > 0 ? AutoSwapperHud.lastBaseH : AutoSwapperHud.fallbackBaseH();
      }

      @Override
      public float backgroundOpacity() {
         return SuiteConfig.INSTANCE.AutoSwapperHudConfig.backgroundOpacity;
      }

      @Override
      public void setBackgroundOpacity(float opacity) {
         SuiteConfig.INSTANCE.AutoSwapperHudConfig.backgroundOpacity = Math.max(0.0F, Math.min(1.0F, opacity));
         ConfigIO.saveIfDirty();
      }
   };
   public static int lastX;
   public static int lastY;
   public static int lastW;
   public static int lastH;
   private static int lastBaseW = 120;
   private static int lastBaseH = 0;

   private AutoSwapperHud() {
   }

   private static int fallbackBaseH() {
      return SuiteConfig.INSTANCE.AutoSwapperHudConfig.showHeader ? 35 : 21;
   }

   public static void render(DrawContext ctx, MinecraftClient client) {
      if (SuiteConfig.INSTANCE.AutoSwapperHudConfig.showHud) {
         if (client != null && client.player != null) {
            String header = "AutoSwapper";
            boolean enabled = SuiteConfig.INSTANCE.QolConfig.autoSwapperEnabled;
            String row = "Status: " + (enabled ? "ON" : "OFF");
            renderPanel(ctx, client, header, row, enabled ? -8585348 : -5197648);
         }
      }
   }

   private static void renderPanel(DrawContext ctx, MinecraftClient client, String header, String row, int rowColor) {
      int screenH = client.getWindow().getScaledHeight();
      int screenW = client.getWindow().getScaledWidth();
      TextRenderer tr = client.textRenderer;
      boolean showHeader = SuiteConfig.INSTANCE.AutoSwapperHudConfig.showHeader;
      int headerH = 14;
      int pad = 4;
      int rowH = 9;
      int baseH = showHeader ? 2 + headerH + pad + rowH + pad + 2 : 2 + pad + rowH + pad + 2;
      int baseW = Math.max(110, (showHeader ? Math.max(tr.getWidth(header), tr.getWidth(row)) : tr.getWidth(row)) + 12);
      if (baseW > 220) {
         baseW = 220;
      }

      lastBaseW = baseW;
      lastBaseH = baseH;
      float scale = HudScaleUtil.scaleFor(SuiteConfig.INSTANCE.AutoSwapperHudConfig.scale, 0.1F, 2.0F, baseW, baseH, screenW, screenH);
      int w = Math.round(baseW * scale);
      int h = Math.round(baseH * scale);
      int x;
      int y;
      if (!(SuiteConfig.INSTANCE.AutoSwapperHudConfig.positionX < 0.0F) && !(SuiteConfig.INSTANCE.AutoSwapperHudConfig.positionY < 0.0F)) {
         int maxX = Math.max(0, screenW - w);
         int maxY = Math.max(0, screenH - h);
         x = Math.round(SuiteConfig.INSTANCE.AutoSwapperHudConfig.positionX * maxX);
         y = Math.round(SuiteConfig.INSTANCE.AutoSwapperHudConfig.positionY * maxY);
      } else {
         x = screenW - w - 6;
         y = 42;
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
         float opacity = SuiteConfig.INSTANCE.AutoSwapperHudConfig.backgroundOpacity;
         ctx.fill(0, 0, baseW, baseH, HudStyleUtil.panelBg(opacity));
         if (showHeader) {
            ctx.fill(0, 0, baseW, headerH, HudStyleUtil.panelHeader(opacity));
            ctx.fill(0, headerH, baseW, headerH + 1, HudStyleUtil.panelDivider(opacity));
            ctx.drawTextWithShadow(tr, header, 6, 4, -1);
         }

         int rowY = showHeader ? headerH + pad : 2 + pad;
         ctx.drawTextWithShadow(tr, row, 6, rowY, rowColor);
      } finally {
         matrices.popMatrix();
      }
   }
}
