package org.blossomsuite.core.session;

import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * One play session: from joining to leaving. Saved on your computer only, so {@code /buddy session} can show today, this week and so on.
 *
 * @param money       job money earned during the session
 * @param overflowXp  overflow XP earned during the session
 * @param realmMs     how long you spent on each realm (milliseconds), by realm key
 */
public record SessionRecord(long startMs, long endMs, double money, double overflowXp, int dungeonRuns, int dungeonDone, Map<String, Long> realmMs) {
   public long durationMs() {
      return Math.max(0L, this.endMs - this.startMs);
   }

   public JsonObject toJson() {
      JsonObject o = new JsonObject();
      o.addProperty("start", this.startMs);
      o.addProperty("end", this.endMs);
      o.addProperty("money", this.money);
      o.addProperty("overflow", this.overflowXp);
      o.addProperty("runs", this.dungeonRuns);
      o.addProperty("done", this.dungeonDone);
      JsonObject realms = new JsonObject();
      for (Map.Entry<String, Long> e : this.realmMs.entrySet()) {
         realms.addProperty(e.getKey(), e.getValue());
      }

      o.add("realms", realms);
      return o;
   }

   /** Reads one saved line; null if it is not a usable record. */
   public static SessionRecord fromJson(JsonObject o) {
      try {
         long start = o.get("start").getAsLong();
         long end = o.get("end").getAsLong();
         if (end < start) {
            return null;
         }

         Map<String, Long> realms = new LinkedHashMap<>();
         if (o.has("realms") && o.get("realms").isJsonObject()) {
            for (Map.Entry<String, com.google.gson.JsonElement> e : o.getAsJsonObject("realms").entrySet()) {
               realms.put(e.getKey().toLowerCase(Locale.ROOT), Math.max(0L, e.getValue().getAsLong()));
            }
         }

         return new SessionRecord(
            start,
            end,
            Math.max(0.0, o.has("money") ? o.get("money").getAsDouble() : 0.0),
            Math.max(0.0, o.has("overflow") ? o.get("overflow").getAsDouble() : 0.0),
            o.has("runs") ? Math.max(0, o.get("runs").getAsInt()) : 0,
            o.has("done") ? Math.max(0, o.get("done").getAsInt()) : 0,
            realms
         );
      } catch (RuntimeException junk) {
         return null;
      }
   }
}
