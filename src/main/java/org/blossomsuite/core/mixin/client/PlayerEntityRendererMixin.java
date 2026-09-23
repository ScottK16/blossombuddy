package org.blossomsuite.core.mixin.client;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.blossomsuite.core.emote.EmotePose;
import org.blossomsuite.core.emote.EmoteRenderData;
import org.blossomsuite.core.emote.EmoteState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererMixin {
   /** Works out whether this player is in the middle of an emote, and how their body is turned right now. */
   @Inject(method = "updateRenderState(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V", at = @At("TAIL"))
   private void suitecore$emotePose(AbstractClientPlayerEntity player, PlayerEntityRenderState state, float tickDelta, CallbackInfo ci) {
      EmotePose pose = EmoteState.INSTANCE.poseFor(player.getUuid(), System.currentTimeMillis());
      ((EmoteRenderData)(Object)state).suitecore$setEmotePose(pose);
      if (pose != null) {
         state.bodyYaw += pose.bodyYawDegrees; // spins
         state.y += pose.yOffset; // hops and sitting
         if (pose.prone > 0.0F) {
            state.leaningPitch = pose.prone; // the game's own tilt for a swimming player: face down
         }
      }
   }

   /** The shift the game adds when it lays a swimming player down, eased in along with the tilt. */
   @Inject(method = "setupTransforms(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;FF)V", at = @At("TAIL"))
   private void suitecore$lieDown(PlayerEntityRenderState state, MatrixStack matrices, float bodyYaw, float baseHeight, CallbackInfo ci) {
      EmotePose pose = ((EmoteRenderData)(Object)state).suitecore$getEmotePose();
      if (pose != null && pose.prone > 0.0F) {
         matrices.translate(0.0F, -1.0F * pose.prone, 0.3F * pose.prone);
      }
   }
}
