package org.blossomsuite.core.hud;

import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.keybinds.KeybindUtil;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public final class HudEditScreen extends Screen {
   private DraggableHud active = null;
   private int grabOffX = 0;
   private int grabOffY = 0;
   private boolean dragging = false;
   private boolean resizing = false;
   private float resizeStartScale = 1.0F;
   private int resizeStartMouseX = 0;
   private int resizeStartMouseY = 0;
   private static float resizeGrabOffX = 0.0F;
   private static float resizeGrabOffY = 0.0F;
   private boolean draggingOpacity = false;
   private int sliderX;
   private int sliderY;
   private int sliderWidth;
   private int sliderHeight;
   private static final int RESIZE_HANDLE = 10;
   public static boolean requestOpenEditScreen = false;
   public static boolean requestCloseEditScreen = false;
   private final List<DraggableHud> panels = List.of(
      JobsHud.DRAGGABLE,
      CooldownsHud.DRAGGABLE,
      CoordsHud.DRAGGABLE,
      BiomeHud.DRAGGABLE,
      AutoSwapperHud.DRAGGABLE,
      HolePuncherHud.DRAGGABLE,
      VoteHud.DRAGGABLE,
      RentalsHud.DRAGGABLE,
      ChatHud.DRAGGABLE,
      ScoreboardHud.DRAGGABLE,
      XpTrackerHud.INSTANCE,
      ExtraHotbarHud.INSTANCE,
      SecondaryChatHud.INSTANCE,
      ChatWindowHud.WINDOWS[0],
      ChatWindowHud.WINDOWS[1],
      ChatWindowHud.WINDOWS[2],
      ChatWindowHud.WINDOWS[3],
      MapArtPreviewHud.INSTANCE,
      MapArtListHud.INSTANCE,
      EmoteTimerHud.INSTANCE
   );

   public HudEditScreen() {
      super(Text.literal(SuiteRuntime.profile().displayName() + " HUD Editor"));
   }

   @Override
   public boolean shouldPause() {
      return false;
   }

   @Override
   public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
      ctx.fill(0, 0, this.width, this.height, 570425344);
      JobsHud.render(ctx, MinecraftClient.getInstance());
      CooldownsHud.renderPanel(ctx, MinecraftClient.getInstance());
      CooldownsHud.renderHotbar(ctx, MinecraftClient.getInstance());
      CoordsHud.render(ctx, MinecraftClient.getInstance());
      BiomeHud.render(ctx, MinecraftClient.getInstance());
      AutoSwapperHud.render(ctx, MinecraftClient.getInstance());
      HolePuncherHud.render(ctx, MinecraftClient.getInstance());
      VoteHud.render(ctx, MinecraftClient.getInstance());
      RentalsHud.render(ctx, MinecraftClient.getInstance());
      ChatHud.render(ctx, MinecraftClient.getInstance());
      XpTrackerHud.INSTANCE.render(ctx, MinecraftClient.getInstance());
      ExtraHotbarHud.INSTANCE.render(ctx, MinecraftClient.getInstance());
      SecondaryChatHud.INSTANCE.render(ctx, MinecraftClient.getInstance());
      for (ChatWindowHud window : ChatWindowHud.WINDOWS) {
         window.render(ctx, MinecraftClient.getInstance());
      }
      MapArtPreviewHud.INSTANCE.render(ctx, MinecraftClient.getInstance());
      MapArtListHud.INSTANCE.render(ctx, MinecraftClient.getInstance());
      EmoteTimerHud.INSTANCE.render(ctx, MinecraftClient.getInstance());


      for (DraggableHud p : this.panels) {
         if (p.enabled()) {
            int x = p.x();
            int y = p.y();
            int w = p.w();
            int h = p.h();
            int c = p == this.active ? -1426063361 : 1728053247;
            ctx.fill(x, y, x + w, y + 1, c);
            ctx.fill(x, y + h - 1, x + w, y + h, c);
            ctx.fill(x, y, x + 1, y + h, c);
            ctx.fill(x + w - 1, y, x + w, y + h, c);
            if (canResize(p)) {
               int hx1 = x + w - 10 - 2;
               int hy1 = y + h - 10 - 2;
               int hx2 = x + w - 2;
               int hy2 = y + h - 2;
               ctx.fill(hx1, hy1, hx2, hy2, p == this.active && this.resizing ? -855638017 : -1711276033);
            }
         }
      }

      if (this.active != null && this.active.supportsBackgroundOpacity()) {
         MinecraftClient client = MinecraftClient.getInstance();
         float hudScale = this.active.scale();
         float sliderScale = Math.max(0.85F, Math.min(hudScale, 1.5F));
         this.sliderWidth = Math.round(100.0F * sliderScale);
         this.sliderHeight = Math.round(10.0F * sliderScale);
         int offset = Math.round(4.0F * sliderScale);
         this.sliderX = this.active.x() + (this.active.w() - this.sliderWidth) / 2;
         int desiredAboveY = this.active.y() - this.sliderHeight - offset;
         int desiredBelowY = this.active.y() + this.active.h() + offset;
         int screenW = client.getWindow().getScaledWidth();
         int screenH = client.getWindow().getScaledHeight();
         this.sliderX = Math.max(5, Math.min(this.sliderX, screenW - this.sliderWidth - 5));
         if (desiredAboveY >= 5) {
            this.sliderY = desiredAboveY;
         } else if (desiredBelowY + this.sliderHeight <= screenH - 5) {
            this.sliderY = desiredBelowY;
         } else {
            this.sliderY = Math.max(5, Math.min(desiredBelowY, screenH - this.sliderHeight - 5));
         }

         float opacity = this.active.backgroundOpacity();
         int filled = Math.round(this.sliderWidth * opacity);
         ctx.fill(this.sliderX, this.sliderY, this.sliderX + this.sliderWidth, this.sliderY + this.sliderHeight, 1711276032);
         ctx.fill(this.sliderX, this.sliderY, this.sliderX + filled, this.sliderY + this.sliderHeight, -16733441);
         String text = Math.round(opacity * 100.0F) + "%";
         ctx.drawTextWithShadow(client.textRenderer, text, this.sliderX + this.sliderWidth + Math.round(6.0F * sliderScale), this.sliderY - 1, -1);
      }

      super.render(ctx, mouseX, mouseY, delta);
   }

   private boolean isInsideSlider(double mouseX, double mouseY) {
      return mouseX >= this.sliderX && mouseX <= this.sliderX + this.sliderWidth && mouseY >= this.sliderY && mouseY <= this.sliderY + this.sliderHeight;
   }

   private boolean overResizeHandle(DraggableHud p, double mouseX, double mouseY) {
      int x = p.x();
      int y = p.y();
      int w = p.w();
      int h = p.h();
      int hx1 = x + w - 10 - 2;
      int hy1 = y + h - 10 - 2;
      int hx2 = x + w - 2;
      int hy2 = y + h - 2;
      return mouseX >= hx1 && mouseX <= hx2 && mouseY >= hy1 && mouseY <= hy2;
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (button != 0) {
         return super.mouseClicked(mouseX, mouseY, button);
      }

      if (this.active != null && this.active.supportsBackgroundOpacity() && this.isInsideSlider(mouseX, mouseY)) {
         this.draggingOpacity = true;
         this.dragging = false;
         this.resizing = false;
         this.updateOpacityFromMouse(mouseX);
         return true;
      }

      for (int i = this.panels.size() - 1; i >= 0; i--) {
         DraggableHud p = this.panels.get(i);
         if (p.enabled()) {
            int x = p.x();
            int y = p.y();
            int w = p.w();
            int h = p.h();
            if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h) {
               this.active = p;
               this.draggingOpacity = false;
               if (canResize(p) && this.overResizeHandle(p, mouseX, mouseY)) {
                  this.resizing = true;
                  this.dragging = false;
                  this.resizeStartScale = p.scale();
                  this.resizeStartMouseX = (int)mouseX;
                  this.resizeStartMouseY = (int)mouseY;
                  resizeGrabOffX = (float)mouseX - (this.active.x() + this.active.w());
                  resizeGrabOffY = (float)mouseY - (this.active.y() + this.active.h());
                  return true;
               }

               this.dragging = true;
               this.resizing = false;
               this.grabOffX = (int)mouseX - x;
               this.grabOffY = (int)mouseY - y;
               return true;
            }
         }
      }

      this.draggingOpacity = false;
      this.active = null;
      this.dragging = false;
      this.resizing = false;
      return super.mouseClicked(mouseX, mouseY, button);
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
      if (this.draggingOpacity) {
         if (this.active != null && this.active.supportsBackgroundOpacity()) {
            this.updateOpacityFromMouse(mouseX);
            return true;
         }

         this.draggingOpacity = false;
      }

      if (this.active == null) {
         return super.mouseDragged(mouseX, mouseY, button, dx, dy);
      }

      MinecraftClient client = MinecraftClient.getInstance();
      int screenW = client.getWindow().getScaledWidth();
      int screenH = client.getWindow().getScaledHeight();
      if (this.resizing && canResize(this.active)) {
         int px = this.active.x();
         int py = this.active.y();
         float baseW = Math.max(1.0F, this.active.baseW());
         float baseH = Math.max(1.0F, this.active.baseH());
         float desiredW = (float)mouseX - resizeGrabOffX - px;
         float desiredH = (float)mouseY - resizeGrabOffY - py;
         desiredW = Math.max(this.active.minPixelWidth(), desiredW);
         desiredH = Math.max(this.active.minPixelHeight(), desiredH);
         float sx = desiredW / baseW;
         float sy = desiredH / baseH;
         float newDrawnScale = Math.max(sx, sy);
         float newScale = HudScaleUtil.savedScaleForDrawnScale(newDrawnScale, this.active.minScale(), this.active.maxScale());
         if (newScale < this.active.minScale()) {
            newScale = this.active.minScale();
         }

         if (newScale > this.active.maxScale()) {
            newScale = this.active.maxScale();
         }

         this.active.setScale(newScale);
         float effectiveScale = HudScaleUtil.scaleFor(newScale, this.active.minScale(), this.active.maxScale(), (int)baseW, (int)baseH, screenW, screenH);
         int newW = Math.round(baseW * effectiveScale);
         int newH = Math.round(baseH * effectiveScale);
         setPanelTopLeft(this.active, px, py, screenW, screenH, newW, newH);
         return true;
      } else {
         if (!this.dragging) {
            return super.mouseDragged(mouseX, mouseY, button, dx, dy);
         }

         int w = this.active.w();
         int h = this.active.h();
         int maxX = Math.max(0, screenW - w);
         int maxY = Math.max(0, screenH - h);
         int newX = (int)mouseX - this.grabOffX;
         int newY = (int)mouseY - this.grabOffY;
         if (newX < 0) {
            newX = 0;
         }

         if (newY < 0) {
            newY = 0;
         }

         if (newX > maxX) {
            newX = maxX;
         }

         if (newY > maxY) {
            newY = maxY;
         }

         float nx = maxX == 0 ? 0.0F : (float)newX / maxX;
         float ny = maxY == 0 ? 0.0F : (float)newY / maxY;
         this.active.setPos(nx, ny);
         return true;
      }
   }

   private static void setPanelTopLeft(DraggableHud panel, int x, int y, int screenW, int screenH, int w, int h) {
      if (panel != null) {
         int maxX = Math.max(0, screenW - Math.max(0, w));
         int maxY = Math.max(0, screenH - Math.max(0, h));
         int clampedX = Math.max(0, Math.min(x, maxX));
         int clampedY = Math.max(0, Math.min(y, maxY));
         float nx = maxX == 0 ? 0.0F : (float)clampedX / maxX;
         float ny = maxY == 0 ? 0.0F : (float)clampedY / maxY;
         panel.setPos(nx, ny);
      }
   }

   private static boolean canResize(DraggableHud panel) {
      return panel != null && panel.resizable() && SuiteConfig.INSTANCE.hudScalingMode != SuiteConfig.HudScalingMode.GUI_ADAPTIVE;
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      this.dragging = false;
      this.resizing = false;
      this.draggingOpacity = false;
      ConfigIO.saveIfDirty();
      return super.mouseReleased(mouseX, mouseY, button);
   }

   private void updateOpacityFromMouse(double mouseX) {
      float t = (float)(mouseX - this.sliderX) / this.sliderWidth;
      t = Math.max(0.0F, Math.min(1.0F, t));
      this.active.setBackgroundOpacity(t);
   }

   @Override
   public void close() {
      HudEditState.editMode = false;
      this.dragging = false;
      this.resizing = false;
      ConfigIO.saveIfDirty();
      super.close();
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (KeybindUtil.matchesKeyPressed(SuiteConfig.INSTANCE.KeybindsConfig.toggleEditMode, keyCode, modifiers)) {
         MinecraftClient client = MinecraftClient.getInstance();
         HudEditState.editMode = false;
         client.setScreen(null);
         ConfigIO.saveIfDirty();
         return true;
      } else {
         return super.keyPressed(keyCode, scanCode, modifiers);
      }
   }
}
