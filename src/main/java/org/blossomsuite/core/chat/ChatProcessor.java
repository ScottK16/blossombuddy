package org.blossomsuite.core.chat;

import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.config.ChatConfig;
import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.util.TextUtil;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public final class ChatProcessor {
   private static final Pattern MONEY_AMOUNT = Pattern.compile("\\$\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)");

   private ChatProcessor() {
   }

   public static boolean process(Text message, boolean overlay) {
      if (overlay) {
         return true;
      }

      ChatConfig chatCfg = SuiteConfig.INSTANCE.ChatConfig;
      if (chatCfg.hideInventoryFullVoucherMessages && isInventoryFullVoucherMessage(message)) {
         return false;
      }

      if (chatCfg.hideCooldownMessages && isCooldownMessage(message)) {
         return false;
      }

      if (chatCfg.hideInventoryCompactMessages && isInventoryCompactMessage(message)) {
         return false;
      }

      if (chatCfg.hidePinataBalanceMessages && isPinataBalanceMessageBelowThreshold(message, chatCfg.pinataBalanceHideBelowDollars)) {
         return false;
      }

      if (chatCfg.crateMessageMode == ChatConfig.CrateMessageMode.HIDE && CrateMessageFormatter.isCrateMessage(message)) {
         return false;
      }

      if (chatCfg.crateMessageMode == ChatConfig.CrateMessageMode.UPDATED) {
         Text cleaned = CrateMessageFormatter.tryRewrite(message);
         if (cleaned != null) {
            ChatOutput.raw(cleaned);
            return false;
         }
      }

      String text = message.getString();
      if (isSuiteMessage(text)) {
         return true;
      }

      if (text != null && StaffChatState.trackingEnabled() && text.contains("---[Online Staff]---")) {
         StaffChatState.observeOnlineStaffHeader();
      }

      observeChatToggleMessage(text);
      return true;
   }

   private static boolean isSuiteMessage(String text) {
      return text != null && text.contains("[" + SuiteRuntime.profile().displayName().toUpperCase(Locale.ROOT) + "]");
   }

   private static boolean isInventoryFullVoucherMessage(Text message) {
      if (message == null) {
         return false;
      } else {
         String raw = message.getString();
         if (raw != null && !raw.isBlank()) {
            String folded = TextUtil.foldToLettersDigitsSpace(raw);
            return folded != null && !folded.isBlank()
               ? folded.contains("items your inventory is full") && folded.contains("voucher") && folded.contains("has been give to you around your feet")
               : false;
         } else {
            return false;
         }
      }
   }

   private static boolean isCooldownMessage(Text message) {
      if (message == null) {
         return false;
      } else {
         String raw = message.getString();
         if (raw != null && !raw.isBlank()) {
            String folded = TextUtil.foldToLettersDigitsSpace(raw);
            return folded != null && !folded.isBlank() ? folded.contains("you are in cooldown") || folded.contains(" is on cooldown") : false;
         } else {
            return false;
         }
      }
   }

   private static boolean isInventoryCompactMessage(Text message) {
      if (message == null) {
         return false;
      } else {
         String raw = message.getString();
         if (raw != null && !raw.isBlank()) {
            String folded = TextUtil.foldToLettersDigitsSpace(raw);
            return folded != null && !folded.isBlank()
               ? folded.contains("you have no items that can be converted into blocks")
                  || folded.contains("converted all items into blocks")
                  || folded.contains("converted all items into block")
               : false;
         } else {
            return false;
         }
      }
   }

   private static boolean isPinataBalanceMessageBelowThreshold(Text message, int thresholdDollars) {
      if (message == null) {
         return false;
      }

      String raw = message.getString();
      if (raw != null && !raw.isBlank()) {
         String folded = TextUtil.foldToLettersDigitsSpace(raw);
         if (folded == null || folded.isBlank()) {
            return false;
         }

         if (!folded.contains("has been added to your account")) {
            return false;
         }

         Matcher matcher = MONEY_AMOUNT.matcher(raw);
         if (!matcher.find()) {
            return false;
         }

         try {
            double amount = Double.parseDouble(matcher.group(1).replace(",", ""));
            return amount < Math.max(0, thresholdDollars);
         } catch (NumberFormatException ignored) {
            return false;
         }
      } else {
         return false;
      }
   }

   private static void observeChatToggleMessage(String text) {
      if (text != null && !text.isBlank()) {
         ChatConfig cfg = SuiteConfig.INSTANCE.ChatConfig;
         Boolean vanished = VanishState.parseServerLine(text, selfName());
         if (vanished != null) {
            VanishState.setVanished(vanished);
         }

         if (!isNotInPartyMessage(text) && !isPartyEndedMessage(text)) {
            Boolean partyChatEnabled = PartyChatState.parseServerLine(text);
            if (partyChatEnabled != null) {
               PartyChatState.setPartyChatEnabled(partyChatEnabled);
               PublicChatSendState.onPartyChatToggled(partyChatEnabled);
               ConfigIO.saveIfDirty();
            } else if (StaffChatState.isStaffTrackingActive() && text.contains("[Staff] updated staff chat toggle to on.")) {
               ChatModeProbe.onStaffToggleLine(true);
               PublicChatSendState.onStaffChatToggled(true);
               ConfigIO.saveIfDirty();
            } else if (StaffChatState.isStaffTrackingActive() && text.contains("[Staff] updated staff chat toggle to off.")) {
               ChatModeProbe.onStaffToggleLine(false);
               PublicChatSendState.onStaffChatToggled(false);
               ConfigIO.saveIfDirty();
            } else if (text.contains("You are not married.")) {
               ChatModeProbe.onMarryNotMarriedLine();
               cfg.setTrackedChannel(ChatChannel.PUBLIC);
               ConfigIO.saveIfDirty();
            } else if (text.contains("You have set your chat to public chat.")) {
               cfg.setTrackedChannel(ChatChannel.PUBLIC);
               ConfigIO.saveIfDirty();
               ChatModeProbe.onMarryNowPublicLine();
               PublicChatSendState.onBaseChannelNowPublic();
            } else {
               if (text.contains("You have set your chat to the private marry chat.")) {
                  cfg.setTrackedChannel(ChatChannel.MARRY);
                  ConfigIO.saveIfDirty();
                  ChatModeProbe.onMarryNowPrivateLine();
                  PublicChatSendState.onBaseChannelNowMarry();
               }
            }
         } else {
            PartyChatState.setPartyChatEnabled(false);
            PublicChatSendState.onPartyChatToggled(false);
            ConfigIO.saveIfDirty();
         }
      }
   }

   private static String selfName() {
      MinecraftClient mc = MinecraftClient.getInstance();
      return mc == null || mc.getSession() == null ? "" : mc.getSession().getUsername();
   }

   private static boolean isNotInPartyMessage(String text) {
      if (text != null && !text.isBlank()) {
         String folded = TextUtil.foldToLettersDigitsSpace(text).toLowerCase(Locale.ROOT);
         return folded.contains("you are not in a party") || folded.contains("you re not in a party") || folded.contains("you arent in a party");
      } else {
         return false;
      }
   }

   private static boolean isPartyEndedMessage(String text) {
      if (text != null && !text.isBlank()) {
         String folded = TextUtil.foldToLettersDigitsSpace(text).toLowerCase(Locale.ROOT);
         return folded.contains("you left the party")
            || folded.contains("you have left the party")
            || folded.contains("party has been disbanded")
            || folded.contains("the party has been disbanded because the leader left")
            || folded.contains("the party was disbanded");
      } else {
         return false;
      }
   }

}
