package org.blossomsuite.core.mixin.client;

import net.minecraft.world.entity.Avatar;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import org.blossomsuite.core.emote.EmotePose;
import org.blossomsuite.core.emote.EmoteRenderData;
import org.blossomsuite.core.emote.EmoteState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public class PlayerEntityRendererMixin {
   /** Works out whether this player is in the middle of an emote, and how their body is turned right now. */
   @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
   private void suitecore$emotePose(Avatar player, AvatarRenderState state, float tickDelta, CallbackInfo ci) {
      EmotePose pose = EmoteState.INSTANCE.poseFor(player.getUUID(), System.currentTimeMillis());
      ((EmoteRenderData)(Object)state).suitecore$setEmotePose(pose);
      if (pose != null) {
         state.bodyRot += pose.bodyYawDegrees; // spins
         state.y += pose.yOffset; // hops and sitting
         if (pose.prone > 0.0F) {
            state.swimAmount = pose.prone; // the game's own tilt for a swimming player: face down
         }
      }
   }

   /** The shift the game adds when it lays a swimming player down, eased in along with the tilt. */
   @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("TAIL"))
   private void suitecore$lieDown(AvatarRenderState state, PoseStack matrices, float bodyYaw, float baseHeight, CallbackInfo ci) {
      EmotePose pose = ((EmoteRenderData)(Object)state).suitecore$getEmotePose();
      if (pose != null && pose.prone > 0.0F) {
         matrices.translate(0.0F, -1.0F * pose.prone, 0.3F * pose.prone);
      }
   }
}
