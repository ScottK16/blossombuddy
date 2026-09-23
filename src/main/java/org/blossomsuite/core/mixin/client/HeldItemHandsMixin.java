package org.blossomsuite.core.mixin.client;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.blossomsuite.core.config.FeatureConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Small Hands (shrinks the first-person arm and item) and No Item Movement (no swing, no equip bob). */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemHandsMixin {
   // where the held item normally sits, so the shrink happens in place instead of pulling it to the screen centre
   private static final float PIVOT_X = 0.56F;
   private static final float PIVOT_Y = -0.52F;
   private static final float PIVOT_Z = -0.72F;
   private static boolean suitecore$scaled = false;

   private static boolean suitecore$active() {
      return SuiteConfig.INSTANCE.isEnabledForCurrentWorld();
   }

   @ModifyVariable(method = "renderFirstPersonItem", at = @At("HEAD"), argsOnly = true, ordinal = 2)
   private float suitecore$noSwing(float swingProgress) {
      return suitecore$active() && FeatureConfig.INSTANCE.hands.freezeSwing ? 0.0F : swingProgress;
   }

   @ModifyVariable(method = "renderFirstPersonItem", at = @At("HEAD"), argsOnly = true, ordinal = 3)
   private float suitecore$noEquipBob(float equipProgress) {
      return suitecore$active() && FeatureConfig.INSTANCE.hands.freezeEquip ? 1.0F : equipProgress;
   }

   @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
   private void suitecore$shrinkStart(
      AbstractClientPlayerEntity player,
      float tickProgress,
      float pitch,
      Hand hand,
      float swingProgress,
      ItemStack item,
      float equipProgress,
      MatrixStack matrices,
      VertexConsumerProvider vertexConsumers,
      int light,
      CallbackInfo ci
   ) {
      suitecore$scaled = false;
      FeatureConfig.Hands h = FeatureConfig.INSTANCE.hands;
      if (suitecore$active() && h.smallHands && h.smallHandsScale < 0.999F) {
         Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
         float side = arm == Arm.RIGHT ? 1.0F : -1.0F;
         float s = h.smallHandsScale;
         matrices.push();
         matrices.translate(PIVOT_X * side, PIVOT_Y, PIVOT_Z);
         matrices.scale(s, s, s);
         matrices.translate(-PIVOT_X * side, -PIVOT_Y, -PIVOT_Z);
         suitecore$scaled = true;
      }
   }

   @Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
   private void suitecore$shrinkEnd(
      AbstractClientPlayerEntity player,
      float tickProgress,
      float pitch,
      Hand hand,
      float swingProgress,
      ItemStack item,
      float equipProgress,
      MatrixStack matrices,
      VertexConsumerProvider vertexConsumers,
      int light,
      CallbackInfo ci
   ) {
      if (suitecore$scaled) {
         matrices.pop();
         suitecore$scaled = false;
      }
   }
}
