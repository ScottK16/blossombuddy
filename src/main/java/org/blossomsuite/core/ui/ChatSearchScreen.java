package org.blossomsuite.core.ui;

import java.util.List;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.blossomsuite.core.chat.ChatSearchHighlight;
import org.blossomsuite.core.chat.ChatTimestampFormat;
import org.blossomsuite.core.chat.MainChatLog;
import org.lwjgl.glfw.GLFW;

/**
 * Find a player's name or any word across everything that has shown up in main chat since you joined, and copy it out. Type in the box
 * to filter as you go; click a line to copy just that one, or use the buttons to copy everything currently shown or the last 200 lines.
 */
public final class ChatSearchScreen extends Screen {
   private static final int ROW_H = 11;
   private static final int TOP = 40;
   private static final int BOTTOM_MARGIN = 32;
   private static final int LIST_WIDTH = 520;

   private final Screen parent;
   private final String initialQuery;
   private EditBox field;
   private List<MainChatLog.Line> results = List.of();
   private int scroll = 0;
   private long copiedAt = -1L;
   private String copiedWhat = "";

   public ChatSearchScreen(Screen parent) {
      this(parent, "");
   }

   public ChatSearchScreen(Screen parent, String initialQuery) {
      super(Component.literal("Search Chat"));
      this.parent = parent;
      this.initialQuery = initialQuery == null ? "" : initialQuery;
   }

   @Override
   protected void init() {
      this.field = new EditBox(this.font, this.width / 2 - 150, 16, 300, 20, Component.literal("Search"));
      this.field.setMaxLength(200);
      this.field.setHint(Component.literal("Search main chat: a name or any word..."));
      this.field.setValue(this.initialQuery);
      this.field.setResponder(s -> {
         this.results = MainChatLog.INSTANCE.search(s);
         this.scroll = 0;
      });
      this.addRenderableWidget(this.field);
      this.setInitialFocus(this.field);
      this.results = MainChatLog.INSTANCE.search(this.initialQuery);

      int by = this.height - 24;
      this.addRenderableWidget(StyledButton.of(Component.literal("Copy shown"), b -> this.copyShown()).dimensions(this.width / 2 - 214, by, 140, 20).build());
      this.addRenderableWidget(StyledButton.of(Component.literal("Copy last 200"), b -> this.copyRecent()).dimensions(this.width / 2 - 70, by, 140, 20).build());
      this.addRenderableWidget(StyledButton.of(Component.literal("Done"), b -> this.onClose()).dimensions(this.width / 2 + 74, by, 140, 20).build());
   }

   @Override
   public void onClose() {
      if (this.minecraft != null) {
         this.minecraft.setScreen(this.parent);
      }
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   private int maxRows() {
      return Math.max(1, (this.height - BOTTOM_MARGIN - TOP) / ROW_H);
   }

   private int listX() {
      return Math.max(8, this.width / 2 - LIST_WIDTH / 2);
   }

   private int listWidth() {
      return Math.min(this.width - 16, LIST_WIDTH);
   }

   private void copyToClipboard(String text, String what) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.keyboardHandler != null) {
         mc.keyboardHandler.setClipboard(text.isEmpty() ? " " : text);
      }

      this.copiedAt = System.currentTimeMillis();
      this.copiedWhat = what;
   }

   private static String join(List<MainChatLog.Line> lines) {
      return lines.stream().map(MainChatLog.Line::plain).collect(Collectors.joining("\n"));
   }

   private void copyShown() {
      this.copyToClipboard(join(this.results), this.results.size() + (this.results.size() == 1 ? " line" : " lines"));
   }

   private void copyRecent() {
      List<MainChatLog.Line> recent = MainChatLog.INSTANCE.search("", 200);
      this.copyToClipboard(join(recent), "the last " + recent.size());
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
      super.extractRenderState(ctx, mouseX, mouseY, delta);
      Font tr = this.font;
      ctx.centeredText(tr, Component.literal("Search Chat").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), this.width / 2, 4, -1);

      String query = this.field.getValue();
      String count = this.results.isEmpty() ? "No matches"
         : this.results.size() + (this.results.size() == 1 ? " match" : " matches") + (this.results.size() >= MainChatLog.MAX_RESULTS ? " (showing the newest " + MainChatLog.MAX_RESULTS + ")" : "");
      ctx.centeredText(tr, Component.literal(count).withStyle(ChatFormatting.GRAY), this.width / 2, TOP - 12, -1);

      int rows = this.maxRows();
      this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, this.results.size() - rows)));
      int x = this.listX();
      int w = this.listWidth();
      MainChatLog.Line hoveredLine = null;
      for (int i = 0; i < rows && this.scroll + i < this.results.size(); i++) {
         MainChatLog.Line line = this.results.get(this.scroll + i);
         int y = TOP + i * ROW_H;
         boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + ROW_H;
         if (hovered) {
            ctx.fill(x - 2, y - 1, x + w, y + ROW_H - 1, 0x33FFFFFF);
            hoveredLine = line;
         }

         ctx.text(tr, this.highlighted(line.plain(), query), x, y, hovered ? -1 : 0xFFC9BFD8, false);
      }

      if (hoveredLine != null) {
         ctx.setTooltipForNextFrame(tr, Component.literal(ChatTimestampFormat.format(hoveredLine.atMs(), System.currentTimeMillis())).withStyle(ChatFormatting.GRAY), mouseX, mouseY);
      }

      if (this.results.isEmpty() && !query.isBlank()) {
         ctx.centeredText(tr, Component.literal("Nothing found. Only chat you've already seen since joining can be searched.").withStyle(ChatFormatting.DARK_GRAY), this.width / 2, TOP + 8, -1);
      }

      if (this.copiedAt > 0 && System.currentTimeMillis() - this.copiedAt < 1500L) {
         ctx.centeredText(tr, Component.literal("Copied " + this.copiedWhat + " to the clipboard.").withStyle(ChatFormatting.GREEN), this.width / 2, this.height - 44, -1);
      }
   }

   private Component highlighted(String plain, String query) {
      ChatSearchHighlight.Parts p = ChatSearchHighlight.split(plain, query);
      if (p.match().isEmpty()) {
         return Component.literal(p.before());
      }

      MutableComponent out = Component.literal(p.before());
      out.append(Component.literal(p.match()).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
      out.append(Component.literal(p.after()));
      return out;
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent inputEvent, boolean isDoubleClick) {
      double mouseX = inputEvent.x();
      double mouseY = inputEvent.y();
      int button = inputEvent.button();
      if (button == 0) {
         int rows = this.maxRows();
         int x = this.listX();
         int w = this.listWidth();
         for (int i = 0; i < rows && this.scroll + i < this.results.size(); i++) {
            int y = TOP + i * ROW_H;
            if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + ROW_H) {
               this.copyToClipboard(this.results.get(this.scroll + i).plain(), "1 line");
               return true;
            }
         }
      }

      return super.mouseClicked(inputEvent, isDoubleClick);
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      if (verticalAmount != 0.0D) {
         this.scroll = Math.max(0, this.scroll - (int)Math.signum(verticalAmount) * 3);
         return true;
      }

      return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
   }

   @Override
   public boolean keyPressed(KeyEvent inputEvent) {
      int keyCode = inputEvent.key();
      int scanCode = inputEvent.scancode();
      int modifiers = inputEvent.modifiers();
      if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
         this.onClose();
         return true;
      }

      return super.keyPressed(inputEvent);
   }
}
