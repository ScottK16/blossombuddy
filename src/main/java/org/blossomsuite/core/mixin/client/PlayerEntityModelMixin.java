package org.blossomsuite.core.mixin.client;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.blossomsuite.core.emote.EmotePose;
import org.blossomsuite.core.emote.EmoteRenderData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityModel.class)
public class PlayerEntityModelMixin {
   /**
    * After the game has posed the model, an emote turns the head, arms and legs into the emote's pose. The outer skin layers (hat,
    * jacket, sleeves, pants) are children of those parts in this version of the game, so they follow on their own; copying the
    * movement onto them as well would turn them twice.
    */
   @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V", at = @At("TAIL"))
   private void suitecore$applyEmote(PlayerEntityRenderState state, CallbackInfo ci) {
      EmotePose pose = ((EmoteRenderData)(Object)state).suitecore$getEmotePose();
      if (pose == null) {
         return;
      }

      PlayerEntityModel model = (PlayerEntityModel)(Object)this;
      if (pose.head != null) {
         model.head.pitch += pose.head[0];
         model.head.yaw += pose.head[1];
         model.head.roll += pose.head[2];
      }

      if (pose.torso != null) {
         model.body.pitch += pose.torso[0];
         model.body.yaw += pose.torso[1];
         model.body.roll += pose.torso[2];
      }

      set(model.rightArm, pose.rightArm);
      set(model.leftArm, pose.leftArm);
      set(model.rightLeg, pose.rightLeg);
      set(model.leftLeg, pose.leftLeg);
   }

   private static void set(ModelPart part, float[] rotation) {
      if (rotation != null) {
         part.pitch = rotation[0];
         part.yaw = rotation[1];
         part.roll = rotation[2];
      }
   }
}
