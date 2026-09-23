package org.blossomsuite.core.keybinds;

import org.blossomsuite.core.chat.StaffChatState;
import org.blossomsuite.core.config.KeybindsConfig;
import org.blossomsuite.core.config.SuiteConfig;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.InputUtil.Key;
import net.minecraft.client.util.InputUtil.Type;

public final class KeybindManager {
   private static final Map<KeybindActions.Action, Boolean> prevDown = new HashMap<>();

   private KeybindManager() {
   }

   public static void tick(MinecraftClient client) {
      if (client != null) {
         if (client.currentScreen != null) {
            if (client.currentScreen instanceof HandledScreen) {
               tickHandledScreenSortKeys(client);
            }

            resetNonHandledScreenActions();
         } else {
            long window = KeybindUtil.windowHandle();
            if (window != 0L) {
               KeybindsConfig cfg = SuiteConfig.INSTANCE.KeybindsConfig;
               fireIfPressed(client, KeybindActions.Action.OPEN_SETTINGS, cfg.openSettings, window, true);
               if (!SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
                  resetFeatureActions();
               } else {
                  fireIfPressed(client, KeybindActions.Action.TOGGLE_EDIT_MODE, cfg.toggleEditMode, window, true);
                  fireIfPressed(client, KeybindActions.Action.PAUSE_RESUME, cfg.pauseResume, window, true);
                  fireIfPressed(client, KeybindActions.Action.RESET_SESSION, cfg.resetSession, window, true);
                  fireIfPressed(client, KeybindActions.Action.RESET_SEGMENT, cfg.resetSegment, window, true);
                  fireIfPressed(client, KeybindActions.Action.TOGGLE_AUTO_SWAPPER, cfg.toggleAutoSwapper, window, true);
                  fireIfPressed(client, KeybindActions.Action.TOGGLE_AUTO_DROPPER, cfg.toggleAutoDropper, window, true);
                  fireIfPressed(client, KeybindActions.Action.TOGGLE_RENTALS_PAUSE, cfg.toggleRentalsPause, window, true);
                  fireIfPressed(client, KeybindActions.Action.CLEAR_EXPIRED_RENTALS, cfg.clearExpiredRentals, window, true);
                  fireIfPressed(client, KeybindActions.Action.RUN_AUTO_DROPPER, cfg.runAutoDropper, window, true);
                  fireIfPressed(client, KeybindActions.Action.RUN_CONDENSE, cfg.runCondense, window, true);
                  fireIfPressed(client, KeybindActions.Action.SORT_INVENTORY, cfg.sortInventory, window, true);
                  fireIfPressed(client, KeybindActions.Action.SORT_CONTAINER, cfg.sortContainer, window, true);
                  fireIfPressed(client, KeybindActions.Action.SORT_ALL, cfg.sortAll, window, true);
                  fireIfPressed(client, KeybindActions.Action.DEPOSIT_ALL_TO_CONTAINER, cfg.depositAllToContainer, window, true);
                  fireIfPressed(client, KeybindActions.Action.DEPOSIT_MATCHING_TO_CONTAINER, cfg.depositMatchingToContainer, window, true);
                  fireIfPressed(client, KeybindActions.Action.WITHDRAW_ALL_FROM_CONTAINER, cfg.withdrawAllFromContainer, window, true);
                  fireIfPressed(client, KeybindActions.Action.WITHDRAW_MATCHING_FROM_CONTAINER, cfg.withdrawMatchingFromContainer, window, true);
                  fireIfPressed(client, KeybindActions.Action.TOGGLE_MARRY_CHAT, cfg.toggleMarryChat, window, true);
                  fireIfPressed(client, KeybindActions.Action.TOGGLE_HOLE_PUNCHER_MODE, cfg.toggleHolePuncherMode, window, true);
                  fireIfPressed(client, KeybindActions.Action.TOGGLE_HOLE_PUNCHER_ENABLED, cfg.toggleHolePuncherEnabled, window, true);
                  fireIfPressed(client, KeybindActions.Action.TOGGLE_HOLE_PUNCHER_MARKERS, cfg.toggleHolePuncherMarkers, window, true);
                  fireIfPressed(client, KeybindActions.Action.SET_MINING_TRACK, cfg.setMiningTrack, window, true);
                  fireIfPressed(client, KeybindActions.Action.CLEAR_MINING_TRACK, cfg.clearMiningTrack, window, true);
                  fireIfPressed(client, KeybindActions.Action.TOGGLE_MINING_TRACK_INDICATOR, cfg.toggleMiningTrackIndicator, window, true);
                  fireIfPressed(client, KeybindActions.Action.TOGGLE_TOOL_LOCK, cfg.toggleToolLock, window, true);
                  fireIfPressed(client, KeybindActions.Action.TOGGLE_TOOL_LOCK_SLOT, cfg.toggleToolLockSlot, window, true);
                  fireIfPressed(client, KeybindActions.Action.USE_ADVERTISER, cfg.useAdvertiser, window, true);
                  fireIfPressed(client, KeybindActions.Action.CYCLE_ADVERTISER_PROFILE, cfg.cycleAdvertiserProfile, window, true);
                  fireIfPressed(client, KeybindActions.Action.SEND_WELCOME_MESSAGE, cfg.sendWelcomeMessage, window, true);
                  if (StaffChatState.isStaffTrackingActive()) {
                     fireIfPressed(client, KeybindActions.Action.TOGGLE_STAFF_CHAT, cfg.toggleStaffChat, window, true);
                  } else {
                     prevDown.put(KeybindActions.Action.TOGGLE_STAFF_CHAT, false);
                  }
               }
            }
         }
      }
   }

   public static boolean shouldBlockVanilla(long windowHandle, int keyCode) {
      return shouldBlockVanillaInput(windowHandle, Type.KEYSYM, keyCode);
   }

   public static boolean shouldBlockVanillaMouse(long windowHandle, int button) {
      return shouldBlockVanillaInput(windowHandle, Type.MOUSE, button);
   }

   private static boolean shouldBlockVanillaInput(long windowHandle, Type type, int code) {
      KeybindsConfig cfg = SuiteConfig.INSTANCE.KeybindsConfig;
      if (cfg == null) {
         return false;
      } else if (matchesBlockingChord(cfg.openSettings, windowHandle, type, code)) {
         return true;
      } else if (!SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
         return false;
      } else if (matchesBlockingChord(cfg.toggleEditMode, windowHandle, type, code)) {
         return true;
      } else if (matchesBlockingChord(cfg.pauseResume, windowHandle, type, code)) {
         return true;
      } else if (matchesBlockingChord(cfg.resetSession, windowHandle, type, code)) {
         return true;
      } else if (matchesBlockingChord(cfg.resetSegment, windowHandle, type, code)) {
         return true;
      } else if (matchesBlockingChord(cfg.toggleAutoSwapper, windowHandle, type, code)) {
         return true;
      } else if (matchesBlockingChord(cfg.toggleAutoDropper, windowHandle, type, code)) {
         return true;
      } else if (matchesBlockingChord(cfg.toggleRentalsPause, windowHandle, type, code)) {
         return true;
      } else if (matchesBlockingChord(cfg.clearExpiredRentals, windowHandle, type, code)) {
         return true;
      } else if (matchesBlockingChord(cfg.runAutoDropper, windowHandle, type, code)) {
         return true;
      } else if (matchesBlockingChord(cfg.runCondense, windowHandle, type, code)) {
         return true;
      } else if (matchesBlockingChord(cfg.sortInventory, windowHandle, type, code)) {
         return true;
      } else if (matchesBlockingChord(cfg.sortContainer, windowHandle, type, code)) {
         return true;
      } else if (matchesBlockingChord(cfg.sortAll, windowHandle, type, code)) {
         return true;
      } else if (matchesBlockingChord(cfg.depositAllToContainer, windowHandle, type, code)) {
         return true;
      } else if (matchesBlockingChord(cfg.depositMatchingToContainer, windowHandle, type, code)) {
         return true;
      } else if (matchesBlockingChord(cfg.withdrawAllFromContainer, windowHandle, type, code)) {
         return true;
      } else if (matchesBlockingChord(cfg.withdrawMatchingFromContainer, windowHandle, type, code)) {
         return true;
      } else if (matchesBlockingChord(cfg.toggleMarryChat, windowHandle, type, code)) {
         return true;
      } else if (matchesBlockingChord(cfg.toggleHolePuncherMode, windowHandle, type, code)) {
         return true;
      } else if (matchesBlockingChord(cfg.toggleHolePuncherEnabled, windowHandle, type, code)) {
         return true;
      } else if (matchesBlockingChord(cfg.toggleHolePuncherMarkers, windowHandle, type, code)) {
         return true;
      } else if (matchesBlockingChord(cfg.setMiningTrack, windowHandle, type, code)) {
         return true;
      } else if (matchesBlockingChord(cfg.clearMiningTrack, windowHandle, type, code)) {
         return true;
      } else if (matchesBlockingChord(cfg.toggleMiningTrackIndicator, windowHandle, type, code)) {
         return true;
      } else if (matchesBlockingChord(cfg.toggleToolLock, windowHandle, type, code)) {
         return true;
      } else if (matchesBlockingChord(cfg.toggleToolLockSlot, windowHandle, type, code)) {
         return true;
      } else if (matchesBlockingChord(cfg.useAdvertiser, windowHandle, type, code)) {
         return true;
      } else if (matchesBlockingChord(cfg.cycleAdvertiserProfile, windowHandle, type, code)) {
         return true;
      } else {
         return matchesBlockingChord(cfg.sendWelcomeMessage, windowHandle, type, code)
            ? true
            : StaffChatState.isStaffTrackingActive() && matchesBlockingChord(cfg.toggleStaffChat, windowHandle, type, code);
      }
   }

   private static void resetFeatureActions() {
      for (KeybindActions.Action action : KeybindActions.Action.values()) {
         if (action != KeybindActions.Action.OPEN_SETTINGS) {
            prevDown.put(action, false);
         }
      }
   }

   private static void resetNonHandledScreenActions() {
      for (KeybindActions.Action action : KeybindActions.Action.values()) {
         if (action != KeybindActions.Action.SORT_INVENTORY
            && action != KeybindActions.Action.SORT_CONTAINER
            && action != KeybindActions.Action.SORT_ALL
            && action != KeybindActions.Action.DEPOSIT_ALL_TO_CONTAINER
            && action != KeybindActions.Action.DEPOSIT_MATCHING_TO_CONTAINER
            && action != KeybindActions.Action.WITHDRAW_ALL_FROM_CONTAINER
            && action != KeybindActions.Action.WITHDRAW_MATCHING_FROM_CONTAINER) {
            prevDown.put(action, false);
         }
      }
   }

   private static void tickHandledScreenSortKeys(MinecraftClient client) {
      long window = KeybindUtil.windowHandle();
      if (window != 0L) {
         KeybindsConfig cfg = SuiteConfig.INSTANCE.KeybindsConfig;
         if (cfg != null) {
            if (!SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
               prevDown.put(KeybindActions.Action.SORT_INVENTORY, false);
               prevDown.put(KeybindActions.Action.SORT_CONTAINER, false);
               prevDown.put(KeybindActions.Action.SORT_ALL, false);
               prevDown.put(KeybindActions.Action.DEPOSIT_ALL_TO_CONTAINER, false);
               prevDown.put(KeybindActions.Action.DEPOSIT_MATCHING_TO_CONTAINER, false);
               prevDown.put(KeybindActions.Action.WITHDRAW_ALL_FROM_CONTAINER, false);
               prevDown.put(KeybindActions.Action.WITHDRAW_MATCHING_FROM_CONTAINER, false);
            } else {
               fireIfPressed(client, KeybindActions.Action.SORT_INVENTORY, cfg.sortInventory, window, true);
               fireIfPressed(client, KeybindActions.Action.SORT_CONTAINER, cfg.sortContainer, window, true);
               fireIfPressed(client, KeybindActions.Action.SORT_ALL, cfg.sortAll, window, true);
               fireIfPressed(client, KeybindActions.Action.DEPOSIT_ALL_TO_CONTAINER, cfg.depositAllToContainer, window, true);
               fireIfPressed(client, KeybindActions.Action.DEPOSIT_MATCHING_TO_CONTAINER, cfg.depositMatchingToContainer, window, true);
               fireIfPressed(client, KeybindActions.Action.WITHDRAW_ALL_FROM_CONTAINER, cfg.withdrawAllFromContainer, window, true);
               fireIfPressed(client, KeybindActions.Action.WITHDRAW_MATCHING_FROM_CONTAINER, cfg.withdrawMatchingFromContainer, window, true);
            }
         }
      }
   }

   private static boolean matchesBlockingChord(KeybindsConfig.Chord chord, long windowHandle, Type type, int code) {
      if (chord == null || !chord.blockVanilla) {
         return false;
      }

      if (chord.key == null || chord.key.isBlank()) {
         return false;
      }

      if (KeybindUtil.isModifierOnlyChord(chord)) {
         if (type != Type.KEYSYM) {
            return false;
         }

         int keyCode = code;
         int pressedMod = KeybindUtil.modFromKeyCode(keyCode);
         if (pressedMod == 0) {
            return false;
         }

         int chordMods = KeybindUtil.normalizeMods(chord.mods);
         return (chordMods & pressedMod) != 0 && KeybindUtil.exactModsDown(windowHandle, chordMods);
      } else {
         if (KeybindUtil.isModifierTranslationKey(chord.key)) {
            return false;
         }

         Key k = InputUtil.fromTranslationKey(chord.key);
         if (k == null) {
            return false;
         }

         Key extra = chord.extraKey != null && !chord.extraKey.isBlank() ? InputUtil.fromTranslationKey(chord.extraKey) : null;
         boolean currentInputMatches = k.getCategory() == type && k.getCode() == code;
         if (extra != null) {
            currentInputMatches |= extra.getCategory() == type && extra.getCode() == code;
         }

         if (!currentInputMatches) {
            return false;
         }

         if (!KeybindUtil.isKeyLikeDown(windowHandle, k)) {
            return false;
         }

         if (extra != null && !KeybindUtil.isKeyLikeDown(windowHandle, extra)) {
            return false;
         }

         int mods = KeybindUtil.normalizeMods(chord.mods);
         return KeybindUtil.requiredModsHeld(windowHandle, mods);
      }
   }

   private static void fireIfPressed(MinecraftClient client, KeybindActions.Action action, KeybindsConfig.Chord chord, long window, boolean allowExtraMods) {
      if (!KeybindActions.isAvailable(action)) {
         prevDown.put(action, false);
      } else {
         boolean down = isChordDown(window, chord, allowExtraMods);
         boolean wasDown = prevDown.getOrDefault(action, false);
         if (down && !wasDown) {
            KeybindActions.run(action, client);
         }

         prevDown.put(action, down);
      }
   }

   private static boolean isChordDown(long window, KeybindsConfig.Chord chord, boolean allowExtraMods) {
      if (chord == null || chord.key == null || chord.key.isBlank()) {
         return false;
      }

      if (KeybindUtil.isModifierOnlyChord(chord)) {
         return KeybindUtil.exactModsDown(window, chord.mods);
      }

      if (KeybindUtil.isModifierTranslationKey(chord.key)) {
         return false;
      }

      Key k = InputUtil.fromTranslationKey(chord.key);
      if (k == null) {
         return false;
      }

      if (!KeybindUtil.isKeyLikeDown(window, k)) {
         return false;
      }

      if (chord.extraKey != null && !chord.extraKey.isBlank()) {
         Key extra = InputUtil.fromTranslationKey(chord.extraKey);
         if (extra == null || !KeybindUtil.isKeyLikeDown(window, extra)) {
            return false;
         }
      }

      int mods = KeybindUtil.normalizeMods(chord.mods);
      return !KeybindUtil.requiredModsHeld(window, mods) ? false : allowExtraMods || KeybindUtil.exactModsDown(window, mods);
   }
}
