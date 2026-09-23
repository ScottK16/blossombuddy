package org.blossomsuite.core.util;

public final class RateColors {
   private RateColors() {
   }

   public static int rateColor(boolean captureOn, boolean paused, double perHr) {
      if (!captureOn) {
         return -9408400;
      } else if (paused) {
         return -6645094;
      } else if (perHr >= 200000.0) {
         return -11672879;
      } else if (perHr >= 150000.0) {
         return -6591489;
      } else {
         return perHr >= 100000.0 ? -10901 : -38037;
      }
   }
}
