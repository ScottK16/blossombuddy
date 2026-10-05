package org.blossomsuite.core.hud;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.blossomsuite.core.config.FeatureConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.emote.EmoteState;

/** Who is emoting right now and for how long (you first, then anyone else on your realm who is), movable like any other HUD panel. */
public final class EmoteTimerHud extends PanelHud {
   public static final EmoteTimerHud INSTANCE = new EmoteTimerHud();
   private static final int WIDTH = 150;
   private static final int ROW_H = 10;
   private static final int MAX_ROWS = 6;

   private EmoteTimerHud() {
   }

   @Override
   public String id() {
      return "emotetimers";
   }

   @Override
   protected FeatureConfig.Panel panel() {
      return FeatureConfig.INSTANCE.emotes.timerPanel;
   }

   @Override
   protected boolean shown() {
      return FeatureConfig.INSTANCE.emotes.enabled && FeatureConfig.INSTANCE.emotes.showTimers;
   }

   private static String nameOf(Minecraft client, UUID id) {
      if (client.player != null && id.equals(client.player.getUUID())) {
         return "You";
      }

      PlayerInfo entry = client.getConnection() == null ? null : client.getConnection().getPlayerInfo(id);
      return entry == null || entry.getProfile() == null ? "Someone" : entry.getProfile().name();
   }

   public void render(GuiGraphicsExtractor ctx, Minecraft client) {
      if (!this.shown() || client.player == null || !SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
         return;
      }

      long now = System.currentTimeMillis();
      List<Map.Entry<UUID, EmoteState.Active>> rows = EmoteState.INSTANCE.allActive(now);
      if (rows.isEmpty() && !HudEditState.editMode) {
         return; // nothing to show, and the panel shouldn't sit on the screen empty
      }

      // you come first, whoever has been at it longest
      UUID me = client.player.getUUID();
      rows = new java.util.ArrayList<>(rows);
      rows.sort((a, b) -> Boolean.compare(b.getKey().equals(me), a.getKey().equals(me)));

      Font tr = client.font;
      int shown = Math.min(rows.size(), MAX_ROWS);
      int extra = rows.size() - shown;
      int lines = Math.max(1, shown + (extra > 0 ? 1 : 0));
      int height = 6 + lines * ROW_H + 6;

      List<Map.Entry<UUID, EmoteState.Active>> shownRows = rows.subList(0, shown);
      this.draw(ctx, client, WIDTH, height, true, c -> {
         if (shownRows.isEmpty()) {
            c.text(tr, Component.literal("Nobody is emoting").withStyle(ChatFormatting.DARK_GRAY), 6, 6, -1);
            return;
         }

         int y = 6;
         for (Map.Entry<UUID, EmoteState.Active> row : shownRows) {
            String left = nameOf(client, row.getKey()) + " - " + row.getValue().emote().label();
            String time = EmoteState.formatElapsed(now - row.getValue().startMs());
            c.text(tr, Component.literal(tr.plainSubstrByWidth(left, WIDTH - 12 - tr.width(time) - 6)), 6, y, -1);
            c.text(tr, Component.literal(time).withStyle(ChatFormatting.GREEN), WIDTH - 6 - tr.width(time), y, -1);
            y += ROW_H;
         }

         if (extra > 0) {
            c.text(tr, Component.literal("+" + extra + " more").withStyle(ChatFormatting.DARK_GRAY), 6, y, -1);
         }
      });
   }
}
