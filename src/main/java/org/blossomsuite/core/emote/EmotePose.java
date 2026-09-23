package org.blossomsuite.core.emote;

/**
 * How a player's model is posed at one moment of an emote. Each part is {pitch, yaw, roll} in radians, or null to leave that part as the
 * game drew it. The head and torso are ADDED to the game's rotation (so the head keeps following where the player looks); the arms and legs
 * REPLACE it. The outer skin layers (sleeves, jacket, pants, hat) are attached to these parts by the game and follow them on their own.
 *
 * <p>{@code bodyYawDegrees} turns the whole character around (added to the way they are facing), {@code yOffset} lifts or lowers them in
 * blocks (negative = lower), and {@code prone} (0 to 1) lays them face down on the ground, the way the game does for a swimming player.
 */
public final class EmotePose {
   public final float[] head;
   public final float[] rightArm;
   public final float[] leftArm;
   public final float[] rightLeg;
   public final float[] leftLeg;
   public final float bodyYawDegrees;
   public final float yOffset;
   /** Extra turn of the torso on its own, added to the game's (the arms and legs do not follow it). Null for none. */
   public final float[] torso;
   public final float prone;

   public EmotePose(float[] head, float[] rightArm, float[] leftArm, float[] rightLeg, float[] leftLeg) {
      this(head, rightArm, leftArm, rightLeg, leftLeg, 0.0F, 0.0F, null, 0.0F);
   }

   private EmotePose(float[] head, float[] rightArm, float[] leftArm, float[] rightLeg, float[] leftLeg, float bodyYawDegrees, float yOffset, float[] torso, float prone) {
      this.head = head;
      this.rightArm = rightArm;
      this.leftArm = leftArm;
      this.rightLeg = rightLeg;
      this.leftLeg = leftLeg;
      this.bodyYawDegrees = bodyYawDegrees;
      this.yOffset = yOffset;
      this.torso = torso;
      this.prone = prone;
   }

   /** The same pose, also turning the whole character and lifting or lowering them. */
   public EmotePose withBody(float bodyYawDegrees, float yOffset) {
      return new EmotePose(this.head, this.rightArm, this.leftArm, this.rightLeg, this.leftLeg, bodyYawDegrees, yOffset, this.torso, this.prone);
   }

   /** The same pose, also turning the torso by itself. */
   public EmotePose withTorso(float[] torso) {
      return new EmotePose(this.head, this.rightArm, this.leftArm, this.rightLeg, this.leftLeg, this.bodyYawDegrees, this.yOffset, torso, this.prone);
   }

   /** The same pose, also laying the character down face first (0 = standing, 1 = lying flat). */
   public EmotePose withProne(float prone) {
      return new EmotePose(this.head, this.rightArm, this.leftArm, this.rightLeg, this.leftLeg, this.bodyYawDegrees, this.yOffset, this.torso, prone);
   }
}
