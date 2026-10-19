package org.blossomsuite.core.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.blossomsuite.core.util.JsonUtil;
import org.junit.jupiter.api.Test;

/**
 * The Linux and macOS side of Discord presence: a Unix socket in the runtime or temp folder, or inside a Flatpak or Snap folder. A stand-in
 * Discord listens on a real Unix socket (which Java supports on Windows too), so this runs the same code Linux would.
 */
class DiscordSocketTest {
   /** Plays Discord for one connection: accepts the handshake, says READY, and records the activity it is sent. */
   private static final class FakeDiscord implements AutoCloseable {
      final List<JsonObject> received = new CopyOnWriteArrayList<>();
      private final ServerSocketChannel server;
      private final Thread thread;

      FakeDiscord(Path socketFile) throws IOException {
         Files.createDirectories(socketFile.getParent());
         this.server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
         this.server.bind(UnixDomainSocketAddress.of(socketFile));
         this.thread = new Thread(() -> {
            try (SocketChannel c = this.server.accept()) {
               while (true) {
                  ByteBuffer head = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
                  if (!fill(c, head)) {
                     return;
                  }

                  head.flip();
                  int op = head.getInt();
                  ByteBuffer body = ByteBuffer.allocate(head.getInt());
                  if (!fill(c, body)) {
                     return;
                  }

                  JsonObject msg = JsonUtil.GSON.fromJson(new String(body.array(), StandardCharsets.UTF_8), JsonObject.class);
                  this.received.add(msg);
                  String reply = op == DiscordPresence.OP_HANDSHAKE
                     ? "{\"cmd\":\"DISPATCH\",\"evt\":\"READY\",\"data\":{\"v\":1}}"
                     : "{\"cmd\":\"SET_ACTIVITY\",\"evt\":null,\"data\":{}}";
                  byte[] out = DiscordPresence.encode(DiscordPresence.OP_FRAME, reply);
                  c.write(ByteBuffer.wrap(out));
               }
            } catch (IOException ended) {
               // the client hung up
            }
         }, "fake-discord");
         this.thread.setDaemon(true);
         this.thread.start();
      }

      private static boolean fill(SocketChannel c, ByteBuffer buf) throws IOException {
         while (buf.hasRemaining()) {
            if (c.read(buf) < 0) {
               return false;
            }
         }

         return true;
      }

      @Override
      public void close() throws IOException {
         this.server.close();
      }
   }

   private static DiscordPresence.Activity activity() {
      return new DiscordPresence.Activity("On Cherry Realm", "", 1_700_000_000L, "logo", "BlossomBuddy", "Get BlossomBuddy", "https://blossombuddy.site");
   }

   private static void waitFor(java.util.function.BooleanSupplier done) throws InterruptedException {
      for (int i = 0; i < 100 && !done.getAsBoolean(); i++) {
         Thread.sleep(20);
      }
   }

   @Test
   void aPlainDiscordSocketInTheRuntimeFolderIsFoundAndUsed() throws Exception {
      Path dir = Files.createTempDirectory("dc");
      try (FakeDiscord discord = new FakeDiscord(dir.resolve("discord-ipc-0"))) {
         DiscordPresence presence = new DiscordPresence(() -> DiscordTransports.openSocket(List.of(dir)), Runnable::run, () -> 1_000_000L, 4242L);
         presence.tick("1550998827996160100", activity());
         assertEquals("Connected to Discord", presence.status());
         presence.tick("1550998827996160100", activity());
         assertEquals("Showing on Discord", presence.status());
         waitFor(() -> discord.received.size() >= 2);
         assertEquals("1550998827996160100", discord.received.get(0).get("client_id").getAsString());
         JsonObject set = discord.received.get(1);
         assertEquals("SET_ACTIVITY", set.get("cmd").getAsString());
         assertEquals("On Cherry Realm", set.getAsJsonObject("args").getAsJsonObject("activity").get("details").getAsString());
      }
   }

   @Test
   void aFlatpakDiscordSocketInsideTheRuntimeFolderIsFoundToo() throws Exception {
      Path dir = Files.createTempDirectory("dc");
      try (FakeDiscord discord = new FakeDiscord(dir.resolve("app/com.discordapp.Discord/discord-ipc-0"))) {
         DiscordPresence.Conn conn = DiscordTransports.openSocket(List.of(dir));
         conn.close();
         assertTrue(true, "connected without an exception");
      }
   }

   @Test
   void aSnapDiscordSocketAndAnotherFoldersSocketAreFoundToo() throws Exception {
      Path empty = Files.createTempDirectory("dc");
      Path dir = Files.createTempDirectory("dc");
      try (FakeDiscord discord = new FakeDiscord(dir.resolve("snap.discord/discord-ipc-3"))) {
         DiscordTransports.openSocket(List.of(empty, dir)).close(); // not in the first folder, and the third of ten slots in the second
      }
   }

   @Test
   void withNoDiscordSocketAnywhereItReportsThatDiscordIsNotRunning() throws Exception {
      Path dir = Files.createTempDirectory("dc");
      IOException e = org.junit.jupiter.api.Assertions.assertThrows(IOException.class, () -> DiscordTransports.openSocket(List.of(dir)));
      assertTrue(e.getMessage().contains("not running"));
   }

   @Test
   void theFoldersToLookInAreTheRuntimeTempAndPerUserFoldersWithoutRepeats() {
      Map<String, String> env = Map.of("XDG_RUNTIME_DIR", "/run/user/1000", "TMPDIR", "/var/folders/ab/T");
      List<Path> dirs = DiscordTransports.socketDirs(env::get, () -> "Name:\tjava\nUid:\t1000\t1000\t1000\t1000\nGid:\t1000\n");
      assertEquals(List.of(Path.of("/run/user/1000"), Path.of("/var/folders/ab/T"), Path.of("/tmp")), dirs);
   }

   @Test
   void theRuntimeFolderIsGuessedFromTheUserIdWhenTheEnvironmentLacksIt() {
      List<Path> dirs = DiscordTransports.socketDirs(name -> null, () -> "Uid:\t1234\t1234\t1234\t1234\n");
      assertEquals(List.of(Path.of("/run/user/1234"), Path.of("/tmp")), dirs);
      assertEquals(List.of(Path.of("/tmp")), DiscordTransports.socketDirs(name -> null, () -> null), "macOS has no /proc");
   }

   @Test
   void everyKnownDiscordFlavourHasAFolderToLookIn() {
      List<String> subs = new ArrayList<>(List.of(DiscordTransports.SOCKET_SUBDIRS));
      assertTrue(subs.contains(""));
      assertTrue(subs.contains("app/com.discordapp.Discord"));
      assertTrue(subs.contains("snap.discord"));
      assertTrue(subs.contains("app/dev.vencord.Vesktop"));
   }
}
