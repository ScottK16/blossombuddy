package org.blossomsuite.core.hud;

import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.CooldownsConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.cooldowns.CooldownNotifier;
import org.blossomsuite.core.cooldowns.CooldownRules;
import org.blossomsuite.core.cooldowns.CooldownRuntime;
import org.blossomsuite.core.cooldowns.CooldownState;
import org.blossomsuite.core.cooldowns.CooldownsMode;
import org.blossomsuite.core.services.models.RelayModels;
import org.blossomsuite.core.util.HudStyleUtil;
import org.blossomsuite.core.util.SuiteItemIdUtil;
import org.blossomsuite.core.util.TextUtil;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.joml.Matrix3x2fStack;

public final class CooldownsHud {
   public static final DraggableHud DRAGGABLE = new DraggableHud() {
      @Override
      public String id() {
         return "cooldowns";
      }

      @Override
      public int x() {
         return CooldownsHud.lastX;
      }

      @Override
      public int y() {
         return CooldownsHud.lastY;
      }

      @Override
      public int w() {
         return CooldownsHud.lastW;
      }

      @Override
      public int h() {
         return CooldownsHud.lastH;
      }

      @Override
      public float posX() {
         return SuiteConfig.INSTANCE.CooldownsConfig.positionX;
      }

      @Override
      public float posY() {
         return SuiteConfig.INSTANCE.CooldownsConfig.positionY;
      }

      @Override
      public void setPos(float nx, float ny) {
         SuiteConfig.INSTANCE.CooldownsConfig.setPosition(nx, ny);
         ConfigIO.saveIfDirty();
      }

      @Override
      public boolean enabled() {
         return SuiteConfig.INSTANCE.CooldownsConfig.showHud;
      }

      @Override
      public boolean resizable() {
         return true;
      }

      @Override
      public float scale() {
         return SuiteConfig.INSTANCE.CooldownsConfig.scale;
      }

      @Override
      public void setScale(float s) {
         SuiteConfig.INSTANCE.CooldownsConfig.setScale(s);
         ConfigIO.saveIfDirty();
      }

      @Override
      public int baseW() {
         return CooldownsHud.lastBaseW > 0 ? CooldownsHud.lastBaseW : 200;
      }

      @Override
      public int baseH() {
         return CooldownsHud.lastBaseH > 0 ? CooldownsHud.lastBaseH : 110;
      }

      @Override
      public float backgroundOpacity() {
         return SuiteConfig.INSTANCE.CooldownsConfig.backgroundOpacity;
      }

      @Override
      public void setBackgroundOpacity(float opacity) {
         SuiteConfig.INSTANCE.CooldownsConfig.backgroundOpacity = Math.max(0.0F, Math.min(1.0F, opacity));
         ConfigIO.saveIfDirty();
      }
   };
   public static int lastX;
   public static int lastY;
   public static int lastW;
   public static int lastH;
   private static int lastBaseW = 200;
   private static int lastBaseH = 110;
   private static final long RELAY_RENDER_ROWS_REBUILD_INTERVAL_MS = 250L;
   private static long lastRelayRenderRowsBuildMs = 0L;
   private static List<CooldownsHud.RelayRenderRow> cachedRelayRenderRows = List.of();

   private CooldownsHud() {
   }

   public static void renderPanel(DrawContext ctx, MinecraftClient client) {
      if (SuiteConfig.INSTANCE.CooldownsConfig.showHud) {
         if (client.player != null) {
            if (!client.options.hudHidden) {
               boolean compact = SuiteConfig.INSTANCE.CooldownsConfig.mode.equals(CooldownsMode.COMPACT);
               long now = System.currentTimeMillis();
               boolean legacyActiveOnly = SuiteConfig.INSTANCE.CooldownsConfig.legacyActiveOnly;
               List<CooldownState.ActionReadyEntry> localEntries;
               if (legacyActiveOnly) {
                  localEntries = new ArrayList<>();

                  for (CooldownState.ActionReadyEntry e : CooldownState.actionReadyCache) {
                     if (e != null && e.endsAt > now) {
                        localEntries.add(e);
                     }
                  }
               } else {
                  localEntries = CooldownState.actionReadyCache;
               }

               List<CooldownsHud.RelayRenderRow> relayRows = filterRelayRows(now, relayRenderRows(now), legacyActiveOnly);
               if (!localEntries.isEmpty() || !relayRows.isEmpty() || HudEditState.editMode) {
                  int baseW = compact ? 70 : 200;
                  int baseRowH = 18;
                  int baseTitleH = compact ? 0 : 14;
                  int basePad = 6;
                  int rowsLocal = localEntries.isEmpty() ? 5 : localEntries.size();
                  int spacerRows = !relayRows.isEmpty() && rowsLocal > 0 ? 1 : 0;
                  int rows = rowsLocal + spacerRows + relayRows.size();
                  int baseH = 2 + baseTitleH + rows * baseRowH + 4;
                  lastBaseW = baseW;
                  lastBaseH = baseH;
                  int screenW = client.getWindow().getScaledWidth();
                  int screenH = client.getWindow().getScaledHeight();
                  float s = HudScaleUtil.scaleFor(SuiteConfig.INSTANCE.CooldownsConfig.scale, 0.1F, 2.0F, baseW, baseH, screenW, screenH);
                  int w = Math.round(baseW * s);
                  int h = Math.round(baseH * s);
                  int defaultX = screenW - w - 6;
                  int defaultY = screenH / 2 + 80 + 90;
                  int x;
                  int y;
                  if (!(SuiteConfig.INSTANCE.CooldownsConfig.positionX < 0.0F) && !(SuiteConfig.INSTANCE.CooldownsConfig.positionY < 0.0F)) {
                     int maxX = Math.max(0, screenW - w);
                     int maxY = Math.max(0, screenH - h);
                     x = Math.round(SuiteConfig.INSTANCE.CooldownsConfig.positionX * maxX);
                     y = Math.round(SuiteConfig.INSTANCE.CooldownsConfig.positionY * maxY);
                  } else {
                     x = defaultX;
                     y = defaultY;
                  }

                  lastX = x;
                  lastY = y;
                  lastW = w;
                  lastH = h;
                  Matrix3x2fStack matrices = ctx.getMatrices();
                  matrices.pushMatrix();
                  matrices.translate(x, y);
                  matrices.scale(s, s);

                  try {
                     int x1 = 2;
                     int y1 = 2;
                     int x2 = baseW - 2;
                     int y2 = baseH - 2;
                     float bg = SuiteConfig.INSTANCE.CooldownsConfig.backgroundOpacity;
                     if (!compact) {
                        ctx.fill(x1, y1, x2, y2, HudStyleUtil.panelBg(bg));
                        ctx.fill(x1, y1, x2, y1 + baseTitleH, HudStyleUtil.panelHeader(bg));
                        ctx.fill(x1 + 4, y1 + baseTitleH, x2 - 4, y1 + baseTitleH + 1, HudStyleUtil.panelDivider(bg));
                        ctx.drawTextWithShadow(client.textRenderer, "COOLDOWNS", basePad, 6, -1);
                     } else {
                        ctx.fill(x1, y1, x2, y2, HudStyleUtil.panelBg(bg));
                     }

                     int rowY = y1 + (compact ? 3 : baseTitleH + 3);
                     if (!localEntries.isEmpty() || !relayRows.isEmpty()) {
                        for (CooldownState.ActionReadyEntry entry : localEntries) {
                           boolean onCooldown = entry.endsAt > now;
                           long remainingMs = onCooldown ? entry.endsAt - now : 0L;
                           int iconX = basePad;
                           int iconY = rowY + 1;
                           if (entry.stack != null && !entry.stack.isEmpty()) {
                              ctx.drawItem(entry.stack, iconX, iconY);
                           } else {
                              ctx.fill(iconX, iconY, iconX + 16, iconY + 16, 1442840575);
                           }

                           if (onCooldown) {
                              ctx.fill(iconX, iconY, iconX + 16, iconY + 16, -2013265920);
                           }

                           String rightText = onCooldown ? (int)Math.ceil(remainingMs / 1000.0) + "s" : "READY";
                           int rightW = client.textRenderer.getWidth(rightText);
                           int rightX;
                           if (compact) {
                              rightX = iconX + 18;
                           } else {
                              rightX = baseW - 6 - rightW;
                           }

                           int rightColor = onCooldown ? -4208683 : -8585317;
                           if (!compact) {
                              Text name = entry.stack != null && !entry.stack.isEmpty()
                                 ? TextUtil.stripLegacySectionCodes(entry.stack.getName())
                                 : Text.literal(entry.rule.fallback());
                              int nameX = iconX + 18;
                              int avail = rightX - 6 - nameX;
                              name = trimToWidth(client, name, avail);
                              int nameColor = onCooldown ? -8551021 : -1;
                              ctx.drawTextWithShadow(client.textRenderer, name, nameX, rowY + 2, nameColor);
                              ctx.drawTextWithShadow(client.textRenderer, rightText, rightX, rowY + 2, rightColor);
                           } else {
                              ctx.drawTextWithShadow(client.textRenderer, rightText, rightX, rowY + 4, rightColor);
                           }

                           rowY += baseRowH;
                        }

                        if (relayRows.isEmpty()) {
                           return;
                        }

                        if (!compact) {
                           int lineY = rowY + baseRowH / 2;
                           ctx.fill(basePad, lineY, baseW - 6, lineY + 1, HudStyleUtil.panelDivider(bg));
                        }

                        rowY += baseRowH;

                        for (CooldownsHud.RelayRenderRow rr : relayRows) {
                           if (rr.type == CooldownsHud.RelayRowType.REALM_TITLE) {
                              String t = rr.text.toUpperCase(Locale.ROOT);
                              t = trimToWidth(client, t, baseW - basePad - 6);
                              ctx.drawTextWithShadow(client.textRenderer, t, basePad, rowY + 2, -1);
                              rowY += baseRowH;
                           } else if (rr.type == CooldownsHud.RelayRowType.ALT_SUBTITLE) {
                              String t = rr.text;
                              t = trimToWidth(client, t, baseW - basePad - 6);
                              ctx.drawTextWithShadow(client.textRenderer, t, basePad, rowY + 2, -8551021);
                              rowY += baseRowH;
                           } else {
                              boolean onCooldown = rr.endsAtMs > now;
                              long remainingMs = onCooldown ? rr.endsAtMs - now : 0L;
                              int iconX = basePad;
                              int iconY = rowY + 1;
                              if (rr.stack != null && !rr.stack.isEmpty()) {
                                 ctx.drawItem(rr.stack, iconX, iconY);
                              } else {
                                 ctx.fill(iconX, iconY, iconX + 16, iconY + 16, 1442840575);
                              }

                              if (onCooldown) {
                                 ctx.fill(iconX, iconY, iconX + 16, iconY + 16, -2013265920);
                              }

                              String rightText = onCooldown ? (int)Math.ceil(remainingMs / 1000.0) + "s" : "READY";
                              int rightW = client.textRenderer.getWidth(rightText);
                              int rightX = compact ? iconX + 18 : baseW - 6 - rightW;
                              int rightColor = onCooldown ? -4208683 : -8585317;
                              if (!compact) {
                                 int nameX = iconX + 18;
                                 int avail = rightX - 6 - nameX;
                                 String name = rr.name == null ? "Cooldown" : rr.name.toUpperCase();
                                 name = trimToWidth(client, name, avail);
                                 int nameColor = onCooldown ? -8551021 : -1;
                                 ctx.drawTextWithShadow(client.textRenderer, name, nameX, rowY + 2, nameColor);
                                 ctx.drawTextWithShadow(client.textRenderer, rightText, rightX, rowY + 2, rightColor);
                              } else {
                                 ctx.drawTextWithShadow(client.textRenderer, rightText, rightX, rowY + 4, rightColor);
                              }

                              rowY += baseRowH;
                           }
                        }

                        return;
                     }

                     for (int i = 0; i < rows; i++) {
                        int iconX = basePad;
                        int iconY = rowY + 1;
                        ctx.fill(iconX, iconY, iconX + 16, iconY + 16, 1442840575);
                        String rightText = "READY";
                        int rightW = client.textRenderer.getWidth(rightText);
                        int rightX;
                        if (compact) {
                           rightX = iconX + 18;
                        } else {
                           rightX = baseW - 6 - rightW;
                        }

                        if (!compact) {
                           String name = "Cooldown Item";
                           int nameX = iconX + 18;
                           int avail = rightX - 6 - nameX;
                           name = trimToWidth(client, name, avail);
                           ctx.drawTextWithShadow(client.textRenderer, name, nameX, rowY + 2, -8551021);
                        }

                        ctx.drawTextWithShadow(client.textRenderer, rightText, rightX, rowY + 2, -8585317);
                        rowY += baseRowH;
                     }
                  } finally {
                     matrices.popMatrix();
                  }
               }
            }
         }
      }
   }

   public static void renderHotbar(DrawContext ctx, MinecraftClient client) {
      if (SuiteConfig.INSTANCE.CooldownsConfig.showHotbar) {
         if (client.player != null) {
            if (!client.options.hudHidden) {
               if (!CooldownRules.activeByItemId.isEmpty()) {
                  long now = System.currentTimeMillis();
                  int sw = client.getWindow().getScaledWidth();
                  int sh = client.getWindow().getScaledHeight();
                  int hotbarLeft = sw / 2 - 91;
                  int hotbarTop = sh - 22;

                  for (int slot = 0; slot < 9; slot++) {
                     ItemStack s = client.player.getInventory().getStack(slot);
                     if (!s.isEmpty()) {
                        String id = CooldownRules.HOTBAR_IDS[slot];
                        if (id == null) {
                           id = SuiteItemIdUtil.getBestId(s);
                        }

                        CooldownRules.ActiveView v = CooldownRules.activeByItemId.get(id);
                        if (v != null) {
                           long remaining = v.endsAt() - now;
                           if (remaining > 0L) {
                              float remaining01 = Math.min(1.0F, Math.max(0.0F, (float)remaining / (float)v.totalMs()));
                              CooldownsConfig.HotbarIndicatorStyle style = SuiteConfig.INSTANCE.CooldownsConfig.hotbarIndicatorStyle;
                              if (style == CooldownsConfig.HotbarIndicatorStyle.VANILLA_SWIPE) {
                                 drawHotbarCooldownVanillaSwipe(ctx, hotbarLeft, hotbarTop, slot, remaining01);
                              } else {
                                 drawHotbarCooldownBarTop(ctx, hotbarLeft, hotbarTop, slot, remaining01);
                              }

                              int secsLeft = (int)Math.ceil(remaining / 1000.0);
                              if (secsLeft <= 5) {
                                 drawHotbarSlotCountdown(ctx, client, hotbarLeft, hotbarTop, slot, secsLeft);
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public static List<CooldownsHud.ActiveCd> collectActive(long now) {
      List<CooldownsHud.ActiveCd> out = new ArrayList<>();

      for (Entry<CooldownRules.CooldownRule, Long> e : CooldownState.endsAtByRuleKey.entrySet()) {
         long endsAt = e.getValue() != null ? e.getValue() : 0L;
         if (endsAt > now) {
            out.add(new CooldownsHud.ActiveCd(e.getKey(), endsAt));
         }
      }

      return out;
   }

   private static String trimToWidth(MinecraftClient client, String s, int maxW) {
      if (client.textRenderer.getWidth(s) <= maxW) {
         return s;
      }

      String ell = "...";
      int ellW = client.textRenderer.getWidth(ell);

      for (int len = s.length(); len > 0; len--) {
         String sub = s.substring(0, len);
         if (client.textRenderer.getWidth(sub) + ellW <= maxW) {
            return sub + ell;
         }
      }

      return ell;
   }

   private static Text trimToWidth(MinecraftClient client, Text t, int maxW) {
      if (t == null) {
         return Text.empty();
      }

      if (client.textRenderer.getWidth(t) <= maxW) {
         return t;
      }

      String ell = "...";
      int ellW = client.textRenderer.getWidth(ell);
      int limit = maxW - ellW;
      if (limit <= 0) {
         return Text.literal(ell);
      }

      MutableText out = Text.empty();
      int[] w = new int[]{0};
      Optional<Boolean> stopped = t.visit((style, s) -> {
         if (s != null && !s.isEmpty()) {
            String clean = TextUtil.stripLegacySectionCodes(s);

            for (int i = 0; i < clean.length(); i++) {
               String ch = String.valueOf(clean.charAt(i));
               Text piece = Text.literal(ch).setStyle(style);
               int cw = client.textRenderer.getWidth(piece);
               if (w[0] + cw > limit) {
                  return Optional.of(Boolean.TRUE);
               }

               out.append(piece);
               w[0] += cw;
            }

            return Optional.empty();
         } else {
            return Optional.empty();
         }
      }, Style.EMPTY);
      out.append(Text.literal(ell));
      return out;
   }

   private static void drawHotbarSlotCountdown(DrawContext ctx, MinecraftClient client, int hotbarLeft, int hotbarTop, int hotbarIndex, int secsLeft) {
      String text = switch (secsLeft) {
         case 1 -> "1";
         case 2 -> "2";
         case 3 -> "3";
         case 4 -> "4";
         case 5 -> "5";
         default -> Integer.toString(secsLeft);
      };
      int slotX = hotbarLeft + hotbarIndex * 20;
      int slotY = hotbarTop;
      int tw = client.textRenderer.getWidth(text);
      int th = 9;
      int tx = slotX + (20 - tw) / 2 + 1;
      int ty = slotY + (20 - th) / 2 + 1;
      int color = -1;
      ctx.drawTextWithShadow(client.textRenderer, text, tx, ty, color);
   }

   private static void drawHotbarCooldownBarTop(DrawContext ctx, int hotbarLeft, int hotbarTop, int hotbarIndex, float remaining01) {
      int slotX = hotbarLeft + hotbarIndex * 20;
      int slotY = hotbarTop;
      int insetX = 2;
      int barH = 3;
      int barY = slotY + 2;
      int innerW = 20 - insetX * 2;
      int w = (int)Math.ceil(innerW * remaining01);
      ctx.fill(slotX + insetX, barY, slotX + insetX + innerW, barY + barH, 1711276032);
      ctx.fill(slotX + insetX, barY, slotX + insetX + w, barY + barH, -1);
   }

   private static void drawHotbarCooldownVanillaSwipe(DrawContext ctx, int hotbarLeft, int hotbarTop, int hotbarIndex, float remaining01) {
      int iconX = hotbarLeft + hotbarIndex * 20 + 2;
      int iconY = hotbarTop + 2;
      int y1 = iconY + (int)Math.floor(16.0F * (1.0F - remaining01));
      int y2 = y1 + (int)Math.ceil(16.0F * remaining01);
      ctx.fill(iconX, y1, iconX + 16, Math.min(iconY + 16, y2), -2013265920);
   }

   private static void updateTrackedFromRelay(long now) {
      RelayModels.RelayFetchResponse state = CooldownRuntime.relayState();
      if (state != null && state.alts != null) {
         for (RelayModels.AltState alt : state.alts) {
            if (alt != null && alt.altName != null && !alt.altName.isBlank()) {
               String altName = alt.altName;
               String realm = alt.realmName != null && !alt.realmName.isBlank() ? alt.realmName : null;
               Map<String, CooldownState.TrackedAltCooldown> map = CooldownState.trackedByAlt.computeIfAbsent(altName, k -> new HashMap<>());
               Set<String> seenThisPoll = new HashSet<>();
               if (alt.cooldowns != null) {
                  for (RelayModels.RelayCooldownEntry cd : alt.cooldowns) {
                     if (cd != null && cd.id != null && !cd.id.isBlank()) {
                        String id = cd.id;
                        long endsAt = cd.expiresAt == null ? 0L : parseIsoUtcToMs(cd.expiresAt);
                        if (endsAt <= now) {
                           endsAt = 0L;
                        }

                        CooldownState.TrackedAltCooldown t = map.get(id);
                        if (t != null || endsAt > 0L) {
                           if (t == null) {
                              t = new CooldownState.TrackedAltCooldown();
                              t.lastChangedMs = now;
                              map.put(id, t);
                           }

                           long prevEndsAt = t.endsAtMs;
                           if (prevEndsAt != endsAt) {
                              t.lastChangedMs = now;
                           }

                           t.endsAtMs = endsAt;
                           t.lastSeenMs = now;
                           t.realmName = realm;
                           seenThisPoll.add(id);
                           if (prevEndsAt > 0L && endsAt == 0L && prevEndsAt != 0L) {
                              CooldownNotifier.maybeNotifyRelayReady(altName, realm, id, prevEndsAt, now);
                           }
                        }
                     }
                  }
               }

               for (Entry<String, CooldownState.TrackedAltCooldown> e : map.entrySet()) {
                  String id = e.getKey();
                  CooldownState.TrackedAltCooldown t = e.getValue();
                  if (!seenThisPoll.contains(id)) {
                     long prevEndsAt = t.endsAtMs;
                     if (prevEndsAt > now) {
                        t.endsAtMs = 0L;
                        t.lastChangedMs = now;
                        CooldownNotifier.maybeNotifyRelayReady(altName, realm, id, prevEndsAt, now);
                     }
                  }
               }
            }
         }

         pruneTrackedAltCooldowns(now);
      } else {
         pruneTrackedAltCooldowns(now);
         clearRelayTracking();
      }
   }

   private static void pruneTrackedAltCooldowns(long now) {
      for (Map<String, CooldownState.TrackedAltCooldown> map : CooldownState.trackedByAlt.values()) {
         if (map != null) {
            map.entrySet().removeIf(e -> {
               CooldownState.TrackedAltCooldown t = e.getValue();
               if (t == null) {
                  return true;
               } else {
                  long lastSeen = t.lastSeenMs;
                  if (lastSeen > 0L && now - lastSeen > 300000L) {
                     return true;
                  } else if (t.endsAtMs <= now) {
                     long readySince = t.lastChangedMs > 0L ? t.lastChangedMs : t.lastSeenMs;
                     return readySince > 0L && now - readySince > 300000L;
                  } else {
                     return false;
                  }
               }
            });
         }
      }

      CooldownState.trackedByAlt.entrySet().removeIf(e -> e.getValue() == null || e.getValue().isEmpty());
   }

   private static void clearRelayTracking() {
      CooldownState.trackedByAlt.clear();
      CooldownState.relayReadyNotified.clear();
   }

   private static List<CooldownsHud.RelayRenderRow> buildRelayRenderRows(long now) {
      updateTrackedFromRelay(now);
      Map<String, Map<String, List<CooldownsHud.RelayRenderRow>>> grouped = new HashMap<>();

      for (Entry<String, Map<String, CooldownState.TrackedAltCooldown>> altEntry : CooldownState.trackedByAlt.entrySet()) {
         String altName = altEntry.getKey();
         MinecraftClient mc = MinecraftClient.getInstance();
         String selfName = mc.player == null ? null : mc.player.getName().getString();
         if (selfName == null || altName == null || !altName.equalsIgnoreCase(selfName)) {
            Map<String, CooldownState.TrackedAltCooldown> cds = altEntry.getValue();
            if (cds != null && !cds.isEmpty()) {
               String realm = null;
               Iterator altNamex = cds.values().iterator();
               if (altNamex.hasNext()) {
                  CooldownState.TrackedAltCooldown t = (CooldownState.TrackedAltCooldown)altNamex.next();
                  realm = t.realmName;
               }

               if (realm == null || realm.isBlank()) {
                  realm = "Unknown Realm";
               }

               Map<String, List<CooldownsHud.RelayRenderRow>> realmMap = grouped.computeIfAbsent(realm, r -> new HashMap<>());
               List<CooldownsHud.RelayRenderRow> list = realmMap.computeIfAbsent(altName, a -> new ArrayList<>());

               for (Entry<String, CooldownState.TrackedAltCooldown> cdEntry : cds.entrySet()) {
                  String id = cdEntry.getKey();
                  CooldownState.TrackedAltCooldown t = cdEntry.getValue();
                  CooldownRules.CooldownRule rule = CooldownRules.byId.get(id);
                  ItemStack stack = ItemStack.EMPTY;
                  if (rule != null) {
                     ItemStack s = CooldownState.lastStackByRuleKey.get(rule);
                     if (s != null) {
                        stack = s;
                     }
                  }

                  String name;
                  if (stack != null && !stack.isEmpty()) {
                     name = TextUtil.stripLegacySectionCodes(stack.getName()).getString();
                  } else if (rule != null && rule.fallback() != null && !rule.fallback().isBlank()) {
                     name = rule.fallback();
                  } else {
                     name = id;
                  }

                  list.add(CooldownsHud.RelayRenderRow.cooldown(stack == null ? ItemStack.EMPTY : stack, name, t.endsAtMs));
               }

               list.sort((a, b) -> {
                  boolean aOn = a.endsAtMs > now;
                  boolean bOn = b.endsAtMs > now;
                  if (aOn != bOn) {
                     return aOn ? -1 : 1;
                  }

                  long ak = aOn ? a.endsAtMs : Long.MAX_VALUE;
                  long bk = bOn ? b.endsAtMs : Long.MAX_VALUE;
                  return Long.compare(ak, bk);
               });
            }
         }
      }

      List<CooldownsHud.RelayRenderRow> out = new ArrayList<>();
      List<String> realms = new ArrayList<>(grouped.keySet());
      realms.sort(String::compareToIgnoreCase);

      for (String realm : realms) {
         out.add(CooldownsHud.RelayRenderRow.realmTitle(realm));
         Map<String, List<CooldownsHud.RelayRenderRow>> alts = grouped.get(realm);
         List<String> altNames = new ArrayList<>(alts.keySet());
         altNames.sort(String::compareToIgnoreCase);

         for (String altName : altNames) {
            out.add(CooldownsHud.RelayRenderRow.altSubtitle(altName));
            out.addAll(alts.get(altName));
         }
      }

      int maxRows = 18;
      if (out.size() > maxRows) {
         out = out.subList(0, maxRows);
      }

      return out;
   }

   private static List<CooldownsHud.RelayRenderRow> relayRenderRows(long now) {
      if (lastRelayRenderRowsBuildMs > 0L && now - lastRelayRenderRowsBuildMs < 250L) {
         return cachedRelayRenderRows;
      }

      lastRelayRenderRowsBuildMs = now;
      cachedRelayRenderRows = buildRelayRenderRows(now);
      return cachedRelayRenderRows;
   }

   private static long parseIsoUtcToMs(String isoUtc) {
      try {
         return Instant.parse(isoUtc).toEpochMilli();
      } catch (Exception e) {
         return 0L;
      }
   }

   private static List<CooldownsHud.RelayRenderRow> filterRelayRows(long now, List<CooldownsHud.RelayRenderRow> in, boolean activeOnly) {
      if (in != null && !in.isEmpty()) {
         if (!activeOnly) {
            return in;
         }

         List<CooldownsHud.RelayRenderRow> out = new ArrayList<>();
         CooldownsHud.RelayFilterState st = new CooldownsHud.RelayFilterState();

         for (CooldownsHud.RelayRenderRow rr : in) {
            if (rr != null) {
               if (rr.type == CooldownsHud.RelayRowType.REALM_TITLE) {
                  st.flushAlt(out);
                  st.curRealm = rr;
                  st.curAlt = null;
                  st.curCooldowns.clear();
                  st.realmEmitted = false;
               } else if (rr.type == CooldownsHud.RelayRowType.ALT_SUBTITLE) {
                  st.flushAlt(out);
                  st.curAlt = rr;
                  st.curCooldowns.clear();
               } else if (rr.type == CooldownsHud.RelayRowType.COOLDOWN && rr.endsAtMs > now) {
                  st.curCooldowns.add(rr);
               }
            }
         }

         st.flushAlt(out);
         return out;
      } else {
         return List.of();
      }
   }

   public record ActiveCd(CooldownRules.CooldownRule rule, long endsAt) {
   }

   private static final class RelayFilterState {
      CooldownsHud.RelayRenderRow curRealm = null;
      CooldownsHud.RelayRenderRow curAlt = null;
      List<CooldownsHud.RelayRenderRow> curCooldowns = new ArrayList<>();
      boolean realmEmitted = false;

      void flushAlt(List<CooldownsHud.RelayRenderRow> out) {
         if (this.curCooldowns.isEmpty()) {
            this.curCooldowns.clear();
         } else {
            if (!this.realmEmitted && this.curRealm != null) {
               out.add(this.curRealm);
               this.realmEmitted = true;
            }

            if (this.curAlt != null) {
               out.add(this.curAlt);
            }

            out.addAll(this.curCooldowns);
            this.curCooldowns.clear();
         }
      }
   }

   private static final class RelayRenderRow {
      final CooldownsHud.RelayRowType type;
      final ItemStack stack;
      final String name;
      final long endsAtMs;
      final String text;

      private RelayRenderRow(CooldownsHud.RelayRowType type, String text, ItemStack stack, String name, long endsAtMs) {
         this.type = type;
         this.text = text;
         this.stack = stack;
         this.name = name;
         this.endsAtMs = endsAtMs;
      }

      static CooldownsHud.RelayRenderRow realmTitle(String realm) {
         return new CooldownsHud.RelayRenderRow(CooldownsHud.RelayRowType.REALM_TITLE, realm, ItemStack.EMPTY, null, 0L);
      }

      static CooldownsHud.RelayRenderRow altSubtitle(String altName) {
         return new CooldownsHud.RelayRenderRow(CooldownsHud.RelayRowType.ALT_SUBTITLE, altName, ItemStack.EMPTY, null, 0L);
      }

      static CooldownsHud.RelayRenderRow cooldown(ItemStack stack, String name, long endsAtMs) {
         return new CooldownsHud.RelayRenderRow(CooldownsHud.RelayRowType.COOLDOWN, null, stack, name, endsAtMs);
      }
   }

   private enum RelayRowType {
      REALM_TITLE,
      ALT_SUBTITLE,
      COOLDOWN;
   }
}
