package org.blossomsuite.core.hud;

import org.blossomsuite.core.chat.AdvertisementState;
import org.blossomsuite.core.chat.ChatChannel;
import org.blossomsuite.core.chat.PartyChatState;
import org.blossomsuite.core.chat.StaffChatState;
import org.blossomsuite.core.config.ChatConfig;
import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.util.HudStyleUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;

public final class ChatHud {
   public static int lastX;
   public static int lastY;
   public static int lastW;
   public static int lastH;
   public static final DraggableHud DRAGGABLE = new DraggableHud() {
      @Override
      public String id() {
         return "chat_mode";
      }

      @Override
      public int x() {
         return ChatHud.lastX;
      }

      @Override
      public int y() {
         return ChatHud.lastY;
      }

      @Override
      public int w() {
         return ChatHud.lastW;
      }

      @Override
      public int h() {
         return ChatHud.lastH;
      }

      @Override
      public float posX() {
         return SuiteConfig.INSTANCE.ChatConfig.positionX;
      }

      @Override
      public float posY() {
         return SuiteConfig.INSTANCE.ChatConfig.positionY;
      }

      @Override
      public void setPos(float nx, float ny) {
         SuiteConfig.INSTANCE.ChatConfig.setPosition(nx, ny);
         ConfigIO.saveIfDirty();
      }

      @Override
      public boolean enabled() {
         ChatConfig cfg = SuiteConfig.INSTANCE.ChatConfig;
         boolean showStaff = ChatHud.showStaffHud(cfg);
         return cfg.showTrackedChannelHud || cfg.showAdvertisementHud || showStaff;
      }

      @Override
      public boolean resizable() {
         return true;
      }

      @Override
      public float scale() {
         return SuiteConfig.INSTANCE.ChatConfig.scale;
      }

      @Override
      public void setScale(float s) {
         if (s < 0.1F) {
            s = 0.1F;
         }

         if (s > 2.0F) {
            s = 2.0F;
         }

         SuiteConfig.INSTANCE.ChatConfig.setScale(s);
         ConfigIO.saveIfDirty();
      }

      @Override
      public int baseW() {
         return ChatHud.getBaseWidth();
      }

      @Override
      public int baseH() {
         return ChatHud.getBaseHeight();
      }

      @Override
      public int minPixelWidth() {
         return SuiteConfig.INSTANCE.ChatConfig.compact ? 14 : 24;
      }

      @Override
      public int minPixelHeight() {
         return SuiteConfig.INSTANCE.ChatConfig.compact ? 8 : 12;
      }

      @Override
      public float backgroundOpacity() {
         return SuiteConfig.INSTANCE.ChatConfig.backgroundOpacity;
      }

      @Override
      public void setBackgroundOpacity(float opacity) {
         SuiteConfig.INSTANCE.ChatConfig.backgroundOpacity = Math.max(0.0F, Math.min(1.0F, opacity));
         ConfigIO.saveIfDirty();
      }
   };

   private ChatHud() {
   }

   public static void render(DrawContext ctx, MinecraftClient client) {
      if (client != null) {
         if (client.player != null) {
            if (!client.options.hudHidden) {
               ChatConfig cfg = SuiteConfig.INSTANCE.ChatConfig;
               boolean showTracked = cfg.showTrackedChannelHud;
               boolean showAd = cfg.showAdvertisementHud;
               boolean showStaff = showStaffHud(cfg);
               if (!showTracked && !showAd && !showStaff) {
                  lastH = 0;
                  lastW = 0;
                  lastY = 0;
                  lastX = 0;
               } else {
                  boolean compact = cfg.compact;
                  int baseW = getBaseWidth();
                  int baseH = getBaseHeight();
                  int screenW = client.getWindow().getScaledWidth();
                  int screenH = client.getWindow().getScaledHeight();
                  float scale = HudScaleUtil.scaleFor(cfg.scale, 0.1F, 2.0F, baseW, baseH, screenW, screenH);
                  int scaledW = Math.round(baseW * scale);
                  int scaledH = Math.round(baseH * scale);
                  int defaultX = 6;
                  int defaultY = screenH - scaledH - 40;
                  int x;
                  int y;
                  if (!(cfg.positionX < 0.0F) && !(cfg.positionY < 0.0F)) {
                     int maxX = Math.max(0, screenW - scaledW);
                     int maxY = Math.max(0, screenH - scaledH);
                     x = Math.round(cfg.positionX * maxX);
                     y = Math.round(cfg.positionY * maxY);
                  } else {
                     x = defaultX;
                     y = defaultY;
                  }

                  lastX = x;
                  lastY = y;
                  lastW = scaledW;
                  lastH = scaledH;
                  Matrix3x2fStack matrices = ctx.getMatrices();
                  matrices.pushMatrix();
                  matrices.translate(x, y);
                  matrices.scale(scale, scale);

                  try {
                     float bg = SuiteConfig.INSTANCE.ChatConfig.backgroundOpacity;
                     ctx.fill(0, 0, baseW, baseH, HudStyleUtil.panelBg(bg));
                     if (!compact) {
                        renderNormal(ctx, client, showTracked, showAd, baseW);
                     } else {
                        renderCompact(ctx, client, showTracked, showAd, baseW);
                     }
                  } finally {
                     matrices.popMatrix();
                  }
               }
            }
         }
      }
   }

   public static int getBaseWidth() {
      ChatConfig cfg = SuiteConfig.INSTANCE.ChatConfig;
      boolean showTracked = cfg.showTrackedChannelHud;
      boolean showAd = cfg.showAdvertisementHud;
      boolean compact = cfg.compact;
      boolean showStaff = showStaffHud(cfg);
      if (compact) {
         if (showAd) {
            return 84;
         } else {
            return !showTracked && !showStaff ? 54 : 54;
         }
      } else if (showAd) {
         return 120;
      } else {
         return !showTracked && !showStaff ? 72 : 120;
      }
   }

   public static int getBaseHeight() {
      ChatConfig cfg = SuiteConfig.INSTANCE.ChatConfig;
      boolean showTracked = cfg.showTrackedChannelHud;
      boolean showAd = cfg.showAdvertisementHud;
      boolean compact = cfg.compact;
      boolean showStaff = showStaffHud(cfg);
      if (compact) {
         int lines = 0;
         if (showTracked) {
            lines++;
         }

         if (showStaff) {
            lines++;
         }

         if (showAd) {
            lines++;
         }

         return lines <= 1 ? 12 : 12 + (lines - 1) * 10;
      } else {
         int lines = 0;
         if (showTracked) {
            lines++;
         }

         if (showStaff) {
            lines++;
         }

         if (showAd) {
            lines++;
         }

         return 12 + lines * 10 + 4;
      }
   }

   private static String getDisplayText(ChatChannel channel) {
      if (channel == null) {
         channel = ChatChannel.UNKNOWN;
      }
      return switch (channel) {
         case PUBLIC -> "PUBLIC";
         case MARRY -> "PRIVATE";
         case PARTY -> "PARTY";
         case STAFF -> "PUBLIC";
         case UNKNOWN -> "UNKNOWN";
      };
   }

   private static int getChannelColor(ChatChannel channel) {
      if (channel == null) {
         channel = ChatChannel.UNKNOWN;
      }
      return switch (channel) {
         case PUBLIC -> -8585317;
         case MARRY -> -11410;
         case PARTY -> -8859649;
         case STAFF -> -8585317;
         case UNKNOWN -> -4208683;
      };
   }

   private static ChatChannel effectiveTrackedChannel(ChatChannel channel) {
      return PartyChatState.partyChatEnabled ? ChatChannel.PARTY : channel;
   }

   private static int getStaffColor() {
      return StaffChatState.staffChatEnabled ? -8585317 : -37266;
   }

   private static boolean showStaffHud(ChatConfig cfg) {
      return cfg.trackStaffChat && cfg.showStaffHud && StaffChatState.isStaffMember;
   }

   private static String getAdvertisementStatusText() {
      if (AdvertisementState.isReady()) {
         return "Ready";
      }

      long totalSeconds = AdvertisementState.remainingMs() / 1000L;
      long minutes = totalSeconds / 60L;
      long seconds = totalSeconds % 60L;
      return String.format("%02d:%02d", minutes, seconds);
   }

   private static int getAdvertisementStatusColor() {
      return AdvertisementState.isReady() ? -8585317 : -37266;
   }

   private static void renderNormal(DrawContext ctx, MinecraftClient client, boolean showTracked, boolean showAd, int baseW) {
      float bg = SuiteConfig.INSTANCE.ChatConfig.backgroundOpacity;
      ctx.fill(0, 0, baseW, 12, HudStyleUtil.panelHeader(bg));
      ctx.drawTextWithShadow(client.textRenderer, "CHAT", 6, 2, -1);
      int y = 14;
      ChatConfig cfg = SuiteConfig.INSTANCE.ChatConfig;
      boolean showStaff = showStaffHud(cfg);
      if (showTracked) {
         ChatChannel channel = effectiveTrackedChannel(SuiteConfig.INSTANCE.ChatConfig.trackedChannel);
         if (channel == null) {
            channel = ChatChannel.UNKNOWN;
         }

         String text = "Channel: " + getDisplayText(channel);
         int color = getChannelColor(channel);
         ctx.drawTextWithShadow(client.textRenderer, text, 6, y, color);
         y += 12;
      }

      if (showStaff) {
         ctx.drawTextWithShadow(client.textRenderer, "Staff", 6, y, getStaffColor());
         y += 12;
      }

      if (showAd) {
         ChatConfig chatCfg = SuiteConfig.INSTANCE.ChatConfig;
         chatCfg.ensureAdvertiserProfilesInitialized();
         String profile = chatCfg.getActiveAdvertiserProfileName();
         String text = "Advertisement (" + profile + "): " + getAdvertisementStatusText();
         int color = getAdvertisementStatusColor();
         ctx.drawTextWithShadow(client.textRenderer, text, 6, y, color);
      }
   }

   private static void renderCompact(DrawContext ctx, MinecraftClient client, boolean showTracked, boolean showAd, int baseW) {
      int y = 2;
      ChatConfig cfg = SuiteConfig.INSTANCE.ChatConfig;
      boolean showStaff = showStaffHud(cfg);
      if (showTracked) {
         ChatChannel channel = effectiveTrackedChannel(SuiteConfig.INSTANCE.ChatConfig.trackedChannel);
         if (channel == null) {
            channel = ChatChannel.UNKNOWN;
         }

         String text = getDisplayText(channel);
         int color = getChannelColor(channel);
         int textW = client.textRenderer.getWidth(text);
         int textX = Math.max(2, (baseW - textW) / 2);
         ctx.drawTextWithShadow(client.textRenderer, text, textX, y, color);
         y += !showStaff && !showAd ? 0 : 10;
      }

      if (showStaff) {
         String text = "STAFF";
         int color = getStaffColor();
         int textW = client.textRenderer.getWidth(text);
         int textX = Math.max(2, (baseW - textW) / 2);
         ctx.drawTextWithShadow(client.textRenderer, text, textX, y, color);
         y += showAd ? 10 : 0;
      }

      if (showAd) {
         ChatConfig chatCfg = SuiteConfig.INSTANCE.ChatConfig;
         chatCfg.ensureAdvertiserProfilesInitialized();
         String profile = chatCfg.getActiveAdvertiserProfileName();
         String text = "Ad: " + profile;
         int color = getAdvertisementStatusColor();
         int textW = client.textRenderer.getWidth(text);
         int textX = Math.max(2, (baseW - textW) / 2);
         ctx.drawTextWithShadow(client.textRenderer, text, textX, y, color);
      }
   }
}
