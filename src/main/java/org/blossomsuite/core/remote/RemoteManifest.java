package org.blossomsuite.core.remote;

public final class RemoteManifest {
   public RemoteManifest.Cooldowns cooldowns;

   public static final class Cooldowns {
      public String sha256;
      public String url;
      public String version;
   }
}
