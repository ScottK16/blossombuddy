package org.blossomsuite.core.events;

import org.blossomsuite.core.chat.SecondaryChat;

import org.blossomsuite.core.chat.ChatProcessor;
import org.blossomsuite.core.chat.MainChatLog;
import org.blossomsuite.core.config.SuiteConfig;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

public final class ChatEvents {
   private ChatEvents() {
   }

   public static void register() {
      ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
         if (!SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
            return true;
         }

         // before any secondary-chat filter gets a chance to divert/hide the line, so state is learned either way
         if (!overlay) {
            ChatProcessor.observeState(message);
         }

         if (!overlay && SecondaryChat.INSTANCE.onMessage(message)) {
            return false;
         }

         boolean show = ChatProcessor.process(message, overlay);
         // only what really lands in main chat is worth searching later; the action bar (overlay) is not chat
         if (show && !overlay) {
            MainChatLog.INSTANCE.record(message);
         }

         return show;
      });
      ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, params, receivedAt) -> {
         if (!SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
            return true;
         }

         if (SecondaryChat.INSTANCE.onMessage(message)) {
            return false;
         }

         MainChatLog.INSTANCE.record(message);
         return true;
      });
   }
}
