package org.blossomsuite.core.chat;

import java.util.function.Supplier;

public final class ChatRuntime {
   private static Supplier<String> realmSupplier = () -> "";

   private ChatRuntime() {
   }

   public static void setRealmSupplier(Supplier<String> supplier) {
      realmSupplier = supplier != null ? supplier : () -> "";
   }

   public static String currentRealm() {
      String realm = realmSupplier.get();
      return realm == null ? "" : realm;
   }

}
