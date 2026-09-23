package org.blossomsuite.core.jobs;

public final class JobsChattextSetup {
   public static long sentAtMs = 0L;
   public static boolean doneThisRealm = false;
   public static String realmName = "";

   private JobsChattextSetup() {
   }

   public static void resetForRealm() {
      sentAtMs = 0L;
      doneThisRealm = false;
      realmName = "";
   }
}
