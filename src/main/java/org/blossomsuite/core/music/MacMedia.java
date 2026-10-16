package org.blossomsuite.core.music;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * macOS: asks Spotify and Apple Music (the Music app) what is playing, through AppleScript. The first time, macOS asks whether the game
 * may control those apps; saying yes is what allows this. Only apps that are already running are asked, so nothing gets launched.
 */
final class MacMedia implements MediaBackend {
   private static final long TIMEOUT_MS = 6_000L;
   private static final String SCRIPT = String.join("\n",
      "set out to \"\"",
      "if application \"Spotify\" is running then",
      "  tell application \"Spotify\"",
      "    if player state is not stopped then set out to out & \"Spotify\" & tab & (player state as text) & tab & (name of current track) & tab & (artist of current track) & linefeed",
      "  end tell",
      "end if",
      "if application \"Music\" is running then",
      "  tell application \"Music\"",
      "    if player state is not stopped then set out to out & \"Music\" & tab & (player state as text) & tab & (name of current track) & tab & (artist of current track) & linefeed",
      "  end tell",
      "end if",
      "return out");

   private volatile String lastApp = "Spotify";

   @Override
   public NowPlaying poll() throws IOException {
      String out = run(SCRIPT);
      NowPlaying found = parse(out);
      if (found.hasTrack()) {
         this.lastApp = found.app();
      }

      return found;
   }

   @Override
   public NowPlaying command(MediaCommand command) throws IOException {
      String verb = switch (command) {
         case NEXT -> "next track";
         case PREVIOUS -> "previous track";
         case PLAY_PAUSE -> "playpause";
      };
      String app = "Music".equals(this.lastApp) ? "Music" : "Spotify";
      run("tell application \"" + app + "\" to " + verb);
      return this.poll();
   }

   /** One line per app that is open: app, state, title, artist (tab separated). A playing app wins over a paused one. */
   static NowPlaying parse(String out) {
      NowPlaying paused = NowPlaying.NONE;
      for (String line : (out == null ? "" : out).split("\\R")) {
         String[] f = line.split("\t", -1);
         if (f.length < 4 || f[2].isBlank()) {
            continue;
         }

         boolean playing = f[1].trim().equalsIgnoreCase("playing");
         NowPlaying np = new NowPlaying(f[2], f[3], playing, f[0]);
         if (playing) {
            return np;
         }

         if (!paused.hasTrack()) {
            paused = np;
         }
      }

      return paused;
   }

   private static String run(String script) throws IOException {
      Process p = new ProcessBuilder("osascript", "-e", script).redirectError(ProcessBuilder.Redirect.DISCARD).start();
      try {
         if (!p.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            p.destroyForcibly();
            throw new IOException("osascript timed out");
         }

         return new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         p.destroyForcibly();
         throw new IOException("Interrupted.");
      }
   }

   @Override
   public void close() {
      // nothing stays running between questions
   }
}
