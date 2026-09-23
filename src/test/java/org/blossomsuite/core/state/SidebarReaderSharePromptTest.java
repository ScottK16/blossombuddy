package org.blossomsuite.core.state;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/**
 * The decision behind the vote-share prompt: off by default, and it should ask again each login (a new "generation"), not just
 * once ever, but go quiet the moment sharing is on or there is nowhere to share with. Reached via reflection since the real method
 * is package-private on purpose (no game-facing API needed), and it is pure, so no game state is needed to test it.
 */
class SidebarReaderSharePromptTest {
   private static boolean shouldPrompt(boolean share, boolean hasConfirmedServer, boolean relayReachable, long currentGeneration, long promptedForGeneration) throws Exception {
      Method m = SidebarReader.class.getDeclaredMethod("shouldPrompt", boolean.class, boolean.class, boolean.class, long.class, long.class);
      m.setAccessible(true);
      return (boolean)m.invoke(null, share, hasConfirmedServer, relayReachable, currentGeneration, promptedForGeneration);
   }

   @Test
   void promptsOnceTheFirstTimeSharingIsOffAndConnected() throws Exception {
      assertTrue(shouldPrompt(false, true, true, 1L, -1L));
   }

   @Test
   void doesNotRepeatWithinTheSameLoginOnceItHasAsked() throws Exception {
      assertFalse(shouldPrompt(false, true, true, 1L, 1L), "already asked for this generation");
   }

   @Test
   void asksAgainOnTheNextLoginOrRealmSwitch() throws Exception {
      assertTrue(shouldPrompt(false, true, true, 2L, 1L), "a new generation: log in again, or switched realms");
   }

   @Test
   void staysQuietOnceSharingIsOn() throws Exception {
      assertFalse(shouldPrompt(true, true, true, 5L, 1L), "they already opted in");
   }

   @Test
   void neverPromptsBeforeARealmIsConfirmed() throws Exception {
      assertFalse(shouldPrompt(false, false, true, 1L, -1L), "not settled on a realm yet");
   }

   @Test
   void neverPromptsWithNoRelayToShareWith() throws Exception {
      assertFalse(shouldPrompt(false, true, false, 1L, -1L));
   }

   @Test
   void turningSharingBackOffMidSessionDoesNotImmediatelyReAsk() throws Exception {
      // the same generation they already saw the prompt for, or later turned it on and off again
      assertFalse(shouldPrompt(false, true, true, 1L, 1L));
   }
}
