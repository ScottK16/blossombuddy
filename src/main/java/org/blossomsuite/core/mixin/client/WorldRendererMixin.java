package org.blossomsuite.core.mixin.client;

import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.util.TargetBlockOutlineColorUtil;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
   @ModifyArg(
      method = "renderTargetBlockOutline",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/WorldRenderer;drawBlockOutline(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/entity/Entity;DDDLnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)V"
      ),
      index = 8
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
