package org.blossomsuite.core.progress;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.blossomsuite.core.config.FeatureConfig;

/**
 * What the mod counts toward your BlossomBuddy level: time spent on a realm, dungeon runs, and a few "first time" things. The counters
 * live on your computer and go to the leaderboard with your totals (if you are on it), which turns them into XP.
 */
public final class Progress {
   /** Must match FIRSTS in relay/progress.js: the relay ignores anything else. */
   public static final List<String> FIRSTS = List.of("emote", "find", "presence", "music", "session", "notify", "dungeon", "leaderboard");

   private Progress() {
   }

   private static FeatureConfig.Progress data() {
      return FeatureConfig.INSTANCE.progress;
   }

   /** Time spent on a realm (milliseconds). */
   public static void addPlayMs(long ms) {
      if (ms > 0L) {
         data().playMs += ms;
         FeatureConfig.markDirty();
      }
   }

   public static void dungeon(boolean completed) {
      if (completed) {
         data().dungeonsDone++;
      } else {
         data().dungeonsFailed++;
      }

      FeatureConfig.markDirty();
   }

   /** Notes that something was done for the first time (nothing happens the second time). */
   public static void first(String id) {
      if (!FIRSTS.contains(id)) {
         return;
      }

      List<String> seen = data().firsts;
      if (!seen.contains(id)) {
         seen.add(id);
         FeatureConfig.markDirty();
      }
   }

   /** What goes to the relay with the totals: {minutes, done, failed, firsts}. */
   public static Map<String, Object> snapshot() {
      FeatureConfig.Progress p = data();
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("minutes", p.playMs / 60_000L);
      out.put("done", p.dungeonsDone);
      out.put("failed", p.dungeonsFailed);
      out.put("firsts", new ArrayList<>(p.firsts));
      return out;
   }
}
