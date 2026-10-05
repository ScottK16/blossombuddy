package org.blossomsuite.core.ui;

import java.util.ArrayList;
import net.minecraft.client.input.MouseButtonEvent;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.blossomsuite.core.config.FeatureConfig;
import org.blossomsuite.core.presence.PresenceClient;
import org.blossomsuite.core.xchat.XChatText;

/**
 * Who is using BlossomBuddy right now, by realm: a column for each of the four realms with the players' heads and names. Click
 * a name to open the chat with {@code /xc Name, } ready to send.
 */
public final class PlayerListScreen extends Screen {
   static final String[] REALMS = {"cherry", "spirit", "lotus", "tulip"};
   private static final int TOP = 56;
   private static final int ROW_H = 14;
   private static final int HEAD = 10;
   private static final int BOTTOM_MARGIN = 40;

   public PlayerListScreen() {
      super(Component.literal("BlossomBuddy players"));
   }

   @Override
   protected void init() {
      PresenceClient.INSTANCE.refreshSoon();
      boolean on = PresenceClient.INSTANCE.enabled();
      int y = this.height - 28;
      this.addRenderableWidget(StyledButton.of(Component.literal(on ? "Appear in the list: ON" : "Appear in the list: OFF"), b -> {
         PresenceClient.INSTANCE.setEnabled(!PresenceClient.INSTANCE.enabled());
         this.rebuildWidgets();
      }).dimensions(this.width / 2 - 154, y, 150, 20).build());
      this.addRenderableWidget(StyledButton.of(Component.literal("Done"), b -> this.onClose()).dimensions(this.width / 2 + 4, y, 150, 20).build());
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   // ------------------------------------------------------------------ layout

   /** One thing that can be pointed at: a player, or the "+N more" line at the bottom of a crowded column. */
   private record Cell(int x, int y, int w, PresenceClient.Player player, int more) {
      boolean hit(double mx, double my) {
         return mx >= this.x && mx < this.x + this.w && my >= this.y - 1 && my < this.y + ROW_H - 2;
      }
   }

   private int colW() {
      return Math.max(60, Math.min(160, (this.width - 24) / 4));
   }

   private int startX() {
      return (this.width - (this.colW() * 4 + 6 * 3)) / 2;
   }

   private int maxRows() {
      return Math.max(1, (this.height - BOTTOM_MARGIN - (TOP + 16)) / ROW_H);
   }

   private List<PresenceClient.Player> playersOn(String realm) {
      List<PresenceClient.Player> out = new ArrayList<>();
      for (PresenceClient.Player p : PresenceClient.INSTANCE.players()) {
         if (p.realm().equals(realm)) {
            out.add(p);
         }
      }

      out.sort((a, b) -> a.name().toLowerCase(Locale.ROOT).compareTo(b.name().toLowerCase(Locale.ROOT)));
      return out;
   }

   private List<Cell> cells() {
      List<Cell> cells = new ArrayList<>();
      int rows = this.maxRows();
      for (int i = 0; i < REALMS.length; i++) {
         int x = this.startX() + i * (this.colW() + 6);
         List<PresenceClient.Player> here = this.playersOn(REALMS[i]);
         boolean crowded = here.size() > rows;
         int shown = crowded ? rows - 1 : here.size();
         for (int r = 0; r < shown; r++) {
            cells.add(new Cell(x, TOP + 16 + r * ROW_H, this.colW(), here.get(r), 0));
         }

         if (crowded) {
            cells.add(new Cell(x, TOP + 16 + shown * ROW_H, this.colW(), null, here.size() - shown));
         }
      }

      return cells;
   }

   // ------------------------------------------------------------------ drawing

   @Override
   public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
      super.extractRenderState(ctx, mouseX, mouseY, delta);
      Font tr = this.font;
      ctx.centeredText(tr, Component.literal("BlossomBuddy players").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), this.width / 2, 12, -1);

      PresenceClient presence = PresenceClient.INSTANCE;
      if (!presence.enabled()) {
         this.drawIntro(ctx, tr);
         return;
      }

      ctx.centeredText(tr, Component.literal(presence.status()).withStyle(ChatFormatting.GRAY), this.width / 2, 26, -1);
      String hint = FeatureConfig.INSTANCE.xchat.enabled
         ? "Click a name to say hi across realms."
         : "Cross-realm chat is off. Turn it on with /buddy xchat on to talk to people here.";
      ctx.centeredText(tr, Component.literal(hint).withStyle(ChatFormatting.DARK_GRAY), this.width / 2, 38, -1);

      String me = this.minecraft == null || this.minecraft.getUser() == null ? "" : this.minecraft.getUser().getName();
      for (int i = 0; i < REALMS.length; i++) {
         int x = this.startX() + i * (this.colW() + 6);
         ctx.fill(x, TOP - 4, x + this.colW(), this.height - BOTTOM_MARGIN + 4, 0x66000000);
         List<PresenceClient.Player> here = this.playersOn(REALMS[i]);
         String label = Character.toUpperCase(REALMS[i].charAt(0)) + REALMS[i].substring(1) + " (" + here.size() + ")";
         Component coloured = Component.literal(label).withStyle(s -> s.withBold(true).withColor(XChatText.realmColor(realmOf(label))));
         ctx.text(tr, coloured, x + 4, TOP, -1);
         ctx.fill(x + 2, TOP + 11, x + this.colW() - 2, TOP + 12, 0x55FFFFFF);
      }

      for (Cell c : this.cells()) {
         boolean hovered = c.hit(mouseX, mouseY);
         if (c.player() == null) {
            ctx.text(tr, Component.literal("+" + c.more() + " more").withStyle(ChatFormatting.DARK_GRAY), c.x() + 4, c.y() + 1, -1);
            continue;
         }

         if (hovered) {
            ctx.fill(c.x() + 1, c.y() - 1, c.x() + c.w() - 1, c.y() + ROW_H - 2, 0x33FFFFFF);
         }

         PresenceClient.Player p = c.player();
         PlayerFaceExtractor.extractRenderState(ctx, PlayerHeads.textures(this.minecraft, p.uuid()), c.x() + 3, c.y(), HEAD);
         boolean mine = p.name().equalsIgnoreCase(me);
         Component name = mine ? Component.literal(p.name()).withStyle(ChatFormatting.LIGHT_PURPLE) : Component.literal(p.name());
         ctx.text(tr, name, c.x() + 3 + HEAD + 4, c.y() + 1, -1);
      }
   }

   /** What the screen says before the player has chosen to appear. */
   private void drawIntro(GuiGraphicsExtractor ctx, Font tr) {
      String[] paragraphs = {
         "See who is using BlossomBuddy on Cherry, Spirit, Lotus and Tulip, and say hi across realms.",
         "You have switched the list off, so you don't appear and can't see anyone. Turn \"Appear in the list\" back on below to see who is using BlossomBuddy. You will appear in it too: your Minecraft name and the realm you are on are shown only to other players who are in the list.",
         "Nothing is saved, and you disappear a minute after you leave or the moment you turn it off."};
      int y = 44;
      for (String paragraph : paragraphs) {
         for (FormattedCharSequence line : tr.split(Component.literal(paragraph), Math.min(this.width - 40, 320))) {
            ctx.centeredText(tr, line, this.width / 2, y, 0xFFC9BFD8);
            y += 11;
         }

         y += 6;
      }
   }

   /** The realm key at the start of a column label such as "Cherry (3)". */
   private static String realmOf(String label) {
      int space = label.indexOf(' ');
      return (space < 0 ? label : label.substring(0, space)).toLowerCase(Locale.ROOT);
   }

   // ------------------------------------------------------------------ clicking

   @Override
   public boolean mouseClicked(MouseButtonEvent inputEvent, boolean isDoubleClick) {
      double mouseX = inputEvent.x();
      double mouseY = inputEvent.y();
      int button = inputEvent.button();
      if (button == 0 && PresenceClient.INSTANCE.enabled()) {
         for (Cell c : this.cells()) {
            if (c.player() != null && c.hit(mouseX, mouseY)) {
               if (this.minecraft != null) {
                  this.minecraft.setScreen(new ChatScreen("/xc " + c.player().name() + ", ", false));
               }

               return true;
            }
         }
      }

      return super.mouseClicked(inputEvent, isDoubleClick);
   }
}
