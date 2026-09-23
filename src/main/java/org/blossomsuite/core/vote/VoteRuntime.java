package org.blossomsuite.core.vote;

import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.util.SuiteLog;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public final class VoteRuntime {
   private static final int SCOREBOARD_CONFIRMATION_SCANS = 2;
   private static Supplier<String> serverSupplier = () -> "";
   private static Supplier<Boolean> activeWorldSupplier = () -> true;
   private static Runnable confirmedServerHook = () -> {};
   private static Runnable temporaryStateResetHook = () -> {};
   private static final AtomicLong sessionGeneration = new AtomicLong(0L);
   private static String confirmedServer = "";
   private static String confirmedDisplayName = "";
   private static String candidateServer = "";
   private static String candidateDisplayName = "";
   private static int candidateScans = 0;
   private static boolean transitioning = true;

   private VoteRuntime() {
   }

   public static void setServerSupplier(Supplier<String> supplier) {
      serverSupplier = supplier != null ? supplier : () -> "";
   }

   public static void setActiveWorldSupplier(Supplier<Boolean> supplier) {
      activeWorldSupplier = supplier != null ? supplier : () -> true;
   }

   public static void setConfirmedServerHook(Runnable hook) {
      confirmedServerHook = hook != null ? hook : () -> {};
   }

   public static void setTemporaryStateResetHook(Runnable hook) {
      temporaryStateResetHook = hook != null ? hook : () -> {};
   }

   public static String currentServer() {
      return confirmedServer();
   }

   public static String confirmedServer() {
      return confirmedServer == null ? "" : confirmedServer;
   }

   public static String confirmedDisplayName() {
      return confirmedDisplayName != null && !confirmedDisplayName.isBlank() ? confirmedDisplayName : confirmedServer();
   }

   public static boolean hasConfirmedServer() {
      return !confirmedServer().isBlank() && !transitioning;
   }

   public static boolean isTransitioning() {
      return transitioning || confirmedServer().isBlank();
   }

   public static long sessionGeneration() {
      return sessionGeneration.get();
   }

   public static void onConnectionSessionChanged(String reason) {
      long generation = sessionGeneration.incrementAndGet();
      confirmedServer = "";
      confirmedDisplayName = "";
      candidateServer = "";
      candidateDisplayName = "";
      candidateScans = 0;
      transitioning = true;
      temporaryStateResetHook.run();
      SuiteLog.logger().debug("[vote-party] session generation changed to {} ({})", generation, reason);
   }

   public static boolean observeStrongServer(String rawServer, long now) {
      return observeServer(rawServer, now, true);
   }

   public static boolean observeScoreboardServer(String rawServer, long now) {
      return observeServer(rawServer, now, false);
   }

   private static boolean observeServer(String rawServer, long now, boolean strong) {
      String resolved = resolve(rawServer);
      if (resolved.isBlank()) {
         return false;
      }

      if (hasConfirmedServer() && resolved.equalsIgnoreCase(confirmedServer())) {
         candidateServer = "";
         candidateDisplayName = "";
         candidateScans = 0;
         return true;
      }

      if (hasConfirmedServer() && !resolved.equalsIgnoreCase(confirmedServer())) {
         beginTransition(resolved, rawServer, "candidate differs from confirmed server");
      } else if (confirmedServer().isBlank()) {
         transitioning = true;
      }

      if (strong) {
         confirmServer(resolved, rawServer, now, "strong");
         return true;
      } else if (!resolved.equalsIgnoreCase(candidateServer)) {
         candidateServer = resolved;
         candidateDisplayName = displayName(rawServer, resolved);
         candidateScans = 1;
         SuiteLog.logger().debug("[vote-party] candidate server detected: {} generation={}", candidateServer, sessionGeneration());
         return false;
      } else {
         candidateScans++;
         if (candidateScans >= 2) {
            confirmServer(candidateServer, candidateDisplayName, now, "scoreboard");
            return true;
         } else {
            return false;
         }
      }
   }

   private static void beginTransition(String nextServer, String displayName, String reason) {
      if (!transitioning) {
         long generation = sessionGeneration.incrementAndGet();
         SuiteLog.logger()
            .debug("[vote-party] transition started: {} -> {} generation={} ({})", new Object[]{confirmedServer(), nextServer, generation, reason});
      }

      transitioning = true;
      confirmedServer = "";
      confirmedDisplayName = "";
      candidateServer = nextServer;
      candidateDisplayName = displayName(displayName, nextServer);
      candidateScans = 0;
      temporaryStateResetHook.run();
   }

   private static void confirmServer(String server, String displayName, long now, String source) {
      String resolved = resolve(server);
      if (!resolved.isBlank()) {
         boolean changed = !resolved.equalsIgnoreCase(confirmedServer());
         if (changed || transitioning) {
            long generation = sessionGeneration.incrementAndGet();
            confirmedServer = resolved;
            confirmedDisplayName = displayName(displayName, resolved);
            transitioning = false;
            candidateServer = "";
            candidateDisplayName = "";
            candidateScans = 0;
            SuiteLog.logger().debug("[vote-party] server confirmed: {} generation={} source={} at={}", new Object[]{confirmedServer, generation, source, now});
            confirmedServerHook.run();
            SuiteLog.logger().debug("[vote-party] transition completed: {}", confirmedServer);
         }
      }
   }

   public static boolean isActiveWorld() {
      Boolean active = activeWorldSupplier.get();
      return active == null || active;
   }

   private static String resolve(String server) {
      if (server != null && !server.isBlank()) {
         try {
            return SuiteRuntime.profile().resolveServerKey(server);
         } catch (IllegalStateException ignored) {
            return server.trim();
         }
      } else {
         return "";
      }
   }

   private static String displayName(String raw, String fallback) {
      String source = raw != null && !raw.isBlank() ? raw : fallback;
      if (source != null && !source.isBlank()) {
         try {
            return SuiteRuntime.profile().serverDisplayName(source);
         } catch (IllegalStateException ignored) {
            return source.trim();
         }
      } else {
         return "";
      }
   }
}
