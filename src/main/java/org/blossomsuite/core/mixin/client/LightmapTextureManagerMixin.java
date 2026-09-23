package org.blossomsuite.core.mixin.client;

import net.minecraft.client.render.LightmapTextureManager;
import org.blossomsuite.core.config.FeatureConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Full Bright: {@code pack(block, sky)} is the one place every rendered vertex's lightmap coordinate gets built
 * (block/fluid/entity/particle rendering all funnel through it), so forcing both inputs to their max here makes
 * everything sample the brightest texel of the lightmap texture the game already generates - lava, water and dark
 * caves included - without needing to touch how that texture itself is built.
 */
@Mixin(LightmapTextureManager.class)
public abstract class LightmapTextureManagerMixin {
   @ModifyVariable(method = "pack", at = @At("HEAD"), argsOnly = true, ordinal = 0)
   private static int suitecore$fullBrightBlock(int block) {
      return suitecore$active() ? 15 : block;
   }

   @ModifyVariable(method = "pack", at = @At("HEAD"), argsOnly = true, ordinal = 1)
   private static int suitecore$fullBrightSky(int sky) {
      return suitecore$active() ? 15 : sky;
   }

   private static boolean suitecore$active() {
      return SuiteConfig.INSTANCE.isEnabledForCurrentWorld() && FeatureConfig.INSTANCE.render.fullBright;
   }
}
