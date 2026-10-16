package org.blossomsuite.core.music;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import org.blossomsuite.core.config.FeatureConfig;

/**
 * Music controls: reads what your computer is playing (Windows and macOS) so it can sit on a HUD and be skipped from the keyboard, and,
 * only if you also turn sharing on, tells the player list so other players can see it. Everything is off until you turn it on, and the
 * reading happens on your computer: nothing leaves it unless you share.
 */
public final class MusicService {
   public static final MusicService INSTANCE = new MusicService(
      MusicService::systemBackend,
      Executors.newSingleThreadExecutor(r -> {
         Thread t = new Thread(r, "BlossomBuddy-Music");
         t.setDaemon(true);
         return t;
      }),
      System::currentTimeMillis,
      () -> FeatureConfig.INSTANCE.music.enabled,
      () -> FeatureConfig.INSTANCE.music.share
   );
   /** How often the music app is asked what is playing. */
   static final long POLL_MS = 3_000L;
   /** After a failure, wait this long before starting the helper again. */
   static final long RETRY_MS = 30_000L;

   /** Starts the helper for this computer; throws when it cannot (or the system is not supported). */
   interface Factory {
      MediaBackend open() throws IOException;
   }

   private final Factory factory;
   private final Executor io;
   private final LongSupplier clock;
   private final BooleanSupplier enabled;
   private final BooleanSupplier share;
   private MediaBackend backend;
   private boolean busy = false;
   private long lastPollAtMs = Long.MIN_VALUE / 2;
   private long lastFailAtMs = Long.MIN_VALUE / 2;
   private volatile NowPlaying current = NowPlaying.NONE;
   private volatile String status = "Off";

   MusicService(Factory factory, Executor io, LongSupplier clock, BooleanSupplier enabled, BooleanSupplier share) {
      this.factory = factory;
      this.io = io;
      this.clock = clock;
      this.enabled = enabled;
      this.share = share;
   }

   public NowPlaying current() {
      return this.current;
   }

   public String status() {
      return this.enabled.getAsBoolean() ? this.status : "Off";
   }

   /** What goes to the player list: the track, only when music is on, sharing is on and something is actually playing. */
   public String sharedText() {
      return this.enabled.getAsBoolean() && this.share.getAsBoolean() ? this.current.shareText() : "";
   }

   /** Call about once a second. Turning the feature off hangs up the helper and forgets the track. */
   public synchronized void tick() {
      if (!this.enabled.getAsBoolean()) {
         if (this.backend != null || this.current.hasTrack()) {
            this.shutDown();
         }

         return;
      }

      long now = this.clock.getAsLong();
      if (this.busy || now - this.lastPollAtMs < POLL_MS || now - this.lastFailAtMs < RETRY_MS) {
         return;
      }

      this.lastPollAtMs = now;
      this.busy = true;
      this.io.execute(() -> this.run(null));
   }

   /** Skip, go back or pause/resume. Does nothing when music controls are off. */
   public synchronized void command(MediaCommand command) {
      if (!this.enabled.getAsBoolean()) {
         return;
      }

      this.io.execute(() -> this.run(command));
   }

   private void run(MediaCommand command) {
      try {
         MediaBackend b;
         synchronized (this) {
            b = this.backend;
         }

         if (b == null) {
            b = this.factory.open();
            synchronized (this) {
               this.backend = b;
            }
         }

         NowPlaying now = command == null ? b.poll() : b.command(command);
         synchronized (this) {
            this.current = now;
            this.status = now.hasTrack() ? (now.playing() ? "Playing: " + now.display() : "Paused: " + now.display()) : "Nothing is playing";
            this.busy = false;
         }
      } catch (IOException | RuntimeException e) {
         synchronized (this) {
            this.closeBackend();
            this.current = NowPlaying.NONE;
            this.lastFailAtMs = this.clock.getAsLong();
            this.status = e.getMessage() == null || e.getMessage().isBlank() ? "Couldn't read your music app (will try again)" : e.getMessage();
            this.busy = false;
         }
      }
   }

   private void shutDown() {
      this.closeBackend();
      this.current = NowPlaying.NONE;
      this.status = "Off";
   }

   private void closeBackend() {
      MediaBackend b = this.backend;
      this.backend = null;
      if (b != null) {
         b.close();
      }
   }

   public static boolean supported() {
      String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
      return os.contains("win") || os.contains("mac");
   }

   private static MediaBackend systemBackend() throws IOException {
      String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
      if (os.contains("win")) {
         return new WindowsMedia();
      }

      if (os.contains("mac")) {
         return new MacMedia();
      }

      throw new IOException("Music controls work on Windows and macOS only.");
   }
}
