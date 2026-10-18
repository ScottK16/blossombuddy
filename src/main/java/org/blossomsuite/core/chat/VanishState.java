package org.blossomsuite.core.chat;

import org.blossomsuite.core.util.TextStrings;
import java.util.Locale;

public final class VanishState {
   public static volatile boolean vanished = false;
   /** The sidebar showed a "Visibility" line this session, so this player is staff. Worked out afresh on every join. */
   public static volatile boolean staffSidebar = false;

   private VanishState() {
   }

   /**
    * With "auto-vanish on join" (for staff the server vanishes whenever they join) it is switched ON here instead, so nobody is on the player
    * list or Discord for the moment before a vanish message arrives, or when none does.
    *
    * <p>Joining a server or changing realm does NOT clear it any more: the server only prints "Vanish for X: ENABLED" when vanish is switched,
    * not on every join, so forgetting here put vanished staff back on the player list and on Discord. It only changes when the server says
    * so (or the player corrects it with /buddy vanish). If that ever leaves someone hidden by mistake, that is the safe way to be wrong.
    */
   public static void resetForJoin() {
      staffSidebar = false; // learned again from the sidebar of the server joined
      if (org.blossomsuite.core.config.FeatureConfig.INSTANCE.staff.autoVanish) {
         setVanished(true); // the server vanishes this player on every join, and does not necessarily say so
      }
   }

   /**
    * Whether this player must be kept off Discord and the player list: vanished, or staff (unless they chose to show while visible). A staff
    * member who is simply never shown gives nothing away when they toggle vanish.
    */
   public static boolean hiddenFromLists() {
      return vanished || (staffSidebar && !org.blossomsuite.core.config.FeatureConfig.INSTANCE.staff.showWhenStaff);
   }

   /** Reads what was last known from the saved settings (call once the settings are loaded). */
   public static void loadFromConfig() {
      vanished = org.blossomsuite.core.config.FeatureConfig.INSTANCE.staff.vanished;
   }

   public static void setVanished(boolean value) {
      vanished = value;
      org.blossomsuite.core.config.FeatureConfig.INSTANCE.staff.vanished = value;
      org.blossomsuite.core.config.FeatureConfig.markDirty();
   }

   /**
    * "Vanish for BaraGC: ENABLED" / "Vanish for ~Bara: DISABLED" - the server sometimes shows a nicknamed,
    * truncated form of the name (the tilde folds away with the rest of the punctuation), so the name in the
    * line is matched as an exact match OR a prefix either way against the local player's real name.
    */
   public static Boolean parseServerLine(String text, String selfName) {
      if (text == null || text.isBlank() || selfName == null || selfName.isBlank()) {
         return null;
      }

      String folded = TextStrings.foldToLettersDigitsSpace(text).toLowerCase(Locale.ROOT);
      if (!folded.startsWith("vanish for ")) {
         return null;
      }

      boolean enabled;
      String rest;
      if (folded.endsWith(" enabled")) {
         enabled = true;
         rest = folded.substring("vanish for ".length(), folded.length() - " enabled".length());
      } else if (folded.endsWith(" disabled")) {
         enabled = false;
         rest = folded.substring("vanish for ".length(), folded.length() - " disabled".length());
      } else {
         return null;
      }

      String name = rest.trim();
      String self = selfName.trim().toLowerCase(Locale.ROOT);
      if (name.isEmpty() || self.isEmpty()) {
         return null;
      }

      return name.equals(self) || self.startsWith(name) || name.startsWith(self) ? enabled : null;
   }
}
