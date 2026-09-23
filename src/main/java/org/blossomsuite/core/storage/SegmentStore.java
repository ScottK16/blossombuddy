package org.blossomsuite.core.storage;

import org.blossomsuite.core.jobs.JobsTracker;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class SegmentStore {
   private static Path file;
   private static Path legacyFile;
   private static final int MAX_RECENT = 12;
   private static final Deque<JobsTracker.SegmentSnapshot> recent = new ArrayDeque<>(12);
   private static final int SEGMENT_FORMAT_VERSION = 2;

   private SegmentStore() {
   }

   public static void init(Path targetFile) throws IOException {
      init(targetFile, null);
   }

   public static void init(Path targetFile, Path legacyTypoFile) throws IOException {
      file = targetFile;
      legacyFile = legacyTypoFile;
      synchronized (recent) {
         recent.clear();
      }

      Files.createDirectories(file.getParent());
      if (!Files.exists(file)) {
         Files.createFile(file);
      }

      loadRecentFromDisk();
   }

   public static void saveSegment(JobsTracker.SegmentSnapshot segment) throws IOException {
      if (file == null) {
         throw new IllegalStateException("SegmentStore is not initialized");
      }

      synchronized (recent) {
         if (recent.size() == 12) {
            recent.removeFirst();
         }

         recent.addLast(segment);
      }

      try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.APPEND)) {
         String line = "{\"v\":2,\"ended\":"
            + segment.endedAtMs()
            + ",\"active\":"
            + segment.activeMs()
            + ",\"totalMoney\":"
            + segment.totalMoney()
            + ",\"totalExp\":"
            + segment.totalExp()
            + ",\"moneyPerHr\":"
            + segment.moneyPerHr()
            + ",\"expPerHr\":"
            + segment.expPerHr()
            + "}";
         w.write(line);
         w.newLine();
      }
   }

   public static List<JobsTracker.SegmentSnapshot> getRecent12() {
      synchronized (recent) {
         return new ArrayList<>(recent);
      }
   }

   private static void loadRecentFromDisk() throws IOException {
      loadRecentFromFile(legacyFile);
      loadRecentFromFile(file);
   }

   private static void loadRecentFromFile(Path path) throws IOException {
      if (path != null && Files.exists(path)) {
         String line;
         try (BufferedReader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            while ((line = r.readLine()) != null) {
               JobsTracker.SegmentSnapshot segment = parseLine(line);
               if (segment != null) {
                  synchronized (recent) {
                     if (recent.size() == 12) {
                        recent.removeFirst();
                     }

                     recent.addLast(segment);
                  }
               }
            }
         }
      }
   }

   private static JobsTracker.SegmentSnapshot parseLine(String s) {
      try {
         int v = extractIntOrDefault(s, "\"v\":", 1);
         if (v != 2) {
            return null;
         }

         long ended = extractLong(s, "\"ended\":");
         long active = extractLong(s, "\"active\":");
         double totalMoney = extractDouble(s, "\"totalMoney\":");
         double totalExp = extractDouble(s, "\"totalExp\":");
         double moneyPerHr = extractDouble(s, "\"moneyPerHr\":");
         double expPerHr = extractDouble(s, "\"expPerHr\":");
         long started = ended - active;
         return new JobsTracker.SegmentSnapshot(started, ended, active, totalMoney, totalExp, moneyPerHr, expPerHr);
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

   private static double extractDouble(String s, String key) {
      int i = s.indexOf(key);
      if (i < 0) {
         throw new IllegalArgumentException();
      }

      i += key.length();
      int j = i;

      while (j < s.length() && "0123456789.-".indexOf(s.charAt(j)) >= 0) {
         j++;
      }

      return Double.parseDouble(s.substring(i, j));
   }
}
