package org.blossomsuite.core.mixin.client;

import net.minecraft.world.BossEvent;
import net.minecraft.network.chat.Component;
import org.blossomsuite.core.jobs.overflow.OverflowTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets the overflow module swap the title of a maxed-out Jobs boss bar. Based on Jobs Overflow XP (MIT, Mills). */
@Mixin(BossEvent.class)
public abstract class BossBarMixin {
   @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
   private void suitecore$overflowName(CallbackInfoReturnable<Component> cir) {
      Component override = OverflowTracker.INSTANCE.overrideName(((BossEvent)(Object)this).getId());
      if (override != null) {
         cir.setReturnValue(override);
      }
   }
}
