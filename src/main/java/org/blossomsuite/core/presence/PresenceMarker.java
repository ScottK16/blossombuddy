package org.blossomsuite.core.presence;

import net.minecraft.network.chat.Component;
/** The little symbol next to the name of a player who is using BlossomBuddy, in the tab list. */
public final class PresenceMarker {
   /** A flower, in the mod's blossom pink. */
   public static final String SYMBOL = "✿";
   private static final int PINK = 0xF48FB1;

   private PresenceMarker() {
   }

   /** The same name with the symbol after it (after, so ranks and prefixes stay where the server put them). */
   public static Component decorate(Component name) {
      return name.copy().append(Component.literal(" " + SYMBOL).withStyle(style -> style.withColor(PINK)));
   }
}
