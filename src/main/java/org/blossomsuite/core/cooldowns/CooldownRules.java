package org.blossomsuite.core.cooldowns;

import org.blossomsuite.core.config.FeatureConfig;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.fabricmc.loader.api.FabricLoader;
import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.util.SuiteLog;
import org.blossomsuite.core.config.CooldownsConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.hud.CooldownsHud;
import org.blossomsuite.core.qol.autoswap.AutoSwapBlockMatcher;
import org.blossomsuite.core.services.RelayService;
import org.blossomsuite.core.services.models.RelayModels;
import org.blossomsuite.core.util.SuiteItemIdUtil;
import org.blossomsuite.core.util.TextUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

public final class CooldownRules {
   private static final int MAX_LOCAL_ROWS = 18;
   private static final EnumMap<CooldownRules.Trigger, List<CooldownRules.CooldownRule>> SLOT_RULES_BY_TRIGGER = new EnumMap<>(CooldownRules.Trigger.class);
   public static final Map<String, CooldownRules.ActiveView> activeByItemId = new HashMap<>();
   public static final String[] HOTBAR_IDS = new String[9];
   private static final Map<CooldownRules.Trigger, Map<String, CooldownRules.CooldownRule>> BY_ID = new EnumMap<>(CooldownRules.Trigger.class);
   private static final Map<CooldownRules.Trigger, List<CooldownRules.CooldownRule>> BY_FALLBACK = new EnumMap<>(CooldownRules.Trigger.class);
   public static final Map<String, CooldownRules.CooldownRule> byId = new HashMap<>();
   public static final Map<String, CooldownRules.CooldownRule> byFallback = new HashMap<>();
   private static final long ACTION_READY_REBUILD_INTERVAL_MS = 250L;
   private static long lastActionReadyRebuildMs = 0L;
   private static Path FILE;

   private CooldownRules() {
   }

   public static List<CooldownRules.CooldownRule> getSlotRules(CooldownRules.Trigger trigger) {
      if (trigger == null) {
         return List.of();
      }

      synchronized (CooldownRules.class) {
         List<CooldownRules.CooldownRule> list = SLOT_RULES_BY_TRIGGER.get(trigger);
         return list == null ? List.of() : list;
      }
   }

   public static boolean slotMatches(CooldownRules.CooldownRule r, CooldownRules.SlotKind kind, int index, CooldownRules.ArmorSlot armorSlot) {
      if (r == null) {
         return false;
      }

      if (r.slotKind == null || r.slotKind == CooldownRules.SlotKind.ANY) {
         return true;
      }

      if (r.slotKind == CooldownRules.SlotKind.INDEX) {
         return kind == CooldownRules.SlotKind.INDEX && r.slotIndex >= 0 && r.slotIndex == index;
      }

      if (r.slotKind != CooldownRules.SlotKind.HOTBAR) {
         if (r.slotKind == CooldownRules.SlotKind.MAINHAND) {
            return kind == CooldownRules.SlotKind.MAINHAND;
         } else if (r.slotKind == CooldownRules.SlotKind.OFFHAND) {
            return kind == CooldownRules.SlotKind.OFFHAND;
         } else if (r.slotKind != CooldownRules.SlotKind.ARMOR) {
            return false;
         } else if (kind != CooldownRules.SlotKind.ARMOR) {
            return false;
         } else {
            return r.armorSlot != null && r.armorSlot != CooldownRules.ArmorSlot.ANY ? r.armorSlot == armorSlot : true;
         }
      } else if (kind == CooldownRules.SlotKind.INDEX) {
         return index >= 0 && index <= 8;
      } else {
         return kind != CooldownRules.SlotKind.MAINHAND ? false : index >= 0 && index <= 8;
      }
   }

   public static boolean matchesRule(CooldownRules.CooldownRule r, ItemStack stack) {
      if (r != null && stack != null && !stack.isEmpty()) {
         if (r.id != null && !r.id.isBlank()) {
            String best = SuiteItemIdUtil.getBestId(stack);
            if (r.id.equals(best)) {
               return true;
            }
         }

         if (r.fallback != null && !r.fallback.isBlank()) {
            String foldedName = TextUtil.foldName(stack.getName().getString());
            return !foldedName.isBlank() && foldedName.contains(r.fallback);
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean matchesBreakBlock(CooldownRules.CooldownRule r, BlockState state) {
      if (r == null) {
         return false;
      }

      CooldownRules.BreakBlockKind kind = r.breakBlockKind == null ? CooldownRules.BreakBlockKind.ANY : r.breakBlockKind;

      return switch (kind) {
         case ANY -> true;
         case ORE -> AutoSwapBlockMatcher.isOreLike(state);
      };
   }

   public static void init() {
      CustomCooldownStore.load();
      CooldownSoundStore.load();
      loadLocalFile();
   }

   private static volatile String lastJson = "";

   /** One switchable item in the cooldown rules: several rules for the same item share one switch. */
   public record RuleInfo(String key, String name, String detail) {
   }

   /** The id a rule is switched on/off by: its item id, or its folded name when it has none. */
   public static String ruleKey(JsonObject o) {
      String id = blankToNull(optString(o, "id", null));
      if (id != null) {
         return id;
      }

      String fallback = blankToNull(optString(o, "fallback", null));
      return fallback == null ? "" : TextUtil.foldName(fallback);
   }

   private static JsonArray withoutDisabled(JsonArray rules) {
      List<String> off = FeatureConfig.INSTANCE.disabledCooldownItems;
      if (rules == null || off.isEmpty()) {
         return rules;
      }

      JsonArray kept = new JsonArray();
      for (JsonElement el : rules) {
         if (!el.isJsonObject() || !off.contains(ruleKey(el.getAsJsonObject()))) {
            kept.add(el);
         }
      }

      return kept;
   }

   /** Every item the loaded rules cover (including switched-off ones), by name. */
   public static List<RuleInfo> allRuleInfo() {
      List<RuleInfo> out = new ArrayList<>();
      Set<String> seen = new HashSet<>();
      try {
         JsonArray rules = JsonParser.parseString(lastJson).getAsJsonObject().getAsJsonArray("rules");
         if (rules == null) {
            return out;
         }

         for (JsonElement el : rules) {
            if (!el.isJsonObject()) {
               continue;
            }

            JsonObject o = el.getAsJsonObject();
            String key = ruleKey(o);
            if (key.isEmpty() || !seen.add(key)) {
               continue;
            }

            String fallback = blankToNull(optString(o, "fallback", null));
            Long ms = optLong(o, "ms", null);
            String trigger = optString(o, "trigger", "");
            if (ms == null || ms <= 0L || !isKnownTrigger(trigger)) {
               continue; // the loader ignores these too, so a switch for them would do nothing
            }

            String detail = (trigger == null ? "" : trigger.toLowerCase(Locale.ROOT).replace('_', ' ')) + (ms == null ? "" : ", " + (ms / 1000L) + "s");
            out.add(new RuleInfo(key, prettyName(fallback != null ? fallback : key), detail));
         }
      } catch (Exception ignored) {
      }

      out.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
      return out;
   }

   private static boolean isKnownTrigger(String name) {
      try {
         CooldownRules.Trigger.valueOf(name.trim().toUpperCase(Locale.ROOT));
         return true;
      } catch (Exception e) {
         return false;
      }
   }

   /** Re-applies the last rules after a switch changed. */
   public static void reapply() {
      if (!lastJson.isBlank()) {
         applyFromJson(lastJson);
      }
   }

   private static String prettyName(String raw) {
      StringBuilder sb = new StringBuilder();
      for (String word : raw.replace('_', ' ').trim().split("\\s+")) {
         if (!word.isEmpty()) {
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase(Locale.ROOT)).append(' ');
         }
      }

      return sb.toString().trim();
   }

   /**
    * Loads {@code config/<modId>/cooldowns.json}. The rules used to come only from the original developer's
    * server; reading the local copy keeps the cooldown HUD working with no backend.
    *
    * @return the number of rules in the file, or -1 if there is no readable file
    */
   public static int loadLocalFile() {
      Path file = FabricLoader.getInstance().getConfigDir().resolve(SuiteRuntime.profile().modId()).resolve("cooldowns.json");
      if (!Files.isRegularFile(file)) {
         SuiteLog.logger().info("[cooldowns] no {} - the cooldown HUD has no rules until one is added", file);
         applyCustomRules(); // still apply the player's own rules even with no cooldowns.json at all
         return -1;
      }

      try {
         String json = Files.readString(file);
         applyFromJson(json);
         applyCustomRules();
         JsonArray rules = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("rules");
         int count = rules == null ? 0 : rules.size();
         SuiteLog.logger().info("[cooldowns] loaded {} rules from {}", count, file.getFileName());
         return count;
      } catch (Exception e) {
         SuiteLog.logger().warn("[cooldowns] could not read {}: {}", file, e.toString());
         return -1;
      }
   }

   /**
    * Layers the player's own cooldown rules ({@code /buddy cooldown add}) on top of whatever cooldowns.json just
    * loaded - additive, so it never needs cooldowns.json to know about them.
    */
   public static void applyCustomRules() {
      for (CustomCooldownStore.Entry e : CustomCooldownStore.entries()) {
         addRule(new CooldownRules.CooldownRule(CustomCooldownStore.triggerOf(e), e.ms(), e.id(), null, CooldownRules.SlotKind.MAINHAND, -1, CooldownRules.ArmorSlot.ANY, CooldownRules.BreakBlockKind.ANY));
      }
   }

   public static CooldownRules.CooldownRule resolveRule(String itemKey, CooldownRules.Trigger trigger, ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         Map<String, CooldownRules.CooldownRule> byID = BY_ID.get(trigger);
         if (byID != null) {
            CooldownRules.CooldownRule r = byID.get(itemKey);
            if (r != null) {
               return r;
            }
         }

         List<CooldownRules.CooldownRule> byFallback = BY_FALLBACK.get(trigger);
         if (byFallback != null && !byFallback.isEmpty()) {
            String foldedName = TextUtil.foldName(stack.getName().getString());
            if (foldedName.isBlank()) {
               return null;
            }

            for (CooldownRules.CooldownRule r : byFallback) {
               String token = r.fallback;
               if (token != null && !token.isBlank() && foldedName.contains(token)) {
                  return r;
               }
            }

            return null;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   public static List<RelayModels.RelayCooldownEntry> buildCooldownSnapshot() {
      List<RelayModels.RelayCooldownEntry> out = new ArrayList<>();
      long now = System.currentTimeMillis();

      for (Entry<CooldownRules.CooldownRule, Long> e : CooldownState.endsAtByRuleKey.entrySet()) {
         CooldownRules.CooldownRule rule = e.getKey();
         long endsAtMs = e.getValue();
         if (endsAtMs > now) {
            String id = rule.id();
            if (id != null && !id.isBlank()) {
               Instant expiresAt = Instant.ofEpochMilli(endsAtMs);
               String isoUtc = RelayService.isoUtc(expiresAt);
               out.add(new RelayModels.RelayCooldownEntry(id, isoUtc));
            }
         }
      }

      return out;
   }

   public static List<CooldownRules.CooldownRule> getAllRules() {
      List<CooldownRules.CooldownRule> out = new ArrayList<>();

      for (Map<String, CooldownRules.CooldownRule> byId : BY_ID.values()) {
         out.addAll(byId.values());
      }

      return out;
   }

   public static CooldownRules.CooldownRule findRule(CooldownRules.Trigger trigger, String id, String displayName) {
      if (id != null && !id.isBlank() && !id.equals("<none>")) {
         Map<String, CooldownRules.CooldownRule> map = BY_ID.get(trigger);
         if (map != null) {
            CooldownRules.CooldownRule r = map.get(id);
            if (r != null) {
               return r;
            }
         }
      }

      String folded = TextUtil.foldName(displayName);
      if (!folded.isBlank()) {
         List<CooldownRules.CooldownRule> list = BY_FALLBACK.get(trigger);
         if (list != null) {
            for (CooldownRules.CooldownRule r : list) {
               String tok = r.fallback;
               if (tok != null && folded.contains(tok)) {
                  return r;
               }
            }
         }
      }

      return null;
   }

   public static void tick(MinecraftClient client, long now) {
      if (client.player != null) {
         activeByItemId.clear();
         Iterator<Entry<CooldownRules.CooldownRule, Long>> it = CooldownState.endsAtByRuleKey.entrySet().iterator();

         while (it.hasNext()) {
            Entry<CooldownRules.CooldownRule, Long> e = it.next();
            CooldownRules.CooldownRule rule = e.getKey();
            long endsAt = e.getValue() != null ? e.getValue() : 0L;
            if (endsAt <= now) {
               it.remove();
               CooldownState.lastItemKeyByRuleKey.remove(rule);
            } else {
               String itemId = CooldownState.lastItemKeyByRuleKey.get(rule);
               if (itemId != null) {
                  CooldownRules.ActiveView cur = activeByItemId.get(itemId);
                  if (cur == null || endsAt > cur.endsAt()) {
                     activeByItemId.put(itemId, new CooldownRules.ActiveView(endsAt, rule.ms()));
                  }
               }
            }
         }

         if (lastActionReadyRebuildMs <= 0L || now - lastActionReadyRebuildMs >= 250L) {
            lastActionReadyRebuildMs = now;

            for (int slot = 0; slot < 9; slot++) {
               ItemStack stack = client.player.getInventory().getStack(slot);
               HOTBAR_IDS[slot] = stack.isEmpty() ? null : SuiteItemIdUtil.getBestId(stack);
            }

            CooldownState.actionReadyCache.clear();
            List<CooldownsHud.ActiveCd> actives = CooldownsHud.collectActive(now);
            Map<CooldownRules.CooldownRule, CooldownsHud.ActiveCd> activeByRule = new HashMap<>();

            for (CooldownsHud.ActiveCd cd : actives) {
               activeByRule.put(cd.rule(), cd);
            }

            PlayerInventory inv = client.player.getInventory();

            int invSize;
            try {
               invSize = inv.size();
            } catch (Throwable t) {
               invSize = 9;
            }

            CooldownsConfig cfg = SuiteConfig.INSTANCE.CooldownsConfig;
            if (cfg != null && !cfg.trackInventory) {
               invSize = Math.min(invSize, 9);
            }

            Set<CooldownRules.CooldownRule> seenRules = new HashSet<>();

            for (int slot = 0; slot < invSize; slot++) {
               ItemStack stack;
               try {
                  stack = inv.getStack(slot);
               } catch (Throwable t) {
                  continue;
               }

               if (stack != null && !stack.isEmpty()) {
                  CooldownRules.CooldownRule rule = resolveDisplayRule(stack);
                  if (rule != null) {
                     boolean onlyUsable = cfg != null && cfg.inventoryHudMode == CooldownsConfig.InventoryHudMode.ONLY_USABLE;
                     if (rule.slotKind() != CooldownRules.SlotKind.OFFHAND
                        && rule.slotKind() != CooldownRules.SlotKind.ARMOR
                        && (
                           !onlyUsable
                              || (rule.slotKind() != CooldownRules.SlotKind.INDEX || rule.slotIndex() < 0 || rule.slotIndex() == slot)
                                 && (rule.slotKind() != CooldownRules.SlotKind.MAINHAND && rule.slotKind() != CooldownRules.SlotKind.HOTBAR || slot <= 8)
                                 && (rule.slotKind() != CooldownRules.SlotKind.MAINHAND || slot <= 8)
                        )
                        && seenRules.add(rule)) {
                        CooldownsHud.ActiveCd active = activeByRule.get(rule);
                        CooldownState.ActionReadyEntry ar = new CooldownState.ActionReadyEntry();
                        ar.stack = stack.copy();
                        ar.rule = rule;
                        ar.endsAt = active != null ? active.endsAt() : 0L;
                        CooldownState.actionReadyCache.add(ar);
                        if (CooldownState.actionReadyCache.size() >= 18) {
                           break;
                        }
                     }
                  }
               }
            }

            if (CooldownState.actionReadyCache.size() < 18 && (cfg == null || cfg.trackOffhand)) {
               ItemStack off = client.player.getOffHandStack();
               if (off != null && !off.isEmpty()) {
                  CooldownRules.CooldownRule rule = resolveDisplayRule(off);
                  if (rule != null && slotMatches(rule, CooldownRules.SlotKind.OFFHAND, -1, CooldownRules.ArmorSlot.ANY) && seenRules.add(rule)) {
                     CooldownsHud.ActiveCd active = activeByRule.get(rule);
                     CooldownState.ActionReadyEntry ar = new CooldownState.ActionReadyEntry();
                     ar.stack = off.copy();
                     ar.rule = rule;
                     ar.endsAt = active != null ? active.endsAt() : 0L;
                     CooldownState.actionReadyCache.add(ar);
                  }
               }
            }

            if (CooldownState.actionReadyCache.size() < 18 && (cfg == null || cfg.trackArmor)) {
               EquipmentSlot[] armorSlots = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
               CooldownRules.ArmorSlot[] armorKinds = new CooldownRules.ArmorSlot[]{
                  CooldownRules.ArmorSlot.HEAD, CooldownRules.ArmorSlot.CHEST, CooldownRules.ArmorSlot.LEGS, CooldownRules.ArmorSlot.FEET
               };

               for (int i = 0; i < armorSlots.length && CooldownState.actionReadyCache.size() < 18; i++) {
                  ItemStack a = client.player.getEquippedStack(armorSlots[i]);
                  if (a != null && !a.isEmpty()) {
                     CooldownRules.CooldownRule rule = resolveDisplayRule(a);
                     if (rule != null && slotMatches(rule, CooldownRules.SlotKind.ARMOR, -1, armorKinds[i]) && seenRules.add(rule)) {
                        CooldownsHud.ActiveCd active = activeByRule.get(rule);
                        CooldownState.ActionReadyEntry ar = new CooldownState.ActionReadyEntry();
                        ar.stack = a.copy();
                        ar.rule = rule;
                        ar.endsAt = active != null ? active.endsAt() : 0L;
                        CooldownState.actionReadyCache.add(ar);
                     }
                  }
               }
            }

            if (CooldownState.actionReadyCache.size() < 18) {
               for (CooldownsHud.ActiveCd cd : actives) {
                  if (CooldownState.actionReadyCache.size() >= 18) {
                     break;
                  }

                  CooldownRules.CooldownRule rule = cd.rule();
                  if (rule != null
                     && !seenRules.contains(rule)
                     && (
                        cfg == null
                           || (cfg.trackOffhand || rule.slotKind() != CooldownRules.SlotKind.OFFHAND)
                              && (cfg.trackArmor || rule.slotKind() != CooldownRules.SlotKind.ARMOR)
                              && (cfg.trackInventory || rule.slotKind() != CooldownRules.SlotKind.INDEX || rule.slotIndex() < 9)
                              && (cfg.trackInventory || rule.slotKind() != CooldownRules.SlotKind.ANY)
                     )) {
                     ItemStack snap = CooldownState.lastStackByRuleKey.get(rule);
                     if (snap == null) {
                        snap = ItemStack.EMPTY;
                     }

                     CooldownState.ActionReadyEntry ar = new CooldownState.ActionReadyEntry();
                     ar.stack = snap.copy();
                     ar.rule = rule;
                     ar.endsAt = cd.endsAt();
                     CooldownState.actionReadyCache.add(ar);
                     seenRules.add(rule);
                  }
               }
            }
         }
      }
   }

   private static CooldownRules.CooldownRule resolveDisplayRule(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         String bestId = SuiteItemIdUtil.getBestId(stack);
         CooldownRules.CooldownRule rule = byId.get(bestId);
         if (rule != null) {
            return rule;
         }

         if (byFallback.isEmpty()) {
            return null;
         }

         String folded = TextUtil.foldName(stack.getName().getString());
         if (folded.isBlank()) {
            return null;
         }

         for (Entry<String, CooldownRules.CooldownRule> e : byFallback.entrySet()) {
            if (folded.contains(e.getKey())) {
               return e.getValue();
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public static void applyFromJson(String json) {
      if (json != null && !json.isBlank()) {
         try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            lastJson = json;
            JsonArray rules = withoutDisabled(root.getAsJsonArray("rules"));
            if (rules != null) {
               Map<CooldownRules.Trigger, Map<String, CooldownRules.CooldownRule>> nextById = new EnumMap<>(CooldownRules.Trigger.class);
               Map<CooldownRules.Trigger, List<CooldownRules.CooldownRule>> nextByFallback = new EnumMap<>(CooldownRules.Trigger.class);
               Map<String, CooldownRules.CooldownRule> nextFlatById = new HashMap<>();
               Map<String, CooldownRules.CooldownRule> nextFlatByFallback = new HashMap<>();
               EnumMap<CooldownRules.Trigger, List<CooldownRules.CooldownRule>> nextSlotRules = new EnumMap<>(CooldownRules.Trigger.class);
               Iterator var8 = rules.iterator();

               while (true) {
                  JsonObject o;
                  Long ms;
                  CooldownRules.Trigger trigger;
                  while (true) {
                     if (!var8.hasNext()) {
                        synchronized (CooldownRules.class) {
                           BY_ID.clear();
                           BY_ID.putAll(nextById);
                           BY_FALLBACK.clear();
                           BY_FALLBACK.putAll(nextByFallback);
                           byId.clear();
                           byId.putAll(nextFlatById);
                           byFallback.clear();
                           byFallback.putAll(nextFlatByFallback);
                           SLOT_RULES_BY_TRIGGER.clear();
                           SLOT_RULES_BY_TRIGGER.putAll(nextSlotRules);
                           return;
                        }
                     }

                     JsonElement el = (JsonElement)var8.next();
                     if (el.isJsonObject()) {
                        o = el.getAsJsonObject();
                        String trigStr = optString(o, "trigger", null);
                        ms = optLong(o, "ms", null);
                        if (trigStr != null && ms != null && ms > 0L) {
                           try {
                              trigger = CooldownRules.Trigger.valueOf(trigStr.trim().toUpperCase(Locale.ROOT));
                              break;
                           } catch (Exception e) {
                           }
                        }
                     }
                  }

                  String eiId = blankToNull(optString(o, "id", null));
                  String fallback = blankToNull(optString(o, "fallback", null));
                  if (fallback != null) {
                     fallback = TextUtil.foldName(fallback);
                  }

                  boolean legacyInventory = optBool(o, "inventory", false);
                  CooldownRules.SlotSpec slotSpec = parseSlotSpec(o.get("slot"), legacyInventory);
                  CooldownRules.BreakBlockKind breakBlockKind = parseBreakBlockKind(o);
                  if (eiId != null || fallback != null) {
                     CooldownRules.CooldownRule r = new CooldownRules.CooldownRule(
                        trigger, ms, eiId, fallback, slotSpec.kind, slotSpec.index, slotSpec.armor, breakBlockKind
                     );
                     if (r.slotKind() == CooldownRules.SlotKind.INDEX
                        || r.slotKind() == CooldownRules.SlotKind.HOTBAR
                        || r.slotKind() == CooldownRules.SlotKind.MAINHAND
                        || r.slotKind() == CooldownRules.SlotKind.OFFHAND
                        || r.slotKind() == CooldownRules.SlotKind.ARMOR
                        || r.slotKind() == CooldownRules.SlotKind.ANY) {
                        nextSlotRules.computeIfAbsent(trigger, t -> new ArrayList<>()).add(r);
                     }

                     if (r.id() != null) {
                        nextById.computeIfAbsent(trigger, t -> new HashMap<>()).put(r.id(), r);
                        nextFlatById.put(r.id(), r);
                     }

                     if (r.fallback() != null) {
                        nextByFallback.computeIfAbsent(trigger, t -> new ArrayList<>()).add(r);
                        nextFlatByFallback.put(r.fallback(), r);
                     }
                  }
               }
            }
         } catch (Exception var23) {
         }
      }
   }

   private static void addRule(CooldownRules.CooldownRule r) {
      synchronized (CooldownRules.class) {
         if (r.id() != null) {
            BY_ID.computeIfAbsent(r.trigger(), t -> new HashMap<>()).put(r.id(), r);
            byId.put(r.id, r);
         }

         if (r.fallback != null) {
            BY_FALLBACK.computeIfAbsent(r.trigger(), t -> new ArrayList<>()).add(r);
            byFallback.put(r.fallback, r);
         }
      }
   }

   private static String optString(JsonObject o, String key, String def) {
      JsonElement e = o.get(key);
      if (e != null && e.isJsonPrimitive()) {
         String value = e.getAsString();
         return value.isBlank() ? def : value;
      } else {
         return def;
      }
   }

   private static Long optLong(JsonObject o, String key, Long def) {
      JsonElement e = o.get(key);
      return e != null && e.isJsonPrimitive() ? e.getAsLong() : def;
   }

   private static boolean optBool(JsonObject o, String key, boolean def) {
      JsonElement e = o.get(key);
      return e != null && e.isJsonPrimitive() ? e.getAsBoolean() : def;
   }

   private static int optInt(JsonObject o, String key, int def) {
      JsonElement e = o.get(key);
      return e != null && e.isJsonPrimitive() ? e.getAsInt() : def;
   }

   private static CooldownRules.BreakBlockKind parseBreakBlockKind(JsonObject o) {
      if (o == null) {
         return CooldownRules.BreakBlockKind.ANY;
      }

      String raw = optString(o, "breakBlock", null);
      if (raw == null) {
         raw = optString(o, "break_block", null);
      }

      if (raw == null) {
         raw = optString(o, "breakType", null);
      }

      if (raw == null) {
         return CooldownRules.BreakBlockKind.ANY;
      }

      String s = raw.trim().toLowerCase(Locale.ROOT);
      return !s.equals("ore") && !s.equals("ores") && !s.equals("ore_only") && !s.equals("ore-only")
         ? CooldownRules.BreakBlockKind.ANY
         : CooldownRules.BreakBlockKind.ORE;
   }

   private static CooldownRules.SlotSpec parseSlotSpec(JsonElement e, boolean legacyInventory) {
      if (e == null) {
         return legacyInventory
            ? new CooldownRules.SlotSpec(CooldownRules.SlotKind.ANY, -1, CooldownRules.ArmorSlot.ANY)
            : new CooldownRules.SlotSpec(CooldownRules.SlotKind.MAINHAND, -1, CooldownRules.ArmorSlot.ANY);
      }

      if (!e.isJsonPrimitive()) {
         return legacyInventory
            ? new CooldownRules.SlotSpec(CooldownRules.SlotKind.ANY, -1, CooldownRules.ArmorSlot.ANY)
            : new CooldownRules.SlotSpec(CooldownRules.SlotKind.MAINHAND, -1, CooldownRules.ArmorSlot.ANY);
      }

      JsonPrimitive p = e.getAsJsonPrimitive();
      if (p.isNumber()) {
         int idx = p.getAsInt();
         return new CooldownRules.SlotSpec(idx >= 0 ? CooldownRules.SlotKind.INDEX : CooldownRules.SlotKind.ANY, idx, CooldownRules.ArmorSlot.ANY);
      }

      if (!p.isString()) {
         return new CooldownRules.SlotSpec(CooldownRules.SlotKind.ANY, -1, CooldownRules.ArmorSlot.ANY);
      }

      String raw = p.getAsString();
      if (raw == null) {
         return new CooldownRules.SlotSpec(CooldownRules.SlotKind.ANY, -1, CooldownRules.ArmorSlot.ANY);
      }

      String s = raw.trim().toLowerCase(Locale.ROOT);
      if (s.isEmpty() || s.equals("any")) {
         return new CooldownRules.SlotSpec(CooldownRules.SlotKind.ANY, -1, CooldownRules.ArmorSlot.ANY);
      }

      if (s.equals("offhand")) {
         return new CooldownRules.SlotSpec(CooldownRules.SlotKind.OFFHAND, -1, CooldownRules.ArmorSlot.ANY);
      }

      if (s.equals("mainhand") || s.equals("hand") || s.equals("held")) {
         return new CooldownRules.SlotSpec(CooldownRules.SlotKind.MAINHAND, -1, CooldownRules.ArmorSlot.ANY);
      }

      if (s.equals("hotbar")) {
         return new CooldownRules.SlotSpec(CooldownRules.SlotKind.HOTBAR, -1, CooldownRules.ArmorSlot.ANY);
      }

      if (s.equals("armor")) {
         return new CooldownRules.SlotSpec(CooldownRules.SlotKind.ARMOR, -1, CooldownRules.ArmorSlot.ANY);
      }

      if (s.equals("head") || s.equals("helmet")) {
         return new CooldownRules.SlotSpec(CooldownRules.SlotKind.ARMOR, -1, CooldownRules.ArmorSlot.HEAD);
      }

      if (s.equals("chest") || s.equals("chestplate")) {
         return new CooldownRules.SlotSpec(CooldownRules.SlotKind.ARMOR, -1, CooldownRules.ArmorSlot.CHEST);
      }

      if (s.equals("legs") || s.equals("leggings")) {
         return new CooldownRules.SlotSpec(CooldownRules.SlotKind.ARMOR, -1, CooldownRules.ArmorSlot.LEGS);
      }

      if (s.equals("feet") || s.equals("boots")) {
         return new CooldownRules.SlotSpec(CooldownRules.SlotKind.ARMOR, -1, CooldownRules.ArmorSlot.FEET);
      }

      if (s.startsWith("index:")) {
         try {
            int idx = Integer.parseInt(s.substring("index:".length()).trim());
            return new CooldownRules.SlotSpec(idx >= 0 ? CooldownRules.SlotKind.INDEX : CooldownRules.SlotKind.ANY, idx, CooldownRules.ArmorSlot.ANY);
         } catch (Exception var6) {
         }
      }

      return new CooldownRules.SlotSpec(CooldownRules.SlotKind.MAINHAND, -1, CooldownRules.ArmorSlot.ANY);
   }

   private static String blankToNull(String s) {
      if (s == null) {
         return null;
      }

      s = s.trim();
      return s.isEmpty() ? null : s;
   }

   public record ActiveView(long endsAt, long totalMs) {
   }

   public enum ArmorSlot {
      ANY,
      HEAD,
      CHEST,
      LEGS,
      FEET;
   }

   public enum BreakBlockKind {
      ANY,
      ORE;
   }

   public record CooldownRule(
      CooldownRules.Trigger trigger,
      long ms,
      String id,
      String fallback,
      CooldownRules.SlotKind slotKind,
      int slotIndex,
      CooldownRules.ArmorSlot armorSlot,
      CooldownRules.BreakBlockKind breakBlockKind
   ) {
   }

   public enum SlotKind {
      ANY,
      INDEX,
      HOTBAR,
      MAINHAND,
      OFFHAND,
      ARMOR;
   }

   private static final class SlotSpec {
      final CooldownRules.SlotKind kind;
      final int index;
      final CooldownRules.ArmorSlot armor;

      SlotSpec(CooldownRules.SlotKind kind, int index, CooldownRules.ArmorSlot armor) {
         this.kind = kind;
         this.index = index;
         this.armor = armor;
      }
   }

   public enum Trigger {
      LEFT_CLICK,
      RIGHT_CLICK,
      ON_ATTACK,
      ON_DAMAGE_TAKEN,
      SNEAK_LEFT_CLICK,
      SNEAK_RIGHT_CLICK,
      SNEAK,
      BREAK,
      SNEAK_BREAK,
      PLACE;
   }
}
