package org.blossomsuite.core.hud;

import org.blossomsuite.core.vote.VoteNotifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public final class VoteNotificationOverlay {
   private VoteNotificationOverlay() {
   }

   public static void render(DrawContext ctx, MinecraftClient client) {
      if (client != null && client.player != null && !client.options.hudHidden) {
         long now = System.currentTimeMillis();
         if (VoteNotifier.hasActiveOngoing(now)) {
            renderBox(ctx, client, "Vote Party", VoteNotifier.activeOngoingServer() + " ongoing", -11141291);
         } else if (VoteNotifier.hasActiveCountdown(now)) {
            String server = VoteNotifier.activeCountdownServer();
            int seconds = VoteNotifier.activeCountdownSeconds(now);
            if (!server.isBlank() && seconds > 0) {
               renderBox(ctx, client, "Vote Party", server + " in " + seconds + "s", -11174);
            }
         }
      }
   }

   private static void renderBox(DrawContext ctx, MinecraftClient client, String title, String line, int accentColor) {
      int screenW = client.getWindow().getScaledWidth();
      int screenH = client.getWindow().getScaledHeight();
      int titleW = client.textRenderer.getWidth(title);
      int lineW = client.textRenderer.getWidth(line);
      int boxW = Math.max(titleW, lineW) + 18;
      int boxH = 31;
      int x = (screenW - boxW) / 2;
      int y = Math.max(34, screenH / 2 - 96);
      ctx.fill(x, y, x + boxW, y + boxH, -1441787369);
      ctx.fill(x, y, x + boxW, y + 1, accentColor);
      ctx.drawTextWithShadow(client.textRenderer, title, x + (boxW - titleW) / 2, y + 6, accentColor);
      ctx.drawTextWithShadow(client.textRenderer, line, x + (boxW - lineW) / 2, y + 18, -1);
   }
}
