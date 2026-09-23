package org.blossomsuite.core.ui.QolSubTabs;

import org.blossomsuite.core.ui.StyledButton;

import org.blossomsuite.core.config.ConfigIO;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.qol.holepuncher.HolePuncher;
import org.blossomsuite.core.ui.HoverLabelWidget;
import org.blossomsuite.core.ui.SuiteSettingsScreen;
import org.blossomsuite.core.ui.SuiteSubTab;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class HolePuncherSubTab implements SuiteSubTab {
   @Override
   public String titleKey() {
      return "suitecore.tab.holepuncher";
   }

   @Override
   public void build(SuiteSettingsScreen screen, int contentTopOffset) {
      SuiteConfig cfg = SuiteConfig.INSTANCE;
      int x = screen.contentX();
      int w = screen.contentW();
      int y = screen.bodyContentY() - screen.scrollOffset() + contentTopOffset;
      int rowH = 20;
      screen.addContentWidget(
         new HoverLabelWidget(
            x,
            y + 6,
            180,
            12,
            Text.literal("Enabled"),
            Tooltip.of(Text.literal("Master toggle for HolePuncher. When OFF, the HolePuncher hotkey does nothing."))
         )
      );
      ButtonWidget enabledBtn = StyledButton.of(Text.literal(cfg.QolConfig.holePuncherEnabled ? "ON" : "OFF"), b -> {
         cfg.QolConfig.toggleHolePuncherEnabled();
         if (!cfg.QolConfig.holePuncherEnabled) {
            HolePuncher.stopNow(MinecraftClient.getInstance());
         }

         cfg.markDirty();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(x + w - 80, y, 80, 20).build();
      enabledBtn.setTooltip(Tooltip.of(Text.literal("Enables or disables HolePuncher.")));
      screen.addContentWidget(enabledBtn);
      y += 28;
      screen.addContentWidget(
         new HoverLabelWidget(
            x, y + 6, 180, 12, Text.literal("Failure Action Bar"), Tooltip.of(Text.literal("Shows a short action-bar message when Hole Puncher cannot start."))
         )
      );
      ButtonWidget failureScreenBtn = StyledButton.of(Text.literal(cfg.QolConfig.holePuncherFailureScreenMessage ? "ON" : "OFF"), b -> {
         cfg.QolConfig.toggleHolePuncherFailureScreenMessage();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(x + w - 80, y, 80, 20).build();
      failureScreenBtn.setTooltip(Tooltip.of(Text.literal("Shows warnings above the hotbar. Examples: No pickaxe equipped, Not valid blocks to break.")));
      screen.addContentWidget(failureScreenBtn);
      y += 28;
      screen.addContentWidget(
         new HoverLabelWidget(
            x, y + 6, 180, 12, Text.literal("Failure Chat Msg"), Tooltip.of(Text.literal("Sends a normal chat message when Hole Puncher cannot start."))
         )
      );
      ButtonWidget failureChatBtn = StyledButton.of(Text.literal(cfg.QolConfig.holePuncherFailureChatMessage ? "ON" : "OFF"), b -> {
         cfg.QolConfig.toggleHolePuncherFailureChatMessage();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(x + w - 80, y, 80, 20).build();
      failureChatBtn.setTooltip(Tooltip.of(Text.literal("Examples: No block targeted, Not on guided grid.")));
      screen.addContentWidget(failureChatBtn);
      y += 28;
      screen.addContentWidget(
         new HoverLabelWidget(
            x, y + 6, 180, 12, Text.literal("Failure Notice"), Tooltip.of(Text.literal("Shows a short upper-center warning above the crosshair area."))
         )
      );
      ButtonWidget failureNoticeBtn = StyledButton.of(Text.literal(cfg.QolConfig.holePuncherFailureNoticeMessage ? "ON" : "OFF"), b -> {
         cfg.QolConfig.toggleHolePuncherFailureNoticeMessage();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(x + w - 80, y, 80, 20).build();
      failureNoticeBtn.setTooltip(Tooltip.of(Text.literal("WoW-style warning placement between boss bars and the crosshair.")));
      screen.addContentWidget(failureNoticeBtn);
      y += 28;
      screen.addContentWidget(
         new HoverLabelWidget(
            x,
            y + 6,
            180,
            12,
            Text.literal("Status HUD"),
            Tooltip.of(Text.literal("Shows a draggable Hole Puncher status HUD.\nUse HUD Edit Mode to move or resize it."))
         )
      );
      ButtonWidget hudBtn = StyledButton.of(Text.literal(cfg.HolePuncherHudConfig.showHud ? "ON" : "OFF"), b -> {
         cfg.HolePuncherHudConfig.toggleShowHud();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(x + w - 80, y, 80, 20).build();
      hudBtn.setTooltip(Tooltip.of(Text.literal("Shows or hides the Hole Puncher status HUD.")));
      screen.addContentWidget(hudBtn);
      y += 28;
      screen.addContentWidget(
         new HoverLabelWidget(x, y + 6, 180, 12, Text.literal("HUD Header"), Tooltip.of(Text.literal("Shows or hides the Hole Puncher title bar on its HUD.")))
      );
      ButtonWidget hudHeaderBtn = StyledButton.of(Text.literal(cfg.HolePuncherHudConfig.showHeader ? "ON" : "OFF"), b -> {
         cfg.HolePuncherHudConfig.toggleShowHeader();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(x + w - 80, y, 80, 20).build();
      hudHeaderBtn.setTooltip(Tooltip.of(Text.literal("Shows or hides the Hole Puncher HUD header.")));
      screen.addContentWidget(hudHeaderBtn);
      y += 28;
      screen.addContentWidget(
         new HoverLabelWidget(
            x,
            y + 6,
            180,
            12,
            Text.literal("Guided Grid"),
            Tooltip.of(
               Text.literal(
                  "When enabled, HolePuncher only works on a 5-block cardinal grid.\nThe anchor locks to your position the first time you use HolePuncher in guided mode.\nAfter that, it only starts on targets aligned to that 5x5 X/Z grid."
               )
            )
         )
      );
      ButtonWidget guidedBtn = StyledButton.of(Text.literal(cfg.QolConfig.holePuncherGuided ? "ON" : "OFF"), b -> {
         cfg.QolConfig.holePuncherGuided = !cfg.QolConfig.holePuncherGuided;
         if (!cfg.QolConfig.holePuncherGuided) {
            HolePuncher.clearGuideAnchor();
         }

         cfg.markDirty();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(x + w - 80, y, 80, 20).build();
      guidedBtn.setTooltip(Tooltip.of(Text.literal("Toggles guided hole punching.")));
      screen.addContentWidget(guidedBtn);
      y += 28;
      screen.addContentWidget(
         new HoverLabelWidget(
            x, y + 6, 220, 12, Text.literal("Visual"), Tooltip.of(Text.literal("Choose how the guided grid is visualized when Guided Grid is ON."))
         )
      );
      int btnW = 140;
      int rightX = x + w;
      boolean blocks = cfg.QolConfig.holePuncherVisualMode == 0;
      String modeText = blocks ? "Blocks" : "Markers";
      ButtonWidget modeBtn = StyledButton.of(Text.literal(modeText), b -> {
         cfg.QolConfig.holePuncherVisualMode = cfg.QolConfig.holePuncherVisualMode == 0 ? 1 : 0;
         cfg.markDirty();
         ConfigIO.saveIfDirty();
         screen.rebuildPreserveScroll();
      }).dimensions(rightX - btnW, y, btnW, 20).build();
      modeBtn.setTooltip(Tooltip.of(Text.literal("Click to toggle between Markers and Blocks.")));
      screen.addContentWidget(modeBtn);
      y += 28;
      if (cfg.QolConfig.holePuncherVisualMode == 1) {
         screen.addContentWidget(
            new HoverLabelWidget(x, y + 6, 220, 12, Text.literal("Markers"), Tooltip.of(Text.literal("Turns the grid markers on/off (guided mode).")))
         );
         ButtonWidget markersBtn = StyledButton.of(Text.literal(cfg.QolConfig.holePuncherMarkersEnabled ? "ON" : "OFF"), b -> {
            cfg.QolConfig.toggleHolePuncherMarkersEnabled();
            cfg.markDirty();
            ConfigIO.saveIfDirty();
            screen.rebuildPreserveScroll();
         }).dimensions(x + w - 80, y, 80, 20).build();
         markersBtn.setTooltip(Tooltip.of(Text.literal("Toggles marker rendering.")));
         screen.addContentWidget(markersBtn);
         y += 28;
      }
   }

   @Override
   public void renderText(SuiteSettingsScreen screen, DrawContext ctx, int mouseX, int mouseY, float delta, int contentTopOffset) {
   }

   @Override
   public int contentHeight(SuiteSettingsScreen screen, int contentTopOffset) {
      int rows = 8 + (SuiteConfig.INSTANCE.QolConfig.holePuncherVisualMode == 1 ? 1 : 0);
      return contentTopOffset + rows * 28;
   }

   @Override
   public void removed() {
   }
}
