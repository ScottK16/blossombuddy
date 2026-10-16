package org.blossomsuite.core.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MusicTest {
   // ------------------------------------------------------------------ the track text

   @Test
   void titlesAreCleanedAndOnlyAPlayingTrackIsShared() {
      NowPlaying np = new NowPlaying("  I   Love\nYou\u0000 ", "Fontaines D.C.", true, "Spotify.exe");
      assertEquals("I Love You", np.title());
      assertEquals("I Love You - Fontaines D.C.", np.display());
      assertEquals("I Love You - Fontaines D.C.", np.shareText());
      assertEquals("", new NowPlaying("Song", "Band", false, "").shareText(), "paused is not shared");
      assertEquals("", NowPlaying.NONE.shareText());
      assertEquals("Song", new NowPlaying("Song", "", true, "").shareText());
   }

   @Test
   void longTitlesAreClippedForSharing() {
      String shared = new NowPlaying("x".repeat(110), "y".repeat(70), true, "").shareText();
      assertEquals(NowPlaying.MAX_SHARED, shared.length());
      assertTrue(shared.endsWith("…"));
   }

   // ------------------------------------------------------------------ reading the helpers' answers

   @Test
   void theWindowsHelpersAnswerIsRead() {
      NowPlaying np = WindowsMedia.parse("{\"artist\":\"Fontaines D.C.\",\"title\":\"I Love You\",\"playing\":true,\"session\":true,\"app\":\"Spotify.exe\"}");
      assertEquals("I Love You", np.title());
      assertEquals("Fontaines D.C.", np.artist());
      assertTrue(np.playing());
      assertEquals("Spotify.exe", np.app());
      assertFalse(WindowsMedia.parse("{\"session\":false}").hasTrack());
      assertFalse(WindowsMedia.parse("not json at all").hasTrack());
      assertFalse(WindowsMedia.parse("").hasTrack());
      assertFalse(WindowsMedia.parse("{\"ready\":true}").hasTrack());
   }

   @Test
   void theMacAnswerPrefersAPlayingAppOverAPausedOne() {
      NowPlaying np = MacMedia.parse("Spotify\tpaused\tOld Song\tOld Band\nMusic\tplaying\tNew Song\tNew Band\n");
      assertEquals("New Song", np.title());
      assertEquals("Music", np.app());
      assertTrue(np.playing());

      NowPlaying paused = MacMedia.parse("Spotify\tpaused\tOld Song\tOld Band\n");
      assertEquals("Old Song", paused.title());
      assertFalse(paused.playing());

      assertFalse(MacMedia.parse("").hasTrack());
      assertFalse(MacMedia.parse("garbage").hasTrack());
      assertEquals("Song", MacMedia.parse("Spotify\tplaying\tSong\t").title());
   }

   // ------------------------------------------------------------------ the service

   static final class FakeBackend implements MediaBackend {
      NowPlaying now = new NowPlaying("Song", "Band", true, "Spotify");
      final List<String> log = new ArrayList<>();
      boolean fail = false;
      boolean closed = false;

      @Override
      public NowPlaying poll() throws IOException {
         this.log.add("poll");
         if (this.fail) {
            throw new IOException("the helper died");
         }

         return this.now;
      }

      @Override
      public NowPlaying command(MediaCommand command) throws IOException {
         this.log.add(command.word());
         if (command == MediaCommand.PLAY_PAUSE) {
            this.now = new NowPlaying(this.now.title(), this.now.artist(), !this.now.playing(), this.now.app());
         } else {
            this.now = new NowPlaying("Next Song", "Band", true, "Spotify");
         }

         return this.now;
      }

      @Override
      public void close() {
         this.closed = true;
      }
   }

   private FakeBackend backend;
   private boolean enabled;
   private boolean share;
   private long now;
   private int opens;
   private MusicService service;

   @BeforeEach
   void setUp() {
      this.backend = new FakeBackend();
      this.enabled = true;
      this.share = false;
      this.now = 1_000_000L;
      this.opens = 0;
      this.service = new MusicService(() -> {
         this.opens++;
         return this.backend;
      }, Runnable::run, () -> this.now, () -> this.enabled, () -> this.share);
   }

   @Test
   void itReadsWhatIsPlayingButOnlyEveryFewSeconds() {
      this.service.tick();
      assertEquals("Song - Band", this.service.current().display());
      assertEquals("Playing: Song - Band", this.service.status());
      this.now += 1_000L;
      this.service.tick();
      assertEquals(List.of("poll"), this.backend.log, "too soon to ask again");
      this.now += MusicService.POLL_MS;
      this.service.tick();
      assertEquals(List.of("poll", "poll"), this.backend.log);
      assertEquals(1, this.opens, "the helper is started once and reused");
   }

   @Test
   void nothingStartsWhileItIsOff() {
      this.enabled = false;
      this.service.tick();
      this.service.command(MediaCommand.NEXT);
      assertEquals(0, this.opens);
      assertEquals("Off", this.service.status());
      assertEquals("", this.service.sharedText());
   }

   @Test
   void theKeysDriveTheMusicApp() {
      this.service.tick();
      this.service.command(MediaCommand.NEXT);
      assertEquals("Next Song - Band", this.service.current().display());
      this.service.command(MediaCommand.PLAY_PAUSE);
      assertFalse(this.service.current().playing());
      assertEquals(List.of("poll", "next", "pause"), this.backend.log);
   }

   @Test
   void nothingIsSharedUnlessSharingIsOnAndSomethingIsPlaying() {
      this.service.tick();
      assertEquals("", this.service.sharedText(), "reading is not sharing");
      this.share = true;
      assertEquals("Song - Band", this.service.sharedText());
      this.service.command(MediaCommand.PLAY_PAUSE);
      assertEquals("", this.service.sharedText(), "paused: nothing to show");
   }

   @Test
   void turningItOffHangsUpAndForgetsTheTrack() {
      this.service.tick();
      this.enabled = false;
      this.service.tick();
      assertTrue(this.backend.closed);
      assertFalse(this.service.current().hasTrack());
   }

   @Test
   void aDeadHelperIsReportedAndRestartedAfterAWhile() {
      this.service.tick();
      this.backend.fail = true;
      this.now += MusicService.POLL_MS;
      this.service.tick();
      assertEquals("the helper died", this.service.status());
      assertFalse(this.service.current().hasTrack());
      assertTrue(this.backend.closed);

      this.backend.fail = false;
      this.backend.closed = false;
      this.now += MusicService.POLL_MS;
      this.service.tick();
      assertEquals(1, this.opens, "waits before trying again");
      this.now += MusicService.RETRY_MS;
      this.service.tick();
      assertEquals(2, this.opens);
      assertTrue(this.service.current().hasTrack());
   }

   @Test
   void aComputerThatCannotStartTheHelperSaysSo() {
      MusicService broken = new MusicService(() -> {
         throw new IOException("Music controls work on Windows and macOS only.");
      }, Runnable::run, () -> this.now, () -> true, () -> false);
      broken.tick();
      assertEquals("Music controls work on Windows and macOS only.", broken.status());
   }
}
