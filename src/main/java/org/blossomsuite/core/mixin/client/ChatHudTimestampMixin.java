package org.blossomsuite.core.mixin.client;

import java.util.List;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import org.blossomsuite.core.chat.ChatHudLineLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Exposes vanilla's own chat hit-testing (the same math {@code getTextStyleAt} uses for link hover) so the mod can find which
 * visible line the mouse is over, for the hover timestamp. Nothing about vanilla's behaviour is changed.
 */
@Mixin(ChatHud.class)
public abstract class ChatHudTimestampMixin implements ChatHudLineLookup {
   @Shadow
   private List<ChatHudLine.Visible> visibleMessages;

   @Shadow
   private double toChatLineX(double x) {
      throw new AssertionError();
   }

   @Shadow
   private double toChatLineY(double y) {
      throw new AssertionError();
   }

   @Shadow
   private int getMessageLineIndex(double chatLineX, double chatLineY) {
      throw new AssertionError();
   }

   @Override
   public ChatHudLine.Visible suitecore$visibleLineAt(double mouseX, double mouseY) {
      int i = this.getMessageLineIndex(this.toChatLineX(mouseX), this.toChatLineY(mouseY));
      return i >= 0 && i < this.visibleMessages.size() ? this.visibleMessages.get(i) : null;
   }
}
