package org.blossomsuite.core.presence;

import net.minecraft.text.Text;

/** The little symbol next to the name of a player who is using BlossomBuddy, in the tab list. */
public final class PresenceMarker {
   /** A flower, in the mod's blossom pink. */
   public static final String SYMBOL = "✿";
   private static final int PINK = 0xF48FB1;

   private PresenceMarker() {
   }

   /** The same name with the symbol after it (after, so ranks and prefixes stay where the server put them). */
   public static Text decorate(Text name) {
      return name.copy().append(Text.literal(" " + SYMBOL).styled(style -> style.withColor(PINK)));
   }
}
