package org.blossomsuite.core.mixin.client;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.ItemInHandRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.InteractionHand;
import org.blossomsuite.core.config.FeatureConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Small Hands (shrinks the first-person arm and item) and No Item Movement (no swing, no equip bob). */
@Mixin(ItemInHandRenderer.class)
public abstract class HeldItemHandsMixin {
   // where the held item normally sits, so the shrink happens in place instead of pulling it to the screen centre
   private static final float PIVOT_X = 0.56F;
   private static final float PIVOT_Y = -0.52F;
   private static final float PIVOT_Z = -0.72F;
   private static boolean suitecore$scaled = false;

   private static boolean suitecore$active() {
      return SuiteConfig.INSTANCE.isEnabledForCurrentWorld();
   }

   @ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true, ordinal = 2)
   private float suitecore$noSwing(float swingProgress) {
      return suitecore$active() && FeatureConfig.INSTANCE.hands.freezeSwing ? 0.0F : swingProgress;
   }

   @ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true, ordinal = 3)
   private float suitecore$noEquipBob(float equipProgress) {
      return suitecore$active() && FeatureConfig.INSTANCE.hands.freezeEquip ? 1.0F : equipProgress;
   }

   @Inject(method = "renderArmWithItem", at = @At("HEAD"))
   private void suitecore$shrinkStart(
      AbstractClientPlayer player,
      float tickProgress,
      float pitch,
      InteractionHand hand,
      float swingProgress,
      ItemStack item,
      float equipProgress,
      PoseStack matrices,
      SubmitNodeCollector vertexConsumers,
      int light,
      CallbackInfo ci
   ) {
      suitecore$scaled = false;
      FeatureConfig.Hands h = FeatureConfig.INSTANCE.hands;
      if (suitecore$active() && h.smallHands && h.smallHandsScale < 0.999F) {
         HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
         float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
         float s = h.smallHandsScale;
         matrices.pushPose();
         matrices.translate(PIVOT_X * side, PIVOT_Y, PIVOT_Z);
         matrices.scale(s, s, s);
         matrices.translate(-PIVOT_X * side, -PIVOT_Y, -PIVOT_Z);
         suitecore$scaled = true;
      }
   }

   @Inject(method = "renderArmWithItem", at = @At("RETURN"))
   private void suitecore$shrinkEnd(
      AbstractClientPlayer player,
      float tickProgress,
      float pitch,
      InteractionHand hand,
      float swingProgress,
      ItemStack item,
      float equipProgress,
      PoseStack matrices,
      SubmitNodeCollector vertexConsumers,
      int light,
      CallbackInfo ci
   ) {
      if (suitecore$scaled) {
         matrices.popPose();
         suitecore$scaled = false;
      }
   }
}
