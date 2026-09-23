package org.blossomsuite.core.storage;

import org.blossomsuite.core.SuiteRuntime;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DungeonStore {
   private static final Logger LOGGER = LoggerFactory.getLogger("suite-core-dungeons");
   private static final int MAX_RECENT_PER_SERVER = 12;
   private static final int FORMAT_VERSION = 1;
   private static Path file;
   private static final Map<String, Deque<DungeonStore.DungeonAttempt>> recentByServer = new LinkedHashMap<>();

   private DungeonStore() {
   }

   public static void init() {
      Path dir = FabricLoader.getInstance().getConfigDir().resolve(SuiteRuntime.profile().modId());
      file = dir.resolve("dungeons.jsonl");

      try {
         Files.createDirectories(file.getParent());
         if (!Files.exists(file)) {
            Files.createFile(file);
         }

         loadRecentFromDisk();
      } catch (IOException e) {
         LOGGER.warn("DungeonStore init failed", e);
      }
   }

   public static void saveAttempt(DungeonStore.DungeonAttempt attempt) {
      if (attempt != null) {
         synchronized (recentByServer) {
            addRecent(attempt);
         }

         try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.APPEND)) {
            w.write(encodeLine(attempt));
            w.newLine();
         } catch (IOException e) {
            LOGGER.warn("DungeonStore saveAttempt failed", e);
         }
      }
   }

   public static Map<String, List<DungeonStore.DungeonAttempt>> getRecent12ByServer() {
      synchronized (recentByServer) {
         Map<String, List<DungeonStore.DungeonAttempt>> out = new LinkedHashMap<>();

         for (Entry<String, Deque<DungeonStore.DungeonAttempt>> entry : recentByServer.entrySet()) {
            out.put(entry.getKey(), new ArrayList<>(entry.getValue()));
         }

         return out;
      }
   }

   private static void loadRecentFromDisk() {
      if (file != null && Files.exists(file)) {
         String line;
         try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            while ((line = r.readLine()) != null) {
               DungeonStore.DungeonAttempt attempt = parseLine(line);
               if (attempt != null) {
                  synchronized (recentByServer) {
                     addRecent(attempt);
                  }
               }
            }
         } catch (IOException e) {
            LOGGER.warn("DungeonStore loadRecent failed", e);
         }
      }
   }

   private static void addRecent(DungeonStore.DungeonAttempt attempt) {
      Deque<DungeonStore.DungeonAttempt> deque = recentByServer.computeIfAbsent(attempt.server(), k -> new ArrayDeque<>(12));
      if (deque.size() == 12) {
         deque.removeFirst();
      }

      deque.addLast(attempt);
   }

   private static String encodeLine(DungeonStore.DungeonAttempt attempt) {
      return "{\"v\":1,\"server\":\""
         + b64(attempt.server())
         + "\",\"ended\":"
         + attempt.endedAtMs()
         + ",\"active\":"
         + attempt.durationMs()
         + ",\"completed\":"
         + attempt.completed()
         + ",\"dungeon\":\""
         + b64(attempt.dungeonName())
         + "\",\"names\":\""
         + b64(attempt.names())
         + "\"}";
   }

   private static DungeonStore.DungeonAttempt parseLine(String s) {
      try {
         int v = extractIntOrDefault(s, "\"v\":", 0);
         if (v != 1) {
            return null;
         }

         String server = unb64(extractString(s, "\"server\":\""));
         long ended = extractLong(s, "\"ended\":");
         long active = extractLong(s, "\"active\":");
         boolean completed = extractBool(s, "\"completed\":");
         String dungeon = unb64(extractString(s, "\"dungeon\":\""));
         String names = unb64(extractString(s, "\"names\":\""));
         return new DungeonStore.DungeonAttempt(server, ended, active, completed, dungeon, names);
      } catch (Exception ignored) {
         return null;
      }
   }

   private static int extractIntOrDefault(String s, String key, int def) {
      try {
         int i = s.indexOf(key);
         if (i < 0) {
            return def;
         }

         i += key.length();
         int j = i;

         while (j < s.length() && (Character.isDigit(s.charAt(j)) || s.charAt(j) == '-')) {
            j++;
         }

         return Integer.parseInt(s.substring(i, j));
      } catch (Exception e) {
         return def;
      }
   }

   private static long extractLong(String s, String key) {
      int i = s.indexOf(key);
      if (i < 0) {
         throw new IllegalArgumentException();
      }

      i += key.length();
      int j = i;

      while (j < s.length() && (Character.isDigit(s.charAt(j)) || s.charAt(j) == '-')) {
         j++;
      }

      return Long.parseLong(s.substring(i, j));
   }

   private static boolean extractBool(String s, String key) {
      int i = s.indexOf(key);
      if (i < 0) {
         throw new IllegalArgumentException();
      }

      i += key.length();
      return s.startsWith("true", i);
   }

   private static String extractString(String s, String key) {
      int i = s.indexOf(key);
      if (i < 0) {
         throw new IllegalArgumentException();
      } else {
         i += key.length();
         int j = s.indexOf(34, i);
         if (j < 0) {
            throw new IllegalArgumentException();
         } else {
            return s.substring(i, j);
         }
      }
   }

   private static String b64(String value) {
      return Base64.getUrlEncoder().withoutPadding().encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
   }

   private static String unb64(String value) {
      if (value != null && !value.isBlank()) {
         try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
         } catch (Exception ignored) {
            return "";
         }
      } else {
         return "";
      }
   }

   public record DungeonAttempt(String server, long endedAtMs, long durationMs, boolean completed, String dungeonName, String names) {
   }
}
