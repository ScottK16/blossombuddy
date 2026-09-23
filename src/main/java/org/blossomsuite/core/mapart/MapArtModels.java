package org.blossomsuite.core.mapart;

import java.util.ArrayList;
import java.util.List;

/** The JSON a design file downloaded from the public website holds (see site/mapart-app.js's download()). Read-only: the
 * mod never writes one of these, only the website does, when a player exports a finished design. */
public final class MapArtModels {
   private MapArtModels() {
   }

   public static final class Material {
      public int colorId;
      public String block;
      /** The real, namespaced Minecraft block id (e.g. "minecraft:white_wool") - see SchematicWriter. */
      public String blockId;
      public int count;
   }

   public static final class Project {
      public String name;
      public int width;
      public int height;
      public int[] blocks;
      public List<Material> materials = new ArrayList<>();
      /** A small "data:image/png;base64,..." preview, or empty if the site could not attach one. */
      public String thumbnail;
   }
}
