package org.blossomsuite.core.jobs;

public final class JobsTracker {
   public boolean paused = false;
   private boolean autoPausedForInactiveWorld = false;
   private boolean captureEnabled = true;
   private double sessionMoney = 0.0;
   private double sessionExp = 0.0;
   private double segmentMoney = 0.0;
   private double segmentExp = 0.0;
   private long lastTickMs = System.currentTimeMillis();
   public long segmentActiveMs = 0L;
   public long sessionActiveMs = 0L;
   private long sessionStartedMs = System.currentTimeMillis();
   private long segmentStartedMs = System.currentTimeMillis();
   private Long pauseStartedMs = null;
   private long pausedMsTotal = 0L;
   private long segmentPausedMsTotal = 0L;
   private double pausedMoneySincePause = 0.0;

   private long getNow() {
      return System.currentTimeMillis() / 1000L * 1000L;
   }

   public void togglePause() {
      if (!this.paused) {
         this.pauseManual();
      } else {
         this.resumeManual();
      }
   }

   public void onJobEvent(long now, double money, double exp, String text) {
      this.sessionMoney += money;
      this.sessionExp += exp;
      this.segmentMoney += money;
      this.segmentExp += exp;
   }

   public void resume() {
      this.resumeManual();
   }

   public void pause() {
      this.pauseManual();
   }

   public void pauseForInactiveWorld() {
      if (!this.paused) {
         this.startPause(this.getNow(), true);
      }
   }

   public void resumeFromInactiveWorld() {
      if (this.paused && this.autoPausedForInactiveWorld) {
         this.finishPause(this.getNow());
      }
   }

   public long currentPauseDurationMs(long nowMs) {
      return this.paused && this.pauseStartedMs != null ? Math.max(0L, nowMs - this.pauseStartedMs) : 0L;
   }

   public boolean isAutoPausedForInactiveWorld() {
      return this.paused && this.autoPausedForInactiveWorld;
   }

   public double currentPausedMoney() {
      return this.paused ? this.pausedMoneySincePause : 0.0;
   }

   private void pauseManual() {
      if (this.paused) {
         this.autoPausedForInactiveWorld = false;
      } else {
         this.startPause(this.getNow(), false);
      }
   }

   private void resumeManual() {
      if (this.paused) {
         this.finishPause(this.getNow());
      }
   }

   private void startPause(long now, boolean autoPaused) {
      this.paused = true;
      this.autoPausedForInactiveWorld = autoPaused;
      this.pauseStartedMs = now;
      this.pausedMoneySincePause = 0.0;
   }

   private void finishPause(long now) {
      if (this.pauseStartedMs != null) {
         long pausedFor = now - this.pauseStartedMs;
         this.pausedMsTotal += pausedFor;
         this.segmentPausedMsTotal += pausedFor;
      }

      this.paused = false;
      this.autoPausedForInactiveWorld = false;
      this.pauseStartedMs = null;
      this.pausedMoneySincePause = 0.0;
      this.lastTickMs = System.currentTimeMillis();
   }

   public void tick() {
      long now = System.currentTimeMillis();
      long dt = now - this.lastTickMs;
      this.lastTickMs = now;
      if (dt < 0L) {
         dt = 0L;
      }

      if (dt > 250L) {
         dt = 250L;
      }

      if (this.captureEnabled && !this.paused) {
         this.segmentActiveMs += dt;
         this.sessionActiveMs += dt;
      }
   }

   public void resetSession() {
      this.sessionMoney = 0.0;
      this.sessionExp = 0.0;
      this.sessionStartedMs = System.currentTimeMillis();
      this.pausedMsTotal = 0L;
      this.segmentActiveMs = 0L;
      this.sessionActiveMs = 0L;
      this.paused = false;
      this.autoPausedForInactiveWorld = false;
      this.pauseStartedMs = null;
      this.pausedMoneySincePause = 0.0;
      this.resetSegmentInternal(false);
   }

   public JobsTracker.SegmentSnapshot resetSegmentAndSnapshot() {
      return this.resetSegmentInternal(true);
   }

   private JobsTracker.SegmentSnapshot resetSegmentInternal(boolean snapshot) {
      long now = System.currentTimeMillis();
      long segmentElapsed = now - this.segmentStartedMs;
      long segmentActive = Math.max(0L, segmentElapsed - this.segmentPausedMsTotal);
      double hours = segmentActive / 3600000.0;
      double moneyPerHr = hours > 3.0E-4 ? this.segmentMoney / hours : 0.0;
      double expPerHr = hours > 3.0E-4 ? this.segmentExp / hours : 0.0;
      JobsTracker.SegmentSnapshot snap = null;
      if (snapshot) {
         snap = new JobsTracker.SegmentSnapshot(this.segmentStartedMs, now, segmentActive, this.segmentMoney, this.segmentExp, moneyPerHr, expPerHr);
      }

      this.segmentActiveMs = 0L;
      this.segmentMoney = 0.0;
      this.segmentExp = 0.0;
      this.segmentStartedMs = now;
      this.segmentPausedMsTotal = 0L;
      return snap;
   }

   public JobsTracker.SegmentSnapshot tryAutoSegment(long nowMs, int thresholdSec) {
      return this.tryAutoSegment(nowMs, JobsAutoSegmentMode.TIME_ACTIVE, thresholdSec, 0.0, 0.0);
   }

   public JobsTracker.SegmentSnapshot tryAutoSegment(long nowMs, JobsAutoSegmentMode mode, int thresholdSec, double moneyThreshold, double expThreshold) {
      if (mode == null) {
         mode = JobsAutoSegmentMode.TIME_ACTIVE;
      }

      if (mode == JobsAutoSegmentMode.SEGMENT_MONEY) {
         double target = Math.max(0.0, moneyThreshold);
         if (target <= 0.0) {
            return null;
         } else {
            return this.segmentMoney < target ? null : this.resetSegmentAndSnapshot();
         }
      } else if (mode == JobsAutoSegmentMode.SEGMENT_EXP) {
         double target = Math.max(0.0, expThreshold);
         if (target <= 0.0) {
            return null;
         } else {
            return this.segmentExp < target ? null : this.resetSegmentAndSnapshot();
         }
      } else {
         if (thresholdSec <= 0) {
            thresholdSec = 3600;
         }

         long currentPauseMs = 0L;
         if (this.paused && this.pauseStartedMs != null) {
            currentPauseMs = Math.max(0L, nowMs - this.pauseStartedMs);
         }

         long hourElapsed = nowMs - this.segmentStartedMs;
         long hourPaused = this.segmentPausedMsTotal + currentPauseMs;
         long hourActive = Math.max(0L, hourElapsed - hourPaused);
         long thresholdMs = thresholdSec * 1000L;
         return hourActive < thresholdMs ? null : this.resetSegmentAndSnapshot();
      }
   }

   public void onReward(double money, double exp) {
      if (this.paused) {
         if (money > 0.0) {
            this.pausedMoneySincePause += money;
         }
      } else {
         this.sessionMoney += money;
         this.sessionExp += exp;
         this.segmentMoney += money;
         this.segmentExp += exp;
      }
   }

   public JobsTracker.LiveStats getLiveStats() {
      long now = System.currentTimeMillis();
      long nowMsAligned = now / 1000L * 1000L;
      long currentPauseMs = 0L;
      if (this.paused && this.pauseStartedMs != null) {
         currentPauseMs = nowMsAligned - this.pauseStartedMs;
         if (currentPauseMs < 0L) {
            currentPauseMs = 0L;
         }
      }

      long sessElapsed = nowMsAligned - this.sessionStartedMs;
      long sessPaused = this.pausedMsTotal + currentPauseMs;
      long sessActive = Math.max(0L, sessElapsed - sessPaused);
      long segmentElapsed = nowMsAligned - this.segmentStartedMs;
      long segmentPaused = this.segmentPausedMsTotal + currentPauseMs;
      long segmentActive = Math.max(0L, segmentElapsed - segmentPaused);
      double sessHr = sessActive / 3600000.0;
      double segmentHr = segmentActive / 3600000.0;
      double sessionExpPerHr = sessHr > 3.0E-4 ? this.sessionExp / sessHr : 0.0;
      double segmentExpPerHr = segmentHr > 3.0E-4 ? this.segmentExp / segmentHr : 0.0;
      double sessionMoneyPerHr = sessHr > 3.0E-4 ? this.sessionMoney / sessHr : 0.0;
      double segmentMoneyPerHr = segmentHr > 3.0E-4 ? this.segmentMoney / segmentHr : 0.0;
      long sessActiveDisp = sessActive / 1000L * 1000L;
      long segmentActiveDisp = segmentActive / 1000L * 1000L;
      return new JobsTracker.LiveStats(
         this.captureEnabled,
         this.paused,
         this.sessionMoney,
         this.sessionExp,
         sessionMoneyPerHr,
         sessionExpPerHr,
         this.segmentMoney,
         this.segmentExp,
         segmentMoneyPerHr,
         segmentExpPerHr,
         segmentActiveDisp,
         sessActiveDisp
      );
   }

   public JobsTracker.SessionSnapshot snapshotSession(long nowMs) {
      return new JobsTracker.SessionSnapshot(this.currentSessionActiveMs(nowMs), this.sessionMoney, this.sessionExp);
   }

   private long currentSessionActiveMs(long nowMs) {
      long currentPauseMs = 0L;
      if (this.paused && this.pauseStartedMs != null) {
         currentPauseMs = Math.max(0L, nowMs - this.pauseStartedMs);
      }

      long sessElapsed = nowMs - this.sessionStartedMs;
      long sessPaused = this.pausedMsTotal + currentPauseMs;
      return Math.max(0L, sessElapsed - sessPaused);
   }

   private long currentSegmentActiveMs(long nowMs) {
      long currentPauseMs = 0L;
      if (this.paused && this.pauseStartedMs != null) {
         currentPauseMs = Math.max(0L, nowMs - this.pauseStartedMs);
      }

      long segmentElapsed = nowMs - this.segmentStartedMs;
      long segmentPaused = this.segmentPausedMsTotal + currentPauseMs;
      return Math.max(0L, segmentElapsed - segmentPaused);
   }

   public void restoreSessionPaused(long nowMs, long activeMs, double money, double exp) {
      JobsTracker.SessionSnapshot current = this.snapshotSession(nowMs);
      long act = Math.max(current.activeMs(), Math.max(0L, activeMs));
      long segmentAct = this.currentSegmentActiveMs(nowMs);
      this.sessionMoney = Math.max(current.money(), money);
      this.sessionExp = Math.max(current.exp(), exp);
      this.sessionStartedMs = nowMs - act;
      this.paused = true;
      this.autoPausedForInactiveWorld = false;
      this.pauseStartedMs = nowMs;
      this.pausedMoneySincePause = 0.0;
      this.pausedMsTotal = 0L;
      this.segmentStartedMs = nowMs - segmentAct;
      this.segmentPausedMsTotal = 0L;
      this.lastTickMs = nowMs;
   }

   public record LiveStats(
      boolean captureEnabled,
      boolean paused,
      double sessionMoney,
      double sessionExp,
      double sessionMoneyPerHr,
      double sessionExpPerHr,
      double segmentMoney,
      double segmentExp,
      double segmentMoneyPerHr,
      double segmentExpPerHr,
      long segmentActiveMs,
      long sessionActiveMs
   ) {
   }

   public record SegmentSnapshot(long startedAtMs, long endedAtMs, long activeMs, double totalMoney, double totalExp, double moneyPerHr, double expPerHr) {
   }

   public record SessionSnapshot(long activeMs, double money, double exp) {
   }
}
