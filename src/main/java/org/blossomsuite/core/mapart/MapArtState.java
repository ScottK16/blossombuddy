package org.blossomsuite.core.mapart;

import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
/**
 * The one map art design currently "loaded" - set whenever {@code /buddy mapart} loads one off disk, read by both
 * {@link org.blossomsuite.core.ui.MapArtScreen} and the on-screen preview/materials HUDs, so those keep showing the
 * last thing you opened even after you close the screen. Owns the uploaded thumbnail texture, so there is exactly
 * one GPU upload per load no matter how many places show it, and exactly one place responsible for freeing it.
 */
public final class MapArtState {
   public static final MapArtState INSTANCE = new MapArtState();
   private static final AtomicInteger TEXTURE_SEQUENCE = new AtomicInteger();

   private volatile String fileName = "";
   private volatile MapArtModels.Project project;
   private volatile Identifier thumbnailId;
   private volatile int thumbnailWidth;
   private volatile int thumbnailHeight;

   private MapArtState() {
   }

   /** Must be called on the render thread (the texture upload needs it). */
   public void load(String fileName, MapArtModels.Project project) {
      this.releaseThumbnail();
      this.fileName = fileName == null ? "" : fileName;
      this.project = project;
      if (project != null) {
         this.uploadThumbnail(project.thumbnail);
      }
   }

   public void clear() {
      this.releaseThumbnail();
      this.fileName = "";
      this.project = null;
   }

   public String fileName() {
      return this.fileName;
   }

   public MapArtModels.Project project() {
      return this.project;
   }

   public Identifier thumbnailId() {
      return this.thumbnailId;
   }

   public int thumbnailWidth() {
      return this.thumbnailWidth;
   }

   public int thumbnailHeight() {
      return this.thumbnailHeight;
   }

   private void releaseThumbnail() {
      Minecraft client = Minecraft.getInstance();
      if (this.thumbnailId != null && client != null) {
         client.getTextureManager().release(this.thumbnailId);
      }

      this.thumbnailId = null;
   }

   /** Decodes the site's small "data:image/png;base64,..." preview and uploads it as a texture. Silently skipped if absent or unreadable. */
   private void uploadThumbnail(String dataUrl) {
      if (dataUrl == null || dataUrl.isBlank()) {
         return;
      }

      int comma = dataUrl.indexOf(',');
      if (comma < 0) {
         return;
      }

      try {
         byte[] bytes = Base64.getDecoder().decode(dataUrl.substring(comma + 1));
         NativeImage image = NativeImage.read(bytes);
         this.thumbnailWidth = image.getWidth();
         this.thumbnailHeight = image.getHeight();
         this.thumbnailId = Identifier.fromNamespaceAndPath("blossombuddy", "mapart_thumbnail_" + TEXTURE_SEQUENCE.incrementAndGet());
         Minecraft.getInstance().getTextureManager().register(this.thumbnailId, new DynamicTexture(() -> "blossombuddy mapart thumbnail", image));
      } catch (Exception e) {
         this.thumbnailId = null; // a broken thumbnail should never stop the block list from showing
      }
   }
}
