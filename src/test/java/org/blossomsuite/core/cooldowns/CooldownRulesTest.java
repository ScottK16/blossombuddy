package org.blossomsuite.core.cooldowns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import org.blossomsuite.core.config.FeatureConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The cooldown rule file format that used to be downloaded from the original developer's server. */
class CooldownRulesTest {
   private static final String SAMPLE = """
      {
        "version": "0.5.43",
        "rules": [
          { "trigger": "SNEAK_BREAK" , "ms": 20000 , "id": "FABLES_AURORACARVER" , "fallback": "aurora carver" },
          { "trigger": "RIGHT_CLICK" , "ms": 40000 , "id": "WARDEN_OREDETECTOR2" , "fallback": "ore exposer" },
          { "trigger": "SNEAK_RIGHT_CLICK" , "ms": 7000 , "id": "BLOSSOM_BLOSSOMSTAFF" , "fallback": "blossom staff" },
          { "trigger": "NOT_A_TRIGGER" , "ms": 1000 , "id": "IGNORED_RULE" },
          { "trigger": "RIGHT_CLICK" , "ms": 0 , "id": "ZERO_MS_IS_IGNORED" }
        ]
      }
      """;

   @Test
   void parsesRulesAndSkipsInvalidOnes() {
      CooldownRules.applyFromJson(SAMPLE);

      CooldownRules.CooldownRule carver = CooldownRules.byId.get("FABLES_AURORACARVER");
      assertNotNull(carver, "rule keyed by its item id");
      assertEquals(20000L, carver.ms());
      assertEquals(CooldownRules.Trigger.SNEAK_BREAK, carver.trigger());

      assertNotNull(CooldownRules.byId.get("BLOSSOM_BLOSSOMSTAFF"));
      assertEquals(3, CooldownRules.byId.size(), "unknown trigger and zero-length rules are dropped");
      assertTrue(CooldownRules.byFallback.containsKey("blossom staff"));
   }

   @AfterEach
   void switchEverythingBackOn() {
      FeatureConfig.INSTANCE.disabledCooldownItems = new ArrayList<>();
   }

   @Test
   void switchedOffItemsAreDroppedButStillListedForTheSettingsPage() {
      FeatureConfig.INSTANCE.disabledCooldownItems = new ArrayList<>(java.util.List.of("FABLES_AURORACARVER"));
      CooldownRules.applyFromJson(SAMPLE);

      assertEquals(2, CooldownRules.byId.size(), "the switched-off item no longer tracks");
      assertTrue(CooldownRules.byId.containsKey("BLOSSOM_BLOSSOMSTAFF"));
      assertEquals(3, CooldownRules.allRuleInfo().size(), "but it is still listed so it can be switched back on");
      assertTrue(CooldownRules.allRuleInfo().stream().anyMatch(r -> r.name().equals("Aurora Carver")));

      FeatureConfig.INSTANCE.disabledCooldownItems.clear();
      CooldownRules.reapply();
      assertEquals(3, CooldownRules.byId.size(), "switching it back on restores the rule");
   }
}
