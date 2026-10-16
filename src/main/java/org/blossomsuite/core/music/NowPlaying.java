package org.blossomsuite.core.music;

/**
 * What your computer says is playing right now. Titles come from another program, so they are cleaned before they are shown or shared:
 * no control characters, one space between words, and a sensible length.
 */
public record NowPlaying(String title, String artist, boolean playing, String app) {
   public static final NowPlaying NONE = new NowPlaying("", "", false, "");
   public static final int MAX_SHARED = 80;

   public NowPlaying {
      title = clean(title, 120);
      artist = clean(artist, 80);
      app = clean(app, 60);
   }

   public boolean hasTrack() {
      return !this.title.isEmpty();
   }

   /** "Title - Artist". */
   public String display() {
      return this.artist.isEmpty() ? this.title : this.title + " - " + this.artist;
   }

   /** What other players see: the track, only while it is actually playing; empty otherwise. */
   public String shareText() {
      if (!this.hasTrack() || !this.playing) {
         return "";
      }

      String text = this.display();
      return text.length() <= MAX_SHARED ? text : text.substring(0, MAX_SHARED - 1) + "…";
   }

   public static String clean(String s, int max) {
      if (s == null) {
         return "";
      }

      StringBuilder out = new StringBuilder();
      boolean space = false;
      for (int i = 0; i < s.length(); i++) {
         char c = s.charAt(i);
         if (Character.isISOControl(c) || Character.isWhitespace(c)) {
            space = out.length() > 0;
            continue;
         }

         if (space) {
            out.append(' ');
            space = false;
         }

         out.append(c);
      }

      return out.length() <= max ? out.toString() : out.substring(0, max);
   }
}
