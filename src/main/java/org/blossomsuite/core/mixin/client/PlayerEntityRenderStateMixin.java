package org.blossomsuite.core.mixin.client;

import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.blossomsuite.core.emote.EmotePose;
import org.blossomsuite.core.emote.EmoteRenderData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Carries the emote pose worked out for a player from where their render state is filled in to where their model is posed. */
@Mixin(PlayerEntityRenderState.class)
public class PlayerEntityRenderStateMixin implements EmoteRenderData {
   @Unique
   private EmotePose suitecore$emotePose;

   @Override
   public EmotePose suitecore$getEmotePose() {
      return this.suitecore$emotePose;
   }

   @Override
   public void suitecore$setEmotePose(EmotePose pose) {
      this.suitecore$emotePose = pose;
   }
}
