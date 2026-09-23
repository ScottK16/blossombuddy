package org.blossomsuite.core.cooldowns;

import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.services.models.RelayModels;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.item.ItemStack;

public final class CooldownState {
   public static final List<CooldownState.PendingTrigger> pending = new ArrayList<>();
   public static final Map<CooldownRules.CooldownRule, ItemStack> lastStackByRuleKey = new HashMap<>();
   public static final Map<CooldownRules.CooldownRule, String> lastItemKeyByRuleKey = new HashMap<>();
   public static final Set<String> relayReadyNotified = new HashSet<>();
   public static final Map<CooldownRules.CooldownRule, Long> endsAtByRuleKey = new HashMap<>();
   public static final Map<CooldownRules.CooldownRule, Long> totalMsByRuleKey = new HashMap<>();
   public static final Map<CooldownRules.CooldownRule, Long> notifiedEndsAtByRuleKey = new HashMap<>();
   private static volatile boolean relayDirty = true;
   private static long nextRelayPublishAtMs = 0L;
   private static final long RELAY_MIN_INTERVAL_MS = 3000L;
   public static final Map<String, Map<String, CooldownState.TrackedAltCooldown>> trackedByAlt = new HashMap<>();
   public static final long TRACKED_TTL_MS = 300000L;
   public static long lastAttackAttemptMs = 0L;
   public static long lastUseAttemptMs = 0L;
   public static boolean wasAttackDown = false;
   public static boolean wasUseDown = false;
   public static boolean wasSneakDown = false;
   public static long lastInventoryUseTriggerAtMs = 0L;
   public static float lastEffectiveHp = -1.0F;
   public static long lastDamageTakenTriggerAtMs = 0L;
   public static final List<CooldownState.ActionReadyEntry> actionReadyCache = new ArrayList<>();

   private CooldownState() {
   }

   public static void pruneEnded(long now) {
      int before = endsAtByRuleKey.size();
      endsAtByRuleKey.entrySet().removeIf(e -> e.getValue() != null && e.getValue() <= now);
      int after = endsAtByRuleKey.size();
      totalMsByRuleKey.keySet().removeIf(k -> !endsAtByRuleKey.containsKey(k));
      notifiedEndsAtByRuleKey.keySet().removeIf(k -> !endsAtByRuleKey.containsKey(k));
      if (SuiteConfig.INSTANCE.RelayConfig.publishCooldowns) {
         if (after != before) {
            markRelayDirty();
         }

         publishRelayIfDirty(now);
      }
   }

   private static void publishRelayIfDirty(long now) {
      if (relayDirty) {
         if (now >= nextRelayPublishAtMs) {
            List<RelayModels.RelayCooldownEntry> snapshot = CooldownRules.buildCooldownSnapshot();
            if (CooldownRuntime.publishCooldownSnapshot(snapshot)) {
               relayDirty = false;
               nextRelayPublishAtMs = now + 3000L;
            }
         }
      }
   }

   private static void markRelayDirty() {
      relayDirty = true;
   }

   public static final class ActionReadyEntry {
      public ItemStack stack;
      public CooldownRules.CooldownRule rule;
      public long endsAt;
   }

   public record PendingTrigger(CooldownRules.CooldownRule rule, long nowMs, ItemStack stack) {
   }

   public static final class TrackedAltCooldown {
      public long endsAtMs;
      public long lastSeenMs;
      public long lastChangedMs;
      public String realmName;
   }
}
