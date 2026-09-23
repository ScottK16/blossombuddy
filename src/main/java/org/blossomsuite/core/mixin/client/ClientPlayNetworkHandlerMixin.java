package org.blossomsuite.core.mixin.client;

import org.blossomsuite.core.jobs.overflow.OverflowTracker;

import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.qol.fishing.FishingAlertController;
import org.blossomsuite.core.services.VoteStateService;
import org.blossomsuite.core.state.SuiteScheduler;
import org.blossomsuite.core.state.SuiteState;
import org.blossomsuite.core.util.SuiteLog;
import org.blossomsuite.core.vote.VoteObservationSource;
import org.blossomsuite.core.vote.VotePartySnapshot;
import org.blossomsuite.core.vote.VoteRuntime;
import org.blossomsuite.core.vote.VoteState;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.boss.BossBar.Color;
import net.minecraft.entity.boss.BossBar.Style;
import net.minecraft.network.packet.s2c.play.BossBarS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.BossBarS2CPacket.Consumer;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {
   private static final Pattern PINATA_COUNTDOWN_PATTERN = Pattern.compile("(?i)(\\d+(?:\\.\\d+)?)\\s+seconds\\s+till\\s+pinataparty");
   private static final Pattern PINATA_HITS_LEFT_PATTERN = Pattern.compile("(?i)pinata:\\s*\\d+\\s+hits\\s+left");
   private static final long NEW_VOTE_BOSSBAR_BIND_DELAY_MS = 1500L;
   private static final Map<UUID, ClientPlayNetworkHandlerMixin.VoteBossbarBinding> SUITECORE_VOTE_BOSSBARS = new ConcurrentHashMap<>();
   private static final Map<UUID, ClientPlayNetworkHandlerMixin.PendingVoteBossbarName> SUITECORE_PENDING_VOTE_BOSSBARS = new ConcurrentHashMap<>();

   private static void suitecore$resetVoteBossbarTracking() {
      SUITECORE_VOTE_BOSSBARS.clear();
      SUITECORE_PENDING_VOTE_BOSSBARS.clear();
   }

   @Inject(method = "onPlaySound", at = @At("HEAD"), cancellable = true)
   private void suitecore$replaceFishingBiteSound(PlaySoundS2CPacket packet, CallbackInfo ci) {
      if (SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
         if (packet.getSound().value() == SoundEvents.ENTITY_FISHING_BOBBER_SPLASH) {
            if (FishingAlertController.isEnabled()) {
               MinecraftClient client = MinecraftClient.getInstance();
               if (client != null) {
                  double x = packet.getX();
                  double y = packet.getY();
                  double z = packet.getZ();
                  float vol = packet.getVolume();
                  float pitch = packet.getPitch();
                  SoundCategory cat = packet.getCategory();
                  ci.cancel();
                  client.execute(() -> {
                     boolean handled = FishingAlertController.tryHandleVanillaFishingSplash(x, y, z);
                     if (!handled) {
                        if (client.world != null) {
                           client.world.playSound(null, x, y, z, SoundEvents.ENTITY_FISHING_BOBBER_SPLASH, cat, vol, pitch);
                        }
                     }
                  });
               }
            }
         }
      }
   }

   @Inject(method = "onBossBar", at = @At("HEAD"))
   private void suitecore$debugBossBar(BossBarS2CPacket packet, CallbackInfo ci) {
      if (SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
         packet.accept(new Consumer() {
            @Override
            public void add(UUID uuid, Text name, float percent, Color color, Style style, boolean darkenSky, boolean dragonMusic, boolean thickenFog) {
               OverflowTracker.INSTANCE.onBarName(uuid, name);
               if (VoteRuntime.hasConfirmedServer()) {
                  ClientPlayNetworkHandlerMixin.suitecore$handleVoteBossbarName(uuid, ClientPlayNetworkHandlerMixin.safe(name), true);
               }
            }

            @Override
            public void remove(UUID uuid) {
               OverflowTracker.INSTANCE.onBarRemoved(uuid);
               ClientPlayNetworkHandlerMixin.SUITECORE_VOTE_BOSSBARS.remove(uuid);
            }

            @Override
            public void updateProgress(UUID uuid, float percent) {
               ClientPlayNetworkHandlerMixin.suitecore$markTrackedVoteBossbarHeartbeat(uuid);
            }

            @Override
            public void updateName(UUID uuid, Text name) {
               OverflowTracker.INSTANCE.onBarName(uuid, name);
               ClientPlayNetworkHandlerMixin.suitecore$handleVoteBossbarName(uuid, ClientPlayNetworkHandlerMixin.safe(name), false);
            }

            @Override
            public void updateStyle(UUID uuid, Color color, Style style) {
               ClientPlayNetworkHandlerMixin.suitecore$markTrackedVoteBossbarHeartbeat(uuid);
            }

            @Override
            public void updateProperties(UUID uuid, boolean darkenSky, boolean dragonMusic, boolean thickenFog) {
               ClientPlayNetworkHandlerMixin.suitecore$markTrackedVoteBossbarHeartbeat(uuid);
            }
         });
      }
   }

   private static void suitecore$handleVoteBossbarName(UUID uuid, String raw, boolean rebindToCurrentServer) {
      if (!VoteRuntime.hasConfirmedServer()) {
         SuiteLog.logger().debug("[vote-party] bossbar binding rejected because server is unknown/transitioning");
      } else if (raw != null && !raw.isBlank()) {
         if (!rebindToCurrentServer && SUITECORE_VOTE_BOSSBARS.containsKey(uuid)) {
            suitecore$applyVoteBossbarName(uuid, raw, rebindToCurrentServer, System.currentTimeMillis(), VoteRuntime.sessionGeneration());
         } else {
            if (PINATA_HITS_LEFT_PATTERN.matcher(raw).find() || PINATA_COUNTDOWN_PATTERN.matcher(raw).find()) {
               suitecore$scheduleVoteBossbarName(uuid, raw, rebindToCurrentServer, System.currentTimeMillis(), VoteRuntime.sessionGeneration());
            }
         }
      }
   }

   private static void suitecore$applyVoteBossbarName(UUID uuid, String raw, boolean rebindToCurrentServer, long seenAt, long sessionGeneration) {
      if (VoteRuntime.hasConfirmedServer()) {
         if (raw != null && !raw.isBlank()) {
            if (sessionGeneration != VoteRuntime.sessionGeneration()) {
               SuiteLog.logger()
                  .debug(
                     "[vote-party] bossbar binding rejected because session changed: task={} current={}", sessionGeneration, VoteRuntime.sessionGeneration()
                  );
            } else if (PINATA_HITS_LEFT_PATTERN.matcher(raw).find()) {
               ClientPlayNetworkHandlerMixin.VoteBossbarBinding binding = suitecore$getOrBindVoteBossbar(uuid, rebindToCurrentServer, sessionGeneration);
               if (binding != null) {
                  VotePartySnapshot snapshot = VoteState.getOrCreate(binding.serverKey(), binding.displayName(), seenAt);
                  if (snapshot != null) {
                     snapshot.markBossbarVoteSeen(seenAt);
                     boolean changed = snapshot.setPartyOngoingIfChanged(true, seenAt, VoteObservationSource.BOSSBAR, 90, sessionGeneration);
                     if (changed) {
                        VoteStateService svc = SuiteState.INSTANCE.voteStateService;
                        if (svc != null) {
                           svc.syncCurrentServerSnapshotIfAllowed(binding.serverKey(), snapshot);
                        }
                     }
                  }
               }
            } else {
               Matcher countdownMatcher = PINATA_COUNTDOWN_PATTERN.matcher(raw);
               if (countdownMatcher.find()) {
                  ClientPlayNetworkHandlerMixin.VoteBossbarBinding binding = suitecore$getOrBindVoteBossbar(uuid, rebindToCurrentServer, sessionGeneration);
                  if (binding == null) {
                     return;
                  }

                  float secondsRemaining = Float.parseFloat(countdownMatcher.group(1));
                  VotePartySnapshot snapshot = VoteState.getOrCreate(binding.serverKey(), binding.displayName(), seenAt);
                  if (snapshot == null) {
                     return;
                  }

                  snapshot.markBossbarVoteSeen(seenAt);
                  boolean changed = snapshot.updateCountdownIfChanged(secondsRemaining, seenAt, VoteObservationSource.BOSSBAR, 90, sessionGeneration);
                  if (secondsRemaining <= 0.05F) {
                     changed |= snapshot.setPartyOngoingIfChanged(true, seenAt, VoteObservationSource.BOSSBAR, 90, sessionGeneration);
                  } else {
                     changed |= snapshot.setPartyOngoingIfChanged(false, seenAt, VoteObservationSource.BOSSBAR, 90, sessionGeneration);
                  }

                  if (changed) {
                     VoteStateService svc = SuiteState.INSTANCE.voteStateService;
                     if (svc != null) {
                        svc.syncCurrentServerSnapshotIfAllowed(binding.serverKey(), snapshot);
                     }
                  }
               }
            }
         }
      }
   }

   private static void suitecore$scheduleVoteBossbarName(UUID uuid, String raw, boolean rebindToCurrentServer, long seenAt, long sessionGeneration) {
      if (uuid != null) {
         SUITECORE_PENDING_VOTE_BOSSBARS.put(
            uuid, new ClientPlayNetworkHandlerMixin.PendingVoteBossbarName(raw, rebindToCurrentServer, seenAt, sessionGeneration)
         );
         SuiteScheduler.IO.schedule(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
               client.execute(() -> {
                  ClientPlayNetworkHandlerMixin.PendingVoteBossbarName pending = SUITECORE_PENDING_VOTE_BOSSBARS.remove(uuid);
                  if (pending != null) {
                     suitecore$applyVoteBossbarName(uuid, pending.raw(), pending.rebindToCurrentServer(), pending.seenAt(), pending.sessionGeneration());
                  }
               });
            }
         }, 1500L, TimeUnit.MILLISECONDS);
      }
   }

   private static void suitecore$markTrackedVoteBossbarHeartbeat(UUID uuid) {
      if (uuid != null) {
         ClientPlayNetworkHandlerMixin.VoteBossbarBinding binding = SUITECORE_VOTE_BOSSBARS.get(uuid);
         if (binding != null) {
            if (binding.sessionGeneration() != VoteRuntime.sessionGeneration()) {
               SUITECORE_VOTE_BOSSBARS.remove(uuid);
               SuiteLog.logger()
                  .debug(
                     "[vote-party] bossbar heartbeat rejected because session changed: binding={} current={}",
                     binding.sessionGeneration(),
                     VoteRuntime.sessionGeneration()
                  );
            } else {
               String confirmedServerKey = VoteState.normalize(VoteRuntime.confirmedServer());
               if (!confirmedServerKey.isBlank() && confirmedServerKey.equals(binding.serverKey())) {
                  long now = System.currentTimeMillis();
                  VotePartySnapshot snapshot = VoteState.getOrCreate(binding.serverKey(), binding.displayName(), now);
                  if (snapshot != null) {
                     snapshot.markBossbarVoteSeen(now);
                  }
               } else {
                  SUITECORE_VOTE_BOSSBARS.remove(uuid);
               }
            }
         }
      }
   }

   private static ClientPlayNetworkHandlerMixin.VoteBossbarBinding suitecore$getOrBindVoteBossbar(
      UUID uuid, boolean rebindToCurrentServer, long sessionGeneration
   ) {
      if (uuid == null) {
         return null;
      }

      if (!VoteRuntime.hasConfirmedServer()) {
         return null;
      }

      if (sessionGeneration != VoteRuntime.sessionGeneration()) {
         return null;
      }

      String displayName = VoteRuntime.confirmedDisplayName();
      String serverKey = VoteState.normalize(VoteRuntime.confirmedServer());
      if (serverKey.isBlank()) {
         return null;
      }

      ClientPlayNetworkHandlerMixin.VoteBossbarBinding existing = SUITECORE_VOTE_BOSSBARS.get(uuid);
      if (existing != null && !rebindToCurrentServer && serverKey.equals(existing.serverKey()) && sessionGeneration == existing.sessionGeneration()) {
         return existing;
      }

      ClientPlayNetworkHandlerMixin.VoteBossbarBinding binding = new ClientPlayNetworkHandlerMixin.VoteBossbarBinding(serverKey, displayName, sessionGeneration);
      SUITECORE_VOTE_BOSSBARS.put(uuid, binding);
      return binding;
   }

   private static String safe(Text text) {
      return text == null ? "<null>" : text.getString();
   }

   private record PendingVoteBossbarName(String raw, boolean rebindToCurrentServer, long seenAt, long sessionGeneration) {
   }

   private record VoteBossbarBinding(String serverKey, String displayName, long sessionGeneration) {
   }
}
