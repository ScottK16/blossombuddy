package org.blossomsuite.core.emote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** The emote menu draws a picture for every emote; a missing or wrongly sized one would show as a magenta box in the game. */
class EmoteImagesTest {
   @Test
   void everyEmoteHasA180By140PictureInTheMod() throws IOException {
      for (Emote e : Emote.ALL) {
         try (InputStream in = EmoteImagesTest.class.getResourceAsStream("/assets/blossombuddy/textures/emote/" + e.id() + ".png")) {
            assertNotNull(in, e.id() + " has no picture");
            var image = ImageIO.read(in);
            assertNotNull(image, e.id() + " is not a readable PNG");
            assertEquals(180, image.getWidth(), e.id() + " width");
            assertEquals(140, image.getHeight(), e.id() + " height");
         }
      }
   }
}
