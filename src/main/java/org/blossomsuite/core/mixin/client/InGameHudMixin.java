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
import net.minecraft.client.MinecraftClient;
import org.blossomsuite.core.state.SidebarParser;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.scoreboard.ReadableScoreboardScore;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

   @Redirect(
      method = "renderCrosshair(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIII)V"
      )
   )
   private void suitecore$drawCrosshairTextureTint(DrawContext ctx, RenderPipeline pipeline, Identifier texture, int x, int y, int w, int h) {
      if (!suitecore$isVanillaCrosshairTexture(texture)) {
         ctx.drawGuiTexture(pipeline, texture, x, y, w, h, 1.0F);
      } else {
         SuiteConfig cfg = SuiteConfig.INSTANCE;
         if (cfg != null && cfg.QolConfig != null && cfg.isEnabledForCurrentWorld() && cfg.QolConfig.crosshairTintEnabled) {
            int argb = CrosshairTintUtil.computeCrosshairArgb(cfg.QolConfig, System.currentTimeMillis());
            if (cfg.QolConfig.crosshairShape != null && cfg.QolConfig.crosshairShape != QolConfig.CrosshairShape.VANILLA) {
               CrosshairShapeRenderer.draw(ctx, x + w / 2, y + h / 2, cfg.QolConfig, argb);
            } else {
               ctx.drawGuiTexture(pipeline, texture, x, y, w, h, argb);
            }
         } else {
            ctx.drawGuiTexture(pipeline, texture, x, y, w, h, 1.0F);
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
   private void suitecore$handleOverlayMessage(Text message, boolean tinted, CallbackInfo ci) {
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
                     Text chatLine = Text.literal("You got: ")
                        .formatted(Formatting.GREEN)
                        .append(Text.literal("$" + TextUtil.fmtMoney(reward.money())).formatted(Formatting.YELLOW))
                        .append(Text.literal(", and ").formatted(Formatting.GRAY))
                        .append(Text.literal(TextUtil.fmtExp(reward.exp()) + " exp").formatted(Formatting.AQUA));
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
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.inGameHud != null) {
         long now = System.currentTimeMillis();
         Text msg = JobsRapidOverlay.addAndFormat(addMoney, addXp, now);
         JobsRapidOverlay.runInternalOverlayWrite(() -> client.inGameHud.setOverlayMessage(msg, false));
      }
   }

   private static void showJobsTotalOverlay(boolean session) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.inGameHud != null) {
         JobsTracker tracker = JobsModule.tracker();
         if (tracker != null) {
            JobsTracker.LiveStats s = tracker.getLiveStats();
            double moneyTotal = session ? s.sessionMoney() : s.segmentMoney();
            double xpTotal = session ? s.sessionExp() : s.segmentExp();
            long now = System.currentTimeMillis();
            JobsRapidOverlay.retarget(session ? JobsRapidOverlay.Kind.SESSION_TOTAL : JobsRapidOverlay.Kind.SEGMENT_TOTAL, moneyTotal, xpTotal, now);
            Text msg = JobsRapidOverlay.formatNow(now);
            JobsRapidOverlay.runInternalOverlayWrite(() -> client.inGameHud.setOverlayMessage(msg, false));
         }
      }
   }

   @Inject(
      method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
      at = @At("HEAD"),
      cancellable = false
   )
   private void suitecore$debugSidebar(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
      SidebarParser.process(objective);
   }
}
