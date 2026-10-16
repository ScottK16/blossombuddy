package org.blossomsuite.core.music;

import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.blossomsuite.core.util.JsonUtil;

/**
 * Windows: asks the same "now playing" service the volume pop-up uses (it knows Spotify, YouTube Music in a browser, and so on), through
 * a small PowerShell helper that stays running and answers one line per question. Nothing is installed and no account is involved.
 */
final class WindowsMedia implements MediaBackend {
   private static final long ANSWER_TIMEOUT_MS = 8_000L;
   private static final long START_TIMEOUT_MS = 20_000L;

   private final Process process;
   private final BufferedWriter in;
   private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();

   WindowsMedia() throws IOException {
      String script;
      try (InputStream stream = WindowsMedia.class.getResourceAsStream("/assets/blossombuddy/media-windows.ps1")) {
         if (stream == null) {
            throw new IOException("The media helper is missing from the mod.");
         }

         script = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      }

      String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
      ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded);
      builder.redirectError(ProcessBuilder.Redirect.DISCARD);
      this.process = builder.start();
      this.in = new BufferedWriter(new OutputStreamWriter(this.process.getOutputStream(), StandardCharsets.UTF_8));
      Thread reader = new Thread(() -> {
         try (BufferedReader out = new BufferedReader(new InputStreamReader(this.process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = out.readLine()) != null) {
               this.lines.add(line);
            }
         } catch (IOException ended) {
            // the helper exited
         }
      }, "BlossomBuddy-MediaReader");
      reader.setDaemon(true);
      reader.start();
      try {
         String hello = this.lines.poll(START_TIMEOUT_MS, TimeUnit.MILLISECONDS);
         if (hello == null || !hello.contains("ready")) {
            this.close();
            throw new IOException("The media helper did not start.");
         }
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         this.close();
         throw new IOException("Interrupted while starting the media helper.");
      }
   }

   @Override
   public NowPlaying poll() throws IOException {
      return this.ask("poll");
   }

   @Override
   public NowPlaying command(MediaCommand command) throws IOException {
      return this.ask(command.word());
   }

   private synchronized NowPlaying ask(String word) throws IOException {
      this.lines.clear();
      try {
         this.in.write(word);
         this.in.write('\n');
         this.in.flush();
         String answer = this.lines.poll(ANSWER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
         if (answer == null) {
            this.close();
            throw new IOException("The media helper stopped answering.");
         }

         return parse(answer);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         throw new IOException("Interrupted.");
      }
   }

   /** The helper answers {"session":false} when nothing is open, else the title, artist and whether it is playing. */
   static NowPlaying parse(String json) {
      try {
         JsonObject o = JsonUtil.GSON.fromJson(json, JsonObject.class);
         if (o == null || !o.has("session") || !o.get("session").getAsBoolean()) {
            return NowPlaying.NONE;
         }

         return new NowPlaying(
            o.has("title") ? o.get("title").getAsString() : "",
            o.has("artist") ? o.get("artist").getAsString() : "",
            o.has("playing") && o.get("playing").getAsBoolean(),
            o.has("app") ? o.get("app").getAsString() : ""
         );
      } catch (RuntimeException junk) {
         return NowPlaying.NONE;
      }
   }

   @Override
   public void close() {
      try {
         this.in.write("quit\n");
         this.in.flush();
      } catch (IOException ignored) {
         // already gone
      }

      this.process.destroy();
   }
}
