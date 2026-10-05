package org.blossomsuite.core.mixin.client;

import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.blossomsuite.core.config.FeatureConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Full bright: the game's own night-vision and brightness settings, pushed to their maximum for the lightmap, so every
 * light level shows fully lit. A visual change only - it doesn't reveal anything hidden, it just brightens what is already there.
 */
@Mixin(Lightmap.class)
public class LightmapTextureManagerMixin {
   @Inject(method = "render", at = @At("HEAD"))
   private void suitecore$fullBright(LightmapRenderState state, CallbackInfo ci) {
      if (SuiteConfig.INSTANCE.isEnabledForCurrentWorld() && FeatureConfig.INSTANCE.render.fullBright) {
         state.needsUpdate = true; // the game only rebuilds the lightmap when something changed
         state.nightVisionEffectIntensity = 1.0F;
         state.nightVisionColor = LightmapRenderStateExtractor.WHITE;
         state.brightness = 1.0F;
         state.darknessEffectScale = 0.0F;
      }
   }
}
