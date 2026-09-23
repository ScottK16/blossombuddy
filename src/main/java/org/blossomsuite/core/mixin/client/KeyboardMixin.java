package org.blossomsuite.core.mixin.client;

import org.blossomsuite.core.keybinds.KeybindManager;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class KeyboardMixin {
   @Inject(method = "onKey(JIIII)V", at = @At("HEAD"), cancellable = true)
   private void suitecore$maybeConsumeKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc != null && mc.currentScreen == null) {
         if (action == 1 || action == 2) {
            if (KeybindManager.shouldBlockVanilla(window, key)) {
               ci.cancel();
            }
         }
      }
   }
}
