package org.blossomsuite.core.xchat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class NameStyleTest {
   @Test
   void onlyShortSymbolsFromTheSymbolBlocksAreAccepted() {
      assertEquals("✿", NameStyle.cleanGlyph("✿"));
      assertEquals("♛", NameStyle.cleanGlyph("♛"));
      assertEquals("❤️".substring(0, 1), NameStyle.cleanGlyph("❤"));
      assertEquals("", NameStyle.cleanGlyph("A"), "letters are not symbols");
      assertEquals("", NameStyle.cleanGlyph("💩"), "emoji outside the game's font are refused");
      assertEquals("", NameStyle.cleanGlyph("✿✿✿"), "too long");
      assertEquals("", NameStyle.cleanGlyph(""));
      assertEquals("", NameStyle.cleanGlyph(null));
      assertEquals("", NameStyle.cleanGlyph("\n"));
   }

   @Test
   void colourListsMustBeProperGradients() {
      assertNotNull(NameStyle.colorsOf(List.of("#ff0000", "#0000ff")));
      assertNull(NameStyle.colorsOf(List.of("#ff0000")));
      assertNull(NameStyle.colorsOf(List.of("red", "blue")));
      assertNull(NameStyle.colorsOf(null));
      assertNull(NameStyle.colorsOf(List.of("#111111", "#222222", "#333333", "#444444", "#555555", "#666666")));
   }

   @Test
   void aGradientNameHasOneColourPerCharacterFromFirstToLast() {
      Component name = NameStyle.gradient("Alice", new String[]{"#ff0000", "#0000ff"});
      assertEquals("Alice", name.getString());
      List<Component> parts = name.getSiblings();
      assertEquals(5, parts.size());
      assertEquals(0xFF0000, parts.get(0).getStyle().getColor().getValue());
      assertEquals(0x0000FF, parts.get(4).getStyle().getColor().getValue());
      int middle = parts.get(2).getStyle().getColor().getValue();
      assertTrue(((middle >> 16) & 0xFF) > 0 && (middle & 0xFF) > 0, "blended in the middle");
   }

   @Test
   void theStyledNameHasTheSymbolAfterItInTheGradientsFirstColour() {
      Component plain = Component.literal("Bob");
      Component styled = NameStyle.styled("Bob", Component.literal("Bob"), new String[]{"#ff0000", "#0000ff"}, "✿");
      assertEquals("Bob ✿", styled.getString());
      Component symbol = NameStyle.symbol("✿", new String[]{"#ff0000", "#0000ff"}, 0xFFFFFF);
      assertEquals(0xFF0000, symbol.getStyle().getColor().getValue());

      assertEquals("Bob", NameStyle.styled("Bob", Component.literal("Bob"), null, null).getString());
      assertEquals("Bob", NameStyle.styled("Bob", Component.literal("Bob"), null, "💩").getString(), "a refused symbol shows nothing");
      assertEquals("Bob", plain.getString());
   }
}
