package org.blossomsuite;

import org.blossomsuite.core.SuiteClientBootstrap;
import org.blossomsuite.core.SuiteClientBootstrap.Hooks;
import org.blossomsuite.core.hud.HudEditScreen;
import org.blossomsuite.core.ui.SuiteSettingsScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;

@Environment(EnvType.CLIENT)
public class BlossomBuddyClient implements ClientModInitializer {
   public void onInitializeClient() {
      SuiteClientBootstrap.initialize(
         BlossomBuddyProfile.INSTANCE,
         new Hooks(
            BlossomBuddy.LOGGER::info,
            (message, throwable) -> {
               if (throwable == null) {
                  BlossomBuddy.LOGGER.warn(message);
               } else {
                  BlossomBuddy.LOGGER.warn(message, throwable);
               }
            },
            (message, args) -> BlossomBuddy.LOGGER.warn(message, args),
            BlossomBuddy.LOGGER::error,
            client -> {
               MinecraftClient mc = MinecraftClient.getInstance();
               mc.setScreen(new SuiteSettingsScreen(mc.currentScreen));
            },
            client -> client.setScreen(new HudEditScreen()),
            screen -> screen instanceof HudEditScreen,
            () -> SuiteSettingsScreen.requestOpenOptionsScreen,
            () -> SuiteSettingsScreen.requestOpenOptionsScreen = false,
            () -> HudEditScreen.requestOpenEditScreen,
            () -> HudEditScreen.requestOpenEditScreen = false,
            () -> HudEditScreen.requestOpenEditScreen = true,
            () -> HudEditScreen.requestCloseEditScreen,
            () -> HudEditScreen.requestCloseEditScreen = false
         )
      );
   }
}
