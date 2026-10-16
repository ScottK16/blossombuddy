package org.blossomsuite.core.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Only runs when DISCORD_FAKE_PIPE=1: talks to a stand-in Discord (a small script listening on the real named pipe or socket) through the
 * real transport, to check the operating-system side that the unit tests fake. Not part of a normal build.
 */
class RealPipeManualTest {
   @Test
   @EnabledIfEnvironmentVariable(named = "DISCORD_FAKE_PIPE", matches = "1")
   void theRealTransportCompletesTheHandshakeAndSendsAnActivity() throws Exception {
      long[] now = {1_000_000L};
      DiscordPresence presence = new DiscordPresence(DiscordTransports.system(), Runnable::run, () -> now[0], 4242L);
      DiscordPresence.Activity activity = new DiscordPresence.Activity("On Cherry Realm", "", 1_700_000_000L, "logo", "BlossomBuddy", "Get BlossomBuddy", "https://blossombuddy.site");
      presence.tick("1550998827996160100", activity);
      assertEquals("Connected to Discord", presence.status(), presence.status());
      presence.tick("1550998827996160100", activity);
      assertEquals("Showing on Discord", presence.status(), presence.status());
      now[0] += 20_000L;
      presence.tick("1550998827996160100", null);
      presence.stop();
      assertEquals("Off", presence.status());
   }
}
