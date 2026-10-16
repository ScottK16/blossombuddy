package org.blossomsuite.core.ui;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.blossomsuite.core.board.BoardFormat;
import org.blossomsuite.core.board.LeaderboardClient;
import org.blossomsuite.core.board.ProfileModels;
import org.blossomsuite.core.session.SessionStats;
import org.blossomsuite.core.xchat.GradientText;
import org.blossomsuite.core.xchat.NameStyle;

/**
 * Your BlossomBuddy profile: level and XP, and a picker for the gradients and symbols you have unlocked. Click one you have unlocked to
 * wear it (it saves at once); a locked one shows the level that opens it. Others see your look in cross-realm chat, the player list
 * and over your head.
 */
public final class ProfileScreen extends Screen {
   private static final int PANEL_MAX_W = 380;
   private static final int SWATCH_H = 15;
   private static final int CELL = 18;
   private static final int GAP = 3;
   public static final String SITE = "https://blossombuddy.site/profile.html?name=";

   private enum Kind { GRADIENT, SYMBOL }

   /** Something to click: a gradient or a symbol, where it is, and whether it can be worn. */
   private record Item(Kind kind, String id, String label, int x, int y, int w, int h, boolean unlocked, int level, List<String> colors, String glyph) {
      boolean hit(double mx, double my) {
         return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
      }
   }

   private volatile ProfileModels.Profile profile;
   private volatile String message = "Loading your profile...";
   private String gradientId;
   private String symbolId;
   private List<String> customColors = List.of("#ff8800", "#ff0088");
   private EditBox colorOne;
   private EditBox colorTwo;

   /** A profile to show as it is (for tests and screenshots); null loads your own from the relay. */
   private final ProfileModels.Profile preset;

   public ProfileScreen() {
      this(null);
   }

   public ProfileScreen(ProfileModels.Profile preset) {
      super(Component.literal("Your BlossomBuddy profile"));
      this.preset = preset;
   }

   @Override
   protected void init() {
      int w = this.panelW();
      int x = (this.width - w) / 2;
      int footY = this.height - 24;
      this.addRenderableWidget(StyledButton.of(Component.literal("Website profile"), b -> {
         String name = this.profile == null ? this.minecraftName() : this.profile.name;
         ConfirmLinkScreen.confirmLink(this, URI.create(SITE + name.replaceAll("[^A-Za-z0-9_]", ""))).onPress(b);
      }).dimensions(x, footY, 120, 18).build());
      this.addRenderableWidget(StyledButton.of(Component.literal("Done"), b -> this.onClose()).dimensions(x + w - 120, footY, 120, 18).build());
      if (this.preset != null && this.profile == null) {
         this.adopt(this.preset);
      } else if (this.profile == null) {
         this.loadProfile();
      }

      this.addColorBoxes();
   }

   /** Takes a loaded profile as the current one, including what is being worn. */
   private void adopt(ProfileModels.Profile p) {
      this.profile = p;
      this.message = "";
      this.gradientId = p.style == null ? null : p.style.gradientId;
      this.symbolId = p.style == null ? null : p.style.symbolId;
      if (p.style != null && p.style.colors != null && "custom".equals(p.style.gradientId) && p.style.colors.size() >= 2) {
         this.customColors = List.copyOf(p.style.colors);
      }
   }

   private String minecraftName() {
      return this.minecraft == null || this.minecraft.getUser() == null ? "" : this.minecraft.getUser().getName();
   }

   private void loadProfile() {
      LeaderboardClient.INSTANCE.loadProfile().whenComplete((p, failure) -> {
         if (this.minecraft == null) {
            return;
         }

         this.minecraft.execute(() -> {
            if (failure != null) {
               this.message = rootMessage(failure);
               return;
            }

            this.adopt(p);
            this.rebuildWidgets();
         });
      });
   }

   private static String rootMessage(Throwable t) {
      Throwable c = t;
      while (c.getCause() != null && c.getMessage() != null && c.getMessage().startsWith("java.")) {
         c = c.getCause();
      }

      String m = c.getMessage();
      return m == null || m.isBlank() ? "Couldn't load your profile." : (m.startsWith("java.lang.IllegalStateException: ") ? m.substring("java.lang.IllegalStateException: ".length()) : m);
   }

   private void addColorBoxes() {
      this.colorOne = null;
      this.colorTwo = null;
      ProfileModels.Profile p = this.profile;
      if (p == null || !this.customUnlocked(p)) {
         return;
      }

      int w = this.panelW();
      int x = (this.width - w) / 2;
      int y = this.customRowY();
      this.colorOne = new EditBox(this.font, x + 120, y, 70, 16, Component.literal("First colour"));
      this.colorOne.setMaxLength(7);
      this.colorOne.setValue(this.customColors.get(0));
      this.colorTwo = new EditBox(this.font, x + 196, y, 70, 16, Component.literal("Second colour"));
      this.colorTwo.setMaxLength(7);
      this.colorTwo.setValue(this.customColors.get(1));
      this.addRenderableWidget(this.colorOne);
      this.addRenderableWidget(this.colorTwo);
   }

   private boolean customUnlocked(ProfileModels.Profile p) {
      for (ProfileModels.GradientOption g : p.gradients()) {
         if (g.custom) {
            return g.unlocked;
         }
      }

      return false;
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   private int panelW() {
      return Math.min(PANEL_MAX_W, this.width - 24);
   }

   private int customRowY() {
      return this.symbolTop() + 2 * (CELL + GAP) + 18;
   }

   private int gradientTop() {
      return 100;
   }

   private int symbolTop() {
      return this.gradientTop() + 2 * (SWATCH_H + GAP) + 16;
   }

   // ------------------------------------------------------------------ what is on screen

   private String[] chosenColors() {
      ProfileModels.Profile p = this.profile;
      if (p == null || this.gradientId == null) {
         return null;
      }

      if ("custom".equals(this.gradientId)) {
         String[] c = this.customColorsNow().toArray(new String[0]);
         return GradientText.isValid(c) ? c : null;
      }

      for (ProfileModels.GradientOption g : p.gradients()) {
         if (g.id.equals(this.gradientId)) {
            return NameStyle.colorsOf(g.colors);
         }
      }

      return null;
   }

   private List<String> customColorsNow() {
      if (this.colorOne != null && this.colorTwo != null) {
         return List.of(this.colorOne.getValue().trim(), this.colorTwo.getValue().trim());
      }

      return this.customColors;
   }

   private String chosenGlyph() {
      ProfileModels.Profile p = this.profile;
      if (p == null || this.symbolId == null) {
         return "";
      }

      for (ProfileModels.SymbolOption s : p.symbols()) {
         if (s.id.equals(this.symbolId)) {
            return s.glyph;
         }
      }

      return "";
   }

   private List<Item> items() {
      List<Item> out = new ArrayList<>();
      ProfileModels.Profile p = this.profile;
      if (p == null) {
         return out;
      }

      int w = this.panelW();
      int x0 = (this.width - w) / 2;
      int perRow = 4;
      int sw = (w - GAP * (perRow - 1)) / perRow;
      int i = 0;
      out.add(new Item(Kind.GRADIENT, "none", "Plain", x0, this.gradientTop(), sw, SWATCH_H, true, 0, List.of(), ""));
      i++;
      for (ProfileModels.GradientOption g : p.gradients()) {
         int col = i % perRow;
         int row = i / perRow;
         out.add(new Item(Kind.GRADIENT, g.id, g.label, x0 + col * (sw + GAP), this.gradientTop() + row * (SWATCH_H + GAP), sw, SWATCH_H, g.unlocked, g.level, g.colors == null ? List.of() : g.colors, ""));
         i++;
      }

      int perSymbolRow = Math.max(1, (w + GAP) / (CELL + GAP));
      int n = 0;
      out.add(new Item(Kind.SYMBOL, "none", "None", x0, this.symbolTop(), CELL, CELL, true, 0, List.of(), ""));
      n++;
      for (ProfileModels.SymbolOption s : p.symbols()) {
         int col = n % perSymbolRow;
         int row = n / perSymbolRow;
         out.add(new Item(Kind.SYMBOL, s.id, s.label, x0 + col * (CELL + GAP), this.symbolTop() + row * (CELL + GAP), CELL, CELL, s.unlocked, s.level, List.of(), s.glyph));
         n++;
      }

      return out;
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
      super.extractRenderState(ctx, mouseX, mouseY, delta);
      Font tr = this.font;
      int w = this.panelW();
      int x = (this.width - w) / 2;
      ctx.centeredText(tr, Component.literal("Your BlossomBuddy profile").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), this.width / 2, 8, -1);

      ProfileModels.Profile p = this.profile;
      if (p == null) {
         ctx.centeredText(tr, Component.literal(this.message).withStyle(ChatFormatting.GRAY), this.width / 2, 50, -1);
         return;
      }

      ctx.fill(x, 20, x + w, this.height - 30, 0x66000000);

      // the name as others see it, then the level and XP bar
      String name = p.name == null ? "" : p.name;
      Component preview = NameStyle.styled(name, Component.literal(name).withStyle(ChatFormatting.WHITE), this.chosenColors(), this.chosenGlyph());
      ctx.centeredText(tr, preview, this.width / 2, 26, -1);
      ctx.centeredText(tr, Component.literal("Level " + p.level + "  -  " + (p.title == null ? "" : p.title)).withStyle(ChatFormatting.GOLD), this.width / 2, 40, -1);
      int barX = x + 8;
      int barW = w - 16;
      ctx.fill(barX, 53, barX + barW, 62, 0xFF1A1A1A);
      int fill = (int)Math.round(barW * p.progress());
      ctx.fill(barX, 53, barX + fill, 62, Theme.ACCENT);
      String xpText = BoardFormat.xp(p.xpIntoLevel) + " / " + BoardFormat.xp(p.xpForNext) + " XP to level " + (p.level + 1);
      ctx.centeredText(tr, Component.literal(xpText).withStyle(ChatFormatting.WHITE), this.width / 2, 54, -1);
      ctx.centeredText(tr, Component.literal(this.statsLine(p)).withStyle(ChatFormatting.GRAY), this.width / 2, 68, -1);
      ctx.centeredText(tr, Component.literal(this.nextUnlock(p)).withStyle(ChatFormatting.DARK_AQUA), this.width / 2, 80, -1);

      ctx.text(tr, Component.literal("Name gradient").withStyle(ChatFormatting.GRAY), x + 4, this.gradientTop() - 11, -1);
      ctx.text(tr, Component.literal("Symbol beside your name").withStyle(ChatFormatting.GRAY), x + 4, this.symbolTop() - 11, -1);

      Item hovered = null;
      for (Item item : this.items()) {
         boolean selected = item.kind() == Kind.GRADIENT ? java.util.Objects.equals(this.gradientId == null ? "none" : this.gradientId, item.id()) : java.util.Objects.equals(this.symbolId == null ? "none" : this.symbolId, item.id());
         if (item.hit(mouseX, mouseY)) {
            hovered = item;
         }

         if (item.kind() == Kind.GRADIENT) {
            this.drawSwatch(ctx, tr, item, selected, item == hovered);
         } else {
            this.drawSymbol(ctx, tr, item, selected, item == hovered);
         }
      }

      if (this.customUnlocked(p)) {
         ctx.text(tr, Component.literal("Your own colours").withStyle(ChatFormatting.GRAY), x + 4, this.customRowY() + 4, -1);
         String hint = "click the swatch above to wear them";
         ctx.text(tr, Component.literal(hint).withStyle(ChatFormatting.DARK_GRAY), x + 272, this.customRowY() + 4, -1);
      }

      if (!this.message.isEmpty()) {
         ctx.centeredText(tr, Component.literal(this.message).withStyle(ChatFormatting.YELLOW), this.width / 2, this.height - 40, -1);
      }

      if (hovered != null) {
         String tip = hovered.unlocked() ? hovered.label() : hovered.label() + " - unlocks at level " + hovered.level();
         int tw = tr.width(tip) + 8;
         int tx = Math.max(2, Math.min(this.width - tw - 2, mouseX + 10));
         int ty = Math.min(this.height - 14, mouseY + 12);
         ctx.fill(tx, ty, tx + tw, ty + 13, 0xEE101010);
         ctx.text(tr, Component.literal(tip).withStyle(hovered.unlocked() ? ChatFormatting.WHITE : ChatFormatting.GRAY), tx + 4, ty + 3, -1);
      }
   }

   private void drawSwatch(GuiGraphicsExtractor ctx, Font tr, Item item, boolean selected, boolean hovered) {
      int edge = selected ? Theme.ACCENT : hovered ? Theme.TEXT_DIM : Theme.CONTROL_EDGE;
      ctx.fill(item.x() - 1, item.y() - 1, item.x() + item.w() + 1, item.y() + item.h() + 1, edge);
      ctx.fill(item.x(), item.y(), item.x() + item.w(), item.y() + item.h(), 0xFF15101C);
      String[] colors = item.id().equals("custom") ? this.customColorsNow().toArray(new String[0]) : NameStyle.colorsOf(item.colors());
      if (colors != null && GradientText.isValid(colors)) {
         int steps = item.w() - 2;
         for (int i = 0; i < steps; i++) {
            int rgb = GradientText.colorAt(colors, i, steps) | 0xFF000000;
            ctx.fill(item.x() + 1 + i, item.y() + item.h() - 4, item.x() + 2 + i, item.y() + item.h() - 1, item.unlocked() ? rgb : dim(rgb));
         }
      }

      String label = item.unlocked() ? item.label() : item.label() + "  Lv " + item.level();
      ctx.text(tr, Component.literal(tr.plainSubstrByWidth(label, item.w() - 4)).withStyle(item.unlocked() ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY), item.x() + 3, item.y() + 2, -1);
   }

   private void drawSymbol(GuiGraphicsExtractor ctx, Font tr, Item item, boolean selected, boolean hovered) {
      int edge = selected ? Theme.ACCENT : hovered ? Theme.TEXT_DIM : Theme.CONTROL_EDGE;
      ctx.fill(item.x() - 1, item.y() - 1, item.x() + item.w() + 1, item.y() + item.h() + 1, edge);
      ctx.fill(item.x(), item.y(), item.x() + item.w(), item.y() + item.h(), 0xFF15101C);
      String text = item.id().equals("none") ? "x" : item.glyph();
      ChatFormatting color = !item.unlocked() ? ChatFormatting.DARK_GRAY : item.id().equals("none") ? ChatFormatting.GRAY : ChatFormatting.WHITE;
      ctx.centeredText(tr, Component.literal(text).withStyle(color), item.x() + item.w() / 2, item.y() + (item.h() - 8) / 2, -1);
      if (!item.unlocked()) {
         String lv = String.valueOf(item.level());
         ctx.text(tr, Component.literal(lv).withStyle(ChatFormatting.DARK_GRAY), item.x() + item.w() - tr.width(lv) - 1, item.y() + item.h() - 8, -1);
      }
   }

   private static int dim(int argb) {
      int r = (argb >> 16 & 0xFF) / 3;
      int g = (argb >> 8 & 0xFF) / 3;
      int b = (argb & 0xFF) / 3;
      return 0xFF000000 | r << 16 | g << 8 | b;
   }

   private String statsLine(ProfileModels.Profile p) {
      List<String> parts = new ArrayList<>();
      parts.add(SessionStats.duration(p.minutes * 60_000L) + " on realms");
      if (p.dungeons != null && (p.dungeons.done > 0 || p.dungeons.failed > 0)) {
         parts.add(p.dungeons.done + " dungeons cleared");
      }

      if (p.ranks != null && p.ranks.get("money") != null && p.ranks.get("money").rank > 0) {
         parts.add("#" + p.ranks.get("money").rank + " earner");
      }

      return String.join("  -  ", parts);
   }

   private String nextUnlock(ProfileModels.Profile p) {
      int best = Integer.MAX_VALUE;
      String what = "";
      for (ProfileModels.GradientOption g : p.gradients()) {
         if (!g.unlocked && g.level < best) {
            best = g.level;
            what = g.label + " gradient";
         }
      }

      for (ProfileModels.SymbolOption s : p.symbols()) {
         if (!s.unlocked && s.level < best) {
            best = s.level;
            what = s.label.toLowerCase(Locale.ROOT) + " symbol";
         }
      }

      return best == Integer.MAX_VALUE ? "Everything is unlocked." : "Next unlock: " + what + " at level " + best + " (" + (best - p.level) + " to go)";
   }

   // ------------------------------------------------------------------ equipping

   @Override
   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      if (event.button() == 0 && this.profile != null) {
         for (Item item : this.items()) {
            if (item.hit(event.x(), event.y())) {
               if (!item.unlocked()) {
                  this.message = item.label() + " unlocks at level " + item.level() + ".";
               } else {
                  this.equip(item);
               }

               return true;
            }
         }
      }

      return super.mouseClicked(event, doubleClick);
   }

   private void equip(Item item) {
      String beforeGradient = this.gradientId;
      String beforeSymbol = this.symbolId;
      List<String> colors = null;
      if (item.kind() == Kind.GRADIENT) {
         this.gradientId = item.id().equals("none") ? null : item.id();
         if ("custom".equals(this.gradientId)) {
            colors = this.customColorsNow();
            if (!GradientText.isValid(colors.toArray(new String[0]))) {
               this.gradientId = beforeGradient;
               this.message = "Type two colours like #ff8800 first.";
               return;
            }

            this.customColors = List.copyOf(colors);
         }
      } else {
         this.symbolId = item.id().equals("none") ? null : item.id();
      }

      if ("custom".equals(this.gradientId) && colors == null) {
         colors = this.customColorsNow();
      }

      this.message = "Saving...";
      LeaderboardClient.INSTANCE.applyStyle(this.gradientId, colors, this.symbolId).whenComplete((v, failure) -> {
         if (this.minecraft == null) {
            return;
         }

         this.minecraft.execute(() -> {
            if (failure != null) {
               this.gradientId = beforeGradient;
               this.symbolId = beforeSymbol;
               this.message = rootMessage(failure);
            } else {
               this.message = "Saved. Other BlossomBuddy players see it within a minute.";
            }
         });
      });
   }
}
