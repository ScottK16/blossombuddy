package org.blossomsuite.core.chat;

import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.util.TextUtil;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class ChatOutput {
   private ChatOutput() {
   }

   public static void info(String message) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.player != null) {
         MutableText full = Text.empty().append(createPrefix()).append(Text.literal(message).formatted(Formatting.GRAY));
         client.player.sendMessage(full, false);
      }
   }

   public static void info(Text message) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.player != null) {
         MutableText full = Text.empty().append(createPrefix()).append(message);
         client.player.sendMessage(full, false);
      }
   }

   private static Text createPrefix() {
      MutableText prefix = Text.literal("[").formatted(Formatting.DARK_GRAY);
      prefix.append(TextUtil.gradient(SuiteRuntime.profile().displayName().toUpperCase(Locale.ROOT), -11672879, -6591489, true));
      prefix.append(Text.literal("] ").formatted(Formatting.DARK_GRAY));
      return prefix;
   }

   public static void raw(Text message) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.player != null) {
         client.player.sendMessage(message, false);
      }
   }
}
