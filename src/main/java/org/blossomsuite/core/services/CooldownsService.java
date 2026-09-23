package org.blossomsuite.core.services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.blossomsuite.core.net.EtagCache;
import org.blossomsuite.core.net.SuiteHttp;
import java.util.function.Consumer;

public final class CooldownsService {
   private final SuiteHttp http;
   private final EtagCache etags;
   private final Consumer<String> cooldownsJsonSink;
   private final Consumer<String> updateMessageSink;

   public CooldownsService(SuiteHttp http, EtagCache etags, Consumer<String> cooldownsJsonSink, Consumer<String> updateMessageSink) {
      this.http = http;
      this.etags = etags;
      this.cooldownsJsonSink = cooldownsJsonSink == null ? json -> {} : cooldownsJsonSink;
      this.updateMessageSink = updateMessageSink == null ? message -> {} : updateMessageSink;
   }

   public void fetchCooldownsIfChanged(String urlPath) {
      String key = "cooldowns_file";
      String inm = this.etags.get(key);
      this.http.get(urlPath, inm).thenAccept(resp -> {
         if (resp.statusCode() != 304) {
            if (resp.statusCode() == 200) {
               this.etags.set(key, resp.headers().firstValue("etag").orElse(null));
               String json = resp.body();
               this.cooldownsJsonSink.accept(json);
               String version = extractVersion(json);
               if (version != null) {
                  this.updateMessageSink.accept("Cooldowns updated (v" + version + ").");
               } else {
                  this.updateMessageSink.accept("Cooldowns updated.");
               }
            }
         }
      }).exceptionally(ex -> null);
   }

   private static String extractVersion(String json) {
      try {
         JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
         return obj.has("version") ? obj.get("version").getAsString() : null;
      } catch (Exception e) {
         return null;
      }
   }
}
