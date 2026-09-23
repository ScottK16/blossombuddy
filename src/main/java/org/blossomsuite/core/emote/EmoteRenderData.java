package org.blossomsuite.core.emote;

/** Added to the game's player render state (by a mixin) so the pose worked out for a player reaches their model. */
public interface EmoteRenderData {
   EmotePose suitecore$getEmotePose();

   void suitecore$setEmotePose(EmotePose pose);
}
