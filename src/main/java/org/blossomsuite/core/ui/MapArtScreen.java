package org.blossomsuite.core.ui;

import java.io.File;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
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
      super(Component.literal("Map Art"));
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

      this.addRenderableWidget(StyledButton.of(Component.literal("Open Folder"), b -> this.openFolder()).dimensions(this.width / 2 - 180, 30, 110, 20).build());
      this.addRenderableWidget(StyledButton.of(Component.literal("Refresh"), b -> this.refreshFiles()).dimensions(this.width / 2 - 62, 30, 70, 20).build());
      this.addRenderableWidget(
         StyledButton.of(Component.literal(this.browsing ? "Loaded" : "Browse Files"), b -> this.setBrowsing(!this.browsing))
            .dimensions(this.width / 2 + 16, 30, 110, 20)
            .build()
      ).active = this.project != null;
      this.addRenderableWidget(StyledButton.of(Component.literal("Save Schematic"), b -> this.saveSchematic()).dimensions(this.width / 2 - 180, this.height - 28, 110, 20).build());
      this.addRenderableWidget(StyledButton.of(Component.literal("Done"), b -> this.onClose()).dimensions(this.width / 2 - 60, this.height - 28, 120, 20).build());
   }

   @Override
   public void onClose() {
      if (this.minecraft != null) {
         this.minecraft.setScreen(this.parent);
      }
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   private void setBrowsing(boolean browsing) {
      this.browsing = browsing;
      this.rebuildWidgets();
   }

   private void openFolder() {
      Util.getPlatform().openPath(MapArtFileStore.folder());
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
      CompoundTag nbt;
      try {
         nbt = SchematicWriter.build(this.project.width, this.project.height, this.project.blocks, palette);
      } catch (IllegalArgumentException e) {
         this.schematicStatus = "Could not build the schematic: " + e.getMessage();
         return;
      }

      File runDir = Minecraft.getInstance().gameDirectory;
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
   public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
      super.extractRenderState(ctx, mouseX, mouseY, delta);
      Font tr = this.font;
      ctx.centeredText(tr, Component.literal("Map Art").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), this.width / 2, 8, -1);

      if (this.browsing) {
         this.renderFileList(ctx, tr, mouseX, mouseY);
         return;
      }

      if (!this.error.isEmpty()) {
         ctx.centeredText(tr, Component.literal(this.error).withStyle(ChatFormatting.RED), this.width / 2, this.panelTop(), -1);
      }

      if (this.project == null) {
         if (this.error.isEmpty()) {
            ctx.centeredText(
               tr, Component.literal("Pick a design file to see the picture and block list here.").withStyle(ChatFormatting.DARK_GRAY), this.width / 2, this.panelTop(), -1
            );
         }

         return;
      }

      this.renderThumbnail(ctx);
      this.renderHeader(ctx, tr);
      this.renderMaterials(ctx, tr, mouseX, mouseY);

      if (!this.schematicStatus.isEmpty()) {
         ctx.centeredText(tr, Component.literal(this.schematicStatus).withStyle(ChatFormatting.GRAY), this.width / 2, this.height - 40, -1);
      }
   }

   private void renderFileList(GuiGraphicsExtractor ctx, Font tr, int mouseX, int mouseY) {
      int x = this.fileListX();
      int y = this.panelTop();
      int w = this.fileListWidth();

      if (!this.error.isEmpty()) {
         ctx.centeredText(tr, Component.literal(this.error).withStyle(ChatFormatting.RED), this.width / 2, y, -1);
         y += 16;
      }

      if (this.files.isEmpty()) {
         ctx.centeredText(tr, Component.literal("No design files yet.").withStyle(ChatFormatting.DARK_GRAY), this.width / 2, y, -1);
         ctx.centeredText(
            tr, Component.literal("Download one from the website and drop it into blossombuddy-mapart, then press Refresh.").withStyle(ChatFormatting.DARK_GRAY), this.width / 2, y + 12, -1
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

         ctx.text(tr, Component.literal(name), x, rowY, hovered ? -1 : Theme.TEXT_DIM, false);
      }

      int extra = this.files.size() - this.fileScroll - shown;
      if (extra > 0) {
         ctx.text(tr, Component.literal("+" + extra + " more (scroll)").withStyle(ChatFormatting.DARK_GRAY), x, y + shown * ROW_H + 4, -1);
      }
   }

   private void renderThumbnail(GuiGraphicsExtractor ctx) {
      int x = this.thumbBoxX();
      int y = this.panelTop();
      Theme.roundBox(ctx, x, y, x + THUMB_BOX, y + THUMB_BOX, 4, Theme.CONTROL_EDGE, Theme.CONTROL_OFF);
      var thumbnailId = MapArtState.INSTANCE.thumbnailId();
      int tw = MapArtState.INSTANCE.thumbnailWidth();
      int th = MapArtState.INSTANCE.thumbnailHeight();
      if (thumbnailId != null && tw > 0 && th > 0) {
         ctx.blit(RenderPipelines.GUI_TEXTURED, thumbnailId, x + 2, y + 2, 0.0F, 0.0F, THUMB_BOX - 4, THUMB_BOX - 4, tw, th, tw, th);
      } else {
         ctx.centeredText(this.font, Component.literal("(no preview)").withStyle(ChatFormatting.DARK_GRAY), x + THUMB_BOX / 2, y + THUMB_BOX / 2 - 4, -1);
      }
   }

   private void renderHeader(GuiGraphicsExtractor ctx, Font tr) {
      int x = this.thumbBoxX();
      int y = this.panelTop() + THUMB_BOX + 8;
      String name = this.project.name == null || this.project.name.isBlank() ? "Untitled design" : this.project.name;
      ctx.text(tr, Component.literal(name).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD), x, y, -1);

      int mapsX = (this.project.width + 127) / 128;
      int mapsY = (this.project.height + 127) / 128;
      int totalMaps = mapsX * mapsY;
      String dims = this.project.width + " x " + this.project.height + " blocks (" + totalMaps + " map" + (totalMaps == 1 ? "" : "s") + ")";
      ctx.text(tr, Component.literal(dims).withStyle(ChatFormatting.GRAY), x, y + 11, -1);
      ctx.text(tr, Component.literal("File: " + this.loadedFile).withStyle(ChatFormatting.DARK_GRAY), x, y + 22, -1);
   }

   private void renderMaterials(GuiGraphicsExtractor ctx, Font tr, int mouseX, int mouseY) {
      List<MapArtModels.Material> materials = this.project.materials;
      int x = this.materialsX();
      int y = this.panelTop();
      int w = this.materialsWidth();

      long totalBlocks = 0L;
      for (MapArtModels.Material m : materials) {
         totalBlocks += m.count;
      }

      ctx.text(tr, Component.literal(materials.size() + " block types, " + totalBlocks + " total").withStyle(ChatFormatting.GRAY), x, y - 11, -1);

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
         ctx.text(tr, Component.literal(line), x, rowY, hovered ? -1 : Theme.TEXT_DIM, false);
      }
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent inputEvent, boolean isDoubleClick) {
      double mouseX = inputEvent.x();
      double mouseY = inputEvent.y();
      int button = inputEvent.button();
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

      return super.mouseClicked(inputEvent, isDoubleClick);
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
   public boolean keyPressed(KeyEvent inputEvent) {
      int keyCode = inputEvent.key();
      int scanCode = inputEvent.scancode();
      int modifiers = inputEvent.modifiers();
      if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
         this.onClose();
         return true;
      }

      return super.keyPressed(inputEvent);
   }
}
