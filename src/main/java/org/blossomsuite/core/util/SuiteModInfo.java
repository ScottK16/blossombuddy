package org.blossomsuite.core.util;

import org.blossomsuite.core.SuiteRuntime;
import net.fabricmc.loader.api.FabricLoader;

public final class SuiteModInfo {
   private SuiteModInfo() {
   }

   public static String getVersion() {
      return FabricLoader.getInstance()
         .getModContainer(SuiteRuntime.profile().modId())
         .map(c -> c.getMetadata().getVersion().getFriendlyString())
         .orElse("unknown");
   }
}
