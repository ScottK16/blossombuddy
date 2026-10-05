package org.blossomsuite.core.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
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
@Mixin(value = Gui.class, priority = 1500)
public abstract class ScoreboardSidebarMixin {
   private static final String OUTER = "extractScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V";
   private static final String INNER = "displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/scores/Objective;)V";

   @Inject(method = OUTER, at = @At("HEAD"))
   private void suitecore$frameStart(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
      ScoreboardHud.frameStart();
   }

   @Inject(method = INNER, at = @At("HEAD"), cancellable = true)
   private void suitecore$sidebar(GuiGraphicsExtractor context, Objective objective, CallbackInfo ci) {
      if (ScoreboardHud.handle(context, objective)) {
         ci.cancel();
      }
   }

   @Inject(method = OUTER, at = @At("RETURN"))
   private void suitecore$frameEnd(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
      Minecraft client = Minecraft.getInstance();
      boolean sidebarExists = client.level != null && client.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR) != null;
      ScoreboardHud.frameEnd(sidebarExists);
   }
}
