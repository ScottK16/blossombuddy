package org.blossomsuite.core.ui.QolSubTabs;

import org.blossomsuite.core.config.FeatureConfig;

import org.blossomsuite.core.ui.StyledButton;

import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.config.VoteConfig;
import org.blossomsuite.core.ui.HoverLabelWidget;
import org.blossomsuite.core.ui.LabelWidget;
import org.blossomsuite.core.ui.SuiteSettingsScreen;
import org.blossomsuite.core.ui.SuiteSubTab;
import org.blossomsuite.core.util.WorldGate;
import org.blossomsuite.core.vote.VotePartySnapshot;
import org.blossomsuite.core.vote.VoteState;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class VoteSubTab implements SuiteSubTab {
   private static final DateTimeFormatter LAST_UPDATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
   private static final long STALE_MS = 600000L;
   private static final int MAX_SERVER_LINES = 8;

   @Override
   public String titleKey() {
      return "suitecore.tab.vote";
   }

   @Override
   public void build(SuiteSettingsScreen screen, int contentTopOffset) {
      SuiteConfig cfg = SuiteConfig.INSTANCE;
      int x = screen.contentX();
      int w = screen.contentW();
      int y = screen.bodyContentY() - screen.scrollOffset() + contentTopOffset;
      int rowH = 20;
      screen.addContentWidget(
         new HoverLabelWidget(x, y + 6, 160, 12, Text.literal("Vote Party HUD"), Tooltip.of(Text.literal("Show or hide the Vote Party HUD panel.")))
      );
      int toggleW = 80;
      int toggleX = x + w - toggleW;
      ButtonWidget enabledButton = StyledButton.of(Text.literal(cfg.VoteConfig.showHud ? "ON" : "OFF"), b -> {
         cfg.VoteConfig.showHud = !cfg.VoteConfig.showHud;
         cfg.markDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(toggleX, y, toggleW, 20).build();
      enabledButton.setTooltip(Tooltip.of(Text.literal("Turns the Vote Party HUD on or off.")));
      screen.addContentWidget(enabledButton);
      y += 26;
      screen.addContentWidget(
         new HoverLabelWidget(
            x, y + 6, 200, 12, Text.literal("Display Current Server"), Tooltip.of(Text.literal("Include the current server in Vote HUD and update displays."))
         )
      );
      ButtonWidget displayCurrentButton = StyledButton.of(Text.literal(cfg.VoteConfig.displayCurrentServer ? "ON" : "OFF"), b -> {
         cfg.VoteConfig.toggleDisplayCurrentServer();
         screen.rebuildPreserveScroll();
      }).dimensions(toggleX, y, toggleW, 20).build();
      displayCurrentButton.setTooltip(
         Tooltip.of(Text.literal("When OFF, the current server is hidden from Vote displays.\nWhen ON, it is shown in addition to other synced servers."))
      );
      screen.addContentWidget(displayCurrentButton);
      y += 26;
      screen.addContentWidget(
         new HoverLabelWidget(
            x,
            y + 6,
            200,
            12,
            Text.literal("Send Vote Data"),
            Tooltip.of(Text.literal("Share your realm's vote-party count anonymously, so players on other realms can be notified."))
         )
      );
      ButtonWidget sendButton = StyledButton.of(Text.literal(FeatureConfig.INSTANCE.relay.share ? "ON" : "OFF"), b -> {
         FeatureConfig.INSTANCE.relay.share = !FeatureConfig.INSTANCE.relay.share;
         FeatureConfig.markDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(toggleX, y, toggleW, 20).build();
      sendButton.setTooltip(
         Tooltip.of(
            Text.literal(
               "When OFF, "
                  + SuiteRuntime.profile().displayName()
                  + " will not send any vote-party data.\nWhen ON, it may send server name and vote-party progress (counts/timestamps)."
            )
         )
      );
      screen.addContentWidget(sendButton);
      y += 26;
      screen.addContentWidget(
         new HoverLabelWidget(
            x,
            y + 6,
            200,
            12,
            Text.literal("Notify When"),
            Tooltip.of(Text.literal("Shows a center-screen notice and plays a firework-style sound when vote party state changes."))
         )
      );
      ButtonWidget notifyButton = StyledButton.of(Text.literal(notifyLabel(cfg.VoteConfig.notifyWhen)), b -> {
         cfg.VoteConfig.notifyWhen = nextNotifyMode(cfg.VoteConfig.notifyWhen);
         cfg.markDirty();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(toggleX, y, toggleW, 20).build();
      notifyButton.setTooltip(
         Tooltip.of(
            Text.literal(
               "Countdown shows the countdown and plays a sound.\nOngoing shows a short party-start notice and plays a sound.\nNotifications are suppressed for your current server."
            )
         )
      );
      screen.addContentWidget(notifyButton);
      y += 26;
      long now = System.currentTimeMillis();
      String selfRealmKey = VoteState.normalize(WorldGate.Server);
      List<VotePartySnapshot> snaps = currentSnapshotsSorted(selfRealmKey, cfg.VoteConfig.displayCurrentServer);
      int listY = y + 20 + 6;
      screen.addContentWidget(
         new HoverLabelWidget(
            x,
            listY,
            160,
            12,
            Text.literal("Per-Server Updates"),
            Tooltip.of(
               Text.literal(
                  cfg.VoteConfig.displayCurrentServer
                     ? "Shows the last time each server's vote state was updated."
                     : "Shows the last time each server's vote state was updated.\n(Current server is hidden.)"
               )
            )
         )
      );
      listY += 14;
      if (snaps.isEmpty()) {
         screen.addContentWidget(new LabelWidget(x, listY, w, 12, Text.literal("(none yet)"), -5197648));
      } else {
         int shown = 0;

         for (VotePartySnapshot s : snaps) {
            if (shown >= 8) {
               break;
            }

            long seenAt = s != null ? s.getSeenAt() : 0L;
            String name = displayName(s);
            String timePart = seenAt <= 0L ? "(never)" : LAST_UPDATE_FMT.format(Instant.ofEpochMilli(seenAt)) + " (" + formatAge(now - seenAt) + " ago)";
            int color = seenAt > 0L && now - seenAt > 600000L ? -5197648 : -1;
            screen.addContentWidget(new LabelWidget(x, listY + shown * 14, w, 12, Text.literal(name + ": " + timePart), color));
            shown++;
         }
      }
   }

   @Override
   public void removed() {
   }

   @Override
   public int contentHeight(SuiteSettingsScreen screen, int contentTopOffset) {
      String selfRealmKey = VoteState.normalize(WorldGate.Server);
      int total = VoteState.byServer.size();
      if (!SuiteConfig.INSTANCE.VoteConfig.displayCurrentServer && !selfRealmKey.isBlank() && VoteState.byServer.containsKey(selfRealmKey)) {
         total = Math.max(0, total - 1);
      }

      int rows = Math.min(8, total);
      if (rows <= 0) {
         rows = 1;
      }

      return contentTopOffset + 138 + 14 + rows * 14 + 10;
   }

   private static VoteConfig.NotifyWhen nextNotifyMode(VoteConfig.NotifyWhen mode) {
      if (mode == null) {
         return VoteConfig.NotifyWhen.COUNTDOWN;
      }

      return switch (mode) {
         case OFF -> VoteConfig.NotifyWhen.COUNTDOWN;
         case COUNTDOWN -> VoteConfig.NotifyWhen.ONGOING;
         case ONGOING -> VoteConfig.NotifyWhen.BOTH;
         case BOTH -> VoteConfig.NotifyWhen.OFF;
      };
   }

   private static String notifyLabel(VoteConfig.NotifyWhen mode) {
      if (mode == null) {
         return "OFF";
      }

      return switch (mode) {
         case OFF -> "OFF";
         case COUNTDOWN -> "Countdown";
         case ONGOING -> "Ongoing";
         case BOTH -> "Both";
      };
   }

   private static List<VotePartySnapshot> currentSnapshotsSorted(String selfRealmKey, boolean displayCurrentServer) {
      List<VotePartySnapshot> out = new ArrayList<>(VoteState.byServer.values());
      out.removeIf(s -> s == null);
      if (!displayCurrentServer && selfRealmKey != null && !selfRealmKey.isBlank()) {
         out.removeIf(s -> selfRealmKey.equalsIgnoreCase(VoteState.normalize(s.getServerKey())));
      }

      out.sort(Comparator.comparing(s -> {
         String n = displayName(s);
         return n == null ? "" : n;
      }, String.CASE_INSENSITIVE_ORDER));
      return out;
   }

   private static String displayName(VotePartySnapshot snapshot) {
      if (snapshot == null) {
         return "Unknown";
      }

      String key = snapshot.getServerKey();
      if (key != null && !key.isBlank()) {
         String fromProfile = VoteState.displayNameFor(key, null);
         if (fromProfile != null && !fromProfile.isBlank()) {
            return fromProfile;
         }
      }

      String display = snapshot.getDisplayName();
      return display != null && !display.isBlank() ? display : "Unknown";
   }

   private static String formatAge(long ageMs) {
      long s = Math.max(0L, ageMs / 1000L);
      if (s < 60L) {
         return s + "s";
      }

      long m = s / 60L;
      if (m < 60L) {
         return m + "m";
      }

      long h = m / 60L;
      if (h < 48L) {
         return h + "h";
      }

      long d = h / 24L;
      return d + "d";
   }
}
