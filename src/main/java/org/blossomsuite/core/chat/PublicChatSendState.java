package org.blossomsuite.core.chat;

import org.blossomsuite.core.config.ChatConfig;
import org.blossomsuite.core.config.SuiteConfig;
import net.minecraft.client.Minecraft;
public final class PublicChatSendState {
   private static final int PUBLIC_SEND_DELAY_TICKS = 0;
   private static final int RESTORE_AFTER_SEND_DELAY_TICKS = 2;
   private static volatile PublicChatSendState.Step step = PublicChatSendState.Step.IDLE;
   private static volatile String pendingMessage = null;
   private static volatile boolean restoreMarry = false;
   private static volatile boolean restoreParty = false;
   private static volatile boolean restoreStaff = false;
   private static volatile boolean startAdCooldown = false;
   private static volatile int sendDelayTicks = 0;
   private static volatile int restoreDelayTicks = 0;

   private PublicChatSendState() {
   }

   public static boolean isActive() {
      return step != PublicChatSendState.Step.IDLE;
   }

   public static void requestSendPublic(String msg, boolean startAdvertisementCooldown) {
      if (msg != null && !msg.isBlank()) {
         if (!isActive()) {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.player != null && client.getConnection() != null) {
               ChatConfig chatCfg = SuiteConfig.INSTANCE.ChatConfig;
               // Which channel the player is actually in is tracked regardless of whether the "Show Tracked
               // Channel" HUD is turned on - that setting only controls a cosmetic display, so it must never decide
               // whether this correctly detects and switches out of marry/party/staff chat before sending.
               ChatChannel base = chatCfg.trackedChannel;
               if (base == null) {
                  base = ChatChannel.UNKNOWN;
               }

               if (base == ChatChannel.STAFF || base == ChatChannel.PARTY) {
                  base = ChatChannel.PUBLIC;
               }

               boolean staffOn = StaffChatState.isStaffTrackingActive() && StaffChatState.staffChatEnabled;
               boolean partyOn = PartyChatState.partyChatEnabled;
               boolean isMarry = base == ChatChannel.MARRY;
               boolean isPublic = base == ChatChannel.PUBLIC || base == ChatChannel.UNKNOWN;
               restoreMarry = isMarry;
               restoreParty = partyOn;
               restoreStaff = staffOn;
               startAdCooldown = startAdvertisementCooldown;
               pendingMessage = msg;
               if (staffOn) {
                  step = PublicChatSendState.Step.WAIT_STAFF_OFF;
                  client.getConnection().sendCommand("sch toggle");
               } else if (partyOn) {
                  step = PublicChatSendState.Step.WAIT_PARTY_OFF;
                  client.getConnection().sendCommand("party chat off");
               } else if (isMarry) {
                  step = PublicChatSendState.Step.WAIT_PUBLIC;
                  client.getConnection().sendCommand("marry chattoggle");
               } else if (isPublic) {
                  sendNow(client, msg);
                  finishAfterSend(client);
               }
            }
         }
      }
   }

   public static void onBaseChannelNowPublic() {
      if (step == PublicChatSendState.Step.WAIT_PUBLIC) {
         Minecraft client = Minecraft.getInstance();
         if (client != null && client.player != null && client.getConnection() != null) {
            scheduleDelayedPublicSend();
         }
      }
   }

   public static void onBaseChannelNowMarry() {
      if (step == PublicChatSendState.Step.WAIT_MARRY_RESTORE) {
         Minecraft client = Minecraft.getInstance();
         if (client != null && client.player != null && client.getConnection() != null) {
            if (restoreParty) {
               step = PublicChatSendState.Step.WAIT_PARTY_RESTORE;
               client.getConnection().sendCommand("party chat on");
            } else if (restoreStaff) {
               step = PublicChatSendState.Step.WAIT_STAFF_RESTORE;
               client.getConnection().sendCommand("sch toggle");
            } else {
               clear();
            }
         }
      }
   }

   public static void onStaffChatToggled(boolean enabled) {
      if (StaffChatState.trackingEnabled()) {
         if (step == PublicChatSendState.Step.WAIT_STAFF_OFF && !enabled) {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.player != null && client.getConnection() != null) {
               if (restoreParty) {
                  step = PublicChatSendState.Step.WAIT_PARTY_OFF;
                  client.getConnection().sendCommand("party chat off");
               } else if (restoreMarry) {
                  step = PublicChatSendState.Step.WAIT_PUBLIC;
                  client.getConnection().sendCommand("marry chattoggle");
               } else if (restoreParty) {
                  step = PublicChatSendState.Step.WAIT_PARTY_OFF;
                  client.getConnection().sendCommand("party chat off");
               } else {
                  scheduleDelayedPublicSend();
               }
            }
         } else {
            if (step == PublicChatSendState.Step.WAIT_STAFF_RESTORE && enabled) {
               clear();
            }
         }
      }
   }

   public static void onPartyChatToggled(boolean enabled) {
      if (step == PublicChatSendState.Step.WAIT_PARTY_OFF && !enabled) {
         Minecraft client = Minecraft.getInstance();
         if (client != null && client.player != null && client.getConnection() != null) {
            if (restoreMarry) {
               step = PublicChatSendState.Step.WAIT_PUBLIC;
               client.getConnection().sendCommand("marry chattoggle");
            } else {
               scheduleDelayedPublicSend();
            }
         }
      } else {
         if (step == PublicChatSendState.Step.WAIT_PARTY_RESTORE && enabled) {
            if (restoreStaff) {
               Minecraft client = Minecraft.getInstance();
               if (client != null && client.player != null && client.getConnection() != null) {
                  step = PublicChatSendState.Step.WAIT_STAFF_RESTORE;
                  client.getConnection().sendCommand("sch toggle");
                  return;
               }

               return;
            }

            clear();
         }
      }
   }

   public static void tick(Minecraft client) {
      if (step == PublicChatSendState.Step.WAIT_SEND_PUBLIC || step == PublicChatSendState.Step.WAIT_RESTORE_AFTER_SEND) {
         if (client == null || client.player == null || client.getConnection() == null) {
            clear();
         } else if (step == PublicChatSendState.Step.WAIT_RESTORE_AFTER_SEND) {
            if (restoreDelayTicks > 0) {
               restoreDelayTicks--;
            } else {
               restoreAfterSend(client);
            }
         } else if (sendDelayTicks > 0) {
            sendDelayTicks--;
         } else {
            String msg = pendingMessage;
            pendingMessage = null;
            if (msg != null && !msg.isBlank()) {
               sendNow(client, msg);
               finishAfterSend(client);
            } else {
               clear();
            }
         }
      }
   }

   private static void scheduleDelayedPublicSend() {
      if (pendingMessage != null && !pendingMessage.isBlank()) {
         step = PublicChatSendState.Step.WAIT_SEND_PUBLIC;
         sendDelayTicks = 0;
      } else {
         clear();
      }
   }

   private static void sendNow(Minecraft client, String msg) {
      client.player.connection.sendChat(msg);
      if (startAdCooldown) {
         AdvertisementState.startCooldown();
      }
   }

   private static void finishAfterSend(Minecraft client) {
      if (!restoreMarry && !restoreParty && !restoreStaff) {
         clear();
      } else {
         step = PublicChatSendState.Step.WAIT_RESTORE_AFTER_SEND;
         restoreDelayTicks = 2;
      }
   }

   private static void restoreAfterSend(Minecraft client) {
      if (restoreMarry) {
         step = PublicChatSendState.Step.WAIT_MARRY_RESTORE;
         client.getConnection().sendCommand("marry chattoggle");
      } else if (restoreParty) {
         step = PublicChatSendState.Step.WAIT_PARTY_RESTORE;
         client.getConnection().sendCommand("party chat on");
      } else if (restoreStaff) {
         step = PublicChatSendState.Step.WAIT_STAFF_RESTORE;
         client.getConnection().sendCommand("sch toggle");
      } else {
         clear();
      }
   }

   private static void clear() {
      step = PublicChatSendState.Step.IDLE;
      pendingMessage = null;
      restoreMarry = false;
      restoreParty = false;
      restoreStaff = false;
      startAdCooldown = false;
      sendDelayTicks = 0;
      restoreDelayTicks = 0;
   }

   private enum Step {
      IDLE,
      WAIT_STAFF_OFF,
      WAIT_PARTY_OFF,
      WAIT_PUBLIC,
      WAIT_SEND_PUBLIC,
      WAIT_RESTORE_AFTER_SEND,
      WAIT_MARRY_RESTORE,
      WAIT_PARTY_RESTORE,
      WAIT_STAFF_RESTORE;
   }
}
