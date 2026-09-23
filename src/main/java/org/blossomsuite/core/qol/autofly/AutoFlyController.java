package org.blossomsuite.core.qol.autofly;

import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.util.TextUtil;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;

public final class AutoFlyController {
   private static final long COMMAND_COOLDOWN_MS = 1000L;
   private static final long WORLD_STABLE_MS = 750L;
   private static final long ARMED_WORLD_STABLE_MS = 250L;
   private static final long TELEPORT_WATCH_MS = 12000L;
   private static String confirmedWorldKey = "";
   private static boolean confirmedWorldExcluded = false;
   private static String pendingWorldKey = "";
   private static long pendingWorldSeenAtMs = 0L;
   private static String pendingLoginServerKey = "";
   private static long lastCommandAtMs = 0L;
   private static long teleportWatchUntilMs = 0L;

   private AutoFlyController() {
   }

   public static void reset() {
      confirmedWorldKey = "";
      confirmedWorldExcluded = false;
      pendingWorldKey = "";
      pendingWorldSeenAtMs = 0L;
      pendingLoginServerKey = "";
      lastCommandAtMs = 0L;
      teleportWatchUntilMs = 0L;
   }

   public static void observeScoreboardWorld(String serverName, String worldName, long nowMs) {
      if (SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
         if (SuiteConfig.INSTANCE.QolConfig != null && SuiteConfig.INSTANCE.QolConfig.autoFlyOnRwWorldLoad) {
            if (worldName != null && !worldName.isBlank()) {
               MinecraftClient client = MinecraftClient.getInstance();
               if (client != null && client.player != null && client.getNetworkHandler() != null) {
                  String key = normalizeKey(serverName) + "|" + normalizeKey(worldName);
                  if (!key.isBlank()) {
                     boolean excludedWorld = SuiteRuntime.profile().isAutoFlyExcludedWorld(worldName);
                     if (excludedWorld) {
                        confirmedWorldKey = key;
                        confirmedWorldExcluded = true;
                        clearLoginTriggerIfMatching(key);
                        clearPendingWorld();
                        teleportWatchUntilMs = 0L;
                     } else if (matchesLoginServer(key)) {
                        confirmedWorldKey = key;
                        confirmedWorldExcluded = false;
                        pendingLoginServerKey = "";
                        clearPendingWorld();
                        teleportWatchUntilMs = 0L;
                        sendFlyEnable(client, nowMs);
                     } else if (confirmedWorldKey.isBlank()) {
                        if (!key.equals(pendingWorldKey)) {
                           pendingWorldKey = key;
                           pendingWorldSeenAtMs = nowMs;
                        } else if (nowMs - pendingWorldSeenAtMs >= 750L) {
                           confirmedWorldKey = pendingWorldKey;
                           confirmedWorldExcluded = false;
                           pendingLoginServerKey = "";
                           clearPendingWorld();
                           sendFlyEnable(client, nowMs);
                        }
                     } else if (key.equals(confirmedWorldKey)) {
                        confirmedWorldExcluded = false;
                        clearPendingWorld();
                     } else if (!key.equals(pendingWorldKey)) {
                        pendingWorldKey = key;
                        pendingWorldSeenAtMs = nowMs;
                     } else {
                        long stableMs = isTeleportWatchArmed(nowMs) ? 250L : 750L;
                        if (nowMs - pendingWorldSeenAtMs >= stableMs) {
                           confirmedWorldKey = pendingWorldKey;
                           confirmedWorldExcluded = false;
                           pendingLoginServerKey = "";
                           clearPendingWorld();
                           teleportWatchUntilMs = 0L;
                           sendFlyEnable(client, nowMs);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public static void onServerSet(String serverName, MinecraftClient client, long nowMs) {
      if (SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
         if (SuiteConfig.INSTANCE.QolConfig != null && SuiteConfig.INSTANCE.QolConfig.autoFlyOnRwWorldLoad) {
            if (serverName != null && !serverName.isBlank()) {
               if (client != null && client.player != null && client.getNetworkHandler() != null) {
                  pendingLoginServerKey = normalizeKey(serverName);
                  if (!confirmedWorldKey.isBlank() && matchesLoginServer(confirmedWorldKey)) {
                     pendingLoginServerKey = "";
                  } else {
                     if (!pendingWorldKey.isBlank() && matchesLoginServer(pendingWorldKey)) {
                        confirmedWorldKey = pendingWorldKey;
                        confirmedWorldExcluded = false;
                        pendingLoginServerKey = "";
                        clearPendingWorld();
                        teleportWatchUntilMs = 0L;
                        sendFlyEnable(client, nowMs);
                     }
                  }
               }
            }
         }
      }
   }

   public static void observeOutgoingCommand(String command) {
      if (SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
         if (SuiteConfig.INSTANCE.QolConfig != null && SuiteConfig.INSTANCE.QolConfig.autoFlyOnRwWorldLoad) {
            if (!confirmedWorldExcluded) {
               if (isTeleportCommand(command)) {
                  if (!targetsExcludedWorld(command)) {
                     teleportWatchUntilMs = System.currentTimeMillis() + 12000L;
                  }
               }
            }
         }
      }
   }

   public static void tick(MinecraftClient client, long nowMs) {
      if (!SuiteConfig.INSTANCE.isEnabledForCurrentWorld() || SuiteConfig.INSTANCE.QolConfig == null || !SuiteConfig.INSTANCE.QolConfig.autoFlyOnRwWorldLoad) {
         clearPendingWorld();
         pendingLoginServerKey = "";
         teleportWatchUntilMs = 0L;
      }
   }

   private static boolean isTeleportWatchArmed(long nowMs) {
      if (teleportWatchUntilMs <= 0L) {
         return false;
      }

      if (nowMs <= teleportWatchUntilMs) {
         return true;
      }

      teleportWatchUntilMs = 0L;
      return false;
   }

   private static boolean isTeleportCommand(String command) {
      String[] parts = commandParts(command);
      if (parts.length == 0) {
         return false;
      }

      return switch (parts[0]) {
         case "back", "home", "spawn", "rtp", "wild", "warp", "is", "island" -> true;
         default -> false;
      };
   }

   private static boolean targetsExcludedWorld(String command) {
      String[] parts = commandParts(command);
      return parts.length < 2 ? false : SuiteRuntime.profile().isAutoFlyExcludedWorld(parts[1]);
   }

   private static String[] commandParts(String command) {
      if (command != null && !command.isBlank()) {
         String s = command.trim();
         if (!s.startsWith("/")) {
            return new String[0];
         }

         s = s.substring(1).trim();
         if (s.isBlank()) {
            return new String[0];
         }

         String folded = TextUtil.foldToLettersDigitsSpace(s);
         return folded.isBlank() ? new String[0] : folded.toLowerCase(Locale.ROOT).split("\\s+");
      } else {
         return new String[0];
      }
   }

   private static void sendFlyEnable(MinecraftClient client, long nowMs) {
      if (nowMs - lastCommandAtMs >= 1000L) {
         client.getNetworkHandler().sendChatCommand("fly enable");
         lastCommandAtMs = nowMs;
      }
   }

   private static void clearPendingWorld() {
      pendingWorldKey = "";
      pendingWorldSeenAtMs = 0L;
   }

   private static boolean matchesLoginServer(String worldKey) {
      return !pendingLoginServerKey.isBlank() && worldKey != null && worldKey.startsWith(pendingLoginServerKey + "|");
   }

   private static void clearLoginTriggerIfMatching(String worldKey) {
      if (matchesLoginServer(worldKey)) {
         pendingLoginServerKey = "";
      }
   }

   private static String normalizeKey(String value) {
      return value != null && !value.isBlank() ? TextUtil.foldToLettersDigitsSpace(value).replace(" ", "").toLowerCase(Locale.ROOT) : "unknown";
   }
}
