package org.blossomsuite.core.ui;

import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.chat.StaffChatState;
import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.KeybindsConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.keybinds.KeybindActions;
import org.blossomsuite.core.keybinds.KeybindUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil.Key;
import net.minecraft.client.util.InputUtil.Type;
import net.minecraft.text.Text;

public final class KeybindsTab implements SuiteTab {
   private KeybindActions.Action capturing = null;
   private int captureModifierMods = 0;
   private final List<String> captureKeys = new ArrayList<>();
   private static final int ROW_H = 20;
   private static final int GAP_X = 6;
   private static final int HEADER_H = 12;
   private static final int HEADER_GAP_BELOW = 14;
   private static final int SECTION_GAP = 10;
   private static final int CONFLICT_H = 12;
   private static final int CONFLICT_GAP_BELOW = 6;

   @Override
   public String titleKey() {
      return "suitecore.tab.keybinds";
   }

   @Override
   public boolean isEnabled() {
      return true;
   }

   @Override
   public void setEnabled(boolean enabled) {
   }

   @Override
   public void build(SuiteSettingsScreen screen) {
      int x = screen.contentX();
      int w = screen.contentW();
      int y = screen.bodyContentY() - screen.scrollOffset() + 8;
      screen.addContentWidget(
         new HoverLabelWidget(
            x,
            y,
            160,
            12,
            Text.literal("Keybinds"),
            Tooltip.of(Text.literal("Custom " + suiteName() + " keybinds (supports left/right Ctrl/Shift/Alt and mouse buttons)."))
         )
      );
      y += 18;
      boolean firstSection = true;

      for (KeybindsTab.Entry e : entries()) {
         if (e instanceof KeybindsTab.Header h) {
            if (!firstSection) {
               y += 10;
            }

            firstSection = false;
            y = addHeaderRow(screen, x, y, h);
         } else if (e instanceof KeybindsTab.Row r) {
            y = this.addKeyRow(screen, x, w, y, 20, 6, r);
         }
      }
   }

   @Override
   public void renderText(SuiteSettingsScreen screen, DrawContext ctx, int mouseX, int mouseY, float delta) {
      if (this.capturing != null) {
         ctx.drawCenteredTextWithShadow(
            screen.getTextRenderer(),
            Text.literal("Press a key, two keys like M + K, mouse button, or release modifiers to bind them alone. ESC cancels."),
            screen.width / 2,
            screen.height - 52,
            -1
         );
      }
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (this.capturing == null) {
         return false;
      }

      if (keyCode == 256) {
         this.clearCapture();
         return true;
      }

      KeybindsConfig.Chord chord = chordFor(this.capturing);
      if (chord == null) {
         this.clearCapture();
         return true;
      }

      int modifierOnly = KeybindUtil.modFromKeyCode(keyCode);
      if (modifierOnly != 0) {
         this.captureModifierMods = KeybindUtil.normalizeMods(this.captureModifierMods | KeybindUtil.heldMods(KeybindUtil.windowHandle()) | modifierOnly);
         return true;
      }

      Key key = Type.KEYSYM.createFromCode(keyCode);
      String translationKey = key.getTranslationKey();
      if (translationKey != null && !translationKey.isBlank() && this.captureKeys.size() < 2 && !this.captureKeys.contains(translationKey)) {
         this.captureKeys.add(translationKey);
      }

      this.captureModifierMods = KeybindUtil.normalizeMods(this.captureModifierMods | KeybindUtil.heldMods(KeybindUtil.windowHandle()));
      return true;
   }

   private void saveCapturedKeys(KeybindsConfig.Chord chord) {
      if (chord != null && !this.captureKeys.isEmpty()) {
         chord.key = this.captureKeys.get(0);
         chord.extraKey = this.captureKeys.size() > 1 ? this.captureKeys.get(1) : "";
         chord.mods = KeybindUtil.normalizeMods(this.captureModifierMods | KeybindUtil.heldMods(KeybindUtil.windowHandle()));
         SuiteConfig.INSTANCE.markDirty();
         ConfigIO.saveIfDirty();
      }
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (this.capturing == null) {
         return false;
      }

      // Left/right click are how the player operates the rest of this very screen, so while waiting for a key they are treated
      // as "never mind" (like Esc), not as a bind: otherwise clicking away to do anything else silently rebinds to a click.
      // A side mouse button (button >= 2) is still a real, intentional binding choice.
      if (button != 0 && button != 1) {
         KeybindsConfig.Chord chord = chordFor(this.capturing);
         if (chord != null) {
            Key key = Type.MOUSE.createFromCode(button);
            String translationKey = key.getTranslationKey();
            chord.key = translationKey == null ? "" : translationKey;
            chord.extraKey = "";
            chord.mods = KeybindUtil.normalizeMods(KeybindUtil.heldMods(KeybindUtil.windowHandle()));
            SuiteConfig.INSTANCE.markDirty();
            ConfigIO.saveIfDirty();
         }
      }

      this.clearCapture();
      return true;
   }

   @Override
   public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
      if (this.capturing == null) {
         return false;
      }

      int releasedMod = KeybindUtil.modFromKeyCode(keyCode);
      if (releasedMod == 0) {
         KeybindsConfig.Chord chord = chordFor(this.capturing);
         this.saveCapturedKeys(chord);
         this.clearCapture();
         return true;
      }

      // A real key (Ctrl+K, say) is recorded on release of the REAL key, not the modifier, however the player happens to let go
      // of them: releasing the modifier first must not finalize early and lose the key that is still held.
      this.captureModifierMods = KeybindUtil.normalizeMods(this.captureModifierMods | releasedMod);
      if (!this.captureKeys.isEmpty()) {
         return true;
      }

      int stillHeld = KeybindUtil.normalizeMods(KeybindUtil.heldMods(KeybindUtil.windowHandle()) & ~releasedMod);
      if (stillHeld != 0) {
         return true;
      }

      KeybindsConfig.Chord chord = chordFor(this.capturing);
      if (chord != null && this.captureModifierMods != 0) {
         chord.key = "suitecore.modifier_only";
         chord.extraKey = "";
         chord.mods = this.captureModifierMods;
         SuiteConfig.INSTANCE.markDirty();
         ConfigIO.saveIfDirty();
      }

      this.clearCapture();
      return true;
   }

   @Override
   public void removed() {
      this.clearCapture();
   }

   private void clearCapture() {
      this.capturing = null;
      this.captureModifierMods = 0;
      this.captureKeys.clear();
   }

   @Override
   public int contentHeight(SuiteSettingsScreen screen) {
      int h = 26;
      boolean firstSection = true;

      for (KeybindsTab.Entry e : entries()) {
         if (e instanceof KeybindsTab.Header) {
            if (!firstSection) {
               h += 10;
            }

            firstSection = false;
            h += 14;
         } else if (e instanceof KeybindsTab.Row r) {
            h += rowHeight(r);
         }
      }

      return h + 40;
   }

   private static int addHeaderRow(SuiteSettingsScreen screen, int x, int y, KeybindsTab.Header h) {
      screen.addContentWidget(new HoverLabelWidget(x, y, 200, 12, Text.literal(h.label), Tooltip.of(Text.literal(h.tooltip))));
      return y + 14;
   }

   private int addKeyRow(SuiteSettingsScreen screen, int x, int w, int y, int rowH, int gapX, KeybindsTab.Row r) {
      int clearW = 60;
      int blockW = 80;
      int minBindW = 160;
      int labelW = Math.min(220, Math.max(140, w - (clearW + blockW + gapX * 2 + minBindW)));
      screen.addContentWidget(new HoverLabelWidget(x, y + 6, labelW, 12, Text.literal(r.label), Tooltip.of(Text.literal(r.tooltip))));
      int bindW = w - labelW - gapX - blockW - gapX - clearW;
      int bindX = x + labelW + gapX;
      int blockX = x + w - clearW - gapX - blockW;
      int clearX = x + w - clearW;
      KeybindsConfig.Chord chord = chordFor(r.action);
      String buttonText = this.capturing == r.action ? "Press key..." : KeybindUtil.formatChord(chord);
      ButtonWidget bindBtn = StyledButton.of(Text.literal(buttonText), b -> {
         this.capturing = this.capturing == r.action ? null : r.action;
         this.captureModifierMods = 0;
         screen.rebuildPreserveScroll();
      }).dimensions(bindX, y, bindW, rowH).build();
      screen.addContentWidget(bindBtn);
      ButtonWidget blockBtn = StyledButton.of(Text.literal(chord != null && chord.blockVanilla ? "Block: ON" : "Block: OFF"), b -> {
         KeybindsConfig.Chord c = chordFor(r.action);
         if (c != null) {
            c.blockVanilla = !c.blockVanilla;
            SuiteConfig.INSTANCE.markDirty();
            ConfigIO.saveIfDirty();
         }

         this.capturing = null;
         screen.rebuildPreserveScroll();
      }).dimensions(blockX, y, blockW, rowH).build();
      blockBtn.setTooltip(
         Tooltip.of(
            Text.literal(
               "When ON, " + suiteName() + " will try to stop vanilla from also using the primary key.\nUse with care: this can block movement/chat/etc."
            )
         )
      );
      screen.addContentWidget(blockBtn);
      ButtonWidget clearBtn = StyledButton.of(Text.literal("Clear"), b -> {
         KeybindsConfig.Chord c = chordFor(r.action);
         if (c != null) {
            c.key = "";
            c.extraKey = "";
            c.mods = 0;
            c.blockVanilla = false;
            SuiteConfig.INSTANCE.markDirty();
            ConfigIO.saveIfDirty();
         }

         this.capturing = null;
         screen.rebuildPreserveScroll();
      }).dimensions(clearX, y, clearW, rowH).build();
      screen.addContentWidget(clearBtn);
      String conflict = computeConflict(r.action);
      if (conflict != null && !conflict.isBlank()) {
         screen.addContentWidget(new LabelWidget(x, y + rowH + 2, w, 12, Text.literal(conflict), -37266));
         return y + rowH + 2 + 12 + 6;
      } else {
         return y + 24;
      }
   }

   private static int rowHeight(KeybindsTab.Row r) {
      String conflict = computeConflict(r.action);
      return conflict != null && !conflict.isBlank() ? 40 : 24;
   }

   private static List<KeybindsTab.Row> keyRows() {
      List<KeybindsTab.Row> out = new ArrayList<>();
      out.add(new KeybindsTab.Row(KeybindActions.Action.OPEN_SETTINGS, "Open Settings", "Opens the " + suiteName() + " settings screen."));
      out.add(new KeybindsTab.Row(KeybindActions.Action.TOGGLE_EDIT_MODE, "Toggle HUD Edit Mode", "Toggles the HUD editor."));
      out.add(new KeybindsTab.Row(KeybindActions.Action.PAUSE_RESUME, "Pause / Resume", "Pauses or resumes Jobs tracking."));
      out.add(new KeybindsTab.Row(KeybindActions.Action.RESET_SESSION, "Reset Session", "Resets the Jobs session and segment."));
      out.add(new KeybindsTab.Row(KeybindActions.Action.RESET_SEGMENT, "Reset Segment", "Resets the Jobs segment."));
      out.add(new KeybindsTab.Row(KeybindActions.Action.TOGGLE_AUTO_SWAPPER, "Toggle AutoSwapper", "Toggles AutoSwapper on/off."));
      out.add(new KeybindsTab.Row(KeybindActions.Action.TOGGLE_AUTO_DROPPER, "Toggle AutoDropper", "Toggles AutoDropper on/off."));
      out.add(new KeybindsTab.Row(KeybindActions.Action.TOGGLE_RENTALS_PAUSE, "Pause / Resume Rentals", "Pauses or resumes all active rental timers."));
      out.add(new KeybindsTab.Row(KeybindActions.Action.CLEAR_EXPIRED_RENTALS, "Clear Expired Rentals", "Removes rental counters whose timers are done."));
      out.add(new KeybindsTab.Row(KeybindActions.Action.RUN_AUTO_DROPPER, "Run AutoDropper", "Drops configured items once immediately."));
      out.add(new KeybindsTab.Row(KeybindActions.Action.RUN_CONDENSE, "Run Condense", "Runs /condense."));
      out.add(
         new KeybindsTab.Row(
            KeybindActions.Action.SORT_INVENTORY, "Sort Player Inventory", "Sorts unlocked player inventory slots and leaves locked slots untouched."
         )
      );
      out.add(
         new KeybindsTab.Row(KeybindActions.Action.SORT_CONTAINER, "Sort Container", "Sorts the open chest, barrel, ender chest, double chest, or shulker box.")
      );
      out.add(new KeybindsTab.Row(KeybindActions.Action.SORT_ALL, "Sort All", "Sorts unlocked player inventory slots and the open supported container."));
      out.add(
         new KeybindsTab.Row(
            KeybindActions.Action.DEPOSIT_ALL_TO_CONTAINER, "Deposit All", "Moves all player inventory items into the open supported container."
         )
      );
      out.add(
         new KeybindsTab.Row(
            KeybindActions.Action.DEPOSIT_MATCHING_TO_CONTAINER, "Deposit Matching", "Moves player inventory items that match existing container items."
         )
      );
      out.add(
         new KeybindsTab.Row(
            KeybindActions.Action.WITHDRAW_ALL_FROM_CONTAINER, "Withdraw All", "Moves all items from the open supported container into your player inventory."
         )
      );
      out.add(
         new KeybindsTab.Row(
            KeybindActions.Action.WITHDRAW_MATCHING_FROM_CONTAINER, "Withdraw Matching", "Moves container items that match existing player inventory items."
         )
      );
      out.add(new KeybindsTab.Row(KeybindActions.Action.TOGGLE_MARRY_CHAT, "Toggle Marry Chat", "Runs /marry chattoggle."));
      out.add(
         new KeybindsTab.Row(
            KeybindActions.Action.TOGGLE_HOLE_PUNCHER_MODE,
            "Use Hole Puncher",
            "Starts Hole Puncher from your crosshair target (requires HolePuncher enabled)."
         )
      );
      out.add(
         new KeybindsTab.Row(
            KeybindActions.Action.TOGGLE_HOLE_PUNCHER_ENABLED,
            "Toggle Hole Puncher",
            "Enables/disables HolePuncher. When disabled, Use Hole Puncher does nothing."
         )
      );
      out.add(
         new KeybindsTab.Row(
            KeybindActions.Action.TOGGLE_HOLE_PUNCHER_MARKERS, "Toggle HP Markers", "Turns HolePuncher grid markers on/off (guided+markers mode)."
         )
      );
      out.add(
         new KeybindsTab.Row(
            KeybindActions.Action.SET_MINING_TRACK,
            "Set Mining Track",
            "Sets your mining track direction (N/S or E/W) based on your current facing, and locks the X/Z coordinate."
         )
      );
      out.add(new KeybindsTab.Row(KeybindActions.Action.CLEAR_MINING_TRACK, "Clear Mining Track", "Clears the locked mining track direction/coordinate."));
      out.add(
         new KeybindsTab.Row(KeybindActions.Action.TOGGLE_MINING_TRACK_INDICATOR, "Toggle Mining Track Overlay", "Turns the Mining Track world overlay on/off.")
      );
      out.add(
         new KeybindsTab.Row(
            KeybindActions.Action.TOGGLE_TOOL_LOCK, "Toggle Tool Lock", "Blocks sneak+right-click for configured tools so they can't swap modes accidentally."
         )
      );
      out.add(
         new KeybindsTab.Row(
            KeybindActions.Action.TOGGLE_TOOL_LOCK_SLOT,
            "Lock/Unlock Slot",
            "Locks or unlocks your currently-held hotbar slot (Tool Lock must be ON to block inputs)."
         )
      );
      out.add(
         new KeybindsTab.Row(KeybindActions.Action.USE_ADVERTISER, "Send Advertisement", "Sends the active advertiser profile message (respects cooldown).")
      );
      out.add(new KeybindsTab.Row(KeybindActions.Action.CYCLE_ADVERTISER_PROFILE, "Next Advertiser Profile", "Cycles to the next advertiser profile."));
      if (StaffChatState.isStaffTrackingActive()) {
         out.add(new KeybindsTab.Row(KeybindActions.Action.SEND_WELCOME_MESSAGE, "Send Welcome Message", "Sends your configured welcome message."));
         out.add(new KeybindsTab.Row(KeybindActions.Action.TOGGLE_STAFF_CHAT, "Toggle Staff Chat", "Runs /sch toggle (staff only)."));
      }

      out.removeIf(row -> !KeybindActions.isAvailable(row.action));
      return out;
   }

   private static List<KeybindsTab.Entry> entries() {
      List<KeybindsTab.Entry> out = new ArrayList<>();
      out.add(new KeybindsTab.Header("UI", "Settings and UI controls."));
      out.add(new KeybindsTab.Row(KeybindActions.Action.OPEN_SETTINGS, "Open Settings", "Opens the " + suiteName() + " settings screen."));
      out.add(new KeybindsTab.Row(KeybindActions.Action.TOGGLE_EDIT_MODE, "Toggle HUD Edit Mode", "Toggles the HUD editor."));
      out.add(new KeybindsTab.Header("QOL", "Quality-of-life feature hotkeys."));
      out.add(new KeybindsTab.Row(KeybindActions.Action.TOGGLE_AUTO_SWAPPER, "Toggle AutoSwapper", "Toggles AutoSwapper on/off."));
      out.add(new KeybindsTab.Row(KeybindActions.Action.TOGGLE_AUTO_DROPPER, "Toggle AutoDropper", "Toggles AutoDropper on/off."));
      out.add(new KeybindsTab.Row(KeybindActions.Action.TOGGLE_RENTALS_PAUSE, "Pause / Resume Rentals", "Pauses or resumes all active rental timers."));
      out.add(new KeybindsTab.Row(KeybindActions.Action.CLEAR_EXPIRED_RENTALS, "Clear Expired Rentals", "Removes rental counters whose timers are done."));
      out.add(new KeybindsTab.Row(KeybindActions.Action.RUN_AUTO_DROPPER, "Run AutoDropper", "Drops configured items once immediately."));
      out.add(new KeybindsTab.Row(KeybindActions.Action.RUN_CONDENSE, "Run Condense", "Runs /condense."));
      out.add(
         new KeybindsTab.Row(
            KeybindActions.Action.SORT_INVENTORY, "Sort Player Inventory", "Sorts unlocked player inventory slots and leaves locked slots untouched."
         )
      );
      out.add(
         new KeybindsTab.Row(KeybindActions.Action.SORT_CONTAINER, "Sort Container", "Sorts the open chest, barrel, ender chest, double chest, or shulker box.")
      );
      out.add(new KeybindsTab.Row(KeybindActions.Action.SORT_ALL, "Sort All", "Sorts unlocked player inventory slots and the open supported container."));
      out.add(
         new KeybindsTab.Row(
            KeybindActions.Action.DEPOSIT_ALL_TO_CONTAINER, "Deposit All", "Moves all player inventory items into the open supported container."
         )
      );
      out.add(
         new KeybindsTab.Row(
            KeybindActions.Action.DEPOSIT_MATCHING_TO_CONTAINER, "Deposit Matching", "Moves player inventory items that match existing container items."
         )
      );
      out.add(
         new KeybindsTab.Row(
            KeybindActions.Action.WITHDRAW_ALL_FROM_CONTAINER, "Withdraw All", "Moves all items from the open supported container into your player inventory."
         )
      );
      out.add(
         new KeybindsTab.Row(
            KeybindActions.Action.WITHDRAW_MATCHING_FROM_CONTAINER, "Withdraw Matching", "Moves container items that match existing player inventory items."
         )
      );
      out.add(
         new KeybindsTab.Row(
            KeybindActions.Action.TOGGLE_HOLE_PUNCHER_MODE,
            "Use Hole Puncher",
            "Starts Hole Puncher from your crosshair target (requires HolePuncher enabled)."
         )
      );
      out.add(
         new KeybindsTab.Row(
            KeybindActions.Action.TOGGLE_HOLE_PUNCHER_ENABLED,
            "Toggle Hole Puncher",
            "Enables/disables HolePuncher. When disabled, Use Hole Puncher does nothing."
         )
      );
      out.add(
         new KeybindsTab.Row(
            KeybindActions.Action.TOGGLE_HOLE_PUNCHER_MARKERS, "Toggle HP Markers", "Turns HolePuncher grid markers on/off (guided+markers mode)."
         )
      );
      out.add(
         new KeybindsTab.Row(
            KeybindActions.Action.SET_MINING_TRACK,
            "Set Mining Track",
            "Sets your mining track direction (N/S or E/W) based on your current facing, and locks the X/Z coordinate."
         )
      );
      out.add(new KeybindsTab.Row(KeybindActions.Action.CLEAR_MINING_TRACK, "Clear Mining Track", "Clears the locked mining track direction/coordinate."));
      out.add(
         new KeybindsTab.Row(KeybindActions.Action.TOGGLE_MINING_TRACK_INDICATOR, "Toggle Mining Track Overlay", "Turns the Mining Track world overlay on/off.")
      );
      out.add(
         new KeybindsTab.Row(
            KeybindActions.Action.TOGGLE_TOOL_LOCK, "Toggle Tool Lock", "Blocks sneak+right-click for configured tools so they can't swap modes accidentally."
         )
      );
      out.add(
         new KeybindsTab.Row(
            KeybindActions.Action.TOGGLE_TOOL_LOCK_SLOT,
            "Lock/Unlock Slot",
            "Locks or unlocks your currently-held hotbar slot (Tool Lock must be ON to block inputs)."
         )
      );
      out.add(new KeybindsTab.Header("Chat", "Chat-related hotkeys and quick actions."));
      out.add(new KeybindsTab.Row(KeybindActions.Action.TOGGLE_MARRY_CHAT, "Toggle Marry Chat", "Runs /marry chattoggle."));
      out.add(new KeybindsTab.Header("Advertiser", "Advertiser profile controls."));
      out.add(
         new KeybindsTab.Row(KeybindActions.Action.USE_ADVERTISER, "Send Advertisement", "Sends the active advertiser profile message (respects cooldown).")
      );
      out.add(new KeybindsTab.Row(KeybindActions.Action.CYCLE_ADVERTISER_PROFILE, "Next Advertiser Profile", "Cycles to the next advertiser profile."));
      if (StaffChatState.isStaffTrackingActive()) {
         out.add(new KeybindsTab.Header("Staff", "Staff-only chat actions."));
         out.add(new KeybindsTab.Row(KeybindActions.Action.SEND_WELCOME_MESSAGE, "Send Welcome Message", "Sends your configured welcome message."));
         out.add(new KeybindsTab.Row(KeybindActions.Action.TOGGLE_STAFF_CHAT, "Toggle Staff Chat", "Runs /sch toggle (staff only)."));
      }

      out.add(new KeybindsTab.Header("Jobs", "Jobs tracking controls."));
      out.add(new KeybindsTab.Row(KeybindActions.Action.PAUSE_RESUME, "Pause / Resume", "Pauses or resumes Jobs tracking."));
      out.add(new KeybindsTab.Row(KeybindActions.Action.RESET_SESSION, "Reset Session", "Resets the Jobs session and segment."));
      out.add(new KeybindsTab.Row(KeybindActions.Action.RESET_SEGMENT, "Reset Segment", "Resets the Jobs segment."));
      return filterUnavailableRows(out);
   }

   private static List<KeybindsTab.Entry> filterUnavailableRows(List<KeybindsTab.Entry> entries) {
      List<KeybindsTab.Entry> filtered = new ArrayList<>();
      KeybindsTab.Header pendingHeader = null;

      for (KeybindsTab.Entry entry : entries) {
         if (entry instanceof KeybindsTab.Header header) {
            pendingHeader = header;
         } else if (entry instanceof KeybindsTab.Row row && KeybindActions.isAvailable(row.action)) {
            if (pendingHeader != null) {
               filtered.add(pendingHeader);
               pendingHeader = null;
            }

            filtered.add(row);
         }
      }

      return filtered;
   }

   private static String suiteName() {
      return SuiteRuntime.profile().displayName();
   }

   private static KeybindsConfig.Chord chordFor(KeybindActions.Action a) {
      KeybindsConfig k = SuiteConfig.INSTANCE.KeybindsConfig;

      return switch (a) {
         case OPEN_SETTINGS -> k.openSettings;
         case TOGGLE_EDIT_MODE -> k.toggleEditMode;
         case PAUSE_RESUME -> k.pauseResume;
         case RESET_SESSION -> k.resetSession;
         case RESET_SEGMENT -> k.resetSegment;
         case TOGGLE_AUTO_SWAPPER -> k.toggleAutoSwapper;
         case TOGGLE_AUTO_DROPPER -> k.toggleAutoDropper;
         case TOGGLE_RENTALS_PAUSE -> k.toggleRentalsPause;
         case CLEAR_EXPIRED_RENTALS -> k.clearExpiredRentals;
         case RUN_AUTO_DROPPER -> k.runAutoDropper;
         case RUN_CONDENSE -> k.runCondense;
         case SORT_INVENTORY -> k.sortInventory;
         case SORT_CONTAINER -> k.sortContainer;
         case SORT_ALL -> k.sortAll;
         case DEPOSIT_ALL_TO_CONTAINER -> k.depositAllToContainer;
         case DEPOSIT_MATCHING_TO_CONTAINER -> k.depositMatchingToContainer;
         case WITHDRAW_ALL_FROM_CONTAINER -> k.withdrawAllFromContainer;
         case WITHDRAW_MATCHING_FROM_CONTAINER -> k.withdrawMatchingFromContainer;
         case TOGGLE_MARRY_CHAT -> k.toggleMarryChat;
         case TOGGLE_HOLE_PUNCHER_MODE -> k.toggleHolePuncherMode;
         case TOGGLE_HOLE_PUNCHER_ENABLED -> k.toggleHolePuncherEnabled;
         case TOGGLE_HOLE_PUNCHER_MARKERS -> k.toggleHolePuncherMarkers;
         case SET_MINING_TRACK -> k.setMiningTrack;
         case CLEAR_MINING_TRACK -> k.clearMiningTrack;
         case TOGGLE_MINING_TRACK_INDICATOR -> k.toggleMiningTrackIndicator;
         case TOGGLE_TOOL_LOCK -> k.toggleToolLock;
         case TOGGLE_TOOL_LOCK_SLOT -> k.toggleToolLockSlot;
         case USE_ADVERTISER -> k.useAdvertiser;
         case CYCLE_ADVERTISER_PROFILE -> k.cycleAdvertiserProfile;
         case SEND_WELCOME_MESSAGE -> k.sendWelcomeMessage;
         case TOGGLE_STAFF_CHAT -> k.toggleStaffChat;
      };
   }

   private static String computeConflict(KeybindActions.Action self) {
      KeybindsConfig.Chord chord = chordFor(self);
      if (chord == null || chord.key == null || chord.key.isBlank()) {
         return "";
      }

      if (KeybindUtil.isModifierTranslationKey(chord.key)) {
         return "";
      }

      for (KeybindsTab.Row r : keyRows()) {
         if (r.action != self) {
            KeybindsConfig.Chord other = chordFor(r.action);
            if (other != null && other.key != null && !other.key.isBlank()) {
               if (sameChord(chord, other)) {
                  return "Conflict: also bound to " + r.label;
               }

               if (overlapsAsPrefix(chord, other)) {
                  return "Overlap: " + r.label + " uses part of this combo.";
               }
            }
         }
      }

      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc != null && mc.options != null && mc.options.allKeys != null) {
         for (KeyBinding kb : mc.options.allKeys) {
            if (kb != null) {
               String bound = kb.getBoundKeyTranslationKey();
               if (bound != null && chordContainsKey(chord, bound)) {
                  return "Vanilla also uses this key: " + Text.translatable(kb.getTranslationKey()).getString() + " (vanilla ignores modifiers).";
               }
            }
         }
      }

      return "";
   }

   private static boolean sameChord(KeybindsConfig.Chord a, KeybindsConfig.Chord b) {
      if (a.mods != b.mods) {
         return false;
      }

      String aKey = safeKey(a.key);
      String aExtra = safeKey(a.extraKey);
      String bKey = safeKey(b.key);
      String bExtra = safeKey(b.extraKey);
      return aKey.equals(bKey) && aExtra.equals(bExtra) || !aExtra.isBlank() && !bExtra.isBlank() && aKey.equals(bExtra) && aExtra.equals(bKey);
   }

   private static boolean overlapsAsPrefix(KeybindsConfig.Chord a, KeybindsConfig.Chord b) {
      if (a.mods != b.mods) {
         return false;
      } else {
         String aKey = safeKey(a.key);
         String aExtra = safeKey(a.extraKey);
         String bKey = safeKey(b.key);
         String bExtra = safeKey(b.extraKey);
         if (aExtra.isBlank() && !bExtra.isBlank()) {
            return chordContainsKey(b, aKey);
         } else {
            return !aExtra.isBlank() && bExtra.isBlank() ? chordContainsKey(a, bKey) : false;
         }
      }
   }

   private static boolean chordContainsKey(KeybindsConfig.Chord chord, String key) {
      return chord != null && key != null && !key.isBlank() ? key.equals(safeKey(chord.key)) || key.equals(safeKey(chord.extraKey)) : false;
   }

   private static String safeKey(String key) {
      return key == null ? "" : key;
   }

   private sealed interface Entry permits KeybindsTab.Header, KeybindsTab.Row {
   }

   private static final class Header implements KeybindsTab.Entry {
      final String label;
      final String tooltip;

      Header(String label, String tooltip) {
         this.label = label;
         this.tooltip = tooltip;
      }
   }

   private static final class Row implements KeybindsTab.Entry {
      final KeybindActions.Action action;
      final String label;
      final String tooltip;

      Row(KeybindActions.Action action, String label, String tooltip) {
         this.action = action;
         this.label = label;
         this.tooltip = tooltip;
      }
   }
}
