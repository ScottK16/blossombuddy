package org.blossomsuite.core.hud;

import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.config.SuiteConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

public final class HudCallbacks {
   private HudCallbacks() {
   }

   public static void init() {
      HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Identifier.of(SuiteRuntime.profile().modId(), "hud"), (ctx, tickCounter) -> {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client.player != null) {
            if (SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
               if (!client.options.hudHidden) {
                  if (!HudEditState.editMode) {
                     JobsHud.render(ctx, client);
                     CooldownsHud.renderPanel(ctx, client);
                     CooldownsHud.renderHotbar(ctx, client);
                     ToolLockHotbarOverlay.render(ctx, client);
                     CoordsHud.render(ctx, client);
                     BiomeHud.render(ctx, client);
                     AutoSwapperHud.render(ctx, client);
                     HolePuncherHud.render(ctx, client);
                     ScreenNoticeOverlay.render(ctx, client);
                     VoteNotificationOverlay.render(ctx, client);
                     VoteHud.render(ctx, client);
                     RentalsHud.render(ctx, client);
                     ChatHud.render(ctx, client);
                     XpTrackerHud.INSTANCE.render(ctx, client);
                     ExtraHotbarHud.INSTANCE.render(ctx, client);
                     SecondaryChatHud.INSTANCE.render(ctx, client);
                     for (ChatWindowHud window : ChatWindowHud.WINDOWS) {
                        window.render(ctx, client);
                     }
                     MapArtPreviewHud.INSTANCE.render(ctx, client);
                     MapArtListHud.INSTANCE.render(ctx, client);
                     EmoteTimerHud.INSTANCE.render(ctx, client);
                  }
               }
            }
         }
      });
   }
}
