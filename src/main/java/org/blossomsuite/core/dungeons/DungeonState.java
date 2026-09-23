package org.blossomsuite.core.dungeons;

import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.SuiteServer;
import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.DungeonConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.services.DungeonReportService;
import org.blossomsuite.core.services.models.DungeonModels;
import org.blossomsuite.core.storage.DungeonStore;
import org.blossomsuite.core.util.TextUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class DungeonState {
   private static final long MAX_TRACKED_RUN_MS = 2700000L;
   private static final long RUN_RECOVERY_WINDOW_MS = 7200000L;
   private static final Pattern AVAILABLE = Pattern.compile("\\[Dungeons\\]\\s+Dungeon\\s+'([^']+)'\\s+is now available[.!]?", 2);
   private static final Pattern READY = Pattern.compile("\\[Dungeons\\]\\s+(.+?)\\s+is ready to enter the dungeon[.!]?\\s*\\((\\d+)/(\\d+)\\)", 2);
   private static final Pattern ENTERING = Pattern.compile("\\[Dungeons\\]\\s+All players are ready[.!]?\\s+Entering the dungeon[.!]?", 2);
   private static final Pattern LOADING = Pattern.compile("\\[Dungeons\\]\\s+Loading dungeon[.!]*", 2);
   private static final Pattern LOADED = Pattern.compile("\\[Dungeons\\]\\s+Dungeon Loaded[.!]?", 2);
   private static final Pattern PARTY_JOINED = Pattern.compile("(?:\\[Party\\]\\s*)?(.+?)\\s+joined the party[.!]?", 2);
   private static final Pattern PARTY_LEFT = Pattern.compile("(?:\\[Party\\]\\s*)?(.+?)\\s+(?:left|quit) the party[.!]?", 2);
   private static final Pattern PARTY_REMOVED = Pattern.compile(
      "(?:\\[Party\\]\\s*)?(.+?)\\s+(?:was removed from|was kicked from|has been removed from|has been kicked from) the party[.!]?", 2
   );
   private static final Pattern PARTY_DISBANDED = Pattern.compile(
      "(?:\\[Party\\]\\s*)?(?:The party has been disbanded|Party has been disbanded|You left the party|You have left the party)[.!]?", 2
   );
   private static final Pattern LOST_LIVES = Pattern.compile("\\[Dungeons\\]\\s+(.+?)\\s+lost all their lives[.!]?", 2);
   private static final Pattern SELF_LOST_LIVES = Pattern.compile("\\[Dungeons\\]\\s+You\\s+lost\\s+all\\s+your\\s+lives[.!]?", 2);
   private static final Pattern COMPLETED = Pattern.compile("(AKUMA HAS FALLEN[.!]?|\\bDungeon completed\\.\\s+.+\\btook\\b)", 2);
   private static final DateTimeFormatter READY_TIME_FORMAT = DateTimeFormatter.ofPattern("MMM d h:mm a", Locale.US).withZone(ZoneId.systemDefault());
   private static String pendingDungeonName = "Dungeon";
   private static boolean inDungeon = false;
   private static String activeDungeonName = "Dungeon";
   private static long startedAtMs = 0L;
   private static String activeClientRunId = "";
   private static String activePartyRunKey = "";
   private static String activeRunMergeKey = "";
   private static volatile String activeRemoteRunUid = "";
   private static boolean activeCooldownReported = false;
   private static boolean managedPartyActive = false;
   private static boolean managedPartyReporter = false;
   private static long lastEnterSignalAtMs = 0L;
   private static int expectedPartySize = 0;
   private static final LinkedHashSet<String> partyNames = new LinkedHashSet<>();
   private static final LinkedHashSet<String> lostLivesNames = new LinkedHashSet<>();

   private DungeonState() {
   }

   public static void observeChatLine(String raw) {
      DungeonConfig cfg = SuiteConfig.INSTANCE.DungeonConfig;
      if (cfg.enabled) {
         if (raw != null && !raw.isBlank()) {
            long now = System.currentTimeMillis();
            expireRunIfNeeded(now);
            if (!observePartyLine(raw)) {
               Matcher available = AVAILABLE.matcher(raw);
               if (available.find()) {
                  pendingDungeonName = cleanName(available.group(1), "Dungeon");
               } else {
                  Matcher ready = READY.matcher(raw);
                  if (ready.find()) {
                     partyNames.add(cleanName(ready.group(1), "Unknown"));

                     try {
                        expectedPartySize = Math.max(expectedPartySize, Integer.parseInt(ready.group(3)));
                     } catch (NumberFormatException var9) {
                     }
                  } else if (ENTERING.matcher(raw).find()) {
                     lastEnterSignalAtMs = now;
                     ensureSelfInParty();
                     if (!inDungeon) {
                        startRun(now);
                     }
                  } else if (LOADING.matcher(raw).find()) {
                     lastEnterSignalAtMs = now;
                     ensureSelfInParty();
                  } else if (LOADED.matcher(raw).find()) {
                     long startAt = lastEnterSignalAtMs > 0L ? lastEnterSignalAtMs : now;
                     lastEnterSignalAtMs = startAt;
                     ensureSelfInParty();
                     if (!inDungeon) {
                        startRun(startAt);
                     }
                  } else {
                     Matcher lost = LOST_LIVES.matcher(raw);
                     if (lost.find()) {
                        String lostName = cleanName(lost.group(1), "Unknown");
                        boolean newlyObserved = lostLivesNames.add(lostName);
                        partyNames.add(lostName);
                        if (newlyObserved) {
                           reportRunLostLives(now, Collections.singletonList(lostName));
                        }

                        if (isSelf(lostName)) {
                           finishSelfLostLives(now);
                        }
                     } else if (SELF_LOST_LIVES.matcher(raw).find()) {
                        finishSelfLostLives(now);
                     } else if (COMPLETED.matcher(raw).find()) {
                        recoverRunIfNeeded(now);
                        if (!selfLostLives()) {
                           finishRun(true, now);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public static boolean cooldownActive(long now) {
      expireRunIfNeeded(now);
      return currentCooldownEndsAt() > now;
   }

   public static String activeRunLine(long now) {
      expireRunIfNeeded(now);
      return inDungeon && startedAtMs > 0L ? activeDungeonName + ": " + TextUtil.fmtStopwatch(Math.max(0L, now - startedAtMs)) : null;
   }

   public static void reportStatus() {
      DungeonConfig cfg = SuiteConfig.INSTANCE.DungeonConfig;
      long now = System.currentTimeMillis();
      expireRunIfNeeded(now);
      if (!cfg.enabled) {
         DungeonRuntime.sendChat(Text.literal("Dungeon tracking is OFF.").formatted(Formatting.GRAY));
      } else {
         if (inDungeon) {
            DungeonRuntime.sendChat(
               Text.literal("Dungeon active: ")
                  .formatted(Formatting.GRAY)
                  .append(Text.literal(activeDungeonName).formatted(Formatting.AQUA))
                  .append(Text.literal(" for " + TextUtil.fmtStopwatch(now - startedAtMs)).formatted(Formatting.GRAY))
            );
         }

         for (DungeonState.CooldownRow row : allStatusRows(cfg, now)) {
            long remaining = row.remainingMs();
            String displayServer = SuiteRuntime.profile().serverDisplayName(row.server());
            if (remaining <= 0L) {
               DungeonRuntime.sendChat(Text.literal("Dungeon cooldown (" + displayServer + "): READY").formatted(Formatting.GREEN));
            } else {
               DungeonRuntime.sendChat(
                  Text.literal("Dungeon cooldown (" + displayServer + "): ")
                     .formatted(Formatting.GRAY)
                     .append(Text.literal(formatLongDuration(remaining)).formatted(Formatting.YELLOW))
                     .append(Text.literal(" remaining, ready ").formatted(Formatting.GRAY))
                     .append(Text.literal(READY_TIME_FORMAT.format(Instant.ofEpochMilli(row.endsAtMs()))).formatted(Formatting.GREEN))
               );
            }
         }
      }
   }

   public static void reportAttempts() {
      Map<String, List<DungeonStore.DungeonAttempt>> byServer = DungeonStore.getRecent12ByServer();
      DungeonRuntime.sendChat(
         Text.literal("---- " + SuiteRuntime.profile().displayName().toUpperCase(Locale.ROOT) + " DUNGEONS BY SERVER ----").formatted(Formatting.GOLD)
      );
      if (byServer.isEmpty()) {
         DungeonRuntime.sendChat(Text.literal("No dungeon attempts tracked yet.").formatted(Formatting.GRAY));
      } else {
         for (Entry<String, List<DungeonStore.DungeonAttempt>> entry : byServer.entrySet()) {
            DungeonRuntime.sendChat(Text.literal(entry.getKey()).formatted(Formatting.AQUA));

            for (DungeonStore.DungeonAttempt attempt : entry.getValue()) {
               Formatting resultColor = attempt.completed() ? Formatting.GREEN : Formatting.RED;
               DungeonRuntime.sendChat(
                  Text.literal("[" + TextUtil.fmtStopwatch(attempt.durationMs()) + "] ")
                     .formatted(Formatting.DARK_GRAY)
                     .append(Text.literal(attempt.completed() ? "Completed" : "Failed").formatted(resultColor))
                     .append(Text.literal(" | ").formatted(Formatting.DARK_GRAY))
                     .append(Text.literal(attempt.dungeonName()).formatted(Formatting.AQUA))
                     .append(Text.literal(" | ").formatted(Formatting.DARK_GRAY))
                     .append(Text.literal(attempt.names()).formatted(Formatting.GRAY))
               );
            }
         }
      }
   }

   public static void resetCurrentServer() {
      resetRun();
      clearManagedParty();
      String server = currentServer();
      SuiteConfig.INSTANCE.DungeonConfig.resetCooldown(server);
      ConfigIO.saveIfDirty();
      DungeonRuntime.sendChat(Text.literal("Dungeon cooldown reset for " + server + ".").formatted(Formatting.YELLOW));
   }

   public static void resetForDisconnect() {
      resetRun();
      clearManagedParty();
   }

   public static void markManagedPartyCreated(String partyName) {
      managedPartyActive = true;
      managedPartyReporter = true;
      partyNames.clear();
      lostLivesNames.clear();
      ensureSelfInParty();
      expectedPartySize = Math.max(expectedPartySize, partyNames.size());
      String name = partyName == null ? "" : partyName.trim();
      DungeonRuntime.sendChat(
         Text.literal("Dungeon party tracking started")
            .formatted(Formatting.GREEN)
            .append(Text.literal(name.isBlank() ? "." : ": " + name).formatted(Formatting.GRAY))
      );
   }

   public static List<DungeonState.CooldownRow> hudCooldownRows(long now) {
      DungeonConfig cfg = SuiteConfig.INSTANCE.DungeonConfig;
      List<DungeonState.CooldownRow> rows = new ArrayList<>();
      if (cfg.hudServerMode == DungeonConfig.HudServerMode.CURRENT_SERVER) {
         String server = currentServer();
         long endsAt = cfg.cooldownEndsAt(server);
         rows.add(new DungeonState.CooldownRow(server, endsAt, Math.max(0L, endsAt - now)));
         return rows;
      }

      for (String server : allTrackedServerKeys(cfg)) {
         long endsAt = cfg.cooldownEndsAt(server);
         rows.add(new DungeonState.CooldownRow(server, endsAt, Math.max(0L, endsAt - now)));
      }

      if (rows.isEmpty()) {
         String server = currentServer();
         rows.add(new DungeonState.CooldownRow(server, 0L, 0L));
      }

      return rows;
   }

   private static List<DungeonState.CooldownRow> allStatusRows(DungeonConfig cfg, long now) {
      List<DungeonState.CooldownRow> rows = new ArrayList<>();

      for (String server : allTrackedServerKeys(cfg)) {
         long endsAt = cfg.cooldownEndsAt(server);
         rows.add(new DungeonState.CooldownRow(server, endsAt, Math.max(0L, endsAt - now)));
      }

      if (rows.isEmpty()) {
         String server = currentServer();
         rows.add(new DungeonState.CooldownRow(server, cfg.cooldownEndsAt(server), 0L));
      }

      return rows;
   }

   private static List<String> allTrackedServerKeys(DungeonConfig cfg) {
      LinkedHashSet<String> servers = new LinkedHashSet<>();

      for (SuiteServer server : SuiteRuntime.profile().servers()) {
         if (server != null && server.key() != null && !server.key().isBlank()) {
            servers.add(DungeonConfig.serverKey(server.key()));
         }
      }

      String current = currentServer();
      if (current != null && !current.isBlank()) {
         servers.add(DungeonConfig.serverKey(current));
      }

      for (String server : cfg.cooldownEndsByServer.keySet()) {
         if (server != null && !server.isBlank()) {
            servers.add(DungeonConfig.serverKey(server));
         }
      }

      return new ArrayList<>(servers);
   }

   private static void startRun(long now) {
      inDungeon = true;
      activeDungeonName = canonicalDungeonName();
      startedAtMs = now;
      ensureSelfInParty();
      activeClientRunId = UUID.randomUUID().toString();
      activePartyRunKey = partyRunKey(currentServer(), activeDungeonName, partyNames, startedAtMs);
      activeRunMergeKey = runMergeKey(currentServer(), activeDungeonName, startedAtMs);
      activeRemoteRunUid = "";
      activeCooldownReported = false;
      lostLivesNames.clear();
      if (expectedPartySize <= 0) {
         expectedPartySize = Math.max(1, partyNames.size());
      }

      reportRunStart(activeClientRunId, activeDungeonName, startedAtMs, expectedPartySize, new LinkedHashSet<>(partyNames));
   }

   private static void finishRun(boolean completed, long now) {
      if (inDungeon && startedAtMs > 0L) {
         DungeonConfig cfg = SuiteConfig.INSTANCE.DungeonConfig;
         long duration = startedAtMs > 0L ? Math.max(0L, now - startedAtMs) : 0L;
         LinkedHashSet<String> names = new LinkedHashSet<>(partyNames);
         names.addAll(lostLivesNames);
         String self = selfName();
         if (names.isEmpty() && !self.isBlank()) {
            names.add(self);
         }

         String server = currentServer();
         DungeonStore.DungeonAttempt attempt = new DungeonStore.DungeonAttempt(server, now, duration, completed, activeDungeonName, joinNames(names));
         Text chatMessage = Text.literal("Dungeon " + (completed ? "completed" : "failed") + ": ")
            .formatted(completed ? Formatting.GREEN : Formatting.RED)
            .append(Text.literal(attempt.names()).formatted(Formatting.GRAY))
            .append(Text.literal(" took " + TextUtil.fmtStopwatch(duration)).formatted(Formatting.YELLOW));
         DungeonStore.saveAttempt(attempt);
         reportRunFinish(
            activeRemoteRunUid,
            activeClientRunId,
            activePartyRunKey,
            activeDungeonName,
            startedAtMs,
            now,
            duration,
            completed,
            expectedPartySize,
            new LinkedHashSet<>(partyNames),
            new LinkedHashSet<>(lostLivesNames)
         );
         applyDungeonCooldown(now, completed ? "party_completed" : "party_failed");
         resetRun();
         clearManagedParty();
         if (cfg.reportRunsInChat) {
            DungeonRuntime.sendChat(chatMessage);
         }
      }
   }

   private static void resetRun() {
      inDungeon = false;
      activeDungeonName = "Dungeon";
      startedAtMs = 0L;
      activeClientRunId = "";
      activePartyRunKey = "";
      activeRunMergeKey = "";
      activeRemoteRunUid = "";
      activeCooldownReported = false;
      expectedPartySize = 0;
      lastEnterSignalAtMs = 0L;
      partyNames.clear();
      lostLivesNames.clear();
   }

   private static void clearManagedParty() {
      managedPartyActive = false;
      managedPartyReporter = false;
   }

   private static boolean observePartyLine(String raw) {
      if (raw == null || raw.isBlank()) {
         return false;
      }

      if (PARTY_DISBANDED.matcher(raw).find()) {
         clearManagedParty();
         partyNames.clear();
         expectedPartySize = 0;
         return true;
      }

      Matcher joined = PARTY_JOINED.matcher(raw);
      if (joined.find()) {
         String name = cleanName(joined.group(1), "");
         if (!name.isBlank()) {
            partyNames.add(name);
            expectedPartySize = Math.max(expectedPartySize, partyNames.size());
         }

         return true;
      } else {
         Matcher left = PARTY_LEFT.matcher(raw);
         if (left.find()) {
            removePartyMember(left.group(1));
            return true;
         } else {
            Matcher removed = PARTY_REMOVED.matcher(raw);
            if (removed.find()) {
               removePartyMember(removed.group(1));
               return true;
            } else {
               return false;
            }
         }
      }
   }

   private static void removePartyMember(String rawName) {
      String name = cleanName(rawName, "");
      if (!name.isBlank()) {
         partyNames.removeIf(existing -> existing.equalsIgnoreCase(name));
         lostLivesNames.removeIf(existing -> existing.equalsIgnoreCase(name));
         if (isSelf(name)) {
            clearManagedParty();
         }
      }
   }

   private static void expireRunIfNeeded(long now) {
      if (inDungeon && startedAtMs > 0L) {
         if (now - startedAtMs >= 2700000L) {
            resetRun();
         }
      }
   }

   private static String cleanName(String raw, String fallback) {
      String s = raw == null ? "" : raw.trim();

      while (!s.isEmpty() && (s.charAt(0) == '~' || s.charAt(0) == '"' || Character.isWhitespace(s.charAt(0)))) {
         s = s.substring(1).trim();
      }

      while (!s.isEmpty() && (s.charAt(s.length() - 1) == '"' || Character.isWhitespace(s.charAt(s.length() - 1)))) {
         s = s.substring(0, s.length() - 1).trim();
      }

      return s.isBlank() ? fallback : s;
   }

   private static String canonicalDungeonName() {
      return SuiteRuntime.profile().primaryDungeonName();
   }

   private static String selfName() {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.player != null) {
         String name = client.player.getName().getString();
         return name == null ? "" : name.trim();
      } else {
         return "";
      }
   }

   private static void ensureSelfInParty() {
      String self = selfName();
      if (!self.isBlank()) {
         partyNames.add(self);
      }
   }

   private static boolean partyContainsSelf() {
      String self = selfName();
      if (self.isBlank()) {
         return false;
      }

      for (String name : partyNames) {
         if (self.equalsIgnoreCase(name)) {
            return true;
         }
      }

      return false;
   }

   private static void recoverRunIfNeeded(long now) {
      if (!inDungeon && startedAtMs <= 0L) {
         if (lastEnterSignalAtMs > 0L) {
            if (now - lastEnterSignalAtMs <= 7200000L) {
               ensureSelfInParty();
               startRun(lastEnterSignalAtMs);
            }
         }
      }
   }

   private static boolean isSelf(String name) {
      String self = selfName();
      return !self.isBlank() && name != null && self.equalsIgnoreCase(name.trim());
   }

   private static boolean selfLostLives() {
      String self = selfName();
      if (self.isBlank()) {
         return false;
      }

      for (String name : lostLivesNames) {
         if (self.equalsIgnoreCase(name)) {
            return true;
         }
      }

      return false;
   }

   private static void finishSelfLostLives(long now) {
      String self = selfName();
      if (!self.isBlank()) {
         lostLivesNames.add(self);
         partyNames.add(self);
      }

      recoverRunIfNeeded(now);
      finishRun(false, now);
   }

   private static void applyDungeonCooldown(long now, String reason) {
      if (inDungeon && startedAtMs > 0L && !activeCooldownReported) {
         DungeonConfig cfg = SuiteConfig.INSTANCE.DungeonConfig;
         String server = currentServer();
         cfg.cooldownTotalMs = Math.max(1, cfg.cooldownHours) * 60L * 60L * 1000L;
         long endsAt = now + cfg.cooldownTotalMs;
         cfg.setCooldownEndsAt(server, endsAt);
         ConfigIO.saveIfDirty();
         activeCooldownReported = true;
         reportRunCooldown(activeRemoteRunUid, activeClientRunId, activePartyRunKey, activeDungeonName, startedAtMs, now, endsAt, cfg.cooldownHours, reason);
      }
   }

   private static void reportRunStart(String clientRunId, String dungeonName, long startMs, int expectedSize, LinkedHashSet<String> names) {
      if (shouldReportDungeonRun()) {
         DungeonReportService service = DungeonRuntime.reportService();
         DungeonState.PlayerIdentity identity = playerIdentity();
         if (service != null && identity != null) {
            DungeonModels.DungeonRunStartRequest req = new DungeonModels.DungeonRunStartRequest();
            req.clientRunId = clientRunId;
            req.partyRunKey = partyRunKey(currentServer(), dungeonName, names, startMs);
            req.playerUuid = identity.uuid;
            req.playerName = identity.name;
            req.reporterUuid = identity.uuid;
            req.reporterName = identity.name;
            req.serverKey = currentServer();
            req.dungeonName = dungeonName;
            req.startedAtMs = startMs;
            req.runMergeKey = runMergeKey(currentServer(), dungeonName, startMs);
            req.partyNames = new ArrayList<>(names);
            req.expectedPartySize = expectedSize;
            service.reportStartIfAllowed(req).thenAccept(uid -> {
               if (uid != null && !uid.isBlank()) {
                  if (clientRunId.equals(activeClientRunId)) {
                     activeRemoteRunUid = uid;
                  }
               }
            }).exceptionally(ex -> null);
         }
      }
   }

   private static void reportRunFinish(
      String remoteUid,
      String clientRunId,
      String partyRunKey,
      String dungeonName,
      long startMs,
      long endMs,
      long durationMs,
      boolean completed,
      int expectedSize,
      LinkedHashSet<String> names,
      LinkedHashSet<String> lostNames
   ) {
      if (shouldReportDungeonRun()) {
         DungeonReportService service = DungeonRuntime.reportService();
         DungeonState.PlayerIdentity identity = playerIdentity();
         if (service != null && identity != null) {
            DungeonModels.DungeonRunFinishRequest req = new DungeonModels.DungeonRunFinishRequest();
            req.uid = remoteUid;
            req.clientRunId = clientRunId;
            req.partyRunKey = partyRunKey;
            req.playerUuid = identity.uuid;
            req.playerName = identity.name;
            req.reporterUuid = identity.uuid;
            req.reporterName = identity.name;
            req.serverKey = currentServer();
            req.dungeonName = dungeonName;
            req.startedAtMs = startMs;
            req.endedAtMs = endMs;
            req.durationMs = durationMs;
            req.status = completed ? "completed" : "failed";
            req.completed = completed;
            req.runMergeKey = runMergeKey(currentServer(), dungeonName, startMs);
            req.partyNames = new ArrayList<>(names);
            req.lostLivesNames = new ArrayList<>(lostNames);
            req.expectedPartySize = expectedSize;
            service.reportFinishIfAllowed(req);
         }
      }
   }

   private static void reportRunLostLives(long observedAtMs, List<String> observedLostNames) {
      if (shouldReportDungeonRun()) {
         DungeonReportService service = DungeonRuntime.reportService();
         DungeonState.PlayerIdentity identity = playerIdentity();
         if (service != null && identity != null) {
            if (observedLostNames != null && !observedLostNames.isEmpty()) {
               long startMs = startedAtMs > 0L ? startedAtMs : lastEnterSignalAtMs;
               String dungeonName = activeDungeonName != null && !activeDungeonName.isBlank() ? activeDungeonName : canonicalDungeonName();
               DungeonModels.DungeonLostLivesRequest req = new DungeonModels.DungeonLostLivesRequest();
               req.uid = activeRemoteRunUid;
               req.clientRunId = activeClientRunId;
               req.partyRunKey = activePartyRunKey;
               req.runMergeKey = activeRunMergeKey != null && !activeRunMergeKey.isBlank()
                  ? activeRunMergeKey
                  : runMergeKey(currentServer(), dungeonName, startMs > 0L ? startMs : observedAtMs);
               req.playerUuid = identity.uuid;
               req.playerName = identity.name;
               req.reporterUuid = identity.uuid;
               req.reporterName = identity.name;
               req.serverKey = currentServer();
               req.dungeonName = dungeonName;
               req.startedAtMs = startMs > 0L ? startMs : null;
               req.observedAtMs = observedAtMs;
               req.partyNames = new ArrayList<>(partyNames);
               req.lostLivesNames = new ArrayList<>(observedLostNames);
               req.expectedPartySize = expectedPartySize;
               service.reportLostLivesIfAllowed(req);
            }
         }
      }
   }

   private static void reportRunCooldown(
      String remoteUid,
      String clientRunId,
      String partyRunKey,
      String dungeonName,
      long startMs,
      long triggeredAtMs,
      long endsAtMs,
      int cooldownHours,
      String reason
   ) {
      if (shouldReportDungeonRun()) {
         DungeonReportService service = DungeonRuntime.reportService();
         DungeonState.PlayerIdentity identity = playerIdentity();
         if (service != null && identity != null) {
            DungeonModels.DungeonCooldownRequest req = new DungeonModels.DungeonCooldownRequest();
            req.uid = remoteUid;
            req.clientRunId = clientRunId;
            req.partyRunKey = partyRunKey;
            req.playerUuid = identity.uuid;
            req.playerName = identity.name;
            req.reporterUuid = identity.uuid;
            req.reporterName = identity.name;
            req.serverKey = currentServer();
            req.dungeonName = dungeonName;
            req.startedAtMs = startMs;
            req.runMergeKey = runMergeKey(currentServer(), dungeonName, startMs);
            req.cooldownTriggeredAtMs = triggeredAtMs;
            req.cooldownEndsAtMs = endsAtMs;
            req.cooldownHours = cooldownHours;
            req.partyNames = new ArrayList<>(partyNames);
            req.lostLivesNames = new ArrayList<>(lostLivesNames);
            req.expectedPartySize = expectedPartySize;
            req.reason = reason;
            service.reportCooldownIfAllowed(req);
         }
      }
   }

   private static boolean shouldReportDungeonRun() {
      return managedPartyActive && managedPartyReporter;
   }

   private static DungeonState.PlayerIdentity playerIdentity() {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.player != null) {
         String uuid = client.player.getUuidAsString();
         String name = client.player.getName().getString();
         return uuid != null && !uuid.isBlank() && name != null && !name.isBlank() ? new DungeonState.PlayerIdentity(uuid, name.trim()) : null;
      } else {
         return null;
      }
   }

   private static String partyRunKey(String server, String dungeonName, LinkedHashSet<String> names, long startMs) {
      long bucket = Math.max(0L, startMs) / 30000L;
      String canonical = normalizeKeyPart(server) + "|" + normalizeKeyPart(dungeonName) + "|" + normalizedPartyNames(names) + "|" + bucket;
      return sha256Hex(canonical);
   }

   private static String runMergeKey(String server, String dungeonName, long startMs) {
      long bucket = Math.max(0L, startMs) / 30000L;
      String canonical = normalizeKeyPart(server) + "|" + normalizeKeyPart(dungeonName) + "|" + bucket;
      return sha256Hex(canonical);
   }

   private static String normalizedPartyNames(LinkedHashSet<String> names) {
      ArrayList<String> normalized = new ArrayList<>();
      if (names != null) {
         for (String name : names) {
            String clean = normalizeKeyPart(name);
            if (!clean.isBlank() && !normalized.contains(clean)) {
               normalized.add(clean);
            }
         }
      }

      Collections.sort(normalized);
      return String.join(",", normalized);
   }

   private static String normalizeKeyPart(String raw) {
      String s = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
      return s.replaceAll("\\s+", " ");
   }

   private static String sha256Hex(String value) {
      try {
         MessageDigest digest = MessageDigest.getInstance("SHA-256");
         byte[] hash = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
         StringBuilder out = new StringBuilder(hash.length * 2);

         for (byte b : hash) {
            out.append(Character.forDigit(b >> 4 & 15, 16));
            out.append(Character.forDigit(b & 15, 16));
         }

         return out.toString();
      } catch (Exception ignored) {
         return UUID.nameUUIDFromBytes((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)).toString();
      }
   }

   private static String joinNames(LinkedHashSet<String> names) {
      return names != null && !names.isEmpty() ? String.join(", ", names) : "Unknown";
   }

   private static long currentCooldownEndsAt() {
      return SuiteConfig.INSTANCE.DungeonConfig.cooldownEndsAt(currentServer());
   }

   public static String currentServer() {
      return DungeonRuntime.currentServer();
   }

   public static String formatCooldownDuration(long ms) {
      long totalSec = Math.max(0L, (long)Math.ceil(ms / 1000.0));
      long h = totalSec / 3600L;
      long m = totalSec % 3600L / 60L;
      long s = totalSec % 60L;
      if (h > 0L) {
         return h + "h " + m + "m";
      } else {
         return m > 0L ? m + "m" : s + "s";
      }
   }

   private static String formatLongDuration(long ms) {
      long totalSec = Math.max(0L, (long)Math.ceil(ms / 1000.0));
      long h = totalSec / 3600L;
      long m = totalSec % 3600L / 60L;
      long s = totalSec % 60L;
      if (h > 0L) {
         return h + "h " + m + "m";
      } else {
         return m > 0L ? m + "m" : s + "s";
      }
   }

   public record CooldownRow(String server, long endsAtMs, long remainingMs) {
      public boolean active() {
         return this.remainingMs > 0L;
      }
   }

   private record PlayerIdentity(String uuid, String name) {
   }
}
