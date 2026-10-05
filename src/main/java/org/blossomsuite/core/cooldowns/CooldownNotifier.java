package org.blossomsuite.core.cooldowns;

import org.blossomsuite.core.config.CooldownsConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.hud.ScreenNoticeOverlay;
import org.blossomsuite.core.util.TextUtil;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
public final class CooldownNotifier {
   private static final int READY_NOTICE_RGB = 5635925;
   private static final long READY_NOTICE_MS = 1800L;
   public static final Set<String> relayReadyNotified = new HashSet<>();

   private CooldownNotifier() {
   }

   public static void tick(Minecraft client) {
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

   private static void playDoneSound(Minecraft client, ItemStack stack, CooldownRules.CooldownRule rule) {
      SuiteConfig cfg = SuiteConfig.INSTANCE;
      if (cfg.CooldownsConfig.completeSound) {
         String override = rule.id() == null ? null : CooldownSoundStore.get(rule.id());
         boolean playedOverride = override != null && playCustomReadySound(client, override, cfg.CooldownsConfig.completeSoundVolume);
         if (!playedOverride) {
            CooldownJingle.enqueueReadyJingle(System.currentTimeMillis());
         }
      }

      if (cfg.CooldownsConfig.completeMessage) {
         sendReadyMessage(client, stack, rule, false, "");
      }
   }

   /** True if a per-item sound override ({@code /buddy cooldown sound}) played instead of the default ready jingle. */
   private static boolean playCustomReadySound(Minecraft client, String soundId, float volume) {
      if (client.level == null || client.player == null) {
         return false;
      }

      Identifier id;
      try {
         id = Identifier.parse(soundId);
      } catch (Exception e) {
         return false;
      }

      SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getValue(id);
      if (sound == null || sound == SoundEvents.EMPTY) {
         return false;
      }

      client.level.playSound(client.player, client.player.getX(), client.player.getY(), client.player.getZ(), sound, SoundSource.MASTER, volume, 1.0F);
      return true;
   }

   private static void sendReadyMessage(Minecraft client, ItemStack stack, CooldownRules.CooldownRule rule, boolean isAlt, String altName) {
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
         itemName = TextUtil.stripLegacySectionCodes(stack.getHoverName()).getString();
      } else {
         itemName = rule == null ? "Cooldown" : rule.fallback();
      }

      String prefix = "";
      if (isAlt && altName != null && !altName.isBlank()) {
         prefix = "[" + altName + "] ";
      }

      ScreenNoticeOverlay.show(prefix + itemName + " is ready", stack == null ? ItemStack.EMPTY : stack, 5635925, 1800L);
   }

   private static void sendReadyChat(Minecraft client, ItemStack stack, CooldownRules.CooldownRule rule, boolean isAlt, String altName) {
      if (client.player != null) {
         Component itemName;
         if (stack != null && !stack.isEmpty()) {
            itemName = TextUtil.stripLegacySectionCodes(stack.getHoverName()).copy();
         } else {
            itemName = Component.literal(rule.fallback());
         }

         itemName = itemName.copy().withStyle(ChatFormatting.AQUA);
         MutableComponent msg = Component.literal("");
         if (isAlt && altName != null && !altName.isBlank()) {
            msg = msg.append(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY))
               .append(Component.literal(altName).withStyle(ChatFormatting.LIGHT_PURPLE))
               .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY));
         }

         msg = msg.append(itemName).append(Component.literal(" is ready").withStyle(ChatFormatting.GRAY));
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
      Minecraft mc = Minecraft.getInstance();
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
                  mc.player.sendSystemMessage(Component.literal("[Alt] " + altName + ": " + id + " ready"));
               }
            }
         }
      }
   }
}
