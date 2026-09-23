package org.blossomsuite.core.config;

import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.SuiteServer;
import org.blossomsuite.core.jobs.JobsActionBarMode;
import org.blossomsuite.core.jobs.JobsAutoSegmentMode;
import org.blossomsuite.core.jobs.JobsMode;
import org.blossomsuite.core.jobs.overflow.OverflowDisplayMode;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class JobsConfig {
   public boolean showHud = true;
   public boolean showStopwatch = true;
   public boolean showSegmentLines = true;
   public boolean showSessionLines = true;
   public boolean showInChat = false;
   public boolean capture = true;
   public JobsActionBarMode actionBarMode = JobsActionBarMode.HIDE;
   public boolean showLifetime = true;
   public boolean showPauseIconNearCrosshair = false;
   public float positionX = 0.5F;
   public float positionY = 0.5F;
   public float scale = 1.0F;
   public float backgroundOpacity = 0.0F;
   public JobsMode mode = JobsMode.MONEY;
   public boolean sessionRollover = false;
   public boolean sessionRolloverHasData = false;
   public long sessionRolloverActiveMs = 0L;
   public double sessionRolloverMoney = 0.0;
   public double sessionRolloverExp = 0.0;
   public boolean autoSegment = false;
   public int autoSegmentInSecs = 3600;
   public JobsAutoSegmentMode autoSegmentMode = JobsAutoSegmentMode.TIME_ACTIVE;
   public double autoSegmentMoneyThreshold = 100000.0;
   public double autoSegmentExpThreshold = 0.0;
   public boolean pauseReminder = false;
   public boolean pauseAutoUnpause = false;
   public int pauseReminderThresholdSecs = 300;
   public double pauseAutoUnpauseMoneyThreshold = 0.0;
   public static final int PAUSE_REMINDER_THRESHOLD_MIN_SECS = 60;
   public static final int PAUSE_REMINDER_THRESHOLD_MAX_SECS = 3600;
   public double cherryLifetime = 0.0;
   public double spiritLifetime = 0.0;
   public double lotusLifetime = 0.0;
   public double tulipLifetime = 0.0;
   public boolean overflowEnabled = true;
   public OverflowDisplayMode overflowDisplay = OverflowDisplayMode.XP;
   public int overflowMaxLevel = 200;
   public boolean overflowLevelUpMessage = true;
   private final Map<String, Double> lifetimeByServer = new LinkedHashMap<>();

   public void toggleShowHud() {
      this.showHud = !this.showHud;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleShowStopwatch() {
      this.showStopwatch = !this.showStopwatch;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleShowSegmentLines() {
      this.showSegmentLines = !this.showSegmentLines;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleShowSessionLines() {
      this.showSessionLines = !this.showSessionLines;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleShowInChat() {
      this.showInChat = !this.showInChat;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleOverflowEnabled() {
      this.overflowEnabled = !this.overflowEnabled;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void cycleOverflowDisplay() {
      this.overflowDisplay = this.overflowDisplay.next();
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleOverflowLevelUpMessage() {
      this.overflowLevelUpMessage = !this.overflowLevelUpMessage;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleCapture() {
      this.capture = !this.capture;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void cycleActionBarMode() {
      this.actionBarMode = switch (this.actionBarMode) {
         case HIDE -> JobsActionBarMode.RUNNING_TOTAL;
         case RUNNING_TOTAL -> JobsActionBarMode.SESSION_TOTAL;
         case SESSION_TOTAL -> JobsActionBarMode.SEGMENT_TOTAL;
         case SEGMENT_TOTAL -> JobsActionBarMode.ORIGINAL;
         case ORIGINAL -> JobsActionBarMode.HIDE;
         default -> JobsActionBarMode.HIDE;
      };
      SuiteConfig.INSTANCE.markDirty();
   }

   public void setPosition(float positionX, float positionY) {
      this.positionX = positionX;
      this.positionY = positionY;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void setScale(float scale) {
      this.scale = scale;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void setMode(JobsMode mode) {
      this.mode = mode;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleSessionRollover() {
      this.sessionRollover = !this.sessionRollover;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void setSessionRolloverData(long sessionRolloverActiveMs, double sessionRolloverMoney, double sessionRolloverExp) {
      long nextActiveMs = Math.max(0L, sessionRolloverActiveMs);
      double nextMoney = sessionRolloverMoney;
      double nextExp = sessionRolloverExp;
      if (this.sessionRolloverHasData) {
         nextActiveMs = Math.max(this.sessionRolloverActiveMs, nextActiveMs);
         nextMoney = Math.max(this.sessionRolloverMoney, nextMoney);
         nextExp = Math.max(this.sessionRolloverExp, nextExp);
      }

      this.sessionRolloverHasData = true;
      this.sessionRolloverActiveMs = nextActiveMs;
      this.sessionRolloverMoney = nextMoney;
      this.sessionRolloverExp = nextExp;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void clearSessionRolloverData() {
      this.sessionRolloverHasData = false;
      this.sessionRolloverActiveMs = 0L;
      this.sessionRolloverMoney = 0.0;
      this.sessionRolloverExp = 0.0;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleAutoSegment() {
      this.autoSegment = !this.autoSegment;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void setJobsAutoSegmentInSecs(int autoSegmentInSecs) {
      this.autoSegmentInSecs = autoSegmentInSecs;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void cycleAutoSegmentMode() {
      if (this.autoSegmentMode == null) {
         this.autoSegmentMode = JobsAutoSegmentMode.TIME_ACTIVE;
      }

      this.autoSegmentMode = this.autoSegmentMode.next();
      SuiteConfig.INSTANCE.markDirty();
   }

   public void setAutoSegmentMoneyThreshold(double amount) {
      this.autoSegmentMoneyThreshold = Math.max(0.0, amount);
      SuiteConfig.INSTANCE.markDirty();
   }

   public void setAutoSegmentExpThreshold(double amount) {
      this.autoSegmentExpThreshold = Math.max(0.0, amount);
      SuiteConfig.INSTANCE.markDirty();
   }

   public void togglePauseReminder() {
      this.pauseReminder = !this.pauseReminder;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void togglePauseAutoUnpause() {
      this.pauseAutoUnpause = !this.pauseAutoUnpause;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void setPauseReminderThresholdSecs(int secs) {
      if (secs < 60) {
         secs = 60;
      }

      if (secs > 3600) {
         secs = 3600;
      }

      this.pauseReminderThresholdSecs = secs / 60 * 60;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void setPauseAutoUnpauseMoneyThreshold(double amount) {
      this.pauseAutoUnpauseMoneyThreshold = Math.max(0.0, amount);
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleShowLifetime() {
      this.showLifetime = !this.showLifetime;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void toggleShowPauseIconNearCrosshair() {
      this.showPauseIconNearCrosshair = !this.showPauseIconNearCrosshair;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void addToCherryLifetime(double val) {
      this.cherryLifetime += val;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void addToSpiritLifetime(double val) {
      this.spiritLifetime += val;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void addToLotusLifetime(double val) {
      this.lotusLifetime += val;
      SuiteConfig.INSTANCE.markDirty();
   }

   public void addToTulipLifetime(double val) {
      this.tulipLifetime += val;
      SuiteConfig.INSTANCE.markDirty();
   }

   public double lifetimeForServer(String serverName) {
      String key = normalizeServerKey(serverName);
      if (key.isBlank()) {
         return 0.0;
      }

      Double generic = this.lifetimeByServer.get(key);
      if (generic != null) {
         return generic;
      }

      return switch (key) {
         case "cherry" -> this.cherryLifetime;
         case "spirit" -> this.spiritLifetime;
         case "lotus" -> this.lotusLifetime;
         case "tulip" -> this.tulipLifetime;
         default -> 0.0;
      };
   }

   public void addToLifetimeForServer(String serverName, double value) {
      String key = normalizeServerKey(serverName);
      if (!key.isBlank()) {
         this.setLifetimeForServer(key, this.lifetimeForServer(key) + value);
         SuiteConfig.INSTANCE.markDirty();
      }
   }

   public void setLifetimeForServer(String serverName, double value) {
      this.setLifetimeForServer(serverName, value, true);
   }

   public void loadLifetimeForServer(String serverName, double value) {
      this.setLifetimeForServer(serverName, value, false);
   }

   private void setLifetimeForServer(String serverName, double value, boolean markDirty) {
      String key = normalizeServerKey(serverName);
      if (!key.isBlank()) {
         this.lifetimeByServer.put(key, value);
         switch (key) {
            case "cherry":
               this.cherryLifetime = value;
               break;
            case "spirit":
               this.spiritLifetime = value;
               break;
            case "lotus":
               this.lotusLifetime = value;
               break;
            case "tulip":
               this.tulipLifetime = value;
         }

         if (markDirty) {
            SuiteConfig.INSTANCE.markDirty();
         }
      }
   }

   public Map<String, Double> lifetimeByServer() {
      LinkedHashMap<String, Double> out = new LinkedHashMap<>();

      for (SuiteServer server : SuiteRuntime.profile().servers()) {
         out.put(server.key(), this.lifetimeForServer(server.key()));
      }

      this.lifetimeByServer.forEach(out::putIfAbsent);
      return out;
   }

   private static String normalizeServerKey(String serverName) {
      return serverName == null ? "" : SuiteServer.normalizeKey(serverName.trim().toLowerCase(Locale.ROOT));
   }
}
