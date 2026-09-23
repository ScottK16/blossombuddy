package org.blossomsuite.core.hud;

import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.util.HudStyleUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

public final class BiomeHud {
   public static final DraggableHud DRAGGABLE = new DraggableHud() {
      @Override
      public String id() {
         return "biome";
      }

      @Override
      public int x() {
         return BiomeHud.lastX;
      }

      @Override
      public int y() {
         return BiomeHud.lastY;
      }

      @Override
      public int w() {
         return BiomeHud.lastW;
      }

      @Override
      public int h() {
         return BiomeHud.lastH;
      }

      @Override
      public float posX() {
         return SuiteConfig.INSTANCE.BiomeHudConfig.positionX;
      }

      @Override
      public float posY() {
         return SuiteConfig.INSTANCE.BiomeHudConfig.positionY;
      }

      @Override
      public void setPos(float nx, float ny) {
         SuiteConfig.INSTANCE.BiomeHudConfig.setPosition(nx, ny);
         ConfigIO.saveIfDirty();
      }

      @Override
      public boolean enabled() {
         return SuiteConfig.INSTANCE.BiomeHudConfig.showHud;
      }

      @Override
      public boolean resizable() {
         return true;
      }

      @Override
      public float scale() {
         return SuiteConfig.INSTANCE.BiomeHudConfig.scale;
      }

      @Override
      public void setScale(float s) {
         SuiteConfig.INSTANCE.BiomeHudConfig.setScale(s);
         ConfigIO.saveIfDirty();
      }

      @Override
      public int baseW() {
         return BiomeHud.lastBaseW > 0 ? BiomeHud.lastBaseW : 110;
      }

      @Override
      public int baseH() {
         return BiomeHud.lastBaseH > 0 ? BiomeHud.lastBaseH : BiomeHud.fallbackBaseH();
      }

      @Override
      public float backgroundOpacity() {
         return SuiteConfig.INSTANCE.BiomeHudConfig.backgroundOpacity;
      }

      @Override
      public void setBackgroundOpacity(float opacity) {
         SuiteConfig.INSTANCE.BiomeHudConfig.backgroundOpacity = Math.max(0.0F, Math.min(1.0F, opacity));
         ConfigIO.saveIfDirty();
      }
   };
   public static int lastX;
   public static int lastY;
   public static int lastW;
   public static int lastH;
   private static int lastBaseW = 110;
   private static int lastBaseH = 0;

   private BiomeHud() {
   }

   private static int fallbackBaseH() {
      return !SuiteConfig.INSTANCE.BiomeHudConfig.showHeader ? 21 : 38;
   }

   public static void render(DrawContext ctx, MinecraftClient client) {
      if (SuiteConfig.INSTANCE.BiomeHudConfig.showHud) {
         if (client != null && client.player != null && client.world != null) {
            int screenH = client.getWindow().getScaledHeight();
            int screenW = client.getWindow().getScaledWidth();
            TextRenderer tr = client.textRenderer;
            String header = "Biome";
            String row = currentBiomeName(client);
            boolean showHeader = SuiteConfig.INSTANCE.BiomeHudConfig.showHeader;
            int headerH = 14;
            int pad = 4;
            int rowH = 9;
            int baseH = showHeader ? 2 + headerH + pad + rowH + pad + 2 : 2 + pad + rowH + pad + 2;
            int textW = showHeader ? Math.max(tr.getWidth(header), tr.getWidth(row)) : tr.getWidth(row);
            int baseW = Math.max(90, textW + 12);
            if (baseW > 260) {
               baseW = 260;
            }

            lastBaseW = baseW;
            lastBaseH = baseH;
            float scale = HudScaleUtil.scaleFor(SuiteConfig.INSTANCE.BiomeHudConfig.scale, 0.1F, 2.0F, baseW, baseH, screenW, screenH);
            int w = Math.round(baseW * scale);
            int h = Math.round(baseH * scale);
            int defaultX = 6;
            int defaultY = 38;
            int x;
            int y;
            if (!(SuiteConfig.INSTANCE.BiomeHudConfig.positionX < 0.0F) && !(SuiteConfig.INSTANCE.BiomeHudConfig.positionY < 0.0F)) {
               int maxX = Math.max(0, screenW - w);
               int maxY = Math.max(0, screenH - h);
               x = Math.round(SuiteConfig.INSTANCE.BiomeHudConfig.positionX * maxX);
               y = Math.round(SuiteConfig.INSTANCE.BiomeHudConfig.positionY * maxY);
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
               float opacity = SuiteConfig.INSTANCE.BiomeHudConfig.backgroundOpacity;
               ctx.fill(0, 0, baseW, baseH, HudStyleUtil.panelBg(opacity));
               if (showHeader) {
                  ctx.fill(0, 0, baseW, headerH, HudStyleUtil.panelHeader(opacity));
                  ctx.fill(0, headerH, baseW, headerH + 1, HudStyleUtil.panelDivider(opacity));
                  ctx.drawTextWithShadow(tr, header, 6, 4, -1);
               }

               int rowY = showHeader ? headerH + pad : 2 + pad;
               int rowW = tr.getWidth(row);
               int rowX = baseW / 2 - rowW / 2;
               ctx.drawTextWithShadow(tr, row, rowX, rowY, -1);
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

   private static String currentBiomeName(MinecraftClient client) {
      return client.world.getBiome(client.player.getBlockPos()).getKey().map(RegistryKey::getValue).map(BiomeHud::formatBiomeName).orElse("Unknown");
   }

   private static String formatBiomeName(Identifier id) {
      if (id == null) {
         return "Unknown";
      }

      String path = id.getPath();
      if (path != null && !path.isBlank()) {
         String[] words = path.split("_");
         StringBuilder out = new StringBuilder();

         for (String word : words) {
            if (word != null && !word.isBlank()) {
               if (!out.isEmpty()) {
                  out.append(' ');
               }

               out.append(Character.toUpperCase(word.charAt(0)));
               if (word.length() > 1) {
                  out.append(word.substring(1));
               }
            }
         }

         return out.isEmpty() ? path : out.toString();
      } else {
         return "Unknown";
      }
   }
}
