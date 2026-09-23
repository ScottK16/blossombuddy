package org.blossomsuite.core.services;

import org.blossomsuite.core.net.EtagCache;
import org.blossomsuite.core.net.SuiteHttp;
import org.blossomsuite.core.services.models.ConfigManifest;
import org.blossomsuite.core.util.JsonUtil;
import java.util.function.Consumer;

public final class ConfigManifestService {
   private final SuiteHttp http;
   private final EtagCache etags;
   private final CooldownsService cooldownsService;
   private final Consumer<String> statusMessageSink;
   private String lastCooldownsSha = "";

   public ConfigManifestService(SuiteHttp http, EtagCache etags, CooldownsService cooldownsService, Consumer<String> statusMessageSink) {
      this.http = http;
      this.etags = etags;
      this.cooldownsService = cooldownsService;
      this.statusMessageSink = statusMessageSink == null ? message -> {} : statusMessageSink;
   }

   public void pollOnce() {
      String key = "manifest_config";
      String inm = this.etags.get(key);
      this.http.get("/v1/manifest/config", inm).thenAccept(resp -> {
         if (resp.statusCode() != 304) {
            if (resp.statusCode() == 200) {
               this.etags.set(key, resp.headers().firstValue("etag").orElse(null));
               ConfigManifest manifest = (ConfigManifest)JsonUtil.GSON.fromJson(resp.body(), ConfigManifest.class);
               if (manifest != null && manifest.resources != null) {
                  ConfigManifest.ResourceEntry cooldowns = manifest.resources.get("cooldowns");
                  if (cooldowns != null) {
                     this.statusMessageSink.accept("Version:" + cooldowns.version);
                     if (!Boolean.TRUE.equals(cooldowns.missing)) {
                        if (cooldowns.sha256 != null && cooldowns.url != null) {
                           if (!cooldowns.sha256.equals(this.lastCooldownsSha)) {
                              this.lastCooldownsSha = cooldowns.sha256;
                              this.cooldownsService.fetchCooldownsIfChanged(cooldowns.url);
                           }
                        }
                     }
                  }
               }
            }
         }
      }).exceptionally(ex -> null);
   }
}
