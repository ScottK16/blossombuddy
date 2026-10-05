package org.blossomsuite.core.alts;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import org.blossomsuite.core.config.FeatureConfig;
import org.blossomsuite.core.util.WorldGate;

/**
 * For alt accounts: leave the server when it is crowded, since alts aren't allowed to stay once it reaches the player limit.
 * Off unless the player turns it on. It only ever acts on a BlossomCraft realm the mod has recognised, never on other servers.
 */
public final class AltGuard {
   public static final int DEFAULT_LIMIT = 60;
   public static final int MIN_LIMIT = 10;
   public static final int MAX_LIMIT = 200;
   /** The tab list fills in just after joining; don't judge a half-loaded one. */
   static final long SETTLE_MS = 5_000L;
   private static final int CHECK_EVERY_TICKS = 20;

   private static Object lastPlayer;
   private static long joinedAtMs;
   private static int ticks;

   private AltGuard() {
   }

   /** Should the alt leave? True when the guard is on and the server is at or over the limit. */
   public static boolean shouldLeave(boolean enabled, int limit, int online) {
      return enabled && online >= limit;
   }

   public static int clampLimit(int limit) {
      return Math.max(MIN_LIMIT, Math.min(MAX_LIMIT, limit));
   }

   public static void tick(Minecraft client) {
      if (client.player != lastPlayer) {
         lastPlayer = client.player;
         joinedAtMs = System.currentTimeMillis();
         ticks = 0;
      }

      FeatureConfig.AltAccount cfg = FeatureConfig.INSTANCE.altAccount;
      if (!cfg.leaveWhenCrowded || client.player == null || ++ticks % CHECK_EVERY_TICKS != 0) {
         return;
      }

      ClientPacketListener handler = client.getConnection();
      if (handler == null || !WorldGate.isActive() || System.currentTimeMillis() - joinedAtMs < SETTLE_MS) {
         return;
      }

      int online = handler.getListedOnlinePlayers().size();
      int limit = clampLimit(cfg.playerLimit);
      if (shouldLeave(cfg.leaveWhenCrowded, limit, online)) {
         handler.getConnection().disconnect(Component.literal("BlossomBuddy: left because " + online + " players are online (alt limit " + limit + "). Turn this off in /buddy > General."));
      }
   }
}
