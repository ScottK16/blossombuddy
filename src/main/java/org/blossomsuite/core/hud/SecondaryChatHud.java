package org.blossomsuite.core.hud;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.blossomsuite.core.chat.SecondaryChat;
import org.blossomsuite.core.config.FeatureConfig;
import org.blossomsuite.core.util.HudStyleUtil;
import org.blossomsuite.core.config.SuiteConfig;

/** Draws the secondary chat window: a tab row of filters, then the newest lines from the selected one. */
public final class SecondaryChatHud extends PanelHud {
   public static final SecondaryChatHud INSTANCE = new SecondaryChatHud();
   private static final int WIDTH = 230;
   private static final int HEADER_H = 14;
   private static final int LINE_H = 9;

   private SecondaryChatHud() {
   }

   @Override
   public String id() {
      return "secondarychat";
   }

   @Override
   protected FeatureConfig.Panel panel() {
      return FeatureConfig.INSTANCE.chat.panel;
   }

   @Override
   protected boolean shown() {
      return FeatureConfig.INSTANCE.chat.show;
   }

   @Override
   protected int[] defaultTopLeft(int screenW, int screenH, int w, int h) {
      return new int[]{6, Math.max(6, screenH - h - 60)};
   }

   public void render(DrawContext ctx, MinecraftClient client) {
      FeatureConfig.Chat cfg = FeatureConfig.INSTANCE.chat;
      if (!cfg.show || client.player == null || !SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
         return;
      }

      TextRenderer tr = client.textRenderer;
      int maxLines = Math.max(2, Math.min(20, cfg.lines));
      String selected = SecondaryChat.INSTANCE.selectedName();

      // like the main chat, you can only be scrolled back while the chat screen is open
      if (SecondaryChat.INSTANCE.scroll() > 0 && !(client.currentScreen instanceof ChatScreen)) {
         SecondaryChat.INSTANCE.resetScroll();
      }

      int skip = SecondaryChat.INSTANCE.scroll();
      List<OrderedText> wrapped = wrap(tr, selected, skip, maxLines);
      // at the oldest line the window should still be full, so step back towards the newest until it is
      while (skip > 0 && wrapped.size() < maxLines) {
         skip--;
         wrapped = wrap(tr, selected, skip, maxLines);
      }

      if (skip != SecondaryChat.INSTANCE.scroll()) {
         SecondaryChat.INSTANCE.setScroll(skip);
      }

      final int newer = skip;

      boolean sample = wrapped.isEmpty() && HudEditState.editMode;
      int lineCount = Math.max(wrapped.size(), sample ? 3 : 1);
      int baseH = HEADER_H + lineCount * LINE_H + 8;
      final List<OrderedText> lines = wrapped;
      this.draw(ctx, client, WIDTH, baseH, true, c -> {
         float opacity = cfg.panel.opacity;
         c.fill(0, 0, WIDTH, HEADER_H, HudStyleUtil.panelHeader(opacity));
         c.fill(0, HEADER_H, WIDTH, HEADER_H + 1, HudStyleUtil.panelDivider(opacity));
         int x = 6;
         x = tab(c, tr, x, "All", selected == null);
         for (FeatureConfig.Filter f : cfg.filters) {
            if (f != null && f.enabled) {
               x = tab(c, tr, x, f.name, f.name.equalsIgnoreCase(selected));
            }
         }

         if (newer > 0) {
            String hint = newer + " newer";
            c.drawTextWithShadow(tr, hint, WIDTH - 6 - tr.getWidth(hint), 4, 0xFFF48FB1);
         }

         int y = HEADER_H + 5;
         if (lines.isEmpty()) {
            c.drawTextWithShadow(tr, Text.literal(sample ? "Filtered chat shows up here." : "Nothing yet."), 6, y, 0xFF8A8098);
         }

         for (OrderedText line : lines) {
            c.drawTextWithShadow(tr, line, 6, y, -1);
            y += LINE_H;
         }
      });
   }

   /** Wraps the lines of {@code filter}, skipping the newest {@code skip}, so the window ends on the last one shown. */
   private static List<OrderedText> wrap(TextRenderer tr, String filter, int skip, int maxLines) {
      return wrap(tr, filter, skip, maxLines, WIDTH - 10);
   }

   /** The same, wrapped to {@code wrapWidth} pixels (the extra chat windows are narrower). */
   static List<OrderedText> wrap(TextRenderer tr, String filter, int skip, int maxLines, int wrapWidth) {
      // Wrapping every line of every chat window every frame is real work (a gradient name is one text piece per letter), and the
      // answer only changes when a line arrives, so keep it until then (or half a second, so a settings change still shows up soon).
      long version = SecondaryChat.INSTANCE.version();
      long now = System.currentTimeMillis();
      String key = filter + "\u0000" + skip + "|" + maxLines + "|" + wrapWidth;
      WrapCache hit = WRAPS.get(key);
      if (hit != null && hit.version == version && now - hit.at < 500L) {
         return hit.lines;
      }

      List<OrderedText> built = wrapNow(tr, filter, skip, maxLines, wrapWidth);
      if (WRAPS.size() >= 32) {
         WRAPS.clear();
      }

      WRAPS.put(key, new WrapCache(version, now, built));
      return built;
   }

   private record WrapCache(long version, long at, List<OrderedText> lines) {
   }

   private static final java.util.Map<String, WrapCache> WRAPS = new java.util.HashMap<>();

   private static List<OrderedText> wrapNow(TextRenderer tr, String filter, int skip, int maxLines, int wrapWidth) {
      List<OrderedText> wrapped = new ArrayList<>();
      List<SecondaryChat.Line> shown = SecondaryChat.INSTANCE.window(filter, skip, maxLines);
      for (int i = shown.size() - 1; i >= 0 && wrapped.size() < maxLines; i--) {
         List<OrderedText> parts = tr.wrapLines(shown.get(i).text(), wrapWidth);
         for (int p = parts.size() - 1; p >= 0 && wrapped.size() < maxLines; p--) {
            wrapped.add(0, parts.get(p));
         }
      }

      return wrapped;
   }

   /**
    * The mouse wheel over the window, with the chat screen open. Up goes back through older lines, down comes forward;
    * Shift moves a page at a time.
    *
    * @return true if the wheel was used here, so the game's own chat should not also scroll
    */
   public boolean onScroll(double mouseX, double mouseY, double amount, boolean shift) {
      FeatureConfig.Chat cfg = FeatureConfig.INSTANCE.chat;
      if (!cfg.show || amount == 0.0D || !SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
         return false;
      }

      if (mouseX < this.lastX || mouseX >= this.lastX + this.lastW || mouseY < this.lastY || mouseY >= this.lastY + this.lastH) {
         return false;
      }

      int step = shift ? Math.max(3, Math.min(20, cfg.lines) - 1) : 2;
      SecondaryChat.INSTANCE.scrollBy(amount > 0.0D ? step : -step);
      return true;
   }

   private static int tab(DrawContext c, TextRenderer tr, int x, String name, boolean on) {
      c.drawTextWithShadow(tr, name, x, 4, on ? 0xFFF48FB1 : 0xFFA79BB8);
      return x + tr.getWidth(name) + 9;
   }
}
