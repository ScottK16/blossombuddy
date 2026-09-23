package org.blossomsuite.core.mixin.client;

import org.blossomsuite.core.keybinds.KeybindManager;
import org.blossomsuite.core.qol.mining.MiningResumeGuard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {
   @Inject(method = "onMouseButton(JIII)V", at = @At("HEAD"), cancellable = true)
   private void suitecore$maybeConsumeMouseButton(long window, int button, int action, int modifiers, CallbackInfo ci) {
      MiningResumeGuard.onMouseButton(button, action);
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc != null && mc.currentScreen == null) {
         if (action == 1) {
            if (KeybindManager.shouldBlockVanillaMouse(window, button)) {
               ci.cancel();
            }
         }
      }
   }
}
