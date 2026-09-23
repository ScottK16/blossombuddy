package org.blossomsuite.core.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.blossomsuite.core.hud.ScoreboardHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands the sidebar to {@link ScoreboardHud} once the player has customised it.
 *
 * <p>The priority is later than the default on purpose: a client that swaps in its own scoreboard (Dawn, Feather)
 * cancels the inner draw before we get there. By noticing that our hook was <em>not</em> reached while a sidebar
 * exists, we know another mod owns the scoreboard and can stand down instead of drawing a second one.
 */
@Mixin(value = InGameHud.class, priority = 1500)
public abstract class ScoreboardSidebarMixin {
   private static final String OUTER = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V";
   private static final String INNER = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V";

   @Inject(method = OUTER, at = @At("HEAD"))
   private void suitecore$frameStart(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      ScoreboardHud.frameStart();
   }

   @Inject(method = INNER, at = @At("HEAD"), cancellable = true)
   private void suitecore$sidebar(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
      if (ScoreboardHud.handle(context, objective)) {
         ci.cancel();
      }
   }

   @Inject(method = OUTER, at = @At("RETURN"))
   private void suitecore$frameEnd(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      MinecraftClient client = MinecraftClient.getInstance();
      boolean sidebarExists = client.world != null && client.world.getScoreboard().getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR) != null;
      ScoreboardHud.frameEnd(sidebarExists);
   }
}
