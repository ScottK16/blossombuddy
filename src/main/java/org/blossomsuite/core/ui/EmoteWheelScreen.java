package org.blossomsuite.core.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.blossomsuite.core.emote.Emote;
import org.blossomsuite.core.emote.EmoteClient;
import org.blossomsuite.core.presence.PresenceClient;
import org.lwjgl.glfw.GLFW;

/**
 * The emote wheel: up to eight emotes arranged in an oval, with pages for the rest. Click one (or press its number) to play it; click
 * Stop, or just walk, to end it. The arrow keys, the mouse wheel and the arrows under the wheel turn the page.
 */
public final class EmoteWheelScreen extends Screen {
   /** How many emotes fit around the wheel. */
   static final int PAGE_SIZE = 8;
   private static final int SLOT_W = 80;
   private static final int SLOT_H = 22;
   private static final int STOP_W = 60;
   private static final int STOP_H = 16;

   private int page = 0;

   public EmoteWheelScreen() {
      super(Text.literal("Emotes"));
   }

   @Override
   public boolean shouldPause() {
      return false;
   }

   // ------------------------------------------------------------------ layout

   static int pageCount(int emotes) {
      return Math.max(1, (emotes + PAGE_SIZE - 1) / PAGE_SIZE);
   }

   private List<Emote> pageEmotes() {
      List<Emote> out = new ArrayList<>();
      for (int i = this.page * PAGE_SIZE; i < Math.min(Emote.ALL.size(), (this.page + 1) * PAGE_SIZE); i++) {
         out.add(Emote.ALL.get(i));
      }

      return out;
   }

   /** The oval is as wide and tall as the window allows, so the slots never crowd each other or the middle. */
   private int radiusX() {
      return Math.max(120, Math.min(210, (int)(this.width * 0.36)));
   }

   private int radiusY() {
      return Math.max(58, Math.min(100, (int)(this.height * 0.28)));
   }

   /** Eight fixed positions, so an emote stays in the same place whatever else is on the page. */
   private int slotX(int i) {
      double angle = -Math.PI / 2.0 + 2.0 * Math.PI * i / PAGE_SIZE;
      return this.width / 2 + (int)Math.round(Math.cos(angle) * this.radiusX()) - SLOT_W / 2;
   }

   private int slotY(int i) {
      double angle = -Math.PI / 2.0 + 2.0 * Math.PI * i / PAGE_SIZE;
      return this.height / 2 + (int)Math.round(Math.sin(angle) * this.radiusY()) - SLOT_H / 2;
   }

   private int controlsY() {
      return Math.min(this.height - 34, this.height / 2 + this.radiusY() + SLOT_H / 2 + 8);
   }

   private static boolean inside(double mx, double my, int x, int y, int w, int h) {
      return mx >= x && mx < x + w && my >= y && my < y + h;
   }

   private int stopX() {
      return this.width / 2 - STOP_W / 2;
   }

   private int stopY() {
      return this.height / 2 + 2;
   }

   private boolean inPrev(double mx, double my) {
      return this.page > 0 && inside(mx, my, this.width / 2 - 62, this.controlsY(), 22, 16);
   }

   private boolean inNext(double mx, double my) {
      return this.page < pageCount(Emote.ALL.size()) - 1 && inside(mx, my, this.width / 2 + 40, this.controlsY(), 22, 16);
   }

   // ------------------------------------------------------------------ drawing

   @Override
   public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
      super.render(ctx, mouseX, mouseY, delta);
      List<Emote> emotes = this.pageEmotes();
      for (int i = 0; i < emotes.size(); i++) {
         int x = this.slotX(i);
         int y = this.slotY(i);
         boolean hovered = inside(mouseX, mouseY, x, y, SLOT_W, SLOT_H);
         ctx.fill(x, y, x + SLOT_W, y + SLOT_H, hovered ? 0xCC7A3F8F : 0xAA2A1533);
         ctx.fill(x, y, x + SLOT_W, y + 1, hovered ? 0xFFF48FB1 : 0x66F48FB1);
         Text label = Text.literal((i + 1) + "  " + emotes.get(i).label()).formatted(hovered ? Formatting.WHITE : Formatting.LIGHT_PURPLE);
         ctx.drawCenteredTextWithShadow(this.textRenderer, label, x + SLOT_W / 2, y + 7, -1);
      }

      ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Emotes").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), this.width / 2, this.height / 2 - 12, -1);
      boolean stopHover = inside(mouseX, mouseY, this.stopX(), this.stopY(), STOP_W, STOP_H);
      ctx.fill(this.stopX(), this.stopY(), this.stopX() + STOP_W, this.stopY() + STOP_H, stopHover ? 0xCC8F3F3F : 0x88331515);
      ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Stop").formatted(Formatting.WHITE), this.width / 2, this.stopY() + 4, -1);

      // page arrows and the note underneath, clear of every slot
      int cy = this.controlsY();
      int pages = pageCount(Emote.ALL.size());
      if (pages > 1) {
         this.drawArrow(ctx, this.width / 2 - 62, cy, "<", this.page > 0, inPrev(mouseX, mouseY));
         this.drawArrow(ctx, this.width / 2 + 40, cy, ">", this.page < pages - 1, inNext(mouseX, mouseY));
         ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Page " + (this.page + 1) + " / " + pages).formatted(Formatting.GRAY), this.width / 2, cy + 4, -1);
      }

      boolean connected = PresenceClient.INSTANCE.state() == PresenceClient.State.READY;
      String hint = connected ? "Others on your realm can see them." : "Others can't see yours until the player list connects (/buddy who).";
      ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(hint).formatted(Formatting.GRAY), this.width / 2, Math.min(this.height - 12, cy + 22), -1);
   }

   private void drawArrow(DrawContext ctx, int x, int y, String label, boolean active, boolean hovered) {
      ctx.fill(x, y, x + 22, y + 16, active ? (hovered ? 0xCC7A3F8F : 0xAA2A1533) : 0x44221122);
      ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(label).formatted(active ? Formatting.WHITE : Formatting.DARK_GRAY), x + 11, y + 4, -1);
   }

   // ------------------------------------------------------------------ input

   private void turnPage(int by) {
      this.page = Math.max(0, Math.min(pageCount(Emote.ALL.size()) - 1, this.page + by));
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (button == 0) {
         List<Emote> emotes = this.pageEmotes();
         for (int i = 0; i < emotes.size(); i++) {
            if (inside(mouseX, mouseY, this.slotX(i), this.slotY(i), SLOT_W, SLOT_H)) {
               EmoteClient.INSTANCE.play(emotes.get(i));
               this.close();
               return true;
            }
         }

         if (inside(mouseX, mouseY, this.stopX(), this.stopY(), STOP_W, STOP_H)) {
            EmoteClient.INSTANCE.stop();
            this.close();
            return true;
         }

         if (this.inPrev(mouseX, mouseY)) {
            this.turnPage(-1);
            return true;
         }

         if (this.inNext(mouseX, mouseY)) {
            this.turnPage(1);
            return true;
         }
      }

      return super.mouseClicked(mouseX, mouseY, button);
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      if (verticalAmount != 0.0D) {
         this.turnPage(verticalAmount < 0.0D ? 1 : -1);
         return true;
      }

      return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (keyCode == GLFW.GLFW_KEY_RIGHT) {
         this.turnPage(1);
         return true;
      }

      if (keyCode == GLFW.GLFW_KEY_LEFT) {
         this.turnPage(-1);
         return true;
      }

      List<Emote> emotes = this.pageEmotes();
      int index = keyCode - GLFW.GLFW_KEY_1;
      if (index >= 0 && index < emotes.size()) {
         EmoteClient.INSTANCE.play(emotes.get(index));
         this.close();
         return true;
      }

      return super.keyPressed(keyCode, scanCode, modifiers);
   }
}
