package org.blossomsuite.core.jobs;

public enum JobsAutoSegmentMode {
   TIME_ACTIVE,
   SEGMENT_MONEY,
   SEGMENT_EXP;

   public JobsAutoSegmentMode next() {
      JobsAutoSegmentMode[] all = values();
      return all[(this.ordinal() + 1) % all.length];
   }
}
