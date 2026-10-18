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

   @Test
   void theListBuiltIntoTheModIsPresentAndEveryRuleInItIsUsable() {
      String bundled = CooldownRules.bundledDefaultJson();
      assertNotNull(bundled, "a new player must get cooldown rules without any file");
      CooldownRules.applyFromJson(bundled);
      assertTrue(CooldownRules.byId.size() > 80, "a real list, not a stub: " + CooldownRules.byId.size());
      com.google.gson.JsonArray rules = com.google.gson.JsonParser.parseString(bundled).getAsJsonObject().getAsJsonArray("rules");
      assertTrue(rules.size() >= 100);
      for (com.google.gson.JsonElement e : rules) {
         com.google.gson.JsonObject o = e.getAsJsonObject();
         assertTrue(o.get("ms").getAsLong() > 0, o.toString());
         CooldownRules.Trigger.valueOf(o.get("trigger").getAsString());
         assertTrue(o.has("id") || o.has("fallback"), o.toString());
      }

      assertTrue(CooldownRules.allRuleInfo().size() > 20, "the Cooldowns tab lists them");
   }

   @Test
   void theOldDownloadedFormatStillMeansTheSame() {
      assertEquals("0.5.43", CooldownRules.versionOf(SAMPLE));
      assertEquals("", CooldownRules.versionOf("not json"));
   }

   @Test
   void versionsComparePartByPart() {
      assertEquals(0, CooldownRules.compareVersions("0.5.43", "0.5.43"));
      assertTrue(CooldownRules.compareVersions("0.5.9", "0.5.43") < 0, "9 is less than 43, not more");
      assertTrue(CooldownRules.compareVersions("0.6", "0.5.99") > 0);
      assertTrue(CooldownRules.compareVersions("", "0.1") < 0);
   }

   @Test
   void aPlayersOwnFileWinsUnlessTheBuiltInListIsNewer() {
      String mine = "{\"version\":\"0.5.43\",\"rules\":[]}";
      String older = "{\"version\":\"0.4.0\",\"rules\":[]}";
      String bundled = "{\"version\":\"0.5.43\",\"rules\":[{\"trigger\":\"RIGHT_CLICK\",\"ms\":1,\"id\":\"X\"}]}";
      assertEquals(mine, CooldownRules.chooseJson(mine, bundled), "same version: their file is respected");
      assertEquals(bundled, CooldownRules.chooseJson(older, bundled), "an old copy does not hide new items");
      assertEquals(bundled, CooldownRules.chooseJson(null, bundled), "no file: the built-in list");
      assertEquals(bundled, CooldownRules.chooseJson("   ", bundled));
      assertEquals(mine, CooldownRules.chooseJson(mine, null), "a build with no built-in list still uses the file");
      assertEquals(null, CooldownRules.chooseJson(null, null));
   }
}
