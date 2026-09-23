package org.blossomsuite;

import org.blossomsuite.core.SuiteDungeon;
import org.blossomsuite.core.SuiteFeature;
import org.blossomsuite.core.SuiteProfile;
import org.blossomsuite.core.SuiteResource;
import org.blossomsuite.core.SuiteServer;
import java.util.EnumSet;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class BlossomBuddyProfile {
   public static final SuiteProfile INSTANCE = new SuiteProfile(
      "blossombuddy",
      "BlossomBuddy",
      "buddy",
      List.of("bb", "bsuite"),
      "blossombuddy.json",
      "", // no backend by default: the upstream host/key belong to the original developer
      "",
      "X-BlossomBuddy-Key",
      EnumSet.complementOf(EnumSet.of(SuiteFeature.REMOTE_CONFIG, SuiteFeature.RELAY)),
      List.of(new SuiteServer("cherry", "Cherry"), new SuiteServer("spirit", "Spirit"), new SuiteServer("lotus", "Lotus"), new SuiteServer("tulip", "Tulip")),
      List.of(new SuiteDungeon("akuma", "Akuma", List.of("akuma"))),
      List.of(new SuiteResource("blossom_petals", "Blossom Petals", List.of("blossom petal", "blossompetal"), List.of())),
      List.of("Pinata", "AkumasCitadel_0")
   );

   private BlossomBuddyProfile() {
   }
}
