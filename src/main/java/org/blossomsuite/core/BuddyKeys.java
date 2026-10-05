package org.blossomsuite.core;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import org.blossomsuite.core.chat.ChatOutput;
import org.blossomsuite.core.chat.SecondaryChat;
import org.blossomsuite.core.chat.StaffChatState;
import org.blossomsuite.core.visibility.PlayerVisibility;
import org.blossomsuite.core.util.WorldGate;
import org.blossomsuite.core.config.FeatureConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.hud.HotbarCycler;
import org.blossomsuite.core.emote.EmoteClient;
import org.blossomsuite.core.ui.ChatSearchScreen;
import org.blossomsuite.core.ui.EmoteWheelScreen;
import org.blossomsuite.core.ui.PlayerListScreen;
import org.blossomsuite.core.xchat.XChatMode;
import org.lwjgl.glfw.GLFW;

/**
 * Hotkeys for the BlossomBuddy features. These are ordinary Minecraft key bindings, so they appear in
 * Options > Controls under "BlossomBuddy" and are rebound there. All start unbound, except the emote menu (B).
 */
public final class BuddyKeys {
   private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("suitecore", "main"));
   private static KeyMapping toggleScoreboard;
   private static KeyMapping hotbarUp;
   private static KeyMapping hotbarDown;
   private static KeyMapping hotbarSwap1;
   private static KeyMapping hotbarSwap2;
   private static KeyMapping chatFilter;
   private static KeyMapping playerList;
   private static KeyMapping xchatMode;
   private static KeyMapping emoteWheel;
   private static KeyMapping emoteStop;
   private static KeyMapping closeStaffChat;
   private static KeyMapping hidePlayers;
   private static KeyMapping searchChat;

   private BuddyKeys() {
   }

   /** Every BlossomBuddy key, for the in-mod keys screen (some clients don't list mod keys in their own controls menu). */
   public static java.util.List<KeyMapping> all() {
      return java.util.List.of(
         emoteWheel, emoteStop, closeStaffChat, hidePlayers, searchChat, xchatMode, playerList, chatFilter,
         toggleScoreboard, hotbarUp, hotbarDown, hotbarSwap1, hotbarSwap2
      );
   }

   public static void init() {
      toggleScoreboard = register("toggle_scoreboard");
      hotbarUp = register("hotbar_up");
      hotbarDown = register("hotbar_down");
      hotbarSwap1 = register("hotbar_swap_1");
      hotbarSwap2 = register("hotbar_swap_2");
      chatFilter = register("chat_filter");
      playerList = register("player_list");
      xchatMode = register("xchat_mode");
      emoteWheel = register("emote_wheel", GLFW.GLFW_KEY_B); // only for people who haven't got this key set yet; anyone's own choice wins
      emoteStop = register("emote_stop");
      closeStaffChat = register("close_staff_chat");
      hidePlayers = register("hide_players");
      searchChat = register("search_chat");
      ClientTickEvents.END_CLIENT_TICK.register(BuddyKeys::tick);
      ClientTickEvents.END_CLIENT_TICK.register(org.blossomsuite.core.alts.AltGuard::tick);
   }

   private static KeyMapping register(String id) {
      return register(id, GLFW.GLFW_KEY_UNKNOWN);
   }

   private static KeyMapping register(String id, int defaultKey) {
      return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.suitecore." + id, InputConstants.Type.KEYSYM, defaultKey, CATEGORY));
   }

   /** Switches staff chat off if it is on. Unlike the toggle key, pressing it again never turns it back on. */
   private static void closeStaffChat(Minecraft client) {
      if (!StaffChatState.isStaffTrackingActive()) {
         ChatOutput.info("Staff chat isn't detected for you yet - toggle it once, or check Options > Chat > Staff Chat.");
         return;
      }

      if (!StaffChatState.staffChatEnabled) {
         ChatOutput.info("Staff chat is already off.");
         return;
      }

      if (client.getConnection() != null && WorldGate.isActive()) {
         client.getConnection().sendCommand("sch toggle");
      }
   }

   private static void tick(Minecraft client) {
      if (client.player == null) {
         XChatMode.set(false); // cross-realm chat mode never survives leaving the world
         PlayerVisibility.set(false); // nor does hiding the other players
      }

      if (client.player == null || !SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
         return;
      }

      while (toggleScoreboard.consumeClick()) {
         FeatureConfig.Scoreboard sb = FeatureConfig.INSTANCE.scoreboard;
         sb.hidden = !sb.hidden;
         FeatureConfig.markDirty();
         ChatOutput.info("Scoreboard " + (sb.hidden ? "hidden." : "shown."));
      }

      while (hotbarUp.consumeClick()) {
         HotbarCycler.cycle(client, true);
      }

      while (hotbarDown.consumeClick()) {
         HotbarCycler.cycle(client, false);
      }

      while (hotbarSwap1.consumeClick()) {
         HotbarCycler.swapWithRow(client, 1);
      }

      while (hotbarSwap2.consumeClick()) {
         HotbarCycler.swapWithRow(client, 2);
      }

      EmoteClient.INSTANCE.clientTick(); // moving only ends the sitting/lying emotes

      while (emoteWheel.consumeClick()) {
         client.setScreen(new EmoteWheelScreen());
      }

      while (emoteStop.consumeClick()) {
         EmoteClient.INSTANCE.stop();
      }

      while (closeStaffChat.consumeClick()) {
         closeStaffChat(client);
      }

      while (hidePlayers.consumeClick()) {
         ChatOutput.info(PlayerVisibility.toggle() ? "Other players hidden." : "Other players shown again.");
      }

      while (searchChat.consumeClick()) {
         client.setScreen(new ChatSearchScreen(client.screen));
      }

      while (xchatMode.consumeClick()) {
         XChatMode.toggleAndTell();
      }

      while (playerList.consumeClick()) {
         client.setScreen(new PlayerListScreen());
      }

      while (chatFilter.consumeClick()) {
         ChatOutput.info("Secondary chat: " + SecondaryChat.INSTANCE.cycleFilter());
      }
   }
}
