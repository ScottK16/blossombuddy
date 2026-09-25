package org.blossomsuite.core.ui;

import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.blossomsuite.core.BuddyKeys;
import org.lwjgl.glfw.GLFW;

/**
 * Set the BlossomBuddy keys without leaving the mod. They are ordinary Minecraft key bindings (so they also show up in Options >
 * Controls), but some game clients replace that screen and don't list a mod's keys, so this is a way to reach them either way. Click
 * a key, then press the one you want (Escape clears it).
 */
public final class BuddyKeysScreen extends Screen {
   private static final int ROW_H = 24;
   private static final int LABEL_W = 130;
   private static final int BUTTON_W = 80;

   private final Screen parent;
   private KeyBinding waiting;

   public BuddyKeysScreen(Screen parent) {
      super(Text.literal("BlossomBuddy keys"));
      this.parent = parent;
   }

   @Override
   public boolean shouldPause() {
      return false;
   }

   private static String labelOf(KeyBinding binding) {
      return Text.translatable(binding.getTranslationKey()).getString();
   }

   private int columnX(int column) {
      int total = 2 * (LABEL_W + BUTTON_W + 10) + 20;
      return this.width / 2 - total / 2 + column * (LABEL_W + BUTTON_W + 30);
   }

   @Override
   protected void init() {
      List<KeyBinding> keys = BuddyKeys.all();
      int perColumn = (keys.size() + 1) / 2;
      for (int i = 0; i < keys.size(); i++) {
         KeyBinding binding = keys.get(i);
         int column = i / perColumn;
         int row = i % perColumn;
         int x = this.columnX(column) + LABEL_W + 10;
         int y = 44 + row * ROW_H;
         Text shown = this.waiting == binding ? Text.literal("> press a key <").formatted(Formatting.YELLOW) : binding.getBoundKeyLocalizedText();
         this.addDrawableChild(StyledButton.of(shown, b -> {
            this.waiting = binding;
            this.clearAndInit();
         }).dimensions(x, y, BUTTON_W, 20).build());
      }

      this.addDrawableChild(StyledButton.of(Text.literal("Done"), b -> this.close()).dimensions(this.width / 2 - 60, this.height - 28, 120, 20).build());
   }

   @Override
   public void close() {
      if (this.client != null) {
         this.client.options.write();
         this.client.setScreen(this.parent);
      }
   }

   private void bind(InputUtil.Key key) {
      if (this.waiting != null) {
         this.waiting.setBoundKey(key);
         KeyBinding.updateKeysByCode();
         this.waiting = null;
         if (this.client != null) {
            this.client.options.write();
         }

         this.clearAndInit();
      }
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (this.waiting != null) {
         this.bind(keyCode == GLFW.GLFW_KEY_ESCAPE ? InputUtil.UNKNOWN_KEY : InputUtil.fromKeyCode(keyCode, scanCode));
         return true;
      }

      if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
         this.close();
         return true;
      }

      return super.keyPressed(keyCode, scanCode, modifiers);
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (this.waiting != null && button > 1) { // side buttons can be keys; left and right click are needed to operate this screen
         this.bind(InputUtil.Type.MOUSE.createFromCode(button));
         return true;
      }

      return super.mouseClicked(mouseX, mouseY, button);
   }

   @Override
   public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
      super.render(ctx, mouseX, mouseY, delta);
      ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("BlossomBuddy keys").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), this.width / 2, 10, -1);
      ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Click a key, then press the one you want. Escape clears it.").formatted(Formatting.GRAY), this.width / 2, 24, -1);

      List<KeyBinding> keys = BuddyKeys.all();
      int perColumn = (keys.size() + 1) / 2;
      for (int i = 0; i < keys.size(); i++) {
         int column = i / perColumn;
         int row = i % perColumn;
         ctx.drawTextWithShadow(this.textRenderer, Text.literal(labelOf(keys.get(i))), this.columnX(column), 50 + row * ROW_H, -1);
      }
   }
}
