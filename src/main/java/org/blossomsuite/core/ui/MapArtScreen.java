package org.blossomsuite.core.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import org.blossomsuite.core.mapart.MapArtFileStore;
import org.blossomsuite.core.mapart.MapArtModels;
import org.blossomsuite.core.mapart.MapArtState;
import org.blossomsuite.core.mapart.SchematicWriter;
import org.blossomsuite.core.util.SuiteLog;
import org.lwjgl.glfw.GLFW;

/**
 * Shows a map art design downloaded from the public website (site/mapart.html) - the picture and the exact block
 * list, read straight off the player's own disk, so a design never has to be sent anywhere just to see it in-game.
 */
public final class MapArtScreen extends Screen {
   private static final int THUMB_BOX = 128;
   private static final int ROW_H = 12;
   private static final int MAX_FILE_ROWS = 8;

   private final Screen parent;

   private boolean browsing;
   private List<String> files = List.of();
   private int fileScroll = 0;

   private String loadedFile;
   private String error = "";
   private MapArtModels.Project project;
   private int scroll = 0;
   private String schematicStatus = "";

   public MapArtScreen(Screen parent, String initialFile) {
      super(Text.literal("Map Art"));
      this.parent = parent;
      // reopening the screen (e.g. from the HUD) should show whatever is already loaded, not start blank
      this.loadedFile = MapArtState.INSTANCE.fileName();
      this.project = MapArtState.INSTANCE.project();
      this.browsing = this.project == null;
      if (initialFile != null && !initialFile.isBlank()) {
         this.openFile(initialFile);
      }
   }

   @Override
   protected void init() {
      MapArtFileStore.ensureFolder();
      this.refreshFiles();

      this.addDrawableChild(StyledButton.of(Text.literal("Open Folder"), b -> this.openFolder()).dimensions(this.width / 2 - 180, 30, 110, 20).build());
      this.addDrawableChild(StyledButton.of(Text.literal("Refresh"), b -> this.refreshFiles()).dimensions(this.width / 2 - 62, 30, 70, 20).build());
      this.addDrawableChild(
         StyledButton.of(Text.literal(this.browsing ? "Loaded" : "Browse Files"), b -> this.setBrowsing(!this.browsing))
            .dimensions(this.width / 2 + 16, 30, 110, 20)
            .build()
      ).active = this.project != null;
      this.addDrawableChild(StyledButton.of(Text.literal("Save Schematic"), b -> this.saveSchematic()).dimensions(this.width / 2 - 180, this.height - 28, 110, 20).build());
      this.addDrawableChild(StyledButton.of(Text.literal("Done"), b -> this.close()).dimensions(this.width / 2 - 60, this.height - 28, 120, 20).build());
   }

   @Override
   public void close() {
      if (this.client != null) {
         this.client.setScreen(this.parent);
      }
   }

   @Override
   public boolean shouldPause() {
      return false;
   }

   private void setBrowsing(boolean browsing) {
      this.browsing = browsing;
      this.clearAndInit();
   }

   private void openFolder() {
      Util.getOperatingSystem().open(MapArtFileStore.folder());
   }

   private void refreshFiles() {
      this.files = MapArtFileStore.list();
      this.fileScroll = 0;
   }

   private void openFile(String fileName) {
      MapArtFileStore.Result result = MapArtFileStore.load(fileName);
      this.error = "";
      if (!result.ok()) {
         this.error = result.error();
         return;
      }

      this.loadedFile = fileName;
      this.project = result.project();
      this.scroll = 0;
      this.schematicStatus = "";
      MapArtState.INSTANCE.load(fileName, result.project()); // shared with the HUD, so it keeps showing this after the screen closes
      this.setBrowsing(false);
   }

   /**
    * Writes the design as a vanilla structure NBT into {@code .minecraft/schematics/} - Litematica's own default folder
    * and file browser, which auto-detects and loads this format directly. See SchematicWriter for why this format
    * (not a hand-rolled .litematic) was chosen.
    */
   private void saveSchematic() {
      if (this.project == null) {
         this.schematicStatus = "Open a design first.";
         return;
      }

      Map<Integer, String> palette = SchematicWriter.paletteOf(this.project.materials);
      NbtCompound nbt;
      try {
         nbt = SchematicWriter.build(this.project.width, this.project.height, this.project.blocks, palette);
      } catch (IllegalArgumentException e) {
         this.schematicStatus = "Could not build the schematic: " + e.getMessage();
         return;
      }

      File runDir = MinecraftClient.getInstance().runDirectory;
      Path schematicsDir = runDir.toPath().resolve("schematics");
      String fileName = SchematicWriter.safeFileName(this.project.name, this.loadedFile) + ".nbt";
      Path target = schematicsDir.resolve(fileName);

      try {
         java.nio.file.Files.createDirectories(schematicsDir);
         NbtIo.writeCompressed(nbt, target);
         this.schematicStatus = "Saved to schematics/" + fileName + " - open it from Litematica's own Load Schematic screen.";
      } catch (IOException e) {
         SuiteLog.logger().debug("[mapart] could not write schematic: {}", e.toString());
         this.schematicStatus = "Could not save: " + e.getMessage();
      }
   }

   // ------------------------------------------------------------------ layout

   private int thumbBoxX() {
      return this.width / 2 - THUMB_BOX - 12;
   }

   private int panelTop() {
      return 64;
   }

   private int materialsX() {
      return this.width / 2 + 12;
   }

   private int materialsWidth() {
      return Math.min(220, this.width / 2 - 24);
   }

   private int maxRows() {
      return Math.max(1, (this.height - 40 - this.panelTop()) / ROW_H);
   }

   private int fileListX() {
      return this.width / 2 - 150;
   }

   private int fileListWidth() {
      return 300;
   }

   @Override
   public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
      super.render(ctx, mouseX, mouseY, delta);
      TextRenderer tr = this.textRenderer;
      ctx.drawCenteredTextWithShadow(tr, Text.literal("Map Art").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), this.width / 2, 8, -1);

      if (this.browsing) {
         this.renderFileList(ctx, tr, mouseX, mouseY);
         return;
      }

      if (!this.error.isEmpty()) {
         ctx.drawCenteredTextWithShadow(tr, Text.literal(this.error).formatted(Formatting.RED), this.width / 2, this.panelTop(), -1);
      }

      if (this.project == null) {
         if (this.error.isEmpty()) {
            ctx.drawCenteredTextWithShadow(
               tr, Text.literal("Pick a design file to see the picture and block list here.").formatted(Formatting.DARK_GRAY), this.width / 2, this.panelTop(), -1
            );
         }

         return;
      }

      this.renderThumbnail(ctx);
      this.renderHeader(ctx, tr);
      this.renderMaterials(ctx, tr, mouseX, mouseY);

      if (!this.schematicStatus.isEmpty()) {
         ctx.drawCenteredTextWithShadow(tr, Text.literal(this.schematicStatus).formatted(Formatting.GRAY), this.width / 2, this.height - 40, -1);
      }
   }

   private void renderFileList(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY) {
      int x = this.fileListX();
      int y = this.panelTop();
      int w = this.fileListWidth();

      if (!this.error.isEmpty()) {
         ctx.drawCenteredTextWithShadow(tr, Text.literal(this.error).formatted(Formatting.RED), this.width / 2, y, -1);
         y += 16;
      }

      if (this.files.isEmpty()) {
         ctx.drawCenteredTextWithShadow(tr, Text.literal("No design files yet.").formatted(Formatting.DARK_GRAY), this.width / 2, y, -1);
         ctx.drawCenteredTextWithShadow(
            tr, Text.literal("Download one from the website and drop it into blossombuddy-mapart, then press Refresh.").formatted(Formatting.DARK_GRAY), this.width / 2, y + 12, -1
         );
         return;
      }

      int shown = Math.min(this.files.size() - this.fileScroll, MAX_FILE_ROWS);
      for (int i = 0; i < shown; i++) {
         String name = this.files.get(this.fileScroll + i);
         int rowY = y + i * ROW_H;
         boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= rowY && mouseY < rowY + ROW_H;
         if (hovered) {
            ctx.fill(x - 4, rowY - 1, x + w, rowY + ROW_H - 1, 0x33FFFFFF);
         }

         ctx.drawText(tr, Text.literal(name), x, rowY, hovered ? -1 : Theme.TEXT_DIM, false);
      }

      int extra = this.files.size() - this.fileScroll - shown;
      if (extra > 0) {
         ctx.drawTextWithShadow(tr, Text.literal("+" + extra + " more (scroll)").formatted(Formatting.DARK_GRAY), x, y + shown * ROW_H + 4, -1);
      }
   }

   private void renderThumbnail(DrawContext ctx) {
      int x = this.thumbBoxX();
      int y = this.panelTop();
      Theme.roundBox(ctx, x, y, x + THUMB_BOX, y + THUMB_BOX, 4, Theme.CONTROL_EDGE, Theme.CONTROL_OFF);
      var thumbnailId = MapArtState.INSTANCE.thumbnailId();
      int tw = MapArtState.INSTANCE.thumbnailWidth();
      int th = MapArtState.INSTANCE.thumbnailHeight();
      if (thumbnailId != null && tw > 0 && th > 0) {
         ctx.drawTexture(RenderPipelines.GUI_TEXTURED, thumbnailId, x + 2, y + 2, 0.0F, 0.0F, THUMB_BOX - 4, THUMB_BOX - 4, tw, th, tw, th);
      } else {
         ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("(no preview)").formatted(Formatting.DARK_GRAY), x + THUMB_BOX / 2, y + THUMB_BOX / 2 - 4, -1);
      }
   }

   private void renderHeader(DrawContext ctx, TextRenderer tr) {
      int x = this.thumbBoxX();
      int y = this.panelTop() + THUMB_BOX + 8;
      String name = this.project.name == null || this.project.name.isBlank() ? "Untitled design" : this.project.name;
      ctx.drawTextWithShadow(tr, Text.literal(name).formatted(Formatting.WHITE, Formatting.BOLD), x, y, -1);

      int mapsX = (this.project.width + 127) / 128;
      int mapsY = (this.project.height + 127) / 128;
      int totalMaps = mapsX * mapsY;
      String dims = this.project.width + " x " + this.project.height + " blocks (" + totalMaps + " map" + (totalMaps == 1 ? "" : "s") + ")";
      ctx.drawTextWithShadow(tr, Text.literal(dims).formatted(Formatting.GRAY), x, y + 11, -1);
      ctx.drawTextWithShadow(tr, Text.literal("File: " + this.loadedFile).formatted(Formatting.DARK_GRAY), x, y + 22, -1);
   }

   private void renderMaterials(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY) {
      List<MapArtModels.Material> materials = this.project.materials;
      int x = this.materialsX();
      int y = this.panelTop();
      int w = this.materialsWidth();

      long totalBlocks = 0L;
      for (MapArtModels.Material m : materials) {
         totalBlocks += m.count;
      }

      ctx.drawTextWithShadow(tr, Text.literal(materials.size() + " block types, " + totalBlocks + " total").formatted(Formatting.GRAY), x, y - 11, -1);

      int rows = this.maxRows();
      this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, materials.size() - rows)));
      for (int i = 0; i < rows && this.scroll + i < materials.size(); i++) {
         MapArtModels.Material m = materials.get(this.scroll + i);
         int rowY = y + i * ROW_H;
         boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= rowY && mouseY < rowY + ROW_H;
         if (hovered) {
            ctx.fill(x - 2, rowY - 1, x + w, rowY + ROW_H - 1, 0x33FFFFFF);
         }

         String line = m.count + "x " + m.block;
         ctx.drawText(tr, Text.literal(line), x, rowY, hovered ? -1 : Theme.TEXT_DIM, false);
      }
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (this.browsing && button == 0 && !this.files.isEmpty()) {
         int x = this.fileListX();
         int y = this.panelTop();
         int w = this.fileListWidth();
         int shown = Math.min(this.files.size() - this.fileScroll, MAX_FILE_ROWS);
         for (int i = 0; i < shown; i++) {
            int rowY = y + i * ROW_H;
            if (mouseX >= x && mouseX < x + w && mouseY >= rowY && mouseY < rowY + ROW_H) {
               this.openFile(this.files.get(this.fileScroll + i));
               return true;
            }
         }
      }

      return super.mouseClicked(mouseX, mouseY, button);
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      if (verticalAmount == 0.0D) {
         return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
      }

      if (this.browsing) {
         this.fileScroll = Math.max(0, Math.min(this.fileScroll - (int) Math.signum(verticalAmount) * 3, Math.max(0, this.files.size() - MAX_FILE_ROWS)));
         return true;
      }

      if (this.project != null) {
         this.scroll = Math.max(0, this.scroll - (int) Math.signum(verticalAmount) * 3);
         return true;
      }

      return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
         this.close();
         return true;
      }

      return super.keyPressed(keyCode, scanCode, modifiers);
   }
}
