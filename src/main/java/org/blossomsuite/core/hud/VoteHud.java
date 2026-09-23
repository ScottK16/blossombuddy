package org.blossomsuite.core.hud;

import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.util.HudStyleUtil;
import org.blossomsuite.core.vote.VotePartySnapshot;
import org.blossomsuite.core.vote.VoteRuntime;
import org.blossomsuite.core.vote.VoteState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;

public final class VoteHud {
   private static final long STALE_MS = 600000L;
   private static int lastBaseW = 118;
   private static int lastBaseH = 0;
   public static final DraggableHud DRAGGABLE = new DraggableHud() {
      @Override
      public String id() {
         return "vote";
      }

      @Override
      public int x() {
         return VoteHud.lastX;
      }

      @Override
      public int y() {
         return VoteHud.lastY;
      }

      @Override
      public int w() {
         return VoteHud.lastW;
      }

      @Override
      public int h() {
         return VoteHud.lastH;
      }

      @Override
      public float posX() {
         return SuiteConfig.INSTANCE.VoteConfig.positionX;
      }

      @Override
      public float posY() {
         return SuiteConfig.INSTANCE.VoteConfig.positionY;
      }

      @Override
      public void setPos(float nx, float ny) {
         SuiteConfig.INSTANCE.VoteConfig.setPosition(nx, ny);
         ConfigIO.saveIfDirty();
      }

      @Override
      public boolean enabled() {
         return SuiteConfig.INSTANCE.VoteConfig.showHud;
      }

      @Override
      public boolean resizable() {
         return true;
      }

      @Override
      public float scale() {
         return SuiteConfig.INSTANCE.VoteConfig.scale;
      }

      @Override
      public void setScale(float s) {
         SuiteConfig.INSTANCE.VoteConfig.setScale(s);
         ConfigIO.saveIfDirty();
      }

      @Override
      public int baseW() {
         return VoteHud.lastBaseW > 0 ? VoteHud.lastBaseW : 118;
      }

      @Override
      public int baseH() {
         return VoteHud.lastBaseH > 0 ? VoteHud.lastBaseH : 70;
      }

      @Override
      public float backgroundOpacity() {
         return SuiteConfig.INSTANCE.VoteConfig.backgroundOpacity;
      }

      @Override
      public void setBackgroundOpacity(float opacity) {
         SuiteConfig.INSTANCE.VoteConfig.backgroundOpacity = Math.max(0.0F, Math.min(1.0F, opacity));
         ConfigIO.saveIfDirty();
      }
   };
   public static int lastX;
   public static int lastY;
   public static int lastW;
   public static int lastH;

   private VoteHud() {
   }

   public static void render(DrawContext ctx, MinecraftClient client) {
      if (SuiteConfig.INSTANCE.VoteConfig.showHud) {
         if (client.player != null) {
            if (HudEditState.editMode || VoteRuntime.isActiveWorld()) {
               String selfRealmKey = VoteState.normalize(VoteRuntime.currentServer());
               List<Entry<String, VotePartySnapshot>> servers = new ArrayList<>(VoteState.byServer.entrySet());
               servers.removeIf(e -> e == null || e.getKey() == null || e.getKey().isBlank() || e.getValue() == null);
               if (!SuiteConfig.INSTANCE.VoteConfig.displayCurrentServer && !selfRealmKey.isBlank()) {
                  servers.removeIf(e -> selfRealmKey.equalsIgnoreCase(VoteState.normalize(e.getKey())));
               }

               servers.sort(Comparator.comparing(a -> safeName(a.getValue()), String.CASE_INSENSITIVE_ORDER));
               int screenH = client.getWindow().getScaledHeight();
               int screenW = client.getWindow().getScaledWidth();
               int baseW = 118;
               int rowH = 9;
               int rowGap = 4;
               int headerH = 14;
               int pad = 4;
               int rows = servers.size();
               if (rows <= 0) {
                  rows = HudEditState.editMode ? 3 : 0;
               }

               if (rows > 0) {
                  int baseH = 2 + headerH + pad + rows * rowH + (rows - 1) * rowGap + pad + 2;
                  lastBaseW = baseW;
                  lastBaseH = baseH;
                  float scale = HudScaleUtil.scaleFor(SuiteConfig.INSTANCE.VoteConfig.scale, 0.1F, 2.0F, baseW, baseH, screenW, screenH);
                  int w = Math.round(baseW * scale);
                  int h = Math.round(baseH * scale);
                  int defaultX = screenW - w - 6;
                  int defaultY = screenH / 2 - h - 20;
                  int x;
                  int y;
                  if (!(SuiteConfig.INSTANCE.VoteConfig.positionX < 0.0F) && !(SuiteConfig.INSTANCE.VoteConfig.positionY < 0.0F)) {
                     int maxX = Math.max(0, screenW - w);
                     int maxY = Math.max(0, screenH - h);
                     x = Math.round(SuiteConfig.INSTANCE.VoteConfig.positionX * maxX);
                     y = Math.round(SuiteConfig.INSTANCE.VoteConfig.positionY * maxY);
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
                  matrices.scale(scale, scale);

                  try {
                     float opacity = SuiteConfig.INSTANCE.VoteConfig.backgroundOpacity;
                     ctx.fill(0, 0, baseW, baseH, HudStyleUtil.panelBg(opacity));
                     ctx.fill(0, 0, baseW, headerH, HudStyleUtil.panelHeader(opacity));
                     ctx.fill(0, headerH, baseW, headerH + 1, HudStyleUtil.panelDivider(opacity));
                     ctx.drawTextWithShadow(client.textRenderer, "Vote Party", 6, 4, -1);
                     long now = System.currentTimeMillis();
                     int y0 = headerH + pad;
                     int maxRows = 12;
                     int drawn = 0;

                     for (Entry<String, VotePartySnapshot> entry : servers) {
                        if (drawn >= maxRows) {
                           break;
                        }

                        VotePartySnapshot snap = entry.getValue();
                        String left = safeName(snap);
                        boolean stale = isStale(snap, now);
                        boolean ongoing = isDerivedOngoing(snap, now);
                        boolean countingDown = !ongoing && snap != null && snap.isCountdownActive();
                        String right = "";
                        int rowColor = stale ? -5197648 : -1;
                        int midColor = rowColor;
                        String mid;
                        if (!stale && ongoing) {
                           mid = "Ongoing";
                           midColor = -11141291;
                        } else if (!stale && countingDown) {
                           mid = formatStatus(snap, now, false);
                           if (mid != null && !mid.isBlank()) {
                              midColor = -171;
                           } else {
                              mid = formatCount(snap);
                           }
                        } else {
                           mid = formatCount(snap);
                        }

                        int rowY = y0 + drawn * (rowH + rowGap);
                        int gap = 6;
                        int midW = client.textRenderer.getWidth(mid);
                        int leftMaxW = Math.max(0, baseW - 12 - 6 - midW);
                        String leftDraw = truncateToWidth(client.textRenderer, left, leftMaxW);
                        int leftW = client.textRenderer.getWidth(leftDraw);
                        int groupW = leftW + (leftDraw.isBlank() ? 0 : 6) + midW;
                        int leftX = Math.max(6, baseW / 2 - groupW / 2);
                        int midX = leftX + leftW + (leftDraw.isBlank() ? 0 : 6);
                        ctx.drawTextWithShadow(client.textRenderer, leftDraw, leftX, rowY, rowColor);
                        ctx.drawTextWithShadow(client.textRenderer, mid, midX, rowY, midColor);
                        drawn++;
                     }

                     if (drawn == 0 && HudEditState.editMode) {
                        for (int i = 0; i < 3; i++) {
                           int rowY = y0 + i * (rowH + rowGap);
                           String left = "Server";
                           String mid = i == 0 ? "30s" : (i == 1 ? "Ongoing" : "--/--");
                           int midColor = i == 0 ? -171 : (i == 1 ? -11141291 : -4208683);
                           String right = "";
                           int gap = 6;
                           int midW = client.textRenderer.getWidth(mid);
                           int leftMaxW = Math.max(0, baseW - 12 - 6 - midW);
                           String leftDraw = truncateToWidth(client.textRenderer, left, leftMaxW);
                           int leftW = client.textRenderer.getWidth(leftDraw);
                           int groupW = leftW + (leftDraw.isBlank() ? 0 : 6) + midW;
                           int leftX = Math.max(6, baseW / 2 - groupW / 2);
                           int midX = leftX + leftW + (leftDraw.isBlank() ? 0 : 6);
                           ctx.drawTextWithShadow(client.textRenderer, leftDraw, leftX, rowY, -1);
                           ctx.drawTextWithShadow(client.textRenderer, mid, midX, rowY, midColor);
                        }
                     }

                     if (HudEditState.editMode) {
                        ctx.fill(0, 0, baseW, 1, -1996488705);
                        ctx.fill(0, baseH - 1, baseW, baseH, -1996488705);
                        ctx.fill(0, 0, 1, baseH, -1996488705);
                        ctx.fill(baseW - 1, 0, baseW, baseH, -1996488705);
                     }
                  } finally {
                     matrices.popMatrix();
                  }
               }
            }
         }
      }
   }

   private static String formatCount(VotePartySnapshot s) {
      if (s == null) {
         return "--/--";
      }

      int max = s.getMax();
      return max <= 0 ? "--/--" : s.getCurrent() + "/" + max;
   }

   private static boolean isStale(VotePartySnapshot s, long now) {
      if (s == null) {
         return true;
      }

      long seenAt = s.getSeenAt();
      return seenAt <= 0L ? true : now - seenAt > 600000L;
   }

   private static String safeName(VotePartySnapshot s) {
      if (s == null) {
         return "Unknown";
      }

      String key = s.getServerKey();
      if (key != null && !key.isBlank()) {
         String fromProfile = VoteState.displayNameFor(key, null);
         if (fromProfile != null && !fromProfile.isBlank()) {
            return fromProfile;
         }
      }

      String dn = s.getDisplayName();
      return dn != null && !dn.isBlank() ? dn : "Unknown";
   }

   private static boolean isDerivedOngoing(VotePartySnapshot s, long now) {
      if (s == null) {
         return false;
      }

      if (s.isPartyOngoing()) {
         return true;
      }

      if (!s.isCountdownActive()) {
         return false;
      }

      Long t = s.getExpectedTriggerAt();
      return t != null && t > 0L && now >= t;
   }

   private static String formatStatus(VotePartySnapshot s, long now, boolean stale) {
      if (s == null) {
         return "";
      }

      if (s.isPartyOngoing()) {
         return "";
      }

      if (!s.isCountdownActive()) {
         return "";
      }

      Long expected = s.getExpectedTriggerAt();
      if (expected != null && expected > 0L) {
         long ms = expected - now;
         int rounded = Math.max(0, (int)Math.ceil(ms / 1000.0));
         return rounded > 0 ? rounded + "s" : "";
      }

      Float secs = s.getCountdownSecondsRemaining();
      if (secs == null) {
         return "";
      }

      int rounded = Math.max(0, (int)Math.ceil(secs.floatValue()));
      return rounded > 0 ? rounded + "s" : "";
   }

   private static String truncateToWidth(TextRenderer tr, String s, int maxW) {
      if (s == null || s.isBlank()) {
         return "";
      }

      if (maxW <= 0) {
         return "";
      }

      if (tr.getWidth(s) <= maxW) {
         return s;
      }

      String suffix = "...";
      int suffixW = tr.getWidth("...");
      if (suffixW >= maxW) {
         return "";
      }

      String cur = s;

      while (!cur.isEmpty() && tr.getWidth(cur) + suffixW > maxW) {
         cur = cur.substring(0, cur.length() - 1);
      }

      cur = cur.stripTrailing();
      return cur.isEmpty() ? "" : cur + "...";
   }
}
