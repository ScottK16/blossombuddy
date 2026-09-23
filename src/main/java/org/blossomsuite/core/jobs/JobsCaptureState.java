package org.blossomsuite.core.jobs;

public final class JobsCaptureState {
   public static boolean sawBatchedThisSession = false;
   public static boolean promptedThisJoin = false;
   public static long joinMs = 0L;

   private JobsCaptureState() {
   }

   public static void resetForJoin(long nowMs) {
      sawBatchedThisSession = false;
      promptedThisJoin = false;
      joinMs = nowMs;
   }

   public static void markSeen() {
      sawBatchedThisSession = true;
   }
}
