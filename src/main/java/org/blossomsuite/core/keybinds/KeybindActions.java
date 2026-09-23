package org.blossomsuite.core.keybinds;

import org.blossomsuite.core.SuiteFeature;
import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.chat.AdvertisementState;
import org.blossomsuite.core.chat.ChatOutput;
import org.blossomsuite.core.chat.PublicChatSendState;
import org.blossomsuite.core.chat.StaffChatState;
import org.blossomsuite.core.config.ChatConfig;
import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.QolConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.hud.HudEditState;
import org.blossomsuite.core.jobs.JobsModule;
import org.blossomsuite.core.jobs.JobsTracker;
import org.blossomsuite.core.qol.autodrop.AutoDropper;
import org.blossomsuite.core.qol.holepuncher.HolePuncher;
import org.blossomsuite.core.qol.inventorysort.InventorySorter;
import org.blossomsuite.core.storage.SegmentStore;
import org.blossomsuite.core.util.WorldGate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Direction;

public final class KeybindActions {
   private KeybindActions() {
   }

   public static boolean isAvailable(KeybindActions.Action action) {
      SuiteFeature feature = featureFor(action);
      return feature == null || SuiteRuntime.isEnabled(feature);
   }

   public static void run(KeybindActions.Action a, MinecraftClient client) {
      if (client != null) {
         if (isAvailable(a)) {
            switch (a) {
               case OPEN_SETTINGS: {
                  KeybindRuntime.openSettings(MinecraftClient.getInstance());
                  break;
               }
               case TOGGLE_EDIT_MODE: {
                  HudEditState.editMode = !HudEditState.editMode;
                  if (HudEditState.editMode) {
                     KeybindRuntime.openHudEditor(client);
                  } else {
                     client.setScreen(null);
                     ConfigIO.saveIfDirty();
                  }

                  ChatOutput.info("HUD Edit Mode " + (HudEditState.editMode ? "ON." : "OFF."));
                  break;
               }
               case PAUSE_RESUME: {
                  JobsTracker tracker = JobsModule.tracker();
                  if (tracker == null) {
                     return;
                  }

                  tracker.togglePause();
                  ChatOutput.info("Job Capture " + (tracker.paused ? "paused." : "resumed."));
                  break;
               }
               case RESET_SESSION: {
                  JobsTracker tracker = JobsModule.tracker();
                  if (tracker == null) {
                     return;
                  }

                  tracker.resetSession();
                  SuiteConfig.INSTANCE.JobsConfig.clearSessionRolloverData();
                  ChatOutput.info("Session and segment reset.");
                  break;
               }
               case RESET_SEGMENT: {
                  JobsTracker tracker = JobsModule.tracker();
                  if (tracker == null) {
                     return;
                  }

                  JobsTracker.SegmentSnapshot snap = tracker.resetSegmentAndSnapshot();
                  if (snap != null) {
                     try {
                        SegmentStore.saveSegment(snap);
                     } catch (Exception e) {
                        KeybindRuntime.warn("SegmentStore saveSegment failed", e);
                     }
                  }

                  ChatOutput.info("Segment reset. Snapshot saved.");
                  break;
               }
               case TOGGLE_AUTO_SWAPPER: {
                  QolConfig q = SuiteConfig.INSTANCE.QolConfig;
                  if (q == null) {
                     return;
                  }

                  q.toggleAutoSwapperEnabled();
                  ConfigIO.saveIfDirty();
                  ChatOutput.info("AutoSwapper " + (q.autoSwapperEnabled ? "ON." : "OFF."));
                  break;
               }
               case TOGGLE_AUTO_DROPPER: {
                  QolConfig q = SuiteConfig.INSTANCE.QolConfig;
                  if (q == null) {
                     return;
                  }

                  q.autoDropperEnabled = !q.autoDropperEnabled;
                  SuiteConfig.INSTANCE.markDirty();
                  ConfigIO.saveIfDirty();
                  ChatOutput.info("AutoDropper " + (q.autoDropperEnabled ? "ON." : "OFF."));
                  break;
               }
               case TOGGLE_RENTALS_PAUSE: {
                  KeybindRuntime.toggleRentalsPause();
                  break;
               }
               case CLEAR_EXPIRED_RENTALS: {
                  KeybindRuntime.clearExpiredRentals();
                  break;
               }
               case RUN_AUTO_DROPPER: {
                  AutoDropper.triggerHotkey(client);
                  break;
               }
               case RUN_CONDENSE: {
                  if (client.player == null) {
                     return;
                  }

                  if (client.getNetworkHandler() == null) {
                     return;
                  }

                  if (!WorldGate.isActive()) {
                     return;
                  }

                  client.getNetworkHandler().sendChatCommand("condense");
                  ChatOutput.info("Condense command sent.");
                  break;
               }
               case SORT_INVENTORY: {
                  InventorySorter.sortPlayerInventory(client);
                  break;
               }
               case SORT_CONTAINER: {
                  InventorySorter.sortOpenContainer(client);
                  break;
               }
               case SORT_ALL: {
                  InventorySorter.sortPlayerInventoryAndOpenContainer(client);
                  break;
               }
               case DEPOSIT_ALL_TO_CONTAINER: {
                  InventorySorter.depositAllToOpenContainer(client);
                  break;
               }
               case DEPOSIT_MATCHING_TO_CONTAINER: {
                  InventorySorter.depositMatchingToOpenContainer(client);
                  break;
               }
               case WITHDRAW_ALL_FROM_CONTAINER: {
                  InventorySorter.withdrawAllFromOpenContainer(client);
                  break;
               }
               case WITHDRAW_MATCHING_FROM_CONTAINER: {
                  InventorySorter.withdrawMatchingFromOpenContainer(client);
                  break;
               }
               case TOGGLE_MARRY_CHAT: {
                  if (client.player == null) {
                     return;
                  }

                  if (client.getNetworkHandler() == null) {
                     return;
                  }

                  if (!WorldGate.isActive()) {
                     return;
                  }

                  client.getNetworkHandler().sendChatCommand("marry chattoggle");
                  ChatOutput.info("Marry chat toggle command sent.");
                  break;
               }
               case TOGGLE_HOLE_PUNCHER_MODE: {
                  QolConfig q = SuiteConfig.INSTANCE.QolConfig;
                  if (q == null) {
                     return;
                  }

                  if (!q.holePuncherEnabled) {
                     return;
                  }

                  if (client.player == null) {
                     return;
                  }

                  HolePuncher.onKeyPressed(client);
                  break;
               }
               case TOGGLE_HOLE_PUNCHER_ENABLED: {
                  QolConfig q = SuiteConfig.INSTANCE.QolConfig;
                  if (q == null) {
                     return;
                  }

                  q.toggleHolePuncherEnabled();
                  if (!q.holePuncherEnabled) {
                     HolePuncher.stopNow(client);
                  }

                  ConfigIO.saveIfDirty();
                  ChatOutput.info("HolePuncher " + (q.holePuncherEnabled ? "enabled." : "disabled."));
                  break;
               }
               case TOGGLE_HOLE_PUNCHER_MARKERS: {
                  QolConfig q = SuiteConfig.INSTANCE.QolConfig;
                  if (q == null) {
                     return;
                  }

                  q.toggleHolePuncherMarkersEnabled();
                  ConfigIO.saveIfDirty();
                  ChatOutput.info("HolePuncher markers " + (q.holePuncherMarkersEnabled ? "ON." : "OFF."));
                  break;
               }
               case SET_MINING_TRACK: {
                  QolConfig q = SuiteConfig.INSTANCE.QolConfig;
                  if (q == null) {
                     return;
                  }

                  if (client.player == null) {
                     return;
                  }

                  Direction d = client.player.getHorizontalFacing();
                  q.miningTrackDir = d.asString();
                  if (d != Direction.EAST && d != Direction.WEST) {
                     q.miningTrackCoord = client.player.getBlockPos().getX();
                  } else {
                     q.miningTrackCoord = client.player.getBlockPos().getZ();
                  }

                  SuiteConfig.INSTANCE.markDirty();
                  ConfigIO.saveIfDirty();
                  String dir = d.asString().toUpperCase();
                  boolean alongZ = d == Direction.NORTH || d == Direction.SOUTH;
                  String axis = alongZ ? "X" : "Z";
                  ChatOutput.info("Mining Track set: " + (alongZ ? "N/S" : "E/W") + " (" + axis + "=" + q.miningTrackCoord + ").");
                  break;
               }
               case CLEAR_MINING_TRACK: {
                  QolConfig q = SuiteConfig.INSTANCE.QolConfig;
                  if (q == null) {
                     return;
                  }

                  q.miningTrackDir = "";
                  q.miningTrackCoord = 0;
                  SuiteConfig.INSTANCE.markDirty();
                  ConfigIO.saveIfDirty();
                  ChatOutput.info("Mining Track cleared.");
                  break;
               }
               case TOGGLE_MINING_TRACK_INDICATOR: {
                  QolConfig q = SuiteConfig.INSTANCE.QolConfig;
                  if (q == null) {
                     return;
                  }

                  q.miningTrackIndicator = !q.miningTrackIndicator;
                  SuiteConfig.INSTANCE.markDirty();
                  ConfigIO.saveIfDirty();
                  ChatOutput.info("Mining Track Overlay " + (q.miningTrackIndicator ? "ON." : "OFF."));
                  break;
               }
               case TOGGLE_TOOL_LOCK: {
                  QolConfig q = SuiteConfig.INSTANCE.QolConfig;
                  if (q == null) {
                     return;
                  }

                  q.toggleToolLockEnabled();
                  ConfigIO.saveIfDirty();
                  ChatOutput.info("Tool Lock " + (q.toolLockEnabled ? "ON." : "OFF."));
                  break;
               }
               case TOGGLE_TOOL_LOCK_SLOT: {
                  QolConfig q = SuiteConfig.INSTANCE.QolConfig;
                  if (q == null) {
                     return;
                  }

                  if (client.player == null) {
                     return;
                  }

                  int sel = client.player.getInventory().getSelectedSlot();
                  if (sel < 0 || sel > 8) {
                     return;
                  }

                  boolean wasEnabled = q.toolLockEnabled;
                  q.toggleToolLockSlot(sel);
                  ConfigIO.saveIfDirty();
                  boolean locked = (q.toolLockLockedSlotsMask & 1 << sel) != 0;
                  String msg = "Tool Lock slot " + (sel + 1) + ": " + (locked ? "LOCKED" : "UNLOCKED");
                  if (locked && !wasEnabled && q.toolLockEnabled) {
                     msg = msg + ". Tool Lock ON";
                  }

                  ChatOutput.info(msg + ".");
                  break;
               }
               case USE_ADVERTISER: {
                  if (client.player == null) {
                     return;
                  }

                  if (client.getNetworkHandler() == null) {
                     return;
                  }

                  if (!WorldGate.isActive()) {
                     return;
                  }

                  ChatConfig chatCfg = SuiteConfig.INSTANCE.ChatConfig;
                  String msg = chatCfg.getActiveAdvertisementMessage();
                  if (msg == null || msg.isBlank()) {
                     return;
                  }

                  if (!AdvertisementState.isReady()) {
                     return;
                  }

                  PublicChatSendState.requestSendPublic(msg, true);
                  break;
               }
               case CYCLE_ADVERTISER_PROFILE: {
                  ChatConfig chatCfg = SuiteConfig.INSTANCE.ChatConfig;
                  chatCfg.cycleAdvertiserProfile();
                  ConfigIO.saveIfDirty();
                  ChatOutput.info("Advertiser profile: " + chatCfg.getActiveAdvertiserProfileName());
                  break;
               }
               case SEND_WELCOME_MESSAGE: {
                  if (!StaffChatState.isStaffTrackingActive()) {
                     return;
                  }

                  if (client.player == null) {
                     return;
                  }

                  if (client.getNetworkHandler() == null) {
                     return;
                  }

                  if (!WorldGate.isActive()) {
                     return;
                  }

                  String msg = SuiteConfig.INSTANCE.ChatConfig.welcomeMessage;
                  if (msg == null || msg.isBlank()) {
                     return;
                  }

                  PublicChatSendState.requestSendPublic(msg, false);
                  break;
               }
               case TOGGLE_STAFF_CHAT: {
                  if (!StaffChatState.isStaffTrackingActive()) {
                     return;
                  }

                  if (client.player == null) {
                     return;
                  }

                  if (client.getNetworkHandler() == null) {
                     return;
                  }

                  if (!WorldGate.isActive()) {
                     return;
                  }

                  client.getNetworkHandler().sendChatCommand("sch toggle");
                  ChatOutput.info("Staff chat toggle command sent.");
               }
            }
         }
      }
   }

   private static SuiteFeature featureFor(KeybindActions.Action action) {
      return switch (action) {
         case OPEN_SETTINGS -> null;
         case TOGGLE_EDIT_MODE -> SuiteFeature.VISUALS;
         case PAUSE_RESUME, RESET_SESSION, RESET_SEGMENT -> SuiteFeature.JOBS;
         case TOGGLE_AUTO_SWAPPER -> SuiteFeature.AUTO_SWAPPER;
         case TOGGLE_AUTO_DROPPER, RUN_AUTO_DROPPER -> SuiteFeature.AUTO_DROPPER;
         case TOGGLE_RENTALS_PAUSE, CLEAR_EXPIRED_RENTALS -> SuiteFeature.RENTALS;
         case RUN_CONDENSE -> SuiteFeature.QOL;
         case SORT_INVENTORY, SORT_CONTAINER, SORT_ALL, DEPOSIT_ALL_TO_CONTAINER, DEPOSIT_MATCHING_TO_CONTAINER, WITHDRAW_ALL_FROM_CONTAINER, WITHDRAW_MATCHING_FROM_CONTAINER -> SuiteFeature.INVENTORY_SORT;
         case TOGGLE_MARRY_CHAT, USE_ADVERTISER, CYCLE_ADVERTISER_PROFILE, SEND_WELCOME_MESSAGE, TOGGLE_STAFF_CHAT -> SuiteFeature.CHAT_TOOLS;
         case TOGGLE_HOLE_PUNCHER_MODE, TOGGLE_HOLE_PUNCHER_ENABLED, TOGGLE_HOLE_PUNCHER_MARKERS -> SuiteFeature.HOLE_PUNCHER;
         case SET_MINING_TRACK, CLEAR_MINING_TRACK, TOGGLE_MINING_TRACK_INDICATOR -> SuiteFeature.MINING;
         case TOGGLE_TOOL_LOCK, TOGGLE_TOOL_LOCK_SLOT -> SuiteFeature.TOOL_LOCK;
      };
   }

   public enum Action {
      OPEN_SETTINGS,
      TOGGLE_EDIT_MODE,
      PAUSE_RESUME,
      RESET_SESSION,
      RESET_SEGMENT,
      TOGGLE_AUTO_SWAPPER,
      TOGGLE_AUTO_DROPPER,
      TOGGLE_RENTALS_PAUSE,
      CLEAR_EXPIRED_RENTALS,
      RUN_AUTO_DROPPER,
      RUN_CONDENSE,
      SORT_INVENTORY,
      SORT_CONTAINER,
      SORT_ALL,
      DEPOSIT_ALL_TO_CONTAINER,
      DEPOSIT_MATCHING_TO_CONTAINER,
      WITHDRAW_ALL_FROM_CONTAINER,
      WITHDRAW_MATCHING_FROM_CONTAINER,
      TOGGLE_MARRY_CHAT,
      TOGGLE_HOLE_PUNCHER_MODE,
      TOGGLE_HOLE_PUNCHER_ENABLED,
      TOGGLE_HOLE_PUNCHER_MARKERS,
      SET_MINING_TRACK,
      CLEAR_MINING_TRACK,
      TOGGLE_MINING_TRACK_INDICATOR,
      TOGGLE_TOOL_LOCK,
      TOGGLE_TOOL_LOCK_SLOT,
      USE_ADVERTISER,
      CYCLE_ADVERTISER_PROFILE,
      SEND_WELCOME_MESSAGE,
      TOGGLE_STAFF_CHAT;
   }
}
