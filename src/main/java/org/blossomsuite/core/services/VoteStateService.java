package org.blossomsuite.core.services;

import org.blossomsuite.core.net.SuiteHttp;
import org.blossomsuite.core.services.models.VoteModels;
import org.blossomsuite.core.util.JsonUtil;
import org.blossomsuite.core.util.SuiteLog;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;

public final class VoteStateService {
   private static final String SYNC_PATH = "/v1/vote/state";
   private static final long BOSSBAR_STALE_RESET_MS = 15000L;
   private final SuiteHttp http;
   private final BooleanSupplier canSync;
   private final Function<String, String> normalizer;
   private final Consumer<Runnable> mainThreadExecutor;
   private final Consumer<VoteModels.VoteStateSyncResponse> aggregateSink;
   private final BiConsumer<String, String> mismatchWarningSink;
   private final LongSupplier sessionGenerationSupplier;
   private final AtomicLong lastSyncMs = new AtomicLong(0L);
   private final long minIntervalMs = 1200L;

   public VoteStateService(
      SuiteHttp http,
      BooleanSupplier canSync,
      Function<String, String> normalizer,
      Consumer<Runnable> mainThreadExecutor,
      Consumer<VoteModels.VoteStateSyncResponse> aggregateSink,
      BiConsumer<String, String> mismatchWarningSink,
      LongSupplier sessionGenerationSupplier
   ) {
      this.http = http;
      this.canSync = canSync == null ? () -> true : canSync;
      this.normalizer = normalizer == null ? VoteStateService::defaultNormalize : normalizer;
      this.mainThreadExecutor = mainThreadExecutor == null ? Runnable::run : mainThreadExecutor;
      this.aggregateSink = aggregateSink == null ? response -> {} : aggregateSink;
      this.mismatchWarningSink = mismatchWarningSink == null ? (target, snapshot) -> {} : mismatchWarningSink;
      this.sessionGenerationSupplier = sessionGenerationSupplier == null ? () -> 0L : sessionGenerationSupplier;
   }

   public void syncCurrentServerSnapshotIfAllowed(String serverNameOrKey, VoteStateService.Snapshot snapshot) {
      if (this.canSync.getAsBoolean()) {
         if (snapshot != null) {
            String serverKey = this.normalizer.apply(serverNameOrKey);
            if (!serverKey.isBlank()) {
               String snapshotKey = this.normalizer.apply(snapshot.serverKey());
               if (!snapshotKey.isBlank() && !snapshotKey.equals(serverKey)) {
                  this.mismatchWarningSink.accept(serverKey, snapshotKey);
               } else {
                  long now = System.currentTimeMillis();
                  long prev = this.lastSyncMs.get();
                  if (now - prev >= 1200L) {
                     this.lastSyncMs.set(now);
                     VoteModels.VotePartySnapshotDto dto = new VoteModels.VotePartySnapshotDto();
                     dto.serverKey = serverKey;
                     dto.displayName = snapshot.displayName();
                     dto.current = snapshot.current();
                     dto.max = snapshot.max();
                     dto.partyOngoing = snapshot.partyOngoing();
                     dto.seenAt = snapshot.seenAt();
                     dto.countdownActive = snapshot.countdownActive();
                     dto.countdownSecondsRemaining = snapshot.countdownSecondsRemaining();
                     dto.expectedTriggerAt = snapshot.expectedTriggerAt();
                     VoteModels.VoteStateSyncRequest req = new VoteModels.VoteStateSyncRequest();
                     req.snapshot = dto;
                     req.nonce = UUID.randomUUID().toString();
                     String json = JsonUtil.GSON.toJson(req);
                     long requestGeneration = this.sessionGenerationSupplier.getAsLong();
                     this.http
                        .postJson("/v1/vote/state", json)
                        .thenAccept(
                           resp -> {
                              if (resp.statusCode() == 200) {
                                 VoteModels.VoteStateSyncResponse parsed = (VoteModels.VoteStateSyncResponse)JsonUtil.GSON
                                    .fromJson(resp.body(), VoteModels.VoteStateSyncResponse.class);
                                 if (parsed != null && parsed.servers != null) {
                                    this.mainThreadExecutor
                                       .accept(
                                          () -> {
                                             long currentGeneration = this.sessionGenerationSupplier.getAsLong();
                                             if (currentGeneration != requestGeneration) {
                                                SuiteLog.logger()
                                                   .debug(
                                                      "[vote-party] remote response ignored because session changed: request={} current={}",
                                                      requestGeneration,
                                                      currentGeneration
                                                   );
                                             } else {
                                                this.aggregateSink.accept(parsed);
                                             }
                                          }
                                       );
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
   }

   public void expireBossbarStateIfStaleAndSyncIfChanged(String serverNameOrKey, VoteStateService.Snapshot snapshot, long now) {
      if (snapshot != null) {
         boolean changed = snapshot.expireBossbarStateIfStale(now, 15000L);
         if (changed) {
            this.syncCurrentServerSnapshotIfAllowed(serverNameOrKey, snapshot);
         }
      }
   }

   private static String defaultNormalize(String server) {
      return server == null ? "" : server.trim().toLowerCase();
   }

   public interface Snapshot {
      String serverKey();

      String displayName();

      int current();

      int max();

      boolean partyOngoing();

      long seenAt();

      boolean countdownActive();

      Float countdownSecondsRemaining();

      Long expectedTriggerAt();

      boolean expireBossbarStateIfStale(long var1, long var3);
   }
}
