package org.blossomsuite.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

class SettingsSearchTest {
   private static final SettingsSearch.Leaf JOBS_OVERFLOW = new SettingsSearch.Leaf(null, null, null, null, "Jobs > Overflow", 0);

   private static HoverLabelWidget label(int y, String text) {
      return new HoverLabelWidget(16, y, 220, 12, Text.literal(text), null);
   }

   private static StyledButton button(int y, String text) {
      return StyledButton.of(Text.literal(text), b -> {}).dimensions(300, y, 80, 20).build();
   }

   /** A toggle row the way the tabs build them: label at y+6, 20px button at y. */
   private static List<ClickableWidget> toggleRow(int y, String name, String state) {
      List<ClickableWidget> row = new ArrayList<>();
      row.add(label(y + 6, name));
      row.add(button(y, state));
      return row;
   }

   private static List<SettingsSearch.Row> rowsOf(List<ClickableWidget> widgets) {
      return SettingsSearch.group(widgets);
   }

   @Test
   void groupsALabelWithTheControlBesideIt() {
      List<ClickableWidget> ws = new ArrayList<>();
      ws.addAll(toggleRow(100, "Track overflow XP", "ON"));
      ws.addAll(toggleRow(124, "Level-up message", "OFF"));

      List<SettingsSearch.Row> rows = rowsOf(ws);
      assertEquals(2, rows.size());
      assertEquals(2, rows.get(0).widgets().size(), "label + its button");
      assertTrue(rows.get(0).text().contains("track overflow xp"));
      assertTrue(rows.get(0).text().contains("on"));
      assertTrue(rows.get(1).text().contains("level-up message"));
      assertFalse(rows.get(1).text().contains("track"), "rows must not bleed into each other");
   }

   @Test
   void aSliderBelowItsLabelBelongsToThatLabel() {
      List<ClickableWidget> ws = new ArrayList<>();
      ws.add(label(100, "Volume"));
      ws.add(button(114, "Volume: 80%")); // slider sits ~14px under its label
      ws.addAll(toggleRow(148, "Pitch lock", "ON"));

      List<SettingsSearch.Row> rows = rowsOf(ws);
      assertEquals(2, rows.size());
      assertEquals(2, rows.get(0).widgets().size());
      assertTrue(rows.get(0).text().contains("80%"));
   }

   @Test
   void everyWordOfTheQueryMustMatch() {
      List<ClickableWidget> ws = new ArrayList<>();
      ws.addAll(toggleRow(100, "Track overflow XP", "ON"));
      ws.addAll(toggleRow(124, "Show stopwatch", "ON"));
      List<SettingsSearch.Row> rows = rowsOf(ws);

      assertTrue(SettingsSearch.matches(rows.get(0), JOBS_OVERFLOW, SettingsSearch.tokens("overflow")));
      assertTrue(SettingsSearch.matches(rows.get(0), JOBS_OVERFLOW, SettingsSearch.tokens("  TRACK   xp ")), "case and spacing don't matter");
      assertTrue(SettingsSearch.matches(rows.get(1), JOBS_OVERFLOW, SettingsSearch.tokens("overflow stopwatch")), "one word from the row, one from its section");
      assertFalse(SettingsSearch.matches(rows.get(0), JOBS_OVERFLOW, SettingsSearch.tokens("stopwatch")));
      assertFalse(SettingsSearch.matches(rows.get(0), JOBS_OVERFLOW, SettingsSearch.tokens("overflow zzz")));
   }

   @Test
   void theSectionNameCountsAsMatchingText() {
      List<SettingsSearch.Row> rows = rowsOf(toggleRow(100, "Show stopwatch", "ON"));
      assertTrue(SettingsSearch.matches(rows.get(0), JOBS_OVERFLOW, SettingsSearch.tokens("jobs")), "everything under Jobs");
      assertTrue(SettingsSearch.matches(rows.get(0), JOBS_OVERFLOW, SettingsSearch.tokens("jobs stopwatch")));
      assertFalse(SettingsSearch.matches(rows.get(0), JOBS_OVERFLOW, SettingsSearch.tokens("cooldown")));
   }

   @Test
   void aBlankQueryMatchesNothing() {
      List<SettingsSearch.Row> rows = rowsOf(toggleRow(100, "Show stopwatch", "ON"));
      assertEquals(0, SettingsSearch.tokens("   ").length);
      assertFalse(SettingsSearch.matches(rows.get(0), JOBS_OVERFLOW, SettingsSearch.tokens("")));
   }

   @Test
   void controlsWithNoLabelAreStillSearchableByTheirOwnText() {
      List<ClickableWidget> ws = new ArrayList<>();
      ws.add(button(300, "Toggle HUD"));
      List<SettingsSearch.Row> rows = rowsOf(ws);
      assertEquals(1, rows.size());
      assertTrue(SettingsSearch.matches(rows.get(0), JOBS_OVERFLOW, SettingsSearch.tokens("hud")));
   }

   @Test
   void rowsReportTheirVerticalExtentSoTheyCanBeRelaidOut() {
      List<SettingsSearch.Row> rows = rowsOf(toggleRow(100, "Track overflow XP", "ON"));
      assertEquals(100, rows.get(0).top());
      assertEquals(120, rows.get(0).bottom());
      assertEquals(20, rows.get(0).height());
   }
}
