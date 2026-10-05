package org.blossomsuite.core.state;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import org.blossomsuite.core.chat.ChatOutput;
import org.blossomsuite.core.config.FeatureConfig;
import org.blossomsuite.core.vote.VoteRuntime;

/**
 * Reads the sidebar scoreboard straight from the game's scoreboard data a few times a second, so the realm and
 * vote-party count are picked up automatically whatever draws the scoreboard (or whether it is drawn at all).
 */
public final class SidebarReader {
   private static int ticks = 0;
   /** The vote-runtime "generation" (bumped once per confirmed login or realm switch) the share prompt was last shown for. */
   private static long promptedForGeneration = -1L;

   private SidebarReader() {
   }

   public static void init() {
      ClientTickEvents.END_CLIENT_TICK.register(SidebarReader::tick);
   }

   private static void tick(Minecraft client) {
      if (client.level == null || client.player == null || ++ticks % 5 != 0) {
         return;
      }

      Objective objective = sidebarObjective(client);
      if (objective != null) {
         SidebarParser.process(objective);
      }

      maybeShowSharePrompt();
   }

   /** The objective the game would show in the sidebar for this player (their team colour's slot first, like vanilla). */
   private static Objective sidebarObjective(Minecraft client) {
      Scoreboard scoreboard = client.level.getScoreboard();
      PlayerTeam team = scoreboard.getPlayersTeam(client.player.getScoreboardName());
      if (team != null && team.getColor() != null) {
         DisplaySlot slot = DisplaySlot.teamColorToSlot(team.getColor());
         if (slot != null) {
            Objective teamObjective = scoreboard.getDisplayObjective(slot);
            if (teamObjective != null) {
               return teamObjective;
            }
         }
      }

      return scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
   }

   /**
    * Vote-party sharing is off by default. While it stays off, this asks again each time the player logs in (or switches
    * realms) - not just once ever - so someone who missed it, or who joins a lot of different realms, keeps getting the
    * chance to help. It goes quiet the moment sharing is turned on, and won't ask again for the rest of that connection.
    */
   private static void maybeShowSharePrompt() {
      FeatureConfig.Relay relay = FeatureConfig.INSTANCE.relay;
      boolean relayReachable = SuiteState.INSTANCE.http != null && SuiteState.INSTANCE.http.enabled();
      if (shouldPrompt(relay.share, VoteRuntime.hasConfirmedServer(), relayReachable, VoteRuntime.sessionGeneration(), promptedForGeneration)) {
         promptedForGeneration = VoteRuntime.sessionGeneration();
         ChatOutput.info(
            "Help other realms see this realm's vote party: BlossomBuddy can share just the realm name and vote count (nothing about you) with players on Cherry, Spirit, Lotus and Tulip. Turn it on with /buddy share on."
         );
      }
   }

   /** The decision on its own, so it can be tested without a running game. */
   static boolean shouldPrompt(boolean share, boolean hasConfirmedServer, boolean relayReachable, long currentGeneration, long promptedForGeneration) {
      return !share && hasConfirmedServer && relayReachable && currentGeneration != promptedForGeneration;
   }
}
