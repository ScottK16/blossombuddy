package org.blossomsuite.core.ui;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import org.blossomsuite.core.state.SuiteScheduler;
import org.blossomsuite.core.util.SuiteLog;

/**
 * The right skin for a player in the player list. A bare UUID and name carry no skin, so the game would show a default Steve or
 * Alex. Players on the realm you are on are in your tab list, which already has their skin; everyone else is looked up from
 * Mojang's session server (the same lookup the game does for skins) in the background, and shown as the default skin until it
 * arrives.
 */
final class PlayerHeads {
   private static final long RETRY_AFTER_MS = 60_000L;
   private static final Map<UUID, Supplier<SkinTextures>> FETCHED = new ConcurrentHashMap<>();
   private static final Map<UUID, Long> STARTED = new ConcurrentHashMap<>();

   private PlayerHeads() {
   }

   static SkinTextures textures(MinecraftClient mc, UUID id) {
      ClientPlayNetworkHandler network = mc.getNetworkHandler();
      PlayerListEntry entry = network == null ? null : network.getPlayerListEntry(id);
      if (entry != null) {
         return entry.getSkinTextures();
      }

      Supplier<SkinTextures> fetched = FETCHED.get(id);
      if (fetched != null) {
         return fetched.get();
      }

      startLookup(mc, id);
      return DefaultSkinHelper.getSkinTextures(id);
   }

   private static void startLookup(MinecraftClient mc, UUID id) {
      long now = System.currentTimeMillis();
      Long last = STARTED.get(id);
      if (last != null && now - last < RETRY_AFTER_MS) {
         return;
      }

      STARTED.put(id, now);
      SuiteScheduler.IO.execute(() -> {
         try {
            ProfileResult result = mc.getSessionService().fetchProfile(id, false);
            if (result != null && result.profile() != null) {
               GameProfile profile = result.profile();
               mc.execute(() -> FETCHED.put(id, mc.getSkinProvider().getSkinTexturesSupplier(profile)));
            }
         } catch (Throwable t) {
            SuiteLog.logger().debug("[players] skin lookup failed for {}: {}", id, t.toString());
         }
      });
   }
}
