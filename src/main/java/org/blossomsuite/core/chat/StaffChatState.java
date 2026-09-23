package org.blossomsuite.core.chat;

import org.blossomsuite.core.config.SuiteConfig;

public final class StaffChatState {
   public static volatile boolean isStaffMember = false;
   public static volatile boolean staffChatEnabled = false;

   private StaffChatState() {
   }

   public static boolean trackingEnabled() {
      return SuiteConfig.INSTANCE.ChatConfig.trackStaffChat;
   }

   public static boolean isStaffTrackingActive() {
      return trackingEnabled() && isStaffMember;
   }

   public static void observeOnlineStaffHeader() {
      if (trackingEnabled()) {
         isStaffMember = true;
      }
   }

   public static void observeStaffChatState(boolean enabled) {
      if (trackingEnabled()) {
         isStaffMember = true;
         staffChatEnabled = enabled;
      }
   }

   public static void setStaffChatEnabled(boolean enabled) {
      if (trackingEnabled()) {
         staffChatEnabled = enabled;
      }
   }

   public static void resetForJoin() {
      isStaffMember = false;
      staffChatEnabled = false;
   }
}
