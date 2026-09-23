package org.blossomsuite.core.emote;

/**
 * The movements. Each emote is a small function of time that says how the arms, legs and head are turned (and sometimes the whole body),
 * eased in over the first third of a second and out over the last, so the character glides into the pose from wherever they were
 * standing. The numbers were chosen by hand; if a movement looks wrong in the game, this is the one place to change it.
 *
 * <p>Angles are in radians. For the arms, a negative pitch lifts the arm forward and up; a positive roll swings the right arm out to
 * the side (and a negative roll the left arm). For the legs, a negative pitch lifts the leg forward.
 */
public final class EmoteAnimator {
   private static final float EASE_SECONDS = 0.35F;
   private static final float[] RIGHT_ARM_REST = {0.0F, 0.0F, 0.05F};
   private static final float[] LEFT_ARM_REST = {0.0F, 0.0F, -0.05F};
   private static final float[] LEG_REST = {0.0F, 0.0F, 0.0F};
   private static final float PI = (float)Math.PI;

   private EmoteAnimator() {
   }

   /** The pose {@code t} seconds into the emote, or null when it has not started or is over. */
   public static EmotePose pose(Emote emote, float t) {
      if (emote == null || t < 0.0F || t >= emote.durationSeconds()) {
         return null;
      }

      float k = envelope(t, emote.durationSeconds());
      return switch (emote.id()) {
         case "wave" -> wave(t, k);
         case "dance" -> dance(t, k);
         case "cheer" -> cheer(t, k);
         case "clap" -> clap(t, k);
         case "floss" -> floss(t, k);
         case "robot" -> robot(t, k);
         case "spin" -> spin(t, k, emote.durationSeconds());
         case "headbang" -> headbang(t, k);
         case "chicken" -> chicken(t, k);
         case "jacks" -> jacks(t, k);
         case "disco" -> disco(t, k);
         case "salute" -> salute(k);
         case "shrug" -> shrug(t, k);
         case "feetup" -> feetUp(t, k);
         case "twerk" -> twerk(t, k);
         case "dab" -> dab(k);
         case "tpose" -> tPose(t, k);
         case "zombie" -> zombie(t, k);
         case "sprinkler" -> sprinkler(t, k);
         case "march" -> march(t, k);
         case "kickback" -> kickback(t, emote.durationSeconds());
         case "wiggle" -> wiggle(t, k);
         default -> null;
      };
   }

   /** 0 at the very start and end, 1 through the middle. */
   static float envelope(float t, float duration) {
      return Math.min(smooth(t / EASE_SECONDS), smooth((duration - t) / EASE_SECONDS));
   }

   private static float smooth(float x) {
      float c = Math.max(0.0F, Math.min(1.0F, x));
      return c * c * (3.0F - 2.0F * c);
   }

   /** From {@code rest} (k = 0) to {@code target} (k = 1). */
   private static float[] blend(float[] rest, float[] target, float k) {
      return new float[]{rest[0] + (target[0] - rest[0]) * k, rest[1] + (target[1] - rest[1]) * k, rest[2] + (target[2] - rest[2]) * k};
   }

   private static float[] scaled(float[] v, float k) {
      return new float[]{v[0] * k, v[1] * k, v[2] * k};
   }

   private static float sin(float x) {
      return (float)Math.sin(x);
   }

   private static float cos(float x) {
      return (float)Math.cos(x);
   }

   /**
    * Steps through {@code frames} one after another, {@code stepSeconds} each, gliding from one to the next in the first {@code snap}
    * fraction of each step (a small snap makes it sharp and mechanical).
    */
   private static float[] cycle(float[][] frames, float t, float stepSeconds, float snap) {
      int n = frames.length;
      int step = (int)Math.floor(t / stepSeconds);
      float fraction = t / stepSeconds - step;
      float[] from = frames[Math.floorMod(step - 1, n)];
      float[] to = frames[Math.floorMod(step, n)];
      return blend(from, to, smooth(fraction / snap));
   }

   /** The right arm up and out, waving side to side. */
   private static EmotePose wave(float t, float k) {
      float[] right = blend(RIGHT_ARM_REST, new float[]{0.0F, 0.0F, 2.6F + 0.35F * sin(t * 10.0F)}, k);
      float[] head = scaled(new float[]{0.0F, 0.2F * sin(t * 2.0F), 0.0F}, k);
      return new EmotePose(head, right, null, null, null);
   }

   /** Both arms up and swaying, feet stepping, head bobbing. */
   private static EmotePose dance(float t, float k) {
      float w = 7.0F;
      float s = sin(t * w);
      float[] right = blend(RIGHT_ARM_REST, new float[]{-2.5F + 0.5F * s, 0.0F, 0.5F + 0.3F * s}, k);
      float[] left = blend(LEFT_ARM_REST, new float[]{-2.5F - 0.5F * s, 0.0F, -0.5F - 0.3F * s}, k);
      float[] rightLeg = blend(LEG_REST, new float[]{0.5F * s, 0.0F, 0.0F}, k);
      float[] leftLeg = blend(LEG_REST, new float[]{-0.5F * s, 0.0F, 0.0F}, k);
      float[] head = scaled(new float[]{0.12F * sin(t * w * 2.0F), 0.0F, 0.15F * s}, k);
      return new EmotePose(head, right, left, rightLeg, leftLeg);
   }

   /** Both arms straight up, pumping. */
   private static EmotePose cheer(float t, float k) {
      float s = sin(t * 9.0F);
      float[] right = blend(RIGHT_ARM_REST, new float[]{-3.0F + 0.3F * s, 0.0F, 0.25F}, k);
      float[] left = blend(LEFT_ARM_REST, new float[]{-3.0F - 0.3F * s, 0.0F, -0.25F}, k);
      float[] head = scaled(new float[]{-0.25F, 0.0F, 0.0F}, k);
      return new EmotePose(head, right, left, null, null);
   }

   /** Both arms held out in front, swinging together and apart. */
   private static EmotePose clap(float t, float k) {
      float swing = sin(t * 13.0F);
      float[] right = blend(RIGHT_ARM_REST, new float[]{-1.45F, -0.45F + 0.4F * swing, 0.0F}, k);
      float[] left = blend(LEFT_ARM_REST, new float[]{-1.45F, 0.45F - 0.4F * swing, 0.0F}, k);
      float[] head = scaled(new float[]{0.1F * sin(t * 6.5F), 0.0F, 0.0F}, k);
      return new EmotePose(head, right, left, null, null);
   }

   /** Arms swept across the front from side to side while the hips swing the other way. */
   private static EmotePose floss(float t, float k) {
      float w = 8.0F;
      float s = sin(t * w);
      float[] right = blend(RIGHT_ARM_REST, new float[]{-0.9F, 0.9F * s, 0.0F}, k);
      float[] left = blend(LEFT_ARM_REST, new float[]{-0.9F, 0.9F * s, 0.0F}, k);
      float[] rightLeg = blend(LEG_REST, new float[]{0.0F, 0.0F, 0.2F * s}, k);
      float[] leftLeg = blend(LEG_REST, new float[]{0.0F, 0.0F, 0.2F * s}, k);
      float[] head = scaled(new float[]{0.0F, 0.0F, -0.1F * s}, k);
      return new EmotePose(head, right, left, rightLeg, leftLeg).withBody(-14.0F * s * k, 0.0F);
   }

   /** Stiff, sharp poses one after another, like a robot. */
   private static EmotePose robot(float t, float k) {
      float[][] right = {{-1.57F, 0.0F, 0.0F}, {-1.57F, 0.0F, 0.9F}, {-0.3F, 0.0F, 0.2F}, {-2.4F, 0.0F, 0.4F}};
      float[][] left = {{-0.3F, 0.0F, -0.2F}, {-1.57F, 0.0F, -0.9F}, {-1.57F, 0.0F, 0.0F}, {-2.4F, 0.0F, -0.4F}};
      float[][] head = {{0.0F, 0.0F, 0.0F}, {0.0F, 0.5F, 0.0F}, {0.0F, -0.5F, 0.0F}, {0.0F, 0.0F, 0.0F}};
      return new EmotePose(
         scaled(cycle(head, t, 0.5F, 0.2F), k),
         blend(RIGHT_ARM_REST, cycle(right, t, 0.5F, 0.2F), k),
         blend(LEFT_ARM_REST, cycle(left, t, 0.5F, 0.2F), k),
         null,
         null
      );
   }

   /** Arms out to the sides while the whole character spins round twice. */
   private static EmotePose spin(float t, float k, float duration) {
      float[] right = blend(RIGHT_ARM_REST, new float[]{0.0F, 0.0F, 1.5F}, k);
      float[] left = blend(LEFT_ARM_REST, new float[]{0.0F, 0.0F, -1.5F}, k);
      float turns = 2.0F * smooth(t / duration);
      return new EmotePose(null, right, left, null, null).withBody(360.0F * turns, 0.0F);
   }

   /** The head thrown up and down in time, fists pumping, a little hop on each beat. */
   private static EmotePose headbang(float t, float k) {
      float s = sin(t * 10.0F);
      float[] head = scaled(new float[]{0.7F * s, 0.0F, 0.0F}, k);
      float[] right = blend(RIGHT_ARM_REST, new float[]{-0.6F + 0.3F * s, 0.0F, 0.3F}, k);
      float[] left = blend(LEFT_ARM_REST, new float[]{-0.6F + 0.3F * s, 0.0F, -0.3F}, k);
      return new EmotePose(head, right, left, null, null).withBody(0.0F, 0.06F * Math.abs(s) * k);
   }

   /** Elbows out, arms flapping like wings, feet pecking. */
   private static EmotePose chicken(float t, float k) {
      float flap = 0.5F + 0.5F * sin(t * 12.0F);
      float[] right = blend(RIGHT_ARM_REST, new float[]{0.0F, 0.0F, 0.5F + 1.4F * flap}, k);
      float[] left = blend(LEFT_ARM_REST, new float[]{0.0F, 0.0F, -0.5F - 1.4F * flap}, k);
      float[] rightLeg = blend(LEG_REST, new float[]{0.35F * sin(t * 6.0F), 0.0F, 0.0F}, k);
      float[] leftLeg = blend(LEG_REST, new float[]{-0.35F * sin(t * 6.0F), 0.0F, 0.0F}, k);
      float[] head = scaled(new float[]{0.15F * sin(t * 6.0F), 0.0F, 0.0F}, k);
      return new EmotePose(head, right, left, rightLeg, leftLeg).withBody(0.0F, 0.05F * flap * k);
   }

   /** Jumping jacks: arms up over the head and feet apart, then back, with a hop. */
   private static EmotePose jacks(float t, float k) {
      float open = 0.5F - 0.5F * cos(t * 6.0F);
      float[] right = blend(RIGHT_ARM_REST, new float[]{0.0F, 0.0F, 0.05F + 3.0F * open}, k);
      float[] left = blend(LEFT_ARM_REST, new float[]{0.0F, 0.0F, -0.05F - 3.0F * open}, k);
      float[] rightLeg = blend(LEG_REST, new float[]{0.0F, 0.0F, 0.35F * open}, k);
      float[] leftLeg = blend(LEG_REST, new float[]{0.0F, 0.0F, -0.35F * open}, k);
      return new EmotePose(null, right, left, rightLeg, leftLeg).withBody(0.0F, 0.15F * open * k);
   }

   /** One arm pointing up at the ceiling, then the other, hips swaying. */
   private static EmotePose disco(float t, float k) {
      float[][] right = {{-2.9F, 0.0F, 0.6F}, {0.0F, 0.0F, 0.05F}};
      float[][] left = {{0.0F, 0.0F, -0.05F}, {-2.9F, 0.0F, -0.6F}};
      float sway = sin(t * PI / 0.9F);
      float[] rightLeg = blend(LEG_REST, new float[]{0.3F * sway, 0.0F, 0.0F}, k);
      float[] leftLeg = blend(LEG_REST, new float[]{-0.3F * sway, 0.0F, 0.0F}, k);
      float[] head = scaled(new float[]{0.0F, 0.0F, 0.15F * sway}, k);
      return new EmotePose(head, blend(RIGHT_ARM_REST, cycle(right, t, 0.9F, 0.35F), k), blend(LEFT_ARM_REST, cycle(left, t, 0.9F, 0.35F), k), rightLeg, leftLeg);
   }

   /** The right hand to the forehead, held. */
   private static EmotePose salute(float k) {
      float[] right = blend(RIGHT_ARM_REST, new float[]{-2.5F, 0.0F, 0.35F}, k);
      return new EmotePose(null, right, null, null, null);
   }

   /** Both arms out to the sides, a little bounce, head tilted. */
   private static EmotePose shrug(float t, float k) {
      float s = sin(t * 8.0F);
      float[] right = blend(RIGHT_ARM_REST, new float[]{-0.4F, 0.0F, 0.6F + 0.08F * s}, k);
      float[] left = blend(LEFT_ARM_REST, new float[]{-0.4F, 0.0F, -0.6F - 0.08F * s}, k);
      float[] head = scaled(new float[]{0.0F, 0.0F, 0.18F}, k);
      return new EmotePose(head, right, left, null, null).withBody(0.0F, 0.03F * Math.abs(s) * k);
   }

   /**
    * Sitting on the ground with both legs raised up in the air in front, feet toward whoever the character is facing. The character is
    * lowered until the hips are near the ground, the legs are lifted up and forward and bob a little, and the arms are propped behind.
    */
   private static EmotePose feetUp(float t, float k) {
      float bob = sin(t * 5.0F);
      float[] rightLeg = blend(LEG_REST, new float[]{-2.2F + 0.14F * bob, 0.0F, 0.1F}, k);
      float[] leftLeg = blend(LEG_REST, new float[]{-2.2F - 0.14F * bob, 0.0F, -0.1F}, k);
      float[] right = blend(RIGHT_ARM_REST, new float[]{0.55F, 0.0F, 0.5F}, k);
      float[] left = blend(LEFT_ARM_REST, new float[]{0.55F, 0.0F, -0.5F}, k);
      float[] head = scaled(new float[]{-0.12F + 0.05F * sin(t * 3.0F), 0.0F, 0.0F}, k);
      return new EmotePose(head, right, left, rightLeg, leftLeg).withBody(0.0F, -0.68F * k);
   }

   /** A goofy squat with the feet apart and the hips shaking side to side. */
   private static EmotePose twerk(float t, float k) {
      float s = sin(t * 18.0F);
      float[] rightLeg = blend(LEG_REST, new float[]{0.2F * s, 0.0F, 0.32F}, k);
      float[] leftLeg = blend(LEG_REST, new float[]{-0.2F * s, 0.0F, -0.32F}, k);
      float[] right = blend(RIGHT_ARM_REST, new float[]{-0.5F, 0.0F, 0.15F}, k);
      float[] left = blend(LEFT_ARM_REST, new float[]{-0.5F, 0.0F, -0.15F}, k);
      float[] head = scaled(new float[]{0.15F, 0.0F, 0.0F}, k);
      return new EmotePose(head, right, left, rightLeg, leftLeg).withBody(22.0F * s * k, (-0.3F + 0.03F * Math.abs(s)) * k);
   }

   /** One arm out and up, the other folded across the face, head dropped into it. */
   private static EmotePose dab(float k) {
      float[] right = blend(RIGHT_ARM_REST, new float[]{-0.4F, 0.0F, 2.3F}, k);
      float[] left = blend(LEFT_ARM_REST, new float[]{-1.4F, 1.1F, -0.2F}, k);
      float[] head = scaled(new float[]{0.55F, 0.35F, 0.0F}, k);
      return new EmotePose(head, right, left, null, null);
   }

   /** Arms straight out to the sides, perfectly stiff, with the tiniest tremble. */
   private static EmotePose tPose(float t, float k) {
      float tremble = 0.02F * sin(t * 20.0F);
      float[] right = blend(RIGHT_ARM_REST, new float[]{0.0F, 0.0F, 1.57F + tremble}, k);
      float[] left = blend(LEFT_ARM_REST, new float[]{0.0F, 0.0F, -1.57F - tremble}, k);
      return new EmotePose(null, right, left, null, null);
   }

   /** Arms stretched out in front, a slow shuffle, head lolling. */
   private static EmotePose zombie(float t, float k) {
      float[] right = blend(RIGHT_ARM_REST, new float[]{-1.57F, 0.0F, 0.1F}, k);
      float[] left = blend(LEFT_ARM_REST, new float[]{-1.57F, 0.0F, -0.1F}, k);
      float[] rightLeg = blend(LEG_REST, new float[]{0.35F * sin(t * 4.0F), 0.0F, 0.0F}, k);
      float[] leftLeg = blend(LEG_REST, new float[]{-0.35F * sin(t * 4.0F), 0.0F, 0.0F}, k);
      float[] head = scaled(new float[]{0.1F, 0.0F, 0.2F + 0.1F * sin(t * 2.0F)}, k);
      return new EmotePose(head, right, left, rightLeg, leftLeg).withBody(0.0F, 0.02F * Math.abs(sin(t * 4.0F)) * k);
   }

   /** One arm held out in front and jerked round in little steps, the other hand behind the head. */
   private static EmotePose sprinkler(float t, float k) {
      float[][] sweep = {{-1.5F, -0.7F, 0.0F}, {-1.5F, -0.35F, 0.0F}, {-1.5F, 0.0F, 0.0F}, {-1.5F, 0.35F, 0.0F}, {-1.5F, 0.7F, 0.0F}, {-1.5F, 0.35F, 0.0F}, {-1.5F, 0.0F, 0.0F}, {-1.5F, -0.35F, 0.0F}};
      float[] right = blend(RIGHT_ARM_REST, cycle(sweep, t, 0.22F, 0.3F), k);
      float[] left = blend(LEFT_ARM_REST, new float[]{-2.9F, 0.0F, -0.5F}, k);
      return new EmotePose(null, right, left, null, null);
   }

   /** Knees high, arms swinging the opposite way, like a parade. */
   private static EmotePose march(float t, float k) {
      float s = sin(t * 5.0F);
      float[] rightLeg = blend(LEG_REST, new float[]{-1.1F * Math.max(0.0F, s), 0.0F, 0.0F}, k);
      float[] leftLeg = blend(LEG_REST, new float[]{-1.1F * Math.max(0.0F, -s), 0.0F, 0.0F}, k);
      float[] right = blend(RIGHT_ARM_REST, new float[]{0.8F * s, 0.0F, 0.05F}, k);
      float[] left = blend(LEFT_ARM_REST, new float[]{-0.8F * s, 0.0F, -0.05F}, k);
      return new EmotePose(null, right, left, rightLeg, leftLeg).withBody(0.0F, 0.03F * Math.abs(s) * k);
   }

   /**
    * Lying face down on the ground with the hands up by the chin, kicking the feet up behind, one after the other. The character is laid
    * down slowly (the game's own swimming tilt does the turning; see PlayerEntityRendererMixin), so this eases in over most of a second.
    */
   private static EmotePose kickback(float t, float duration) {
      float prone = Math.min(smooth(t / 0.8F), smooth((duration - t) / 0.8F));
      float s = sin(t * 7.0F);
      float[] rightLeg = blend(LEG_REST, new float[]{0.9F + 0.7F * s, 0.0F, 0.05F}, prone);
      float[] leftLeg = blend(LEG_REST, new float[]{0.9F - 0.7F * s, 0.0F, -0.05F}, prone);
      float[] right = blend(RIGHT_ARM_REST, new float[]{-2.7F, 0.0F, -0.1F}, prone);
      float[] left = blend(LEFT_ARM_REST, new float[]{-2.7F, 0.0F, 0.1F}, prone);
      float[] head = scaled(new float[]{-1.0F + 0.08F * sin(t * 3.0F), 0.0F, 0.0F}, prone);
      return new EmotePose(head, right, left, rightLeg, leftLeg).withProne(prone);
   }

   /** Hands out to the sides and the waist swinging round in circles, the head staying facing front. */
   private static EmotePose wiggle(float t, float k) {
      float w = 9.0F;
      float s = sin(t * w);
      float c = sin(t * w + PI / 2.0F);
      float[] torso = scaled(new float[]{0.0F, 0.4F * c, 0.16F * s}, k);
      float[] head = scaled(new float[]{0.0F, -0.4F * c, -0.12F * s}, k);
      float[] right = blend(RIGHT_ARM_REST, new float[]{-0.4F, 0.0F, 0.85F}, k);
      float[] left = blend(LEFT_ARM_REST, new float[]{-0.4F, 0.0F, -0.85F}, k);
      float[] rightLeg = blend(LEG_REST, new float[]{0.0F, 0.0F, 0.14F * s}, k);
      float[] leftLeg = blend(LEG_REST, new float[]{0.0F, 0.0F, 0.14F * s}, k);
      return new EmotePose(head, right, left, rightLeg, leftLeg).withTorso(torso);
   }
}
