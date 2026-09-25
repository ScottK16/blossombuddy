package org.blossomsuite.core.hud;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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

   private static String nameOf(MinecraftClient client, UUID id) {
      if (client.player != null && id.equals(client.player.getUuid())) {
         return "You";
      }

      PlayerListEntry entry = client.getNetworkHandler() == null ? null : client.getNetworkHandler().getPlayerListEntry(id);
      return entry == null || entry.getProfile() == null ? "Someone" : entry.getProfile().getName();
   }

   public void render(DrawContext ctx, MinecraftClient client) {
      if (!this.shown() || client.player == null || !SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
         return;
      }

      long now = System.currentTimeMillis();
      List<Map.Entry<UUID, EmoteState.Active>> rows = EmoteState.INSTANCE.allActive(now);
      if (rows.isEmpty() && !HudEditState.editMode) {
         return; // nothing to show, and the panel shouldn't sit on the screen empty
      }

      // you come first, whoever has been at it longest
      UUID me = client.player.getUuid();
      rows = new java.util.ArrayList<>(rows);
      rows.sort((a, b) -> Boolean.compare(b.getKey().equals(me), a.getKey().equals(me)));

      TextRenderer tr = client.textRenderer;
      int shown = Math.min(rows.size(), MAX_ROWS);
      int extra = rows.size() - shown;
      int lines = Math.max(1, shown + (extra > 0 ? 1 : 0));
      int height = 6 + lines * ROW_H + 6;

      List<Map.Entry<UUID, EmoteState.Active>> shownRows = rows.subList(0, shown);
      this.draw(ctx, client, WIDTH, height, true, c -> {
         if (shownRows.isEmpty()) {
            c.drawTextWithShadow(tr, Text.literal("Nobody is emoting").formatted(Formatting.DARK_GRAY), 6, 6, -1);
            return;
         }

         int y = 6;
         for (Map.Entry<UUID, EmoteState.Active> row : shownRows) {
            String left = nameOf(client, row.getKey()) + " - " + row.getValue().emote().label();
            String time = EmoteState.formatElapsed(now - row.getValue().startMs());
            c.drawTextWithShadow(tr, Text.literal(tr.trimToWidth(left, WIDTH - 12 - tr.getWidth(time) - 6)), 6, y, -1);
            c.drawTextWithShadow(tr, Text.literal(time).formatted(Formatting.GREEN), WIDTH - 6 - tr.getWidth(time), y, -1);
            y += ROW_H;
         }

         if (extra > 0) {
            c.drawTextWithShadow(tr, Text.literal("+" + extra + " more").formatted(Formatting.DARK_GRAY), 6, y, -1);
         }
      });
   }
}
