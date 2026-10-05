package org.blossomsuite.core.mixin.client;

import org.blossomsuite.core.keybinds.KeybindManager;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardMixin {
   @Inject(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V", at = @At("HEAD"), cancellable = true)
   private void suitecore$maybeConsumeKey(long window, int action, KeyEvent event, CallbackInfo ci) {
      int key = event.key();
      Minecraft mc = Minecraft.getInstance();
      if (mc != null && mc.screen == null) {
         if (action == 1 || action == 2) {
            if (KeybindManager.shouldBlockVanilla(window, key)) {
               ci.cancel();
            }
         }
      }
   }
}
