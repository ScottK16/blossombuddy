package org.blossomsuite.core.vote;

import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.config.VoteConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

public final class VoteNotifier {
   private static String activeCountdownServer = "";
   private static long activeCountdownExpectedAtMs = 0L;
   private static long activeCountdownFallbackEndsAtMs = 0L;
   private static String activeOngoingServer = "";
   private static long activeOngoingEndsAtMs = 0L;
   private static final long ONGOING_DISPLAY_MS = 2400L;

   private VoteNotifier() {
   }

   public static void onCountdownStarted(VotePartySnapshot snapshot) {
      if (snapshot != null) {
         long now = System.currentTimeMillis();
         if (!shouldSuppressForCurrentServer(snapshot)) {
            if (shouldNotifyCountdown()) {
               activeCountdownServer = displayName(snapshot);
               Long expected = snapshot.getExpectedTriggerAt();
               activeCountdownExpectedAtMs = expected == null ? 0L : expected;
               Float seconds = snapshot.getCountdownSecondsRemaining();
               long fallbackMs = seconds == null ? 30000L : Math.max(1000L, (long)(seconds * 1000.0F));
               activeCountdownFallbackEndsAtMs = now + fallbackMs;
               playVoteSound();
            }
         }
      }
   }

   public static void onOngoingStarted(VotePartySnapshot snapshot) {
      if (snapshot != null) {
         String name = displayName(snapshot);
         clearCountdown(name);
         if (!shouldSuppressForCurrentServer(snapshot)) {
            if (shouldNotifyOngoing()) {
               activeOngoingServer = name;
               activeOngoingEndsAtMs = System.currentTimeMillis() + 2400L;
               playVoteSound();
            }
         }
      }
   }

   public static void clearCountdown(String serverName) {
      if (serverName != null && !serverName.isBlank()) {
         if (serverName.equalsIgnoreCase(activeCountdownServer)) {
            activeCountdownServer = "";
            activeCountdownExpectedAtMs = 0L;
            activeCountdownFallbackEndsAtMs = 0L;
         }
      }
   }

   public static boolean hasActiveCountdown(long now) {
      if (activeCountdownServer != null && !activeCountdownServer.isBlank()) {
         long endsAt = activeCountdownExpectedAtMs > 0L ? activeCountdownExpectedAtMs : activeCountdownFallbackEndsAtMs;
         return endsAt > now;
      } else {
         return false;
      }
   }

   public static String activeCountdownServer() {
      return activeCountdownServer == null ? "" : activeCountdownServer;
   }

   public static int activeCountdownSeconds(long now) {
      long endsAt = activeCountdownExpectedAtMs > 0L ? activeCountdownExpectedAtMs : activeCountdownFallbackEndsAtMs;
      return endsAt <= now ? 0 : Math.max(0, (int)Math.ceil((endsAt - now) / 1000.0));
   }

   public static boolean hasActiveOngoing(long now) {
      return activeOngoingServer != null && !activeOngoingServer.isBlank() ? activeOngoingEndsAtMs > now : false;
   }

   public static String activeOngoingServer() {
      return activeOngoingServer == null ? "" : activeOngoingServer;
   }

   private static boolean shouldNotifyCountdown() {
      VoteConfig.NotifyWhen mode = SuiteConfig.INSTANCE.VoteConfig.notifyWhen;
      return mode == VoteConfig.NotifyWhen.COUNTDOWN || mode == VoteConfig.NotifyWhen.BOTH;
   }

   private static boolean shouldNotifyOngoing() {
      VoteConfig.NotifyWhen mode = SuiteConfig.INSTANCE.VoteConfig.notifyWhen;
      return mode == VoteConfig.NotifyWhen.ONGOING || mode == VoteConfig.NotifyWhen.BOTH;
   }

   private static boolean shouldSuppressForCurrentServer(VotePartySnapshot snapshot) {
      if (snapshot == null) {
         return false;
      }

      String eventServer = VoteState.normalize(snapshot.getServerKey());
      String currentServer = VoteState.normalize(VoteRuntime.currentServer());
      return !eventServer.isBlank() && eventServer.equalsIgnoreCase(currentServer);
   }

   private static void playVoteSound() {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.player != null && client.world != null) {
         client.world
            .playSound(
               client.player,
               client.player.getX(),
               client.player.getY(),
               client.player.getZ(),
               SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH,
               SoundCategory.PLAYERS,
               0.8F,
               1.15F
            );
      }
   }

   private static String displayName(VotePartySnapshot snapshot) {
      String key = snapshot.getServerKey();
      if (key != null && !key.isBlank()) {
         String fromProfile = VoteState.displayNameFor(key, null);
         if (fromProfile != null && !fromProfile.isBlank()) {
            return fromProfile;
         }
      }

      String display = snapshot.getDisplayName();
      return display != null && !display.isBlank() ? display : "Unknown";
   }
}
