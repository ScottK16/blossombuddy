package org.blossomsuite.core.cooldowns;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.util.SuiteLog;

/**
 * Cooldown rules the player added themselves (`/buddy cooldown add`), layered on top of whatever cooldowns.json
 * already has - see CooldownRules.applyCustomRules(). Kept in its own file so a future cooldowns.json update never
 * overwrites what a player typed in themselves.
 */
public final class CustomCooldownStore {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

   public record Entry(String id, long ms, String trigger) {
   }

   private static volatile List<Entry> entries = new ArrayList<>();

   private CustomCooldownStore() {
   }

   private static Path file() {
      return FabricLoader.getInstance().getConfigDir().resolve(SuiteRuntime.profile().modId()).resolve("custom-cooldowns.json");
   }

   public static synchronized List<Entry> entries() {
      return List.copyOf(entries);
   }

   public static synchronized void load() {
      Path file = file();
      if (!Files.isRegularFile(file)) {
         return;
      }

      try {
         String json = Files.readString(file, StandardCharsets.UTF_8);
         Entry[] parsed = GSON.fromJson(json, Entry[].class);
         entries = parsed == null ? new ArrayList<>() : new ArrayList<>(List.of(parsed));
      } catch (IOException | JsonParseException e) {
         SuiteLog.logger().warn("[cooldowns] could not read {}: {}", file, e.toString());
      }
   }

   /** Adds one, replacing any existing entry for the same id (a player re-adding an item just updates it). */
   public static synchronized void add(String id, long ms, CooldownRules.Trigger trigger) {
      entries.removeIf(e -> e.id.equals(id));
      entries.add(new Entry(id, ms, trigger.name()));
      save();
   }

   /** True if something was actually removed. */
   public static synchronized boolean remove(String id) {
      boolean removed = entries.removeIf(e -> e.id.equals(id));
      if (removed) {
         save();
      }

      return removed;
   }

   private static void save() {
      Path file = file();
      try {
         Files.createDirectories(file.getParent());
         Files.writeString(file, GSON.toJson(entries), StandardCharsets.UTF_8);
      } catch (IOException e) {
         SuiteLog.logger().warn("[cooldowns] could not save {}: {}", file, e.toString());
      }
   }

   /** Parses a rule's own trigger text; unrecognized/blank falls back to RIGHT_CLICK, the common "use this item" trigger. */
   public static CooldownRules.Trigger triggerOf(Entry e) {
      try {
         return CooldownRules.Trigger.valueOf(e.trigger.trim().toUpperCase(Locale.ROOT));
      } catch (Exception ex) {
         return CooldownRules.Trigger.RIGHT_CLICK;
      }
   }
}
