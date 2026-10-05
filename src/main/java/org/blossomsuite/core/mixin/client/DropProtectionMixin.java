package org.blossomsuite.core.mixin.client;

import net.minecraft.client.player.LocalPlayer;
import org.blossomsuite.core.qol.locks.SlotProtection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Q / Ctrl+Q drop key: does nothing while the selected slot is locked. */
@Mixin(LocalPlayer.class)
public abstract class DropProtectionMixin {
   @Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true)
   private void suitecore$lockedSlotDrop(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
      LocalPlayer self = (LocalPlayer)(Object)this;
      if (SlotProtection.blocksDrop(self)) {
         SlotProtection.notifyBlocked(self);
         cir.setReturnValue(false);
      }
   }
}
