package org.blossomsuite.core.jobs;

import org.blossomsuite.core.util.TextUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class JobsRapidOverlay {
   private static long resetWindowMs = 6000L;
   private static long animateWindowMs = 5000L;
   private static double money = 0.0;
   private static double xp = 0.0;
   private static long lastAtMs = 0L;
   private static long lastBatchAtMs = 0L;
   private static JobsRapidOverlay.Kind kind = JobsRapidOverlay.Kind.RUNNING_TOTAL;
   private static long animStartAtMs = 0L;
   private static double animStartMoney = 0.0;
   private static double animStartXp = 0.0;
   private static double animTargetMoney = 0.0;
   private static double animTargetXp = 0.0;
   private static boolean internalOverlayWrite = false;

   private JobsRapidOverlay() {
   }

   public static long getResetWindowMs() {
      return resetWindowMs;
   }

   public static void setResetWindowMs(long ms) {
      if (ms < 250L) {
         ms = 250L;
      }

      if (ms > 60000L) {
         ms = 60000L;
      }

      resetWindowMs = ms;
   }

   public static long getAnimateWindowMs() {
      return animateWindowMs;
   }

   public static void setAnimateWindowMs(long ms) {
      if (ms < 250L) {
         ms = 250L;
      }

      if (ms > 60000L) {
         ms = 60000L;
      }

      animateWindowMs = ms;
   }

   public static double getMoney() {
      return money;
   }

   public static double getXp() {
      return xp;
   }

   public static long getLastAtMs() {
      return lastAtMs;
   }

   public static void reset() {
      money = 0.0;
      xp = 0.0;
      lastAtMs = 0L;
      lastBatchAtMs = 0L;
      kind = JobsRapidOverlay.Kind.RUNNING_TOTAL;
      animStartAtMs = 0L;
      animStartMoney = 0.0;
      animStartXp = 0.0;
      animTargetMoney = 0.0;
      animTargetXp = 0.0;
   }

   public static boolean isInternalOverlayWrite() {
      return internalOverlayWrite;
   }

   public static void runInternalOverlayWrite(Runnable r) {
      internalOverlayWrite = true;

      try {
         r.run();
      } finally {
         internalOverlayWrite = false;
      }
   }

   public static Text addAndFormat(double addMoney, double addXp, long nowMs) {
      boolean resetBatch = lastBatchAtMs <= 0L || nowMs - lastBatchAtMs > resetWindowMs;
      if (resetBatch) {
         money = 0.0;
         xp = 0.0;
      }

      money += addMoney;
      xp += addXp;
      lastBatchAtMs = nowMs;
      if (resetBatch) {
         retargetFromZero(JobsRapidOverlay.Kind.RUNNING_TOTAL, money, xp, nowMs);
      } else {
         retarget(JobsRapidOverlay.Kind.RUNNING_TOTAL, money, xp, nowMs);
      }

      return formatNow(nowMs);
   }

   public static boolean isActive(long nowMs) {
      return lastAtMs <= 0L ? false : nowMs - lastAtMs <= resetWindowMs;
   }

   public static Text formatCurrent() {
      return formatNow(System.currentTimeMillis());
   }

   public static void setLastText(Text msg, long nowMs) {
      kind = JobsRapidOverlay.Kind.RUNNING_TOTAL;
      animStartAtMs = nowMs;
      animStartMoney = 0.0;
      animStartXp = 0.0;
      animTargetMoney = 0.0;
      animTargetXp = 0.0;
      lastAtMs = nowMs;
   }

   public static void retarget(JobsRapidOverlay.Kind kind, double targetMoney, double targetXp, long nowMs) {
      double curMoney = currentValue(animStartMoney, animTargetMoney, animStartAtMs, nowMs);
      double curXp = currentValue(animStartXp, animTargetXp, animStartAtMs, nowMs);
      if (targetMoney < curMoney) {
         curMoney = targetMoney;
      }

      if (targetXp < curXp) {
         curXp = targetXp;
      }

      JobsRapidOverlay.kind = kind;
      animStartMoney = curMoney;
      animStartXp = curXp;
      animTargetMoney = targetMoney;
      animTargetXp = targetXp;
      animStartAtMs = nowMs;
      lastAtMs = nowMs;
   }

   public static void retargetFromZero(JobsRapidOverlay.Kind kind, double targetMoney, double targetXp, long nowMs) {
      JobsRapidOverlay.kind = kind;
      animStartMoney = 0.0;
      animStartXp = 0.0;
      animTargetMoney = targetMoney;
      animTargetXp = targetXp;
      animStartAtMs = nowMs;
      lastAtMs = nowMs;
   }

   public static Text formatNow(long nowMs) {
      double curMoney = currentValue(animStartMoney, animTargetMoney, animStartAtMs, nowMs);
      double curXp = currentValue(animStartXp, animTargetXp, animStartAtMs, nowMs);

      return switch (kind) {
         case RUNNING_TOTAL -> Text.literal("+" + TextUtil.fmtMoney(curMoney) + "$")
            .formatted(Formatting.YELLOW)
            .append(Text.literal("  "))
            .append(Text.literal("+" + TextUtil.fmtExp(curXp) + "XP").formatted(Formatting.GRAY));
         case SESSION_TOTAL -> Text.literal("Sess ")
            .formatted(Formatting.AQUA)
            .append(Text.literal("$" + TextUtil.fmtMoney(curMoney)).formatted(Formatting.YELLOW))
            .append(Text.literal("  "))
            .append(Text.literal(TextUtil.fmtExp(curXp) + "XP").formatted(Formatting.GRAY));
         case SEGMENT_TOTAL -> Text.literal("Seg ")
            .formatted(Formatting.AQUA)
            .append(Text.literal("$" + TextUtil.fmtMoney(curMoney)).formatted(Formatting.YELLOW))
            .append(Text.literal("  "))
            .append(Text.literal(TextUtil.fmtExp(curXp) + "XP").formatted(Formatting.GRAY));
      };
   }

   private static double currentValue(double start, double target, long startAtMs, long nowMs) {
      if (startAtMs <= 0L) {
         return target;
      }

      long dt = nowMs - startAtMs;
      if (dt <= 0L) {
         return start;
      }

      long dur = animateWindowMs;
      if (dur <= 0L) {
         return target;
      }

      double t = Math.min(1.0, (double)dt / dur);
      double eased = 1.0 - Math.pow(1.0 - t, 3.0);
      return start + (target - start) * eased;
   }

   public static void keepAlive(MinecraftClient client) {
      if (client != null && client.inGameHud != null) {
         long now = System.currentTimeMillis();
         Text msg = formatNow(now);
         runInternalOverlayWrite(() -> client.inGameHud.setOverlayMessage(msg, false));
      }
   }

   public enum Kind {
      RUNNING_TOTAL,
      SESSION_TOTAL,
      SEGMENT_TOTAL;
   }
}
