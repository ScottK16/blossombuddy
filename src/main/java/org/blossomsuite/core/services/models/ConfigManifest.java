package org.blossomsuite.core.services.models;

import java.util.Map;

public final class ConfigManifest {
   public String updatedAt;
   public Map<String, ConfigManifest.ResourceEntry> resources;

   public static final class ResourceEntry {
      public String version;
      public String url;
      public String sha256;
      public String updatedAt;
      public Boolean missing;
      public String error;
   }
}
