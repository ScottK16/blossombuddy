package org.blossomsuite.core.presence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.junit.jupiter.api.Test;

class PresenceMarkerTest {
   @Test
   void theSymbolGoesAfterTheNameAndKeepsTheNameAsItWas() {
      Text name = Text.literal("[VIP] Alice").formatted(Formatting.GOLD);
      Text marked = PresenceMarker.decorate(name);

      assertEquals("[VIP] Alice ✿", marked.getString());
      assertTrue(marked.getString().endsWith(PresenceMarker.SYMBOL));
      assertEquals(Formatting.GOLD.getColorValue(), marked.getStyle().getColor().getRgb(), "the server's own colouring of the name is untouched");
      assertEquals("[VIP] Alice", name.getString(), "the original text is not modified");
   }

   @Test
   void theSymbolIsBlossomPink() {
      Text marked = PresenceMarker.decorate(Text.literal("Bob"));
      Text symbol = marked.getSiblings().get(marked.getSiblings().size() - 1);
      assertEquals(0xF48FB1, symbol.getStyle().getColor().getRgb());
   }
}
