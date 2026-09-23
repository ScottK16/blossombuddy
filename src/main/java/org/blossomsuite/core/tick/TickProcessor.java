package org.blossomsuite.core.tick;

import org.blossomsuite.core.SuiteFeature;
import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.alts.AltResourceState;
import org.blossomsuite.core.chat.ChatModeProbe;
import org.blossomsuite.core.chat.ChatOutput;
import org.blossomsuite.core.chat.PublicChatSendState;
import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.JobsConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.cooldowns.CooldownInput;
import org.blossomsuite.core.cooldowns.CooldownJingle;
import org.blossomsuite.core.cooldowns.CooldownNotifier;
import org.blossomsuite.core.cooldowns.CooldownRules;
import org.blossomsuite.core.cooldowns.CooldownState;
import org.blossomsuite.core.hud.HudEditState;
import org.blossomsuite.core.hud.JobsHud;
import org.blossomsuite.core.jobs.JobsActionBarMode;
import org.blossomsuite.core.jobs.JobsModule;
import org.blossomsuite.core.jobs.JobsRapidOverlay;
import org.blossomsuite.core.jobs.JobsTracker;
import org.blossomsuite.core.keybinds.KeybindManager;
import org.blossomsuite.core.qol.autodrop.AutoDropper;
import org.blossomsuite.core.qol.autofly.AutoFlyController;
import org.blossomsuite.core.qol.holepuncher.HolePuncher;
import org.blossomsuite.core.qol.inventorysort.InventorySorter;
import org.blossomsuite.core.qol.mining.MiningResumeGuard;
import org.blossomsuite.core.remote.RemoteConfigClient;
import org.blossomsuite.core.services.VoteStateService;
import org.blossomsuite.core.state.SuiteState;
import org.blossomsuite.core.storage.SegmentStore;
import org.blossomsuite.core.util.WorldGate;
import org.blossomsuite.core.vote.VotePartySnapshot;
import org.blossomsuite.core.vote.VoteRuntime;
import org.blossomsuite.core.vote.VoteState;
import net.minecraft.client.MinecraftClient;

public final class TickProcessor {
   private static long lastVoteExpiryCheckMs = 0L;
   private static long lastPauseReminderBucket = 0L;
   private static boolean wasActiveLastTick = false;

   public static void process(MinecraftClient client) {
      if (client.player != null) {
         long now = System.currentTimeMillis();
         JobsTracker tracker = JobsModule.tracker();
         boolean active = WorldGate.isActive();
         if (HudEditState.editMode
            && client.currentScreen != null
            && !TickRuntime.isHudEditScreen(client.currentScreen)
            && !TickRuntime.hasHudEditOpenRequest()) {
            HudEditState.editMode = false;
            JobsHud.dragging = false;
            ConfigIO.saveIfDirty();
         }

         KeybindManager.tick(client);
         handleEditScreenRequests(client);
         if (!SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
            onInactiveWorld(tracker);
         } else {
            if (feature(SuiteFeature.AUTO_FLY)) {
               AutoFlyController.tick(client, now);
            }

            if (!active) {
               onInactiveWorld(tracker);
            } else {
               wasActiveLastTick = true;
               if (now - lastVoteExpiryCheckMs >= 1000L) {
                  lastVoteExpiryCheckMs = now;
                  if (feature(SuiteFeature.VOTE)) {
                     tryExpireVoteBossbarState(now);
                  }
               }

               if (feature(SuiteFeature.COOLDOWNS)) {
                  CooldownInput.tick(client);
               }

               if (feature(SuiteFeature.JOBS) && tracker != null) {
                  tracker.tick();
               }

               if (feature(SuiteFeature.JOBS)) {
                  handlePauseReminder(tracker, now);
               }

               if (feature(SuiteFeature.COOLDOWNS)) {
                  CooldownNotifier.tick(client);
               }

               if (feature(SuiteFeature.CHAT_TOOLS)) {
                  ChatModeProbe.tick();
                  PublicChatSendState.tick(client);
               }

               if (feature(SuiteFeature.ALT_RESOURCES)) {
                  AltResourceState.tickInventoryScan(client, WorldGate.Server, now);
               }

               JobsConfig jobsCfg = SuiteConfig.INSTANCE.JobsConfig;
               if (jobsCfg != null
                  && jobsCfg.capture
                  && (
                     jobsCfg.actionBarMode == JobsActionBarMode.RUNNING_TOTAL
                        || jobsCfg.actionBarMode == JobsActionBarMode.SESSION_TOTAL
                        || jobsCfg.actionBarMode == JobsActionBarMode.SEGMENT_TOTAL
                  )
                  && JobsRapidOverlay.isActive(now)) {
                  JobsRapidOverlay.keepAlive(client);
               }

               if (feature(SuiteFeature.JOBS) && SuiteConfig.INSTANCE.JobsConfig.autoSegment && JobsModule.tracker() != null) {
                  JobsConfig segCfg = SuiteConfig.INSTANCE.JobsConfig;
                  JobsTracker.SegmentSnapshot snap = JobsModule.tracker()
                     .tryAutoSegment(now, segCfg.autoSegmentMode, segCfg.autoSegmentInSecs, segCfg.autoSegmentMoneyThreshold, segCfg.autoSegmentExpThreshold);
                  if (snap != null) {
                     try {
                        SegmentStore.saveSegment(snap);
                     } catch (Exception e) {
                        TickRuntime.warn("SegmentStore saveSegment failed", e);
                     }

                     ConfigIO.saveIfDirty();
                     ChatOutput.info("Segment auto-saved & reset");
                  }
               }

               if (TickRuntime.consumeSettingsOpenRequest()) {
                  TickRuntime.openSettings(MinecraftClient.getInstance());
               }

               if (feature(SuiteFeature.REMOTE_CONFIG)) {
                  RemoteConfigClient.checkIfUpdateAvailable(client, now);
               }

               if (feature(SuiteFeature.COOLDOWNS)) {
                  CooldownJingle.tick(client, now);
                  CooldownState.pruneEnded(now);
                  CooldownRules.tick(client, now);
               }

               if (feature(SuiteFeature.HOLE_PUNCHER)) {
                  HolePuncher.tick(client);
               }

               if (feature(SuiteFeature.AUTO_DROPPER)) {
                  AutoDropper.tick(client, now);
               }

               if (feature(SuiteFeature.MINING)) {
                  MiningResumeGuard.tick(client);
               }

               if (feature(SuiteFeature.INVENTORY_SORT)) {
                  InventorySorter.tick(client);
               }
            }
         }
      }
   }

   private static boolean feature(SuiteFeature feature) {
      return SuiteRuntime.isEnabled(feature);
   }

   private static void tryExpireVoteBossbarState(long now) {
      if (VoteRuntime.hasConfirmedServer()) {
         String server = VoteRuntime.confirmedServer();
         VotePartySnapshot snapshot = VoteState.get(server);
         if (snapshot != null) {
            VoteStateService svc = SuiteState.INSTANCE.voteStateService;
            if (svc != null) {
               svc.expireBossbarStateIfStaleAndSyncIfChanged(server, snapshot, now);
            }
         }
      }
   }

   private static void handlePauseReminder(JobsTracker tracker, long now) {
      if (tracker != null && tracker.paused && !tracker.isAutoPausedForInactiveWorld()) {
         JobsConfig cfg = SuiteConfig.INSTANCE.JobsConfig;
         if (cfg != null && cfg.capture) {
            boolean remind = cfg.pauseReminder;
            boolean autoUnpause = cfg.pauseAutoUnpause;
            if (!remind && !autoUnpause) {
               lastPauseReminderBucket = 0L;
            } else {
               long thresholdMs = Math.max(1L, cfg.pauseReminderThresholdSecs) * 1000L;
               long pausedMs = tracker.currentPauseDurationMs(now);
               double pausedMoney = tracker.currentPausedMoney();
               double moneyThreshold = Math.max(0.0, cfg.pauseAutoUnpauseMoneyThreshold);
               if (autoUnpause && moneyThreshold > 0.0 && pausedMoney >= moneyThreshold) {
                  tracker.resume();
                  lastPauseReminderBucket = 0L;
                  ChatOutput.info("Jobs tracking auto-resumed after $" + formatMoney(pausedMoney) + " was earned while paused.");
               } else if (!remind) {
                  lastPauseReminderBucket = 0L;
               } else if (pausedMs >= thresholdMs) {
                  long bucket = pausedMs / thresholdMs;
                  if (bucket > lastPauseReminderBucket) {
                     lastPauseReminderBucket = bucket;
                     ChatOutput.info("Jobs tracking is still paused (" + formatPauseDuration(pausedMs) + ").");
                  }
               }
            }
         } else {
            lastPauseReminderBucket = 0L;
         }
      } else {
         lastPauseReminderBucket = 0L;
      }
   }

   private static String formatPauseDuration(long ms) {
      long totalSeconds = Math.max(0L, ms / 1000L);
      long minutes = totalSeconds / 60L;
      long seconds = totalSeconds % 60L;
      if (minutes <= 0L) {
         return seconds + "s";
      } else {
         return seconds == 0L ? minutes + "m" : minutes + "m " + seconds + "s";
      }
   }

   private static String formatMoney(double amount) {
      if (amount >= 1.0E9) {
         return String.format("%.2fb", amount / 1.0E9);
      } else if (amount >= 1000000.0) {
         return String.format("%.2fm", amount / 1000000.0);
      } else {
         return amount >= 1000.0 ? String.format("%.2fk", amount / 1000.0) : String.format("%.2f", amount);
      }
   }

   private static void onInactiveWorld(JobsTracker tracker) {
      if (wasActiveLastTick && tracker != null) {
         tracker.pauseForInactiveWorld();
      }

      wasActiveLastTick = false;
   }

   private static void handleEditScreenRequests(MinecraftClient client) {
      if (TickRuntime.consumeHudEditOpenRequest()) {
         if (client.currentScreen == null) {
            TickRuntime.openHudEdit(client);
         } else {
            TickRuntime.requeueHudEditOpenRequest();
         }
      }

      if (TickRuntime.consumeHudEditCloseRequest()) {
         if (TickRuntime.isHudEditScreen(client.currentScreen)) {
            client.setScreen(null);
         }

         ConfigIO.saveIfDirty();
      }
   }
}
