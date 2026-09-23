package org.blossomsuite.core.cooldowns;

import org.blossomsuite.core.config.CooldownsConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.hud.ScreenNoticeOverlay;
import org.blossomsuite.core.util.TextUtil;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class CooldownNotifier {
   private static final int READY_NOTICE_RGB = 5635925;
   private static final long READY_NOTICE_MS = 1800L;
   public static final Set<String> relayReadyNotified = new HashSet<>();

   private CooldownNotifier() {
   }

   public static void tick(MinecraftClient client) {
      if (client.player != null) {
         long now = System.currentTimeMillis();

         for (Entry<CooldownRules.CooldownRule, Long> e : CooldownState.endsAtByRuleKey.entrySet()) {
            CooldownRules.CooldownRule rule = e.getKey();
            long endsAt = e.getValue() == null ? 0L : e.getValue();
            if (endsAt > 0L && now >= endsAt) {
               Long lastNotified = CooldownState.notifiedEndsAtByRuleKey.get(rule);
               if (lastNotified == null || lastNotified != endsAt) {
                  CooldownState.notifiedEndsAtByRuleKey.put(rule, endsAt);
                  long totalMs = CooldownState.totalMsByRuleKey.getOrDefault(rule, 0L);
                  if (shouldNotify(totalMs)) {
                     ItemStack stack = CooldownState.lastStackByRuleKey.get(rule);
                     playDoneSound(client, stack, rule);
                  }
               }
            }
         }
      }
   }

   private static boolean shouldNotify(long totalMs) {
      SuiteConfig cfg = SuiteConfig.INSTANCE;
      int th = cfg.CooldownsConfig.completeThresholdInSecs;
      return th <= 0 || totalMs >= th * 1000L;
   }

   private static void playDoneSound(MinecraftClient client, ItemStack stack, CooldownRules.CooldownRule rule) {
      SuiteConfig cfg = SuiteConfig.INSTANCE;
      if (stack != null && !stack.isEmpty()) {
         stack.getName().getString();
      } else {
         rule.id();
      }

      if (cfg.CooldownsConfig.completeSound) {
         CooldownJingle.enqueueReadyJingle(System.currentTimeMillis());
      }

      if (cfg.CooldownsConfig.completeMessage) {
         sendReadyMessage(client, stack, rule, false, "");
      }
   }

   private static void sendReadyMessage(MinecraftClient client, ItemStack stack, CooldownRules.CooldownRule rule, boolean isAlt, String altName) {
      SuiteConfig cfg = SuiteConfig.INSTANCE;
      CooldownsConfig.CompleteMessageLocation location = cfg.CooldownsConfig.completeMessageLocation;
      if (location == CooldownsConfig.CompleteMessageLocation.SCREEN) {
         showReadyNotice(stack, rule, isAlt, altName);
      } else {
         sendReadyChat(client, stack, rule, isAlt, altName);
      }
   }

   private static void showReadyNotice(ItemStack stack, CooldownRules.CooldownRule rule, boolean isAlt, String altName) {
      String itemName;
      if (stack != null && !stack.isEmpty()) {
         itemName = TextUtil.stripLegacySectionCodes(stack.getName()).getString();
      } else {
         itemName = rule == null ? "Cooldown" : rule.fallback();
      }

      String prefix = "";
      if (isAlt && altName != null && !altName.isBlank()) {
         prefix = "[" + altName + "] ";
      }

      ScreenNoticeOverlay.show(prefix + itemName + " is ready", stack == null ? ItemStack.EMPTY : stack, 5635925, 1800L);
   }

   private static void sendReadyChat(MinecraftClient client, ItemStack stack, CooldownRules.CooldownRule rule, boolean isAlt, String altName) {
      if (client.player != null) {
         Text itemName;
         if (stack != null && !stack.isEmpty()) {
            itemName = TextUtil.stripLegacySectionCodes(stack.getName()).copy();
         } else {
            itemName = Text.literal(rule.fallback());
         }

         itemName = itemName.copy().formatted(Formatting.AQUA);
         MutableText msg = Text.literal("");
         if (isAlt && altName != null && !altName.isBlank()) {
            msg = msg.append(Text.literal("[").formatted(Formatting.DARK_GRAY))
               .append(Text.literal(altName).formatted(Formatting.LIGHT_PURPLE))
               .append(Text.literal("] ").formatted(Formatting.DARK_GRAY));
         }

         msg = msg.append(itemName).append(Text.literal(" is ready").formatted(Formatting.GRAY));
         CooldownRuntime.sendChat(msg);
      }
   }

   private static void cleanup(long now) {
      Iterator<Entry<CooldownRules.CooldownRule, Long>> it = CooldownState.notifiedEndsAtByRuleKey.entrySet().iterator();

      while (it.hasNext()) {
         CooldownRules.CooldownRule rule = it.next().getKey();
         Long endsAt = CooldownState.endsAtByRuleKey.get(rule);
         if (endsAt == null || endsAt <= now) {
            it.remove();
         }
      }
   }

   public static void maybeNotifyRelayReady(String altName, String realm, String id, long prevEndsAt, long now) {
      MinecraftClient mc = MinecraftClient.getInstance();
      String selfName = mc.player == null ? null : mc.player.getName().getString();
      if (selfName == null || altName == null || !altName.equalsIgnoreCase(selfName)) {
         String key = altName + "|" + id + "|" + prevEndsAt;
         if (relayReadyNotified.add(key)) {
            CooldownRules.CooldownRule rule = CooldownRules.byId.get(id);
            long totalMs = 0L;
            ItemStack stack = ItemStack.EMPTY;
            if (rule != null) {
               ItemStack s = CooldownState.lastStackByRuleKey.get(rule);
               if (s != null) {
                  stack = s;
               }
            }

            SuiteConfig cfg = SuiteConfig.INSTANCE;
            if (cfg.CooldownsConfig.completeSound) {
               CooldownJingle.enqueueRelayReadyJingle(System.currentTimeMillis());
            }

            if (cfg.CooldownsConfig.completeMessage) {
               if (rule != null) {
                  sendReadyMessage(mc, stack, rule, true, altName);
               } else if (cfg.CooldownsConfig.completeMessageLocation == CooldownsConfig.CompleteMessageLocation.SCREEN) {
                  ScreenNoticeOverlay.show("[Alt] " + altName + ": " + id + " ready", 5635925, 1800L);
               } else if (mc.player != null) {
                  mc.player.sendMessage(Text.literal("[Alt] " + altName + ": " + id + " ready"), false);
               }
            }
         }
      }
   }
}
