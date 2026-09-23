package org.blossomsuite.core.config;

import org.blossomsuite.core.chat.ChatChannel;
import java.util.ArrayList;
import java.util.List;

public final class ChatConfig {
   public ChatChannel trackedChannel = ChatChannel.UNKNOWN;
   public boolean showTrackedChannelHud = true;
   public boolean showAdvertisementHud = true;
   public boolean trackStaffChat = true;
   public boolean showStaffHud = true;
   public String advertisementMessage = "";
   public String welcomeMessage = "";
   public List<ChatConfig.AdvertiserProfile> advertiserProfiles = new ArrayList<>();
   public int advertiserActiveProfile = 0;
   public float positionX = 0.5F;
   public float positionY = 0.5F;
   public float scale = 1.0F;
   public float backgroundOpacity = 0.0F;
   public boolean compact = true;
   public boolean cleanUpCrateMessages = true;
   public ChatConfig.CrateMessageMode crateMessageMode = ChatConfig.CrateMessageMode.UPDATED;
   public boolean hideInventoryFullVoucherMessages = false;
   public boolean hideCooldownMessages = false;
   public boolean hideInventoryCompactMessages = false;
   public boolean hidePinataBalanceMessages = false;
   public int pinataBalanceHideBelowDollars = 100;
   public long advertisementReadyAtEpochMs = 0L;

   public void ensureAdvertiserProfilesInitialized() {
      if (this.advertiserProfiles == null) {
         this.advertiserProfiles = new ArrayList<>();
      }

      if (this.advertiserProfiles.isEmpty()) {
         ChatConfig.AdvertiserProfile p = new ChatConfig.AdvertiserProfile();
         p.name = "Default";
         p.message = this.advertisementMessage == null ? "" : this.advertisementMessage;
         this.advertiserProfiles.add(p);
         this.advertiserActiveProfile = 0;
      }

      if (this.advertiserActiveProfile < 0 || this.advertiserActiveProfile >= this.advertiserProfiles.size()) {
         this.advertiserActiveProfile = 0;
      }

      ChatConfig.AdvertiserProfile active = this.advertiserProfiles.get(this.advertiserActiveProfile);
      this.advertisementMessage = active.message == null ? "" : active.message;
   }

   public ChatConfig.AdvertiserProfile getActiveAdvertiserProfile() {
      this.ensureAdvertiserProfilesInitialized();
      return this.advertiserProfiles.get(this.advertiserActiveProfile);
   }

   public String getActiveAdvertiserProfileName() {
      ChatConfig.AdvertiserProfile p = this.getActiveAdvertiserProfile();
      String n = p.name == null ? "" : p.name;
      return n.isBlank() ? "Profile " + (this.advertiserActiveProfile + 1) : n;
   }

   public String getActiveAdvertisementMessage() {
      ChatConfig.AdvertiserProfile p = this.getActiveAdvertiserProfile();
      return p.message == null ? "" : p.message;
   }

   public void cycleAdvertiserProfile() {
      this.ensureAdvertiserProfilesInitialized();
      if (this.advertiserProfiles.size() > 1) {
         this.advertiserActiveProfile = (this.advertiserActiveProfile + 1) % this.advertiserProfiles.size();
         this.advertisementMessage = this.getActiveAdvertisementMessage();
         SuiteConfig.INSTANCE.markDirty();
      }
   }

   public void addAdvertiserProfile() {
      this.ensureAdvertiserProfilesInitialized();
      ChatConfig.AdvertiserProfile p = new ChatConfig.AdvertiserProfile();
      p.name = "Profile " + (this.advertiserProfiles.size() + 1);
      p.message = "";
      this.advertiserProfiles.add(p);
      this.advertiserActiveProfile = this.advertiserProfiles.size() - 1;
      this.advertisementMessage = p.message;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void deleteActiveAdvertiserProfile() {
      this.ensureAdvertiserProfilesInitialized();
      if (this.advertiserProfiles.size() > 1) {
         int idx = this.advertiserActiveProfile;
         this.advertiserProfiles.remove(idx);
         if (this.advertiserProfiles.isEmpty()) {
            this.advertiserActiveProfile = 0;
         } else {
            this.advertiserActiveProfile = Math.min(idx, this.advertiserProfiles.size() - 1);
         }

         this.advertisementMessage = this.getActiveAdvertisementMessage();
         SuiteConfig.INSTANCE.markDirty();
      }
   }

   public void setTrackedChannel(ChatChannel trackedChannel) {
      this.trackedChannel = trackedChannel;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleShowTrackedChannelHud() {
      this.showTrackedChannelHud = !this.showTrackedChannelHud;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleShowAdvertisementHud() {
      this.showAdvertisementHud = !this.showAdvertisementHud;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleShowStaffHud() {
      this.showStaffHud = !this.showStaffHud;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleTrackStaffChat() {
      this.trackStaffChat = !this.trackStaffChat;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void setAutomatedAdvertisementMessage(String advertisementMessage) {
      this.ensureAdvertiserProfilesInitialized();
      String msg = advertisementMessage == null ? "" : advertisementMessage;
      ChatConfig.AdvertiserProfile p = this.advertiserProfiles.get(this.advertiserActiveProfile);
      p.message = msg;
      this.advertisementMessage = msg;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void setWelcomeMessage(String welcomeMessage) {
      this.welcomeMessage = welcomeMessage == null ? "" : welcomeMessage;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void setPosition(float positionX, float positionY) {
      this.positionX = positionX;
      this.positionY = positionY;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void setScale(float scale) {
      this.scale = scale;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleCompact() {
      this.compact = !this.compact;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleCleanUpCrateMessages() {
      if (this.crateMessageMode == ChatConfig.CrateMessageMode.NORMAL) {
         this.setCrateMessageMode(ChatConfig.CrateMessageMode.UPDATED);
      } else {
         this.setCrateMessageMode(ChatConfig.CrateMessageMode.NORMAL);
      }
   }

   public void cycleCrateMessageMode() {
      ChatConfig.CrateMessageMode[] values = ChatConfig.CrateMessageMode.values();
      this.setCrateMessageMode(values[(this.crateMessageMode.ordinal() + 1) % values.length]);
   }

   public void setCrateMessageMode(ChatConfig.CrateMessageMode mode) {
      this.crateMessageMode = mode == null ? ChatConfig.CrateMessageMode.UPDATED : mode;
      this.cleanUpCrateMessages = this.crateMessageMode == ChatConfig.CrateMessageMode.UPDATED;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleHideInventoryFullVoucherMessages() {
      this.hideInventoryFullVoucherMessages = !this.hideInventoryFullVoucherMessages;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleHideCooldownMessages() {
      this.hideCooldownMessages = !this.hideCooldownMessages;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleHideInventoryCompactMessages() {
      this.hideInventoryCompactMessages = !this.hideInventoryCompactMessages;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleHidePinataBalanceMessages() {
      this.hidePinataBalanceMessages = !this.hidePinataBalanceMessages;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void setAdvertisementReadyAtEpochMs(long advertisementReadyAtEpochMs) {
      this.advertisementReadyAtEpochMs = advertisementReadyAtEpochMs;
      SuiteConfig.INSTANCE.markDirty();
   }

   public static final class AdvertiserProfile {
      public String name = "";
      public String message = "";
   }

   public enum CrateMessageMode {
      NORMAL,
      UPDATED,
      HIDE;
   }
}
