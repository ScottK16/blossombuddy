package org.blossomsuite.core.mixin.client;

import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.chat.PartyChatState;
import org.blossomsuite.core.qol.autofly.AutoFlyController;
import java.util.Locale;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import org.blossomsuite.core.hud.ChatWindowHud;
import org.blossomsuite.core.hud.SecondaryChatHud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.blossomsuite.core.chat.ChatHudLineLookup;
import org.blossomsuite.core.chat.ChatLineTimestamps;
import org.blossomsuite.core.chat.ChatTimestampFormat;
import org.blossomsuite.core.xchat.XChatClient;
import org.blossomsuite.core.xchat.XChatMode;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {
   /** Cross-realm chat mode: what is typed goes to every realm instead of the server (commands are left alone). */
   @Inject(method = "sendMessage", at = @At("HEAD"), cancellable = true)
   private void suitecore$crossRealmMode(String chatText, boolean addToHistory, CallbackInfo ci) {
      String redirected = XChatMode.redirect(chatText);
      if (redirected != null) {
         if (addToHistory) {
            MinecraftClient.getInstance().inGameHud.getChatHud().addToMessageHistory(chatText);
         }

         XChatClient.INSTANCE.sendAsync(redirected);
         ci.cancel();
      }
   }

   /** A reminder above the chat box while cross-realm chat mode is on. */
   @Inject(method = "render", at = @At("TAIL"))
   private void suitecore$crossRealmModeLabel(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      if (XChatMode.active()) {
         MinecraftClient mc = MinecraftClient.getInstance();
         context.drawTextWithShadow(mc.textRenderer, Text.literal("\u273f Cross-realm chat mode: what you type goes to every realm. /xc switches it off.").styled(s -> s.withColor(0xF48FB1)), 4, mc.getWindow().getScaledHeight() - 26, -1);
      }
   }

   /** Hovering a main chat line shows when it was sent. */
   @Inject(method = "render", at = @At("TAIL"))
   private void suitecore$hoverTimestamp(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      MinecraftClient mc = MinecraftClient.getInstance();
      Object chatHud = mc.inGameHud.getChatHud();
      if (!(chatHud instanceof ChatHudLineLookup lookup)) {
         return;
      }

      ChatHudLine.Visible line = lookup.suitecore$visibleLineAt(mouseX, mouseY);
      if (line == null) {
         return;
      }

      Long atMs = ChatLineTimestamps.timeOf(line);
      if (atMs != null) {
         context.drawTooltip(mc.textRenderer, Text.literal(ChatTimestampFormat.format(atMs, System.currentTimeMillis())).formatted(Formatting.GRAY), mouseX, mouseY);
      }
   }

   /** The mouse wheel over the secondary chat window scrolls that window, not the main chat. */
   @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
   private void suitecore$scrollSecondaryChat(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
      boolean shift = Screen.hasShiftDown();
      if (SecondaryChatHud.INSTANCE.onScroll(mouseX, mouseY, verticalAmount, shift) || ChatWindowHud.scrollAny(mouseX, mouseY, verticalAmount, shift)) {
         cir.setReturnValue(true);
      }
   }

   @ModifyVariable(method = "sendMessage", at = @At("HEAD"), argsOnly = true)
   private String suitecore$normalizeCommandRoot(String message) {
      AutoFlyController.observeOutgoingCommand(message);
      Boolean partyEnabled = PartyChatState.parseOutgoingCommand(message);
      if (partyEnabled != null) {
         PartyChatState.setPartyChatEnabled(partyEnabled);
      } else if (PartyChatState.isOutgoingPartyExitCommand(message)) {
         PartyChatState.setPartyChatEnabled(false);
      }

      if (message != null && message.length() >= 2 && message.charAt(0) == '/') {
         int space = message.indexOf(32);
         String root = space < 0 ? message.substring(1) : message.substring(1, space);
         String normalizedRoot = root.toLowerCase(Locale.ROOT);
         if (!suitecore$isSuiteCommand(normalizedRoot)) {
            return message;
         }

         String rest = space < 0 ? "" : message.substring(space);
         return "/" + normalizedRoot + rest;
      } else {
         return message;
      }
   }

   private static boolean suitecore$isSuiteCommand(String normalizedRoot) {
      if (normalizedRoot.equals(SuiteRuntime.profile().commandName().toLowerCase(Locale.ROOT))) {
         return true;
      }

      for (String alias : SuiteRuntime.profile().commandAliases()) {
         if (normalizedRoot.equals(alias.toLowerCase(Locale.ROOT))) {
            return true;
         }
      }

      return false;
   }
}
