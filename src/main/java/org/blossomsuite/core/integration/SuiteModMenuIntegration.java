package org.blossomsuite.core.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import org.blossomsuite.core.ui.SuiteSettingsScreen;

public final class SuiteModMenuIntegration implements ModMenuApi {
   @Override
   public ConfigScreenFactory<?> getModConfigScreenFactory() {
      return SuiteSettingsScreen::new;
   }
}
