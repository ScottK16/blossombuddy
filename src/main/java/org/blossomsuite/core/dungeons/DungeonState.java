package org.blossomsuite.core.dungeons;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.SuiteServer;
import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.DungeonConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.storage.DungeonStore;
import org.blossomsuite.core.util.TextUtil;

/**
 * The dungeon timer. It reads the server's own [Dungeons] chat lines to know when YOUR run starts and ends, and keeps two clocks that
 * belong to you alone: how long this run has taken, and the cooldown (8 hours by default) before you can run again.
 *
 * <p>Everything here stays on your computer. Other players' events (a teammate who runs out of lives, someone joining or leaving the
 * party) only change who is listed in the party; they never stop, restart or reset your own clocks. Only your own death or your own
 * completion ends your run and starts your cooldown.
 */
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

   private static Supplier<String> selfNameSource = DungeonState::gameSelfName;
   private static Runnable saver = ConfigIO::saveIfDirty;
   private static boolean inDungeon = false;
   private static String activeDungeonName = "Dungeon";
   private static long startedAtMs = 0L;
   private static long lastEnterSignalAtMs = 0L;
   private static final LinkedHashSet<String> partyNames = new LinkedHashSet<>();
   private static final LinkedHashSet<String> lostLivesNames = new LinkedHashSet<>();

   private DungeonState() {
   }

   /** Reads one line of chat (plain text). Safe to call for every message: lines that are not about dungeons or the party are ignored. */
   public static void observeChatLine(String raw) {
      DungeonConfig cfg = SuiteConfig.INSTANCE.DungeonConfig;
      if (!cfg.enabled || raw == null || raw.isBlank()) {
         return;
      }

      long now = System.currentTimeMillis();
      expireRunIfNeeded(now);
      if (observePartyLine(raw)) {
         return;
      }

      if (AVAILABLE.matcher(raw).find()) {
         return;
      }

      Matcher ready = READY.matcher(raw);
      if (ready.find()) {
         partyNames.add(cleanName(ready.group(1), "Unknown"));
         return;
      }

      if (ENTERING.matcher(raw).find()) {
         lastEnterSignalAtMs = now;
         ensureSelfInParty();
         if (!inDungeon) {
            startRun(now);
         }

         return;
      }

      if (LOADING.matcher(raw).find()) {
         lastEnterSignalAtMs = now;
         ensureSelfInParty();
         return;
      }

      if (LOADED.matcher(raw).find()) {
         long startAt = lastEnterSignalAtMs > 0L ? lastEnterSignalAtMs : now;
         lastEnterSignalAtMs = startAt;
         ensureSelfInParty();
         if (!inDungeon) {
            startRun(startAt);
         }

         return;
      }

      Matcher lost = LOST_LIVES.matcher(raw);
      if (lost.find()) {
         String lostName = cleanName(lost.group(1), "Unknown");
         lostLivesNames.add(lostName);
         partyNames.add(lostName);
         if (isSelf(lostName)) {
            finishSelfLostLives(now);
         }

         return; // somebody else running out of lives changes nothing about your own run
      }

      if (SELF_LOST_LIVES.matcher(raw).find()) {
         finishSelfLostLives(now);
      } else if (COMPLETED.matcher(raw).find()) {
         recoverRunIfNeeded(now);
         if (!selfLostLives()) {
            finishRun(true, now);
         }
      }
   }

   public static boolean cooldownActive(long now) {
      expireRunIfNeeded(now);
      return currentCooldownEndsAt() > now;
   }

   /** "Akuma: 03:21" while a run is on, else null. */
   public static String activeRunLine(long now) {
      expireRunIfNeeded(now);
      return inDungeon && startedAtMs > 0L ? activeDungeonName + ": " + TextUtil.fmtStopwatch(Math.max(0L, now - startedAtMs)) : null;
   }

   public static boolean inRun() {
      return inDungeon;
   }

   public static void reportStatus() {
      DungeonConfig cfg = SuiteConfig.INSTANCE.DungeonConfig;
      long now = System.currentTimeMillis();
      expireRunIfNeeded(now);
      if (!cfg.enabled) {
         DungeonRuntime.sendChat(Component.literal("Dungeon tracking is OFF.").withStyle(ChatFormatting.GRAY));
         return;
      }

      if (inDungeon) {
         DungeonRuntime.sendChat(
            Component.literal("Dungeon active: ")
               .withStyle(ChatFormatting.GRAY)
               .append(Component.literal(activeDungeonName).withStyle(ChatFormatting.AQUA))
               .append(Component.literal(" for " + TextUtil.fmtStopwatch(now - startedAtMs)).withStyle(ChatFormatting.GRAY))
         );
      }

      for (CooldownRow row : allStatusRows(cfg, now)) {
         long remaining = row.remainingMs();
         String displayServer = SuiteRuntime.profile().serverDisplayName(row.server());
         if (remaining <= 0L) {
            DungeonRuntime.sendChat(Component.literal("Dungeon cooldown (" + displayServer + "): READY").withStyle(ChatFormatting.GREEN));
         } else {
            DungeonRuntime.sendChat(
               Component.literal("Dungeon cooldown (" + displayServer + "): ")
                  .withStyle(ChatFormatting.GRAY)
                  .append(Component.literal(formatCooldownDuration(remaining)).withStyle(ChatFormatting.YELLOW))
                  .append(Component.literal(" remaining, ready ").withStyle(ChatFormatting.GRAY))
                  .append(Component.literal(READY_TIME_FORMAT.format(Instant.ofEpochMilli(row.endsAtMs()))).withStyle(ChatFormatting.GREEN))
            );
         }
      }
   }

   public static void reportAttempts() {
      Map<String, List<DungeonStore.DungeonAttempt>> byServer = DungeonStore.getRecent12ByServer();
      DungeonRuntime.sendChat(
         Component.literal("---- " + SuiteRuntime.profile().displayName().toUpperCase(Locale.ROOT) + " DUNGEONS BY SERVER ----").withStyle(ChatFormatting.GOLD)
      );
      if (byServer.isEmpty()) {
         DungeonRuntime.sendChat(Component.literal("No dungeon attempts tracked yet.").withStyle(ChatFormatting.GRAY));
         return;
      }

      for (Entry<String, List<DungeonStore.DungeonAttempt>> entry : byServer.entrySet()) {
         DungeonRuntime.sendChat(Component.literal(entry.getKey()).withStyle(ChatFormatting.AQUA));
         for (DungeonStore.DungeonAttempt attempt : entry.getValue()) {
            ChatFormatting resultColor = attempt.completed() ? ChatFormatting.GREEN : ChatFormatting.RED;
            DungeonRuntime.sendChat(
               Component.literal("[" + TextUtil.fmtStopwatch(attempt.durationMs()) + "] ")
                  .withStyle(ChatFormatting.DARK_GRAY)
                  .append(Component.literal(attempt.completed() ? "Completed" : "Failed").withStyle(resultColor))
                  .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                  .append(Component.literal(attempt.dungeonName()).withStyle(ChatFormatting.AQUA))
                  .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                  .append(Component.literal(attempt.names()).withStyle(ChatFormatting.GRAY))
            );
         }
      }
   }

   /** Forgets the run in progress and clears this realm's cooldown (for when the clock is wrong). */
   public static void resetCurrentServer() {
      resetRun();
      String server = currentServer();
      SuiteConfig.INSTANCE.DungeonConfig.resetCooldown(server);
      saver.run();
      DungeonRuntime.sendChat(Component.literal("Dungeon cooldown reset for " + server + ".").withStyle(ChatFormatting.YELLOW));
   }

   /** Leaving the game ends the run being timed (a cooldown that already started is kept). */
   public static void resetForDisconnect() {
      resetRun();
   }

   /** Starts a cooldown by hand, for when a run happened without the mod watching. */
   public static void startCooldownNow() {
      applyDungeonCooldown(System.currentTimeMillis());
   }

   public static List<CooldownRow> hudCooldownRows(long now) {
      DungeonConfig cfg = SuiteConfig.INSTANCE.DungeonConfig;
      List<CooldownRow> rows = new ArrayList<>();
      if (cfg.hudServerMode == DungeonConfig.HudServerMode.CURRENT_SERVER) {
         String server = currentServer();
         long endsAt = cfg.cooldownEndsAt(server);
         rows.add(new CooldownRow(server, endsAt, Math.max(0L, endsAt - now)));
         return rows;
      }

      for (String server : allTrackedServerKeys(cfg)) {
         long endsAt = cfg.cooldownEndsAt(server);
         rows.add(new CooldownRow(server, endsAt, Math.max(0L, endsAt - now)));
      }

      if (rows.isEmpty()) {
         rows.add(new CooldownRow(currentServer(), 0L, 0L));
      }

      return rows;
   }

   private static List<CooldownRow> allStatusRows(DungeonConfig cfg, long now) {
      List<CooldownRow> rows = new ArrayList<>();
      for (String server : allTrackedServerKeys(cfg)) {
         long endsAt = cfg.cooldownEndsAt(server);
         rows.add(new CooldownRow(server, endsAt, Math.max(0L, endsAt - now)));
      }

      if (rows.isEmpty()) {
         String server = currentServer();
         rows.add(new CooldownRow(server, cfg.cooldownEndsAt(server), 0L));
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
      lostLivesNames.clear();
   }

   private static void finishRun(boolean completed, long now) {
      if (!inDungeon || startedAtMs <= 0L) {
         return;
      }

      DungeonConfig cfg = SuiteConfig.INSTANCE.DungeonConfig;
      long duration = Math.max(0L, now - startedAtMs);
      LinkedHashSet<String> names = new LinkedHashSet<>(partyNames);
      names.addAll(lostLivesNames);
      String self = selfName();
      if (names.isEmpty() && !self.isBlank()) {
         names.add(self);
      }

      DungeonStore.DungeonAttempt attempt = new DungeonStore.DungeonAttempt(
         currentServer(), now, duration, completed, activeDungeonName, joinNames(names)
      );
      Component chatMessage = Component.literal("Dungeon " + (completed ? "completed" : "failed") + ": ")
         .withStyle(completed ? ChatFormatting.GREEN : ChatFormatting.RED)
         .append(Component.literal(attempt.names()).withStyle(ChatFormatting.GRAY))
         .append(Component.literal(" took " + TextUtil.fmtStopwatch(duration)).withStyle(ChatFormatting.YELLOW));
      DungeonStore.saveAttempt(attempt);
      applyDungeonCooldown(now);
      resetRun();
      if (cfg.reportRunsInChat) {
         DungeonRuntime.sendChat(chatMessage);
      }
   }

   private static void resetRun() {
      inDungeon = false;
      activeDungeonName = "Dungeon";
      startedAtMs = 0L;
      lastEnterSignalAtMs = 0L;
      partyNames.clear();
      lostLivesNames.clear();
   }

   private static boolean observePartyLine(String raw) {
      if (PARTY_DISBANDED.matcher(raw).find()) {
         partyNames.clear();
         return true;
      }

      Matcher joined = PARTY_JOINED.matcher(raw);
      if (joined.find()) {
         String name = cleanName(joined.group(1), "");
         if (!name.isBlank()) {
            partyNames.add(name);
         }

         return true;
      }

      Matcher left = PARTY_LEFT.matcher(raw);
      if (left.find()) {
         removePartyMember(left.group(1));
         return true;
      }

      Matcher removed = PARTY_REMOVED.matcher(raw);
      if (removed.find()) {
         removePartyMember(removed.group(1));
         return true;
      }

      return false;
   }

   private static void removePartyMember(String rawName) {
      String name = cleanName(rawName, "");
      if (!name.isBlank()) {
         partyNames.removeIf(existing -> existing.equalsIgnoreCase(name));
         lostLivesNames.removeIf(existing -> existing.equalsIgnoreCase(name));
      }
   }

   private static void expireRunIfNeeded(long now) {
      if (inDungeon && startedAtMs > 0L && now - startedAtMs >= MAX_TRACKED_RUN_MS) {
         resetRun();
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

   private static String gameSelfName() {
      Minecraft client = Minecraft.getInstance();
      if (client != null && client.player != null) {
         String name = client.player.getName().getString();
         return name == null ? "" : name.trim();
      }

      return "";
   }

   private static String selfName() {
      String name = selfNameSource.get();
      return name == null ? "" : name.trim();
   }

   private static void ensureSelfInParty() {
      String self = selfName();
      if (!self.isBlank()) {
         partyNames.add(self);
      }
   }

   /** If the "entering" line was missed but the dungeon clearly started recently, count the run from then. */
   private static void recoverRunIfNeeded(long now) {
      if (!inDungeon && startedAtMs <= 0L && lastEnterSignalAtMs > 0L && now - lastEnterSignalAtMs <= RUN_RECOVERY_WINDOW_MS) {
         ensureSelfInParty();
         startRun(lastEnterSignalAtMs);
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

   /** Your own death ends your run (as a failure) and starts your cooldown, whatever the rest of the party is still doing. */
   private static void finishSelfLostLives(long now) {
      String self = selfName();
      if (!self.isBlank()) {
         lostLivesNames.add(self);
         partyNames.add(self);
      }

      recoverRunIfNeeded(now);
      finishRun(false, now);
   }

   private static void applyDungeonCooldown(long now) {
      DungeonConfig cfg = SuiteConfig.INSTANCE.DungeonConfig;
      cfg.cooldownTotalMs = Math.max(1, cfg.cooldownHours) * 60L * 60L * 1000L;
      cfg.setCooldownEndsAt(currentServer(), now + cfg.cooldownTotalMs);
      saver.run();
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
      }

      return m > 0L ? m + "m" : s + "s";
   }

   public record CooldownRow(String server, long endsAtMs, long remainingMs) {
      public boolean active() {
         return this.remainingMs > 0L;
      }
   }

   /** Pretends to be this player and forgets everything, so tests can replay chat from a clean start. */
   static void resetForTests(String selfName) {
      selfNameSource = () -> selfName;
      saver = () -> {};
      resetRun();
   }
}
