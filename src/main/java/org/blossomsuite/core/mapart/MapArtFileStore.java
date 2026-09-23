package org.blossomsuite.core.mapart;

import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import org.blossomsuite.core.util.JsonUtil;

/**
 * Map art designs made on the public website (site/mapart.html) and downloaded as a file, read straight off the
 * player's own computer - a design never has to be sent anywhere just to see it in-game. See site/mapart-app.js's
 * download() for the file this reads.
 */
public final class MapArtFileStore {
   private MapArtFileStore() {
   }

   public static Path folder() {
      return MinecraftClient.getInstance().runDirectory.toPath().resolve("blossombuddy-mapart");
   }

   /** Makes the folder if it doesn't exist yet, so there's always somewhere to point a player at. */
   public static void ensureFolder() {
      try {
         Files.createDirectories(folder());
      } catch (IOException ignored) {
      }
   }

   /** File names, without the .json extension, most recently modified first. */
   public static List<String> list() {
      return list(folder());
   }

   static List<String> list(Path dir) {
      List<Path> files;
      try (var stream = Files.list(dir)) {
         files = stream.filter(p -> p.getFileName().toString().endsWith(".json")).sorted(Comparator.comparingLong(MapArtFileStore::modifiedOrZero).reversed()).toList();
      } catch (IOException e) {
         return List.of();
      }

      List<String> names = new ArrayList<>();
      for (Path p : files) {
         String n = p.getFileName().toString();
         names.add(n.substring(0, n.length() - ".json".length()));
      }

      return names;
   }

   private static long modifiedOrZero(Path p) {
      try {
         return Files.getLastModifiedTime(p).toMillis();
      } catch (IOException e) {
         return 0L;
      }
   }

   /** Either the project, or a message the player can be told. Never both. */
   public record Result(MapArtModels.Project project, String error) {
      static Result ok(MapArtModels.Project project) {
         return new Result(project, null);
      }

      static Result fail(String error) {
         return new Result(null, error);
      }

      public boolean ok() {
         return this.project != null;
      }
   }

   public static Result load(String fileName) {
      return load(folder(), fileName);
   }

   static Result load(Path dir, String fileName) {
      Path file = dir.resolve(fileName + ".json");
      String text;
      try {
         text = Files.readString(file, StandardCharsets.UTF_8);
      } catch (IOException e) {
         return Result.fail("Could not read that file.");
      }

      MapArtModels.Project project;
      try {
         project = JsonUtil.GSON.fromJson(text, MapArtModels.Project.class);
      } catch (JsonParseException e) {
         return Result.fail("That file isn't a valid map art design.");
      }

      if (project == null || project.blocks == null || project.width <= 0 || project.height <= 0) {
         return Result.fail("That file isn't a valid map art design.");
      }

      return Result.ok(project);
   }
}
