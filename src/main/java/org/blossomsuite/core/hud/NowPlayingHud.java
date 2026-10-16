package org.blossomsuite.core.hud;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.blossomsuite.core.config.FeatureConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.music.MusicService;
import org.blossomsuite.core.music.NowPlaying;

/** What your computer is playing, in a small movable panel. Only while music controls are on and something is playing. */
public final class NowPlayingHud extends PanelHud {
   public static final NowPlayingHud INSTANCE = new NowPlayingHud();
   private static final int WIDTH = 170;
   private static final int HEIGHT = 30;

   private NowPlayingHud() {
   }

   @Override
   public String id() {
      return "nowplaying";
   }

   @Override
   protected FeatureConfig.Panel panel() {
      return FeatureConfig.INSTANCE.music.panel;
   }

   @Override
   protected boolean shown() {
      return FeatureConfig.INSTANCE.music.enabled && FeatureConfig.INSTANCE.music.showHud;
   }

   @Override
   protected int[] defaultTopLeft(int screenW, int screenH, int w, int h) {
      return new int[]{screenW - w - 6, 26};
   }

   public void render(GuiGraphicsExtractor ctx, Minecraft client) {
      if (!this.shown() || client.player == null || !SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
         return;
      }

      NowPlaying np = MusicService.INSTANCE.current();
      if (!np.hasTrack() && !HudEditState.editMode) {
         return; // nothing playing: no empty panel on the screen
      }

      Font tr = client.font;
      String title = np.hasTrack() ? np.title() : "Song title";
      String artist = np.hasTrack() ? np.artist() : "Artist";
      boolean paused = np.hasTrack() && !np.playing();
      int textW = WIDTH - 12;
      this.draw(ctx, client, WIDTH, HEIGHT, true, c -> {
         c.text(tr, Component.literal(tr.plainSubstrByWidth((paused ? "|| " : "♪ ") + title, textW)).withStyle(ChatFormatting.WHITE), 6, 5, -1);
         c.text(tr, Component.literal(tr.plainSubstrByWidth(artist.isEmpty() ? np.app() : artist, textW)).withStyle(ChatFormatting.GRAY), 6, 17, -1);
      });
   }
}
