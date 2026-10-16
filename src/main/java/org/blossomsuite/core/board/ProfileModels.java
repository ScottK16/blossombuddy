package org.blossomsuite.core.board;

import java.util.List;
import java.util.Map;

/** What the relay's profile endpoints send back (see relay/progress.js and leaderboard.js). */
public final class ProfileModels {
   private ProfileModels() {
   }

   public static final class Rank {
      public int rank;
      public int total;
      public double value;
   }

   public static final class Dungeons {
      public int done;
      public int failed;
   }

   public static final class Style {
      public String gradientId;
      public List<String> colors;
      public String symbolId;
      public String glyph;
   }

   public static final class GradientOption {
      public String id;
      public String label;
      public int level;
      public List<String> colors;
      public boolean custom;
      public boolean unlocked;
   }

   public static final class SymbolOption {
      public String id;
      public String label;
      public String glyph;
      public int level;
      public boolean unlocked;
   }

   public static final class Catalog {
      public List<GradientOption> gradients;
      public List<SymbolOption> symbols;
   }

   /** One player's level and look. {@code catalog} is only there for your own profile. */
   public static final class Profile {
      public String name;
      public String uuid;
      public long xp;
      public int level;
      public String title;
      public long xpIntoLevel;
      public long xpForNext;
      public Map<String, Rank> ranks;
      public long minutes;
      public Dungeons dungeons;
      public Style style;
      public Catalog catalog;

      public List<GradientOption> gradients() {
         return this.catalog == null || this.catalog.gradients == null ? List.of() : this.catalog.gradients;
      }

      public List<SymbolOption> symbols() {
         return this.catalog == null || this.catalog.symbols == null ? List.of() : this.catalog.symbols;
      }

      public double progress() {
         return this.xpForNext <= 0L ? 0.0 : Math.max(0.0, Math.min(1.0, (double)this.xpIntoLevel / this.xpForNext));
      }
   }
}
