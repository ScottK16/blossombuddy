package org.blossomsuite.core.xchat;

import org.blossomsuite.core.chat.ChatOutput;
import org.blossomsuite.core.config.FeatureConfig;

/**
 * Cross-realm chat mode: while it is on, whatever you type in the chat box goes to every realm, so you don't have to start each
 * message with {@code /xc}. Commands (anything starting with "/") still work normally. It is never saved: it always starts off,
 * and switches itself off when you leave the world, so nothing can be sent to other realms by accident after a restart.
 */
public final class XChatMode {
   private static volatile boolean on = false;

   private XChatMode() {
   }

   /** On, and cross-realm chat itself is on. */
   public static boolean active() {
      return on && FeatureConfig.INSTANCE.xchat.enabled;
   }

   public static boolean toggle() {
      on = !on;
      return on;
   }

   public static void set(boolean value) {
      on = value;
   }

   /**
    * What to do with something just typed in the chat box.
    *
    * @return the text to send to every realm, or null to let it go to the server as usual (mode off, a command, or empty)
    */
   public static String redirect(String typed) {
      if (!active() || typed == null) {
         return null;
      }

      String text = typed.strip();
      return text.isEmpty() || text.startsWith("/") ? null : text;
   }

   /** The {@code /xc} command and the hotkey: switch the mode and say what it is now. */
   public static void toggleAndTell() {
      if (!FeatureConfig.INSTANCE.xchat.enabled) {
         ChatOutput.info("Cross-realm chat is off. Turn it on with /buddy xchat on.");
         return;
      }

      tell(toggle());
   }

   public static void tell(boolean now) {
      ChatOutput.info(now
         ? "Cross-realm chat mode is ON: everything you type goes to every realm. Type /xc again to switch it off. Commands starting with / still work as usual."
         : "Cross-realm chat mode is off: what you type goes to the server again.");
   }
}
