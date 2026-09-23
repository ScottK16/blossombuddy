package org.blossomsuite.core.hud;

import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.DungeonConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.dungeons.DungeonState;
import org.blossomsuite.core.util.HudStyleUtil;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;

public final class DungeonHud {
   public static final DraggableHud DRAGGABLE = new DraggableHud() {
      @Override
      public String id() {
         return "dungeon";
      }

      @Override
      public int x() {
         return DungeonHud.lastX;
      }

      @Override
      public int y() {
         return DungeonHud.lastY;
      }

      @Override
      public int w() {
         return DungeonHud.lastW;
      }

      @Override
      public int h() {
         return DungeonHud.lastH;
      }

      @Override
      public float posX() {
         return SuiteConfig.INSTANCE.DungeonConfig.positionX;
      }

      @Override
      public float posY() {
         return SuiteConfig.INSTANCE.DungeonConfig.positionY;
      }

      @Override
      public void setPos(float nx, float ny) {
         SuiteConfig.INSTANCE.DungeonConfig.setPosition(nx, ny);
         ConfigIO.saveIfDirty();
      }

      @Override
      public boolean enabled() {
         return SuiteConfig.INSTANCE.DungeonConfig.showHud;
      }

      @Override
      public boolean resizable() {
         return true;
      }

      @Override
      public float scale() {
         return SuiteConfig.INSTANCE.DungeonConfig.scale;
      }

      @Override
      public void setScale(float s) {
         SuiteConfig.INSTANCE.DungeonConfig.setScale(s);
         ConfigIO.saveIfDirty();
      }

      @Override
      public int baseW() {
         return DungeonHud.lastBaseW > 0 ? DungeonHud.lastBaseW : 140;
      }

      @Override
      public int baseH() {
         return DungeonHud.lastBaseH > 0 ? DungeonHud.lastBaseH : DungeonHud.fallbackBaseH();
      }

      @Override
      public float backgroundOpacity() {
         return SuiteConfig.INSTANCE.DungeonConfig.backgroundOpacity;
      }

      @Override
      public void setBackgroundOpacity(float opacity) {
         SuiteConfig.INSTANCE.DungeonConfig.backgroundOpacity = Math.max(0.0F, Math.min(1.0F, opacity));
         ConfigIO.saveIfDirty();
      }
   };
   public static int lastX;
   public static int lastY;
   public static int lastW;
   public static int lastH;
   private static int lastBaseW = 140;
   private static int lastBaseH = 0;

   private DungeonHud() {
   }

   private static int fallbackBaseH() {
      return SuiteConfig.INSTANCE.DungeonConfig.showHeader ? 38 : 24;
   }

   public static void render(DrawContext ctx, MinecraftClient client) {
      DungeonConfig cfg = SuiteConfig.INSTANCE.DungeonConfig;
      if (cfg.showHud) {
         if (client != null && client.player != null) {
            long now = System.currentTimeMillis();
            List<DungeonState.CooldownRow> rows = DungeonState.hudCooldownRows(now);
            String activeRunLine = DungeonState.activeRunLine(now);
            if (!rows.isEmpty() || activeRunLine != null || HudEditState.editMode) {
               int screenH = client.getWindow().getScaledHeight();
               int screenW = client.getWindow().getScaledWidth();
               TextRenderer tr = client.textRenderer;
               String header = cfg.hudServerMode == DungeonConfig.HudServerMode.ALL_SERVERS ? "Dungeons" : "Dungeon";
               boolean showHeader = cfg.showHeader;
               int headerH = 14;
               int pad = 4;
               int rowH = 12;
               int activeRows = activeRunLine == null ? 0 : 1;
               int rowCount = Math.max(1, rows.size() + activeRows);
               int baseH = showHeader ? 2 + headerH + pad + rowCount * rowH + pad + 2 : 2 + pad + rowCount * rowH + pad + 2;
               int textW = showHeader ? tr.getWidth(header) : 0;
               if (activeRunLine != null) {
                  textW = Math.max(textW, tr.getWidth(activeRunLine));
               }

               for (DungeonState.CooldownRow row : rows) {
                  String line = lineText(row, cfg.hudServerMode == DungeonConfig.HudServerMode.ALL_SERVERS);
                  textW = Math.max(textW, tr.getWidth(line));
               }

               int baseW = Math.max(120, textW + 12);
               if (baseW > 260) {
                  baseW = 260;
               }

               lastBaseW = baseW;
               lastBaseH = baseH;
               float scale = HudScaleUtil.scaleFor(cfg.scale, 0.1F, 2.0F, baseW, baseH, screenW, screenH);
               int w = Math.round(baseW * scale);
               int h = Math.round(baseH * scale);
               int x;
               int y;
               if (!(cfg.positionX < 0.0F) && !(cfg.positionY < 0.0F)) {
                  int maxX = Math.max(0, screenW - w);
                  int maxY = Math.max(0, screenH - h);
                  x = Math.round(cfg.positionX * maxX);
                  y = Math.round(cfg.positionY * maxY);
               } else {
                  x = screenW - w - 6;
                  y = 72;
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
                  float opacity = cfg.backgroundOpacity;
                  ctx.fill(0, 0, baseW, baseH, HudStyleUtil.panelBg(opacity));
                  if (showHeader) {
                     ctx.fill(0, 0, baseW, headerH, HudStyleUtil.panelHeader(opacity));
                     ctx.fill(0, headerH, baseW, headerH + 1, HudStyleUtil.panelDivider(opacity));
                     ctx.drawTextWithShadow(tr, header, 6, 4, -1);
                  }

                  int rowY = showHeader ? headerH + pad : 2 + pad;
                  if (activeRunLine != null) {
                     ctx.drawTextWithShadow(tr, trimToWidth(client, activeRunLine, baseW - 12), 6, rowY, -8054);
                     rowY += rowH;
                  }

                  for (DungeonState.CooldownRow row : rows) {
                     String line = lineText(row, cfg.hudServerMode == DungeonConfig.HudServerMode.ALL_SERVERS);
                     int color = row.active() ? -4208683 : -8585317;
                     ctx.drawTextWithShadow(tr, trimToWidth(client, line, baseW - 12), 6, rowY, color);
                     rowY += rowH;
                  }

                  if (HudEditState.editMode) {
                     ctx.fill(0, 0, baseW, 1, -1996488705);
                     ctx.fill(0, baseH - 1, baseW, baseH, -1996488705);
                     ctx.fill(0, 0, 1, baseH, -1996488705);
                     ctx.fill(baseW - 1, 0, baseW, baseH, -1996488705);
                  }
               } finally {
                  matrices.popMatrix();
               }
            }
         }
      }
   }

   private static String lineText(DungeonState.CooldownRow row, boolean includeServer) {
      String status = row.active() ? DungeonState.formatCooldownDuration(row.remainingMs()) : "READY";
      return includeServer ? SuiteRuntime.profile().serverDisplayName(row.server()) + ": " + status : status;
   }

   private static String trimToWidth(MinecraftClient client, String s, int maxW) {
      if (client.textRenderer.getWidth(s) <= maxW) {
         return s;
      }

      String ell = "...";
      int ellW = client.textRenderer.getWidth(ell);

      for (int len = s.length(); len > 0; len--) {
         String sub = s.substring(0, len);
         if (client.textRenderer.getWidth(sub) + ellW <= maxW) {
            return sub + ell;
         }
      }

      return ell;
   }
}
