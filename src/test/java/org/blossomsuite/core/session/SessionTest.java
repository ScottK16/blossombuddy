package org.blossomsuite.core.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionTest {
   private static final ZoneId UTC = ZoneId.of("UTC");

   static final class FakeEnv implements SessionTracker.Env {
      long now = 1_000_000L;
      Map<String, double[]> totals = new TreeMap<>(Map.of("cherry", new double[]{1000.0, 50.0}));
      String realm = "cherry";
      int[] dungeons = {0, 0};
      final List<SessionRecord> saved = new ArrayList<>();

      @Override public long now() { return this.now; }
      @Override public Map<String, double[]> totals() { return this.totals; }
      @Override public String realm() { return this.realm; }
      @Override public int[] dungeons(long sinceMs) { return this.dungeons; }
      @Override public void save(SessionRecord record) { this.saved.add(record); }
      @Override public List<SessionRecord> history() { return this.saved; }
      @Override public void clearHistory() { this.saved.clear(); }
   }

   private FakeEnv env;
   private SessionTracker tracker;

   @BeforeEach
   void setUp() {
      this.env = new FakeEnv();
      this.tracker = new SessionTracker(this.env);
   }

   private void play(long ms) {
      for (long t = 0; t < ms; t += 1000L) {
         this.env.now += 1000L;
         this.tracker.tick();
      }
   }

   @Test
   void earningsAreWhatYourTotalsGainedSinceYouJoined() {
      this.tracker.begin();
      this.play(60_000L);
      this.env.totals.put("cherry", new double[]{1750.0, 80.0});
      this.env.totals.put("spirit", new double[]{300.0, 0.0}); // a realm with no earlier total
      SessionRecord live = this.tracker.live();
      assertEquals(1050.0, live.money(), 0.001);
      assertEquals(30.0, live.overflowXp(), 0.001);
      assertEquals(60_000L, live.durationMs());
   }

   @Test
   void timeIsKeptPerRealm() {
      this.tracker.begin();
      this.play(30_000L);
      this.env.realm = "spirit";
      this.play(45_000L);
      SessionRecord live = this.tracker.live();
      assertEquals(30_000L, live.realmMs().get("cherry"));
      assertEquals(45_000L, live.realmMs().get("spirit"));
   }

   @Test
   void aFreezeIsNotCountedAsTimeOnARealm() {
      this.tracker.begin();
      this.play(5_000L);
      this.env.now += 600_000L; // the laptop slept
      this.tracker.tick();
      assertEquals(5_000L, this.tracker.live().realmMs().get("cherry"));
   }

   @Test
   void leavingSavesASessionThatWasWorthKeeping() {
      this.tracker.begin();
      this.play(3 * 60_000L);
      SessionRecord saved = this.tracker.end();
      assertNotNull(saved);
      assertEquals(1, this.env.saved.size());
      assertFalse(this.tracker.running());
      assertNull(this.tracker.live());
   }

   @Test
   void aQuickVisitThatEarnedNothingIsNotKept() {
      this.tracker.begin();
      this.play(20_000L);
      assertNull(this.tracker.end());
      assertTrue(this.env.saved.isEmpty());
   }

   @Test
   void aQuickVisitThatEarnedSomethingIsKept() {
      this.tracker.begin();
      this.play(20_000L);
      this.env.totals.put("cherry", new double[]{1500.0, 50.0});
      assertNotNull(this.tracker.end());
   }

   @Test
   void timeOnAServerThatIsNotARealmIsNotKept() {
      this.env.realm = "";
      this.tracker.begin();
      this.play(10 * 60_000L);
      assertNull(this.tracker.end());
   }

   @Test
   void aResetTotalNeverShowsNegativeEarnings() {
      this.tracker.begin();
      this.play(60_000L);
      this.env.totals.put("cherry", new double[]{0.0, 0.0});
      assertEquals(0.0, this.tracker.live().money());
   }

   @Test
   void aBusyReadingKeepsTheLastGoodNumbers() {
      this.tracker.begin();
      this.play(60_000L);
      this.env.totals.put("cherry", new double[]{1500.0, 50.0});
      assertEquals(500.0, this.tracker.live().money(), 0.001);
      this.env.totals = new TreeMap<>(); // the totals were being updated just then
      assertEquals(500.0, this.tracker.live().money(), 0.001);
   }

   @Test
   void dungeonRunsDuringTheSessionAreCounted() {
      this.tracker.begin();
      this.play(60_000L);
      this.env.dungeons = new int[]{2, 1};
      SessionRecord live = this.tracker.live();
      assertEquals(2, live.dungeonRuns());
      assertEquals(1, live.dungeonDone());
   }

   // ------------------------------------------------------------------ adding sessions up

   private static SessionRecord rec(String day, double money, long minutes) {
      long end = LocalDate.parse(day).atTime(20, 0).atZone(UTC).toInstant().toEpochMilli();
      return new SessionRecord(end - minutes * 60_000L, end, money, 0.0, 0, 0, new LinkedHashMap<>());
   }

   @Test
   void todayAndWeekAddUpTheRightSessions() {
      long now = LocalDate.parse("2026-10-16").atTime(22, 0).atZone(UTC).toInstant().toEpochMilli();
      List<SessionRecord> all = List.of(rec("2026-10-16", 100, 30), rec("2026-10-16", 50, 30), rec("2026-10-12", 400, 60), rec("2026-10-01", 9000, 60));
      SessionStats.Totals today = SessionStats.since(all, SessionStats.startOfDay(now, UTC));
      assertEquals(2, today.sessions());
      assertEquals(150.0, today.money(), 0.001);
      assertEquals(60 * 60_000L, today.timeMs());
      assertEquals(150.0, today.moneyPerHour(), 0.001);

      SessionStats.Totals week = SessionStats.since(all, SessionStats.startOfDay(now, UTC) - 6 * 86_400_000L);
      assertEquals(550.0, week.money(), 0.001);
      assertEquals(9550.0, SessionStats.since(all, 0L).money(), 0.001);
   }

   @Test
   void theSevenDayChartHasADayForEveryDayEvenQuietOnes() {
      long now = LocalDate.parse("2026-10-16").atTime(22, 0).atZone(UTC).toInstant().toEpochMilli();
      List<SessionStats.Day> days = SessionStats.lastDays(List.of(rec("2026-10-16", 100, 30), rec("2026-10-14", 40, 10)), now, UTC, 7);
      assertEquals(7, days.size());
      assertEquals(LocalDate.parse("2026-10-10"), days.get(0).date());
      assertEquals(LocalDate.parse("2026-10-16"), days.get(6).date());
      assertEquals(100.0, days.get(6).money());
      assertEquals(0.0, days.get(5).money());
      assertEquals(40.0, days.get(4).money());
   }

   @Test
   void perHourIsZeroForVeryShortSessionsInsteadOfAbsurd() {
      SessionStats.Totals t = new SessionStats.Totals(1, 5_000L, 1000.0, 0.0, 0, 0);
      assertEquals(0.0, t.moneyPerHour());
   }

   @Test
   void durationsAreReadable() {
      assertEquals("35s", SessionStats.duration(35_000L));
      assertEquals("42m", SessionStats.duration(42 * 60_000L));
      assertEquals("1h 23m", SessionStats.duration(83 * 60_000L));
   }

   // ------------------------------------------------------------------ the saved file

   @Test
   void sessionsAreSavedAndReadBackAndOldOnesDropped(@TempDir Path dir) throws Exception {
      SessionStore store = new SessionStore(dir.resolve("sessions.jsonl"));
      long now = 400L * 86_400_000L;
      Map<String, Long> realms = new LinkedHashMap<>();
      realms.put("cherry", 120_000L);
      store.append(new SessionRecord(now - 3_600_000L, now - 1_800_000L, 123.5, 7.0, 2, 1, realms));
      store.append(new SessionRecord(now - 200L * 86_400_000L, now - 200L * 86_400_000L + 1000L, 1.0, 0.0, 0, 0, new LinkedHashMap<>()));
      Files.writeString(dir.resolve("sessions.jsonl"), "not json\n{\"start\":5}\n", java.nio.file.StandardOpenOption.APPEND);

      List<SessionRecord> back = store.load(now);
      assertEquals(1, back.size(), "the old session and the junk lines are gone");
      assertEquals(123.5, back.get(0).money(), 0.001);
      assertEquals(2, back.get(0).dungeonRuns());
      assertEquals(120_000L, back.get(0).realmMs().get("cherry"));
      assertEquals(1, Files.readAllLines(dir.resolve("sessions.jsonl")).size(), "the file was tidied too");

      store.clear();
      assertTrue(store.load(now).isEmpty());
   }
}
