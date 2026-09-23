package org.blossomsuite.core.config;

import java.util.ArrayList;
import java.util.List;

public final class RentalsConfig {
   public final List<RentalsConfig.RentalEntry> entries = new ArrayList<>();
   public boolean showHud = true;
   public float positionX = -1.0F;
   public float positionY = -1.0F;
   public float scale = 1.0F;
   public float backgroundOpacity = 0.2F;

   public RentalsConfig.RentalEntry addRental(String itemName, String location, int minutes) {
      RentalsConfig.RentalEntry entry = new RentalsConfig.RentalEntry();
      entry.itemName = clean(itemName, "Rental");
      entry.location = clean(location, "Unknown");
      entry.totalMinutes = Math.max(1, Math.min(10080, minutes));
      entry.endAtEpochMs = System.currentTimeMillis() + entry.totalMinutes * 60000L;
      entry.pausedRemainingMs = 0L;
      entry.paused = false;
      this.entries.add(entry);
      SuiteConfig.INSTANCE.markDirty();
      return entry;
   }

   public void pauseAll() {
      long now = System.currentTimeMillis();

      for (RentalsConfig.RentalEntry entry : this.entries) {
         entry.pause(now);
      }

      SuiteConfig.INSTANCE.markDirty();
   }

   public void resumeAll() {
      long now = System.currentTimeMillis();

      for (RentalsConfig.RentalEntry entry : this.entries) {
         entry.resume(now);
      }

      SuiteConfig.INSTANCE.markDirty();
   }

   public boolean hasActiveRentals() {
      long now = System.currentTimeMillis();

      for (RentalsConfig.RentalEntry entry : this.entries) {
         if (!entry.finished(now)) {
            return true;
         }
      }

      return false;
   }

   public boolean hasPausedRentals() {
      long now = System.currentTimeMillis();

      for (RentalsConfig.RentalEntry entry : this.entries) {
         if (entry.paused && !entry.finished(now)) {
            return true;
         }
      }

      return false;
   }

   public boolean hasRunningRentals() {
      long now = System.currentTimeMillis();

      for (RentalsConfig.RentalEntry entry : this.entries) {
         if (!entry.paused && !entry.finished(now)) {
            return true;
         }
      }

      return false;
   }

   public int clearFinished() {
      long now = System.currentTimeMillis();
      int before = this.entries.size();
      this.entries.removeIf(entry -> entry.remainingMs(now) <= 0L);
      int cleared = before - this.entries.size();
      if (cleared > 0) {
         SuiteConfig.INSTANCE.markDirty();
      }

      return cleared;
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

   private static String clean(String raw, String fallback) {
      String s = raw == null ? "" : raw.trim();
      if (s.isEmpty()) {
         return fallback;
      } else {
         return s.length() > 64 ? s.substring(0, 64) : s;
      }
   }

   public static final class RentalEntry {
      public String itemName = "Rental";
      public String location = "Unknown";
      public int totalMinutes = 60;
      public long endAtEpochMs = 0L;
      public long pausedRemainingMs = 0L;
      public boolean paused = false;

      public long remainingMs(long now) {
         return this.paused ? Math.max(0L, this.pausedRemainingMs) : Math.max(0L, this.endAtEpochMs - now);
      }

      public boolean finished(long now) {
         return this.remainingMs(now) <= 0L;
      }

      public void pause(long now) {
         if (!this.paused && !this.finished(now)) {
            this.pausedRemainingMs = this.remainingMs(now);
            this.paused = true;
            SuiteConfig.INSTANCE.markDirty();
         }
      }

      public void resume(long now) {
         if (this.paused && this.pausedRemainingMs > 0L) {
            this.endAtEpochMs = now + this.pausedRemainingMs;
            this.pausedRemainingMs = 0L;
            this.paused = false;
            SuiteConfig.INSTANCE.markDirty();
         }
      }
   }
}
