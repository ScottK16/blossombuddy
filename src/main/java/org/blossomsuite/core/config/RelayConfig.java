package org.blossomsuite.core.config;

public final class RelayConfig {
   public String linkId = "";
   public boolean publishCooldowns = false;
   public boolean subscribeCooldowns = false;

   public void setLinkId(String val) {
      this.linkId = val;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleSubscribeCooldowns() {
      this.subscribeCooldowns = !this.subscribeCooldowns;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void togglePublishCooldowns() {
      this.publishCooldowns = !this.publishCooldowns;
      SuiteConfig.INSTANCE.markDirty();
   }
}
