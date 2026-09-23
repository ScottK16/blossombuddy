package org.blossomsuite.core.mixin.client;

import net.minecraft.client.gui.hud.ChatHudLine;
import org.blossomsuite.core.chat.ChatLineTimestamps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records the real time a visible chat line was created, the moment vanilla builds it (see {@link ChatLineTimestamps}). */
@Mixin(ChatHudLine.Visible.class)
public class ChatHudLineVisibleMixin {
   @Inject(method = "<init>", at = @At("RETURN"))
   private void suitecore$recordCreationTime(CallbackInfo ci) {
      ChatLineTimestamps.record((ChatHudLine.Visible)(Object)this, System.currentTimeMillis());
   }
}
