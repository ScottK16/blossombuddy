package org.blossomsuite.core.chat;

import org.blossomsuite.core.config.SuiteConfig;

public final class AdvertisementState {
   public static long COOLDOWN_MS = 1800000L;

   private AdvertisementState() {
   }

   public static boolean isReady() {
      return System.currentTimeMillis() >= SuiteConfig.INSTANCE.ChatConfig.advertisementReadyAtEpochMs;
   }

   public static long remainingMs() {
      return Math.max(0L, SuiteConfig.INSTANCE.ChatConfig.advertisementReadyAtEpochMs - System.currentTimeMillis());
   }

   public static void startCooldown() {
      SuiteConfig.INSTANCE.ChatConfig.advertisementReadyAtEpochMs = System.currentTimeMillis() + COOLDOWN_MS;
      SuiteConfig.INSTANCE.markDirty();
   }
}
