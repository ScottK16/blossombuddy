package org.blossomsuite.core.services;

import org.blossomsuite.core.net.EtagCache;
import org.blossomsuite.core.net.SuiteHttp;
import org.blossomsuite.core.services.models.RelayModels;
import org.blossomsuite.core.util.JsonUtil;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class RelayService {
   private final SuiteHttp http;
   private final EtagCache etags;
   private final AtomicLong lastPublishMs = new AtomicLong(0L);
   private final long publishMinIntervalMs = 1500L;
   private final AtomicLong lastPollMs = new AtomicLong(0L);
   private final long pollIntervalMs = 4000L;
   private volatile RelayModels.RelayFetchResponse lastState = null;

   public RelayService(SuiteHttp http, EtagCache etags) {
      this.http = http;
      this.etags = etags;
   }

   public RelayModels.RelayFetchResponse getLastState() {
      return this.lastState;
   }

   public void publishSnapshotIfAllowed(
      boolean altTrackingEnabled, String linkId, String realmName, String altName, List<RelayModels.RelayCooldownEntry> cooldownsSnapshot
   ) {
      if (altTrackingEnabled) {
         if (linkId != null && !linkId.isBlank()) {
            if (realmName != null && !realmName.isBlank()) {
               if (altName != null && !altName.isBlank()) {
                  long now = System.currentTimeMillis();
                  long prev = this.lastPublishMs.get();
                  if (now - prev >= 1500L) {
                     this.lastPublishMs.set(now);
                     RelayModels.RelayPublishDto dto = new RelayModels.RelayPublishDto();
                     dto.linkId = linkId;
                     dto.realmName = realmName;
                     dto.altName = altName;
                     dto.cooldowns = cooldownsSnapshot;
                     dto.nonce = UUID.randomUUID().toString();
                     String json = JsonUtil.GSON.toJson(dto);
                     this.http.postJson("/v1/relay/cooldowns", json).thenAccept(resp -> {}).exceptionally(ex -> null);
                  }
               }
            }
         }
      }
   }

   public void pollIfAllowed(boolean showHudEnabled, String linkId, String realmName) {
      if (showHudEnabled) {
         if (linkId != null && !linkId.isBlank()) {
            if (realmName != null && !realmName.isBlank()) {
               long now = System.currentTimeMillis();
               long prev = this.lastPollMs.get();
               if (now - prev >= 4000L) {
                  this.lastPollMs.set(now);
                  String key = "relay_cooldowns_" + linkId + "_" + realmName;
                  String inm = this.etags.get(key);
                  String qLink = URLEncoder.encode(linkId, StandardCharsets.UTF_8);
                  String path = "/v1/relay/cooldowns?linkId=" + qLink;
                  this.http
                     .get(path, inm)
                     .thenAccept(
                        resp -> {
                           if (resp.statusCode() != 304) {
                              if (resp.statusCode() == 200) {
                                 this.etags.set(key, resp.headers().firstValue("etag").orElse(null));
                                 RelayModels.RelayFetchResponse parsed = (RelayModels.RelayFetchResponse)JsonUtil.GSON
                                    .fromJson(resp.body(), RelayModels.RelayFetchResponse.class);
                                 if (parsed != null) {
                                    this.lastState = parsed;
                                 }
                              }
                           }
                        }
                     )
                     .exceptionally(ex -> null);
               }
            }
         }
      }
   }

   public static String isoUtc(Instant instant) {
      return instant.toString();
   }
}
