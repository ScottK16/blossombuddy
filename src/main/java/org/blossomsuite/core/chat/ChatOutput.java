package org.blossomsuite.core.chat;

import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.util.TextUtil;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
public final class ChatOutput {
   private ChatOutput() {
   }

   public static void info(String message) {
      Minecraft client = Minecraft.getInstance();
      if (client.player != null) {
         MutableComponent full = Component.empty().append(createPrefix()).append(Component.literal(message).withStyle(ChatFormatting.GRAY));
         client.player.sendSystemMessage(full);
      }
   }

   public static void info(Component message) {
      Minecraft client = Minecraft.getInstance();
      if (client.player != null) {
         MutableComponent full = Component.empty().append(createPrefix()).append(message);
         client.player.sendSystemMessage(full);
      }
   }

   private static Component createPrefix() {
      MutableComponent prefix = Component.literal("[").withStyle(ChatFormatting.DARK_GRAY);
      prefix.append(TextUtil.gradient(SuiteRuntime.profile().displayName().toUpperCase(Locale.ROOT), -11672879, -6591489, true));
      prefix.append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY));
      return prefix;
   }

   public static void raw(Component message) {
      Minecraft client = Minecraft.getInstance();
      if (client.player != null) {
         client.player.sendSystemMessage(message);
      }
   }
}
