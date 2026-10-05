package org.blossomsuite.core.mixin.client;

import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.util.TargetBlockOutlineColorUtil;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LevelRenderer.class)
public class WorldRendererMixin {
   @ModifyArg(
      method = "renderBlockOutline",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/LevelRenderer;renderHitOutline(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;DDDLnet/minecraft/client/renderer/state/level/BlockOutlineRenderState;IF)V",
         ordinal = 1 // the second call is the outline itself; the first is the black backing drawn only in high-contrast mode
      ),
      index = 6
   )
   private int suitecore$targetBlockOutlineColor(int originalColor) {
      SuiteConfig cfg = SuiteConfig.INSTANCE;
      if (cfg == null || cfg.QolConfig == null) {
         return originalColor;
      } else if (!cfg.isEnabledForCurrentWorld()) {
         return originalColor;
      } else {
         return !cfg.QolConfig.targetBlockOutlineEnabled ? originalColor : TargetBlockOutlineColorUtil.computeArgb(cfg.QolConfig, System.currentTimeMillis());
      }
   }
}
