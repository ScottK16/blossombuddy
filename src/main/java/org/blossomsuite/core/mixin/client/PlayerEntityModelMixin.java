package org.blossomsuite.core.mixin.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.blossomsuite.core.emote.EmotePose;
import org.blossomsuite.core.emote.EmoteRenderData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public class PlayerEntityModelMixin {
   /**
    * After the game has posed the model, an emote turns the head, arms and legs into the emote's pose. The outer skin layers (hat,
    * jacket, sleeves, pants) are children of those parts in this version of the game, so they follow on their own; copying the
    * movement onto them as well would turn them twice.
    */
   @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
   private void suitecore$applyEmote(AvatarRenderState state, CallbackInfo ci) {
      EmotePose pose = ((EmoteRenderData)(Object)state).suitecore$getEmotePose();
      if (pose == null) {
         return;
      }

      PlayerModel model = (PlayerModel)(Object)this;
      if (pose.head != null) {
         model.head.xRot += pose.head[0];
         model.head.yRot += pose.head[1];
         model.head.zRot += pose.head[2];
      }

      if (pose.torso != null) {
         model.body.xRot += pose.torso[0];
         model.body.yRot += pose.torso[1];
         model.body.zRot += pose.torso[2];
      }

      set(model.rightArm, pose.rightArm);
      set(model.leftArm, pose.leftArm);
      set(model.rightLeg, pose.rightLeg);
      set(model.leftLeg, pose.leftLeg);
   }

   private static void set(ModelPart part, float[] rotation) {
      if (rotation != null) {
         part.xRot = rotation[0];
         part.yRot = rotation[1];
         part.zRot = rotation[2];
      }
   }
}
