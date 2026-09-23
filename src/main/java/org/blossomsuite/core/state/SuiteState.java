package org.blossomsuite.core.state;

import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.chat.ChatOutput;
import org.blossomsuite.core.config.FeatureConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.cooldowns.CooldownRules;
import org.blossomsuite.core.net.EtagCache;
import org.blossomsuite.core.net.SuiteHttp;
import org.blossomsuite.core.services.ConfigManifestService;
import org.blossomsuite.core.services.CooldownsService;
import org.blossomsuite.core.services.ModInfoService;
import org.blossomsuite.core.services.RelayService;
import org.blossomsuite.core.services.VoteStateService;
import org.blossomsuite.core.services.models.VoteModels;
import org.blossomsuite.core.util.SuiteLog;
import org.blossomsuite.core.util.SuiteModInfo;
import org.blossomsuite.core.vote.VotePartySnapshot;
import org.blossomsuite.core.vote.VoteRuntime;
import org.blossomsuite.core.vote.VoteState;
import java.util.function.BiConsumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class SuiteState {
   public static final SuiteState INSTANCE = new SuiteState();
   private static BiConsumer<String, Object[]> warnReporter = (message, args) -> {};
   public SuiteHttp http;
   public EtagCache etags;
   public RelayService relay;
   public ModInfoService modInfoService;
   public ConfigManifestService configManifestService;
   public CooldownsService cooldownsService;
   public VoteStateService voteStateService;

   private SuiteState() {
   }

   public static void setWarnReporter(BiConsumer<String, Object[]> reporter) {
      warnReporter = reporter != null ? reporter : (message, args) -> {};
   }

   public void init(String baseUrl, String ingestKey) {
      if (this.relay == null) {
         this.http = new SuiteHttp(baseUrl, ingestKey, SuiteRuntime.profile().ingestHeaderName());
         this.etags = new EtagCache();
         this.relay = new RelayService(this.http, this.etags);
         this.modInfoService = new ModInfoService(this.http, this.etags, SuiteModInfo.getVersion(), SuiteState::showModUpdateNotice);
         this.cooldownsService = new CooldownsService(this.http, this.etags, CooldownRules::applyFromJson, SuiteState::showClientThreadMessage);
         this.configManifestService = new ConfigManifestService(this.http, this.etags, this.cooldownsService, ChatOutput::info);
         this.voteStateService = new VoteStateService(
            this.http,
            () -> FeatureConfig.INSTANCE.relay.share,
            VoteState::normalize,
            SuiteState::executeOnClientThread,
            SuiteState::applyVoteAggregate,
            SuiteState::warnMismatchedVoteSnapshot,
            VoteRuntime::sessionGeneration
         );
      }
   }

   private static void showModUpdateNotice(ModInfoService.UpdateNotice notice) {
      if (notice != null) {
         if (notice.type() == ModInfoService.Type.REQUIRED) {
            ChatOutput.info(requiredUpdateText(notice.currentVersion(), notice.requiredVersion(), notice.latestVersion()));
         } else {
            ChatOutput.info(optionalUpdateText(notice.currentVersion(), notice.latestVersion()));
         }
      }
   }

   private static void showClientThreadMessage(String message) {
      executeOnClientThread(() -> ChatOutput.info(message));
   }

   private static void executeOnClientThread(Runnable action) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null) {
         client.execute(action);
      }
   }

   private static void warnMismatchedVoteSnapshot(String targetServerKey, String snapshotServerKey) {
      warnReporter.accept("[vote-party] refused to sync mismatched snapshot: target={} snapshot={}", new Object[]{targetServerKey, snapshotServerKey});
   }

   private static void applyVoteAggregate(VoteModels.VoteStateSyncResponse response) {
      if (VoteRuntime.isTransitioning()) {
         SuiteLog.logger().debug("[vote-party] remote response ignored while server is unknown/transitioning");
      } else {
         for (VoteModels.VotePartySnapshotDto dto : response.servers) {
            if (dto != null) {
               String key = VoteState.normalize(dto.serverKey != null ? dto.serverKey : dto.displayName);
               if (!key.isBlank()) {
                  if (dto.serverKey != null && !dto.serverKey.isBlank() && !key.equals(VoteState.normalize(dto.serverKey))) {
                     SuiteLog.logger().debug("[vote-party] remote response ignored due invalid server key: {}", dto.serverKey);
                  } else {
                     long observedAt = dto.seenAt != null ? dto.seenAt : 0L;
                     VotePartySnapshot snap = VoteState.getOrCreate(key, dto.displayName != null ? dto.displayName : key, observedAt);
                     snap.applyRemoteState(
                        dto.current, dto.max, dto.partyOngoing, dto.seenAt, dto.countdownActive, dto.countdownSecondsRemaining, dto.expectedTriggerAt
                     );
                  }
               }
            }
         }
      }
   }

   private static Text requiredUpdateText(String current, String required, String latest) {
      return Text.empty()
         .append(Text.literal(SuiteRuntime.profile().displayName() + ": ").formatted(Formatting.LIGHT_PURPLE))
         .append(Text.literal("REQUIRED UPDATE ").formatted(Formatting.RED, Formatting.BOLD))
         .append(Text.literal("(min ").formatted(Formatting.GRAY))
         .append(Text.literal(required).formatted(Formatting.RED))
         .append(Text.literal(", you have ").formatted(Formatting.GRAY))
         .append(Text.literal(current).formatted(Formatting.RED))
         .append(Text.literal(")").formatted(Formatting.GRAY))
         .append(Text.literal(" Latest: ").formatted(Formatting.GRAY))
         .append(Text.literal(latest).formatted(Formatting.WHITE));
   }

   private static Text optionalUpdateText(String current, String latest) {
      return Text.empty()
         .append(Text.literal(SuiteRuntime.profile().displayName() + ": ").formatted(Formatting.LIGHT_PURPLE))
         .append(Text.literal("Optional update available ").formatted(Formatting.YELLOW))
         .append(Text.literal(latest).formatted(Formatting.GOLD, Formatting.BOLD))
         .append(Text.literal(" (you have ").formatted(Formatting.GRAY))
         .append(Text.literal(current).formatted(Formatting.WHITE))
         .append(Text.literal(")").formatted(Formatting.GRAY));
   }
}
