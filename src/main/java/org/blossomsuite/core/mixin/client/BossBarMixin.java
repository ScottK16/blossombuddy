package org.blossomsuite.core.mixin.client;

import net.minecraft.entity.boss.BossBar;
import net.minecraft.text.Text;
import org.blossomsuite.core.jobs.overflow.OverflowTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets the overflow module swap the title of a maxed-out Jobs boss bar. Based on Jobs Overflow XP (MIT, Mills). */
@Mixin(BossBar.class)
public abstract class BossBarMixin {
   @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
   private void suitecore$overflowName(CallbackInfoReturnable<Text> cir) {
      Text override = OverflowTracker.INSTANCE.overrideName(((BossBar)(Object)this).getUuid());
      if (override != null) {
         cir.setReturnValue(override);
      }
   }
}
