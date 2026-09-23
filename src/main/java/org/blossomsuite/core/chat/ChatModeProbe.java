package org.blossomsuite.core.chat;

import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.SuiteConfig;
import net.minecraft.client.MinecraftClient;

public final class ChatModeProbe {
   private static volatile ChatModeProbe.Phase phase = ChatModeProbe.Phase.IDLE;
   private static volatile boolean marryProbeDone = false;
   private static volatile boolean staffProbeDone = false;
   private static volatile boolean marryProbeStarted = false;
   private static volatile boolean staffProbeStarted = false;

   private ChatModeProbe() {
   }

   public static void resetForJoin() {
      phase = ChatModeProbe.Phase.IDLE;
      marryProbeDone = false;
      staffProbeDone = false;
      marryProbeStarted = false;
      staffProbeStarted = false;
   }

   public static void requestProbe() {
      resetForJoin();
   }

   public static void tick() {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.player != null && client.getNetworkHandler() != null) {
         // This is what discovers which channel the player is actually in, which PublicChatSendState relies on to
         // correctly switch out of marry/party/staff chat before sending - it must always run, independent of
         // whether the "Show Tracked Channel" HUD (a cosmetic display) is turned on.
         if (!PublicChatSendState.isActive()) {
            if (!staffProbeStarted && !staffProbeDone && StaffChatState.isStaffTrackingActive() && phase == ChatModeProbe.Phase.IDLE) {
               staffProbeStarted = true;
               phase = ChatModeProbe.Phase.STAFF_TOGGLE_WAIT;
               client.getNetworkHandler().sendChatCommand("sch toggle");
            } else {
               if (!marryProbeStarted && !marryProbeDone && phase == ChatModeProbe.Phase.IDLE) {
                  marryProbeStarted = true;
                  phase = ChatModeProbe.Phase.MARRY_TOGGLE_WAIT;
                  client.getNetworkHandler().sendChatCommand("marry chattoggle");
               }
            }
         }
      }
   }

   public static void onStaffToggleLine(boolean nowEnabled) {
      if (StaffChatState.trackingEnabled()) {
         StaffChatState.setStaffChatEnabled(nowEnabled);
         if (phase == ChatModeProbe.Phase.STAFF_TOGGLE_WAIT) {
            phase = ChatModeProbe.Phase.STAFF_RESTORE_WAIT;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getNetworkHandler() != null) {
               client.getNetworkHandler().sendChatCommand("sch toggle");
            }
         } else {
            if (phase == ChatModeProbe.Phase.STAFF_RESTORE_WAIT) {
               staffProbeDone = true;
               phase = ChatModeProbe.Phase.IDLE;
            }
         }
      }
   }

   public static void onStaffScoreboardLine(boolean enabled) {
      if (StaffChatState.trackingEnabled()) {
         StaffChatState.observeStaffChatState(enabled);
         if (phase != ChatModeProbe.Phase.STAFF_TOGGLE_WAIT && phase != ChatModeProbe.Phase.STAFF_RESTORE_WAIT) {
            staffProbeStarted = true;
            staffProbeDone = true;
         } else {
            onStaffToggleLine(enabled);
         }
      }
   }

   public static void onMarryNotMarriedLine() {
      SuiteConfig.INSTANCE.ChatConfig.setTrackedChannel(ChatChannel.PUBLIC);
      ConfigIO.saveIfDirty();
      marryProbeDone = true;
      if (phase == ChatModeProbe.Phase.MARRY_TOGGLE_WAIT) {
         phase = ChatModeProbe.Phase.IDLE;
      }
   }

   public static void onMarryNowPublicLine() {
      if (phase == ChatModeProbe.Phase.MARRY_TOGGLE_WAIT) {
         phase = ChatModeProbe.Phase.MARRY_RESTORE_WAIT;
         MinecraftClient client = MinecraftClient.getInstance();
         if (client != null && client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendChatCommand("marry chattoggle");
         }
      } else {
         if (phase == ChatModeProbe.Phase.MARRY_RESTORE_WAIT) {
            marryProbeDone = true;
            phase = ChatModeProbe.Phase.IDLE;
         }
      }
   }

   public static void onMarryNowPrivateLine() {
      if (phase == ChatModeProbe.Phase.MARRY_TOGGLE_WAIT) {
         phase = ChatModeProbe.Phase.MARRY_RESTORE_WAIT;
         MinecraftClient client = MinecraftClient.getInstance();
         if (client != null && client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendChatCommand("marry chattoggle");
         }
      } else {
         if (phase == ChatModeProbe.Phase.MARRY_RESTORE_WAIT) {
            marryProbeDone = true;
            phase = ChatModeProbe.Phase.IDLE;
         }
      }
   }

   private enum Phase {
      IDLE,
      STAFF_TOGGLE_WAIT,
      STAFF_RESTORE_WAIT,
      MARRY_TOGGLE_WAIT,
      MARRY_RESTORE_WAIT,
      DONE;
   }
}
