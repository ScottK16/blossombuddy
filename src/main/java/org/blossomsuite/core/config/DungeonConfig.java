package org.blossomsuite.core.config;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class DungeonConfig {
   public final Map<String, Long> cooldownEndsByServer = new HashMap<>();
   public boolean enabled = true;
   public boolean showHud = true;
   public boolean showHeader = true;
   public DungeonConfig.HudServerMode hudServerMode = DungeonConfig.HudServerMode.CURRENT_SERVER;
   public boolean reportRunsInChat = true;
   public boolean reportRunsHttp = true;
   public int cooldownHours = 8;
   public long cooldownTotalMs = 28800000L;
   public float positionX = -1.0F;
   public float positionY = -1.0F;
   public float scale = 1.0F;
   public float backgroundOpacity = 0.2F;

   public void toggleEnabled() {
      this.enabled = !this.enabled;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleShowHud() {
      this.showHud = !this.showHud;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleShowHeader() {
      this.showHeader = !this.showHeader;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleReportRunsInChat() {
      this.reportRunsInChat = !this.reportRunsInChat;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleReportRunsHttp() {
      this.reportRunsHttp = !this.reportRunsHttp;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void setCooldownHours(int hours) {
      this.cooldownHours = Math.max(1, Math.min(72, hours));
      this.cooldownTotalMs = this.cooldownHours * 60L * 60L * 1000L;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void cycleHudServerMode() {
      this.hudServerMode = this.hudServerMode == DungeonConfig.HudServerMode.CURRENT_SERVER
         ? DungeonConfig.HudServerMode.ALL_SERVERS
         : DungeonConfig.HudServerMode.CURRENT_SERVER;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void setPosition(float positionX, float positionY) {
      this.positionX = positionX;
      this.positionY = positionY;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void setScale(float scale) {
      this.scale = Math.max(0.1F, Math.min(2.0F, scale));
      SuiteConfig.INSTANCE.markDirty();
   }

   public long cooldownEndsAt(String server) {
      return this.cooldownEndsByServer.getOrDefault(serverKey(server), 0L);
   }

   public void setCooldownEndsAt(String server, long endsAtMs) {
      String key = serverKey(server);
      if (endsAtMs <= 0L) {
         this.cooldownEndsByServer.remove(key);
      } else {
         this.cooldownEndsByServer.put(key, endsAtMs);
      }

      SuiteConfig.INSTANCE.markDirty();
   }

   public void resetCooldown(String server) {
      this.cooldownEndsByServer.remove(serverKey(server));
      SuiteConfig.INSTANCE.markDirty();
   }

   public static String serverKey(String server) {
      String key = server == null ? "" : server.trim();
      return (key.isBlank() ? "unknown" : key).toLowerCase(Locale.ROOT);
   }

   public enum HudServerMode {
      CURRENT_SERVER,
      ALL_SERVERS;
   }
}
