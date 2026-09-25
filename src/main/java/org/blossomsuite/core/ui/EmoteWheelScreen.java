package org.blossomsuite.core.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.blossomsuite.core.emote.Emote;
import org.blossomsuite.core.emote.EmoteClient;
import org.blossomsuite.core.presence.PresenceClient;
import org.lwjgl.glfw.GLFW;

/**
 * The emote menu: eight emotes a page as picture tiles (a still from the clip on the website), with pages for the rest. Click one (or
 * press its number) to start it; it loops until you click it again, press Stop, or use the Stop Emote key. The arrow keys, the mouse
 * wheel and the arrows under the tiles turn the page.
 */
public final class EmoteWheelScreen extends Screen {
   /** How many emotes fit on a page: four across, two down. */
   static final int PAGE_SIZE = 8;
   private static final int COLUMNS = 4;
   private static final int GAP = 6;
   private static final int LABEL_H = 12;
   private static final int STOP_W = 60;
   private static final int STOP_H = 16;
   /** The stills are 180 x 140. */
   private static final int IMAGE_W = 180;
   private static final int IMAGE_H = 140;

   private int page = 0;

   public EmoteWheelScreen() {
      super(Text.literal("Emotes"));
   }

   @Override
   public boolean shouldPause() {
      return false;
   }

   /** Where an emote's picture lives inside the mod. */
   public static Identifier imageOf(Emote emote) {
      return Identifier.of("blossombuddy", "textures/emote/" + emote.id() + ".png");
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

   /** As big as the window allows, so a small window still fits four tiles across. */
   private int tileW() {
      return Math.max(44, Math.min(78, (this.width - 40) / COLUMNS - GAP));
   }

   private int tileH() {
      return this.tileW() * IMAGE_H / IMAGE_W;
   }

   private int cellH() {
      return this.tileH() + LABEL_H;
   }

   private int gridW() {
      return COLUMNS * this.tileW() + (COLUMNS - 1) * GAP;
   }

   private int gridH() {
      return 2 * this.cellH() + GAP;
   }

   private int gridTop() {
      return Math.max(22, (this.height - this.gridH() - 58) / 2);
   }

   private int slotX(int i) {
      return this.width / 2 - this.gridW() / 2 + (i % COLUMNS) * (this.tileW() + GAP);
   }

   private int slotY(int i) {
      return this.gridTop() + (i / COLUMNS) * (this.cellH() + GAP);
   }

   private int controlsY() {
      return this.gridTop() + this.gridH() + 8;
   }

   private static boolean inside(double mx, double my, int x, int y, int w, int h) {
      return mx >= x && mx < x + w && my >= y && my < y + h;
   }

   private int stopX() {
      return this.width / 2 - STOP_W / 2;
   }

   private int stopY() {
      return this.controlsY();
   }

   private boolean inPrev(double mx, double my) {
      return this.page > 0 && inside(mx, my, this.width / 2 - this.gridW() / 2, this.controlsY(), 22, 16);
   }

   private boolean inNext(double mx, double my) {
      return this.page < pageCount(Emote.ALL.size()) - 1 && inside(mx, my, this.width / 2 + this.gridW() / 2 - 22, this.controlsY(), 22, 16);
   }

   // ------------------------------------------------------------------ drawing

   @Override
   public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
      super.render(ctx, mouseX, mouseY, delta);
      ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Emotes").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), this.width / 2, Math.max(4, this.gridTop() - 14), -1);

      List<Emote> emotes = this.pageEmotes();
      int tw = this.tileW();
      int th = this.tileH();
      for (int i = 0; i < emotes.size(); i++) {
         int x = this.slotX(i);
         int y = this.slotY(i);
         boolean hovered = inside(mouseX, mouseY, x, y, tw, this.cellH());
         ctx.fill(x - 2, y - 2, x + tw + 2, y + this.cellH(), hovered ? 0xCC7A3F8F : 0xAA2A1533);
         ctx.drawTexture(RenderPipelines.GUI_TEXTURED, imageOf(emotes.get(i)), x, y, 0.0F, 0.0F, tw, th, IMAGE_W, IMAGE_H, IMAGE_W, IMAGE_H);
         Text label = Text.literal((i + 1) + " " + emotes.get(i).label()).formatted(hovered ? Formatting.WHITE : Formatting.LIGHT_PURPLE);
         ctx.drawCenteredTextWithShadow(this.textRenderer, label, x + tw / 2, y + th + 3, -1);
      }

      int cy = this.controlsY();
      boolean stopHover = inside(mouseX, mouseY, this.stopX(), this.stopY(), STOP_W, STOP_H);
      ctx.fill(this.stopX(), this.stopY(), this.stopX() + STOP_W, this.stopY() + STOP_H, stopHover ? 0xCC8F3F3F : 0x88331515);
      ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Stop").formatted(Formatting.WHITE), this.width / 2, this.stopY() + 4, -1);

      int pages = pageCount(Emote.ALL.size());
      if (pages > 1) {
         this.drawArrow(ctx, this.width / 2 - this.gridW() / 2, cy, "<", this.page > 0, inPrev(mouseX, mouseY));
         this.drawArrow(ctx, this.width / 2 + this.gridW() / 2 - 22, cy, ">", this.page < pages - 1, inNext(mouseX, mouseY));
         ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Page " + (this.page + 1) + " / " + pages).formatted(Formatting.GRAY), this.width / 2, cy + STOP_H + 4, -1);
      }

      boolean connected = PresenceClient.INSTANCE.state() == PresenceClient.State.READY;
      String hint = (connected ? "Others on your realm can see them. " : "Others can't see yours until the player list connects (/buddy who). ")
         + "Emotes loop until you stop them.";
      ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(hint).formatted(Formatting.GRAY), this.width / 2, Math.min(this.height - 12, cy + STOP_H + 18), -1);
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
            if (inside(mouseX, mouseY, this.slotX(i), this.slotY(i), this.tileW(), this.cellH())) {
               EmoteClient.INSTANCE.toggle(emotes.get(i));
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
         EmoteClient.INSTANCE.toggle(emotes.get(index));
         this.close();
         return true;
      }

      return super.keyPressed(keyCode, scanCode, modifiers);
   }
}
