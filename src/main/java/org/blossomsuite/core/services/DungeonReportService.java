package org.blossomsuite.core.services;

import org.blossomsuite.core.net.SuiteHttp;
import org.blossomsuite.core.services.models.DungeonModels;
import org.blossomsuite.core.util.JsonUtil;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

public final class DungeonReportService {
   private static final String START_PATH = "/v1/dungeons/runs/start";
   private static final String FINISH_PATH = "/v1/dungeons/runs/finish";
   private static final String LOST_LIVES_PATH = "/v1/dungeons/runs/lost-lives";
   private static final String COOLDOWN_PATH = "/v1/dungeons/runs/cooldown";
   private final SuiteHttp http;
   private final BooleanSupplier canReport;

   public DungeonReportService(SuiteHttp http, BooleanSupplier canReport) {
      this.http = http;
      this.canReport = canReport == null ? () -> true : canReport;
   }

   public CompletableFuture<String> reportStartIfAllowed(DungeonModels.DungeonRunStartRequest request) {
      if (!this.canReport()) {
         return CompletableFuture.completedFuture(null);
      }

      if (request == null) {
         return CompletableFuture.completedFuture(null);
      }

      if (blank(request.clientRunId)) {
         return CompletableFuture.completedFuture(null);
      }

      if (!blank(request.playerUuid) && !blank(request.playerName)) {
         if (blank(request.reporterUuid)) {
            request.reporterUuid = request.playerUuid;
         }

         if (blank(request.reporterName)) {
            request.reporterName = request.playerName;
         }

         if (blank(request.serverKey)) {
            return CompletableFuture.completedFuture(null);
         }

         request.nonce = UUID.randomUUID().toString();
         String json = JsonUtil.GSON.toJson(request);
         return this.http
            .postJson("/v1/dungeons/runs/start", json)
            .thenApply(
               resp -> {
                  if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                     DungeonModels.DungeonRunStartResponse parsed = (DungeonModels.DungeonRunStartResponse)JsonUtil.GSON
                        .fromJson(resp.body(), DungeonModels.DungeonRunStartResponse.class);
                     if (parsed == null) {
                        return null;
                     } else if (!blank(parsed.uid)) {
                        return parsed.uid;
                     } else if (!blank(parsed.runUid)) {
                        return parsed.runUid;
                     } else {
                        return !blank(parsed.id) ? parsed.id : null;
                     }
                  } else {
                     return null;
                  }
               }
            )
            .exceptionally(ex -> null);
      } else {
         return CompletableFuture.completedFuture(null);
      }
   }

   public void reportFinishIfAllowed(DungeonModels.DungeonRunFinishRequest request) {
      if (this.canReport()) {
         if (request != null) {
            if (!blank(request.clientRunId) || !blank(request.uid)) {
               if (!blank(request.playerUuid) && !blank(request.playerName)) {
                  if (blank(request.reporterUuid)) {
                     request.reporterUuid = request.playerUuid;
                  }

                  if (blank(request.reporterName)) {
                     request.reporterName = request.playerName;
                  }

                  if (!blank(request.serverKey)) {
                     if (!blank(request.status)) {
                        request.nonce = UUID.randomUUID().toString();
                        String json = JsonUtil.GSON.toJson(request);
                        this.http.postJson("/v1/dungeons/runs/finish", json).thenAccept(resp -> {}).exceptionally(ex -> null);
                     }
                  }
               }
            }
         }
      }
   }

   public void reportLostLivesIfAllowed(DungeonModels.DungeonLostLivesRequest request) {
      if (this.canReport()) {
         if (request != null) {
            if (!blank(request.clientRunId) || !blank(request.uid) || !blank(request.runMergeKey)) {
               if (!blank(request.playerUuid) && !blank(request.playerName)) {
                  if (blank(request.reporterUuid)) {
                     request.reporterUuid = request.playerUuid;
                  }

                  if (blank(request.reporterName)) {
                     request.reporterName = request.playerName;
                  }

                  if (!blank(request.serverKey)) {
                     if (request.lostLivesNames != null && !request.lostLivesNames.isEmpty()) {
                        request.nonce = UUID.randomUUID().toString();
                        String json = JsonUtil.GSON.toJson(request);
                        this.http.postJson("/v1/dungeons/runs/lost-lives", json).thenAccept(resp -> {}).exceptionally(ex -> null);
                     }
                  }
               }
            }
         }
      }
   }

   public void reportCooldownIfAllowed(DungeonModels.DungeonCooldownRequest request) {
      if (this.canReport()) {
         if (request != null) {
            if (!blank(request.clientRunId) || !blank(request.uid)) {
               if (!blank(request.playerUuid) && !blank(request.playerName)) {
                  if (blank(request.reporterUuid)) {
                     request.reporterUuid = request.playerUuid;
                  }

                  if (blank(request.reporterName)) {
                     request.reporterName = request.playerName;
                  }

                  if (!blank(request.serverKey)) {
                     if (!blank(request.reason)) {
                        request.nonce = UUID.randomUUID().toString();
                        String json = JsonUtil.GSON.toJson(request);
                        this.http.postJson("/v1/dungeons/runs/cooldown", json).thenAccept(resp -> {}).exceptionally(ex -> null);
                     }
                  }
               }
            }
         }
      }
   }

   private boolean canReport() {
      return this.canReport.getAsBoolean();
   }

   private static boolean blank(String value) {
      return value == null || value.isBlank();
   }
}
