package org.blossomsuite.core.chat;

import net.minecraft.client.multiplayer.chat.GuiMessage;
/** Added to vanilla's {@code ChatHud} by a mixin, so the exact visible line under the mouse can be found from outside it. */
public interface ChatHudLineLookup {
   /** The visible chat line at this screen position (vanilla's own coordinates, same ones {@code getTextStyleAt} uses), or null. */
   GuiMessage.Line suitecore$visibleLineAt(double mouseX, double mouseY);

   /** The text style of the exact character under this screen position (null when it is not over a chat line's text). */
   net.minecraft.network.chat.Style suitecore$styleAt(double mouseX, double mouseY);
}
