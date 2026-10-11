package org.blossomsuite.core.board;

import java.util.List;

/** What the relay's leaderboard endpoints send back. */
public final class LeaderboardModels {
   private LeaderboardModels() {
   }

   /** Which board: what is ranked, where, and over what time. */
   public record Choice(String metric, String realm, String period) {
      public static final Choice DEFAULT = new Choice("money", "all", "all");
   }

   public static final class Entry {
      public int rank;
      public String name;
      public double value;
   }

   public static final class RealmInfo {
      public String key;
      public String label;
   }

   /** One board, from GET /v1/lb/top. */
   public static final class Board {
      public String metric;
      public String realm;
      public String period;
      public int total;
      public List<Entry> entries;
      public List<RealmInfo> realmsAvailable;

      public List<Entry> rows() {
         return this.entries == null ? List.of() : this.entries;
      }
   }

   /** Where the signed-in player stands on a board, from POST /v1/lb/me. rank is 0 when they are not on it. */
   public static final class Me {
      public int rank;
      public double value;
      public int total;
   }
}
