package org.blossomsuite.core.emote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The emotes the mod offers and the emotes the relay accepts must be the same list, or some would silently never be seen. */
class EmoteIdsMatchRelayTest {
   @Test
   void theRelaysDefaultListIsTheModsList() throws Exception {
      Path file = Path.of("relay", "emotes.js");
      assumeTrue(Files.exists(file), "the relay folder is not next to the mod sources");

      Matcher m = Pattern.compile("DEFAULT_EMOTES = \\[([^\\]]*)]").matcher(Files.readString(file));
      assumeTrue(m.find(), "DEFAULT_EMOTES not found in relay/emotes.js");
      List<String> relay = new ArrayList<>();
      Matcher id = Pattern.compile("'([a-z0-9_]+)'").matcher(m.group(1));
      while (id.find()) {
         relay.add(id.group(1));
      }

      List<String> mod = Emote.ALL.stream().map(Emote::id).toList();
      assertEquals(mod, relay, "keep EMOTE list in relay/emotes.js (DEFAULT_EMOTES) and Emote.ALL in step");
   }
}
