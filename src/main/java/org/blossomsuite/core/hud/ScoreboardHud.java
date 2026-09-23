package org.blossomsuite.core.hud;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.text.Text;
import org.blossomsuite.core.config.FeatureConfig;
import org.blossomsuite.core.config.SuiteConfig;
import org.blossomsuite.core.ui.Theme;
import org.joml.Matrix3x2fStack;

/**
 * The sidebar scoreboard, drawn by us once the player customises it (moved, resized, hidden or restyled).
 *
 * <p>Vanilla's layout is reproduced exactly, so an untouched scoreboard looks the same. Until something is
 * customised we leave vanilla (or another mod's scoreboard) alone. The whole draw, including the translate and
 * scale, happens inside {@link #handle}, so the matrix can never be left pushed by a mod that cancels the method.
 */
public final class ScoreboardHud {
   private static final Comparator<ScoreboardEntry> ORDER = Comparator.comparing(ScoreboardEntry::value)
      .reversed()
      .thenComparing(ScoreboardEntry::owner, String.CASE_INSENSITIVE_ORDER);
   private static final float MIN_SCALE = 0.3F;
   private static final float MAX_SCALE = 2.0F;
   private static final float AUTO_OPACITY_SHOWN = 0.35F;
   private static final int TITLE_H = 9;

   private static int lastX;
   private static int lastY;
   private static int lastW;
   private static int lastH;
   private static int baseW = 90;
   private static int baseH = 70;
   /** Did our hook run this frame? If a sidebar exists and it did not, another mod took the scoreboard over. */
   private static boolean reached = false;
   private static boolean foreign = false;

   record Entry(Text name, Text score, int scoreW) {
   }

   record Snapshot(Text title, int titleW, List<Entry> entries, int widest) {
   }

   public static final DraggableHud DRAGGABLE = new DraggableHud() {
      @Override
      public String id() {
         return "scoreboard";
      }

      @Override
      public int x() {
         return lastX;
      }

      @Override
      public int y() {
         return lastY;
      }

      @Override
      public int w() {
         return lastW;
      }

      @Override
      public int h() {
         return lastH;
      }

      @Override
      public float posX() {
         return cfg().panel.x;
      }

      @Override
      public float posY() {
         return cfg().panel.y;
      }

      @Override
      public void setPos(float nx, float ny) {
         cfg().panel.x = nx;
         cfg().panel.y = ny;
         FeatureConfig.markDirty();
      }

      /** Listed in the editor whenever a scoreboard has been measured, even one that is hidden. */
      @Override
      public boolean enabled() {
         return lastW > 0 && !foreign;
      }

      @Override
      public boolean resizable() {
         return true;
      }

      @Override
      public float scale() {
         return cfg().panel.scale;
      }

      @Override
      public void setScale(float s) {
         cfg().panel.scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, s));
         FeatureConfig.markDirty();
      }

      @Override
      public float minScale() {
         return MIN_SCALE;
      }

      @Override
      public float maxScale() {
         return MAX_SCALE;
      }

      @Override
      public int baseW() {
         return baseW;
      }

      @Override
      public int baseH() {
         return baseH;
      }

      @Override
      public boolean supportsBackgroundOpacity() {
         return true;
      }

      @Override
      public float backgroundOpacity() {
         float o = cfg().backgroundOpacity;
         return o >= 0.0F ? o : AUTO_OPACITY_SHOWN;
      }

      @Override
      public void setBackgroundOpacity(float opacity) {
         cfg().backgroundOpacity = Math.max(0.0F, Math.min(1.0F, opacity));
         FeatureConfig.markDirty();
      }
   };

   private ScoreboardHud() {
   }

   /** Client mods known to draw their own scoreboard (Fabric mod id, display name); only used to word messages. */
   private static final String[][] CLIENTS = {{"dawn", "Dawn"}, {"feather", "Feather"}};

   /** True while another mod is drawing the scoreboard instead of the game, so our settings cannot affect it. */
   public static boolean foreign() {
      return foreign;
   }

   /** Who is drawing it, for messages. */
   public static String foreignName() {
      for (String[] c : CLIENTS) {
         if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(c[0])) {
            return c[1];
         }
      }

      return "Another mod";
   }

   public static void frameStart() {
      reached = false;
   }

   public static void frameEnd(boolean sidebarExists) {
      foreign = isForeign(sidebarExists, reached);
      if (foreign) {
         lastW = 0;
      }
   }

   /** A sidebar exists but nobody called us to draw it: someone cancelled the game's draw before our hook. */
   static boolean isForeign(boolean sidebarExists, boolean hookReached) {
      return sidebarExists && !hookReached;
   }

   private static FeatureConfig.Scoreboard cfg() {
      return FeatureConfig.INSTANCE.scoreboard;
   }

   /**
    * Called at the head of the vanilla sidebar draw.
    *
    * @return true if we drew the scoreboard (or it is hidden), so vanilla must not
    */
   public static boolean handle(DrawContext ctx, ScoreboardObjective objective) {
      reached = true;
      foreign = false;
      if (!SuiteConfig.INSTANCE.isEnabledForCurrentWorld()) {
         lastW = 0;
         return false;
      }

      FeatureConfig.Scoreboard c = cfg();
      Snapshot snap = snapshot(objective, c.showNumbers);
      int screenW = ctx.getScaledWindowWidth();
      int screenH = ctx.getScaledWindowHeight();
      int[] box = box(snap.widest(), snap.entries().size(), screenW, screenH);
      baseW = box[2] - box[0];
      baseH = box[3] - box[1];

      float scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, c.panel.scale));
      int w = Math.round(baseW * scale);
      int h = Math.round(baseH * scale);
      int nx;
      int ny;
      if (c.panel.x < 0.0F || c.panel.y < 0.0F) {
         nx = box[2] - w; // keep the right edge where vanilla puts it
         ny = box[1] + (baseH - h) / 2; // and the vertical centre
      } else {
         nx = Math.round(c.panel.x * Math.max(0, screenW - w));
         ny = Math.round(c.panel.y * Math.max(0, screenH - h));
      }

      lastX = nx;
      lastY = ny;
      lastW = w;
      lastH = h;

      if (!c.isCustomized()) {
         return false; // untouched: vanilla (or the client's own scoreboard) does the drawing
      }

      if (c.hidden && !HudEditState.editMode) {
         return true;
      }

      Matrix3x2fStack matrices = ctx.getMatrices();
      matrices.pushMatrix();
      try {
         matrices.translate(nx, ny);
         matrices.scale(scale, scale);
         drawLocal(ctx, snap, c, baseW, baseH);
      } finally {
         matrices.popMatrix();
      }

      return true;
   }

   /** Draws in local coordinates: (0,0) is the top-left of the box, one unit per (unscaled) pixel. */
   private static void drawLocal(DrawContext ctx, Snapshot snap, FeatureConfig.Scoreboard c, int bw, int bh) {
      MinecraftClient client = MinecraftClient.getInstance();
      TextRenderer tr = client.textRenderer;
      if (c.background) {
         int body;
         int title;
         if (c.backgroundOpacity >= 0.0F) {
            int a = Math.round(Math.max(0.0F, Math.min(1.0F, c.backgroundOpacity)) * 255.0F);
            body = a << 24;
            title = Math.min(255, Math.round(a * 1.33F)) << 24;
         } else {
            body = client.options.getTextBackgroundColor(0.3F);
            title = client.options.getTextBackgroundColor(0.4F);
         }

         if (c.rounded) {
            Theme.roundRect(ctx, 0, 0, bw, bh, 3, body);
            Theme.roundRect(ctx, 0, 0, bw, TITLE_H + 1, 3, title);
            ctx.fill(0, 5, bw, TITLE_H + 1, title); // square off the title bar's lower corners
         } else {
            ctx.fill(0, 0, bw, TITLE_H, title);
            ctx.fill(0, TITLE_H, bw, bh, body);
         }
      }

      if (c.border) {
         int col = Theme.ACCENT;
         ctx.fill(0, 0, bw, 1, col);
         ctx.fill(0, bh - 1, bw, bh, col);
         ctx.fill(0, 0, 1, bh, col);
         ctx.fill(bw - 1, 0, bw, bh, col);
      }

      ctx.drawText(tr, snap.title(), 2 + (snap.widest() - snap.titleW()) / 2, 1, -1, c.textShadow);
      int y = 10;
      for (Entry e : snap.entries()) {
         ctx.drawText(tr, e.name(), 2, y, -1, c.textShadow);
         if (c.showNumbers && e.scoreW() > 0) {
            ctx.drawText(tr, e.score(), bw - e.scoreW(), y, -1, c.textShadow);
         }

         y += 9;
      }
   }

   /** What vanilla is about to draw, read the same way vanilla reads it. */
   private static Snapshot snapshot(ScoreboardObjective objective, boolean showNumbers) {
      TextRenderer tr = MinecraftClient.getInstance().textRenderer;
      Scoreboard scoreboard = objective.getScoreboard();
      NumberFormat numberFormat = objective.getNumberFormatOr(StyledNumberFormat.RED);
      List<Entry> entries = new ArrayList<>();
      for (ScoreboardEntry e : scoreboard.getScoreboardEntries(objective).stream().filter(x -> !x.hidden()).sorted(ORDER).limit(15L).toList()) {
         Team team = scoreboard.getScoreHolderTeam(e.owner());
         Text name = Team.decorateName(team, e.name());
         Text score = e.formatted(numberFormat);
         entries.add(new Entry(name, score, showNumbers ? tr.getWidth(score) : 0));
      }

      Text title = objective.getDisplayName();
      int titleW = tr.getWidth(title);
      int colon = tr.getWidth(": ");
      int widest = titleW;
      for (Entry e : entries) {
         widest = Math.max(widest, tr.getWidth(e.name()) + (e.scoreW() > 0 ? colon + e.scoreW() : 0));
      }

      return new Snapshot(title, titleW, entries, widest);
   }

   /**
    * The box vanilla draws the sidebar in: {x1, y1, x2, y2}. The right edge sits 1px inside the screen and the
    * block is centred a third of its height below the middle.
    */
   static int[] box(int widest, int lines, int screenW, int screenH) {
      int bottom = screenH / 2 + lines * 9 / 3;
      int x1 = screenW - widest - 3 - 2;
      int x2 = screenW - 3 + 2;
      int y1 = bottom - lines * 9 - 9 - 1;
      return new int[]{x1, y1, x2, bottom};
   }
}
