package org.blossomsuite.core.jobs;

import java.util.function.Supplier;

public final class JobsRuntime {
   private static Supplier<String> realmSupplier = () -> "";

   private JobsRuntime() {
   }

   public static void setRealmSupplier(Supplier<String> supplier) {
      realmSupplier = supplier != null ? supplier : () -> "";
   }

   public static String currentRealm() {
      String realm = realmSupplier.get();
      return realm == null ? "" : realm;
   }
}
