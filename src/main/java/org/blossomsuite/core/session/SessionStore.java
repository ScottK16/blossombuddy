package org.blossomsuite.core.session;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import org.blossomsuite.core.util.JsonUtil;

/** The saved sessions: one line of JSON each, in a file on your computer. Anything older than {@link #KEEP_DAYS} days is dropped. */
public final class SessionStore {
   public static final int KEEP_DAYS = 120;
   private final Path file;

   public SessionStore(Path file) {
      this.file = file;
   }

   public synchronized void append(SessionRecord record) {
      try {
         Files.createDirectories(this.file.getParent());
         Files.writeString(this.file, JsonUtil.GSON.toJson(record.toJson()) + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      } catch (IOException e) {
         // not being able to save a history line must never get in the way of playing
      }
   }

   public synchronized void clear() {
      try {
         Files.deleteIfExists(this.file);
      } catch (IOException e) {
         // nothing to do
      }
   }

   /** Every saved session newer than the keep window, oldest first. Old and unreadable lines are dropped from the file too. */
   public synchronized List<SessionRecord> load(long nowMs) {
      List<SessionRecord> out = new ArrayList<>();
      if (!Files.isRegularFile(this.file)) {
         return out;
      }

      boolean pruned = false;
      long cutoff = nowMs - KEEP_DAYS * 86_400_000L;
      try {
         for (String line : Files.readAllLines(this.file, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
               continue;
            }

            SessionRecord record;
            try {
               record = SessionRecord.fromJson(JsonUtil.GSON.fromJson(line, JsonObject.class));
            } catch (RuntimeException junk) {
               record = null;
            }

            if (record == null || record.endMs() < cutoff) {
               pruned = true;
            } else {
               out.add(record);
            }
         }

         if (pruned) {
            StringBuilder text = new StringBuilder();
            for (SessionRecord r : out) {
               text.append(JsonUtil.GSON.toJson(r.toJson())).append(System.lineSeparator());
            }

            Files.writeString(this.file, text.toString(), StandardCharsets.UTF_8);
         }
      } catch (IOException e) {
         // read what we could
      }

      return out;
   }
}
