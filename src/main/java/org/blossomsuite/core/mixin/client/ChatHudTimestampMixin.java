package org.blossomsuite.core.mixin.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.Mth;
import org.blossomsuite.core.chat.ChatHudLineLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Finds which visible chat line the mouse is over, for the hover timestamp. It repeats the layout vanilla draws the chat with
 * (rows counted up from {@code guiHeight - 40}, newest at the bottom, shifted by the scroll position), using vanilla's own
 * values for scale, width and line height. Nothing about vanilla's behaviour is changed.
 */
@Mixin(ChatComponent.class)
public abstract class ChatHudTimestampMixin implements ChatHudLineLookup {
   @Shadow
   private List<GuiMessage.Line> trimmedMessages;

   @Shadow
   private int chatScrollbarPos;

   @Shadow
   private double getScale() {
      throw new AssertionError();
   }

   @Shadow
   private int getWidth() {
      throw new AssertionError();
   }

   @Shadow
   private int getLineHeight() {
      throw new AssertionError();
   }

   @Shadow
   public abstract int getLinesPerPage();

   @Override
   public GuiMessage.Line suitecore$visibleLineAt(double mouseX, double mouseY) {
      double scale = this.getScale();
      double chatX = mouseX / scale - 4.0;
      if (chatX < -4.0 || chatX > Mth.floor(this.getWidth() / scale)) {
         return null;
      }

      int baseY = Mth.floor((Minecraft.getInstance().getWindow().getGuiScaledHeight() - 40) / (float)scale);
      double rowFromBottom = (baseY - mouseY / scale) / this.getLineHeight();
      if (rowFromBottom < 0.0) {
         return null;
      }

      int row = (int)Math.floor(rowFromBottom);
      int shown = Math.min(this.trimmedMessages.size() - this.chatScrollbarPos, this.getLinesPerPage());
      int index = row + this.chatScrollbarPos;
      return row < shown && index >= 0 && index < this.trimmedMessages.size() ? this.trimmedMessages.get(index) : null;
   }
}
