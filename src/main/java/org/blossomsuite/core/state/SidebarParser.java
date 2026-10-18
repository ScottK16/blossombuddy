package org.blossomsuite.core.state;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.alts.AltResourceState;
import org.blossomsuite.core.chat.ChatModeProbe;
import org.blossomsuite.core.chat.ChatOutput;
import org.blossomsuite.core.chat.PublicChatSendState;
import org.blossomsuite.core.chat.StaffChatState;
import org.blossomsuite.core.chat.VanishState;
import org.blossomsuite.core.tps.TpsTracker;
import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.QolConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.jobs.JobsActionBarMode;
import org.blossomsuite.core.jobs.JobsCaptureState;
import org.blossomsuite.core.jobs.JobsModule;
import org.blossomsuite.core.jobs.JobsParser;
import org.blossomsuite.core.jobs.JobsRapidOverlay;
import org.blossomsuite.core.jobs.JobsTracker;
import org.blossomsuite.core.qol.autofly.AutoFlyController;
import org.blossomsuite.core.services.VoteStateService;
import org.blossomsuite.core.state.SuiteState;
import org.blossomsuite.core.util.CrosshairShapeRenderer;
import org.blossomsuite.core.util.CrosshairTintUtil;
import org.blossomsuite.core.util.SuiteLog;
import org.blossomsuite.core.util.TextUtil;
import org.blossomsuite.core.util.WorldGate;
import org.blossomsuite.core.vote.VotePartySnapshot;
import org.blossomsuite.core.vote.VoteRuntime;
import org.blossomsuite.core.vote.VoteState;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Gui;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reads the server's sidebar scoreboard (realm name, vote-party count, balance, ...).
 *
 * <p>This used to run only inside the game's scoreboard <em>drawing</em> code, so a client that replaces the
 * scoreboard, or hiding the HUD, silently stopped it. It is now a plain class that {@link SidebarReader} also runs on
 * a timer straight from the scoreboard data. The 250 ms throttle below is shared, so calling it from both places is
 * harmless.
 */
public final class SidebarParser {
   static final Pattern VOTE_PARTY_PATTERN = Pattern.compile("(?i)vote party:\\s*(\\d+)\\s*/\\s*(\\d+)");
   static final Pattern SERVER_PATTERN = Pattern.compile("(?i)server:\\s*(\\S+)");
   static final Pattern STAFF_CHAT_PATTERN = Pattern.compile("(?i)staff\\s*chat\\s*:\\s*(on|off)");
   static final Pattern WORLD_PATTERN = Pattern.compile("(?i)world\\s*:\\s*(.+)");
   /** Staff see "VISIBILITY: Vanished" (or "Visible") on the sidebar: the server's own word on whether they are in vanish. */
   static final Pattern VISIBILITY_PATTERN = Pattern.compile("(?i)visibility\\s*:\\s*([a-z]+)");
   /** The sidebar shows "TPS: 15.68", the server's own figure. */
   static final Pattern TPS_PATTERN = Pattern.compile("(?i)\\btps\\s*:\\s*(\\d{1,2}(?:\\.\\d+)?)");
   private static long lastSidebarParseAtMs = 0L;

   private SidebarParser() {
   }

   public static void process(Objective objective) {
      if (objective != null) {
         if (SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
            long now = System.currentTimeMillis();
            if (lastSidebarParseAtMs <= 0L || now - lastSidebarParseAtMs >= 250L || WorldGate.Server == null || WorldGate.Server.isBlank()) {
               lastSidebarParseAtMs = now;
               Scoreboard scoreboard = objective.getScoreboard();
               String detectedServer = null;
               String detectedWorld = null;
               Integer detectedCurrent = null;
               Integer detectedMax = null;
               Boolean detectedStaffChatEnabled = null;
               Boolean detectedVanished = null;
               Double detectedTps = null;
               AltResourceState.ScoreboardParse detectedResources = new AltResourceState.ScoreboardParse(null, null, null);

               for (ScoreHolder holder : scoreboard.getTrackedPlayers()) {
                  ReadOnlyScoreInfo score = scoreboard.getPlayerScoreInfo(holder, objective);
                  if (score != null) {
                     String entry = holder.getScoreboardName();
                     PlayerTeam team = scoreboard.getPlayersTeam(entry);
                     String prefix = team != null && team.getPlayerPrefix() != null ? team.getPlayerPrefix().getString() : "";
                     String suffix = team != null && team.getPlayerSuffix() != null ? team.getPlayerSuffix().getString() : "";
                     String[] candidates = new String[]{entry.trim(), (prefix + suffix).trim(), (prefix + entry + suffix).trim()};

                     for (String visible : candidates) {
                        String normalized = normalizeSidebarText(ChatFormatting.stripFormatting(visible));
                        if (!normalized.isBlank()) {
                           AltResourceState.ScoreboardParse resources = AltResourceState.parseScoreboardLine(normalized);
                           if (resources.hasAny()) {
                              detectedResources = mergeResourceParse(detectedResources, resources);
                           }

                           Boolean visibility = parseVisibility(normalized);
                           if (visibility != null) {
                              detectedVanished = visibility;
                           }

                           Double reportedTps = parseTps(normalized);
                           if (reportedTps != null) {
                              detectedTps = reportedTps;
                           }

                           Matcher staffChatMatcher = STAFF_CHAT_PATTERN.matcher(normalized);
                           if (staffChatMatcher.find()) {
                              detectedStaffChatEnabled = "on".equalsIgnoreCase(staffChatMatcher.group(1));
                           } else {
                              Matcher worldMatcher = WORLD_PATTERN.matcher(normalized);
                              if (worldMatcher.find()) {
                                 detectedWorld = worldMatcher.group(1).trim();
                              } else {
                                 Matcher serverMatcher = SERVER_PATTERN.matcher(normalized);
                                 if (serverMatcher.find()) {
                                    detectedServer = SuiteRuntime.profile().resolveServerKey(serverMatcher.group(1));
                                 } else {
                                    Matcher voteMatcher = VOTE_PARTY_PATTERN.matcher(normalized);
                                    if (voteMatcher.find()) {
                                       try {
                                          int current = Integer.parseInt(voteMatcher.group(1));
                                          int max = Integer.parseInt(voteMatcher.group(2));
                                          if (max > 0 && current >= 0 && current <= max) {
                                             detectedCurrent = current;
                                             detectedMax = max;
                                          }
                                       } catch (NumberFormatException ignored) {
                                          SuiteLog.logger().debug("[vote-party] malformed scoreboard vote values rejected: '{}'", normalized);
                                       }
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }

               if (detectedVanished != null && VanishState.vanished != detectedVanished) {
                  VanishState.setVanished(detectedVanished); // the scoreboard is the server's own answer, and covers staff who are vanished on every join
               }

               if (detectedTps != null) {
                  TpsTracker.INSTANCE.noteReported(detectedTps, now);
               }

               if (detectedStaffChatEnabled != null && StaffChatState.trackingEnabled()) {
                  ChatModeProbe.onStaffScoreboardLine(detectedStaffChatEnabled);
                  PublicChatSendState.onStaffChatToggled(detectedStaffChatEnabled);
               }

               if (detectedServer != null && !detectedServer.isBlank()) {
                  WorldGate.noteDetectedRealm(detectedServer, now);
                  if (detectedWorld != null && !detectedWorld.isBlank()) {
                     AutoFlyController.observeScoreboardWorld(detectedServer, detectedWorld, now);
                  }

                  boolean confirmed = VoteRuntime.observeScoreboardServer(detectedServer, now);
                  String confirmedServer = VoteRuntime.confirmedServer();
                  if (detectedCurrent != null && detectedMax != null) {
                     if (confirmed && !VoteRuntime.isTransitioning() && detectedServer.equalsIgnoreCase(confirmedServer)) {
                        if (detectedResources.hasAny()) {
                           AltResourceState.updateScoreboard(confirmedServer, detectedResources, now);
                        }

                        long sessionGeneration = VoteRuntime.sessionGeneration();
                        VotePartySnapshot voteState = VoteState.getOrCreate(confirmedServer, VoteRuntime.confirmedDisplayName(), now);
                        if (voteState != null) {
                           boolean changed = voteState.updateFromScoreboard(detectedCurrent, detectedMax, now, sessionGeneration);
                           if (changed) {
                              VoteStateService svc = SuiteState.INSTANCE.voteStateService;
                              if (svc != null) {
                                 svc.syncCurrentServerSnapshotIfAllowed(confirmedServer, voteState);
                              }
                           }
                        }
                     } else {
                        SuiteLog.logger()
                           .debug(
                              "[vote-party] vote update rejected because server is unknown: detected={} confirmed={} transitioning={}",
                              new Object[]{detectedServer, confirmedServer, VoteRuntime.isTransitioning()}
                           );
                     }
                  } else {
                     if (confirmed && detectedResources.hasAny()) {
                        AltResourceState.updateScoreboard(detectedServer, detectedResources, now);
                     }
                  }
               } else {
                  if (detectedCurrent != null && detectedMax != null) {
                     SuiteLog.logger().debug("[vote-party] vote update rejected because server is unknown");
                  }
               }
            }
         }
      }
   
   }

   /** True for "Visibility: Vanished", false for "Visibility: Visible", null for anything else (so an odd word changes nothing). */
   static Boolean parseVisibility(String normalized) {
      Matcher m = VISIBILITY_PATTERN.matcher(normalized == null ? "" : normalized);
      if (!m.find()) {
         return null;
      }

      String word = m.group(1).toLowerCase(java.util.Locale.ROOT);
      if (word.equals("vanished") || word.equals("vanish") || word.equals("hidden")) {
         return Boolean.TRUE;
      }

      return word.equals("visible") || word.equals("shown") ? Boolean.FALSE : null;
   }

   /** The server's TPS from a sidebar line like "TPS: 15.68" (0 to 20), or null. */
   static Double parseTps(String normalized) {
      Matcher m = TPS_PATTERN.matcher(normalized == null ? "" : normalized);
      if (!m.find()) {
         return null;
      }

      try {
         double v = Double.parseDouble(m.group(1));
         return v >= 0.0 && v <= 25.0 ? Math.min(20.0, v) : null;
      } catch (NumberFormatException e) {
         return null;
      }
   }

   static String normalizeSidebarText(String input) {
      return TextUtil.normalizeSmallCaps(input);
   }

   private static AltResourceState.ScoreboardParse mergeResourceParse(AltResourceState.ScoreboardParse current, AltResourceState.ScoreboardParse next) {
      return new AltResourceState.ScoreboardParse(
         next.balanceRaw() != null ? next.balanceRaw() : current.balanceRaw(),
         next.balanceValue() != null ? next.balanceValue() : current.balanceValue(),
         next.claimBlocks() != null ? next.claimBlocks() : current.claimBlocks()
      );
   }
}
