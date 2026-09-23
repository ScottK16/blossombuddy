package org.blossomsuite.core.hud;

import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.blossomsuite.core.config.FeatureConfig;
import org.blossomsuite.core.util.HudStyleUtil;
import org.joml.Matrix3x2fStack;

/**
 * Base for the HUD panels added in BlossomBuddy. It draws content in local (unscaled) coordinates inside a
 * moved and scaled matrix, remembers where it landed, and exposes that to the HUD editor for dragging and resizing.
 */
public abstract class PanelHud implements DraggableHud {
   protected int lastX;
   protected int lastY;
   protected int lastW;
   protected int lastH;
   protected int lastBaseW = 100;
   protected int lastBaseH = 24;

   protected abstract FeatureConfig.Panel panel();

   protected abstract boolean shown();

   /** Black-tinted translucent by default; a panel can tint white (or anything else) instead by overriding this. */
   protected int backgroundColor(float opacity) {
      return HudStyleUtil.panelBg(opacity);
   }

   /** Where the panel goes until the player drags it somewhere. */
   protected int[] defaultTopLeft(int screenW, int screenH, int w, int h) {
      return new int[]{6, 6};
   }

   protected final void draw(DrawContext ctx, MinecraftClient client, int baseW, int baseH, boolean background, Consumer<DrawContext> content) {
      int screenW = client.getWindow().getScaledWidth();
      int screenH = client.getWindow().getScaledHeight();
      FeatureConfig.Panel p = this.panel();
      this.lastBaseW = baseW;
      this.lastBaseH = baseH;
      float scale = HudScaleUtil.scaleFor(p.scale, 0.1F, 2.0F, baseW, baseH, screenW, screenH);
      int w = Math.round(baseW * scale);
      int h = Math.round(baseH * scale);
      int x;
      int y;
      if (p.x < 0.0F || p.y < 0.0F) {
         int[] d = this.defaultTopLeft(screenW, screenH, w, h);
         x = d[0];
         y = d[1];
      } else {
         x = Math.round(p.x * Math.max(0, screenW - w));
         y = Math.round(p.y * Math.max(0, screenH - h));
      }

      this.lastX = x;
      this.lastY = y;
      this.lastW = w;
      this.lastH = h;
      Matrix3x2fStack matrices = ctx.getMatrices();
      matrices.pushMatrix();
      matrices.translate(x, y);
      matrices.scale(scale, scale);

      try {
         if (background) {
            ctx.fill(0, 0, baseW, baseH, this.backgroundColor(p.opacity));
         }

         content.accept(ctx);
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

   @Override
   public int x() {
      return this.lastX;
   }

   @Override
   public int y() {
      return this.lastY;
   }

   @Override
   public int w() {
      return this.lastW;
   }

   @Override
   public int h() {
      return this.lastH;
   }

   @Override
   public float posX() {
      return this.panel().x;
   }

   @Override
   public float posY() {
      return this.panel().y;
   }

   @Override
   public void setPos(float nx, float ny) {
      this.panel().x = nx;
      this.panel().y = ny;
      FeatureConfig.markDirty();
   }

   @Override
   public boolean enabled() {
      return this.shown();
   }

   @Override
   public boolean resizable() {
      return true;
   }

   @Override
   public float scale() {
      return this.panel().scale;
   }

   @Override
   public void setScale(float s) {
      this.panel().scale = Math.max(this.minScale(), Math.min(this.maxScale(), s));
      FeatureConfig.markDirty();
   }

   @Override
   public int baseW() {
      return this.lastBaseW;
   }

   @Override
   public int baseH() {
      return this.lastBaseH;
   }

   @Override
   public float backgroundOpacity() {
      return this.panel().opacity;
   }

   @Override
   public void setBackgroundOpacity(float opacity) {
      this.panel().opacity = Math.max(0.0F, Math.min(1.0F, opacity));
      FeatureConfig.markDirty();
   }
}
