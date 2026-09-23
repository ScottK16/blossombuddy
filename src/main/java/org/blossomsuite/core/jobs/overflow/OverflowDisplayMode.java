package org.blossomsuite.core.jobs.overflow;

public enum OverflowDisplayMode {
   /** Boss bar title shows the raw overflow XP total. */
   XP,
   /** Boss bar title and bar show cosmetic levels beyond the job's max level. */
   LEVELS,
   /** Overflow XP is still tracked but the boss bar is left untouched. */
   OFF;

   public OverflowDisplayMode next() {
      OverflowDisplayMode[] all = values();
      return all[(this.ordinal() + 1) % all.length];
   }
}
