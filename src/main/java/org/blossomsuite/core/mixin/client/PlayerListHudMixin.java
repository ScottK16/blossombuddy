package org.blossomsuite.core.mixin.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.blossomsuite.core.presence.PresenceClient;
import org.blossomsuite.core.presence.PresenceMarker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerListHud.class)
public class PlayerListHudMixin {
   /** Adds a small symbol after the name of players in the tab list who are using BlossomBuddy (and chose to appear). */
   @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
   private void suitecore$markBlossomBuddyUsers(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
      GameProfile profile = entry.getProfile();
      if (profile != null && PresenceClient.INSTANCE.marks(profile.getId(), profile.getName())) {
         cir.setReturnValue(PresenceMarker.decorate(cir.getReturnValue()));
      }
   }
}
