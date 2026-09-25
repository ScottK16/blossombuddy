package org.blossomsuite.core;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.blossomsuite.core.chat.ChatOutput;
import org.blossomsuite.core.chat.SecondaryChat;
import org.blossomsuite.core.chat.StaffChatState;
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
   private static final String CATEGORY = "category.suitecore";
   private static KeyBinding toggleScoreboard;
   private static KeyBinding hotbarUp;
   private static KeyBinding hotbarDown;
   private static KeyBinding hotbarSwap1;
   private static KeyBinding hotbarSwap2;
   private static KeyBinding chatFilter;
   private static KeyBinding playerList;
   private static KeyBinding xchatMode;
   private static KeyBinding emoteWheel;
   private static KeyBinding emoteStop;
   private static KeyBinding closeStaffChat;
   private static KeyBinding searchChat;

   private BuddyKeys() {
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
      searchChat = register("search_chat");
      ClientTickEvents.END_CLIENT_TICK.register(BuddyKeys::tick);
   }

   private static KeyBinding register(String id) {
      return register(id, GLFW.GLFW_KEY_UNKNOWN);
   }

   private static KeyBinding register(String id, int defaultKey) {
      return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.suitecore." + id, InputUtil.Type.KEYSYM, defaultKey, CATEGORY));
   }

   /** Switches staff chat off if it is on. Unlike the toggle key, pressing it again never turns it back on. */
   private static void closeStaffChat(MinecraftClient client) {
      if (!StaffChatState.isStaffTrackingActive()) {
         ChatOutput.info("Staff chat isn't detected for you yet - toggle it once, or check Options > Chat > Staff Chat.");
         return;
      }

      if (!StaffChatState.staffChatEnabled) {
         ChatOutput.info("Staff chat is already off.");
         return;
      }

      if (client.getNetworkHandler() != null && WorldGate.isActive()) {
         client.getNetworkHandler().sendChatCommand("sch toggle");
      }
   }

   private static void tick(MinecraftClient client) {
      if (client.player == null) {
         XChatMode.set(false); // cross-realm chat mode never survives leaving the world
      }

      if (client.player == null || !SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
         return;
      }

      while (toggleScoreboard.wasPressed()) {
         FeatureConfig.Scoreboard sb = FeatureConfig.INSTANCE.scoreboard;
         sb.hidden = !sb.hidden;
         FeatureConfig.markDirty();
         ChatOutput.info("Scoreboard " + (sb.hidden ? "hidden." : "shown."));
      }

      while (hotbarUp.wasPressed()) {
         HotbarCycler.cycle(client, true);
      }

      while (hotbarDown.wasPressed()) {
         HotbarCycler.cycle(client, false);
      }

      while (hotbarSwap1.wasPressed()) {
         HotbarCycler.swapWithRow(client, 1);
      }

      while (hotbarSwap2.wasPressed()) {
         HotbarCycler.swapWithRow(client, 2);
      }

      EmoteClient.INSTANCE.clientTick(); // moving only ends the sitting/lying emotes

      while (emoteWheel.wasPressed()) {
         client.setScreen(new EmoteWheelScreen());
      }

      while (emoteStop.wasPressed()) {
         EmoteClient.INSTANCE.stop();
      }

      while (closeStaffChat.wasPressed()) {
         closeStaffChat(client);
      }

      while (searchChat.wasPressed()) {
         client.setScreen(new ChatSearchScreen(client.currentScreen));
      }

      while (xchatMode.wasPressed()) {
         XChatMode.toggleAndTell();
      }

      while (playerList.wasPressed()) {
         client.setScreen(new PlayerListScreen());
      }

      while (chatFilter.wasPressed()) {
         ChatOutput.info("Secondary chat: " + SecondaryChat.INSTANCE.cycleFilter());
      }
   }
}
