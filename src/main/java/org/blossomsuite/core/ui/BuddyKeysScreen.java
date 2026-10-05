package org.blossomsuite.core.ui;

import java.util.List;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
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
   private KeyMapping waiting;

   public BuddyKeysScreen(Screen parent) {
      super(Component.literal("BlossomBuddy keys"));
      this.parent = parent;
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   private static String labelOf(KeyMapping binding) {
      return Component.translatable(binding.getName()).getString();
   }

   private int columnX(int column) {
      int total = 2 * (LABEL_W + BUTTON_W + 10) + 20;
      return this.width / 2 - total / 2 + column * (LABEL_W + BUTTON_W + 30);
   }

   @Override
   protected void init() {
      List<KeyMapping> keys = BuddyKeys.all();
      int perColumn = (keys.size() + 1) / 2;
      for (int i = 0; i < keys.size(); i++) {
         KeyMapping binding = keys.get(i);
         int column = i / perColumn;
         int row = i % perColumn;
         int x = this.columnX(column) + LABEL_W + 10;
         int y = 44 + row * ROW_H;
         Component shown = this.waiting == binding ? Component.literal("> press a key <").withStyle(ChatFormatting.YELLOW) : binding.getTranslatedKeyMessage();
         this.addRenderableWidget(StyledButton.of(shown, b -> {
            this.waiting = binding;
            this.rebuildWidgets();
         }).dimensions(x, y, BUTTON_W, 20).build());
      }

      this.addRenderableWidget(StyledButton.of(Component.literal("Done"), b -> this.onClose()).dimensions(this.width / 2 - 60, this.height - 28, 120, 20).build());
   }

   @Override
   public void onClose() {
      if (this.minecraft != null) {
         this.minecraft.options.save();
         this.minecraft.setScreen(this.parent);
      }
   }

   private void bind(InputConstants.Key key) {
      if (this.waiting != null) {
         this.waiting.setKey(key);
         KeyMapping.resetMapping();
         this.waiting = null;
         if (this.minecraft != null) {
            this.minecraft.options.save();
         }

         this.rebuildWidgets();
      }
   }

   @Override
   public boolean keyPressed(KeyEvent inputEvent) {
      int keyCode = inputEvent.key();
      int scanCode = inputEvent.scancode();
      int modifiers = inputEvent.modifiers();
      if (this.waiting != null) {
         this.bind(keyCode == GLFW.GLFW_KEY_ESCAPE ? InputConstants.UNKNOWN : InputConstants.getKey(inputEvent));
         return true;
      }

      if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
         this.onClose();
         return true;
      }

      return super.keyPressed(inputEvent);
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent inputEvent, boolean isDoubleClick) {
      double mouseX = inputEvent.x();
      double mouseY = inputEvent.y();
      int button = inputEvent.button();
      if (this.waiting != null && button > 1) { // side buttons can be keys; left and right click are needed to operate this screen
         this.bind(InputConstants.Type.MOUSE.getOrCreate(button));
         return true;
      }

      return super.mouseClicked(inputEvent, isDoubleClick);
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
      super.extractRenderState(ctx, mouseX, mouseY, delta);
      ctx.centeredText(this.font, Component.literal("BlossomBuddy keys").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), this.width / 2, 10, -1);
      ctx.centeredText(this.font, Component.literal("Click a key, then press the one you want. Escape clears it.").withStyle(ChatFormatting.GRAY), this.width / 2, 24, -1);

      List<KeyMapping> keys = BuddyKeys.all();
      int perColumn = (keys.size() + 1) / 2;
      for (int i = 0; i < keys.size(); i++) {
         int column = i / perColumn;
         int row = i % perColumn;
         ctx.text(this.font, Component.literal(labelOf(keys.get(i))), this.columnX(column), 50 + row * ROW_H, -1);
      }
   }
}
