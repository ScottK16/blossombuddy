package org.blossomsuite.core.events;

import org.blossomsuite.core.chat.ChatNotify;
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
            ChatNotify.INSTANCE.onLine(message.getString());
            return false;
         }

         boolean show = ChatProcessor.process(message, overlay);
         // only what really lands in main chat is worth searching later; the action bar (overlay) is not chat
         if (show && !overlay) {
            MainChatLog.INSTANCE.record(message);
            ChatNotify.INSTANCE.onLine(message.getString());
         }

         return show;
      });
      ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, params, receivedAt) -> {
         if (!SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
            return true;
         }

         String sentBy = sender == null ? null : sender.name();
         if (SecondaryChat.INSTANCE.onMessage(message)) {
            ChatNotify.INSTANCE.onPlayerChat(message.getString(), sentBy);
            return false;
         }

         MainChatLog.INSTANCE.record(message);
         ChatNotify.INSTANCE.onPlayerChat(message.getString(), sentBy);
         return true;
      });
   }
}
