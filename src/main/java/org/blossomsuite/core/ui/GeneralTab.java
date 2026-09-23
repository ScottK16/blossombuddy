package org.blossomsuite.core.ui;

import org.blossomsuite.core.SuiteRuntime;
import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.SuiteConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class GeneralTab implements SuiteTab {
   @Override
   public String titleKey() {
      return "suitecore.tab.general";
   }

   @Override
   public boolean isEnabled() {
      return true;
   }

   @Override
   public void setEnabled(boolean enabled) {
   }

   @Override
   public void build(SuiteSettingsScreen screen) {
      SuiteConfig cfg = SuiteConfig.INSTANCE;
      int x = screen.contentX();
      int w = screen.contentW();
      int y = screen.bodyContentY() - screen.scrollOffset() + 8;
      screen.addContentWidget(
         new HoverLabelWidget(
            x,
            y + 6,
            180,
            12,
            Text.literal("Activation"),
            Tooltip.of(Text.literal("Controls whether " + SuiteRuntime.profile().displayName() + " runs in worlds."))
         )
      );
      ButtonWidget button = StyledButton.of(Text.literal(cfg.activationModeLabel()), b -> {
         cfg.cycleActivationMode();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(x + w - 120, y, 120, 20).build();
      button.setTooltip(Tooltip.of(Text.literal("Cycles between Multiplayer Only, On, and Off.")));
      screen.addContentWidget(button);
      y += 28;
      screen.addContentWidget(
         new HoverLabelWidget(
            x,
            y + 6,
            180,
            12,
            Text.literal("HUD Scaling Type"),
            Tooltip.of(Text.literal("Classic uses saved HUD scale. GUI-Adapt uses Minecraft GUI scale and disables HUD resizing."))
         )
      );
      ButtonWidget scalingButton = StyledButton.of(Text.literal(cfg.hudScalingModeLabel()), b -> {
         cfg.cycleHudScalingMode();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(x + w - 120, y, 120, 20).build();
      scalingButton.setTooltip(Tooltip.of(Text.literal("Cycles between Classic and GUI-Adapt HUD scaling.")));
      screen.addContentWidget(scalingButton);
   }

   @Override
   public void renderText(SuiteSettingsScreen screen, DrawContext ctx, int mouseX, int mouseY, float delta) {
      int x = screen.contentX();
      int y = screen.bodyContentY() - screen.scrollOffset() + 64;

      Text helper = switch (SuiteConfig.INSTANCE.activationMode) {
         case MULTIPLAYER_ONLY -> Text.literal("Runs on multiplayer servers and stays inactive in singleplayer.");
         case ON -> Text.literal("Runs in both multiplayer and singleplayer worlds.");
         case OFF -> Text.literal("Disables " + SuiteRuntime.profile().displayName() + " runtime hooks until re-enabled here.");
      };
      ctx.drawTextWithShadow(screen.getTextRenderer(), helper, x, y, -5592406);

      String scalingHelper = switch (SuiteConfig.INSTANCE.hudScalingMode) {
         case CLASSIC, AUTO_FIT -> "HUDs render at their saved scale. Minimum resize is 10%.";
         case GUI_ADAPTIVE -> "HUDs follow Minecraft GUI scale. Move them in HUD Edit Mode; resize is disabled.";
      };
      ctx.drawTextWithShadow(screen.getTextRenderer(), Text.literal(scalingHelper), x, y + 14, -5592406);
   }

   @Override
   public int contentHeight(SuiteSettingsScreen screen) {
      return 112;
   }

   @Override
   public void removed() {
   }
}
