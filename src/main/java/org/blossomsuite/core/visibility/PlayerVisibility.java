package org.blossomsuite.core.visibility;

import net.minecraft.client.MinecraftClient;
import org.blossomsuite.core.config.FeatureConfig;
import org.blossomsuite.core.util.WorldGate;
import org.blossomsuite.core.vote.VotePartySnapshot;
import org.blossomsuite.core.vote.VoteState;

/**
 * Hiding other players on your own screen (a crowded vote party can be hard to see through, and hard on the frame rate). Purely
 * client-side: nobody else is affected and the game still knows they are there. The switch is for this session only, so a forgotten
 * one is gone next time you join; the option to do it automatically during a vote party is saved.
 */
public final class PlayerVisibility {
   /** How recent the vote-party reading must be for "a party is on" to be believed. */
   static final long FRESH_MS = 90_000L;

   private static volatile boolean manual = false;

   private PlayerVisibility() {
   }

   public static boolean manual() {
      return manual;
   }

   public static void set(boolean hidden) {
      manual = hidden;
   }

   public static boolean toggle() {
      manual = !manual;
      return manual;
   }

   /** Should other players be left out of the picture right now? */
   public static boolean hidden() {
      if (manual) {
         return true;
      }

      if (!FeatureConfig.INSTANCE.render.hidePlayersDuringVoteParty) {
         return false;
      }

      return partyRunning(VoteState.get(WorldGate.Server), System.currentTimeMillis());
   }

   /** A vote party is going on for the realm this reading is from, and the reading is recent. */
   static boolean partyRunning(VotePartySnapshot snapshot, long now) {
      return snapshot != null && snapshot.isPartyOngoing() && now - snapshot.getSeenAt() <= FRESH_MS;
   }

   /** Whether this player should be drawn: never you, and only players who really are in the tab list (server NPCs are left alone). */
   public static boolean shouldSkip(net.minecraft.entity.Entity entity) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client == null || client.player == null || entity == client.player || !(entity instanceof net.minecraft.entity.player.PlayerEntity)) {
         return false;
      }

      if (!hidden() || client.getNetworkHandler() == null) {
         return false;
      }

      return client.getNetworkHandler().getPlayerListEntry(entity.getUuid()) != null;
   }
}
