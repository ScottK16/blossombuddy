package org.blossomsuite.core.hud;

import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.RentalsConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.util.HudStyleUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;

public final class RentalsHud {
   private static int lastBaseW = 160;
   private static int lastBaseH = 42;
   public static int lastX;
   public static int lastY;
   public static int lastW;
   public static int lastH;
   public static final DraggableHud DRAGGABLE = new DraggableHud() {
      @Override
      public String id() {
         return "rentals";
      }

      @Override
      public int x() {
         return RentalsHud.lastX;
      }

      @Override
      public int y() {
         return RentalsHud.lastY;
      }

      @Override
      public int w() {
         return RentalsHud.lastW;
      }

      @Override
      public int h() {
         return RentalsHud.lastH;
      }

      @Override
      public float posX() {
         return SuiteConfig.INSTANCE.RentalsConfig.positionX;
      }

      @Override
      public float posY() {
         return SuiteConfig.INSTANCE.RentalsConfig.positionY;
      }

      @Override
      public void setPos(float nx, float ny) {
         SuiteConfig.INSTANCE.RentalsConfig.setPosition(nx, ny);
         ConfigIO.saveIfDirty();
      }

      @Override
      public boolean enabled() {
         return SuiteConfig.INSTANCE.RentalsConfig.showHud;
      }

      @Override
      public boolean resizable() {
         return true;
      }

      @Override
      public float scale() {
         return SuiteConfig.INSTANCE.RentalsConfig.scale;
      }

      @Override
      public void setScale(float s) {
         SuiteConfig.INSTANCE.RentalsConfig.setScale(s);
         ConfigIO.saveIfDirty();
      }

      @Override
      public int baseW() {
         return Math.max(140, RentalsHud.lastBaseW);
      }

      @Override
      public int baseH() {
         return Math.max(36, RentalsHud.lastBaseH);
      }

      @Override
      public float backgroundOpacity() {
         return SuiteConfig.INSTANCE.RentalsConfig.backgroundOpacity;
      }

      @Override
      public void setBackgroundOpacity(float opacity) {
         SuiteConfig.INSTANCE.RentalsConfig.backgroundOpacity = Math.max(0.0F, Math.min(1.0F, opacity));
         ConfigIO.saveIfDirty();
      }
   };

   private RentalsHud() {
   }

   public static void render(DrawContext ctx, MinecraftClient client) {
      RentalsConfig cfg = SuiteConfig.INSTANCE.RentalsConfig;
      if (cfg.showHud) {
         if (client != null && client.player != null) {
            TextRenderer tr = client.textRenderer;
            long now = System.currentTimeMillis();
            List<RentalsConfig.RentalEntry> entries = visibleEntries(cfg, now);
            if (!entries.isEmpty() || HudEditState.editMode) {
               int headerH = 14;
               int pad = 4;
               int rowH = 9;
               int rowGap = 4;
               List<RentalsHud.Line> lines = new ArrayList<>();
               if (entries.isEmpty()) {
                  lines.add(new RentalsHud.Line("(no rentals)", -5197648));
               } else {
                  for (RentalsConfig.RentalEntry entry : entries) {
                     long remaining = entry.remainingMs(now);
                     int color = entry.paused ? -11410 : (remaining <= 0L ? -37266 : -1);
                     lines.add(new RentalsHud.Line(shorten(entry.itemName, 18) + ": " + formatRemaining(remaining), color));
                     lines.add(new RentalsHud.Line("  " + shorten(entry.location, 24), -5197648));
                  }
               }

               String header = allActiveEntriesPaused(entries, now) ? "Rentals - Paused" : "Rentals";
               int baseW = Math.max(140, tr.getWidth(header) + 12);

               for (RentalsHud.Line line : lines) {
                  baseW = Math.max(baseW, tr.getWidth(line.text) + 12);
               }

               baseW = Math.min(260, baseW);
               int baseH = 2 + headerH + pad + lines.size() * rowH + Math.max(0, lines.size() - 1) * rowGap + pad + 2;
               lastBaseW = baseW;
               lastBaseH = baseH;
               int screenW = client.getWindow().getScaledWidth();
               int screenH = client.getWindow().getScaledHeight();
               float scale = HudScaleUtil.scaleFor(cfg.scale, 0.1F, 2.0F, baseW, baseH, screenW, screenH);
               int w = Math.round(baseW * scale);
               int h = Math.round(baseH * scale);
               int defaultX = screenW - w - 6;
               int defaultY = 44;
               int x;
               int y;
               if (!(cfg.positionX < 0.0F) && !(cfg.positionY < 0.0F)) {
                  x = Math.round(cfg.positionX * Math.max(0, screenW - w));
                  y = Math.round(cfg.positionY * Math.max(0, screenH - h));
               } else {
                  x = defaultX;
                  y = defaultY;
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
                  ctx.fill(0, 0, baseW, headerH, HudStyleUtil.panelHeader(opacity));
                  ctx.fill(0, headerH, baseW, headerH + 1, HudStyleUtil.panelDivider(opacity));
                  ctx.drawTextWithShadow(tr, header, 6, 4, -1);
                  int yy = headerH + pad;

                  for (RentalsHud.Line line : lines) {
                     ctx.drawTextWithShadow(tr, line.text, 6, yy, line.color);
                     yy += rowH + rowGap;
                  }
               } finally {
                  matrices.popMatrix();
               }
            }
         }
      }
   }

   private static List<RentalsConfig.RentalEntry> visibleEntries(RentalsConfig cfg, long now) {
      List<RentalsConfig.RentalEntry> out = new ArrayList<>(cfg.entries);
      out.sort(Comparator.comparingLong(entry -> entry.remainingMs(now)));
      return out;
   }

   public static String formatRemaining(long ms) {
      if (ms <= 0L) {
         return "DONE";
      }

      long totalSeconds = (ms + 999L) / 1000L;
      long minutes = totalSeconds / 60L;
      long seconds = totalSeconds % 60L;
      long hours = minutes / 60L;
      minutes %= 60L;
      return hours > 0L ? hours + "h " + minutes + "m" : minutes + "m " + seconds + "s";
   }

   private static boolean allActiveEntriesPaused(List<RentalsConfig.RentalEntry> entries, long now) {
      boolean foundActive = false;

      for (RentalsConfig.RentalEntry entry : entries) {
         if (!entry.finished(now)) {
            foundActive = true;
            if (!entry.paused) {
               return false;
            }
         }
      }

      return foundActive;
   }

   private static String shorten(String raw, int max) {
      String s = raw != null && !raw.isBlank() ? raw.trim() : "-";
      return s.length() <= max ? s : s.substring(0, Math.max(1, max - 3)) + "...";
   }

   private record Line(String text, int color) {
   }
}
