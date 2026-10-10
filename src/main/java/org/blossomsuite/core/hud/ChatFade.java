package org.blossomsuite.core.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import org.blossomsuite.core.config.FeatureConfig;

/**
 * Lets the secondary chat windows fade away when idle, like the game's own chat. A window shows fully for a while after its
 * newest line (or while the chat box is open, or being moved in the HUD editor), then fades out, and a new line brings it back.
 */
public final class ChatFade {
   /** How long the fade itself takes once the window has been idle long enough. */
   static final long FADE_MS = 1500L;
   /** Below this the text would be drawn solid (the game treats a near-zero alpha as "no alpha"), so the window is skipped instead. */
   public static final float INVISIBLE = 0.03F;

   private static long chatBoxLastOpenMs = 0L;

   private ChatFade() {
   }

   /** 1 = fully shown, 0 = gone. {@code newestLineAtMs} is when the window's newest line arrived (0 if it has none). */
   public static float current(long newestLineAtMs) {
      FeatureConfig.Chat cfg = FeatureConfig.INSTANCE.chat;
      long now = System.currentTimeMillis();
      Minecraft mc = Minecraft.getInstance();
      if (mc.screen instanceof ChatScreen) {
         chatBoxLastOpenMs = now;
      }

      if (HudEditState.editMode) {
         return 1.0F;
      }

      long lastActivity = Math.max(newestLineAtMs, chatBoxLastOpenMs);
      return alpha(cfg.fadeWhenIdle, Math.max(1, cfg.fadeSeconds) * 1000L, now - lastActivity);
   }

   /** The fade as a pure function of how long the window has been idle. */
   static float alpha(boolean fadeWhenIdle, long holdMs, long idleMs) {
      if (!fadeWhenIdle || idleMs <= holdMs) {
         return 1.0F;
      }

      if (idleMs >= holdMs + FADE_MS) {
         return 0.0F;
      }

      return 1.0F - (float)(idleMs - holdMs) / (float)FADE_MS;
   }

   /** The colour with its alpha scaled by the fade. */
   public static int tint(int argb, float fade) {
      int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0.0F, Math.min(1.0F, fade)));
      return a << 24 | argb & 0xFFFFFF;
   }
}
