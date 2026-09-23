package org.blossomsuite.core.hud;

import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.blossomsuite.core.chat.SecondaryChat;
import org.blossomsuite.core.config.FeatureConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.util.HudStyleUtil;

/**
 * An extra secondary-chat window that shows one filter only (for example a window just for Marry chat, or Staff chat). There
 * are {@link FeatureConfig.Chat#MAX_WINDOWS} of them; each has its own position, size, line count and scrolling, and they are
 * moved in the HUD editor like every other panel.
 */
public final class ChatWindowHud extends PanelHud {
   public static final ChatWindowHud[] WINDOWS = {new ChatWindowHud(0), new ChatWindowHud(1), new ChatWindowHud(2), new ChatWindowHud(3)};
   private static final FeatureConfig.Panel FALLBACK = new FeatureConfig.Panel();
   private static final int WIDTH = 200;
   private static final int HEADER_H = 14;
   private static final int LINE_H = 9;

   private final int index;
   private final ChatWindowState state = new ChatWindowState();

   private ChatWindowHud(int index) {
      this.index = index;
   }

   private FeatureConfig.ChatWindow cfg() {
      List<FeatureConfig.ChatWindow> windows = FeatureConfig.INSTANCE.chat.windows;
      return windows != null && this.index < windows.size() ? windows.get(this.index) : null;
   }

   @Override
   public String id() {
      return "chatwindow" + (this.index + 1);
   }

   @Override
   protected FeatureConfig.Panel panel() {
      FeatureConfig.ChatWindow c = this.cfg();
      return c == null || c.panel == null ? FALLBACK : c.panel;
   }

   @Override
   protected boolean shown() {
      FeatureConfig.ChatWindow c = this.cfg();
      return c != null && c.enabled && c.filter != null && !c.filter.isBlank();
   }

   @Override
   protected int[] defaultTopLeft(int screenW, int screenH, int w, int h) {
      return new int[]{Math.max(6, screenW - w - 6), 40 + this.index * 80};
   }

   public void render(DrawContext ctx, MinecraftClient client) {
      FeatureConfig.ChatWindow cfg = this.cfg();
      if (!this.shown() || client.player == null || !SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
         return;
      }

      TextRenderer tr = client.textRenderer;
      String filter = cfg.filter;
      int maxLines = Math.max(2, Math.min(20, cfg.lines));

      // like the main chat, you can only be scrolled back while the chat screen is open
      if (!(client.currentScreen instanceof ChatScreen)) {
         this.state.reset();
      }

      this.state.update(SecondaryChat.INSTANCE.count(filter));
      int skip = this.state.scroll();
      List<OrderedText> wrapped = SecondaryChatHud.wrap(tr, filter, skip, maxLines, WIDTH - 10);
      while (skip > 0 && wrapped.size() < maxLines) {
         skip--;
         wrapped = SecondaryChatHud.wrap(tr, filter, skip, maxLines, WIDTH - 10);
      }

      if (skip != this.state.scroll()) {
         this.state.set(skip);
      }

      boolean sample = wrapped.isEmpty() && HudEditState.editMode;
      int lineCount = Math.max(wrapped.size(), sample ? 3 : 1);
      int baseH = HEADER_H + lineCount * LINE_H + 8;
      final int newer = skip;
      final List<OrderedText> lines = wrapped;
      this.draw(ctx, client, WIDTH, baseH, true, c -> {
         float opacity = this.panel().opacity;
         c.fill(0, 0, WIDTH, HEADER_H, HudStyleUtil.panelHeader(opacity));
         c.fill(0, HEADER_H, WIDTH, HEADER_H + 1, HudStyleUtil.panelDivider(opacity));
         c.drawTextWithShadow(tr, filter, 6, 4, 0xFFF48FB1);
         if (newer > 0) {
            String hint = newer + " newer";
            c.drawTextWithShadow(tr, hint, WIDTH - 6 - tr.getWidth(hint), 4, 0xFFF48FB1);
         }

         int y = HEADER_H + 5;
         if (lines.isEmpty()) {
            c.drawTextWithShadow(tr, Text.literal(sample ? "Only " + filter + " lines show up here." : "Nothing yet."), 6, y, 0xFF8A8098);
         }

         for (OrderedText line : lines) {
            c.drawTextWithShadow(tr, line, 6, y, -1);
            y += LINE_H;
         }
      });
   }

   /** The mouse wheel over this window, with the chat screen open. */
   private boolean onScroll(double mouseX, double mouseY, double amount, boolean shift) {
      FeatureConfig.ChatWindow cfg = this.cfg();
      if (!this.shown() || amount == 0.0D || !SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
         return false;
      }

      if (mouseX < this.lastX || mouseX >= this.lastX + this.lastW || mouseY < this.lastY || mouseY >= this.lastY + this.lastH) {
         return false;
      }

      int step = shift ? Math.max(3, Math.min(20, cfg.lines) - 1) : 2;
      this.state.scrollBy(amount > 0.0D ? step : -step, SecondaryChat.INSTANCE.count(cfg.filter));
      return true;
   }

   /** Offers the wheel to each extra window in turn; true when one used it. */
   public static boolean scrollAny(double mouseX, double mouseY, double amount, boolean shift) {
      for (ChatWindowHud w : WINDOWS) {
         if (w.onScroll(mouseX, mouseY, amount, shift)) {
            return true;
         }
      }

      return false;
   }
}
