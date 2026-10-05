package org.blossomsuite.core.mixin.client;

import org.blossomsuite.core.keybinds.KeybindManager;
import org.blossomsuite.core.qol.mining.MiningResumeGuard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseMixin {
   @Inject(method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V", at = @At("HEAD"), cancellable = true)
   private void suitecore$maybeConsumeMouseButton(long window, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
      int button = buttonInfo.button();
      MiningResumeGuard.onMouseButton(button, action);
      Minecraft mc = Minecraft.getInstance();
      if (mc != null && mc.screen == null) {
         if (action == 1) {
            if (KeybindManager.shouldBlockVanillaMouse(window, button)) {
               ci.cancel();
            }
         }
      }
   }
}
