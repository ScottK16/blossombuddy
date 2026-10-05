package org.blossomsuite.core.presence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.junit.jupiter.api.Test;

class PresenceMarkerTest {
   @Test
   void theSymbolGoesAfterTheNameAndKeepsTheNameAsItWas() {
      Component name = Component.literal("[VIP] Alice").withStyle(ChatFormatting.GOLD);
      Component marked = PresenceMarker.decorate(name);

      assertEquals("[VIP] Alice ✿", marked.getString());
      assertTrue(marked.getString().endsWith(PresenceMarker.SYMBOL));
      assertEquals(ChatFormatting.GOLD.getColor(), marked.getStyle().getColor().getValue(), "the server's own colouring of the name is untouched");
      assertEquals("[VIP] Alice", name.getString(), "the original text is not modified");
   }

   @Test
   void theSymbolIsBlossomPink() {
      Component marked = PresenceMarker.decorate(Component.literal("Bob"));
      Component symbol = marked.getSiblings().get(marked.getSiblings().size() - 1);
      assertEquals(0xF48FB1, symbol.getStyle().getColor().getValue());
   }
}
