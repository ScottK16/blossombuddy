package org.blossomsuite.core.ui;

import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.blossomsuite.core.chat.ChatTimestampFormat;
import org.blossomsuite.core.chat.MainChatLog;
import org.lwjgl.glfw.GLFW;

/**
 * Find a player's name or any word across everything that has shown up in main chat since you joined, and copy it out. Type in the box
 * to filter as you go. Each result is a real selectable text field - click to place the cursor, shift-click or drag to select part of
 * it, Ctrl+C to copy just that; right-click a line to copy the whole thing at once. Use the buttons to copy everything shown or the
 * last 200 lines.
 */
public final class ChatSearchScreen extends Screen {
   private static final int ROW_H = 12;
   private static final int TOP = 40;
   private static final int BOTTOM_MARGIN = 44;
   private static final int LIST_WIDTH = 520;

   private final Screen parent;
   private final String initialQuery;
   private TextFieldWidget field;
   private TextFieldWidget[] rowFields = new TextFieldWidget[0];
   private List<MainChatLog.Line> results = List.of();
   private int scroll = 0;
   private long copiedAt = -1L;
   private String copiedWhat = "";

   public ChatSearchScreen(Screen parent) {
      this(parent, "");
   }

   public ChatSearchScreen(Screen parent, String initialQuery) {
      super(Text.literal("Search Chat"));
      this.parent = parent;
      this.initialQuery = initialQuery == null ? "" : initialQuery;
   }

   @Override
   protected void init() {
      this.field = new TextFieldWidget(this.textRenderer, this.width / 2 - 150, 16, 300, 20, Text.literal("Search"));
      this.field.setMaxLength(200);
      this.field.setPlaceholder(Text.literal("Search main chat: a name or any word..."));
      this.field.setText(this.initialQuery);
      this.field.setChangedListener(s -> {
         this.results = MainChatLog.INSTANCE.search(s);
         this.scroll = 0;
         this.refreshRowFields();
      });
      this.addDrawableChild(this.field);
      this.setInitialFocus(this.field);
      this.results = MainChatLog.INSTANCE.search(this.initialQuery);

      int rows = this.maxRows();
      int x = this.listX();
      int w = this.listWidth();
      this.rowFields = new TextFieldWidget[rows];
      for (int i = 0; i < rows; i++) {
         TextFieldWidget rowField = new TextFieldWidget(this.textRenderer, x, TOP + i * ROW_H, w, ROW_H - 1, Text.empty());
         rowField.setDrawsBackground(false);
         rowField.setEditableColor(0xFFC9BFD8);
         rowField.setMaxLength(4000);
         rowField.setTextPredicate(s -> s.equals(rowField.getText())); // selectable and copyable, but never actually editable
         rowField.setVisible(false);
         this.addDrawableChild(rowField);
         this.rowFields[i] = rowField;
      }

      this.refreshRowFields();

      int by = this.height - 24;
      this.addDrawableChild(StyledButton.of(Text.literal("Copy shown"), b -> this.copyShown()).dimensions(this.width / 2 - 214, by, 140, 20).build());
      this.addDrawableChild(StyledButton.of(Text.literal("Copy last 200"), b -> this.copyRecent()).dimensions(this.width / 2 - 70, by, 140, 20).build());
      this.addDrawableChild(StyledButton.of(Text.literal("Done"), b -> this.close()).dimensions(this.width / 2 + 74, by, 140, 20).build());
   }

   @Override
   public void close() {
      if (this.client != null) {
         this.client.setScreen(this.parent);
      }
   }

   @Override
   public boolean shouldPause() {
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

   /** Fills the visible row fields from the current results/scroll, hiding whichever rows have nothing to show. */
   private void refreshRowFields() {
      this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, this.results.size() - this.rowFields.length)));
      for (int i = 0; i < this.rowFields.length; i++) {
         TextFieldWidget rowField = this.rowFields[i];
         int index = this.scroll + i;
         if (index < this.results.size()) {
            rowField.setText(this.results.get(index).plain());
            rowField.setVisible(true);
         } else {
            rowField.setText("");
            rowField.setVisible(false);
         }
      }
   }

   private void copyToClipboard(String text, String what) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.keyboard != null) {
         mc.keyboard.setClipboard(text.isEmpty() ? " " : text);
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
   public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
      super.render(ctx, mouseX, mouseY, delta);
      TextRenderer tr = this.textRenderer;
      ctx.drawCenteredTextWithShadow(tr, Text.literal("Search Chat").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), this.width / 2, 4, -1);

      String query = this.field.getText();
      String count = this.results.isEmpty() ? "No matches"
         : this.results.size() + (this.results.size() == 1 ? " match" : " matches") + (this.results.size() >= MainChatLog.MAX_RESULTS ? " (showing the newest " + MainChatLog.MAX_RESULTS + ")" : "");
      ctx.drawCenteredTextWithShadow(tr, Text.literal(count).formatted(Formatting.GRAY), this.width / 2, TOP - 12, -1);

      int x = this.listX();
      int w = this.listWidth();
      MainChatLog.Line hoveredLine = null;
      for (int i = 0; i < this.rowFields.length && this.scroll + i < this.results.size(); i++) {
         int y = TOP + i * ROW_H;
         boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + ROW_H;
         if (hovered) {
            ctx.fill(x - 2, y - 1, x + w, y + ROW_H - 1, 0x33FFFFFF);
            hoveredLine = this.results.get(this.scroll + i);
         }
      }

      if (hoveredLine != null) {
         ctx.drawTooltip(tr, Text.literal(ChatTimestampFormat.format(hoveredLine.atMs(), System.currentTimeMillis())).formatted(Formatting.GRAY), mouseX, mouseY);
      }

      if (this.results.isEmpty() && !query.isBlank()) {
         ctx.drawCenteredTextWithShadow(tr, Text.literal("Nothing found. Only chat you've already seen since joining can be searched.").formatted(Formatting.DARK_GRAY), this.width / 2, TOP + 8, -1);
      }

      boolean showingCopied = this.copiedAt > 0 && System.currentTimeMillis() - this.copiedAt < 1500L;
      if (showingCopied) {
         ctx.drawCenteredTextWithShadow(tr, Text.literal("Copied " + this.copiedWhat + " to the clipboard.").formatted(Formatting.GREEN), this.width / 2, this.height - 40, -1);
      } else if (!this.results.isEmpty()) {
         ctx.drawCenteredTextWithShadow(
            tr, Text.literal("Click to place the cursor, shift-click or drag to select, Ctrl+C to copy - right-click a line to copy all of it.").formatted(Formatting.DARK_GRAY),
            this.width / 2, this.height - 40, -1
         );
      }
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (button == 1) {
         int x = this.listX();
         int w = this.listWidth();
         for (int i = 0; i < this.rowFields.length && this.scroll + i < this.results.size(); i++) {
            int y = TOP + i * ROW_H;
            if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + ROW_H) {
               this.copyToClipboard(this.results.get(this.scroll + i).plain(), "1 line");
               return true;
            }
         }
      }

      return super.mouseClicked(mouseX, mouseY, button);
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      if (verticalAmount != 0.0D) {
         this.scroll = Math.max(0, this.scroll - (int) Math.signum(verticalAmount) * 3);
         this.refreshRowFields();
         return true;
      }

      return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
         this.close();
         return true;
      }

      return super.keyPressed(keyCode, scanCode, modifiers);
   }
}
