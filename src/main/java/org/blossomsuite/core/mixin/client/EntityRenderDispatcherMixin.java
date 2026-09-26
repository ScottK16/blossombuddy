package org.blossomsuite.core.mixin.client;

import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.visibility.PlayerVisibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hide other players: asking "should this entity be drawn?" is where the game decides, and it also drops their name tags. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
   @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
   private void suitecore$hideOtherPlayers(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
      if (SuiteConfig.INSTANCE.isEnabledForCurrentWorld() && PlayerVisibility.shouldSkip(entity)) {
         cir.setReturnValue(false);
      }
   }
}
