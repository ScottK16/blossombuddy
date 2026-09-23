package org.blossomsuite.core.config;

import org.blossomsuite.core.SuiteRuntime;

public final class RemoteConfig {
   public boolean enabled = true;
   public String cooldownsSha256 = "";
   public String cooldownVersion = "0";
   public long lastCheckMs = 0L;

   public static String baseUrl() {
      return SuiteRuntime.profile().apiBaseUrl();
   }

   public static String ingestKey() {
      return SuiteRuntime.profile().ingestKey();
   }

   public void setCooldownsSha256(String cooldownsSha256) {
      this.cooldownsSha256 = safe(cooldownsSha256);
      SuiteConfig.INSTANCE.markDirty();
   }

   public void setLastCheckMs(int lastCheckMs) {
      this.lastCheckMs = lastCheckMs;
   }

   public void setCooldownVersion(String cooldownVersion) {
      this.cooldownVersion = cooldownVersion;
      SuiteConfig.INSTANCE.markDirty();
   }

   private static String safe(String value) {
      return value == null ? "" : value;
   }
}
