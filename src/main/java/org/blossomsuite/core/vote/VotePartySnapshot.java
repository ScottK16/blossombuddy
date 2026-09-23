package org.blossomsuite.core.vote;

import org.blossomsuite.core.services.VoteStateService;

public class VotePartySnapshot implements VoteStateService.Snapshot {
   private final String serverKey;
   private final String displayName;
   private int current;
   private int max;
   private boolean partyOngoing;
   private long seenAt;
   private boolean countdownActive;
   private Float countdownSecondsRemaining;
   private Long expectedTriggerAt;
   private long lastBossbarVoteSeenAt;
   private VoteObservationSource voteSource = VoteObservationSource.REMOTE;
   private int voteConfidence = 0;
   private long voteObservedAt;
   private long voteSessionGeneration = -1L;
   private VoteObservationSource bossbarSource = VoteObservationSource.REMOTE;
   private int bossbarConfidence = 0;
   private long bossbarObservedAt;
   private long bossbarSessionGeneration = -1L;

   public VotePartySnapshot(String serverKey, String displayName, int current, int max, boolean partyOngoing, long seenAt) {
      this.serverKey = normalize(serverKey);
      this.displayName = displayName;
      this.current = current;
      this.max = max;
      this.partyOngoing = partyOngoing;
      this.seenAt = seenAt;
      this.voteObservedAt = seenAt;
      this.bossbarObservedAt = seenAt;
   }

   public String getServerKey() {
      return this.serverKey;
   }

   @Override
   public String serverKey() {
      return this.getServerKey();
   }

   public String getDisplayName() {
      return this.displayName;
   }

   @Override
   public String displayName() {
      return this.getDisplayName();
   }

   public int getCurrent() {
      return this.current;
   }

   @Override
   public int current() {
      return this.getCurrent();
   }

   public int getMax() {
      return this.max;
   }

   @Override
   public int max() {
      return this.getMax();
   }

   public boolean isPartyOngoing() {
      return this.partyOngoing;
   }

   @Override
   public boolean partyOngoing() {
      return this.isPartyOngoing();
   }

   public long getSeenAt() {
      return this.seenAt;
   }

   @Override
   public long seenAt() {
      return this.getSeenAt();
   }

   public boolean isCountdownActive() {
      return this.countdownActive;
   }

   @Override
   public boolean countdownActive() {
      return this.isCountdownActive();
   }

   public Float getCountdownSecondsRemaining() {
      return this.countdownSecondsRemaining;
   }

   @Override
   public Float countdownSecondsRemaining() {
      return this.getCountdownSecondsRemaining();
   }

   public Long getExpectedTriggerAt() {
      return this.expectedTriggerAt;
   }

   @Override
   public Long expectedTriggerAt() {
      return this.getExpectedTriggerAt();
   }

   public long getLastBossbarVoteSeenAt() {
      return this.lastBossbarVoteSeenAt;
   }

   public void markBossbarVoteSeen(long now) {
      this.lastBossbarVoteSeenAt = now;
   }

   public void updateVote(int current, int max, long seenAt) {
      this.updateVote(current, max, seenAt, VoteObservationSource.SCOREBOARD, 80, VoteRuntime.sessionGeneration());
   }

   public boolean updateVote(int current, int max, long seenAt, VoteObservationSource source, int confidence, long sessionGeneration) {
      if (!this.shouldAcceptVoteObservation(seenAt, source, confidence, sessionGeneration)) {
         return false;
      }

      boolean changed = this.current != current || this.max != max;
      this.current = current;
      this.max = max;
      this.seenAt = seenAt;
      this.voteSource = source == null ? VoteObservationSource.SCOREBOARD : source;
      this.voteConfidence = confidence;
      this.voteObservedAt = seenAt;
      this.voteSessionGeneration = sessionGeneration;
      return changed;
   }

   public boolean updateVoteIfChanged(int current, int max, long seenAt) {
      return this.updateVoteIfChanged(current, max, seenAt, VoteObservationSource.SCOREBOARD, 80, VoteRuntime.sessionGeneration());
   }

   public boolean updateVoteIfChanged(int current, int max, long seenAt, VoteObservationSource source, int confidence, long sessionGeneration) {
      if (this.current == current && this.max == max && this.shouldAcceptVoteObservation(seenAt, source, confidence, sessionGeneration)) {
         this.updateVote(current, max, seenAt, source, confidence, sessionGeneration);
         return false;
      } else {
         return this.updateVote(current, max, seenAt, source, confidence, sessionGeneration);
      }
   }

   public boolean updateIfChanged(int current, int max, boolean partyOngoing, long seenAt) {
      boolean changed = this.updateVoteIfChanged(current, max, seenAt);
      return changed | this.setPartyOngoingIfChanged(partyOngoing, seenAt);
   }

   public boolean updateFromScoreboard(int current, int max, long seenAt, long sessionGeneration) {
      return this.updateVoteIfChanged(current, max, seenAt, VoteObservationSource.SCOREBOARD, 80, sessionGeneration);
   }

   public boolean applyRemoteState(
      Integer current, Integer max, Boolean partyOngoing, Long seenAt, Boolean countdownActive, Float countdownSecondsRemaining, Long expectedTriggerAt
   ) {
      boolean changed = false;
      long at = seenAt != null ? seenAt : this.seenAt;
      long remoteSession = VoteRuntime.sessionGeneration();
      if (current != null && max != null) {
         changed |= this.updateVoteIfChanged(current, max, at, VoteObservationSource.REMOTE, 20, remoteSession);
      } else if (seenAt != null && at >= this.seenAt) {
         this.seenAt = at;
      }

      if (partyOngoing != null) {
         changed |= this.setPartyOngoingIfChanged(partyOngoing, at, VoteObservationSource.REMOTE, 20, remoteSession);
      }

      boolean wantsCountdownActive = countdownActive != null ? countdownActive : this.countdownActive;
      if (!wantsCountdownActive) {
         if (this.countdownActive && this.shouldAcceptBossbarObservation(at, VoteObservationSource.REMOTE, 20, remoteSession)) {
            this.clearCountdown();
            this.seenAt = at;
            changed = true;
         }

         return changed;
      } else {
         Float secs = countdownSecondsRemaining;
         if (secs == null && expectedTriggerAt != null) {
            secs = Math.max(0.0F, (float)(expectedTriggerAt - at) / 1000.0F);
         }

         if (secs != null) {
            changed |= this.updateCountdownIfChanged(secs, at, VoteObservationSource.REMOTE, 20, remoteSession);
            if (expectedTriggerAt != null && (this.expectedTriggerAt == null || !this.expectedTriggerAt.equals(expectedTriggerAt))) {
               this.expectedTriggerAt = expectedTriggerAt;
               changed = true;
            }
         }

         return changed;
      }
   }

   public void setPartyOngoing(boolean partyOngoing, long seenAt) {
      this.setPartyOngoing(partyOngoing, seenAt, VoteObservationSource.BOSSBAR, 90, VoteRuntime.sessionGeneration());
   }

   public boolean setPartyOngoing(boolean partyOngoing, long seenAt, VoteObservationSource source, int confidence, long sessionGeneration) {
      if (!this.shouldAcceptBossbarObservation(seenAt, source, confidence, sessionGeneration)) {
         return false;
      }

      boolean wasOngoing = this.partyOngoing;
      boolean changed = wasOngoing != partyOngoing;
      this.partyOngoing = partyOngoing;
      this.seenAt = seenAt;
      this.bossbarSource = source == null ? VoteObservationSource.BOSSBAR : source;
      this.bossbarConfidence = confidence;
      this.bossbarObservedAt = seenAt;
      this.bossbarSessionGeneration = sessionGeneration;
      if (partyOngoing) {
         this.clearCountdown();
      }

      if (!wasOngoing && partyOngoing) {
         VoteNotifier.onOngoingStarted(this);
      }

      return changed;
   }

   public boolean setPartyOngoingIfChanged(boolean partyOngoing, long seenAt) {
      return this.setPartyOngoingIfChanged(partyOngoing, seenAt, VoteObservationSource.BOSSBAR, 90, VoteRuntime.sessionGeneration());
   }

   public boolean setPartyOngoingIfChanged(boolean partyOngoing, long seenAt, VoteObservationSource source, int confidence, long sessionGeneration) {
      if (this.partyOngoing == partyOngoing && this.shouldAcceptBossbarObservation(seenAt, source, confidence, sessionGeneration)) {
         this.setPartyOngoing(partyOngoing, seenAt, source, confidence, sessionGeneration);
         return false;
      } else {
         return this.setPartyOngoing(partyOngoing, seenAt, source, confidence, sessionGeneration);
      }
   }

   public void updateCountdown(float secondsRemaining, long seenAt) {
      this.updateCountdown(secondsRemaining, seenAt, VoteObservationSource.BOSSBAR, 90, VoteRuntime.sessionGeneration());
   }

   public boolean updateCountdown(float secondsRemaining, long seenAt, VoteObservationSource source, int confidence, long sessionGeneration) {
      if (!this.shouldAcceptBossbarObservation(seenAt, source, confidence, sessionGeneration)) {
         return false;
      }

      boolean wasCountdownActive = this.countdownActive;
      Float previousSeconds = this.countdownSecondsRemaining;
      this.countdownActive = true;
      this.countdownSecondsRemaining = secondsRemaining;
      this.expectedTriggerAt = seenAt + (long)(secondsRemaining * 1000.0F);
      this.seenAt = seenAt;
      this.bossbarSource = source == null ? VoteObservationSource.BOSSBAR : source;
      this.bossbarConfidence = confidence;
      this.bossbarObservedAt = seenAt;
      this.bossbarSessionGeneration = sessionGeneration;
      if (!wasCountdownActive && secondsRemaining > 0.05F) {
         VoteNotifier.onCountdownStarted(this);
      }

      return !wasCountdownActive || previousSeconds == null || Math.abs(previousSeconds - secondsRemaining) >= 0.05F;
   }

   public boolean updateCountdownIfChanged(float secondsRemaining, long seenAt) {
      return this.updateCountdownIfChanged(secondsRemaining, seenAt, VoteObservationSource.BOSSBAR, 90, VoteRuntime.sessionGeneration());
   }

   public boolean updateCountdownIfChanged(float secondsRemaining, long seenAt, VoteObservationSource source, int confidence, long sessionGeneration) {
      if (this.countdownActive
         && this.countdownSecondsRemaining != null
         && Math.abs(this.countdownSecondsRemaining - secondsRemaining) < 0.05F
         && this.shouldAcceptBossbarObservation(seenAt, source, confidence, sessionGeneration)) {
         this.updateCountdown(secondsRemaining, seenAt, source, confidence, sessionGeneration);
         return false;
      } else {
         return this.updateCountdown(secondsRemaining, seenAt, source, confidence, sessionGeneration);
      }
   }

   public void clearCountdown() {
      boolean wasCountdownActive = this.countdownActive;
      this.countdownActive = false;
      this.countdownSecondsRemaining = null;
      this.expectedTriggerAt = null;
      if (wasCountdownActive) {
         VoteNotifier.clearCountdown(this.displayName);
      }
   }

   @Override
   public boolean expireBossbarStateIfStale(long now, long thresholdMs) {
      if (this.lastBossbarVoteSeenAt <= 0L) {
         return false;
      }

      if (now - this.lastBossbarVoteSeenAt <= thresholdMs) {
         return false;
      }

      boolean changed = false;
      if (this.countdownActive) {
         this.clearCountdown();
         changed = true;
      }

      if (this.partyOngoing) {
         this.partyOngoing = false;
         changed = true;
      }

      if (changed) {
         this.seenAt = now;
      }

      return changed;
   }

   public boolean isStale(long now, long thresholdMs) {
      return now - this.seenAt > thresholdMs;
   }

   private static String normalize(String server) {
      return server == null ? "" : server.trim().toLowerCase();
   }

   private boolean shouldAcceptVoteObservation(long observedAt, VoteObservationSource source, int confidence, long sessionGeneration) {
      if (source == VoteObservationSource.REMOTE
         && this.voteSource != VoteObservationSource.REMOTE
         && this.voteObservedAt > 0L
         && observedAt <= this.voteObservedAt) {
         return false;
      } else {
         return sessionGeneration >= 0L && this.voteSessionGeneration >= 0L && sessionGeneration < this.voteSessionGeneration
            ? false
            : observedAt >= this.voteObservedAt || confidence > this.voteConfidence;
      }
   }

   private boolean shouldAcceptBossbarObservation(long observedAt, VoteObservationSource source, int confidence, long sessionGeneration) {
      if (source == VoteObservationSource.REMOTE
         && this.bossbarSource != VoteObservationSource.REMOTE
         && this.bossbarObservedAt > 0L
         && observedAt <= this.bossbarObservedAt) {
         return false;
      } else {
         return sessionGeneration >= 0L && this.bossbarSessionGeneration >= 0L && sessionGeneration < this.bossbarSessionGeneration
            ? false
            : observedAt >= this.bossbarObservedAt || confidence > this.bossbarConfidence;
      }
   }

   @Override
   public String toString() {
      return "VotePartySnapshot{server='"
         + this.displayName
         + "', current="
         + this.current
         + ", max="
         + this.max
         + ", ongoing="
         + this.partyOngoing
         + ", countdownActive="
         + this.countdownActive
         + ", countdownSecondsRemaining="
         + this.countdownSecondsRemaining
         + ", expectedTriggerAt="
         + this.expectedTriggerAt
         + ", seenAt="
         + this.seenAt
         + "}";
   }
}
