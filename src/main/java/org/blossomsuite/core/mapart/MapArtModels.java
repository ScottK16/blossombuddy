package org.blossomsuite.core.mapart;

import java.util.ArrayList;
import java.util.List;

/** The JSON the mod exchanges with the relay's /v1/mapart/get endpoint (see relay/mapart.js). The mod never creates a
 * project - that only ever happens on the public website - so there is no request model for that here. */
public final class MapArtModels {
   private MapArtModels() {
   }

   public static final class GetRequest {
      public String code;

      public GetRequest(String code) {
         this.code = code;
      }
   }

   public static final class Material {
      public int colorId;
      public String block;
      /** The real, namespaced Minecraft block id (e.g. "minecraft:white_wool") - see SchematicWriter. */
      public String blockId;
      public int count;
   }

   public static final class Project {
      public String code;
      public String name;
      public int width;
      public int height;
      public int[] blocks;
      public List<Material> materials = new ArrayList<>();
      /** A small "data:image/png;base64,..." preview, or empty if the site could not attach one. */
      public String thumbnail;
      public long createdAt;
   }

   public static final class Error {
      public String error;
   }
}
