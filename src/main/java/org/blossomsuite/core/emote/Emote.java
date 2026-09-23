package org.blossomsuite.core.emote;

import java.util.List;
import java.util.Locale;

/** One emote: what it is called, and how long it plays. The movement itself is in {@link EmoteAnimator}. */
public record Emote(String id, String label, float durationSeconds) {
   public static final Emote WAVE = new Emote("wave", "Wave", 4.0F);
   public static final Emote DANCE = new Emote("dance", "Dance", 8.0F);
   public static final Emote CHEER = new Emote("cheer", "Cheer", 5.0F);
   public static final Emote CLAP = new Emote("clap", "Clap", 5.0F);
   public static final Emote FLOSS = new Emote("floss", "Floss", 8.0F);
   public static final Emote ROBOT = new Emote("robot", "Robot", 8.0F);
   public static final Emote SPIN = new Emote("spin", "Spin", 4.0F);
   public static final Emote HEADBANG = new Emote("headbang", "Headbang", 6.0F);
   public static final Emote CHICKEN = new Emote("chicken", "Chicken", 7.0F);
   public static final Emote JACKS = new Emote("jacks", "Jacks", 6.0F);
   public static final Emote DISCO = new Emote("disco", "Disco", 8.0F);
   public static final Emote SALUTE = new Emote("salute", "Salute", 3.0F);
   public static final Emote SHRUG = new Emote("shrug", "Shrug", 3.0F);
   public static final Emote FEETUP = new Emote("feetup", "Feet Up", 6.0F);
   public static final Emote TWERK = new Emote("twerk", "Twerk", 6.0F);
   public static final Emote DAB = new Emote("dab", "Dab", 4.0F);
   public static final Emote TPOSE = new Emote("tpose", "T-Pose", 5.0F);
   public static final Emote ZOMBIE = new Emote("zombie", "Zombie", 8.0F);
   public static final Emote SPRINKLER = new Emote("sprinkler", "Sprinkler", 7.0F);
   public static final Emote MARCH = new Emote("march", "March", 8.0F);
   public static final Emote KICKBACK = new Emote("kickback", "Kickback", 8.0F);
   public static final Emote WIGGLE = new Emote("wiggle", "Wiggle", 6.0F);

   /**
    * Every emote, in the order they appear on the wheel. The relay has to accept the same ids (DEFAULT_EMOTES in relay/emotes.js, or the
    * EMOTE_IDS setting); a test keeps the two lists from drifting apart.
    */
   public static final List<Emote> ALL = List.of(WAVE, DANCE, CHEER, CLAP, FLOSS, ROBOT, SPIN, HEADBANG, CHICKEN, JACKS, DISCO, SALUTE, SHRUG, FEETUP, TWERK, DAB, TPOSE, ZOMBIE, SPRINKLER, MARCH, KICKBACK, WIGGLE);

   /** The emote with this id (any case), or null. */
   public static Emote byId(String id) {
      if (id == null) {
         return null;
      }

      String key = id.trim().toLowerCase(Locale.ROOT);
      for (Emote e : ALL) {
         if (e.id.equals(key)) {
            return e;
         }
      }

      return null;
   }
}
