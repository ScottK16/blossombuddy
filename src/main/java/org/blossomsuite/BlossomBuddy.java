package org.blossomsuite;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlossomBuddy implements ModInitializer {
   public static final String MOD_ID = "blossombuddy";
   public static final Logger LOGGER = LoggerFactory.getLogger("blossombuddy");

   public void onInitialize() {
      LOGGER.info("BlossomBuddy loaded");
   }
}
