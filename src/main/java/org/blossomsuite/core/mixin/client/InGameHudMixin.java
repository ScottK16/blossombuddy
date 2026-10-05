package org.blossomsuite.core.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.alts.AltResourceState;
import org.blossomsuite.core.chat.ChatModeProbe;
import org.blossomsuite.core.chat.ChatOutput;
import org.blossomsuite.core.chat.PublicChatSendState;
import org.blossomsuite.core.chat.StaffChatState;
import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.QolConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.jobs.JobsActionBarMode;
import org.blossomsuite.core.jobs.JobsCaptureState;
import org.blossomsuite.core.jobs.JobsModule;
import org.blossomsuite.core.jobs.JobsParser;
import org.blossomsuite.core.jobs.JobsRapidOverlay;
import org.blossomsuite.core.jobs.JobsTracker;
import org.blossomsuite.core.qol.autofly.AutoFlyController;
import org.blossomsuite.core.services.VoteStateService;
import org.blossomsuite.core.state.SuiteState;
import org.blossomsuite.core.util.CrosshairShapeRenderer;
import org.blossomsuite.core.util.CrosshairTintUtil;
import org.blossomsuite.core.util.SuiteLog;
import org.blossomsuite.core.util.TextUtil;
import org.blossomsuite.core.util.WorldGate;
import org.blossomsuite.core.vote.VotePartySnapshot;
import org.blossomsuite.core.vote.VoteRuntime;
import org.blossomsuite.core.vote.VoteState;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import org.blossomsuite.core.state.SidebarParser;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Gui;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class InGameHudMixin {

   @Redirect(
      method = "extractCrosshair(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"
      )
   )
   private void suitecore$drawCrosshairTextureTint(GuiGraphicsExtractor ctx, RenderPipeline pipeline, Identifier texture, int x, int y, int w, int h) {
      if (!suitecore$isVanillaCrosshairTexture(texture)) {
         ctx.blitSprite(pipeline, texture, x, y, w, h, 1.0F);
      } else {
         SuiteConfig cfg = SuiteConfig.INSTANCE;
         if (cfg != null && cfg.QolConfig != null && cfg.isEnabledForCurrentWorld() && cfg.QolConfig.crosshairTintEnabled) {
            int argb = CrosshairTintUtil.computeCrosshairArgb(cfg.QolConfig, System.currentTimeMillis());
            if (cfg.QolConfig.crosshairShape != null && cfg.QolConfig.crosshairShape != QolConfig.CrosshairShape.VANILLA) {
               CrosshairShapeRenderer.draw(ctx, x + w / 2, y + h / 2, cfg.QolConfig, argb);
            } else {
               ctx.blitSprite(pipeline, texture, x, y, w, h, argb);
            }
         } else {
            ctx.blitSprite(pipeline, texture, x, y, w, h, 1.0F);
         }
      }
   }

   private static boolean suitecore$isVanillaCrosshairTexture(Identifier texture) {
      if (texture == null) {
         return false;
      }

      String path = texture.getPath();
      return "crosshair".equals(path) || "hud/crosshair".equals(path) || path.endsWith("/crosshair");
   }

   @Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
   private void suitecore$handleOverlayMessage(Component message, boolean tinted, CallbackInfo ci) {
      String raw = message == null ? "" : message.getString();
      SuiteLog.logger().debug("[hud-overlay] tinted={} text='{}'", tinted, raw);
      if (!JobsRapidOverlay.isInternalOverlayWrite()) {
         if (SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
            if (SuiteConfig.INSTANCE.JobsConfig.capture) {
               JobsParser.ParsedReward reward = JobsParser.tryParse(raw);
               if (reward != null) {
                  JobsCaptureState.markSeen();
                  JobsModule.tracker().onReward(reward.money(), reward.exp());
                  JobsModule.addLifeTime(reward.money());
                  ConfigIO.saveIfDirty();
                  SuiteLog.logger().info("[jobs-overlay] +${} +{}xp :: {}", new Object[]{reward.money(), reward.exp(), raw});
                  if (SuiteConfig.INSTANCE.JobsConfig.showInChat) {
                     Component chatLine = Component.literal("You got: ")
                        .withStyle(ChatFormatting.GREEN)
                        .append(Component.literal("$" + TextUtil.fmtMoney(reward.money())).withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(", and ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(TextUtil.fmtExp(reward.exp()) + " exp").withStyle(ChatFormatting.AQUA));
                     ChatOutput.raw(chatLine);
                  }

                  JobsActionBarMode mode = SuiteConfig.INSTANCE.JobsConfig.actionBarMode;
                  if (mode == null) {
                     mode = JobsActionBarMode.HIDE;
                  }

                  if (mode == JobsActionBarMode.HIDE) {
                     ci.cancel();
                  } else if (mode != JobsActionBarMode.ORIGINAL) {
                     ci.cancel();
                     switch (mode) {
                        case RUNNING_TOTAL:
                           showJobsRapidOverlay(reward.money(), reward.exp());
                           break;
                        case SESSION_TOTAL:
                           showJobsTotalOverlay(true);
                           break;
                        case SEGMENT_TOTAL:
                           showJobsTotalOverlay(false);
                     }
                  }
               }
            }
         }
      }
   }

   private static void showJobsRapidOverlay(double addMoney, double addXp) {
      Minecraft client = Minecraft.getInstance();
      if (client != null && client.gui != null) {
         long now = System.currentTimeMillis();
         Component msg = JobsRapidOverlay.addAndFormat(addMoney, addXp, now);
         JobsRapidOverlay.runInternalOverlayWrite(() -> client.gui.setOverlayMessage(msg, false));
      }
   }

   private static void showJobsTotalOverlay(boolean session) {
      Minecraft client = Minecraft.getInstance();
      if (client != null && client.gui != null) {
         JobsTracker tracker = JobsModule.tracker();
         if (tracker != null) {
            JobsTracker.LiveStats s = tracker.getLiveStats();
            double moneyTotal = session ? s.sessionMoney() : s.segmentMoney();
            double xpTotal = session ? s.sessionExp() : s.segmentExp();
            long now = System.currentTimeMillis();
            JobsRapidOverlay.retarget(session ? JobsRapidOverlay.Kind.SESSION_TOTAL : JobsRapidOverlay.Kind.SEGMENT_TOTAL, moneyTotal, xpTotal, now);
            Component msg = JobsRapidOverlay.formatNow(now);
            JobsRapidOverlay.runInternalOverlayWrite(() -> client.gui.setOverlayMessage(msg, false));
         }
      }
   }

   @Inject(
      method = "displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/scores/Objective;)V",
      at = @At("HEAD"),
      cancellable = false
   )
   private void suitecore$debugSidebar(GuiGraphicsExtractor context, Objective objective, CallbackInfo ci) {
      SidebarParser.process(objective);
   }
}
