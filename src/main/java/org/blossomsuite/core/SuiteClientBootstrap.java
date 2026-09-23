package org.blossomsuite.core;

import org.blossomsuite.core.commands.BuddyCommands;

import org.blossomsuite.core.emote.EmoteClient;
import org.blossomsuite.core.presence.PresenceClient;
import org.blossomsuite.core.stats.StatsClient;
import org.blossomsuite.core.xchat.XChatClient;

import org.blossomsuite.core.state.SidebarReader;

import org.blossomsuite.core.config.FeatureConfig;

import org.blossomsuite.core.config.LegacyMigration;
import org.blossomsuite.core.jobs.overflow.OverflowCommands;
import org.blossomsuite.core.jobs.overflow.OverflowTracker;

import org.blossomsuite.core.alts.AltResourceState;
import org.blossomsuite.core.chat.ChatModeProbe;
import org.blossomsuite.core.chat.ChatOutput;
import org.blossomsuite.core.chat.ChatRuntime;
import org.blossomsuite.core.chat.PartyChatState;
import org.blossomsuite.core.chat.StaffChatState;
import org.blossomsuite.core.chat.VanishState;
import org.blossomsuite.core.commands.SuiteCommands;
import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.RemoteConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.cooldowns.CooldownJingle;
import org.blossomsuite.core.cooldowns.CooldownModule;
import org.blossomsuite.core.cooldowns.CooldownRules;
import org.blossomsuite.core.cooldowns.CooldownRuntime;
import org.blossomsuite.core.events.ChatEvents;
import org.blossomsuite.core.events.TickEvents;
import org.blossomsuite.core.hud.HudCallbacks;
import org.blossomsuite.core.hud.HudEditScreen;
import org.blossomsuite.core.hud.HudEditState;
import org.blossomsuite.core.hud.JobsHud;
import org.blossomsuite.core.jobs.JobsCaptureState;
import org.blossomsuite.core.jobs.JobsChattextSetup;
import org.blossomsuite.core.jobs.JobsModule;
import org.blossomsuite.core.jobs.JobsRuntime;
import org.blossomsuite.core.jobs.JobsTracker;
import org.blossomsuite.core.keybinds.KeybindRuntime;
import org.blossomsuite.core.qol.autodrop.AutoDropper;
import org.blossomsuite.core.qol.autofly.AutoFlyController;
import org.blossomsuite.core.qol.autoswap.AutoToolSwapHook;
import org.blossomsuite.core.qol.fishing.FishingAlertController;
import org.blossomsuite.core.qol.holepuncher.HolePuncher;
import org.blossomsuite.core.remote.RemoteConfigClient;
import org.blossomsuite.core.render.WorldOverlays;
import org.blossomsuite.core.services.ConfigManifestService;
import org.blossomsuite.core.services.ModInfoService;
import org.blossomsuite.core.services.RelayService;
import org.blossomsuite.core.services.VoteStateService;
import org.blossomsuite.core.services.models.RelayModels;
import org.blossomsuite.core.state.SuiteScheduler;
import org.blossomsuite.core.state.SuiteState;
import org.blossomsuite.core.storage.SegmentStore;
import org.blossomsuite.core.tick.TickProcessor;
import org.blossomsuite.core.tick.TickRuntime;
import org.blossomsuite.core.ui.SuiteSettingsScreen;
import org.blossomsuite.core.util.WorldGate;
import org.blossomsuite.core.vote.VotePartySnapshot;
import org.blossomsuite.core.vote.VoteRuntime;
import org.blossomsuite.core.vote.VoteState;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.sound.SoundCategory;

public final class SuiteClientBootstrap {
   private static final long MODINFO_POLL_MINUTES = 30L;
   private static final long VOTE_POLL_SECONDS = 30L;

   private SuiteClientBootstrap() {
   }

   public static void initialize(SuiteProfile profile, SuiteClientBootstrap.Hooks hooks) {
      Objects.requireNonNull(hooks, "hooks");
      SuiteRuntime.initialize(profile);
      ConfigIO.setErrorReporter(hooks.errorReporter());
      ConfigIO.setAltResourceSnapshotLoader(AltResourceState::loadPersistentSnapshots);
      AutoDropper.setChatReporter(ChatOutput::info);
      if (feature(SuiteFeature.HOLE_PUNCHER)) {
         AutoDropper.setExternalMiningHooks(HolePuncher::isRunning, HolePuncher::onInventoryInterrupted);
         AutoToolSwapHook.setExternalBlocker(HolePuncher::isRunning);
      } else {
         AutoDropper.setExternalMiningHooks(() -> false, client -> {});
         AutoToolSwapHook.setExternalBlocker(() -> false);
      }

      CooldownRuntime.setRealmSupplier(() -> JobsChattextSetup.realmName);
      CooldownRuntime.setRelaySupplier(() -> SuiteState.INSTANCE.relay);
      CooldownRuntime.setChatReporter(ChatOutput::info);
      ChatRuntime.setRealmSupplier(() -> JobsChattextSetup.realmName);
      SuiteState.setWarnReporter(hooks.stateWarnReporter());
      JobsRuntime.setRealmSupplier(() -> JobsChattextSetup.realmName);
      KeybindRuntime.setSettingsOpener(hooks.settingsOpener());
      KeybindRuntime.setHudEditorOpener(hooks.hudEditorOpener());
      KeybindRuntime.setRentalsPauseToggler(SuiteCommands::toggleRentalsPaused);
      KeybindRuntime.setExpiredRentalsClearer(SuiteCommands::clearExpiredRentals);
      KeybindRuntime.setWarnReporter(hooks.warnReporter());
      TickRuntime.setHudEditScreenPredicate(hooks.hudEditScreenPredicate());
      TickRuntime.setSettingsRequestHandlers(hooks.settingsOpenRequested(), hooks.clearSettingsOpenRequest(), hooks.settingsOpener());
      TickRuntime.setHudEditRequestHandlers(
         hooks.hudEditOpenRequested(),
         hooks.clearHudEditOpenRequest(),
         hooks.requeueHudEditOpenRequest(),
         hooks.hudEditorOpener(),
         hooks.hudEditCloseRequested(),
         hooks.clearHudEditCloseRequest()
      );
      TickRuntime.setWarnReporter(hooks.warnReporter());
      SuiteCommands.configure(hooks.requestSettingsOpen(), hooks.requestHudEditOpen(), hooks.infoLogger(), hooks.warnReporter());
      WorldGate.setResetHook(() -> {
         if (feature(SuiteFeature.AUTO_FLY)) {
            AutoFlyController.reset();
         }

         JobsChattextSetup.resetForRealm();
      });
      WorldGate.setRealmSetHook((server, client) -> {
         JobsChattextSetup.resetForRealm();
         JobsChattextSetup.sentAtMs = System.currentTimeMillis();
         JobsChattextSetup.realmName = server;
         if (feature(SuiteFeature.AUTO_FLY)) {
            AutoFlyController.onServerSet(server, client, System.currentTimeMillis());
         }

         if (client != null && client.getNetworkHandler() != null) {
            if (SuiteConfig.INSTANCE.JobsConfig.capture) {
               client.getNetworkHandler().sendChatCommand("jobs toggle actionbar batched");
            } else {
               client.getNetworkHandler().sendChatCommand("jobs toggle actionbar off");
            }

            // Always probe which chat channel the player is in - PublicChatSendState (the welcome message / advertiser
            // "send to public chat" feature) needs this regardless of whether the tracked-channel HUD is shown.
            ChatModeProbe.requestProbe();

            JobsChattextSetup.doneThisRealm = true;
         }
      });
      WorldGate.setActiveLogger(server -> hooks.info("[WORLDGATE RUNNING] " + server));
      WorldGate.setBecameActiveHook(() -> {
         JobsTracker t = JobsModule.tracker();
         if (t != null) {
            t.resumeFromInactiveWorld();
         }
      });
      VoteRuntime.setServerSupplier(() -> WorldGate.Server);
      VoteRuntime.setActiveWorldSupplier(WorldGate::isActive);
      VoteRuntime.setConfirmedServerHook(() -> {
         String server = VoteRuntime.confirmedServer();
         if (server != null && !server.isBlank()) {
            WorldGate.SetRealm(server);
         }
      });
      VoteRuntime.setTemporaryStateResetHook(() -> {});
      RemoteConfigClient.setChatReporter(ChatOutput::info);
      RemoteConfigClient.setInfoReporter(hooks.infoReporter());
      RemoteConfigClient.setWarnReporter((message, throwable) -> {
         if (throwable == null) {
            hooks.warn(message);
         } else {
            hooks.warn(message, throwable);
         }
      });
      hooks.info(profile.displayName() + " client initialized");
      LegacyMigration.run();
      FeatureConfig.init();
      BuddyKeys.init();
      SuiteState.INSTANCE.init(RemoteConfig.baseUrl(), RemoteConfig.ingestKey());
      SuiteState.INSTANCE.http.setBaseUrl(FeatureConfig.effectiveRelayUrl());
      SidebarReader.init();
      XChatClient.INSTANCE.init();
      StatsClient.INSTANCE.init();
      PresenceClient.INSTANCE.init();
      EmoteClient.INSTANCE.init();
      ConfigIO.load();
      CooldownJingle.configure(() -> SuiteConfig.INSTANCE.CooldownsConfig.completeSoundVolume, SoundCategory.MASTER);
      FishingAlertController.configure(
         () -> new FishingAlertController.Settings(
            SuiteConfig.INSTANCE.QolConfig.fishingEnabled,
            SuiteConfig.INSTANCE.QolConfig.fishingSoundId,
            SuiteConfig.INSTANCE.QolConfig.fishingVolume,
            SuiteConfig.INSTANCE.QolConfig.fishingPitch,
            SuiteConfig.INSTANCE.QolConfig.fishingCooldownMs,
            SoundCategory.MASTER
         )
      );

      try {
         Path dir = FabricLoader.getInstance().getConfigDir().resolve(SuiteRuntime.profile().modId());
         SegmentStore.init(dir.resolve("segments.jsonl"), dir.resolve("segements.jsonl"));
      } catch (Exception e) {
         hooks.warn("SegmentStore init failed", e);
      }

      if (feature(SuiteFeature.JOBS)) {
         JobsModule.init();
         OverflowTracker.INSTANCE.init();
      }

      if (feature(SuiteFeature.COOLDOWNS)) {
         CooldownModule.init();
         CooldownRules.init();
      }

      if (feature(SuiteFeature.JOBS)) {
         JobsHud.init(JobsModule.tracker());
      }

      ChatEvents.register();
      TickEvents.register(TickProcessor::process);
      RelayService relay = SuiteState.INSTANCE.relay;
      ModInfoService modInfo = SuiteState.INSTANCE.modInfoService;
      ConfigManifestService configManifest = SuiteState.INSTANCE.configManifestService;
      SuiteScheduler.IO.scheduleAtFixedRate(() -> {
         try {
            if (!SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
               return;
            }

            if (!feature(SuiteFeature.REMOTE_CONFIG)) {
               return;
            }

            modInfo.pollOnce();
         } catch (Throwable var2x) {
         }
      }, 5L, 30L, TimeUnit.MINUTES);
      SuiteScheduler.IO.scheduleAtFixedRate(() -> {
         try {
            if (!SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
               return;
            }

            if (!feature(SuiteFeature.REMOTE_CONFIG)) {
               return;
            }

            if (!SuiteConfig.INSTANCE.RemoteConfig.enabled) {
               return;
            }

            configManifest.pollOnce();
         } catch (Throwable var2x) {
         }
      }, 10L, 60L, TimeUnit.SECONDS);
      SuiteScheduler.IO.scheduleAtFixedRate(() -> {
         try {
            if (!SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
               return;
            }

            if (!feature(SuiteFeature.RELAY)) {
               return;
            }

            if (!SuiteConfig.INSTANCE.RelayConfig.subscribeCooldowns) {
               return;
            }

            RelayService relayService = SuiteState.INSTANCE.relay;
            if (relayService == null) {
               return;
            }

            relayService.pollIfAllowed(SuiteConfig.INSTANCE.CooldownsConfig.showHud, SuiteConfig.INSTANCE.RelayConfig.linkId, JobsChattextSetup.realmName);
         } catch (Throwable var1) {
         }
      }, 1L, 1L, TimeUnit.SECONDS);
      SuiteScheduler.IO
         .scheduleAtFixedRate(
            () -> {
               try {
                  if (!SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
                     return;
                  }

                  if (!feature(SuiteFeature.RELAY)) {
                     return;
                  }

                  if (!SuiteConfig.INSTANCE.RelayConfig.publishCooldowns) {
                     return;
                  }

                  if (SuiteConfig.INSTANCE.RelayConfig.linkId == null || SuiteConfig.INSTANCE.RelayConfig.linkId.isBlank()) {
                     return;
                  }

                  if (JobsChattextSetup.realmName == null || JobsChattextSetup.realmName.isBlank()) {
                     return;
                  }

                  MinecraftClient client = MinecraftClient.getInstance();
                  if (client.player == null) {
                     return;
                  }

                  if (relay == null) {
                     return;
                  }

                  List<RelayModels.RelayCooldownEntry> snapshot = CooldownRules.buildCooldownSnapshot();
                  relay.publishSnapshotIfAllowed(
                     true, SuiteConfig.INSTANCE.RelayConfig.linkId, JobsChattextSetup.realmName, client.player.getName().getString(), snapshot
                  );
               } catch (Throwable var3x) {
               }
            },
            15L,
            60L,
            TimeUnit.SECONDS
         );
      SuiteScheduler.IO.scheduleAtFixedRate(() -> {
         try {
            if (!SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
               return;
            }

            if (!feature(SuiteFeature.VOTE)) {
               return;
            }

            if (!SuiteConfig.INSTANCE.VoteConfig.showHud && !FeatureConfig.INSTANCE.relay.share) {
               return;
            }

            if (!VoteRuntime.hasConfirmedServer()) {
               return;
            }

            VoteStateService svc = SuiteState.INSTANCE.voteStateService;
            if (svc == null) {
               return;
            }

            long now = System.currentTimeMillis();
            String server = VoteRuntime.confirmedServer();
            VotePartySnapshot snap = VoteState.getOrCreate(server, VoteRuntime.confirmedDisplayName(), now);
            if (snap == null) {
               return;
            }

            svc.syncCurrentServerSnapshotIfAllowed(server, snap);
         } catch (Throwable var5x) {
         }
      }, 10L, 10L, TimeUnit.SECONDS);
      ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
         SuiteCommands.register(dispatcher);
         OverflowCommands.register(dispatcher);
         BuddyCommands.registerTopLevel(dispatcher);
      });
      ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
         if (client != null) {
            VoteRuntime.onConnectionSessionChanged("disconnect");
            WorldGate.reset();
            PartyChatState.resetForJoin();
            StaffChatState.resetForJoin();
            ChatModeProbe.resetForJoin();
            VanishState.resetForJoin();
            if (SuiteConfig.INSTANCE.isEnabled()) {
               if (feature(SuiteFeature.JOBS)) {
                  if (JobsModule.tracker() != null) {
                     SuiteConfig cfg = SuiteConfig.INSTANCE;
                     if (!cfg.JobsConfig.sessionRollover) {
                        cfg.JobsConfig.clearSessionRolloverData();
                        ConfigIO.saveIfDirty();
                     } else {
                        long now = System.currentTimeMillis();
                        JobsTracker.SessionSnapshot snap = JobsModule.tracker().snapshotSession(now);
                        cfg.JobsConfig.setSessionRolloverData(snap.activeMs(), snap.money(), snap.exp());
                        ConfigIO.saveIfDirty();
                     }
                  }
               }
            }
         }
      });
      ClientPlayConnectionEvents.JOIN
         .register(
            (handler, sender, client) -> {
               if (client != null) {
                  VoteRuntime.onConnectionSessionChanged("join");
                  PartyChatState.resetForJoin();
                  StaffChatState.resetForJoin();
                  ChatModeProbe.resetForJoin();
                  VanishState.resetForJoin();
                  if (feature(SuiteFeature.JOBS)) {
                     JobsCaptureState.resetForJoin(System.currentTimeMillis());
                  }

                  SuiteConfig cfg = SuiteConfig.INSTANCE;
                  if (cfg.isEnabledForCurrentWorld()) {
                     if (feature(SuiteFeature.JOBS)) {
                        if (!cfg.JobsConfig.sessionRollover) {
                           cfg.JobsConfig.clearSessionRolloverData();
                           ConfigIO.saveIfDirty();
                        } else if (cfg.JobsConfig.sessionRolloverHasData) {
                           client.execute(
                              () -> {
                                 if (client.player != null) {
                                    if (JobsModule.tracker() != null) {
                                       long now = System.currentTimeMillis();
                                       JobsModule.tracker()
                                          .restoreSessionPaused(
                                             now,
                                             cfg.JobsConfig.sessionRolloverActiveMs,
                                             cfg.JobsConfig.sessionRolloverMoney,
                                             cfg.JobsConfig.sessionRolloverExp
                                          );
                                    }
                                 }
                              }
                           );
                        }
                     }
                  }
               }
            }
         );
      if (feature(SuiteFeature.AUTO_SWAPPER)) {
         AutoToolSwapHook.init();
      }

      HudCallbacks.init();
      WorldOverlays.init();
   }

   private static boolean feature(SuiteFeature feature) {
      return SuiteRuntime.isEnabled(feature);
   }

   public static final class Hooks {
      private final Consumer<String> infoReporter;
      private final BiConsumer<String, Throwable> warnReporter;
      private final BiConsumer<String, Object[]> stateWarnReporter;
      private final BiConsumer<String, Throwable> errorReporter;
      private final Consumer<MinecraftClient> settingsOpener;
      private final Consumer<MinecraftClient> hudEditorOpener;
      private final Predicate<Screen> hudEditScreenPredicate;
      private final BooleanSupplier settingsOpenRequested;
      private final Runnable clearSettingsOpenRequest;
      private final BooleanSupplier hudEditOpenRequested;
      private final Runnable clearHudEditOpenRequest;
      private final Runnable requeueHudEditOpenRequest;
      private final BooleanSupplier hudEditCloseRequested;
      private final Runnable clearHudEditCloseRequest;

      public Hooks(
         Consumer<String> infoReporter,
         BiConsumer<String, Throwable> warnReporter,
         BiConsumer<String, Object[]> stateWarnReporter,
         BiConsumer<String, Throwable> errorReporter,
         Consumer<MinecraftClient> settingsOpener,
         Consumer<MinecraftClient> hudEditorOpener,
         Predicate<Screen> hudEditScreenPredicate,
         BooleanSupplier settingsOpenRequested,
         Runnable clearSettingsOpenRequest,
         BooleanSupplier hudEditOpenRequested,
         Runnable clearHudEditOpenRequest,
         Runnable requeueHudEditOpenRequest,
         BooleanSupplier hudEditCloseRequested,
         Runnable clearHudEditCloseRequest
      ) {
         this.infoReporter = Objects.requireNonNull(infoReporter, "infoReporter");
         this.warnReporter = Objects.requireNonNull(warnReporter, "warnReporter");
         this.stateWarnReporter = Objects.requireNonNull(stateWarnReporter, "stateWarnReporter");
         this.errorReporter = Objects.requireNonNull(errorReporter, "errorReporter");
         this.settingsOpener = Objects.requireNonNull(settingsOpener, "settingsOpener");
         this.hudEditorOpener = Objects.requireNonNull(hudEditorOpener, "hudEditorOpener");
         this.hudEditScreenPredicate = Objects.requireNonNull(hudEditScreenPredicate, "hudEditScreenPredicate");
         this.settingsOpenRequested = Objects.requireNonNull(settingsOpenRequested, "settingsOpenRequested");
         this.clearSettingsOpenRequest = Objects.requireNonNull(clearSettingsOpenRequest, "clearSettingsOpenRequest");
         this.hudEditOpenRequested = Objects.requireNonNull(hudEditOpenRequested, "hudEditOpenRequested");
         this.clearHudEditOpenRequest = Objects.requireNonNull(clearHudEditOpenRequest, "clearHudEditOpenRequest");
         this.requeueHudEditOpenRequest = Objects.requireNonNull(requeueHudEditOpenRequest, "requeueHudEditOpenRequest");
         this.hudEditCloseRequested = Objects.requireNonNull(hudEditCloseRequested, "hudEditCloseRequested");
         this.clearHudEditCloseRequest = Objects.requireNonNull(clearHudEditCloseRequest, "clearHudEditCloseRequest");
      }

      public static SuiteClientBootstrap.Hooks headless(
         Consumer<String> infoReporter,
         BiConsumer<String, Throwable> warnReporter,
         BiConsumer<String, Object[]> stateWarnReporter,
         BiConsumer<String, Throwable> errorReporter
      ) {
         return new SuiteClientBootstrap.Hooks(
            infoReporter,
            warnReporter,
            stateWarnReporter,
            errorReporter,
            client -> {
               MinecraftClient mc = MinecraftClient.getInstance();
               mc.setScreen(new SuiteSettingsScreen(mc.currentScreen));
            },
            client -> {
               HudEditState.editMode = true;
               client.setScreen(new HudEditScreen());
            },
            screen -> screen instanceof HudEditScreen,
            () -> SuiteSettingsScreen.requestOpenOptionsScreen,
            () -> SuiteSettingsScreen.requestOpenOptionsScreen = false,
            () -> HudEditScreen.requestOpenEditScreen,
            () -> HudEditScreen.requestOpenEditScreen = false,
            () -> HudEditScreen.requestOpenEditScreen = true,
            () -> false,
            () -> {}
         );
      }

      private Consumer<String> infoReporter() {
         return this.infoReporter;
      }

      private BiConsumer<String, Throwable> infoLogger() {
         return (message, throwable) -> this.infoReporter.accept(message);
      }

      private BiConsumer<String, Throwable> warnReporter() {
         return this.warnReporter;
      }

      private BiConsumer<String, Object[]> stateWarnReporter() {
         return this.stateWarnReporter;
      }

      private BiConsumer<String, Throwable> errorReporter() {
         return this.errorReporter;
      }

      private Consumer<MinecraftClient> settingsOpener() {
         return this.settingsOpener;
      }

      private Consumer<MinecraftClient> hudEditorOpener() {
         return this.hudEditorOpener;
      }

      private Predicate<Screen> hudEditScreenPredicate() {
         return this.hudEditScreenPredicate;
      }

      private BooleanSupplier settingsOpenRequested() {
         return this.settingsOpenRequested;
      }

      private Runnable clearSettingsOpenRequest() {
         return this.clearSettingsOpenRequest;
      }

      private BooleanSupplier hudEditOpenRequested() {
         return this.hudEditOpenRequested;
      }

      private Runnable clearHudEditOpenRequest() {
         return this.clearHudEditOpenRequest;
      }

      private Runnable requeueHudEditOpenRequest() {
         return this.requeueHudEditOpenRequest;
      }

      private BooleanSupplier hudEditCloseRequested() {
         return this.hudEditCloseRequested;
      }

      private Runnable clearHudEditCloseRequest() {
         return this.clearHudEditCloseRequest;
      }

      private Runnable requestSettingsOpen() {
         return () -> SuiteSettingsScreen.requestOpenOptionsScreen = true;
      }

      private Runnable requestHudEditOpen() {
         return () -> HudEditScreen.requestOpenEditScreen = true;
      }

      private void info(String message) {
         this.infoReporter.accept(message);
      }

      private void warn(String message) {
         this.warnReporter.accept(message, null);
      }

      private void warn(String message, Throwable throwable) {
         this.warnReporter.accept(message, throwable);
      }
   }
}
