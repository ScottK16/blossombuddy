package org.blossomsuite.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.blossomsuite.core.config.FeatureConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ThemeTest {
   @AfterEach
   void back() {
      FeatureConfig.INSTANCE.render.theme = Theme.CLASSIC;
      Theme.refresh();
   }

   @Test
   void autoIsHalloweenFromTheFirstOfOctoberToTheSecondOfNovember() {
      assertFalse(Theme.halloweenOn("AUTO", LocalDate.of(2026, 9, 30)));
      assertTrue(Theme.halloweenOn("AUTO", LocalDate.of(2026, 10, 1)));
      assertTrue(Theme.halloweenOn("AUTO", LocalDate.of(2026, 10, 31)));
      assertTrue(Theme.halloweenOn("AUTO", LocalDate.of(2026, 11, 2)));
      assertFalse(Theme.halloweenOn("AUTO", LocalDate.of(2026, 11, 3)));
      assertFalse(Theme.halloweenOn("AUTO", LocalDate.of(2027, 4, 15)));
   }

   @Test
   void theChoiceOverridesTheCalendar() {
      assertTrue(Theme.halloweenOn("HALLOWEEN", LocalDate.of(2026, 3, 1)));
      assertFalse(Theme.halloweenOn("CLASSIC", LocalDate.of(2026, 10, 31)));
      assertTrue(Theme.halloweenOn("nonsense", LocalDate.of(2026, 10, 31)), "anything unknown reads as auto");
      assertTrue(Theme.halloweenOn(null, LocalDate.of(2026, 10, 31)));
   }

   @Test
   void theChoiceCyclesRoundAndHasALabel() {
      assertEquals("HALLOWEEN", Theme.nextMode("AUTO"));
      assertEquals("CLASSIC", Theme.nextMode("HALLOWEEN"));
      assertEquals("AUTO", Theme.nextMode("CLASSIC"));
      assertEquals("Halloween", Theme.modeLabel("HALLOWEEN"));
      assertEquals("Auto", Theme.modeLabel(null));
   }

   @Test
   void refreshSwapsTheWholeColourSet() {
      FeatureConfig.INSTANCE.render.theme = Theme.CLASSIC;
      Theme.refresh();
      int pink = Theme.ACCENT;
      assertFalse(Theme.halloween());

      FeatureConfig.INSTANCE.render.theme = Theme.HALLOWEEN;
      Theme.refresh();
      assertTrue(Theme.halloween());
      assertNotEquals(pink, Theme.ACCENT);
      assertEquals(0xFFFF9A2E, Theme.ACCENT, "pumpkin orange");

      FeatureConfig.INSTANCE.render.theme = Theme.CLASSIC;
      Theme.refresh();
      assertEquals(pink, Theme.ACCENT);
   }
}
