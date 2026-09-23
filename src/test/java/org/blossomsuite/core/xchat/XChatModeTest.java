package org.blossomsuite.core.xchat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.blossomsuite.core.config.FeatureConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Cross-realm chat mode: what typed text goes to every realm and what still goes to the server. */
class XChatModeTest {
   @BeforeEach
   void setUp() {
      FeatureConfig.INSTANCE.xchat.enabled = true;
      XChatMode.set(false);
   }

   @AfterEach
   void tearDown() {
      XChatMode.set(false);
   }

   @Test
   void itStartsOffSoNothingGoesToOtherRealmsByAccident() {
      assertFalse(XChatMode.active());
      assertNull(XChatMode.redirect("hello"), "with the mode off, chat goes to the server as always");
   }

   @Test
   void withTheModeOnPlainMessagesGoToEveryRealm() {
      assertTrue(XChatMode.toggle());
      assertTrue(XChatMode.active());
      assertEquals("hello everyone", XChatMode.redirect("hello everyone"));
      assertEquals("spaced out", XChatMode.redirect("   spaced out  "), "surrounding spaces are dropped");
   }

   @Test
   void commandsAndEmptyLinesAreNeverRedirected() {
      XChatMode.set(true);
      assertNull(XChatMode.redirect("/tpa Bob"), "commands still work");
      assertNull(XChatMode.redirect("  /home"), "even with a space in front");
      assertNull(XChatMode.redirect("/xc"), "so /xc itself can switch the mode off");
      assertNull(XChatMode.redirect(""));
      assertNull(XChatMode.redirect("   "));
      assertNull(XChatMode.redirect(null));
   }

   @Test
   void togglingSwitchesBackAndForth() {
      assertTrue(XChatMode.toggle());
      assertFalse(XChatMode.toggle());
      assertNull(XChatMode.redirect("hi"));
   }

   @Test
   void ifCrossRealmChatItselfIsOffTheModeDoesNothing() {
      XChatMode.set(true);
      FeatureConfig.INSTANCE.xchat.enabled = false;
      assertFalse(XChatMode.active());
      assertNull(XChatMode.redirect("hello"), "a message is never swallowed with nowhere to go");
   }
}
