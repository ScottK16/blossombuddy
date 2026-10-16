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
      // the symbol another BlossomBuddy player chose goes beside their name
      net.minecraft.client.Minecraft look = net.minecraft.client.Minecraft.getInstance();
      if (org.blossomsuite.core.config.FeatureConfig.INSTANCE.progress.showOthersLook && state.nameTag != null && look.player != null && !player.getUUID().equals(look.player.getUUID())) {
         org.blossomsuite.core.presence.PresenceClient.Player listed = org.blossomsuite.core.presence.PresenceClient.INSTANCE.lookup(player.getUUID(), player.getName().getString());
         if (listed != null && !listed.glyph().isEmpty()) {
            state.nameTag = state.nameTag.copy().append(" ").append(org.blossomsuite.core.xchat.NameStyle.symbol(listed.glyph(), org.blossomsuite.core.xchat.NameStyle.colorsOf(listed.gradient()), 0xFFFFFF));
         }
      }

      // the song another BlossomBuddy player is sharing goes under their name
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      if (org.blossomsuite.core.config.FeatureConfig.INSTANCE.music.showOthers && mc.player != null && !player.getUUID().equals(mc.player.getUUID())) {
         String song = org.blossomsuite.core.presence.PresenceClient.INSTANCE.playingOf(player.getUUID(), player.getName().getString());
         if (!song.isEmpty()) {
            net.minecraft.network.chat.Component line = net.minecraft.network.chat.Component.literal("\u266A " + song).withStyle(net.minecraft.ChatFormatting.GREEN);
            state.scoreText = state.scoreText == null ? line : state.scoreText.copy().append(" ").append(line);
         }
      }

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

      if (pose != null && pose.flips()) {
         // somersaults and cartwheels turn the whole body about its middle
         matrices.translate(0.0F, 0.9F, 0.0F);
         if (pose.flipPitchDegrees != 0.0F) {
            matrices.mulPose(com.mojang.math.Axis.XP.rotationDegrees(pose.flipPitchDegrees));
         }

         if (pose.flipRollDegrees != 0.0F) {
            matrices.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(pose.flipRollDegrees));
         }

         matrices.translate(0.0F, -0.9F, 0.0F);
      }
   }
}
