package org.blossomsuite.core.cooldowns;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.util.SuiteLog;

/**
 * Per-item overrides for the cooldown-ready sound (`/buddy cooldown sound`), keyed by the same id a cooldown rule
 * matches on - works for items from cooldowns.json just as well as ones added with `/buddy cooldown add`, since
 * this is a separate, independent mapping rather than a field on a rule.
 */
public final class CooldownSoundStore {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final java.lang.reflect.Type MAP_TYPE = new TypeToken<Map<String, String>>() {
   }.getType();

   private static volatile Map<String, String> soundByKey = new HashMap<>();

   private CooldownSoundStore() {
   }

   private static Path file() {
      return FabricLoader.getInstance().getConfigDir().resolve(SuiteRuntime.profile().modId()).resolve("cooldown-sounds.json");
   }

   public static synchronized void load() {
      Path file = file();
      if (!Files.isRegularFile(file)) {
         return;
      }

      try {
         String json = Files.readString(file, StandardCharsets.UTF_8);
         Map<String, String> parsed = GSON.fromJson(json, MAP_TYPE);
         soundByKey = parsed == null ? new HashMap<>() : new HashMap<>(parsed);
      } catch (IOException | JsonParseException e) {
         SuiteLog.logger().warn("[cooldowns] could not read {}: {}", file, e.toString());
      }
   }

   /** The overridden sound id for this item, or null if it just uses the default ready jingle. */
   public static synchronized String get(String key) {
      return key == null ? null : soundByKey.get(key);
   }

   public static synchronized Map<String, String> entries() {
      return Map.copyOf(soundByKey);
   }

   public static synchronized void set(String key, String soundId) {
      soundByKey.put(key, soundId);
      save();
   }

   /** True if something was actually removed. */
   public static synchronized boolean clear(String key) {
      boolean removed = soundByKey.remove(key) != null;
      if (removed) {
         save();
      }

      return removed;
   }

   private static void save() {
      Path file = file();
      try {
         Files.createDirectories(file.getParent());
         Files.writeString(file, GSON.toJson(soundByKey), StandardCharsets.UTF_8);
      } catch (IOException e) {
         SuiteLog.logger().warn("[cooldowns] could not save {}: {}", file, e.toString());
      }
   }
}
