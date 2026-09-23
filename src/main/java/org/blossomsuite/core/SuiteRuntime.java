package org.blossomsuite.core;

public final class SuiteRuntime {
   private static final boolean DUNGEON_RUNTIME_ON_HOLD = true;
   private static volatile SuiteProfile profile;

   private SuiteRuntime() {
   }

   public static void initialize(SuiteProfile suiteProfile) {
      if (suiteProfile == null) {
         throw new IllegalArgumentException("suiteProfile must not be null");
      }

      profile = suiteProfile;
   }

   public static SuiteProfile profile() {
      SuiteProfile current = profile;
      if (current == null) {
         throw new IllegalStateException("SuiteRuntime has not been initialized");
      } else {
         return current;
      }
   }

   public static boolean isEnabled(SuiteFeature feature) {
      return feature == SuiteFeature.DUNGEONS ? false : profile().isEnabled(feature);
   }
}
