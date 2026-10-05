package org.blossomsuite.core.mixin.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.blossomsuite.core.presence.PresenceClient;
import org.blossomsuite.core.presence.PresenceMarker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public class PlayerListHudMixin {
   /** Adds a small symbol after the name of players in the tab list who are using BlossomBuddy (and chose to appear). */
   @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
   private void suitecore$markBlossomBuddyUsers(PlayerInfo entry, CallbackInfoReturnable<Component> cir) {
      GameProfile profile = entry.getProfile();
      if (profile != null && PresenceClient.INSTANCE.marks(profile.id(), profile.name())) {
         cir.setReturnValue(PresenceMarker.decorate(cir.getReturnValue()));
      }
   }
}
