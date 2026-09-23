package org.blossomsuite.core.util;

import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.config.SuiteConfig;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;

public final class WorldGate {
   private static Runnable resetHook = () -> {};
   private static BiConsumer<String, MinecraftClient> realmSetHook = (server, client) -> {};
   private static Consumer<String> activeLogger = server -> {};
   private static Runnable becameActiveHook = () -> {};
   private static volatile boolean active = false;
   private static volatile String lastDetectedServer = null;
   private static volatile long lastDetectedServerAtMs = 0L;
   public static String Server = null;

   private WorldGate() {
   }

   public static boolean isActive() {
      return active && SuiteConfig.INSTANCE.isEnabledForCurrentWorld();
   }

   public static void setResetHook(Runnable hook) {
      resetHook = hook != null ? hook : () -> {};
   }

   public static void setRealmSetHook(BiConsumer<String, MinecraftClient> hook) {
      realmSetHook = hook != null ? hook : (server, client) -> {};
   }

   public static void setActiveLogger(Consumer<String> logger) {
      activeLogger = logger != null ? logger : server -> {};
   }

   public static void setBecameActiveHook(Runnable hook) {
      becameActiveHook = hook != null ? hook : () -> {};
   }

   public static void reset() {
      active = false;
      Server = null;
      lastDetectedServer = null;
      lastDetectedServerAtMs = 0L;
      resetHook.run();
   }

   public static void noteDetectedRealm(String server, long now) {
      if (SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
         if (server != null && !server.isBlank()) {
            lastDetectedServer = SuiteRuntime.profile().resolveServerKey(server);
            lastDetectedServerAtMs = now;
         }
      }
   }

   public static String bestVoteDataRealm(long now) {
      String detected = lastDetectedServer;
      return detected != null && !detected.isBlank() && now - lastDetectedServerAtMs <= 2500L ? detected : Server;
   }

   public static String recentDetectedVoteDataRealm(long now) {
      String detected = lastDetectedServer;
      return detected != null && !detected.isBlank() && now - lastDetectedServerAtMs <= 2500L ? detected : null;
   }

   public static void SetRealm(String server) {
      if (SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
         if (server != null) {
            noteDetectedRealm(server, System.currentTimeMillis());
            Server = SuiteRuntime.profile().resolveServerKey(server);
            activeLogger.accept(Server);
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getNetworkHandler() != null) {
               realmSetHook.accept(Server, client);
            }

            if (!active) {
               active = true;
               becameActiveHook.run();
            }
         }
      }
   }
}
