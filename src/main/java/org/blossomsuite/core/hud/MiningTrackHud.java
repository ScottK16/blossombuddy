package org.blossomsuite.core.hud;

import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.QolConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.util.HudStyleUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.joml.Matrix3x2fStack;

public final class MiningTrackHud {
   private static int lastBaseW = 160;
   private static int lastBaseH = 0;
   public static final DraggableHud DRAGGABLE = new DraggableHud() {
      @Override
      public String id() {
         return "mining";
      }

      @Override
      public int x() {
         return MiningTrackHud.lastX;
      }

      @Override
      public int y() {
         return MiningTrackHud.lastY;
      }

      @Override
      public int w() {
         return MiningTrackHud.lastW;
      }

      @Override
      public int h() {
         return MiningTrackHud.lastH;
      }

      @Override
      public float posX() {
         return SuiteConfig.INSTANCE.MiningHudConfig.positionX;
      }

      @Override
      public float posY() {
         return SuiteConfig.INSTANCE.MiningHudConfig.positionY;
      }

      @Override
      public void setPos(float nx, float ny) {
         SuiteConfig.INSTANCE.MiningHudConfig.setPosition(nx, ny);
         ConfigIO.saveIfDirty();
      }

      @Override
      public boolean enabled() {
         return SuiteConfig.INSTANCE.MiningHudConfig.showHud;
      }

      @Override
      public boolean resizable() {
         return true;
      }

      @Override
      public float scale() {
         return SuiteConfig.INSTANCE.MiningHudConfig.scale;
      }

      @Override
      public void setScale(float s) {
         SuiteConfig.INSTANCE.MiningHudConfig.setScale(s);
         ConfigIO.saveIfDirty();
      }

      @Override
      public int baseW() {
         return MiningTrackHud.lastBaseW > 0 ? MiningTrackHud.lastBaseW : 160;
      }

      @Override
      public int baseH() {
         return MiningTrackHud.lastBaseH > 0 ? MiningTrackHud.lastBaseH : 48;
      }

      @Override
      public float backgroundOpacity() {
         return SuiteConfig.INSTANCE.MiningHudConfig.backgroundOpacity;
      }

      @Override
      public void setBackgroundOpacity(float opacity) {
         SuiteConfig.INSTANCE.MiningHudConfig.backgroundOpacity = Math.max(0.0F, Math.min(1.0F, opacity));
         ConfigIO.saveIfDirty();
      }
   };
   public static int lastX;
   public static int lastY;
   public static int lastW;
   public static int lastH;

   private MiningTrackHud() {
   }

   public static void render(DrawContext ctx, MinecraftClient client) {
      if (SuiteConfig.INSTANCE.MiningHudConfig.showHud) {
         if (client != null && client.player != null) {
            QolConfig q = SuiteConfig.INSTANCE.QolConfig;
            if (q != null) {
               String dirStr = q.miningTrackDir == null ? "" : q.miningTrackDir.trim().toLowerCase();
               if (dirStr.isBlank()) {
                  if (!HudEditState.editMode) {
                     return;
                  }

                  dirStr = "west";
               }

               Direction targetDir = parseCardinal(dirStr);
               if (targetDir != null) {
                  Direction cur = client.player.getHorizontalFacing();
                  boolean facingOk = cur == targetDir;
                  boolean alongX = targetDir == Direction.NORTH || targetDir == Direction.SOUTH;
                  BlockPos p = client.player.getBlockPos();
                  int lateral = alongX ? p.getX() : p.getZ();
                  int delta = lateral - q.miningTrackCoord;
                  String axis = alongX ? "X" : "Z";
                  String header = "Mining Track";
                  String line = shortDir(targetDir) + "  " + axis + "=" + q.miningTrackCoord + "  d" + axis + "=" + (delta >= 0 ? "+" : "") + delta;
                  TextRenderer tr = client.textRenderer;
                  int headerH = 14;
                  int pad = 4;
                  int rowH = 9;
                  int baseW = Math.max(150, Math.max(tr.getWidth(header) + 12, tr.getWidth(line) + 12));
                  if (baseW > 240) {
                     baseW = 240;
                  }

                  int barH = 6;
                  int baseH = 2 + headerH + pad + rowH + pad + barH + pad + 2;
                  lastBaseW = baseW;
                  lastBaseH = baseH;
                  int screenH = client.getWindow().getScaledHeight();
                  int screenW = client.getWindow().getScaledWidth();
                  float scale = HudScaleUtil.scaleFor(SuiteConfig.INSTANCE.MiningHudConfig.scale, 0.1F, 2.0F, baseW, baseH, screenW, screenH);
                  int w = Math.round(baseW * scale);
                  int h = Math.round(baseH * scale);
                  int maxX = Math.max(0, screenW - w);
                  int maxY = Math.max(0, screenH - h);
                  int x = Math.round(SuiteConfig.INSTANCE.MiningHudConfig.positionX * maxX);
                  int y = Math.round(SuiteConfig.INSTANCE.MiningHudConfig.positionY * maxY);
                  lastX = x;
                  lastY = y;
                  lastW = w;
                  lastH = h;
                  Matrix3x2fStack matrices = ctx.getMatrices();
                  matrices.pushMatrix();
                  matrices.translate(x, y);
                  matrices.scale(scale, scale);

                  try {
                     float opacity = SuiteConfig.INSTANCE.MiningHudConfig.backgroundOpacity;
                     ctx.fill(0, 0, baseW, baseH, HudStyleUtil.panelBg(opacity));
                     ctx.fill(0, 0, baseW, headerH, HudStyleUtil.panelHeader(opacity));
                     ctx.fill(0, headerH, baseW, headerH + 1, HudStyleUtil.panelDivider(opacity));
                     ctx.drawTextWithShadow(tr, header, 6, 4, -1);
                     int color = delta == 0 && facingOk ? -8585348 : -37266;
                     ctx.drawTextWithShadow(tr, line, 6, headerH + pad, color);
                     drawDriftBar(ctx, baseW, headerH + pad + rowH + pad, barH, delta, facingOk);
                  } finally {
                     matrices.popMatrix();
                  }
               }
            }
         }
      }
   }

   private static void drawDriftBar(DrawContext ctx, int baseW, int y, int barH, int delta, boolean facingOk) {
      int barW = Math.min(180, Math.max(120, baseW - 24));
      int x = baseW / 2 - barW / 2;
      ctx.fill(x, y, x + barW, y + barH, 1711276032);
      int cx = x + barW / 2;
      ctx.fill(cx - 1, y - 1, cx + 1, y + barH + 1, -1426063361);
      int pxPerBlock = 10;
      int maxOffset = barW / 2 - 6;
      int off = delta * pxPerBlock;
      if (off > maxOffset) {
         off = maxOffset;
      }

      if (off < -maxOffset) {
         off = -maxOffset;
      }

      int mx = cx + off;
      int color = delta == 0 && facingOk ? -8585348 : -37266;
      ctx.fill(mx - 2, y - 2, mx + 2, y + barH + 2, color);
   }

   private static Direction parseCardinal(String s) {
      if (s == null) {
         return null;
      }

      return switch (s.trim().toLowerCase()) {
         case "north" -> Direction.NORTH;
         case "south" -> Direction.SOUTH;
         case "east" -> Direction.EAST;
         case "west" -> Direction.WEST;
         default -> null;
      };
   }

   private static String shortDir(Direction d) {
      if (d == null) {
         return "?";
      }

      return switch (d) {
         case NORTH -> "N";
         case SOUTH -> "S";
         case EAST -> "E";
         case WEST -> "W";
         default -> "?";
      };
   }
}
