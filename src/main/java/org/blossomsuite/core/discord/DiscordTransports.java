package org.blossomsuite.core.discord;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** How to reach the Discord app on this computer: a named pipe on Windows, a Unix socket on Linux and macOS. Tries discord-ipc-0 to 9. */
final class DiscordTransports {
   private DiscordTransports() {
   }

   static DiscordPresence.Transport system() {
      boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
      return windows ? DiscordTransports::openPipe : DiscordTransports::openSocket;
   }

   // ------------------------------------------------------------------ Windows

   private static DiscordPresence.Conn openPipe() throws IOException {
      IOException last = new IOException("Discord is not running");
      for (int i = 0; i < 10; i++) {
         try {
            RandomAccessFile pipe = new RandomAccessFile("\\\\?\\pipe\\discord-ipc-" + i, "rw");
            return new PipeConn(pipe);
         } catch (IOException e) {
            last = e;
         }
      }

      throw last;
   }

   private static final class PipeConn implements DiscordPresence.Conn {
      private final RandomAccessFile pipe;

      PipeConn(RandomAccessFile pipe) {
         this.pipe = pipe;
      }

      @Override
      public void write(byte[] frame) throws IOException {
         this.pipe.write(frame);
      }

      @Override
      public DiscordPresence.Frame readFrame() throws IOException {
         byte[] header = new byte[8];
         this.pipe.readFully(header);
         byte[] body = new byte[DiscordPresence.bodyLength(header)];
         this.pipe.readFully(body);
         return new DiscordPresence.Frame(DiscordPresence.opcodeOf(header), new String(body, StandardCharsets.UTF_8));
      }

      @Override
      public void close() throws IOException {
         this.pipe.close();
      }
   }

   // ------------------------------------------------------------------ Linux and macOS

   private static DiscordPresence.Conn openSocket() throws IOException {
      List<Path> dirs = new ArrayList<>();
      for (String var : new String[]{"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
         String value = System.getenv(var);
         if (value != null && !value.isBlank()) {
            dirs.add(Path.of(value));
         }
      }

      dirs.add(Path.of("/tmp"));
      IOException last = new IOException("Discord is not running");
      List<Path> candidates = new ArrayList<>();
      for (Path dir : dirs) {
         for (String sub : new String[]{"", "app/com.discordapp.Discord", "snap.discord"}) { // plain, Flatpak, Snap
            for (int i = 0; i < 10; i++) {
               candidates.add(sub.isEmpty() ? dir.resolve("discord-ipc-" + i) : dir.resolve(sub).resolve("discord-ipc-" + i));
            }
         }
      }

      for (Path path : candidates) {
         if (!Files.exists(path)) {
            continue;
         }

         try {
            SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
            try {
               channel.connect(UnixDomainSocketAddress.of(path));
               return new SocketConn(channel);
            } catch (IOException e) {
               channel.close();
               last = e;
            }
         } catch (IOException e) {
            last = e;
         }
      }

      throw last;
   }

   private static final class SocketConn implements DiscordPresence.Conn {
      private final SocketChannel channel;

      SocketConn(SocketChannel channel) {
         this.channel = channel;
      }

      @Override
      public void write(byte[] frame) throws IOException {
         ByteBuffer buf = ByteBuffer.wrap(frame);
         while (buf.hasRemaining()) {
            this.channel.write(buf);
         }
      }

      private void readFully(byte[] into) throws IOException {
         ByteBuffer buf = ByteBuffer.wrap(into);
         while (buf.hasRemaining()) {
            if (this.channel.read(buf) < 0) {
               throw new IOException("Discord hung up");
            }
         }
      }

      @Override
      public DiscordPresence.Frame readFrame() throws IOException {
         byte[] header = new byte[8];
         this.readFully(header);
         byte[] body = new byte[DiscordPresence.bodyLength(header)];
         this.readFully(body);
         return new DiscordPresence.Frame(DiscordPresence.opcodeOf(header), new String(body, StandardCharsets.UTF_8));
      }

      @Override
      public void close() throws IOException {
         this.channel.close();
      }
   }
}
